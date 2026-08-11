package org.xoops.support.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Flags module/class PHP files that lack the classic direct-access guard.
 */
public final class XoopsRootPathGuardInspection extends LocalInspectionTool {

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitFile(@NotNull PsiFile file) {
                if (!PhpTextUtil.isPhpFile(file) || PhpTextUtil.looksLikeVendorOrCache(file)) {
                    return;
                }
                // Language packs and pure define files often skip the guard.
                if (PhpTextUtil.looksLikeLanguageFile(file)) {
                    return;
                }
                String path = file.getVirtualFile() != null
                        ? file.getVirtualFile().getPath().replace('\\', '/').toLowerCase()
                        : "";
                // Focus on module/class trees; skip tests bootstrap noise lightly.
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
                // Already has a guard.
                if (text.contains("XOOPS_ROOT_PATH") && text.contains("defined")) {
                    return;
                }
                // Skip empty stubs / pure interfaces without side effects (heuristic).
                if (!text.contains("<?php") && !text.contains("<?=")) {
                    return;
                }
                holder.registerProblem(
                        file,
                        "XOOPS: missing direct-access guard - add "
                                + "defined('XOOPS_ROOT_PATH') || exit('Restricted access'); "
                                + "(Alt+Enter or live template: xoguard)",
                        new InsertRootPathGuardQuickFix()
                );
            }
        };
    }
}
