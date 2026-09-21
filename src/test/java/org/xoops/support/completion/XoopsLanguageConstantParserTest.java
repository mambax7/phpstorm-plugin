package org.xoops.support.completion;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class XoopsLanguageConstantParserTest {

    @Test
    public void parsesDefineNamesIncludingSearchPhpStyleFiles() {
        String php = """
                <?php
                define('_MD_WGSIMPLEACC_FOO', 'Foo');
                define("_MI_WGSIMPLEACC_NAME", 'Name');
                define('_AM_WGSIMPLEACC_BAR', 'Bar');
                define('_MB_WGSIMPLEACC_BLOCK', 'Block');
                define('_CO_WGSIMPLEACC_OK', 'OK');
                define('NOT_A_XOOPS_CONST', 'x');
                """;
        List<XoopsLanguageConstantParser.Occurrence> occ = XoopsLanguageConstantParser.parse(php);
        assertEquals(5, occ.size());
        assertEquals("_MD_WGSIMPLEACC_FOO", occ.get(0).name());
    }

    @Test
    public void extractNameAcceptsQuotedAndBare() {
        assertEquals("_MD_FOO", XoopsLanguageConstantParser.extractName("'_MD_FOO'"));
        assertEquals("_MD_FOO", XoopsLanguageConstantParser.extractName("\"_MD_FOO\""));
        assertEquals("_MD_FOO", XoopsLanguageConstantParser.extractName("_MD_FOO"));
        assertNull(XoopsLanguageConstantParser.extractName("not_a_const"));
        assertNull(XoopsLanguageConstantParser.extractName("'_XX_FOO'"));
    }

    @Test
    public void parseIgnoresEmbeddedFunctionNames() {
        String php = """
                <?php
                mydefine('_MI_NOT_A_DEFINE', 'x');
                $obj->define('_MI_NOT_EITHER', 'x');
                \\define('_MI_NAMESPACED', 'y');
                define('_MI_PLAIN', 'z');
                """;
        List<XoopsLanguageConstantParser.Occurrence> occ = XoopsLanguageConstantParser.parse(php);
        assertEquals(2, occ.size());
        assertEquals("_MI_NAMESPACED", occ.get(0).name());
        assertEquals("_MI_PLAIN", occ.get(1).name());
    }

    @Test
    public void parsePreservesDistinctSpellings() {
        String php = """
                <?php
                define('_mi_foo', 'a');
                define('_MI_FOO', 'b');
                """;
        List<XoopsLanguageConstantParser.Occurrence> occ = XoopsLanguageConstantParser.parse(php);
        assertEquals(2, occ.size());
        assertEquals("_mi_foo", occ.get(0).name());
        assertEquals("_MI_FOO", occ.get(1).name());
    }

    @Test
    public void extractNamePreservesSpellingAndSmartyConst() {
        assertEquals("_mi_foo", XoopsLanguageConstantParser.extractName("_mi_foo"));
        assertEquals("_MI_FOO", XoopsLanguageConstantParser.extractName("$smarty.const._MI_FOO"));
        assertEquals("_MI_FOO", XoopsLanguageConstantParser.extractName("<{$smarty.const._MI_FOO}>"));
        assertNull(XoopsLanguageConstantParser.extractName("'customer.const._MI_FOO'"));
    }

    @Test
    public void languagePathFilter() {
        assertTrue(XoopsLanguageConstantParser.isLanguagePath("C:/m/language/english/search.php"));
        assertTrue(XoopsLanguageConstantParser.isLanguagePath("/modules/news/language/german/mail.php"));
        assertFalse(XoopsLanguageConstantParser.isLanguagePath("/modules/news/class/Item.php"));
    }
}
