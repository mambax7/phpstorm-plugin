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
final class PhpTextUtil {

    private PhpTextUtil() {
    }

    static boolean isPhpFile(@Nullable PsiFile file) {
        if (file == null) {
            return false;
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".php") || name.endsWith(".inc");
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
    static @NotNull String maskCommentsOnly(@NotNull String text) {
        return maskInternal(text, false);
    }

    /**
     * Mask comments, quoted strings, and heredoc/nowdoc bodies with spaces so
     * regex matches keep the same offsets but cannot hit non-code.
     */
    static @NotNull String maskCommentsAndStrings(@NotNull String text) {
        return maskInternal(text, true);
    }

    private static @NotNull String maskInternal(@NotNull String text, boolean maskStrings) {
        char[] chars = text.toCharArray();
        int i = 0;
        int n = chars.length;
        while (i < n) {
            // Heredoc / nowdoc: <<<IDENT  <<<'IDENT'  <<<"IDENT"
            if (maskStrings && i + 3 < n && chars[i] == '<' && chars[i + 1] == '<' && chars[i + 2] == '<') {
                int start = i;
                i += 3;
                while (i < n && (chars[i] == ' ' || chars[i] == '\t')) {
                    i++;
                }
                boolean nowdoc = false;
                boolean quoted = false;
                char quote = 0;
                if (i < n && (chars[i] == '\'' || chars[i] == '"')) {
                    nowdoc = chars[i] == '\'';
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
                // mask declaration through end of line
                while (start < i) {
                    chars[start++] = ' ';
                }
                while (i < n && chars[i] != '\n') {
                    chars[i++] = ' ';
                }
                if (i < n && chars[i] == '\n') {
                    chars[i++] = ' ';
                }
                // body until a line that is only IDENT or IDENT;
                while (i < n) {
                    int lineStart = i;
                    while (i < n && chars[i] != '\n') {
                        i++;
                    }
                    String line = text.substring(lineStart, i);
                    String body = line.stripTrailing().stripLeading();
                    // Closer: IDENT at start of line; following token may be ; ) , etc., but not more word chars.
                    boolean closer = false;
                    if (body.startsWith(ident)) {
                        if (body.length() == ident.length()) {
                            closer = true;
                        } else {
                            char next = body.charAt(ident.length());
                            closer = !Character.isLetterOrDigit(next) && next != '_';
                        }
                    }
                    for (int k = lineStart; k < i; k++) {
                        chars[k] = ' ';
                    }
                    if (i < n && chars[i] == '\n') {
                        chars[i++] = ' ';
                    }
                    if (closer) {
                        break;
                    }
                }
                continue;
            }
            // // line comment
            if (i + 1 < n && chars[i] == '/' && chars[i + 1] == '/') {
                while (i < n && chars[i] != '\n') {
                    chars[i++] = ' ';
                }
                continue;
            }
            // # line comment
            if (chars[i] == '#') {
                while (i < n && chars[i] != '\n') {
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
