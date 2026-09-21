package org.xoops.support.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xoops.support.XoopsSupportPlugin;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flags a module {@code .tpl} that sits under {@code templates/} (or {@code blocks/})
 * but is never listed in {@code xoops_version.php} — the inverse of
 * {@link XoopsMissingRegisteredTemplateInspection}.
 */
public final class XoopsUnregisteredTemplateInspection extends LocalInspectionTool {

    private static final Pattern REGISTERED_TEMPLATE = Pattern.compile(
            "(?is)['\"](?:file|template)['\"]\\s*=>\\s*['\"]([^'\"]+\\.tpl)['\"]"
    );

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitFile(@NotNull PsiFile file) {
                VirtualFile vf = candidateTpl(file);
                if (vf == null) {
                    return;
                }
                String relative = relativeTemplateName(vf);
                VirtualFile moduleRoot = relative == null ? null : moduleRootOf(vf);
                VirtualFile manifest = moduleRoot == null ? null : moduleRoot.findChild("xoops_version.php");
                PsiFile manifestPsi = manifest == null ? null : file.getManager().findFile(manifest);
                if (relative == null || manifestPsi == null) {
                    return;
                }
                Set<String> registered = registeredTemplates(manifestPsi.getText());
                if (registered.contains(relative.toLowerCase(Locale.ROOT))) {
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

    static @NotNull Set<String> registeredTemplates(@NotNull String manifestText) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = REGISTERED_TEMPLATE.matcher(PhpTextUtil.maskCommentsOnly(manifestText));
        while (m.find()) {
            out.add(m.group(1).replace('\\', '/').toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private static @Nullable String relativeTemplateName(@NotNull VirtualFile tpl) {
        VirtualFile dir = tpl.getParent();
        StringBuilder tail = new StringBuilder(tpl.getName());
        while (dir != null) {
            String dirName = dir.getName().toLowerCase(Locale.ROOT);
            if ("templates".equals(dirName) || "blocks".equals(dirName)) {
                return tail.toString().replace('\\', '/');
            }
            tail.insert(0, dir.getName() + "/");
            dir = dir.getParent();
        }
        return null;
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
