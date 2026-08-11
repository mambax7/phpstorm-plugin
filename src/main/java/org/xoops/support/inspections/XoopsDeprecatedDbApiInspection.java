package org.xoops.support.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
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
 * Flags deprecated queryF() and quoteString().
 * exec() is only offered for single-argument calls (exec accepts SQL only).
 */
public final class XoopsDeprecatedDbApiInspection extends LocalInspectionTool {

    private static final Pattern DEPRECATED = Pattern.compile(
            "->\\s*(queryF|quoteString)\\s*\\(",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern QUERY_F_SQL = Pattern.compile(
            "->\\s*queryF\\s*\\(\\s*([\"'])([\\s\\S]*?)\\1",
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
                Matcher m = DEPRECATED.matcher(code);
                while (m.find()) {
                    String method = m.group(1);
                    int nameStart = m.start(1);
                    int nameEnd = m.end(1);
                    PsiElement leaf = PhpTextUtil.leafAt(file, nameStart);
                    if (leaf == null) {
                        continue;
                    }
                    String expectedName = text.substring(nameStart, nameEnd);
                    if (method.equalsIgnoreCase("quoteString")) {
                        holder.registerProblem(
                                leaf,
                                "XOOPS: quoteString() is deprecated - use quote()",
                                new ReplaceRangeQuickFix(
                                        "Replace quoteString() with quote()",
                                        nameStart, nameEnd, "quote", expectedName)
                        );
                        continue;
                    }
                    // Find '(' after method name in original text
                    int openParen = text.indexOf('(', nameEnd - 1);
                    if (openParen < 0) {
                        openParen = m.end() - 1;
                    }
                    int extraCommas = PhpTextUtil.countTopLevelCommasInCall(text, openParen);
                    boolean singleArg = extraCommas == 0;

                    String message = "XOOPS: queryF() is deprecated - use query() for SELECT, exec() for mutations";
                    Matcher sqlMatch = QUERY_F_SQL.matcher(code.substring(m.start()));
                    boolean knownMutation = false;
                    boolean knownSelect = false;
                    if (sqlMatch.lookingAt()) {
                        String sql = sqlMatch.group(2).trim().toUpperCase(Locale.ROOT);
                        // group 2 is masked if it was a string — recover from original
                        int sqlStart = m.start() + sqlMatch.start(2);
                        int sqlEnd = m.start() + sqlMatch.end(2);
                        if (sqlStart >= 0 && sqlEnd <= text.length()) {
                            sql = text.substring(sqlStart, sqlEnd).trim().toUpperCase(Locale.ROOT);
                        }
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
                            "Replace queryF() with query()", nameStart, nameEnd, "query", expectedName);
                    // exec($sql) only — never leave $limit/$start on exec()
                    LocalQuickFix toExec = singleArg
                            ? new ReplaceRangeQuickFix(
                            "Replace queryF() with exec()", nameStart, nameEnd, "exec", expectedName)
                            : null;

                    if (knownMutation && toExec != null) {
                        holder.registerProblem(leaf, message, toExec, toQuery);
                    } else if (knownMutation) {
                        holder.registerProblem(
                                leaf,
                                message + " (multi-arg call: rename to query() only, or drop limit args for exec())",
                                toQuery
                        );
                    } else if (knownSelect) {
                        holder.registerProblem(leaf, message, toQuery);
                    } else if (toExec != null) {
                        holder.registerProblem(leaf, message, toQuery, toExec);
                    } else {
                        holder.registerProblem(leaf, message + " (multi-arg: use query() rename only)", toQuery);
                    }
                }
            }
        };
    }
}
