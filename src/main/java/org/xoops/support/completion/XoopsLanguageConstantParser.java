package org.xoops.support.completion;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xoops.support.inspections.PhpTextUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses {@code define('_MI_…', …)} (and AM/MD/CO/MB) from a language PHP file.
 * Pure regex so it can be unit-tested without PSI.
 */
public final class XoopsLanguageConstantParser {

    public static final Pattern DEFINE = Pattern.compile(
            "(?<![A-Za-z0-9_$\\\\>:])\\\\?define\\b\\s*\\(\\s*['\"](_(?:MI|AM|MD|CO|MB)_[A-Z0-9_]+)['\"]",
            Pattern.CASE_INSENSITIVE
    );

    public static final Pattern CONSTANT_NAME = Pattern.compile(
            "_(?:MI|AM|MD|CO|MB)_[A-Z0-9_]+"
    );

    private XoopsLanguageConstantParser() {
    }

    /**
     * @return occurrences of {@code define('NAME'} as (name, offset-of-name)
     */
    public static @NotNull List<Occurrence> parse(@NotNull String phpSource) {
        List<Occurrence> out = new ArrayList<>();
        // Comments masked, strings kept: a commented-out define() is not a definition,
        // and the mask preserves offsets so Ctrl+B still lands on the real name.
        // The fully masked copy tells whether the define keyword itself sits in code:
        // inside a string literal it is blanked there, so the occurrence is skipped.
        String code = PhpTextUtil.maskCommentsAndStrings(phpSource);
        Matcher m = DEFINE.matcher(PhpTextUtil.maskCommentsOnly(phpSource));
        while (m.find()) {
            if (code.charAt(m.start()) == ' ') {
                continue;
            }
            out.add(new Occurrence(m.group(1), m.start(1)));
        }
        return out;
    }

    /**
     * Extract a XOOPS language-constant name from a PSI element's text
     * (quoted string, bare identifier, {@code $smarty.const._MI_FOO}, or Smarty token).
     * Recognition is case-insensitive; the returned spelling is the original.
     */
    public static @Nullable String extractName(@NotNull String elementText) {
        String trimmed = elementText.strip();
        if (trimmed.length() >= 2) {
            char a = trimmed.charAt(0);
            char b = trimmed.charAt(trimmed.length() - 1);
            if ((a == '\'' && b == '\'') || (a == '"' && b == '"')) {
                trimmed = trimmed.substring(1, trimmed.length() - 1);
            }
        }
        if (trimmed.startsWith("<{") && trimmed.endsWith("}>") && trimmed.length() > 4) {
            trimmed = trimmed.substring(2, trimmed.length() - 2).strip();
        }
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (upper.startsWith("$SMARTY.CONST.")) {
            trimmed = trimmed.substring("$SMARTY.CONST.".length());
            upper = trimmed.toUpperCase(Locale.ROOT);
        }
        return CONSTANT_NAME.matcher(upper).matches() ? trimmed : null;
    }

    public static boolean isLanguagePath(@Nullable String path) {
        if (path == null) {
            return false;
        }
        String p = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        return (p.contains("/language/") || p.endsWith("/language"))
                && (p.endsWith(".php") || p.endsWith("/language") || !p.contains("."));
    }

    public record Occurrence(@NotNull String name, int offset) {
    }
}
