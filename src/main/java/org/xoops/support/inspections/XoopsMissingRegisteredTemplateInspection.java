package org.xoops.support.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.xoops.support.XoopsSupportPlugin;


/**
 * Flags templates listed in xoops_version.php that are missing on disk.
 */
public final class XoopsMissingRegisteredTemplateInspection extends LocalInspectionTool {


    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitFile(@NotNull PsiFile file) {
                if (!XoopsSupportPlugin.isEnabled(file) || !PhpTextUtil.isPrimaryPsiFile(file)) {
                    return;
                }
                if (!"xoops_version.php".equalsIgnoreCase(file.getName()) || PhpTextUtil.looksLikeVendorOrCache(file)) {
                    return;
                }
                VirtualFile vf = file.getVirtualFile();
                if (vf == null || vf.getParent() == null) {
                    return;
                }
                VirtualFile moduleRoot = vf.getParent();
                String text = file.getText();
                // Comments only: the registration key and file name are string literals.
                String code = PhpTextUtil.maskCommentsOnly(text);
                for (XoopsManifestTemplates.Registration reg : XoopsManifestTemplates.find(code)) {
                    String template = reg.name();
                    String expected = XoopsManifestTemplates.diskPath(template, reg.block());
                    String actual = XoopsTemplatePaths.existingPath(moduleRoot, expected);
                    if (actual != null && !expected.equals(actual)) {
                        PsiElement leaf = PhpTextUtil.leafAt(file, reg.nameOffset());
                        if (leaf != null) {
                            holder.registerProblem(leaf,
                                    "XOOPS: template filename case mismatch: " + expected + " (on disk: " + actual + ")");
                        }
                    } else if (actual == null) {
                        PsiElement leaf = PhpTextUtil.leafAt(file, reg.nameOffset());
                        if (leaf != null) {
                            holder.registerProblem(
                                    leaf,
                                    "XOOPS: registered template missing on disk: " + template,
                                    new CreateMissingTemplateQuickFix(template, reg.block())
                            );
                        }
                    }
                }
            }
        };
    }

}
