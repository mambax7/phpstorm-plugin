package org.xoops.support.inspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Replaces the problem PSI element's current text. Safe across batch Inspect Code
 * because the range is read from the element at apply-time, not a frozen offset.
 */
public final class ReplacePsiTextQuickFix implements LocalQuickFix {

    private final String familyName;
    private final String replacement;

    public ReplacePsiTextQuickFix(@NotNull String familyName, @NotNull String replacement) {
        this.familyName = familyName;
        this.replacement = replacement;
    }

    @Override
    public @NotNull String getFamilyName() {
        return familyName;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (element == null) {
            return;
        }
        Document document = DocumentEditHelper.documentOf(project, element);
        if (document == null) {
            return;
        }
        TextRange range = element.getTextRange();
        if (range == null || range.getEndOffset() > document.getTextLength()) {
            return;
        }
        document.replaceString(range.getStartOffset(), range.getEndOffset(), replacement);
        PsiDocumentManager.getInstance(project).commitDocument(document);
    }
}
