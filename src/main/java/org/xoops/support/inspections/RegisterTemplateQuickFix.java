package org.xoops.support.inspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Appends a {@code $modversion['templates'][]} entry for the unregistered .tpl.
 */
public final class RegisterTemplateQuickFix implements LocalQuickFix {

    private final String templateName;

    public RegisterTemplateQuickFix(@NotNull String templateName) {
        this.templateName = templateName.replace('\\', '/');
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Register template in xoops_version.php";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiFile tplFile = descriptor.getPsiElement() == null
                ? null
                : descriptor.getPsiElement().getContainingFile();
        if (tplFile == null || tplFile.getVirtualFile() == null) {
            return;
        }
        VirtualFile dir = tplFile.getVirtualFile().getParent();
        VirtualFile manifest = null;
        while (dir != null) {
            manifest = dir.findChild("xoops_version.php");
            if (manifest != null) {
                break;
            }
            dir = dir.getParent();
        }
        if (manifest == null) {
            return;
        }
        PsiFile manifestPsi = PsiManager.getInstance(project).findFile(manifest);
        if (manifestPsi == null) {
            return;
        }
        Document document = PsiDocumentManager.getInstance(project).getDocument(manifestPsi);
        if (document == null) {
            return;
        }
        String text = document.getText();
        String lowerName = templateName.toLowerCase(Locale.ROOT);
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
}
