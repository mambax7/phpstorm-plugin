package org.xoops.support.completion;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public final class LanguageCacheRegressionTest extends BasePlatformTestCase {
    public void testAncestorModulesDirectoryDoesNotChooseModule() {
        assertEquals("/modules/news/", XoopsLanguageConstantsCache.moduleSegment(
                "/srv/modules/work/site/modules/news/file.php"));
    }

    public void testCacheIgnoresUnrelatedEditsButTracksUnsavedLanguageChanges() {
        var language = myFixture.addFileToProject("modules/news/language/english/main.php",
                "<?php define('_MI_BEFORE', 'Before');");
        var unrelated = myFixture.addFileToProject("modules/news/index.php", "<?php echo 1;");
        var cache = XoopsLanguageConstantsCache.getInstance(getProject());
        var before = cache.getConstants();
        assertTrue(before.contains("_MI_BEFORE"));
        var manager = PsiDocumentManager.getInstance(getProject());
        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            manager.getDocument(unrelated).setText("<?php echo 2;");
            manager.commitAllDocuments();
        });
        assertSame(before, cache.getConstants());
        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            manager.getDocument(language).setText("<?php define('_MI_AFTER', 'After');");
            manager.commitAllDocuments();
        });
        assertTrue(cache.getConstants().contains("_MI_AFTER"));
        assertFalse(cache.getConstants().contains("_MI_BEFORE"));
        myFixture.addFileToProject("modules/news/language/english/extra.php", "<?php define('_MI_EXTRA', 'Extra');");
        assertTrue(cache.getConstants().contains("_MI_EXTRA"));
    }
    public void testDefinitionsUseTheirOwnModuleRatherThanAnAncestor() {
        myFixture.addFileToProject("modules/news/site/modules/blog/language/english/main.php",
                "<?php define('_MI_SHARED', 'Blog');");
        var news = myFixture.addFileToProject("modules/news/site/modules/news/language/english/main.php",
                "<?php define('_MI_SHARED', 'News');");
        var from = myFixture.addFileToProject("modules/news/site/modules/news/index.php", "<?php");
        var target = XoopsLanguageConstantsCache.getInstance(getProject()).resolve("_MI_SHARED", from.getVirtualFile());
        assertNotNull(target);
        assertEquals(news.getVirtualFile(), target.getContainingFile().getVirtualFile());
    }

    public void testCacheTracksMovesIntoLanguageAndDirectoryRenames() {
        var language = myFixture.addFileToProject("modules/news/language/english/main.php", "<?php");
        var moved = myFixture.addFileToProject("modules/news/moved.php", "<?php define('_MI_MOVED', 'Moved');");
        var cache = XoopsLanguageConstantsCache.getInstance(getProject());
        assertFalse(cache.getConstants().contains("_MI_MOVED"));
        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            try {
                moved.getVirtualFile().move(this, language.getVirtualFile().getParent());
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        });
        assertTrue(cache.getConstants().contains("_MI_MOVED"));
        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            try {
                language.getVirtualFile().getParent().getParent().rename(this, "translations");
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        });
        assertFalse(cache.getConstants().contains("_MI_MOVED"));
    }
    public void testMoveIntoEmptyLanguageDirectoryInvalidatesEmptyCache() throws Exception {
        var directory = myFixture.getTempDirFixture().findOrCreateDir("modules/news/language/english");
        var moved = myFixture.addFileToProject("modules/news/moved.php", "<?php define('_MI_FIRST', 'First');");
        var cache = XoopsLanguageConstantsCache.getInstance(getProject());
        assertTrue(cache.getConstants().isEmpty());
        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            try {
                moved.getVirtualFile().move(this, directory);
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        });
        assertTrue(cache.getConstants().contains("_MI_FIRST"));
    }
}
