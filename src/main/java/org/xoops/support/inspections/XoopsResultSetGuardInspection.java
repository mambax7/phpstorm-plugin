package org.xoops.support.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flags fetchArray/fetchRow/fetchBoth calls without a nearby isResultSet check
 * that would prevent the fetch from running on a failed query.
 *
 * <p>Note: full PHP CFG dominance is not available without a deep PhpStorm PSI
 * analysis pass. Suppression uses a comment-stripped preceding window and a
 * variable-bound {@code isResultSet($result)} match. Failure branches use
 * {@code throw}, which is valid in methods, constructors, loops, and file scope.
 */
public final class XoopsResultSetGuardInspection extends LocalInspectionTool {

    private static final Pattern FETCH = Pattern.compile(
            "(\\$[A-Za-z_][\\w]*(?:\\s*->\\s*\\$?[A-Za-z_][\\w]*)*)\\s*->\\s*fetch(Array|Row|Both)\\s*\\(\\s*(\\$[A-Za-z_][\\w]*)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern HASH_COMMENT = Pattern.compile("#[^\\n]*");

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitFile(@NotNull PsiFile file) {
                if (!PhpTextUtil.isPhpFile(file) || PhpTextUtil.looksLikeVendorOrCache(file)) {
                    return;
                }
                String text = file.getText();
                Matcher m = FETCH.matcher(text);
                while (m.find()) {
                    String dbExpr = m.group(1).replaceAll("\\s+", "");
                    String resultVar = m.group(3);
                    int start = Math.max(0, m.start() - 600);
                    String window = stripPhpComments(text.substring(start, m.start()));
                    // Variable-bound guard only (ignore unrelated isResultSet / comments).
                    if (hasDominatingIsResultSetGuard(window, resultVar)) {
                        continue;
                    }
                    PsiElement leaf = PhpTextUtil.leafAt(file, m.start());
                    if (leaf == null) {
                        continue;
                    }
                    String indentGuess = guessIndent(text, m.start());
                    // throw is valid in constructors, void methods, loops, and file scope,
                    // and prevents fetch* from executing on a failed query.
                    String failAction = failureAction(text, m.start(), indentGuess);
                    String block = indentGuess + "if (!" + dbExpr + "->isResultSet(" + resultVar
                            + ") || !" + resultVar + " instanceof \\mysqli_result) {\n"
                            + indentGuess + "    " + failAction + "\n"
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
     * True when a prior non-comment isResultSet($resultVar) appears to guard the fetch.
     * Lightweight approximation of “dominates fetch” without full PHP CFG.
     */
    private static boolean hasDominatingIsResultSetGuard(@NotNull String window, @NotNull String resultVar) {
        String escapedVar = Pattern.quote(resultVar);
        Pattern pos = Pattern.compile(
                "isResultSet\\s*\\(\\s*" + escapedVar + "\\s*\\)",
                Pattern.CASE_INSENSITIVE
        );
        Pattern neg = Pattern.compile(
                "!\\s*[\\w$\\->\\s]*isResultSet\\s*\\(\\s*" + escapedVar + "\\s*\\)"
                        + "|!\\s*" + escapedVar + "\\s*instanceof",
                Pattern.CASE_INSENSITIVE
        );
        // Either positive check wrapping the fetch later, or negative early-exit pattern.
        return pos.matcher(window).find() || neg.matcher(window).find();
    }

    /**
     * Prefer {@code continue} inside obvious loop bodies; otherwise {@code throw}
     * (safe in constructors / void methods / file scope).
     */
    private static @NotNull String failureAction(@NotNull String text, int offset, @NotNull String indent) {
        String before = stripPhpComments(text.substring(Math.max(0, offset - 800), offset));
        // Heuristic: last loop keyword after last function/method opening is still “open”.
        int lastFor = Math.max(
                Math.max(before.lastIndexOf("foreach"), before.lastIndexOf("for (")),
                Math.max(before.lastIndexOf("while ("), before.lastIndexOf("do {"))
        );
        int lastFunction = Math.max(
                Math.max(before.lastIndexOf("function "), before.lastIndexOf("function(")),
                before.lastIndexOf("function\t")
        );
        if (lastFor > lastFunction && lastFor >= 0) {
            // Inside a loop-like region relative to the enclosing function start.
            return "continue;";
        }
        if (before.contains("__construct") && before.lastIndexOf("__construct") > lastFunction - 20) {
            // Constructor: bare return; is fine; throw is clearer for failed DB work.
            return "throw new \\RuntimeException('Database query failed');";
        }
        // Default: throw aborts before fetch in every scope without inventing a return type.
        return "throw new \\RuntimeException('Database query failed');";
    }

    /** Remove //, #, and /* *\/ comments so comment text cannot suppress findings. */
    private static @NotNull String stripPhpComments(@NotNull String text) {
        String s = BLOCK_COMMENT.matcher(text).replaceAll(" ");
        s = LINE_COMMENT.matcher(s).replaceAll(" ");
        s = HASH_COMMENT.matcher(s).replaceAll(" ");
        return s;
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
