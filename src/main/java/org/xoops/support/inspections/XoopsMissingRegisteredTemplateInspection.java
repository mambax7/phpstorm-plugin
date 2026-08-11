package org.xoops.support.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.xoops.support.XoopsSupportPlugin;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flags templates listed in xoops_version.php that are missing on disk.
 */
public final class XoopsMissingRegisteredTemplateInspection extends LocalInspectionTool {

    private static final Pattern REGISTERED_TEMPLATE = Pattern.compile(
            "(?is)['\"](?:file|template)['\"]\\s*=>\\s*['\"]([^'\"]+\\.tpl)['\"]"
    );

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitFile(@NotNull PsiFile file) {
                if (!XoopsSupportPlugin.isEnabled(file)) {
                    return;
                }
                if (!"xoops_version.php".equalsIgnoreCase(file.getName())) {
                    return;
                }
                VirtualFile vf = file.getVirtualFile();
                if (vf == null || vf.getParent() == null) {
                    return;
                }
                VirtualFile moduleRoot = vf.getParent();
                String text = file.getText();
                String code = PhpTextUtil.maskCommentsAndStrings(text);
                Matcher m = REGISTERED_TEMPLATE.matcher(code);
                while (m.find()) {
                    String template = m.group(1).replace('\\', '/');
                    boolean exists = childExists(moduleRoot, "templates/" + template)
                            || childExists(moduleRoot, "blocks/" + template)
                            || childExists(moduleRoot, template);
                    if (!exists) {
                        PsiElement leaf = PhpTextUtil.leafAt(file, m.start(1));
                        if (leaf != null) {
                            holder.registerProblem(
                                    leaf,
                                    "XOOPS: registered template missing on disk: " + template,
                                    new CreateMissingTemplateQuickFix(template)
                            );
                        }
                    }
                }
            }
        };
    }

    private static boolean childExists(VirtualFile root, String relative) {
        String[] parts = relative.split("/");
        VirtualFile cur = root;
        for (String part : parts) {
            if (cur == null) {
                return false;
            }
            cur = cur.findChild(part);
        }
        return cur != null && !cur.isDirectory();
    }
}
