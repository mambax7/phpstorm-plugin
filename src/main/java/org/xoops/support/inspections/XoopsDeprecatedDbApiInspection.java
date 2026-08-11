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
                // Comments masked so commented-out calls are ignored; SQL read from original.
                String code = PhpTextUtil.maskCommentsOnly(text);
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

                    int openParen = m.end() - 1;
                    if (openParen < 0 || openParen >= text.length() || text.charAt(openParen) != '(') {
                        openParen = text.indexOf('(', nameEnd - 1);
                    }
                    if (openParen < 0) {
                        continue;
                    }
                    int extraCommas = PhpTextUtil.countTopLevelCommasInCall(text, openParen);
                    boolean singleArg = extraCommas == 0;

                    String sql = PhpTextUtil.firstStringArgContent(text, openParen);
                    boolean knownMutation = false;
                    boolean knownSelect = false;
                    if (sql != null) {
                        String upper = sql.trim().toUpperCase(Locale.ROOT);
                        if (upper.startsWith("INSERT") || upper.startsWith("UPDATE") || upper.startsWith("DELETE")
                                || upper.startsWith("REPLACE") || upper.startsWith("TRUNCATE")
                                || upper.startsWith("ALTER") || upper.startsWith("DROP") || upper.startsWith("CREATE")) {
                            knownMutation = true;
                        } else if (upper.startsWith("SELECT") || upper.startsWith("SHOW")
                                || upper.startsWith("DESCRIBE") || upper.startsWith("EXPLAIN")) {
                            knownSelect = true;
                        }
                    }

                    String message = "XOOPS: queryF() is deprecated - use query() for SELECT, exec() for mutations";
                    LocalQuickFix toQuery = new ReplaceRangeQuickFix(
                            "Replace queryF() with query()", nameStart, nameEnd, "query", expectedName);
                    // exec($sql) only — never leave $limit/$start on exec()
                    LocalQuickFix toExec = singleArg
                            ? new ReplaceRangeQuickFix(
                            "Replace queryF() with exec()", nameStart, nameEnd, "exec", expectedName)
                            : null;

                    if (knownSelect) {
                        // Never offer exec() for SELECT (or other read) SQL.
                        holder.registerProblem(leaf, message, toQuery);
                    } else if (knownMutation && toExec != null) {
                        holder.registerProblem(leaf, message, toExec, toQuery);
                    } else if (knownMutation) {
                        holder.registerProblem(
                                leaf,
                                message + " (multi-arg call: rename to query() only, or drop limit args for exec())",
                                toQuery
                        );
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
