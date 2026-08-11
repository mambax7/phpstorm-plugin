package org.xoops.support.inspections;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class DocumentEditHelper {

    private DocumentEditHelper() {
    }

    static @Nullable Document documentOf(@NotNull Project project, @Nullable PsiElement element) {
        if (element == null) {
            return null;
        }
        PsiFile file = element.getContainingFile();
        if (file == null) {
            return null;
        }
        return PsiDocumentManager.getInstance(project).getDocument(file);
    }

    static void replace(
            @NotNull Project project,
            @NotNull Document document,
            int start,
            int end,
            @NotNull String text
    ) {
        if (start < 0 || end > document.getTextLength() || start > end) {
            return;
        }
        document.replaceString(start, end, text);
        PsiDocumentManager.getInstance(project).commitDocument(document);
    }

    static void insert(
            @NotNull Project project,
            @NotNull Document document,
            int offset,
            @NotNull String text
    ) {
        if (offset < 0 || offset > document.getTextLength()) {
            return;
        }
        document.insertString(offset, text);
        PsiDocumentManager.getInstance(project).commitDocument(document);
    }

    static int lineStart(@NotNull Document document, int offset) {
        int line = document.getLineNumber(Math.min(offset, document.getTextLength()));
        return document.getLineStartOffset(line);
    }

    static int indentOfLine(@NotNull Document document, int offset) {
        int start = lineStart(document, offset);
        int i = start;
        CharSequence seq = document.getCharsSequence();
        while (i < document.getTextLength()) {
            char c = seq.charAt(i);
            if (c == ' ' || c == '\t') {
                i++;
            } else {
                break;
            }
        }
        return i - start;
    }

    static @NotNull String spaces(int n) {
        return " ".repeat(Math.max(0, n));
    }
}
