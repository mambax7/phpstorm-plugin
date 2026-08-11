package org.xoops.support.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.xoops.support.XoopsSupportPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flags fetchArray/fetchRow/fetchBoth calls that are not protected by an
 * immediately preceding early-exit {@code isResultSet($result)} guard, or by
 * being nested inside a positive {@code if (... isResultSet($result) ...)} block
 * (including compound conditions with parenthesized operands such as
 * {@code count($errors) === 0 && $db->isResultSet($result)}).
 *
 * <p>If-conditions are parsed with balanced parentheses (not {@code [^)]*}), so
 * nested calls in the condition do not truncate the match.
 *
 * <p>Failure quick-fix always uses {@code throw} (valid in every PHP scope).
 */
public final class XoopsResultSetGuardInspection extends LocalInspectionTool {

    private static final Pattern FETCH = Pattern.compile(
            "(\\$[A-Za-z_][\\w]*(?:\\s*->\\s*\\$?[A-Za-z_][\\w]*)*)\\s*->\\s*fetch(Array|Row|Both)\\s*\\(\\s*(\\$[A-Za-z_][\\w]*)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern IF_KEYWORD = Pattern.compile("\\bif\\s*\\(", Pattern.CASE_INSENSITIVE);

    private static final String FAIL_ACTION = "throw new \\RuntimeException('Database query failed');";

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitFile(@NotNull PsiFile file) {
                if (!XoopsSupportPlugin.isEnabled(file)) {
                    return;
                }
                if (!PhpTextUtil.isPhpFile(file) || PhpTextUtil.looksLikeVendorOrCache(file)) {
                    return;
                }
                String text = file.getText();
                String code = PhpTextUtil.maskCommentsAndStrings(text);
                Matcher m = FETCH.matcher(code);
                while (m.find()) {
                    String dbExpr = text.substring(m.start(1), m.end(1)).replaceAll("\\s+", "");
                    String resultVar = text.substring(m.start(3), m.end(3));
                    String before = code.substring(0, m.start());
                    if (isFetchAlreadyGuarded(before, resultVar)) {
                        continue;
                    }
                    PsiElement leaf = PhpTextUtil.leafAt(file, m.start());
                    if (leaf == null) {
                        continue;
                    }
                    String indentGuess = guessIndent(text, m.start());
                    String block = indentGuess + "if (!" + dbExpr + "->isResultSet(" + resultVar
                            + ") || !" + resultVar + " instanceof \\mysqli_result) {\n"
                            + indentGuess + "    " + FAIL_ACTION + "\n"
                            + indentGuess + "}\n";
                    int insertAt = lineStart(text, m.start());
                    String expectedAt = text.substring(insertAt, Math.min(text.length(), insertAt + 32));
                    holder.registerProblem(
                            leaf,
                            "XOOPS: call isResultSet($result) (and prefer mysqli_result check) before fetch*",
                            new InsertBeforeOffsetQuickFix(
                                    "Insert isResultSet guard before fetch",
                                    insertAt,
                                    block,
                                    expectedAt
                            )
                    );
                }
            }
        };
    }

    private static boolean isFetchAlreadyGuarded(@NotNull String before, @NotNull String resultVar) {
        List<IfCond> ifs = findIfConditions(before);
        if (ifs.isEmpty()) {
            return false;
        }

        // 1) Early-exit: last if ends at EOF of `before` and negates isResultSet with return/throw body.
        IfCond last = ifs.get(ifs.size() - 1);
        if (isOnlyWhitespace(before.substring(Math.min(last.ifEnd, before.length())))) {
            // if construct consumed through end of before (still “attached” to the fetch)
            if (conditionNegatesIsResultSet(last.condition, resultVar)
                    && bodyHasEarlyExit(before, last)) {
                return true;
            }
        } else if (last.ifEnd >= before.length()
                && conditionNegatesIsResultSet(last.condition, resultVar)
                && bodyHasEarlyExit(before, last)) {
            return true;
        }

        // Also: early-exit if ends with only whitespace after ifEnd
        if (last.ifEnd <= before.length()
                && isOnlyWhitespace(before.substring(last.ifEnd))
                && conditionNegatesIsResultSet(last.condition, resultVar)
                && bodyHasEarlyExit(before, last)) {
            return true;
        }

        // 2) Nested inside a still-open positive if (... isResultSet($var) ...)
        return isInsidePositiveIsResultSetBlock(before, resultVar, ifs);
    }

    /**
     * Find every {@code if ( ... )} with a fully balanced condition so nested
     * parentheses (e.g. {@code count($errors) === 0 && isResultSet($r)}) work.
     */
    private static @NotNull List<IfCond> findIfConditions(@NotNull String text) {
        List<IfCond> out = new ArrayList<>();
        Matcher m = IF_KEYWORD.matcher(text);
        while (m.find()) {
            int openParen = m.end() - 1;
            int closeParen = matchingCloseParen(text, openParen);
            if (closeParen < 0) {
                continue;
            }
            String cond = text.substring(openParen + 1, closeParen);
            int after = closeParen + 1;
            while (after < text.length() && Character.isWhitespace(text.charAt(after))) {
                after++;
            }
            int bodyStart = -1;
            int openBrace = -1;
            int ifEnd;
            if (after < text.length() && text.charAt(after) == '{') {
                openBrace = after;
                bodyStart = after + 1;
                int closeBrace = matchingCloseBrace(text, after);
                ifEnd = closeBrace < 0 ? text.length() : closeBrace + 1;
            } else {
                int semi = text.indexOf(';', after);
                ifEnd = semi < 0 ? text.length() : semi + 1;
            }
            out.add(new IfCond(m.start(), openParen, closeParen, openBrace, bodyStart, ifEnd, cond));
        }
        return out;
    }

    private static int matchingCloseParen(@NotNull String text, int openParen) {
        if (openParen < 0 || openParen >= text.length() || text.charAt(openParen) != '(') {
            return -1;
        }
        int depth = 1;
        for (int i = openParen + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int matchingCloseBrace(@NotNull String text, int openBrace) {
        if (openBrace < 0 || openBrace >= text.length() || text.charAt(openBrace) != '{') {
            return -1;
        }
        int depth = 1;
        for (int i = openBrace + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean isOnlyWhitespace(@NotNull String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean bodyHasEarlyExit(@NotNull String text, @NotNull IfCond ic) {
        int from;
        int to;
        if (ic.bodyStart >= 0) {
            from = ic.bodyStart;
            to = Math.min(ic.ifEnd, text.length());
            // body only (exclude closing brace if present)
            if (to > from && text.charAt(to - 1) == '}') {
                to--;
            }
        } else {
            from = ic.condEnd + 1;
            to = Math.min(ic.ifEnd, text.length());
        }
        if (from >= to) {
            return false;
        }
        String body = text.substring(from, to);
        return Pattern.compile("(?is)\\b(return|throw|exit|die|break|continue)\\b").matcher(body).find();
    }

    private static boolean conditionMentionsIsResultSet(@NotNull String cond, @NotNull String resultVar) {
        String escaped = Pattern.quote(resultVar);
        return Pattern.compile(
                "(?is)isResultSet\\s*\\(\\s*" + escaped + "\\s*\\)"
        ).matcher(cond).find();
    }

    /**
     * True when {@code !} applies to the isResultSet($var) call itself (or to
     * {@code $var instanceof}), not merely when some other operand is negated
     * (e.g. {@code !$error && isResultSet($result)} is still a positive guard).
     */
    private static boolean conditionNegatesIsResultSet(@NotNull String cond, @NotNull String resultVar) {
        String escaped = Pattern.quote(resultVar);
        Pattern negCall = Pattern.compile(
                "(?is)!\\s*(?:\\$[A-Za-z_][\\w]*(?:\\s*->\\s*\\$?[A-Za-z_][\\w]*)*\\s*->\\s*)?"
                        + "isResultSet\\s*\\(\\s*" + escaped + "\\s*\\)"
        );
        if (negCall.matcher(cond).find()) {
            return true;
        }
        Pattern negInstanceof = Pattern.compile(
                "(?is)!\\s*" + escaped + "\\s*instanceof"
        );
        return negInstanceof.matcher(cond).find();
    }

    private static boolean isInsidePositiveIsResultSetBlock(
            @NotNull String before,
            @NotNull String resultVar,
            @NotNull List<IfCond> ifs
    ) {
        // Prefer the innermost (last) still-open positive if.
        for (int i = ifs.size() - 1; i >= 0; i--) {
            IfCond ic = ifs.get(i);
            if (ic.openBrace < 0 || ic.bodyStart < 0) {
                continue;
            }
            if (!conditionMentionsIsResultSet(ic.condition, resultVar)) {
                continue;
            }
            if (conditionNegatesIsResultSet(ic.condition, resultVar)) {
                continue;
            }
            // Depth from this if's '{' through end of `before`: > 0 means fetch is still inside.
            int depth = 1;
            for (int j = ic.openBrace + 1; j < before.length(); j++) {
                char c = before.charAt(j);
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        break; // closed before fetch
                    }
                }
            }
            if (depth > 0) {
                return true;
            }
        }
        return false;
    }

    private static int lineStart(String text, int offset) {
        int i = text.lastIndexOf('\n', Math.max(0, offset - 1));
        return i < 0 ? 0 : i + 1;
    }

    private static String guessIndent(String text, int offset) {
        int start = lineStart(text, offset);
        int i = start;
        while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) {
            i++;
        }
        return text.substring(start, i);
    }

    private record IfCond(
            int ifStart,
            int openParen,
            int condEnd,
            int openBrace,
            int bodyStart,
            int ifEnd,
            @NotNull String condition
    ) {
    }
}
