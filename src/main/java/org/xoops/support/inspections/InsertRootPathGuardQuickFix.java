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
 * Inserts a file-leading XOOPS_ROOT_PATH guard after a leading {@code <?php} tag only.
 * Does not insert after {@code <?=} or mid-file tags that follow emitted content.
 */
public final class InsertRootPathGuardQuickFix implements LocalQuickFix {

    private static final String GUARD = "defined('XOOPS_ROOT_PATH') || exit('Restricted access');\n";
    private static final Pattern OPEN_PHP = Pattern.compile("<\\?php\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern OPEN_ECHO = Pattern.compile("<\\?=", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROOT_PATH_GUARD = Pattern.compile(
            "defined\\s*\\(\\s*['\"]XOOPS_ROOT_PATH['\"]\\s*\\)",
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

        // Prefer a file-leading <?php (only whitespace/BOM before it).
        Matcher php = OPEN_PHP.matcher(text);
        if (php.find()) {
            String prefix = text.substring(0, php.start());
            if (!prefix.isBlank() && !prefix.replace("\uFEFF", "").isBlank()) {
                // Tag is not at file start — refuse rather than corrupt mixed content.
                return;
            }
            int insertAt = php.end();
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
            PsiDocumentManager.getInstance(project).commitDocument(document);
            return;
        }

        // Leading <?= : insert a separate <?php guard block before it.
        Matcher echo = OPEN_ECHO.matcher(text);
        if (echo.find()) {
            String prefix = text.substring(0, echo.start());
            if (!prefix.isBlank() && !prefix.replace("\uFEFF", "").isBlank()) {
                return;
            }
            document.insertString(echo.start(), "<?php\n" + GUARD + "?>\n");
            PsiDocumentManager.getInstance(project).commitDocument(document);
            return;
        }

        // No open tag — prepend full block.
        if (!ROOT_PATH_GUARD.matcher(text).find()) {
            document.insertString(0, "<?php\n" + GUARD);
            PsiDocumentManager.getInstance(project).commitDocument(document);
        }
    }
}
