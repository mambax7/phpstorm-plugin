package org.xoops.support.scanner;

import com.intellij.openapi.progress.ProgressManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xoops.support.inspections.PhpTextUtil;
import org.xoops.support.inspections.XoopsManifestTemplates;
import org.xoops.support.inspections.XoopsTemplatePaths;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
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
 *
 * <p>Cancellation: {@link ProgressManager#checkCanceled()} is called once per module and
 * throttled every {@link #CANCEL_CHECK_EVERY} paths inside file walks so monorepo scans stay
 * responsive without paying a cancel-check on every path.
 */
public final class XoopsProjectScanner {

    private static final long MAX_SOURCE_BYTES = 1_500_000L;
    /** Paths between {@link ProgressManager#checkCanceled()} in tight file walks. */
    private static final int CANCEL_CHECK_EVERY = 32;
    private static final Set<String> EXCLUDED = Set.of(
            ".git", ".gradle", ".idea", "build", "cache", "caches", "node_modules",
            "smarty_compile", "templates_c", "uploads", "vendor", "xoops_data"
    );

    static final Pattern VERSION_25 = Pattern.compile("(?i)(?:XOOPS[ _-]?)?(?<!\\d)2\\.5(?:[^0-9]|$)");
    static final Pattern VERSION_27 = Pattern.compile("(?i)(?:XOOPS[ _-]?)?(?<!\\d)2\\.7(?:[^0-9]|$)");
    static final Pattern VERSION_40 = Pattern.compile("(?i)(?:XOOPS[ _-]?)?(?<!\\d)4\\.0(?:[^0-9]|$)");
    private static final Pattern MANIFEST_DIRNAME = Pattern.compile(
            "(?is)(?:\\[['\"]dirname['\"]]\\s*=|['\"]dirname['\"]\\s*=>)\\s*['\"]([a-z0-9_-]+)['\"]"
    );

    static final Pattern RAW_REQUEST = Pattern.compile("\\$_REQUEST\\b");
    static final Pattern QUERY_F = Pattern.compile("->\\s*queryF\\s*\\(");
    static final Pattern QUOTE_STRING = Pattern.compile("->\\s*quoteString\\s*\\(");
    static final Pattern MUTATING_QUERY = Pattern.compile(
            "(?is)->\\s*query\\s*\\(\\s*['\"]\\s*(?:INSERT|UPDATE|DELETE|REPLACE|ALTER|CREATE|DROP|TRUNCATE)\\b"
    );
    static final Pattern WRONG_SMARTY = Pattern.compile(
            "(?i)(?<!<)\\{(?:\\$|/?(?:if|foreach|include|assign|block|literal)\\b)"
    );

    public XoopsProjectReport scan(Path requestedRoot) {
        return scan(requestedRoot, "Auto");
    }

    public XoopsProjectReport scan(Path requestedRoot, @NotNull String coreVersionSetting) {
        Path projectRoot = requestedRoot.toAbsolutePath().normalize();
        Path webRoot = detectWebRoot(projectRoot);
        boolean standaloneModule = Files.isRegularFile(projectRoot.resolve("xoops_version.php"))
                || Files.isRegularFile(projectRoot.resolve("module.json"));
        boolean xoopsProject = isCoreRoot(webRoot) || standaloneModule;

        if (!xoopsProject) {
            return new XoopsProjectReport(
                    false, projectRoot, projectRoot, CoreVersion.NOT_XOOPS, List.of(), List.of()
            );
        }

        List<XoopsFinding> findings = new ArrayList<>();
        List<Path> moduleRoots = findModuleRoots(projectRoot, webRoot, standaloneModule, findings);

        // Inspect + scan each module in one cancel-aware loop so Cancel is observed
        // during metadata walks (inspectModule / countFiles), not only during source scan.
        // One check per module (not per file) at this level; file walks throttle below.
        List<XoopsModuleInfo> modules = new ArrayList<>();
        for (Path moduleRoot : moduleRoots) {
            ProgressManager.checkCanceled();
            modules.add(inspectModule(moduleRoot, findings));
            scanModule(moduleRoot, findings);
        }
        modules.sort(Comparator.comparing(XoopsModuleInfo::dirname, String.CASE_INSENSITIVE_ORDER));
        findings.sort(Comparator
                .comparing((XoopsFinding f) -> f.path().toString(), String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(XoopsFinding::line));

        CoreVersion coreVersion = resolveScanCoreVersion(
                coreVersionSetting, standaloneModule && !isCoreRoot(webRoot), projectRoot, webRoot
        );

        return new XoopsProjectReport(true, projectRoot, webRoot, coreVersion, modules, findings);
    }

    /**
     * Explicit Settings → Core Version wins. {@code Auto} (and unknown values) fall back
     * to layout detection ({@code MODULE_ONLY}) or {@link #detectCoreVersion}.
     */
    static @NotNull CoreVersion resolveScanCoreVersion(
            @NotNull String setting,
            boolean standaloneModule,
            @NotNull Path projectRoot,
            @NotNull Path webRoot
    ) {
        CoreVersion fromSetting = coreVersionFromSetting(setting);
        if (fromSetting != null) {
            return fromSetting;
        }
        if (standaloneModule) {
            return CoreVersion.MODULE_ONLY;
        }
        return detectCoreVersion(projectRoot, webRoot);
    }

    static @Nullable CoreVersion coreVersionFromSetting(@NotNull String setting) {
        return switch (setting.trim()) {
            case "2.5" -> CoreVersion.XOOPS_25;
            case "2.7" -> CoreVersion.XOOPS_27;
            case "4.0" -> CoreVersion.XOOPS_40;
            default -> null;
        };
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

    private static List<Path> findModuleRoots(Path projectRoot, Path webRoot, boolean standaloneModule, List<XoopsFinding> findings) {
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
        } catch (IOException | UncheckedIOException exception) {
            findings.add(scanError(modulesDirectory, "Could not list modules: " + exception.getMessage()));
            return List.of();
        }
    }

    private XoopsModuleInfo inspectModule(Path moduleRoot, List<XoopsFinding> findings) {
        return new XoopsModuleInfo(
                readModuleDirname(moduleRoot),
                moduleRoot,
                Files.isRegularFile(moduleRoot.resolve("xoops_version.php")),
                Files.isRegularFile(moduleRoot.resolve("module.json")),
                countFiles(moduleRoot.resolve("templates"), ".tpl", findings),
                countFiles(moduleRoot.resolve("language"), ".php", findings),
                countFiles(moduleRoot.resolve("preloads"), ".php", findings),
                countFiles(moduleRoot.resolve("class"), ".php", findings) + countFiles(moduleRoot.resolve("src"), ".php", findings)
        );
    }

    private static String readModuleDirname(Path moduleRoot) {
        String manifest = readSmallFile(moduleRoot.resolve("xoops_version.php")).orElse("");
        Matcher matcher = MANIFEST_DIRNAME.matcher(manifest);
        return matcher.find() ? matcher.group(1) : moduleRoot.getFileName().toString();
    }

    private static long countFiles(Path directory, String suffix, List<XoopsFinding> findings) {
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        try (Stream<Path> paths = Files.walk(directory, 8)) {
            // Mutable counter for the lambda — throttle cancel checks in large trees.
            int[] seen = {0};
            return paths
                    .filter(path -> {
                        checkCanceledEvery(seen);
                        return Files.isRegularFile(path);
                    })
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(suffix))
                    .count();
        } catch (IOException | UncheckedIOException exception) {
            findings.add(scanError(directory, "Could not count files: " + exception.getMessage()));
            return 0;
        }
    }

    /**
     * Call {@link ProgressManager#checkCanceled()} every {@link #CANCEL_CHECK_EVERY} paths.
     * Keeps cancellation responsive without per-path overhead on large monorepos.
     */
    private static void checkCanceledEvery(int[] pathCounter) {
        if (++pathCounter[0] % CANCEL_CHECK_EVERY == 0) {
            ProgressManager.checkCanceled();
        }
    }

    private static CoreVersion detectCoreVersion(Path projectRoot, Path webRoot) {
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
                return CoreVersion.XOOPS_40;
            }
            if (VERSION_27.matcher(text).find()) {
                return CoreVersion.XOOPS_27;
            }
            if (VERSION_25.matcher(text).find()) {
                return CoreVersion.XOOPS_25;
            }
        }
        // Fallback: bind package name to its version constraint (not independent whole-file matches).
        for (Path candidate : List.of(webRoot.resolve("composer.json"), projectRoot.resolve("composer.json"))) {
            Optional<String> body = readSmallFile(candidate);
            if (body.isEmpty()) {
                continue;
            }
            CoreVersion fromComposer = coreVersionFromComposerJson(body.get());
            if (fromComposer != CoreVersion.UNKNOWN) {
                return fromComposer;
            }
        }
        return CoreVersion.UNKNOWN;
    }

    /**
     * Match a single Composer require entry whose package name contains "xoops"
     * and apply version patterns only to that entry's constraint.
     */
    private static CoreVersion coreVersionFromComposerJson(@NotNull String json) {
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
                return CoreVersion.XOOPS_40;
            }
            if (VERSION_27.matcher(constraint).find()) {
                return CoreVersion.XOOPS_27;
            }
            if (VERSION_25.matcher(constraint).find()) {
                return CoreVersion.XOOPS_25;
            }
        }
        return CoreVersion.UNKNOWN;
    }

    private void scanModule(Path moduleRoot, List<XoopsFinding> findings) {
        checkRegisteredTemplates(moduleRoot, findings);
        try (Stream<Path> paths = Files.walk(moduleRoot, 12)) {
            int[] seen = {0};
            paths.filter(Files::isRegularFile)
                    .filter(path -> !isExcluded(path, moduleRoot))
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.endsWith(".php") || n.endsWith(".tpl");
                    })
                    .forEach(path -> {
                        checkCanceledEvery(seen);
                        scanSourceFile(path, findings);
                    });
        } catch (UncheckedIOException exception) {
            findings.add(scanError(moduleRoot, "Could not scan module: " + walkMessage(exception)));
        } catch (IOException exception) {
            findings.add(scanError(moduleRoot, "Could not scan module: " + exception.getMessage()));
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

    static void checkRegisteredTemplates(Path moduleRoot, List<XoopsFinding> findings) {
        Path manifest = moduleRoot.resolve("xoops_version.php");
        if (!Files.isRegularFile(manifest)) {
            return; // module.json-only module: no legacy manifest to compare templates against
        }
        Optional<String> read = readSmallFile(manifest);
        if (read.isEmpty()) {
            // Unreadable or oversized manifest: no data to compare against, so report
            // that instead of flagging every template as unregistered.
            findings.add(new XoopsFinding(
                    "SCAN_ERROR",
                    manifest,
                    1,
                    "xoops_version.php could not be read (unreadable or larger than " + MAX_SOURCE_BYTES + " bytes)"
            ));
            return;
        }
        String content = read.get();
        Set<String> registered = new LinkedHashSet<>();
        if (!content.isEmpty()) {
            // Masked copy keeps offsets, so lineAt() on the original content stays right.
            for (XoopsManifestTemplates.Registration reg
                    : XoopsManifestTemplates.find(PhpTextUtil.maskCommentsOnly(content))) {
                String template = reg.name();
                String relative = XoopsManifestTemplates.diskPath(template, reg.block());
                registered.add(relative.toLowerCase(Locale.ROOT));
                String actual;
                try {
                    actual = XoopsTemplatePaths.existingPath(moduleRoot, relative);
                } catch (IOException | UncheckedIOException exception) {
                    findings.add(scanError(manifest, "Could not locate template: " + exception.getMessage()));
                    continue;
                }
                if (actual != null && !relative.equals(actual)) {
                    findings.add(new XoopsFinding(
                            "TEMPLATE_CASE_MISMATCH", manifest, lineAt(content, reg.nameOffset()),
                            "Template filename case mismatch: " + relative + " (on disk: " + actual + ")"));
                } else if (actual == null) {
                    findings.add(new XoopsFinding(
                            "MISSING_REGISTERED_TEMPLATE",
                            manifest,
                            lineAt(content, reg.nameOffset()),
                            "Registered template is missing: " + template
                    ));
                }
            }
        }
        addUnregisteredTemplates(moduleRoot, moduleRoot.resolve("templates"), registered, findings);
        addUnregisteredTemplates(moduleRoot, moduleRoot.resolve("blocks"), registered, findings);
    }

    private static void addUnregisteredTemplates(
            Path moduleRoot,
            Path directory,
            Set<String> registered,
            List<XoopsFinding> findings
    ) {
        if (!Files.isDirectory(directory)) {
            return;
        }
        int[] seen = {0};
        try (Stream<Path> paths = Files.walk(directory, 6)) {
            paths.peek(p -> checkCanceledEvery(seen))
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".tpl"))
                    .forEach(path -> {
                        String relative = moduleRoot.relativize(path).toString().replace('\\', '/');
                        String key = relative.toLowerCase(Locale.ROOT);
                        boolean listed = registered.contains(key);
                        if (!listed) {
                            findings.add(new XoopsFinding(
                                    "UNREGISTERED_TEMPLATE",
                                    path,
                                    1,
                                    "Template is not registered in xoops_version.php: " + relative
                            ));
                        }
                    });
        } catch (UncheckedIOException exception) {
            findings.add(scanError(directory, "Could not scan templates: " + walkMessage(exception)));
        } catch (IOException exception) {
            findings.add(scanError(directory, "Could not scan templates: " + exception.getMessage()));
        }
    }

    private static @NotNull XoopsFinding scanError(@NotNull Path path, @NotNull String message) {
        return new XoopsFinding("SCAN_ERROR", path, 1, message);
    }

    private static @NotNull String walkMessage(@NotNull UncheckedIOException exception) {
        Throwable cause = exception.getCause();
        String detail = cause != null && cause.getMessage() != null
                ? cause.getMessage()
                : exception.getMessage();
        return detail == null ? exception.getClass().getSimpleName() : detail;
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
