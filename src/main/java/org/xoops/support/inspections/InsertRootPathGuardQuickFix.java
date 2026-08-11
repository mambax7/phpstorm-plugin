package org.xoops.support.inspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public final class InsertRootPathGuardQuickFix implements LocalQuickFix {

    private static final String GUARD = "defined('XOOPS_ROOT_PATH') || exit('Restricted access');\n";

    @Override
    public @NotNull String getFamilyName() {
        return "Insert XOOPS_ROOT_PATH guard";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiFile file = descriptor.getPsiElement().getContainingFile();
        if (file == null) {
            return;
        }
        var document = PsiDocumentManager.getInstance(project).getDocument(file);
        if (document == null) {
            return;
        }
        String text = document.getText();
        int insertAt = 0;
        int php = text.indexOf("<?php");
        if (php >= 0) {
            insertAt = php + "<?php".length();
            // Skip optional newline after open tag.
            if (insertAt < text.length() && text.charAt(insertAt) == '\r') {
                insertAt++;
            }
            if (insertAt < text.length() && text.charAt(insertAt) == '\n') {
                insertAt++;
            }
            document.insertString(insertAt, GUARD);
        } else {
            document.insertString(0, "<?php\n" + GUARD);
        }
        PsiDocumentManager.getInstance(project).commitDocument(document);
    }
}
