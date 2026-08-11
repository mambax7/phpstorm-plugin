package org.xoops.support.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flags mutating SQL strings passed to ->query( ... ).
 */
public final class XoopsQueryExecInspection extends LocalInspectionTool {

    // Captures method name "query" so we can rename it to exec.
    private static final Pattern QUERY_CALL = Pattern.compile(
            "->\\s*(query)\\s*\\(\\s*([\"'])([\\s\\S]*?)\\2",
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
                Matcher m = QUERY_CALL.matcher(text);
                while (m.find()) {
                    String sql = m.group(3).trim().toUpperCase(Locale.ROOT);
                    if (sql.startsWith("INSERT")
                            || sql.startsWith("UPDATE")
                            || sql.startsWith("DELETE")
                            || sql.startsWith("REPLACE")
                            || sql.startsWith("TRUNCATE")
                            || sql.startsWith("ALTER")
                            || sql.startsWith("DROP")
                            || sql.startsWith("CREATE")) {
                        int nameStart = m.start(1);
                        int nameEnd = m.end(1);
                        PsiElement leaf = PhpTextUtil.leafAt(file, nameStart);
                        if (leaf == null) {
                            continue;
                        }
                        holder.registerProblem(
                                leaf,
                                "XOOPS: mutating SQL must use exec(), not query()",
                                new ReplaceRangeQuickFix("Replace query() with exec()", nameStart, nameEnd, "exec")
                        );
                    }
                }
            }
        };
    }
}
