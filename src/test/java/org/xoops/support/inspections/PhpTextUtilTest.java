package org.xoops.support.inspections;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PhpTextUtilTest {


    @Test
    public void htmlIsOpaqueToBothInspectionMasks() {
        String source = "<p>Don't use XOBJ_DTYPE_UNICODE_TXTBOX</p><?php $live = 1; ?>"
                + "$modversion['templates'][] = ['file' => 'html.tpl'];";
        for (String masked : new String[]{PhpTextUtil.maskCommentsOnly(source),
                PhpTextUtil.maskCommentsAndStrings(source)}) {
            assertEquals(source.length(), masked.length());
            assertFalse(masked.contains("XOBJ_DTYPE_UNICODE_TXTBOX"));
            assertFalse(masked.contains("html.tpl"));
            assertTrue(masked.contains("$live = 1;"));
        }
    }

    @Test
    public void closingTagEndsLineCommentsBeforeNextPhpRegion() {
        for (String comment : new String[]{"//", "#"}) {
            String source = "<?php " + comment + " comment ?>Don't <?php $live = 1;";
            String masked = PhpTextUtil.maskCommentsAndStrings(source);
            assertEquals(source.length(), masked.length());
            assertFalse(masked.contains("Don't"));
            assertTrue(masked.contains("$live = 1;"));
        }
    }

    @Test
    public void commentOnlyMaskKeepsStringsAndHeredocBodies() {
        String php = """
                <?php
                $a = 'https://xoops.org'; // trailing comment
                $b = <<<EOT
                /* not a comment */ // nor this
                EOT;
                define('_MI_AFTER', 'x');
                """;
        String masked = PhpTextUtil.maskCommentsOnly(php);
        assertEquals(php.length(), masked.length());
        assertTrue(masked.contains("'https://xoops.org'"));
        assertFalse(masked.contains("trailing comment"));
        assertTrue(masked.contains("/* not a comment */ // nor this"));
        assertTrue(masked.contains("define('_MI_AFTER'"));
    }

    @Test
    public void unmatchedHtmlQuoteDoesNotSwallowFollowingPhp() {
        String php = """
                <div class="don't">
                <?php
                // defined('XOOPS_ROOT_PATH') || exit;
                $modversion['templates'][] = ['file' => 'real.tpl'];
                """;
        String commentsOnly = PhpTextUtil.maskCommentsOnly(php);
        assertEquals(php.length(), commentsOnly.length());
        assertFalse(commentsOnly.contains("defined('XOOPS_ROOT_PATH')"));
        assertTrue(commentsOnly.contains("$modversion['templates'][] = ['file' => 'real.tpl']"));
        String full = PhpTextUtil.maskCommentsAndStrings(php);
        assertFalse(full.contains("real.tpl"));
        assertTrue(full.contains("$modversion"));
    }

    @Test
    public void fullMaskWipesHeredocButKeepsOffsets() {
        String php = "<?php\n$b = <<<'EOT'\nbody\nEOT;\n$c = 1;\n";
        String masked = PhpTextUtil.maskCommentsAndStrings(php);
        assertEquals(php.length(), masked.length());
        assertFalse(masked.contains("body"));
        assertTrue(masked.contains("$c = 1;"));
    }
}
