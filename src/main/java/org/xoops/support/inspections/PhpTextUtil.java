package org.xoops.support.inspections;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight helpers for text-based XOOPS inspections.
 */
public final class PhpTextUtil {

    private PhpTextUtil() {
    }

    static boolean isPhpFile(@Nullable PsiFile file) {
        if (file == null) {
            return false;
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".php") || name.endsWith(".inc");
    }

    /**
     * True for the view-provider's base-language PSI file. PhpStorm .php files have
     * PHP + HTML trees; a {@code visitFile} regex walk on both doubles every finding.
     */
    static boolean isPrimaryPsiFile(@Nullable PsiFile file) {
        if (file == null) {
            return false;
        }
        var viewProvider = file.getViewProvider();
        PsiFile base = viewProvider.getPsi(viewProvider.getBaseLanguage());
        return base == null || file.equals(base);
    }

    static boolean looksLikeLanguageFile(@NotNull PsiFile file) {
        String path = file.getVirtualFile() != null
                ? file.getVirtualFile().getPath().replace('\\', '/').toLowerCase(Locale.ROOT)
                : file.getName().toLowerCase(Locale.ROOT);
        return path.contains("/language/")
                || path.endsWith("/modinfo.php")
                || path.endsWith("/main.php") && path.contains("/language/");
    }

    static boolean looksLikeVendorOrCache(@NotNull PsiFile file) {
        if (file.getVirtualFile() == null) {
            return false;
        }
        String path = file.getVirtualFile().getPath().replace('\\', '/').toLowerCase(Locale.ROOT);
        return path.contains("/vendor/")
                || path.contains("/templates_c/")
                || path.contains("/cache/")
                || path.contains("/node_modules/");
    }

    /**
     * Mask comments only (keep string/heredoc contents) so call-site patterns that
     * need literal tokens still work while ignoring commented-out code.
     */
    public static @NotNull String maskCommentsOnly(@NotNull String text) {
        return maskInternal(text, false);
    }

    /**
     * Mask comments, quoted strings, and heredoc/nowdoc bodies with spaces so
     * regex matches keep the same offsets but cannot hit non-code.
     */
    public static @NotNull String maskCommentsAndStrings(@NotNull String text) {
        return maskInternal(text, true);
    }

    private static @NotNull String maskInternal(@NotNull String text, boolean maskStrings) {
        char[] chars = text.toCharArray();
        int i = 0;
        int n = chars.length;
        // Snippets with no open tag (statement prefixes) are already PHP.
        // Full files that contain a tag start outside PHP so HTML quotes cannot leak.
        boolean inPhp = !containsPhpOpenTag(text);
        while (i < n) {
            if (!inPhp) {
                int tagLen = phpOpenTagLength(text, i);
                if (tagLen > 0) {
                    inPhp = true;
                    i += tagLen;
                    continue;
                }
                if (chars[i] != '\n' && chars[i] != '\r') {
                    chars[i] = ' ';
                }
                i++;
                continue;
            }
            if (i + 1 < n && chars[i] == '?' && chars[i + 1] == '>') {
                inPhp = false;
                i += 2;
                continue;
            }
            // Heredoc / nowdoc: <<<IDENT  <<<'IDENT'  <<<"IDENT"
            // Detected in both modes so // # /* inside the body are never comments;
            // characters are wiped only when strings are being masked.
            if (i + 3 < n && chars[i] == '<' && chars[i + 1] == '<' && chars[i + 2] == '<') {
                int start = i;
                i += 3;
                while (i < n && (chars[i] == ' ' || chars[i] == '\t')) {
                    i++;
                }
                boolean quoted = false;
                char quote = 0;
                if (i < n && (chars[i] == '\'' || chars[i] == '"')) {
                    quoted = true;
                    quote = chars[i];
                    i++;
                }
                int idStart = i;
                while (i < n && (Character.isLetterOrDigit(chars[i]) || chars[i] == '_')) {
                    i++;
                }
                if (i == idStart) {
                    // Not a valid heredoc — treat '<<<' as ordinary chars
                    i = start + 1;
                    continue;
                }
                String ident = text.substring(idStart, i);
                if (quoted && i < n && chars[i] == quote) {
                    i++;
                }
                // declaration through end of line
                if (maskStrings) {
                    for (int k = start; k < i; k++) {
                        chars[k] = ' ';
                    }
                }
                while (i < n && chars[i] != '\n') {
                    if (maskStrings) {
                        chars[i] = ' ';
                    }
                    i++;
                }
                if (i < n) {
                    i++; // newline
                }
                // body until a line that is only IDENT or IDENT followed by a non-word char
                while (i < n) {
                    int lineStart = i;
                    while (i < n && chars[i] != '\n') {
                        i++;
                    }
                    int labelStart = lineStart;
                    while (labelStart < i && (text.charAt(labelStart) == ' ' || text.charAt(labelStart) == '\t')) {
                        labelStart++;
                    }
                    int labelEnd = labelStart + ident.length();
                    boolean closer = labelEnd <= i && text.startsWith(ident, labelStart)
                            && (labelEnd == i || !Character.isLetterOrDigit(text.charAt(labelEnd))
                            && text.charAt(labelEnd) != '_');
                    int maskEnd = closer ? labelEnd : i;
                    if (maskStrings) {
                        for (int k = lineStart; k < maskEnd; k++) {
                            if (chars[k] != '\r') {
                                chars[k] = ' ';
                            }
                        }
                    }
                    if (closer) {
                        // Resume PHP at the suffix: punctuation, comments and code can share this line.
                        i = labelEnd;
                        break;
                    }
                    if (i < n) {
                        i++; // newline
                    }
                }
                continue;
            }
            // // line comment
            if (i + 1 < n && chars[i] == '/' && chars[i + 1] == '/') {
                while (i < n && chars[i] != '\n'
                        && !(chars[i] == '?' && i + 1 < n && chars[i + 1] == '>')) {
                    chars[i++] = ' ';
                }
                continue;
            }
            // # line comment
            if (chars[i] == '#') {
                while (i < n && chars[i] != '\n'
                        && !(chars[i] == '?' && i + 1 < n && chars[i + 1] == '>')) {
                    chars[i++] = ' ';
                }
                continue;
            }
            // /* block comment */
            if (i + 1 < n && chars[i] == '/' && chars[i + 1] == '*') {
                chars[i++] = ' ';
                chars[i++] = ' ';
                while (i + 1 < n && !(chars[i] == '*' && chars[i + 1] == '/')) {
                    chars[i++] = ' ';
                }
                if (i + 1 < n) {
                    chars[i++] = ' ';
                    chars[i++] = ' ';
                }
                continue;
            }
            if (!maskStrings) {
                // Keep the string, but step over it so // and # inside it are not comments.
                if (chars[i] == '\'' || chars[i] == '"') {
                    char quote = chars[i++];
                    while (i < n && chars[i] != quote) {
                        i += (chars[i] == '\\' && i + 1 < n) ? 2 : 1;
                    }
                    if (i < n) {
                        i++;
                    }
                    continue;
                }
                i++;
                continue;
            }
            // single-quoted string
            if (chars[i] == '\'') {
                chars[i++] = ' ';
                while (i < n) {
                    if (chars[i] == '\\' && i + 1 < n) {
                        chars[i++] = ' ';
                        chars[i++] = ' ';
                        continue;
                    }
                    if (chars[i] == '\'') {
                        chars[i++] = ' ';
                        break;
                    }
                    chars[i++] = ' ';
                }
                continue;
            }
            // double-quoted string
            if (chars[i] == '"') {
                chars[i++] = ' ';
                while (i < n) {
                    if (chars[i] == '\\' && i + 1 < n) {
                        chars[i++] = ' ';
                        chars[i++] = ' ';
                        continue;
                    }
                    if (chars[i] == '"') {
                        chars[i++] = ' ';
                        break;
                    }
                    chars[i++] = ' ';
                }
                continue;
            }
            i++;
        }
        return new String(chars);
    }

    private static boolean containsPhpOpenTag(@NotNull String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '<' && phpOpenTagLength(text, i) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Length of a PHP open tag at {@code i}, or 0. Recognizes {@code <?php}, {@code <?=},
     * and short {@code <?} (not {@code <?xml}).
     */
    private static int phpOpenTagLength(@NotNull String text, int i) {
        int n = text.length();
        if (i + 1 >= n || text.charAt(i) != '<' || text.charAt(i + 1) != '?') {
            return 0;
        }
        if (i + 2 < n && text.charAt(i + 2) == '=') {
            return 3;
        }
        if (startsIgnoreCase(text, i, "<?php")) {
            int after = i + 5;
            if (after == n || !Character.isLetterOrDigit(text.charAt(after))) {
                return 5;
            }
            return 0;
        }
        if (startsIgnoreCase(text, i, "<?xml")) {
            return 0;
        }
        return 2;
    }

    private static boolean startsIgnoreCase(@NotNull String text, int i, @NotNull String prefix) {
        int n = prefix.length();
        if (i + n > text.length()) {
            return false;
        }
        return text.regionMatches(true, i, prefix, 0, n);
    }

    /**
     * First string-literal argument after {@code openParen} ('...' or "...").
     * Returns content without quotes, or null if the first arg is not a string.
     */
    static @Nullable String firstStringArgContent(@NotNull String text, int openParenIndex) {
        if (openParenIndex < 0 || openParenIndex >= text.length() || text.charAt(openParenIndex) != '(') {
            return null;
        }
        int i = openParenIndex + 1;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        if (i >= text.length()) {
            return null;
        }
        char q = text.charAt(i);
        if (q != '\'' && q != '"') {
            return null;
        }
        i++;
        StringBuilder sb = new StringBuilder();
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                sb.append(text.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == q) {
                return sb.toString();
            }
            sb.append(c);
            i++;
        }
        return null;
    }

    /**
     * Count non-string top-level commas between {@code openParen} (exclusive) and matching close paren.
     * Returns -1 if unbalanced. 0 means single-argument call body (no commas).
     */
    static int countTopLevelCommasInCall(@NotNull String text, int openParenIndex) {
        if (openParenIndex < 0 || openParenIndex >= text.length() || text.charAt(openParenIndex) != '(') {
            return -1;
        }
        int depth = 1;
        int commas = 0;
        boolean inSq = false;
        boolean inDq = false;
        for (int i = openParenIndex + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inSq) {
                if (c == '\\' && i + 1 < text.length()) {
                    i++;
                    continue;
                }
                if (c == '\'') {
                    inSq = false;
                }
                continue;
            }
            if (inDq) {
                if (c == '\\' && i + 1 < text.length()) {
                    i++;
                    continue;
                }
                if (c == '"') {
                    inDq = false;
                }
                continue;
            }
            if (c == '\'') {
                inSq = true;
                continue;
            }
            if (c == '"') {
                inDq = true;
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return commas;
                }
            } else if (c == ',' && depth == 1) {
                commas++;
            }
        }
        return -1;
    }

    static @NotNull List<Match> findAll(@NotNull String text, @NotNull Pattern pattern) {
        List<Match> matches = new ArrayList<>();
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            matches.add(new Match(m.start(), m.end(), m.group()));
        }
        return matches;
    }

    static @Nullable PsiElement leafAt(@NotNull PsiFile file, int offset) {
        return file.findElementAt(offset);
    }

    static @NotNull TextRange range(int start, int end) {
        return TextRange.create(start, end);
    }

    record Match(int start, int end, String text) {
    }
}
