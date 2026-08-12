package org.xoops.support.scanner;

import java.nio.file.Path;

public record XoopsFinding(
        String kind,
        Path path,
        int line,
        String message
) {
    public XoopsFinding {
        path = path.toAbsolutePath().normalize();
        if (line < 1) {
            line = 1;
        }
    }
}
