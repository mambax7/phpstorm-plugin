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
    public void testTemplateCaseMismatchIsReportedWithoutCreateFix() {
        var manifest = myFixture.addFileToProject("case-module/xoops_version.php",
                "<?php $modversion['templates'][] = ['file' => 'Admin/Foo.tpl'];");
        var template = myFixture.addFileToProject("case-module/templates/admin/foo.tpl", "template");
        var holder = new com.intellij.codeInspection.ProblemsHolder(
                InspectionManager.getInstance(getProject()), manifest, false);
        new XoopsMissingRegisteredTemplateInspection().buildVisitor(holder, false).visitFile(manifest);
        assertEquals(1, holder.getResults().size());
        assertTrue(holder.getResults().get(0).getDescriptionTemplate().contains("case mismatch"));
        var fixes = holder.getResults().get(0).getFixes();
        assertTrue(fixes == null || fixes.length == 0);
        var inverse = new com.intellij.codeInspection.ProblemsHolder(
                InspectionManager.getInstance(getProject()), template, false);
        new XoopsUnregisteredTemplateInspection().buildVisitor(inverse, false).visitFile(template);
        assertTrue(inverse.getResults().isEmpty());
    }
    public void testPrefixedPathsPreserveCase() {
        assertEquals("Templates/Admin.tpl", XoopsManifestTemplates.diskPath("Templates/Admin.tpl", false));
        assertEquals("Blocks/Header.tpl", XoopsManifestTemplates.diskPath("Blocks/Header.tpl", true));
    }
    public void testHeredocDescriptionDoesNotHideRegistration() {
        String source = """
                <?php
                $modversion['templates'][] = [
                  'description' => <<<DESC
                Text; more text
                DESC,
                  'file' => 'real.tpl'
                ];
                """;
        assertEquals(java.util.Set.of("templates/real.tpl"), XoopsManifestTemplates.keys(source));
    }
    public void testArbitraryInstanceofDoesNotGuardFetch() {
        assertEquals(1, XoopsResultSetGuardInspection.unguardedFetchOffsets(
                "<?php if (!$result instanceof SomeClass) return; $db->fetchArray($result);").size());
    }
    public void testVendorManifestIsIgnored() {
        for (String prefix : new String[]{"vendor", "cache", "templates_c", "node_modules"}) {
            var file = myFixture.addFileToProject(prefix + "/demo/xoops_version.php",
                    "<?php $modversion['templates'][] = ['file' => 'missing.tpl'];");
            var holder = new com.intellij.codeInspection.ProblemsHolder(InspectionManager.getInstance(getProject()), file, false);
            new XoopsMissingRegisteredTemplateInspection().buildVisitor(holder, false).visitFile(file);
            assertTrue(prefix, holder.getResults().isEmpty());
        }
    }
    public void testQuickFixDoesNotHoistGuardOutOfConditional() {
        int index = 0;
        for (String control : new String[]{"if ($enabled)", "while ($enabled)",
                "for ($i = 0; $i < 2; $i++)", "foreach ($items as $item)"}) {
            String source = "<?php " + control + " $db->fetchArray($result);";
            var file = myFixture.addFileToProject("modules/demo/include/example" + index++ + ".php", source);
            var leaf = file.findElementAt(source.indexOf("$db"));
            var descriptor = InspectionManager.getInstance(getProject()).createProblemDescriptor(
                    leaf, "Unguarded", false, new com.intellij.codeInspection.LocalQuickFix[0], ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            WriteCommandAction.runWriteCommandAction(getProject(), () ->
                    new InsertBeforeStatementQuickFix("$db", "$result").applyFix(getProject(), descriptor));
            assertEquals(source, file.getText());
            var holder = new com.intellij.codeInspection.ProblemsHolder(InspectionManager.getInstance(getProject()), file, false);
            new XoopsResultSetGuardInspection().buildVisitor(holder, false).visitFile(file);
            assertEquals(1, holder.getResults().size());
            var fixes = holder.getResults().get(0).getFixes();
            assertTrue(fixes == null || fixes.length == 0);
        }
    }
    public void testGuardFixStillWorksInBracedBody() {
        String source = "<?php if ($enabled) { $db->fetchArray($result); }";
        var file = myFixture.addFileToProject("modules/demo/include/braced.php", source);
        var holder = new com.intellij.codeInspection.ProblemsHolder(InspectionManager.getInstance(getProject()), file, false);
        new XoopsResultSetGuardInspection().buildVisitor(holder, false).visitFile(file);
        assertEquals(1, holder.getResults().size());
        var fix = (com.intellij.codeInspection.LocalQuickFix) holder.getResults().get(0).getFixes()[0];
        WriteCommandAction.runWriteCommandAction(getProject(), () -> fix.applyFix(getProject(), holder.getResults().get(0)));
        assertTrue(file.getText().startsWith("<?php if ($enabled) { "));
        assertTrue(file.getText().contains("isResultSet($result)"));
    }

    public void testHeredocCloserKeepsFollowingEntryAndStatementBoundary() {
        for (String opener : new String[]{"DESC", "'DESC'", "\"DESC\""}) {
            String source = "<?php $modversion['templates'][] = ['description' => <<<" + opener
                    + "\nText; text\n  DESC, 'file' => 'real.tpl'];\n"
                    + "$modversion['blocks'][1]['template'] = 'block.tpl';";
            assertEquals(java.util.Set.of("templates/real.tpl", "templates/blocks/block.tpl"),
                    XoopsManifestTemplates.keys(source));
        }
    }
}
