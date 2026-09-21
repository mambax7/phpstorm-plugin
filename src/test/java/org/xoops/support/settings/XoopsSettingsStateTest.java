package org.xoops.support.settings;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class XoopsSettingsStateTest {

    @Test
    public void legacyOnlyUsesCoreProfile() {
        XoopsSettingsState s = XoopsSettingsState.migrateForTest(null, "2.5");
        assertEquals("2.5", s.coreVersion);
        assertNull(s.coreProfile);
    }

    @Test
    public void newOnlyKeepsCoreVersion() {
        XoopsSettingsState s = XoopsSettingsState.migrateForTest("2.7", null);
        assertEquals("2.7", s.coreVersion);
        assertNull(s.coreProfile);
    }

    @Test
    public void bothKeysPreferCoreVersionIncludingExplicitAuto() {
        XoopsSettingsState s = XoopsSettingsState.migrateForTest("Auto", "2.5");
        assertEquals("Auto", s.coreVersion);
        assertNull(s.coreProfile);
    }

    @Test
    public void bothKeysPreferExplicitNewOverLegacy() {
        XoopsSettingsState s = XoopsSettingsState.migrateForTest("4.0", "2.5");
        assertEquals("4.0", s.coreVersion);
    }

    @Test
    public void neitherKeyDefaultsToAuto() {
        XoopsSettingsState s = XoopsSettingsState.migrateForTest(null, null);
        assertEquals("Auto", s.coreVersion);
        assertNull(s.coreProfile);
    }

    @Test
    public void blankCoreVersionFallsBackToLegacy() {
        XoopsSettingsState s = XoopsSettingsState.migrateForTest("  ", "2.5");
        assertEquals("2.5", s.coreVersion);
    }
}
