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
 * being nested inside a positive {@code if/elseif (... isResultSet($result) ...)}
 * block or brace-less single-statement form.
 *
 * <p>If-conditions use balanced parentheses so nested calls in the condition work.
 * Failure quick-fix always uses {@code throw} (valid in every PHP scope).
 */
public final class XoopsResultSetGuardInspection extends LocalInspectionTool {

    private static final Pattern FETCH = Pattern.compile(
            "(\\$[A-Za-z_][\\w]*(?:\\s*->\\s*\\$?[A-Za-z_][\\w]*)*)\\s*->\\s*fetch(Array|Row|Both)\\s*\\(\\s*(\\$[A-Za-z_][\\w]*)",
            Pattern.CASE_INSENSITIVE
    );

    /** {@code if (}, {@code elseif (}, {@code else if (}. */
    private static final Pattern IF_KEYWORD = Pattern.compile(
            "\\b(?:else\\s+)?if\\s*\\(|\\belseif\\s*\\(",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern EARLY_EXIT_KW = Pattern.compile(
            "(?is)\\b(return|throw|exit|die|break|continue)\\b"
    );

    private static final Pattern IS_RESULT_SET_CALL = Pattern.compile(
            "(?is)isResultSet\\s*\\(\\s*(\\$[A-Za-z_][\\w]*)\\s*\\)"
    );

    private static final Pattern NEG_IS_RESULT_SET = Pattern.compile(
            "(?is)!\\s*(?:\\$[A-Za-z_][\\w]*(?:\\s*->\\s*\\$?[A-Za-z_][\\w]*)*\\s*->\\s*)?"
                    + "isResultSet\\s*\\(\\s*(\\$[A-Za-z_][\\w]*)\\s*\\)"
    );

    private static final Pattern NEG_INSTANCEOF = Pattern.compile(
            "(?is)!\\s*(\\$[A-Za-z_][\\w]*)\\s*instanceof"
    );

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
                // Parse if/elseif once for the whole file; filter by offset per fetch.
                List<IfCond> allIfs = findIfConditions(code);
                Matcher m = FETCH.matcher(code);
                while (m.find()) {
                    String dbExpr = text.substring(m.start(1), m.end(1)).replaceAll("\\s+", "");
                    String resultVar = text.substring(m.start(3), m.end(3));
                    int fetchOffset = m.start();
                    if (isFetchAlreadyGuarded(code, fetchOffset, resultVar, allIfs)) {
                        continue;
                    }
                    PsiElement leaf = PhpTextUtil.leafAt(file, fetchOffset);
                    if (leaf == null) {
                        continue;
                    }
                    String indentGuess = guessIndent(text, fetchOffset);
                    String block = indentGuess + "if (!" + dbExpr + "->isResultSet(" + resultVar
                            + ") || !" + resultVar + " instanceof \\mysqli_result) {\n"
                            + indentGuess + "    " + FAIL_ACTION + "\n"
                            + indentGuess + "}\n";
                    int insertAt = lineStart(text, fetchOffset);
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

    /**
     * @param code        full masked file text
     * @param fetchOffset start of fetch call in {@code code}
     * @param allIfs      all if/elseif constructs in {@code code}
     */
    private static boolean isFetchAlreadyGuarded(
            @NotNull String code,
            int fetchOffset,
            @NotNull String resultVar,
            @NotNull List<IfCond> allIfs
    ) {
        // Only ifs that start before the fetch.
        List<IfCond> before = new ArrayList<>();
        for (IfCond ic : allIfs) {
            if (ic.ifStart < fetchOffset) {
                before.add(ic);
            }
        }
        if (before.isEmpty()) {
            return false;
        }

        IfCond last = before.get(before.size() - 1);
        // Early-exit: if construct ends at/after fetch start with only whitespace between
        // (for braced early-exit ending just before fetch).
        if (last.ifEnd <= fetchOffset
                && isOnlyWhitespace(code.substring(last.ifEnd, fetchOffset))
                && conditionNegatesIsResultSet(last.condition, resultVar)
                && bodyHasEarlyExit(code, last)) {
            return true;
        }

        // Positive wrap (braced or brace-less single-statement).
        return isInsidePositiveIsResultSetGuard(code, fetchOffset, resultVar, before);
    }

    /**
     * Find every {@code if/elseif ( ... )} with a fully balanced condition.
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
                // Brace-less: if (cond) stmt;
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
        return EARLY_EXIT_KW.matcher(text.substring(from, to)).find();
    }

    private static boolean conditionMentionsIsResultSet(@NotNull String cond, @NotNull String resultVar) {
        Matcher m = IS_RESULT_SET_CALL.matcher(cond);
        while (m.find()) {
            if (resultVar.equals(m.group(1))) {
                return true;
            }
        }
        return false;
    }

    private static boolean conditionNegatesIsResultSet(@NotNull String cond, @NotNull String resultVar) {
        Matcher m = NEG_IS_RESULT_SET.matcher(cond);
        while (m.find()) {
            if (resultVar.equals(m.group(1))) {
                return true;
            }
        }
        Matcher mi = NEG_INSTANCEOF.matcher(cond);
        while (mi.find()) {
            if (resultVar.equals(mi.group(1))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fetch is inside a positive isResultSet guard: braced block still open, or
     * brace-less {@code if (isResultSet($r)) $row = $db->fetch...;} spanning the fetch.
     */
    private static boolean isInsidePositiveIsResultSetGuard(
            @NotNull String code,
            int fetchOffset,
            @NotNull String resultVar,
            @NotNull List<IfCond> ifs
    ) {
        for (int i = ifs.size() - 1; i >= 0; i--) {
            IfCond ic = ifs.get(i);
            if (ic.ifStart >= fetchOffset) {
                continue;
            }
            if (!conditionMentionsIsResultSet(ic.condition, resultVar)) {
                continue;
            }
            if (conditionNegatesIsResultSet(ic.condition, resultVar)) {
                continue;
            }

            if (ic.openBrace >= 0) {
                // Braced: depth from '{' through fetchOffset must stay > 0
                int depth = 1;
                for (int j = ic.openBrace + 1; j < fetchOffset; j++) {
                    char c = code.charAt(j);
                    if (c == '{') {
                        depth++;
                    } else if (c == '}') {
                        depth--;
                        if (depth == 0) {
                            break;
                        }
                    }
                }
                if (depth > 0) {
                    return true;
                }
            } else {
                // Brace-less single statement: fetch lies within ifEnd after the condition.
                // e.g. if ($db->isResultSet($result)) $row = $db->fetchArray($result);
                if (fetchOffset > ic.condEnd && fetchOffset < ic.ifEnd) {
                    return true;
                }
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
