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
 * Inserts a file-leading XOOPS_ROOT_PATH guard after a leading PHP open tag.
 * Handles {@code <?php}, short {@code <?}, and {@code <?=} without double open tags.
 */
public final class InsertRootPathGuardQuickFix implements LocalQuickFix {

    private static final String GUARD = "defined('XOOPS_ROOT_PATH') || exit('Restricted access');\n";
    private static final Pattern OPEN_PHP = Pattern.compile("<\\?php\\b", Pattern.CASE_INSENSITIVE);
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
        if (XoopsRootPathGuardInspection.hasLeadingTerminatingGuard(text)) {
            return;
        }

        // Prefer a file-leading <?php
        Matcher php = OPEN_PHP.matcher(text);
        if (php.find() && isLeadingTag(text, php.start())) {
            insertGuardAfter(document, project, php.end());
            return;
        }

        // Leading short <? (not <?php / <?=): insert GUARD after the tag
        Matcher shortTag = OPEN_SHORT.matcher(text);
        if (shortTag.find() && isLeadingTag(text, shortTag.start())) {
            insertGuardAfter(document, project, shortTag.end());
            return;
        }

        // Leading <?= : insert a separate <?php guard block before it (no second short tag)
        Matcher echo = OPEN_ECHO.matcher(text);
        if (echo.find() && isLeadingTag(text, echo.start())) {
            document.insertString(echo.start(), "<?php\n" + GUARD + "?>\n");
            PsiDocumentManager.getInstance(project).commitDocument(document);
            return;
        }

        // No recognized leading open tag — only prepend if file has no PHP open at all
        if (!text.contains("<?")) {
            document.insertString(0, "<?php\n" + GUARD);
            PsiDocumentManager.getInstance(project).commitDocument(document);
        }
        // Otherwise decline rather than prepend a second opening tag mid-file.
    }

    private static boolean isLeadingTag(@NotNull String text, int tagStart) {
        String prefix = text.substring(0, tagStart).replace("\uFEFF", "");
        return prefix.isBlank();
    }

    private static void insertGuardAfter(
            @NotNull Document document,
            @NotNull Project project,
            int tagEnd
    ) {
        int insertAt = tagEnd;
        CharSequence seq = document.getCharsSequence();
        if (insertAt < seq.length() && seq.charAt(insertAt) == '\r') {
            insertAt++;
        }
        if (insertAt < seq.length() && seq.charAt(insertAt) == '\n') {
            insertAt++;
        } else {
            document.insertString(insertAt, "\n");
            insertAt++;
        }
        document.insertString(insertAt, GUARD);
        PsiDocumentManager.getInstance(project).commitDocument(document);
    }
}
