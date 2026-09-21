package org.xoops.support.inspections;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Path + source policy for the XOOPS_ROOT_PATH direct-access guard.
 * Pure string logic so it can be unit-tested without PSI.
 */
public final class XoopsRootPathGuardPolicy {

    private static final Pattern OPEN_PHP = Pattern.compile("<\\?php\\b", Pattern.CASE_INSENSITIVE);
    /** {@code <?} not followed by php, =, or xml. */
    private static final Pattern OPEN_SHORT = Pattern.compile("<\\?(?!php|=|xml\\b)", Pattern.CASE_INSENSITIVE);

    /** defined('XOOPS_ROOT_PATH') || exit/die */
    private static final Pattern GUARD_OR = Pattern.compile(
            "(?i)^defined\\s*\\(\\s*['\"]XOOPS_ROOT_PATH['\"]\\s*\\)\\s*\\|\\|\\s*(?:exit|die)\\b"
    );

    /** if (!defined('XOOPS_ROOT_PATH')) { exit/die */
    private static final Pattern GUARD_IF = Pattern.compile(
            "(?i)^if\\s*\\(\\s*!\\s*defined\\s*\\(\\s*['\"]XOOPS_ROOT_PATH['\"]\\s*\\)\\s*\\)\\s*\\{"
                    + "\\s*(?:exit|die)\\b"
    );

    private static final Pattern DECLARE = Pattern.compile("(?i)^declare\\s*\\([^;]*\\)\\s*;");
    /** Named {@code namespace Foo;} / {@code namespace Foo {} } or anonymous {@code namespace {} }. */
    private static final Pattern NAMESPACE = Pattern.compile(
            "(?i)^namespace(?:\\s+[A-Za-z_][\\w\\\\]*)?\\s*[;{]"
    );
    private static final Pattern USE = Pattern.compile("(?i)^use\\s+(?:function\\s+|const\\s+)?[^;]+;");

    private static final Pattern STUB_HEADER = Pattern.compile(
            "(?i)header\\s*\\(\\s*['\"]HTTP/[0-9.]+\\s+40[34]\\b[^'\"]*['\"]\\s*\\)"
    );
    private static final Pattern STUB_CODE = Pattern.compile(
            "(?i)http_response_code\\s*\\(\\s*40[34]\\s*\\)"
    );
    private static final Pattern STUB_EXIT = Pattern.compile(
            "(?i)(?:exit|die)(?:\\s*\\(\\s*(?:['\"][^'\"]*['\"]|\\d+)?\\s*\\))?"
    );

    private static final Pattern BOOTSTRAP_INCLUDE = Pattern.compile(
            "(?i)^(?:include|include_once|require|require_once)\\b[^;]*"
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
        if (!containsOpenTag(source)) {
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
     * True when, after {@code <?php}/{@code <?}, declare, namespace, and use statements, the first
     * executable statement is a terminating root-path guard ({@code exit} or {@code die}).
     */
    public static boolean hasLeadingTerminatingGuard(@NotNull String text) {
        String first = firstExecutable(text);
        return !first.isEmpty() && (GUARD_OR.matcher(first).lookingAt() || GUARD_IF.matcher(first).lookingAt());
    }

    /**
     * Offset at which to insert the standard guard: after the leading open tag,
     * every leading {@code declare}, and {@code namespace} (including {@code namespace { }),
     * and before {@code use} / the class. PHP allows executable code between namespace and use.
     * Returns -1 when there is no leading open tag.
     */
    public static int insertOffset(@NotNull String text) {
        int openEnd = leadingOpenTagEnd(text);
        if (openEnd < 0) {
            return -1;
        }
        String masked = PhpTextUtil.maskCommentsOnly(text);
        return skipDeclaresAndNamespace(masked, skipWs(masked, openEnd));
    }

    static boolean is404OrForbiddenStub(@NotNull String firstExecutable) {
        boolean any = false;
        for (String raw : splitStatements(firstExecutable)) {
            String stmt = raw.strip();
            if (stmt.isEmpty()) {
                continue;
            }
            any = true;
            if (STUB_HEADER.matcher(stmt).matches()
                    || STUB_CODE.matcher(stmt).matches()
                    || STUB_EXIT.matcher(stmt).matches()) {
                continue;
            }
            return false;
        }
        return any;
    }

    /** Split on {@code ;} outside single/double quotes. */
    private static @NotNull List<String> splitStatements(@NotNull String code) {
        List<String> out = new ArrayList<>();
        char quote = 0;
        int start = 0;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == ';') {
                out.add(code.substring(start, i));
                start = i + 1;
            }
        }
        out.add(code.substring(start));
        return out;
    }

    static boolean isBootstrapEntryPoint(@NotNull String firstExecutable) {
        return BOOTSTRAP_INCLUDE.matcher(firstExecutable).lookingAt();
    }

    /**
     * Comment-masked source from the first executable statement after open tag,
     * {@code declare}, {@code namespace}, and {@code use} statements.
     */
    static @NotNull String firstExecutable(@NotNull String text) {
        int openEnd = firstOpenTagEnd(text);
        if (openEnd < 0) {
            return "";
        }
        String masked = PhpTextUtil.maskCommentsOnly(text);
        int pos = skipDeclaresAndNamespace(masked, skipWs(masked, openEnd));
        pos = skipUseStatements(masked, pos);
        if (pos >= masked.length()) {
            return "";
        }
        String rest = masked.substring(pos).stripLeading();
        return rest.replaceFirst("(?s)\\?>\\s*$", "").strip();
    }

    /** Any {@code <?php} / {@code <?} anywhere, e.g. an include that starts with HTML. */
    static boolean containsOpenTag(@NotNull String text) {
        return firstOpenTagEnd(text) >= 0;
    }

    /** End offset of the first {@code <?php} / {@code <?} at any position. -1 if none. */
    private static int firstOpenTagEnd(@NotNull String text) {
        Matcher php = OPEN_PHP.matcher(text);
        Matcher sh = OPEN_SHORT.matcher(text);
        boolean hasPhp = php.find();
        boolean hasShort = sh.find();
        if (hasPhp && (!hasShort || php.start() <= sh.start())) {
            return php.end();
        }
        return hasShort ? sh.end() : -1;
    }

    /**
     * End offset of a file-leading {@code <?php} or {@code <?}. -1 if none.
     */
    static int leadingOpenTagEnd(@NotNull String text) {
        Matcher php = OPEN_PHP.matcher(text);
        if (php.find() && isFileLeading(text, php.start())) {
            return php.end();
        }
        Matcher sh = OPEN_SHORT.matcher(text);
        if (sh.find() && isFileLeading(text, sh.start())) {
            return sh.end();
        }
        return -1;
    }

    private static boolean isFileLeading(@NotNull String text, int tagStart) {
        return text.substring(0, tagStart).replace("\uFEFF", "").isBlank();
    }

    private static int skipDeclaresAndNamespace(@NotNull String masked, int pos) {
        int cursor = pos;
        while (true) {
            int after = skipPattern(masked, cursor, DECLARE);
            if (after == cursor) {
                break;
            }
            cursor = skipWs(masked, after);
        }
        cursor = skipPattern(masked, cursor, NAMESPACE);
        return skipWs(masked, cursor);
    }

    private static int skipUseStatements(@NotNull String masked, int pos) {
        int cursor = pos;
        while (true) {
            int after = skipPattern(masked, cursor, USE);
            if (after == cursor) {
                break;
            }
            cursor = skipWs(masked, after);
        }
        return cursor;
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
