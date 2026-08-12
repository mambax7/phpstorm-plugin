package org.xoops.support.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.xoops.support.XoopsSupportPlugin;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flags mutating SQL strings passed to -&gt;query( ... ).
 * exec() is only offered for single-argument calls.
 */
public final class XoopsQueryExecInspection extends LocalInspectionTool {

    /** Match method name only on comment-masked code; SQL comes from original text. */
    private static final Pattern QUERY_METHOD = Pattern.compile(
            "->\\s*(query)\\s*\\(",
            Pattern.CASE_INSENSITIVE
    );

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
                // Mask comments only so call structure is found; SQL literals stay in original.
                String code = PhpTextUtil.maskCommentsOnly(text);
                Matcher m = QUERY_METHOD.matcher(code);
                while (m.find()) {
                    int nameStart = m.start(1);
                    int nameEnd = m.end(1);
                    int openParen = m.end() - 1;
                    if (openParen < 0 || openParen >= text.length() || text.charAt(openParen) != '(') {
                        openParen = text.indexOf('(', nameEnd - 1);
                    }
                    if (openParen < 0) {
                        continue;
                    }
                    String sql = PhpTextUtil.firstStringArgContent(text, openParen);
                    if (sql == null) {
                        continue;
                    }
                    String sqlUpper = sql.trim().toUpperCase(Locale.ROOT);
                    if (!(sqlUpper.startsWith("INSERT")
                            || sqlUpper.startsWith("UPDATE")
                            || sqlUpper.startsWith("DELETE")
                            || sqlUpper.startsWith("REPLACE")
                            || sqlUpper.startsWith("TRUNCATE")
                            || sqlUpper.startsWith("ALTER")
                            || sqlUpper.startsWith("DROP")
                            || sqlUpper.startsWith("CREATE"))) {
                        continue;
                    }
                    PsiElement leaf = PhpTextUtil.leafAt(file, nameStart);
                    if (leaf == null) {
                        continue;
                    }
                    String expected = text.substring(nameStart, nameEnd);
                    int commas = PhpTextUtil.countTopLevelCommasInCall(text, openParen);
                    if (commas != 0) {
                        holder.registerProblem(
                                leaf,
                                "XOOPS: mutating SQL must use exec(), not query() "
                                        + "(drop $limit/$start args before renaming multi-arg query() to exec())"
                        );
                        continue;
                    }
                    holder.registerProblem(
                            leaf,
                            "XOOPS: mutating SQL must use exec(), not query()",
                            new ReplaceRangeQuickFix(
                                    "Replace query() with exec()", nameStart, nameEnd, "exec", expected)
                    );
                }
            }
        };
    }
}
