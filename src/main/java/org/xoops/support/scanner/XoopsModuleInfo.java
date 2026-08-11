package org.xoops.support.scanner;

import java.nio.file.Path;

public record XoopsModuleInfo(
        String dirname,
        Path root,
        boolean legacyManifest,
        boolean modernManifest,
        long templateCount,
        long languageFileCount,
        long preloadCount,
        long classCount
) {
    public XoopsModuleInfo {
        root = root.toAbsolutePath().normalize();
    }

    public String manifestLabel() {
        if (legacyManifest && modernManifest) {
            return "hybrid";
        }
        if (modernManifest) {
            return "module.json";
        }
        return "xoops_version.php";
    }
}
