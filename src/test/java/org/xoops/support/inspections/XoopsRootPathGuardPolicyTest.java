package org.xoops.support.inspections;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class XoopsRootPathGuardPolicyTest {

    @Test
    public void recognizesDieGuardAfterNamespaceAndUse() {
        String source = """
                <?php

                namespace XoopsModules\\Wgsimpleacc;

                /**
                 * Class Object Accounts
                 */

                use XoopsModules\\Wgsimpleacc\\Constants;

                defined('XOOPS_ROOT_PATH') || die('Restricted access');

                class Accounts extends \\XoopsObject
                {
                }
                """;
        assertTrue(XoopsRootPathGuardPolicy.hasLeadingTerminatingGuard(source));
        assertFalse(XoopsRootPathGuardPolicy.requiresGuard(
                "C:/site/htdocs/modules/wgsimpleacc/class/Accounts.php",
                source
        ));
    }

    @Test
    public void insertOffsetIsAfterNamespaceNotBefore() {
        String source = """
                <?php

                namespace XoopsModules\\Wgsimpleacc;

                use Foo;

                class Accounts {}
                """;
        int offset = XoopsRootPathGuardPolicy.insertOffset(source);
        assertTrue(offset > 0);
        String before = source.substring(0, offset);
        String after = source.substring(offset);
        assertTrue(before.contains("namespace XoopsModules\\Wgsimpleacc;"));
        assertFalse(before.contains("use Foo"));
        assertTrue(after.stripLeading().startsWith("use Foo"));
    }

    @Test
    public void flagsUnguardedClassFile() {
        assertTrue(XoopsRootPathGuardPolicy.requiresGuard(
                "C:/site/htdocs/modules/wgsimpleacc/class/Accounts.php",
                "<?php\n\nnamespace XoopsModules\\Wgsimpleacc;\n\nclass Accounts {}\n"
        ));
    }

    @Test
    public void skips404StubIndex() {
        String stub = "<?php\nheader('HTTP/1.0 404 Not Found');\n";
        assertFalse(XoopsRootPathGuardPolicy.requiresGuard(
                "C:/site/htdocs/modules/wgsimpleacc/index.php",
                stub
        ));
        assertTrue(XoopsRootPathGuardPolicy.is404OrForbiddenStub(
                XoopsRootPathGuardPolicy.firstExecutable(stub)
        ));
    }

    @Test
    public void skipsBootstrapEntryPoint() {
        assertFalse(XoopsRootPathGuardPolicy.requiresGuard(
                "C:/site/htdocs/modules/wgsimpleacc/index.php",
                "<?php\ninclude_once dirname(__DIR__, 2) . '/mainfile.php';\n"
        ));
    }

    @Test
    public void skipsAdminScripts() {
        assertFalse(XoopsRootPathGuardPolicy.requiresGuard(
                "C:/site/htdocs/modules/wgsimpleacc/admin/about.php",
                "<?php\n// no guard\nclass About {}\n"
        ));
    }

    @Test
    public void skipsLanguageAndXoopsVersion() {
        String classSource = "<?php class X {}";
        assertFalse(XoopsRootPathGuardPolicy.requiresGuard(
                "C:/site/htdocs/modules/news/language/english/main.php",
                classSource
        ));
        assertFalse(XoopsRootPathGuardPolicy.requiresGuard(
                "C:/site/htdocs/modules/news/xoops_version.php",
                classSource
        ));
    }

    @Test
    public void acceptsIfFormGuard() {
        String source = "<?php\nif (!defined('XOOPS_ROOT_PATH')) { exit('Restricted access'); }\nclass X {}\n";
        assertTrue(XoopsRootPathGuardPolicy.hasLeadingTerminatingGuard(source));
    }

    @Test
    public void insertOffsetPlainFileIsAfterOpenTag() {
        String source = "<?php\nclass Foo {}\n";
        int offset = XoopsRootPathGuardPolicy.insertOffset(source);
        assertEquals("class Foo {}\n", source.substring(offset));
    }
}
