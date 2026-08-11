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

    private static final Pattern QUERY_CALL = Pattern.compile(
            "->\\s*(query)\\s*\\(\\s*([\"'])([\\s\\S]*?)\\2",
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
                String code = PhpTextUtil.maskCommentsAndStrings(text);
                Matcher m = QUERY_CALL.matcher(code);
                while (m.find()) {
                    // Recover SQL from original (code has strings masked).
                    int openParen = text.indexOf('(', m.end(1) - 1);
                    if (openParen < 0) {
                        continue;
                    }
                    int commas = PhpTextUtil.countTopLevelCommasInCall(text, openParen);
                    int sqlStart = m.start(3);
                    int sqlEnd = m.end(3);
                    if (sqlStart < 0 || sqlEnd > text.length()) {
                        continue;
                    }
                    String sql = text.substring(sqlStart, sqlEnd).trim().toUpperCase(Locale.ROOT);
                    if (!(sql.startsWith("INSERT")
                            || sql.startsWith("UPDATE")
                            || sql.startsWith("DELETE")
                            || sql.startsWith("REPLACE")
                            || sql.startsWith("TRUNCATE")
                            || sql.startsWith("ALTER")
                            || sql.startsWith("DROP")
                            || sql.startsWith("CREATE"))) {
                        continue;
                    }
                    int nameStart = m.start(1);
                    int nameEnd = m.end(1);
                    PsiElement leaf = PhpTextUtil.leafAt(file, nameStart);
                    if (leaf == null) {
                        continue;
                    }
                    String expected = text.substring(nameStart, nameEnd);
                    if (commas != 0) {
                        // Multi-arg query($sql, $limit, $start) — cannot rename to exec() safely.
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
