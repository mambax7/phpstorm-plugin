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
 * Flags module/class PHP files that lack a terminating direct-access guard
 * as the first executable statement after the opening PHP tag.
 */
public final class XoopsRootPathGuardInspection extends LocalInspectionTool {

    private static final Pattern OPEN_PHP = Pattern.compile("<\\?php\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern OPEN_ANY = Pattern.compile("<\\?(?:php|=)?", Pattern.CASE_INSENSITIVE);

    /** defined('XOOPS_ROOT_PATH') || exit/die(...); */
    private static final Pattern GUARD_OR = Pattern.compile(
            "(?is)^\\s*defined\\s*\\(\\s*['\"]XOOPS_ROOT_PATH['\"]\\s*\\)\\s*\\|\\|\\s*(?:exit|die)\\s*\\s*(?:\\([^;]*\\))?\\s*;"
    );

    /** if (!defined('XOOPS_ROOT_PATH')) { exit/die(...); } */
    private static final Pattern GUARD_IF = Pattern.compile(
            "(?is)^\\s*if\\s*\\(\\s*!\\s*defined\\s*\\(\\s*['\"]XOOPS_ROOT_PATH['\"]\\s*\\)\\s*\\)\\s*\\{"
                    + "\\s*(?:exit|die)\\s*(?:\\([^;]*\\))?\\s*;\\s*\\}"
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
                if (PhpTextUtil.looksLikeLanguageFile(file)) {
                    return;
                }
                String path = file.getVirtualFile() != null
                        ? file.getVirtualFile().getPath().replace('\\', '/').toLowerCase(Locale.ROOT)
                        : "";
                boolean inModuleOrClass = path.contains("/modules/")
                        || path.contains("/class/")
                        || path.contains("/preloads/")
                        || path.contains("/kernel/");
                if (!inModuleOrClass) {
                    return;
                }
                String text = file.getText();
                if (text == null || text.isBlank()) {
                    return;
                }
                if (!text.contains("<?php") && !text.contains("<?=") && !text.contains("<?")) {
                    return;
                }
                // Only suppress when a proper terminating guard is first executable code.
                if (hasLeadingTerminatingGuard(text)) {
                    return;
                }
                PsiElement anchor = file;
                Matcher open = OPEN_PHP.matcher(text);
                if (open.find()) {
                    PsiElement leaf = PhpTextUtil.leafAt(file, open.start());
                    if (leaf != null) {
                        anchor = leaf;
                    }
                } else {
                    Matcher any = OPEN_ANY.matcher(text);
                    if (any.find()) {
                        PsiElement leaf = PhpTextUtil.leafAt(file, any.start());
                        if (leaf != null) {
                            anchor = leaf;
                        }
                    }
                }
                holder.registerProblem(
                        anchor,
                        "XOOPS: missing direct-access guard - add "
                                + "defined('XOOPS_ROOT_PATH') || exit('Restricted access'); "
                                + "as the first statement after <?php "
                                + "(Alt+Enter or live template: xoguard)",
                        new InsertRootPathGuardQuickFix()
                );
            }
        };
    }

    /**
     * True when, after the first {@code <?php} tag, the first non-comment executable
     * statement is a terminating root-path guard (or-exit or if-exit form).
     */
    static boolean hasLeadingTerminatingGuard(@NotNull String text) {
        Matcher open = OPEN_PHP.matcher(text);
        if (!open.find()) {
            return false;
        }
        String after = text.substring(open.end());
        String code = PhpTextUtil.maskCommentsAndStrings(after).stripLeading();
        // Drop BOM / declare(strict_types=1); if present as first statement
        code = stripLeadingDeclare(code);
        return GUARD_OR.matcher(code).lookingAt() || GUARD_IF.matcher(code).lookingAt();
    }

    private static @NotNull String stripLeadingDeclare(@NotNull String code) {
        Pattern declare = Pattern.compile("(?is)^declare\\s*\\([^;]*\\)\\s*;\\s*");
        Matcher m = declare.matcher(code);
        if (m.find()) {
            return code.substring(m.end()).stripLeading();
        }
        return code;
    }
}
