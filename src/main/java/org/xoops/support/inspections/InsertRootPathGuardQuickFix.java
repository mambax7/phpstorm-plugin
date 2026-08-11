package org.xoops.support.inspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InsertRootPathGuardQuickFix implements LocalQuickFix {

    private static final String GUARD = "defined('XOOPS_ROOT_PATH') || exit('Restricted access');\n";
    private static final Pattern OPEN_TAG = Pattern.compile("<\\?(?:php|=)?", Pattern.CASE_INSENSITIVE);

    @Override
    public @NotNull String getFamilyName() {
        return "Insert XOOPS_ROOT_PATH guard";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (element == null) {
            return;
        }
        PsiFile file = element.getContainingFile();
        if (file == null) {
            return;
        }
        Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        if (document == null) {
            return;
        }
        String text = document.getText();
        if (text.toLowerCase(Locale.ROOT).contains("xoops_root_path")
                && text.toLowerCase(Locale.ROOT).contains("defined")) {
            return;
        }
        Matcher open = OPEN_TAG.matcher(text);
        if (open.find()) {
            int insertAt = open.end();
            if (insertAt < text.length() && text.charAt(insertAt) == '\r') {
                insertAt++;
            }
            if (insertAt < text.length() && text.charAt(insertAt) == '\n') {
                insertAt++;
            } else {
                document.insertString(insertAt, "\n");
                insertAt++;
            }
            document.insertString(insertAt, GUARD);
        } else {
            document.insertString(0, "<?php\n" + GUARD);
        }
        PsiDocumentManager.getInstance(project).commitDocument(document);
    }
}
