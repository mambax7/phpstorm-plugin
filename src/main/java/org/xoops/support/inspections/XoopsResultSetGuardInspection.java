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
 * Flags fetchArray/fetchRow/fetchBoth calls without a nearby isResultSet check.
 */
public final class XoopsResultSetGuardInspection extends LocalInspectionTool {

    private static final Pattern FETCH = Pattern.compile(
            "(\\$[A-Za-z_][\\w]*(?:\\s*->\\s*\\$?[A-Za-z_][\\w]*)*)\\s*->\\s*fetch(Array|Row|Both)\\s*\\(\\s*(\\$[A-Za-z_][\\w]*)",
            Pattern.CASE_INSENSITIVE
    );

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
                    int start = Math.max(0, m.start() - 400);
                    String window = text.substring(start, m.start()).replaceAll("\\s+", "");
                    // Only suppress when this result variable is already guarded nearby.
                    if (window.contains("isResultSet(" + resultVar + ")")) {
                        continue;
                    }
                    PsiElement leaf = PhpTextUtil.leafAt(file, m.start());
                    if (leaf == null) {
                        continue;
                    }
                    String indentGuess = guessIndent(text, m.start());
                    // Scope-neutral body: "return null" is unsafe in constructors / void methods.
                    String block = indentGuess + "if (!" + dbExpr + "->isResultSet(" + resultVar
                            + ") || !" + resultVar + " instanceof \\mysqli_result) {\n"
                            + indentGuess + "    // TODO: handle failed query (return, continue, or throw)\n"
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
