package org.xoops.support.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xoops.support.XoopsSupportPlugin;

import java.util.Locale;
import java.util.Set;

/**
 * Flags a module {@code .tpl} that sits under {@code templates/} (or {@code blocks/})
 * but is never listed in {@code xoops_version.php} — the inverse of
 * {@link XoopsMissingRegisteredTemplateInspection}.
 */
public final class XoopsUnregisteredTemplateInspection extends LocalInspectionTool {


    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitFile(@NotNull PsiFile file) {
                VirtualFile vf = candidateTpl(file);
                if (vf == null) {
                    return;
                }
                VirtualFile moduleRoot = moduleRootOf(vf);
                String relative = moduleRoot == null ? null : VfsUtilCore.getRelativePath(vf, moduleRoot, '/');
                if (relative == null || !(relative.startsWith("templates/") || relative.startsWith("blocks/"))) {
                    return;
                }
                VirtualFile manifest = moduleRoot == null ? null : moduleRoot.findChild("xoops_version.php");
                PsiFile manifestPsi = manifest == null ? null : file.getManager().findFile(manifest);
                if (relative == null || manifestPsi == null) {
                    return;
                }
                Set<String> registered = registeredTemplates(manifestPsi.getText());
                String key = relative.toLowerCase(Locale.ROOT);
                if (registered.contains(key)) {
                    return;
                }
                PsiElement anchor = file.getFirstChild() != null ? file.getFirstChild() : file;
                holder.registerProblem(
                        anchor,
                        "XOOPS: template is not registered in xoops_version.php: " + relative,
                        new RegisterTemplateQuickFix(relative)
                );
            }
        };
    }

    private static @Nullable VirtualFile candidateTpl(@NotNull PsiFile file) {
        if (!XoopsSupportPlugin.isEnabled(file) || !PhpTextUtil.isPrimaryPsiFile(file)) {
            return null;
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".tpl") || PhpTextUtil.looksLikeVendorOrCache(file)) {
            return null;
        }
        VirtualFile vf = file.getVirtualFile();
        if (vf == null) {
            return null;
        }
        String path = vf.getPath().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (path.contains("/themes/") || path.contains("/templates_c/")) {
            return null;
        }
        return vf;
    }

    /** Registered names as lookup keys; see {@link XoopsManifestTemplates#keys(String)}. */
    static @NotNull Set<String> registeredTemplates(@NotNull String manifestText) {
        return XoopsManifestTemplates.keys(manifestText);
    }

    private static @Nullable VirtualFile moduleRootOf(@NotNull VirtualFile tpl) {
        VirtualFile dir = tpl.getParent();
        while (dir != null) {
            if (dir.findChild("xoops_version.php") != null) {
                return dir;
            }
            dir = dir.getParent();
        }
        return null;
    }
}
