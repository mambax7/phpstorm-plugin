package org.xoops.support.inspections;

import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class XoopsManifestTemplatesTest {

    @Test
    public void registrationLikeTextOutsideModversionIsIgnored() {
        String manifest = """
                <?php
                $help = "'file' => 'ghost.tpl'";
                $other['templates'][] = ['file' => 'not_modversion.tpl'];
                $modversion['templates'][] = ['file' => 'real.tpl', 'description' => 'x'];
                """;
        Set<String> keys = XoopsManifestTemplates.keys(manifest);
        assertTrue(keys.contains("templates/real.tpl"));
        assertFalse(keys.contains("templates/ghost.tpl"));
        assertFalse(keys.contains("templates/not_modversion.tpl"));
    }

    @Test
    public void singleStatementArrayYieldsEveryFile() {
        String manifest = """
                <?php
                $modversion['templates'] = [
                    ['file' => 'a.tpl', 'description' => 'see https://xoops.org; ok'],
                    ['file' => 'b.tpl'],
                ];
                $modversion['blocks'][1]['template'] = 'c_block.tpl';
                // $modversion['templates'][] = ['file' => 'commented.tpl'];
                """;
        List<XoopsManifestTemplates.Registration> regs =
                XoopsManifestTemplates.find(PhpTextUtil.maskCommentsOnly(manifest));
        assertEquals(3, regs.size());
        assertEquals("a.tpl", regs.get(0).name());
        assertEquals(XoopsManifestTemplates.Section.TEMPLATES, regs.get(0).section());
        assertEquals(manifest.indexOf("a.tpl"), regs.get(0).nameOffset());
        assertEquals("b.tpl", regs.get(1).name());
        assertEquals("c_block.tpl", regs.get(2).name());
        assertEquals(XoopsManifestTemplates.Section.BLOCKS, regs.get(2).section());
        assertTrue(regs.get(2).block());
    }

    @Test
    public void diskPathPutsBareBlockNamesUnderTemplatesBlocks() {
        assertEquals("templates/demo.tpl", XoopsManifestTemplates.diskPath("demo.tpl", false));
        assertEquals("templates/blocks/demo_block.tpl", XoopsManifestTemplates.diskPath("demo_block.tpl", true));
        assertEquals("templates/rooted.tpl", XoopsManifestTemplates.diskPath("templates/rooted.tpl", false));
        assertEquals("blocks/legacy.tpl", XoopsManifestTemplates.diskPath("blocks/legacy.tpl", true));
    }

    @Test
    public void registrationSpellingPreservesNestedDirectories() {
        assertEquals("foo.tpl", XoopsManifestTemplates.registrationName("templates/foo.tpl"));
        for (String path : new String[]{"templates/templates/foo.tpl", "templates/blocks/foo.tpl", "blocks/foo.tpl"}) {
            assertEquals(path, XoopsManifestTemplates.registrationName(path));
            assertEquals(path, XoopsManifestTemplates.diskPath(XoopsManifestTemplates.registrationName(path), false));
        }
    }

    @Test
    public void keysCoverBareAndPrefixedSpellings() {
        Set<String> keys = XoopsManifestTemplates.keys(
                "<?php $modversion['templates'][] = ['file' => 'templates/blocks/deep_block.tpl'];");
        assertTrue(keys.contains("templates/blocks/deep_block.tpl"));
        assertFalse(keys.contains("blocks/deep_block.tpl"));
        assertFalse(keys.contains("deep_block.tpl"));
    }
}
