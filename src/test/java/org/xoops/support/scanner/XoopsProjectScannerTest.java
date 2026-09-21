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
    public void versionPatternsPinTheDocumentedCoreLines() {
        assertTrue(XoopsProjectScanner.VERSION_25.matcher("XOOPS 2.5.11").find());
        assertTrue(XoopsProjectScanner.VERSION_27.matcher("XOOPS 2.7.3").find());
        assertTrue(XoopsProjectScanner.VERSION_40.matcher("XOOPS 4.0.0").find());
        assertFalse(XoopsProjectScanner.VERSION_25.matcher("XOOPS 2.7.3").find());
        assertFalse(XoopsProjectScanner.VERSION_27.matcher("version 12.5-extra").find());
    }

    @Test
    public void manifestDirnameAndRegisteredTemplateRegexes() {
        String manifest = """
                $modversion['dirname'] = 'wgsimpleacc';
                $modversion['templates'][] = ['file' => 'wgsimpleacc_index.tpl', 'description' => ''];
                """;
        var m = XoopsProjectScanner.REGISTERED_TEMPLATE.matcher(manifest);
        assertTrue(m.find());
        assertEquals("wgsimpleacc_index.tpl", m.group(1));
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
