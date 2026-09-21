package org.xoops.support.inspections;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Path + source policy for the XOOPS_ROOT_PATH direct-access guard.
 * Pure string logic so it can be unit-tested without PSI.
 */
public final class XoopsRootPathGuardPolicy {

    private static final Pattern OPEN_PHP = Pattern.compile("<\\?php\\b", Pattern.CASE_INSENSITIVE);

    /** defined('XOOPS_ROOT_PATH') || exit/die(...); */
    private static final Pattern GUARD_OR = Pattern.compile(
            "(?is)^defined\\s*\\(\\s*['\"]XOOPS_ROOT_PATH['\"]\\s*\\)\\s*\\|\\|\\s*(?:exit|die)\\s*(?:\\([^;]*\\))?\\s*;"
    );

    /** if (!defined('XOOPS_ROOT_PATH')) { exit/die(...); } */
    private static final Pattern GUARD_IF = Pattern.compile(
            "(?is)^if\\s*\\(\\s*!\\s*defined\\s*\\(\\s*['\"]XOOPS_ROOT_PATH['\"]\\s*\\)\\s*\\)\\s*\\{"
                    + "\\s*(?:exit|die)\\s*(?:\\([^;]*\\))?\\s*;\\s*\\}"
    );

    private static final Pattern DECLARE = Pattern.compile("(?is)^declare\\s*\\([^;]*\\)\\s*;");
    private static final Pattern NAMESPACE = Pattern.compile(
            "(?is)^namespace\\s+[A-Za-z_\\\\][\\w\\\\]*\\s*(?:;|\\{)"
    );
    private static final Pattern USE = Pattern.compile("(?is)^use\\s+(?:function\\s+|const\\s+)?[^;]+;");

    /**
     * Directory-protection stubs: HTTP 404/403 header and/or http_response_code, optional exit/die.
     */
    private static final Pattern STUB_BODY = Pattern.compile(
            "(?is)^(?:header\\s*\\(\\s*['\"]HTTP/[0-9.]+\\s+(?:404|403)[^'\"]*['\"]\\s*\\)\\s*;\\s*"
                    + "|http_response_code\\s*\\(\\s*40[34]\\s*\\)\\s*;\\s*"
                    + "|(?:exit|die)\\s*(?:\\(\\s*(?:['\"][^'\"]*['\"])?\\s*\\))?\\s*;\\s*)+$"
    );

    /**
     * First statement is a bootstrap include of mainfile / header / admin_header / common.
     * {@code header.php} / {@code footer.php} are matched as a path segment, not {@code xoops_header.php}.
     */
    private static final Pattern BOOTSTRAP_INCLUDE = Pattern.compile(
            "(?is)^(?:include|include_once|require|require_once)\\b[^;]*"
                    + "(?:mainfile\\.php|admin_header\\.php|admin_footer\\.php|cp_header\\.php|common\\.php"
                    + "|(?<![A-Za-z_])header\\.php|(?<![A-Za-z_])footer\\.php)"
    );

    private XoopsRootPathGuardPolicy() {
    }

    /**
     * True when this file should be flagged as missing a terminating ROOT_PATH guard.
     */
    public static boolean requiresGuard(@NotNull String path, @NotNull String source) {
        String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (!(normalized.endsWith(".php") || normalized.endsWith(".inc"))) {
            return false;
        }
        if (normalized.contains("/vendor/")
                || normalized.contains("/templates_c/")
                || normalized.contains("/cache/")
                || normalized.contains("/caches/")
                || normalized.contains("/node_modules/")
                || normalized.contains("/language/")
                || normalized.contains("/tests/")
                || normalized.contains("/test/")
                || normalized.contains("/testdata/")
                || normalized.contains("/assets/")) {
            return false;
        }
        if (normalized.endsWith("/xoops_version.php")) {
            return false;
        }
        // Admin CP scripts are bootstrap entry points (they include admin_header.php).
        if (normalized.contains("/admin/") && !normalized.contains("/class/")) {
            return false;
        }

        boolean watched = normalized.contains("/modules/")
                || normalized.contains("/class/")
                || normalized.contains("/preloads/")
                || normalized.contains("/kernel/")
                || normalized.contains("/include/")
                || normalized.contains("/blocks/")
                || normalized.contains("/src/");
        if (!watched) {
            return false;
        }
        if (!source.contains("<?php") && !source.contains("<?=") && !source.contains("<?")) {
            return false;
        }
        if (hasLeadingTerminatingGuard(source)) {
            return false;
        }
        String first = firstExecutable(source);
        if (first.isEmpty()) {
            return false;
        }
        if (is404OrForbiddenStub(first)) {
            return false;
        }
        return !isBootstrapEntryPoint(first);
    }

    /**
     * True when, after {@code <?php}, declare, namespace, and use statements, the first
     * executable statement is a terminating root-path guard ({@code exit} or {@code die}).
     */
    public static boolean hasLeadingTerminatingGuard(@NotNull String text) {
        String first = firstExecutable(text);
        return !first.isEmpty() && (GUARD_OR.matcher(first).lookingAt() || GUARD_IF.matcher(first).lookingAt());
    }

    /**
     * Offset in {@code text} at which to insert the standard guard: after the leading
     * {@code <?php} / {@code declare} / {@code namespace}, and before {@code use} / the class.
     * Returns -1 when there is no leading {@code <?php}.
     */
    public static int insertOffset(@NotNull String text) {
        Matcher open = OPEN_PHP.matcher(text);
        if (!open.find()) {
            return -1;
        }
        String prefix = text.substring(0, open.start()).replace("\uFEFF", "");
        if (!prefix.isBlank()) {
            return -1;
        }
        String masked = PhpTextUtil.maskCommentsOnly(text);
        int pos = skipWs(masked, open.end());
        pos = skipPattern(masked, pos, DECLARE);
        pos = skipWs(masked, pos);
        pos = skipPattern(masked, pos, NAMESPACE);
        return skipWs(masked, pos);
    }

    static boolean is404OrForbiddenStub(@NotNull String firstExecutable) {
        return STUB_BODY.matcher(firstExecutable).matches();
    }

    static boolean isBootstrapEntryPoint(@NotNull String firstExecutable) {
        return BOOTSTRAP_INCLUDE.matcher(firstExecutable).lookingAt();
    }

    /**
     * Comment-masked source from the first executable statement after {@code <?php},
     * {@code declare}, {@code namespace}, and {@code use} statements.
     */
    static @NotNull String firstExecutable(@NotNull String text) {
        Matcher open = OPEN_PHP.matcher(text);
        if (!open.find()) {
            return "";
        }
        String masked = PhpTextUtil.maskCommentsOnly(text);
        int pos = skipWs(masked, open.end());
        pos = skipPattern(masked, pos, DECLARE);
        pos = skipWs(masked, pos);
        pos = skipPattern(masked, pos, NAMESPACE);
        pos = skipWs(masked, pos);
        while (true) {
            int afterUse = skipPattern(masked, pos, USE);
            if (afterUse == pos) {
                break;
            }
            pos = skipWs(masked, afterUse);
        }
        if (pos >= masked.length()) {
            return "";
        }
        String rest = masked.substring(pos).stripLeading();
        return rest.replaceFirst("(?s)\\?>\\s*$", "").strip();
    }

    private static int skipWs(@NotNull String s, int pos) {
        while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
            pos++;
        }
        return pos;
    }

    private static int skipPattern(@NotNull String s, int pos, @NotNull Pattern pattern) {
        if (pos >= s.length()) {
            return pos;
        }
        Matcher m = pattern.matcher(s.substring(pos));
        if (m.find() && m.start() == 0) {
            return pos + m.end();
        }
        return pos;
    }
}
