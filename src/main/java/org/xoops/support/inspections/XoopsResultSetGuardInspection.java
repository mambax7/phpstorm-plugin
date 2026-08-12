package org.xoops.support.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.psi.elements.Statement;
import org.jetbrains.annotations.NotNull;
import org.xoops.support.XoopsSupportPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flags fetchArray/fetchRow/fetchBoth calls that are not proven-safe by a
 * dominating isResultSet($result) check.
 *
 * <p>Conservative path rules (no full PHP CFG):
 * <ul>
 *   <li>Positive {@code if}: must mention isResultSet($var), must not negate it,
 *       and must have no {@code ||}/{@code or} at any parenthesis depth (rejects
 *       {@code isResultSet($r) || $fallback} and parenthesized forms).</li>
 *   <li>Early-exit {@code if}: must negate isResultSet($var) (including
 *       {@code !($db->isResultSet($r))}), must have no {@code &&}/{@code and}
 *       at any depth (rejects {@code !isResultSet($r) && $strict}), and body must
 *       be a single exit statement (no nested blocks).</li>
 * </ul>
 *
 * <p>Quick fix inserts a throw-guard only at a PSI statement boundary; otherwise
 * the problem is reported without a fix.
 */
public final class XoopsResultSetGuardInspection extends LocalInspectionTool {

    private static final Pattern FETCH = Pattern.compile(
            "(\\$[A-Za-z_][\\w]*(?:\\s*->\\s*\\$?[A-Za-z_][\\w]*)*)\\s*->\\s*fetch(Array|Row|Both)\\s*\\(\\s*(\\$[A-Za-z_][\\w]*)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern IF_KEYWORD = Pattern.compile(
            "\\b(?:else\\s+)?if\\s*\\(|\\belseif\\s*\\(",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern IS_RESULT_SET_CALL = Pattern.compile(
            "(?is)isResultSet\\s*\\(\\s*(\\$[A-Za-z_][\\w]*)\\s*\\)"
    );

    /**
     * Negation of isResultSet for $var, with optional outer parentheses:
     * {@code !isResultSet($r)}, {@code !$db->isResultSet($r)}, {@code !($db->isResultSet($r))}.
     */
    private static final Pattern NEG_IS_RESULT_SET = Pattern.compile(
            "(?is)!\\s*\\(?\\s*"
                    + "(?:\\$[A-Za-z_][\\w]*(?:\\s*->\\s*\\$?[A-Za-z_][\\w]*)*\\s*->\\s*)?"
                    + "isResultSet\\s*\\(\\s*(\\$[A-Za-z_][\\w]*)\\s*\\)"
                    + "\\s*\\)?"
    );

    private static final Pattern NEG_INSTANCEOF = Pattern.compile(
            "(?is)!\\s*\\(?\\s*(\\$[A-Za-z_][\\w]*)\\s*instanceof"
    );

    /** Single exit statement only (no nested control structure). */
    private static final Pattern SIMPLE_EARLY_EXIT = Pattern.compile(
            "(?is)^\\s*(return|throw|exit|die|break|continue)\\b[^;{]*;?\\s*$"
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
                    String message =
                            "XOOPS: call isResultSet($result) (and prefer mysqli_result check) before fetch*";
                    Statement stmt = PsiTreeUtil.getParentOfType(leaf, Statement.class, false);
                    if (stmt == null) {
                        // No safe statement boundary — report without auto-fix.
                        holder.registerProblem(leaf, message);
                        continue;
                    }
                    int insertAt = stmt.getTextRange().getStartOffset();
                    String indentGuess = guessIndent(text, insertAt);
                    String block = indentGuess + "if (!" + dbExpr + "->isResultSet(" + resultVar
                            + ") || !" + resultVar + " instanceof \\mysqli_result) {\n"
                            + indentGuess + "    " + FAIL_ACTION + "\n"
                            + indentGuess + "}\n";
                    String expectedAt = text.substring(
                            insertAt,
                            Math.min(text.length(), insertAt + Math.min(32, stmt.getTextLength()))
                    );
                    holder.registerProblem(
                            leaf,
                            message,
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

    private static boolean isFetchAlreadyGuarded(
            @NotNull String code,
            int fetchOffset,
            @NotNull String resultVar,
            @NotNull List<IfCond> allIfs
    ) {
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
        // Early-exit: only when every fall-through path implies isResultSet($var).
        // Requires pure-enough negation (no top-level &&) + single exit body.
        if (last.ifEnd <= fetchOffset
                && isOnlyWhitespace(code.substring(last.ifEnd, fetchOffset))
                && isSafeEarlyExitCondition(last.condition, resultVar)
                && bodyIsSimpleEarlyExit(code, last)) {
            return true;
        }

        return isInsidePositiveIsResultSetGuard(code, fetchOffset, resultVar, before);
    }

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

    /**
     * Only a single top-level exit statement counts (no nested if/blocks).
     * Avoids treating {@code if (!isResultSet($r)) { if ($x) return; }} as safe.
     */
    private static boolean bodyIsSimpleEarlyExit(@NotNull String text, @NotNull IfCond ic) {
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
        String body = text.substring(from, to).strip();
        if (body.contains("{")) {
            return false;
        }
        return SIMPLE_EARLY_EXIT.matcher(body).matches();
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
     * True when the condition contains {@code ||}/{@code or} (if {@code orOp}) or
     * {@code &&}/{@code and} (if not), at any parenthesis depth.
     *
     * <p>Any-depth is intentional: parenthesized {@code (isResultSet($r) || $x)} or
     * {@code (!isResultSet($r) && $strict)} must not count as a proven-safe guard.
     */
    private static boolean hasBoolOpAnywhere(@NotNull String cond, boolean orOp) {
        for (int i = 0; i < cond.length(); i++) {
            char c = cond.charAt(i);
            if (orOp) {
                if (c == '|' && i + 1 < cond.length() && cond.charAt(i + 1) == '|') {
                    return true;
                }
                if (isWordAt(cond, i, "or")) {
                    return true;
                }
            } else {
                if (c == '&' && i + 1 < cond.length() && cond.charAt(i + 1) == '&') {
                    return true;
                }
                if (isWordAt(cond, i, "and")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isWordAt(@NotNull String s, int i, @NotNull String word) {
        int n = word.length();
        if (i + n > s.length()) {
            return false;
        }
        if (!s.regionMatches(true, i, word, 0, n)) {
            return false;
        }
        // Java identifier parts (incl. '_') so "or" does not match inside "$or_flag".
        boolean leftOk = i == 0 || !Character.isJavaIdentifierPart(s.charAt(i - 1));
        boolean rightOk = i + n >= s.length() || !Character.isJavaIdentifierPart(s.charAt(i + n));
        return leftOk && rightOk;
    }

    /**
     * Positive {@code if (cond) { fetch }}: every path into the body must imply
     * isResultSet($var). Rejects any OR ({@code isResultSet || fallback}, including
     * parenthesized forms).
     */
    private static boolean isSafePositiveGuardCondition(@NotNull String cond, @NotNull String resultVar) {
        if (!conditionMentionsIsResultSet(cond, resultVar)) {
            return false;
        }
        if (conditionNegatesIsResultSet(cond, resultVar)) {
            return false;
        }
        // Reject OR-paths at any depth: isResultSet($r) || $fallback / (isResultSet($r) || $x)
        return !hasBoolOpAnywhere(cond, true);
    }

    /**
     * Early-exit {@code if (cond) { return/throw }} before fetch: every fall-through
     * path must imply isResultSet($var). Requires a proven negation of isResultSet
     * and rejects any AND ({@code !isResultSet($r) && $strict}, including
     * parenthesized forms) where exit is conditional.
     */
    private static boolean isSafeEarlyExitCondition(@NotNull String cond, @NotNull String resultVar) {
        if (!conditionNegatesIsResultSet(cond, resultVar)) {
            return false;
        }
        // Reject AND-paths at any depth that make the exit conditional on other predicates.
        return !hasBoolOpAnywhere(cond, false);
    }

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
            if (!isSafePositiveGuardCondition(ic.condition, resultVar)) {
                continue;
            }

            if (ic.openBrace >= 0) {
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
            } else if (fetchOffset > ic.condEnd && fetchOffset < ic.ifEnd) {
                // Brace-less: if (isResultSet($r)) $row = $db->fetch...;
                return true;
            }
        }
        return false;
    }

    private static String guessIndent(String text, int offset) {
        int start = lineStart(text, offset);
        int i = start;
        while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) {
            i++;
        }
        return text.substring(start, i);
    }

    private static int lineStart(String text, int offset) {
        int i = text.lastIndexOf('\n', Math.max(0, offset - 1));
        return i < 0 ? 0 : i + 1;
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
