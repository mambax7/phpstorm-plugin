package org.xoops.support.scanner;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Filesystem scan of a XOOPS tree (ported/adapted filesystem).
 * Pure NIO — safe to run off the EDT.
 */
public final class XoopsProjectScanner {

    private static final long MAX_SOURCE_BYTES = 1_500_000L;
    private static final Set<String> EXCLUDED = Set.of(
            ".git", ".gradle", ".idea", "build", "cache", "caches", "node_modules",
            "smarty_compile", "templates_c", "uploads", "vendor", "xoops_data"
    );

    private static final Pattern VERSION_25 = Pattern.compile("(?i)(?:XOOPS[ _-]?)?2\\.5(?:\\.|[^0-9]|$)");
    private static final Pattern VERSION_27 = Pattern.compile("(?i)(?:XOOPS[ _-]?)?2\\.7(?:\\.|[^0-9]|$)");
    private static final Pattern VERSION_40 = Pattern.compile("(?i)(?:XOOPS[ _-]?)?4\\.0(?:\\.|[^0-9]|$)");
    private static final Pattern REGISTERED_TEMPLATE = Pattern.compile(
            "(?is)['\"](?:file|template)['\"]\\s*=>\\s*['\"]([^'\"]+\\.tpl)['\"]"
    );
    private static final Pattern MANIFEST_DIRNAME = Pattern.compile(
            "(?is)(?:\\[['\"]dirname['\"]]\\s*=|['\"]dirname['\"]\\s*=>)\\s*['\"]([a-z0-9_-]+)['\"]"
    );

    private static final Pattern RAW_REQUEST = Pattern.compile("\\$_REQUEST\\b");
    private static final Pattern QUERY_F = Pattern.compile("->\\s*queryF\\s*\\(");
    private static final Pattern QUOTE_STRING = Pattern.compile("->\\s*quoteString\\s*\\(");
    private static final Pattern MUTATING_QUERY = Pattern.compile(
            "(?is)->\\s*query\\s*\\(\\s*['\"]\\s*(?:INSERT|UPDATE|DELETE|REPLACE|ALTER|CREATE|DROP|TRUNCATE)\\b"
    );
    private static final Pattern WRONG_SMARTY = Pattern.compile(
            "(?i)(?<!<)\\{(?:\\$|/?(?:if|foreach|include|assign|block|literal)\\b)"
    );

    public XoopsProjectReport scan(Path requestedRoot) {
        Path projectRoot = requestedRoot.toAbsolutePath().normalize();
        Path webRoot = detectWebRoot(projectRoot);
        boolean standaloneModule = Files.isRegularFile(projectRoot.resolve("xoops_version.php"))
                || Files.isRegularFile(projectRoot.resolve("module.json"));
        boolean xoopsProject = isCoreRoot(webRoot) || standaloneModule;

        if (!xoopsProject) {
            return new XoopsProjectReport(
                    false, projectRoot, projectRoot, CoreProfile.NOT_XOOPS, List.of(), List.of()
            );
        }

        List<Path> moduleRoots = findModuleRoots(projectRoot, webRoot, standaloneModule);
        List<XoopsModuleInfo> modules = moduleRoots.stream()
                .map(this::inspectModule)
                .sorted(Comparator.comparing(XoopsModuleInfo::dirname, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<XoopsFinding> findings = new ArrayList<>();
        for (Path moduleRoot : moduleRoots) {
            scanModule(moduleRoot, findings);
        }
        findings.sort(Comparator
                .comparing((XoopsFinding f) -> f.path().toString(), String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(XoopsFinding::line));

        CoreProfile profile = standaloneModule && !isCoreRoot(webRoot)
                ? CoreProfile.MODULE_ONLY
                : detectProfile(projectRoot, webRoot);

        return new XoopsProjectReport(true, projectRoot, webRoot, profile, modules, findings);
    }

    private static Path detectWebRoot(Path projectRoot) {
        if (isCoreRoot(projectRoot)) {
            return projectRoot;
        }
        Path htdocs = projectRoot.resolve("htdocs");
        if (isCoreRoot(htdocs)) {
            return htdocs;
        }
        return projectRoot;
    }

    private static boolean isCoreRoot(Path candidate) {
        return Files.isRegularFile(candidate.resolve("mainfile.php"));
    }

    private static List<Path> findModuleRoots(Path projectRoot, Path webRoot, boolean standaloneModule) {
        if (standaloneModule && !isCoreRoot(webRoot)) {
            return List.of(projectRoot);
        }
        Path modulesDirectory = webRoot.resolve("modules");
        if (!Files.isDirectory(modulesDirectory)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(modulesDirectory)) {
            return paths
                    .filter(Files::isDirectory)
                    .filter(path -> Files.isRegularFile(path.resolve("xoops_version.php"))
                            || Files.isRegularFile(path.resolve("module.json")))
                    .toList();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private XoopsModuleInfo inspectModule(Path moduleRoot) {
        return new XoopsModuleInfo(
                readModuleDirname(moduleRoot),
                moduleRoot,
                Files.isRegularFile(moduleRoot.resolve("xoops_version.php")),
                Files.isRegularFile(moduleRoot.resolve("module.json")),
                countFiles(moduleRoot.resolve("templates"), ".tpl"),
                countFiles(moduleRoot.resolve("language"), ".php"),
                countFiles(moduleRoot.resolve("preloads"), ".php"),
                countFiles(moduleRoot.resolve("class"), ".php") + countFiles(moduleRoot.resolve("src"), ".php")
        );
    }

    private static String readModuleDirname(Path moduleRoot) {
        String manifest = readSmallFile(moduleRoot.resolve("xoops_version.php")).orElse("");
        Matcher matcher = MANIFEST_DIRNAME.matcher(manifest);
        return matcher.find() ? matcher.group(1) : moduleRoot.getFileName().toString();
    }

    private static long countFiles(Path directory, String suffix) {
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        try (Stream<Path> paths = Files.walk(directory, 8)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(suffix))
                    .count();
        } catch (IOException ignored) {
            return 0;
        }
    }

    private static CoreProfile detectProfile(Path projectRoot, Path webRoot) {
        // Prefer core include/version.php — composer.json dependency ranges often mislead (e.g. "2.5").
        for (Path candidate : List.of(
                webRoot.resolve("include/version.php"),
                webRoot.resolve("include/common.php")
        )) {
            Optional<String> body = readSmallFile(candidate);
            if (body.isEmpty()) {
                continue;
            }
            String text = body.get();
            if (VERSION_40.matcher(text).find()) {
                return CoreProfile.XOOPS_40;
            }
            if (VERSION_27.matcher(text).find()) {
                return CoreProfile.XOOPS_27;
            }
            if (VERSION_25.matcher(text).find()) {
                return CoreProfile.XOOPS_25;
            }
        }
        // Fallback: bind package name to its version constraint (not independent whole-file matches).
        for (Path candidate : List.of(webRoot.resolve("composer.json"), projectRoot.resolve("composer.json"))) {
            Optional<String> body = readSmallFile(candidate);
            if (body.isEmpty()) {
                continue;
            }
            CoreProfile fromComposer = profileFromComposerJson(body.get());
            if (fromComposer != CoreProfile.UNKNOWN) {
                return fromComposer;
            }
        }
        return CoreProfile.UNKNOWN;
    }

    /**
     * Match a single Composer require entry whose package name contains "xoops"
     * and apply version patterns only to that entry's constraint.
     */
    private static CoreProfile profileFromComposerJson(@NotNull String json) {
        // "xoops/something": "2.5.11" or "xoopsmodules/foo": "^2.7"
        Pattern entry = Pattern.compile(
                "(?is)\"([^\"]*xoops[^\"]*)\"\\s*:\\s*\"([^\"]+)\""
        );
        Matcher m = entry.matcher(json);
        while (m.find()) {
            String packageName = m.group(1).toLowerCase(Locale.ROOT);
            String constraint = m.group(2);
            // Skip unrelated URL strings
            if (packageName.contains("http") || packageName.contains("github.com")) {
                continue;
            }
            if (VERSION_40.matcher(constraint).find()) {
                return CoreProfile.XOOPS_40;
            }
            if (VERSION_27.matcher(constraint).find()) {
                return CoreProfile.XOOPS_27;
            }
            if (VERSION_25.matcher(constraint).find()) {
                return CoreProfile.XOOPS_25;
            }
        }
        return CoreProfile.UNKNOWN;
    }

    private void scanModule(Path moduleRoot, List<XoopsFinding> findings) {
        checkRegisteredTemplates(moduleRoot, findings);
        try (Stream<Path> paths = Files.walk(moduleRoot, 12)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> !isExcluded(path, moduleRoot))
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.endsWith(".php") || n.endsWith(".tpl");
                    })
                    .forEach(path -> scanSourceFile(path, findings));
        } catch (IOException exception) {
            findings.add(new XoopsFinding(
                    "SCAN_ERROR",
                    moduleRoot,
                    1,
                    "Could not scan module: " + exception.getMessage()
            ));
        }
    }

    private static boolean isExcluded(Path path, Path moduleRoot) {
        Path relative = moduleRoot.relativize(path);
        for (Path part : relative) {
            if (EXCLUDED.contains(part.toString().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static void scanSourceFile(Path path, List<XoopsFinding> findings) {
        String content = readSmallFile(path).orElse(null);
        if (content == null) {
            return;
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".php")) {
            // One finding per kind per file (addFirst) — keeps large modules usable.
            addFirst(findings, content, path, RAW_REQUEST, "RAW_REQUEST",
                    "Avoid $_REQUEST; use a scoped Xmf\\Request API.");
            addFirst(findings, content, path, QUERY_F, "DEPRECATED_QUERY_F",
                    "queryF() is deprecated; use query() for reads or exec() for writes.");
            addFirst(findings, content, path, QUOTE_STRING, "DEPRECATED_QUOTE_STRING",
                    "quoteString() is deprecated; use quote().");
            addFirst(findings, content, path, MUTATING_QUERY, "MUTATING_QUERY",
                    "Mutating SQL must use exec(), not query().");
        } else if (name.endsWith(".tpl")) {
            addFirst(findings, content, path, WRONG_SMARTY, "WRONG_SMARTY_DELIMITER",
                    "XOOPS Smarty templates use <{ and }> delimiters.");
        }
    }

    private static void addFirst(
            List<XoopsFinding> findings,
            String content,
            Path path,
            Pattern pattern,
            String kind,
            String message
    ) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            findings.add(new XoopsFinding(kind, path, lineAt(content, matcher.start()), message));
        }
    }

    private static void checkRegisteredTemplates(Path moduleRoot, List<XoopsFinding> findings) {
        Path manifest = moduleRoot.resolve("xoops_version.php");
        String content = readSmallFile(manifest).orElse(null);
        if (content == null) {
            return;
        }
        Matcher matcher = REGISTERED_TEMPLATE.matcher(content);
        while (matcher.find()) {
            String template = matcher.group(1).replace('\\', '/');
            boolean exists = Files.isRegularFile(moduleRoot.resolve("templates").resolve(template))
                    || Files.isRegularFile(moduleRoot.resolve("blocks").resolve(template))
                    || Files.isRegularFile(moduleRoot.resolve(template));
            if (!exists) {
                findings.add(new XoopsFinding(
                        "MISSING_REGISTERED_TEMPLATE",
                        manifest,
                        lineAt(content, matcher.start(1)),
                        "Registered template is missing: " + template
                ));
            }
        }
    }

    private static int lineAt(String content, int offset) {
        int line = 1;
        int end = Math.min(offset, content.length());
        for (int i = 0; i < end; i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static Optional<String> readSmallFile(Path path) {
        try {
            if (!Files.isRegularFile(path) || Files.size(path) > MAX_SOURCE_BYTES) {
                return Optional.empty();
            }
            return Optional.of(Files.readString(path));
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }
}
