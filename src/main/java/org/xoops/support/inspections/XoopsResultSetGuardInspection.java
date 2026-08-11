package org.xoops.support.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.xoops.support.XoopsSupportPlugin;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flags fetchArray/fetchRow/fetchBoth calls that are not protected by an
 * immediately preceding early-exit {@code isResultSet($result)} guard, or by
 * being nested inside a positive {@code if (isResultSet($result))} block.
 *
 * <p>Failure quick-fix always uses {@code throw} (valid in every PHP scope).
 * We intentionally never emit {@code continue}, which is only legal inside loops
 * and cannot be proven from loose text heuristics.
 */
public final class XoopsResultSetGuardInspection extends LocalInspectionTool {

    private static final Pattern FETCH = Pattern.compile(
            "(\\$[A-Za-z_][\\w]*(?:\\s*->\\s*\\$?[A-Za-z_][\\w]*)*)\\s*->\\s*fetch(Array|Row|Both)\\s*\\(\\s*(\\$[A-Za-z_][\\w]*)",
            Pattern.CASE_INSENSITIVE
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
                // Match fetches outside comments/strings; offsets align with original text.
                String code = PhpTextUtil.maskCommentsAndStrings(text);
                Matcher m = FETCH.matcher(code);
                while (m.find()) {
                    // Recover identifiers from original source (masked region is spaces for strings only).
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
                    // Always throw: never continue (would be invalid outside a loop).
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

    /**
     * Suppress only when the fetch is actually protected:
     * <ul>
     *   <li>An early-exit {@code if (!isResultSet($var)...) { return|throw|... }} ends
     *       immediately before the fetch (only whitespace/semicolons between), or</li>
     *   <li>The fetch sits inside a still-open positive
     *       {@code if (isResultSet($var)...)} block (brace depth ≥ 1).</li>
     * </ul>
     * A bare {@code isResultSet} earlier in the function (sibling branch) does not suppress.
     */
    private static boolean isFetchAlreadyGuarded(@NotNull String before, @NotNull String resultVar) {
        String escaped = Pattern.quote(resultVar);
        // Only "!" applied directly to isResultSet(...) or $var instanceof — not unrelated !$error.
        String negIsResultSet = "!\\s*(?:\\$[A-Za-z_][\\w]*(?:\\s*->\\s*\\$?[A-Za-z_][\\w]*)*\\s*->\\s*)?"
                + "isResultSet\\s*\\(\\s*" + escaped + "\\s*\\)";

        // 1) Early-exit guard immediately preceding the fetch.
        Pattern earlyExit = Pattern.compile(
                "(?is)if\\s*\\([^;{]*" + negIsResultSet + "[^;{]*\\)"
                        + "\\s*\\{[^}]*\\b(return|throw|exit|die|break|continue)\\b[^}]*\\}\\s*$"
        );
        if (earlyExit.matcher(before).find()) {
            return true;
        }

        // Compact single-statement early exit: if (!isResultSet($r)) return;
        Pattern earlyExitOneLiner = Pattern.compile(
                "(?is)if\\s*\\([^;{]*" + negIsResultSet + "[^;{]*\\)"
                        + "\\s*:\\s*\\b(return|throw|exit|die)\\b[^;]*;\\s*$"
                        + "|if\\s*\\([^;{]*" + negIsResultSet + "[^;{]*\\)"
                        + "\\s*\\b(return|throw|exit|die)\\b[^;]*;\\s*$"
        );
        if (earlyExitOneLiner.matcher(before).find()) {
            return true;
        }

        // 2) Nested inside a positive if (isResultSet($var)) { ... fetch ... }
        //    Includes compound conditions such as if (!$error && isResultSet($result)).
        return isInsidePositiveIsResultSetBlock(before, resultVar);
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

    /**
     * Walk from the last {@code if (...isResultSet($var)...)} forward with brace depth.
     * If depth stays &gt; 0 through the end of {@code before}, the fetch is still inside that block.
     */
    private static boolean isInsidePositiveIsResultSetBlock(@NotNull String before, @NotNull String resultVar) {
        String escaped = Pattern.quote(resultVar);
        Pattern openIf = Pattern.compile(
                "(?is)if\\s*\\(([^)]*isResultSet\\s*\\(\\s*" + escaped + "\\s*\\)[^)]*)\\)\\s*\\{"
        );
        Matcher m = openIf.matcher(before);
        int lastOpenEnd = -1;
        while (m.find()) {
            String cond = m.group(1);
            // Skip only when isResultSet($var) itself is negated (early-exit style).
            // Do NOT skip compound positives like: if (!$error && isResultSet($result))
            if (conditionNegatesIsResultSet(cond, resultVar)) {
                continue;
            }
            lastOpenEnd = m.end();
        }
        if (lastOpenEnd < 0) {
            return false;
        }
        // Brace depth from the opening '{' of that if (already consumed by the match end).
        int depth = 1;
        for (int i = lastOpenEnd; i < before.length(); i++) {
            char c = before.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    // Block closed before the fetch → sibling region, not dominating.
                    return false;
                }
            }
        }
        return depth > 0;
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
}
