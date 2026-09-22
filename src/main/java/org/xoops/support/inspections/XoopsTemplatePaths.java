package org.xoops.support.inspections;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/** Resolves registered template paths while retaining their actual disk spelling. */
public final class XoopsTemplatePaths {
    private XoopsTemplatePaths() {
    }

    /** Actual module-relative path, preferring exact spelling; null when no file matches. */
    public static @Nullable String existingPath(@NotNull VirtualFile root, @NotNull String relative) {
        VirtualFile current = root;
        List<String> actual = new ArrayList<>();
        for (String part : relative.split("/")) {
            if (!current.isDirectory()) {
                return null;
            }
            String name = matchingName(Arrays.stream(current.getChildren()).map(VirtualFile::getName).toList(), part);
            if (name == null) {
                return null;
            }
            actual.add(name);
            current = current.findChild(name);
            if (current == null) {
                return null;
            }
        }
        return current.isDirectory() ? null : String.join("/", actual);
    }

    /** Scanner equivalent of the VFS lookup; I/O errors remain distinguishable from missing files. */
    public static @Nullable String existingPath(@NotNull Path root, @NotNull String relative) throws IOException {
        Path current = root;
        List<String> actual = new ArrayList<>();
        for (String part : relative.split("/")) {
            if (!Files.isDirectory(current)) {
                return null;
            }
            String name;
            try (Stream<Path> children = Files.list(current)) {
                name = matchingName(children.map(p -> p.getFileName().toString()).toList(), part);
            }
            if (name == null) {
                return null;
            }
            actual.add(name);
            current = current.resolve(name);
        }
        return Files.isRegularFile(current) ? String.join("/", actual) : null;
    }

    private static @Nullable String matchingName(List<String> names, String requested) {
        if (requested.isEmpty() || requested.equals(".") || requested.equals("..")) {
            return null;
        }
        String fallback = null;
        for (String name : names) {
            if (name.equals(requested)) {
                return name;
            }
            if (name.equalsIgnoreCase(requested)) {
                fallback = name;
            }
        }
        return fallback;
    }
}
