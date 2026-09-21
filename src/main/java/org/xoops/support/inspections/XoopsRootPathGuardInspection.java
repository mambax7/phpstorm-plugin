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
 * Flags include-only PHP files that lack a terminating direct-access guard
 * as the first executable statement after {@code <?php} / {@code namespace} / {@code use}.
 *
 * <p>Entry points (admin pages, mainfile includes) and directory-protection 404 stubs
 * are skipped — see {@link XoopsRootPathGuardPolicy}.
 */
public final class XoopsRootPathGuardInspection extends LocalInspectionTool {

    private static final Pattern OPEN_PHP = Pattern.compile("<\\?php\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern OPEN_ANY = Pattern.compile("<\\?(?:php|=)?", Pattern.CASE_INSENSITIVE);

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitFile(@NotNull PsiFile file) {
                if (!XoopsSupportPlugin.isEnabled(file) || !PhpTextUtil.isPrimaryPsiFile(file)) {
                    return;
                }
                if (!PhpTextUtil.isPhpFile(file) || PhpTextUtil.looksLikeVendorOrCache(file)) {
                    return;
                }
                String path = file.getVirtualFile() != null
                        ? file.getVirtualFile().getPath()
                        : file.getName();
                String text = file.getText();
                if (text == null || text.isBlank()) {
                    return;
                }
                if (!XoopsRootPathGuardPolicy.requiresGuard(path, text)) {
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
                String message = "XOOPS: missing direct-access guard - add "
                        + "defined('XOOPS_ROOT_PATH') || exit('Restricted access'); "
                        + "as the first statement after <?php "
                        + "(after namespace in namespaced files; Alt+Enter or live template: xoguard)";
                if (XoopsRootPathGuardPolicy.canInsertGuard(text)) {
                    holder.registerProblem(anchor, message, new InsertRootPathGuardQuickFix());
                } else {
                    // HTML before the first PHP tag: report, but do not offer a no-op fix.
                    holder.registerProblem(anchor, message);
                }
            }
        };
    }

    /**
     * Delegates to {@link XoopsRootPathGuardPolicy#hasLeadingTerminatingGuard(String)}.
     */
    static boolean hasLeadingTerminatingGuard(@NotNull String text) {
        return XoopsRootPathGuardPolicy.hasLeadingTerminatingGuard(text);
    }
}
