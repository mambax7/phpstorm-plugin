package org.xoops.support.inspections;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public final class ReviewRegressionTest extends BasePlatformTestCase {
    public void testReversedPredicatesDoNotGuardFetches() {
        for (String condition : new String[]{"$db->isResultSet($result) === false",
                "$db->isResultSet($result) ? false : true"}) {
            assertEquals(condition, 1, XoopsResultSetGuardInspection.unguardedFetchOffsets(
                    "<?php if (" + condition + ") { $db->fetchArray($result); }").size());
        }
        assertEquals(1, XoopsResultSetGuardInspection.unguardedFetchOffsets(
                "<?php if (!$db->isResultSet($result) === false) return; $db->fetchArray($result);").size());
    }

    public void testRegistrationExamplesInStringsAreIgnored() {
        String php = """
                <?php
                $modversion['templates'][] = ['file' => 'real.tpl',
                    'description' => "Example: 'file' => 'ghost.tpl'"];
                $help = '$modversion["templates"][] = ["file" => "fake.tpl"];';
                """;
        assertEquals(java.util.Set.of("templates/real.tpl"), XoopsManifestTemplates.keys(php));
    }

    public void testRegistrationStaysInsidePhp() {
        int module = 0;
        for (String suffix : new String[]{"?> <p>HTML ?> suffix</p>",
                "$note = '?>';", "?>HTML<?php $note = '?>';"}) {
            var manifest = myFixture.addFileToProject("module" + module + "/xoops_version.php", "<?php\n" + suffix);
            var template = myFixture.addFileToProject("module" + module++ + "/templates/new.tpl", "template");
            var descriptor = InspectionManager.getInstance(getProject()).createProblemDescriptor(
                    template, "Unregistered", false, new com.intellij.codeInspection.LocalQuickFix[0],
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            WriteCommandAction.runWriteCommandAction(getProject(), () ->
                    new RegisterTemplateQuickFix("new.tpl").applyFix(getProject(), descriptor));
            PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
            assertEquals(suffix, java.util.Set.of("templates/new.tpl"),
                    XoopsManifestTemplates.keys(manifest.getText()));
        }
    }
}
