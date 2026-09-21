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
        assertTrue(keys.contains("real.tpl"));
        assertFalse(keys.contains("ghost.tpl"));
        assertFalse(keys.contains("not_modversion.tpl"));
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
        assertEquals(manifest.indexOf("a.tpl"), regs.get(0).nameOffset());
        assertEquals("b.tpl", regs.get(1).name());
        assertEquals("c_block.tpl", regs.get(2).name());
    }

    @Test
    public void keysCoverBareAndPrefixedSpellings() {
        Set<String> keys = XoopsManifestTemplates.keys(
                "<?php $modversion['templates'][] = ['file' => 'templates/blocks/deep_block.tpl'];");
        assertTrue(keys.contains("templates/blocks/deep_block.tpl"));
        assertTrue(keys.contains("blocks/deep_block.tpl"));
        assertTrue(keys.contains("deep_block.tpl"));
    }
}
