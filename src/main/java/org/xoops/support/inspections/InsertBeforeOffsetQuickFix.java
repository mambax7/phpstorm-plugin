package org.xoops.support.inspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class InsertBeforeOffsetQuickFix implements LocalQuickFix {

    private final String familyName;
    private final int offset;
    private final String text;
    private final @Nullable String expectedAtOffset;

    public InsertBeforeOffsetQuickFix(@NotNull String familyName, int offset, @NotNull String text) {
        this(familyName, offset, text, null);
    }

    public InsertBeforeOffsetQuickFix(
            @NotNull String familyName,
            int offset,
            @NotNull String text,
            @Nullable String expectedAtOffset
    ) {
        this.familyName = familyName;
        this.offset = offset;
        this.text = text;
        this.expectedAtOffset = expectedAtOffset;
    }

    @Override
    public @NotNull String getFamilyName() {
        return familyName;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        if (descriptor.getPsiElement() == null) {
            return;
        }
        Document document = DocumentEditHelper.documentOf(project, descriptor.getPsiElement());
        if (document == null) {
            return;
        }
        DocumentEditHelper.insert(project, document, offset, text, expectedAtOffset);
    }
}
