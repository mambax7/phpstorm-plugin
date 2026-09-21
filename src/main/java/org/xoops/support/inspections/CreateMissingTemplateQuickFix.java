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
 * Creates the on-disk template for a missing {@code xoops_version.php} registration.
 * Block registrations land under {@code templates/blocks/} unless the name already
 * includes a {@code templates/} or {@code blocks/} prefix.
 */
public final class CreateMissingTemplateQuickFix implements LocalQuickFix {

    private final String templateName;
    private final boolean block;

    public CreateMissingTemplateQuickFix(@NotNull String templateName, boolean block) {
        this.templateName = templateName.replace('\\', '/');
        this.block = block;
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
        String relative = XoopsManifestTemplates.diskPath(templateName, block);
        try {
            WriteAction.runAndWait(() -> {
                String[] parts = relative.split("/");
                VirtualFile dir = moduleRoot;
                for (int i = 0; i < parts.length - 1; i++) {
                    if (dir == null) {
                        return;
                    }
                    VirtualFile next = dir.findChild(parts[i]);
                    if (next == null) {
                        next = dir.createChildDirectory(this, parts[i]);
                    }
                    dir = next;
                }
                if (dir == null) {
                    return;
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
