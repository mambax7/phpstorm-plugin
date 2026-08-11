package org.xoops.support.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flags deprecated queryF() and quoteString().
 */
public final class XoopsDeprecatedDbApiInspection extends LocalInspectionTool {

    private static final Pattern DEPRECATED = Pattern.compile(
            "->\\s*(queryF|quoteString)\\s*\\(",
            Pattern.CASE_INSENSITIVE
    );

    // For queryF("INSERT ...") classify mutation
    private static final Pattern QUERY_F_SQL = Pattern.compile(
            "->\\s*queryF\\s*\\(\\s*([\"'])([\\s\\S]*?)\\1",
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
                Matcher m = DEPRECATED.matcher(text);
                while (m.find()) {
                    String method = m.group(1);
                    int nameStart = m.start(1);
                    int nameEnd = m.end(1);
                    PsiElement leaf = PhpTextUtil.leafAt(file, nameStart);
                    if (leaf == null) {
                        continue;
                    }
                    if (method.equalsIgnoreCase("quoteString")) {
                        holder.registerProblem(
                                leaf,
                                "XOOPS: quoteString() is deprecated - use quote()",
                                new ReplaceRangeQuickFix("Replace quoteString() with quote()", nameStart, nameEnd, "quote")
                        );
                        continue;
                    }
                    // queryF — prefer classified replacement; always offer both when SQL is unclear
                    String message = "XOOPS: queryF() is deprecated - use query() for SELECT, exec() for mutations";
                    Matcher sqlMatch = QUERY_F_SQL.matcher(text.substring(m.start()));
                    boolean knownMutation = false;
                    boolean knownSelect = false;
                    if (sqlMatch.lookingAt()) {
                        String sql = sqlMatch.group(2).trim().toUpperCase(Locale.ROOT);
                        if (sql.startsWith("INSERT") || sql.startsWith("UPDATE") || sql.startsWith("DELETE")
                                || sql.startsWith("REPLACE") || sql.startsWith("TRUNCATE")
                                || sql.startsWith("ALTER") || sql.startsWith("DROP") || sql.startsWith("CREATE")) {
                            knownMutation = true;
                        } else if (sql.startsWith("SELECT") || sql.startsWith("SHOW") || sql.startsWith("DESCRIBE")
                                || sql.startsWith("EXPLAIN")) {
                            knownSelect = true;
                        }
                    }
                    LocalQuickFix toQuery = new ReplaceRangeQuickFix(
                            "Replace queryF() with query()", nameStart, nameEnd, "query");
                    LocalQuickFix toExec = new ReplaceRangeQuickFix(
                            "Replace queryF() with exec()", nameStart, nameEnd, "exec");
                    if (knownMutation) {
                        holder.registerProblem(leaf, message, toExec, toQuery);
                    } else if (knownSelect) {
                        holder.registerProblem(leaf, message, toQuery, toExec);
                    } else {
                        // Variable SQL or non-literal: offer both (common peer-plugin behavior)
                        holder.registerProblem(leaf, message, toQuery, toExec);
                    }
                }
            }
        };
    }
}
