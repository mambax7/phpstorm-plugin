package org.xoops.support.inspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public final class InsertBeforeOffsetQuickFix implements LocalQuickFix {

    private final String familyName;
    private final int offset;
    private final String text;

    public InsertBeforeOffsetQuickFix(@NotNull String familyName, int offset, @NotNull String text) {
        this.familyName = familyName;
        this.offset = offset;
        this.text = text;
    }

    @Override
    public @NotNull String getFamilyName() {
        return familyName;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        Document document = DocumentEditHelper.documentOf(project, descriptor.getPsiElement());
        if (document == null) {
            return;
        }
        DocumentEditHelper.insert(project, document, offset, text);
    }
}
