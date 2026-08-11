package org.xoops.support.inspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Creates templates/{name} under the module that owns xoops_version.php.
 */
public final class CreateMissingTemplateQuickFix implements LocalQuickFix {

    private final String templateName;

    public CreateMissingTemplateQuickFix(@NotNull String templateName) {
        this.templateName = templateName.replace('\\', '/');
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Create missing registered template";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiFile file = descriptor.getPsiElement().getContainingFile();
        if (file == null || file.getVirtualFile() == null || file.getVirtualFile().getParent() == null) {
            return;
        }
        VirtualFile moduleRoot = file.getVirtualFile().getParent();
        try {
            WriteAction.runAndWait(() -> {
                VirtualFile templates = moduleRoot.findChild("templates");
                if (templates == null) {
                    templates = moduleRoot.createChildDirectory(this, "templates");
                }
                // Support nested names like admin/list.tpl
                String[] parts = templateName.split("/");
                VirtualFile dir = templates;
                for (int i = 0; i < parts.length - 1; i++) {
                    VirtualFile next = dir.findChild(parts[i]);
                    if (next == null) {
                        next = dir.createChildDirectory(this, parts[i]);
                    }
                    dir = next;
                }
                String leaf = parts[parts.length - 1];
                if (dir.findChild(leaf) != null) {
                    return;
                }
                VirtualFile created = dir.createChildData(this, leaf);
                String body = "<{* " + templateName + " *}>\n";
                created.setBinaryContent(body.getBytes(StandardCharsets.UTF_8));
            });
        } catch (IOException ignored) {
            // User can create manually if VFS write fails.
        }
    }
}
