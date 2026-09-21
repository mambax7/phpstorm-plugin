package org.xoops.support.inspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inserts a XOOPS_ROOT_PATH guard after {@code <?php} / {@code declare} / {@code namespace},
 * never before a namespace declaration (invalid PHP).
 */
public final class InsertRootPathGuardQuickFix implements LocalQuickFix {

    private static final String GUARD = "defined('XOOPS_ROOT_PATH') || exit('Restricted access');\n";
    private static final Pattern OPEN_ECHO = Pattern.compile("<\\?=", Pattern.CASE_INSENSITIVE);
    /** Short open tag: {@code <?} not followed by php, =, or xml (avoid XML prologs). */
    private static final Pattern OPEN_SHORT = Pattern.compile(
            "<\\?(?!php|=|xml\\b)",
            Pattern.CASE_INSENSITIVE
    );

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
        if (XoopsRootPathGuardPolicy.hasLeadingTerminatingGuard(text)) {
            return;
        }

        int policyOffset = XoopsRootPathGuardPolicy.insertOffset(text);
        if (policyOffset >= 0) {
            insertGuardAt(document, project, policyOffset);
            return;
        }

        Matcher shortTag = OPEN_SHORT.matcher(text);
        if (shortTag.find() && isLeadingTag(text, shortTag.start())) {
            insertGuardAt(document, project, shortTag.end());
            return;
        }

        Matcher echo = OPEN_ECHO.matcher(text);
        if (echo.find() && isLeadingTag(text, echo.start())) {
            document.insertString(echo.start(), "<?php\n" + GUARD + "?>\n");
            PsiDocumentManager.getInstance(project).commitDocument(document);
            return;
        }

        if (!text.contains("<?")) {
            document.insertString(0, "<?php\n" + GUARD);
            PsiDocumentManager.getInstance(project).commitDocument(document);
        }
    }

    private static boolean isLeadingTag(@NotNull String text, int tagStart) {
        String prefix = text.substring(0, tagStart).replace("\uFEFF", "");
        return prefix.isBlank();
    }

    private static void insertGuardAt(
            @NotNull Document document,
            @NotNull Project project,
            int offset
    ) {
        int insertAt = Math.max(0, Math.min(offset, document.getTextLength()));
        CharSequence seq = document.getCharsSequence();
        String prefix = insertAt == 0 ? "" : String.valueOf(seq.charAt(insertAt - 1));
        String toInsert = GUARD;
        if (!prefix.isEmpty() && prefix.charAt(0) != '\n') {
            toInsert = "\n" + GUARD;
        }
        document.insertString(insertAt, toInsert);
        PsiDocumentManager.getInstance(project).commitDocument(document);
    }
}
