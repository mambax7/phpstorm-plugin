package org.xoops.support.inspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Replaces a character range in the current document.
 */
public final class ReplaceRangeQuickFix implements LocalQuickFix {

    private final String familyName;
    private final int start;
    private final int end;
    private final String replacement;

    public ReplaceRangeQuickFix(
            @NotNull String familyName,
            int start,
            int end,
            @NotNull String replacement
    ) {
        this.familyName = familyName;
        this.start = start;
        this.end = end;
        this.replacement = replacement;
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
        DocumentEditHelper.replace(project, document, start, end, replacement);
    }
}
