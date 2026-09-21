package org.xoops.support.inspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Appends a {@code $modversion['templates'][]} entry for the unregistered .tpl.
 */
public final class RegisterTemplateQuickFix implements LocalQuickFix {

    private final String templateName;

    public RegisterTemplateQuickFix(@NotNull String templateName) {
        this.templateName = XoopsManifestTemplates.registrationName(templateName);
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Register template in xoops_version.php";
    }

    @Override
    public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
        PsiFile manifest = findManifestPsi(currentFile.getProject(), currentFile);
        return manifest != null ? manifest : currentFile;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiFile tplFile = descriptor.getPsiElement() == null
                ? null
                : descriptor.getPsiElement().getContainingFile();
        PsiFile manifestPsi = findManifestPsi(project, tplFile);
        if (manifestPsi == null) {
            return;
        }
        Document document = PsiDocumentManager.getInstance(project).getDocument(manifestPsi);
        if (document == null) {
            return;
        }
        String text = document.getText();
        String lowerName = XoopsManifestTemplates.diskPath(templateName, false).toLowerCase(Locale.ROOT);
        // Same parser as the inspection: a commented-out entry is not a registration.
        if (XoopsUnregisteredTemplateInspection.registeredTemplates(text).contains(lowerName)) {
            return;
        }
        String entry = "\n$modversion['templates'][] = [\n"
                + "    'file' => '" + templateName.replace("'", "\\'") + "',\n"
                + "    'description' => '',\n"
                + "];\n";
        int insertAt = text.length();
        int closePhp = text.lastIndexOf("?>");
        if (closePhp >= 0 && text.substring(closePhp).trim().equals("?>")) {
            insertAt = closePhp;
        }
        document.insertString(insertAt, entry);
        PsiDocumentManager.getInstance(project).commitDocument(document);
    }

    private static @Nullable PsiFile findManifestPsi(@NotNull Project project, @Nullable PsiFile fromFile) {
        if (fromFile == null || fromFile.getVirtualFile() == null) {
            return null;
        }
        VirtualFile dir = fromFile.getVirtualFile().getParent();
        while (dir != null) {
            VirtualFile manifest = dir.findChild("xoops_version.php");
            if (manifest != null) {
                return PsiManager.getInstance(project).findFile(manifest);
            }
            dir = dir.getParent();
        }
        return null;
    }
}
