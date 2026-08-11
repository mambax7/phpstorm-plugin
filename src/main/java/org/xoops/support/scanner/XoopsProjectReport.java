package org.xoops.support.scanner;

import java.nio.file.Path;
import java.util.List;

public record XoopsProjectReport(
        boolean xoopsProject,
        Path projectRoot,
        Path webRoot,
        CoreProfile profile,
        List<XoopsModuleInfo> modules,
        List<XoopsFinding> findings
) {
    public XoopsProjectReport {
        projectRoot = projectRoot.toAbsolutePath().normalize();
        webRoot = webRoot.toAbsolutePath().normalize();
        modules = List.copyOf(modules);
        findings = List.copyOf(findings);
    }
}
