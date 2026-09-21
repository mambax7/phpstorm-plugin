package org.xoops.support.scanner;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class XoopsProjectScannerTest {


    @Test
    public void pageTemplateDoesNotSatisfyBlockRegistration() throws Exception {
        Path moduleRoot = Files.createTempDirectory("xoops-template-kind");
        try {
            Files.writeString(moduleRoot.resolve("xoops_version.php"),
                    "<?php $modversion['blocks'][1]['template'] = 'shared.tpl';");
            Files.createDirectories(moduleRoot.resolve("templates"));
            Files.writeString(moduleRoot.resolve("templates/shared.tpl"), "page");
            List<XoopsFinding> findings = new ArrayList<>();
            XoopsProjectScanner.checkRegisteredTemplates(moduleRoot, findings);
            assertTrue(findings.stream().anyMatch(f -> "MISSING_REGISTERED_TEMPLATE".equals(f.kind())));
            assertTrue(findings.stream().anyMatch(f -> "UNREGISTERED_TEMPLATE".equals(f.kind())));
            Files.createDirectories(moduleRoot.resolve("templates/blocks"));
            Files.writeString(moduleRoot.resolve("templates/blocks/shared.tpl"), "block");
            findings.clear();
            XoopsProjectScanner.checkRegisteredTemplates(moduleRoot, findings);
            assertFalse(findings.stream().anyMatch(f -> "MISSING_REGISTERED_TEMPLATE".equals(f.kind())));
            assertEquals(1, findings.stream().filter(f -> "UNREGISTERED_TEMPLATE".equals(f.kind())).count());
        } finally {
            deleteRecursively(moduleRoot);
        }
    }

    @Test
    public void versionPatternsPinTheDocumentedCoreLines() {
        assertTrue(XoopsProjectScanner.VERSION_25.matcher("XOOPS 2.5.11").find());
        assertTrue(XoopsProjectScanner.VERSION_27.matcher("XOOPS 2.7.3").find());
        assertTrue(XoopsProjectScanner.VERSION_40.matcher("XOOPS 4.0.0").find());
        assertFalse(XoopsProjectScanner.VERSION_25.matcher("XOOPS 2.7.3").find());
        assertFalse(XoopsProjectScanner.VERSION_27.matcher("version 12.5-extra").find());
        assertFalse(XoopsProjectScanner.VERSION_25.matcher("version 12.5").find());
        assertFalse(XoopsProjectScanner.VERSION_27.matcher("php: 12.7").find());
        assertFalse(XoopsProjectScanner.VERSION_40.matcher("build 14.0").find());
    }

    @Test
    public void coreVersionSettingOverridesAutoDetection() {
        assertEquals(CoreVersion.XOOPS_25, XoopsProjectScanner.coreVersionFromSetting("2.5"));
        assertEquals(CoreVersion.XOOPS_27, XoopsProjectScanner.coreVersionFromSetting("2.7"));
        assertEquals(CoreVersion.XOOPS_40, XoopsProjectScanner.coreVersionFromSetting("4.0"));
        assertEquals(null, XoopsProjectScanner.coreVersionFromSetting("Auto"));
        Path ignored = Path.of(".");
        assertEquals(
                CoreVersion.XOOPS_27,
                XoopsProjectScanner.resolveScanCoreVersion("2.7", true, ignored, ignored)
        );
        assertEquals(
                CoreVersion.MODULE_ONLY,
                XoopsProjectScanner.resolveScanCoreVersion("Auto", true, ignored, ignored)
        );
    }

    @Test
    public void manifestDirnameAndRegisteredTemplateRegexes() {
        String manifest = """
                $modversion['dirname'] = 'wgsimpleacc';
                $modversion['templates'][] = ['file' => 'wgsimpleacc_index.tpl', 'description' => ''];
                """;
        var regs = org.xoops.support.inspections.XoopsManifestTemplates.find(manifest);
        assertEquals(1, regs.size());
        assertEquals("wgsimpleacc_index.tpl", regs.get(0).name());
    }

    @Test
    public void mutatingQueryAndSmartyDelimiterRegexes() {
        assertTrue(XoopsProjectScanner.MUTATING_QUERY.matcher("$db->query('INSERT INTO t')").find());
        assertFalse(XoopsProjectScanner.MUTATING_QUERY.matcher("$db->query('SELECT * FROM t')").find());
        assertTrue(XoopsProjectScanner.WRONG_SMARTY.matcher("{if $x}").find());
        assertFalse(XoopsProjectScanner.WRONG_SMARTY.matcher("<{if $x}>").find());
        assertTrue(XoopsProjectScanner.RAW_REQUEST.matcher("$_REQUEST['id']").find());
        assertTrue(XoopsProjectScanner.QUERY_F.matcher("$db->queryF($sql)").find());
        assertTrue(XoopsProjectScanner.QUOTE_STRING.matcher("$db->quoteString($s)").find());
    }

    @Test
    public void inverseTemplateScan() throws Exception {
        Path moduleRoot = Files.createTempDirectory("xoops-mod");
        try {
            Files.writeString(moduleRoot.resolve("xoops_version.php"), """
                    <?php
                    $modversion['dirname'] = 'demo';
                    $modversion['templates'][] = ['file' => 'listed.tpl', 'description' => ''];
                    """);
            Path templates = moduleRoot.resolve("templates");
            Files.createDirectories(templates);
            Files.writeString(templates.resolve("listed.tpl"), "<{if $x}><{/if}>\n");
            Files.writeString(templates.resolve("orphan.tpl"), "<{if $y}><{/if}>\n");

            List<XoopsFinding> findings = new ArrayList<>();
            XoopsProjectScanner.checkRegisteredTemplates(moduleRoot, findings);
            assertTrue(findings.stream().anyMatch(f -> "UNREGISTERED_TEMPLATE".equals(f.kind())
                    && f.message().contains("orphan.tpl")));
            assertFalse(findings.stream().anyMatch(f -> f.message().contains("listed.tpl")
                    && "UNREGISTERED_TEMPLATE".equals(f.kind())));
        } finally {
            deleteRecursively(moduleRoot);
        }
    }

    @Test
    public void moduleRootRelativeRegistrationMatchesWalkKey() throws Exception {
        Path moduleRoot = Files.createTempDirectory("xoops-mod");
        try {
            Files.writeString(moduleRoot.resolve("xoops_version.php"), """
                    <?php
                    $modversion['templates'][] = ['file' => 'templates/rooted.tpl', 'description' => ''];
                    """);
            Path templates = moduleRoot.resolve("templates");
            Files.createDirectories(templates);
            Files.writeString(templates.resolve("rooted.tpl"), "<{$x}>");

            List<XoopsFinding> findings = new ArrayList<>();
            XoopsProjectScanner.checkRegisteredTemplates(moduleRoot, findings);
            assertTrue(findings.isEmpty());
        } finally {
            deleteRecursively(moduleRoot);
        }
    }

    @Test
    public void blockTemplateUnderTemplatesBlocksIsRegisteredByBareName() throws Exception {
        Path moduleRoot = Files.createTempDirectory("xoops-mod");
        try {
            Files.writeString(moduleRoot.resolve("xoops_version.php"), """
                    <?php
                    $modversion['blocks'][1]['template'] = 'demo_block.tpl';
                    """);
            Path blocks = moduleRoot.resolve("templates").resolve("blocks");
            Files.createDirectories(blocks);
            Files.writeString(blocks.resolve("demo_block.tpl"), "<{$block.title}>");

            List<XoopsFinding> findings = new ArrayList<>();
            XoopsProjectScanner.checkRegisteredTemplates(moduleRoot, findings);
            assertTrue(findings.toString(), findings.isEmpty());
        } finally {
            deleteRecursively(moduleRoot);
        }
    }

    @Test
    public void commentedManifestEntryIsNotARegistration() throws Exception {
        Path moduleRoot = Files.createTempDirectory("xoops-mod");
        try {
            Files.writeString(moduleRoot.resolve("xoops_version.php"), """
                    <?php
                    // $modversion['blocks'][1]['template'] = 'ghost.tpl';
                    /* $modversion['templates'][] = ['file' => 'ghost2.tpl']; */
                    """);
            Files.createDirectories(moduleRoot.resolve("templates"));
            List<XoopsFinding> findings = new ArrayList<>();
            XoopsProjectScanner.checkRegisteredTemplates(moduleRoot, findings);
            assertTrue(findings.toString(), findings.isEmpty());
        } finally {
            deleteRecursively(moduleRoot);
        }
    }

    @Test
    public void urlInDescriptionDoesNotHideRegistrationOnSameLine() throws Exception {
        Path moduleRoot = Files.createTempDirectory("xoops-mod");
        try {
            Files.writeString(moduleRoot.resolve("xoops_version.php"), """
                    <?php
                    $modversion['templates'][] = ['description' => 'see https://xoops.org #1', 'file' => 'listed.tpl'];
                    """);
            Path templates = moduleRoot.resolve("templates");
            Files.createDirectories(templates);
            Files.writeString(templates.resolve("listed.tpl"), "<{$x}>");
            List<XoopsFinding> findings = new ArrayList<>();
            XoopsProjectScanner.checkRegisteredTemplates(moduleRoot, findings);
            assertTrue(findings.toString(), findings.isEmpty());
        } finally {
            deleteRecursively(moduleRoot);
        }
    }

    @Test
    public void moduleJsonOnlyModuleIsNotScannedForTemplates() throws Exception {
        Path moduleRoot = Files.createTempDirectory("xoops-mod");
        try {
            Files.writeString(moduleRoot.resolve("module.json"), "{\"name\": \"demo\"}");
            Path templates = moduleRoot.resolve("templates");
            Files.createDirectories(templates);
            Files.writeString(templates.resolve("demo_index.tpl"), "<{$x}>");
            List<XoopsFinding> findings = new ArrayList<>();
            XoopsProjectScanner.checkRegisteredTemplates(moduleRoot, findings);
            assertTrue(findings.toString(), findings.isEmpty());
        } finally {
            deleteRecursively(moduleRoot);
        }
    }

    @Test
    public void oversizedManifestReportsScanErrorNotUnregisteredTemplates() throws Exception {
        Path moduleRoot = Files.createTempDirectory("xoops-mod");
        try {
            byte[] big = new byte[1_500_001];
            java.util.Arrays.fill(big, (byte) ' ');
            Files.write(moduleRoot.resolve("xoops_version.php"), big);
            Path templates = moduleRoot.resolve("templates");
            Files.createDirectories(templates);
            Files.writeString(templates.resolve("demo_index.tpl"), "<{$x}>");

            List<XoopsFinding> findings = new ArrayList<>();
            XoopsProjectScanner.checkRegisteredTemplates(moduleRoot, findings);
            assertEquals(findings.toString(), 1, findings.size());
            assertEquals("SCAN_ERROR", findings.get(0).kind());
        } finally {
            deleteRecursively(moduleRoot);
        }
    }

    @Test
    public void registrationLikeStringIsNotAMissingTemplate() throws Exception {
        Path moduleRoot = Files.createTempDirectory("xoops-mod");
        try {
            Files.writeString(moduleRoot.resolve("xoops_version.php"), """
                    <?php
                    $example = "'file' => 'ghost.tpl'";
                    """);
            Files.createDirectories(moduleRoot.resolve("templates"));
            List<XoopsFinding> findings = new ArrayList<>();
            XoopsProjectScanner.checkRegisteredTemplates(moduleRoot, findings);
            assertTrue(findings.toString(), findings.isEmpty());
        } finally {
            deleteRecursively(moduleRoot);
        }
    }

    @Test
    public void missingRegisteredTemplateScan() throws Exception {
        Path moduleRoot = Files.createTempDirectory("xoops-mod");
        try {
            Files.writeString(moduleRoot.resolve("xoops_version.php"), """
                    <?php
                    $modversion['templates'][] = ['file' => 'ghost.tpl', 'description' => ''];
                    """);
            Files.createDirectories(moduleRoot.resolve("templates"));
            List<XoopsFinding> findings = new ArrayList<>();
            XoopsProjectScanner.checkRegisteredTemplates(moduleRoot, findings);
            assertTrue(findings.stream().anyMatch(f -> "MISSING_REGISTERED_TEMPLATE".equals(f.kind())
                    && f.message().contains("ghost.tpl")));
        } finally {
            deleteRecursively(moduleRoot);
        }
    }

    private static void deleteRecursively(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> Integer.compare(b.getNameCount(), a.getNameCount()))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {
                            // temp cleanup
                        }
                    });
        }
    }
}
