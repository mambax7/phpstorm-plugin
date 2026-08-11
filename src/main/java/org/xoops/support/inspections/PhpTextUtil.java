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
 * Lightweight helpers — enough for MVP text inspections without deep PHP PSI.
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
