package org.xoops.support.scanner;

/**
 * Detected XOOPS core line .
 */
public enum CoreProfile {
    XOOPS_25("XOOPS 2.5.x"),
    XOOPS_27("XOOPS 2.7.x"),
    XOOPS_40("XOOPS 4.0"),
    MODULE_ONLY("Standalone module"),
    UNKNOWN("XOOPS (version unknown)"),
    NOT_XOOPS("Not a XOOPS project");

    private final String displayName;

    CoreProfile(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
