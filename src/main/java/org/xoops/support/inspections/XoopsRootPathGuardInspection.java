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
 * Flags module/class PHP files that lack the classic direct-access guard.
 */
public final class XoopsRootPathGuardInspection extends LocalInspectionTool {

    private static final Pattern OPEN_TAG = Pattern.compile("<\\?(?:php|=)?", Pattern.CASE_INSENSITIVE);

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitFile(@NotNull PsiFile file) {
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
                if (text.contains("XOOPS_ROOT_PATH") && text.contains("defined")) {
                    return;
                }
                if (!text.contains("<?php") && !text.contains("<?=") && !text.contains("<?")) {
                    return;
                }
                PsiElement anchor = file;
                Matcher open = OPEN_TAG.matcher(text);
                if (open.find()) {
                    PsiElement leaf = PhpTextUtil.leafAt(file, open.start());
                    if (leaf != null) {
                        anchor = leaf;
                    }
                }
                holder.registerProblem(
                        anchor,
                        "XOOPS: missing direct-access guard - add "
                                + "defined('XOOPS_ROOT_PATH') || exit('Restricted access'); "
                                + "(Alt+Enter or live template: xoguard)",
                        new InsertRootPathGuardQuickFix()
                );
            }
        };
    }
}
