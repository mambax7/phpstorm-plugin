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
     * Mask comments and string/heredoc-like quoted regions with spaces so regex
     * matches keep the same offsets but cannot hit non-code.
     */
    static @NotNull String maskCommentsAndStrings(@NotNull String text) {
        char[] chars = text.toCharArray();
        int i = 0;
        int n = chars.length;
        while (i < n) {
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
