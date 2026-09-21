package org.xoops.support.completion;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
            "define\\s*\\(\\s*['\"](_(?:MI|AM|MD|CO|MB)_[A-Z0-9_]+)['\"]",
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
        Matcher m = DEFINE.matcher(phpSource);
        while (m.find()) {
            out.add(new Occurrence(m.group(1), m.start(1)));
        }
        return out;
    }

    /**
     * Extract a XOOPS language-constant name from a PSI element's text
     * (quoted string, bare identifier, or Smarty token).
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
        String upper = trimmed.toUpperCase(Locale.ROOT);
        return CONSTANT_NAME.matcher(upper).matches() ? upper : null;
    }

    public static boolean isLanguagePath(@Nullable String path) {
        if (path == null) {
            return false;
        }
        String p = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        return p.contains("/language/")
                && (p.endsWith(".php") || p.endsWith("/language") || !p.contains("."));
    }

    public record Occurrence(@NotNull String name, int offset) {
    }
}
