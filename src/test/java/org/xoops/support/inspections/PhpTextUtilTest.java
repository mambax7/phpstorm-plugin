package org.xoops.support.inspections;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PhpTextUtilTest {

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
    public void fullMaskWipesHeredocButKeepsOffsets() {
        String php = "<?php\n$b = <<<'EOT'\nbody\nEOT;\n$c = 1;\n";
        String masked = PhpTextUtil.maskCommentsAndStrings(php);
        assertEquals(php.length(), masked.length());
        assertFalse(masked.contains("body"));
        assertTrue(masked.contains("$c = 1;"));
    }
}
