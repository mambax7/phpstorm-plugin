package org.xoops.support.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.xoops.support.XoopsSupportPlugin;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flags raw $_GET/$_POST/$_REQUEST/$_COOKIE in module code.
 * Quick fixes only for keyed GET/POST/COOKIE (known source). Bare and $_REQUEST are warn-only.
 */
public final class XoopsSuperglobalInspection extends LocalInspectionTool {

    private static final Pattern SUPER = Pattern.compile("\\$_(GET|POST|REQUEST|COOKIE)\\b");
    private static final Pattern SUPER_KEY = Pattern.compile(
            "\\$_(GET|POST|REQUEST|COOKIE)\\s*\\[\\s*['\"]([^'\"]+)['\"]\\s*\\]"
    );

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitFile(@NotNull PsiFile file) {
                if (!XoopsSupportPlugin.isEnabled(file)) {
                    return;
                }
                if (!PhpTextUtil.isPhpFile(file) || PhpTextUtil.looksLikeVendorOrCache(file)) {
                    return;
                }
                String path = file.getVirtualFile() != null
                        ? file.getVirtualFile().getPath().replace('\\', '/').toLowerCase(Locale.ROOT)
                        : "";
                if (!path.contains("/modules/") && !path.contains("/xoops_lib/vendor/xoops/")) {
                    return;
                }
                String text = file.getText();
                String code = PhpTextUtil.maskCommentsAndStrings(text);

                Matcher keyed = SUPER_KEY.matcher(code);
                boolean[] covered = new boolean[text.length() + 1];
                while (keyed.find()) {
                    String source = keyed.group(1).toUpperCase(Locale.ROOT);
                    String key = keyed.group(2);
                    String matched = text.substring(keyed.start(), keyed.end());
                    PsiElement leaf = PhpTextUtil.leafAt(file, keyed.start());
                    if (leaf == null) {
                        continue;
                    }
                    for (int i = keyed.start(); i < keyed.end(); i++) {
                        covered[i] = true;
                    }
                    // $_REQUEST merges sources — warn only, no forced-source rewrite.
                    if ("REQUEST".equals(source)) {
                        holder.registerProblem(
                                leaf,
                                "XOOPS: prefer \\Xmf\\Request with an explicit source over $_REQUEST['"
                                        + key + "'] (no auto-fix: GET/POST/COOKIE is ambiguous)"
                        );
                        continue;
                    }
                    String methodSource = switch (source) {
                        case "POST" -> "POST";
                        case "COOKIE" -> "COOKIE";
                        default -> "GET";
                    };
                    // Keyed GET/POST/COOKIE string access → getString (key is a string literal).
                    String replacement = "\\Xmf\\Request::getString('" + key + "', '', '" + methodSource + "')";
                    holder.registerProblem(
                            leaf,
                            "XOOPS: prefer \\Xmf\\Request::getString over $_" + source + "['" + key + "']",
                            new ReplaceRangeQuickFix(
                                    "Replace with Xmf\\Request::getString()",
                                    keyed.start(),
                                    keyed.end(),
                                    replacement,
                                    matched
                            )
                    );
                }

                for (PhpTextUtil.Match match : PhpTextUtil.findAll(code, SUPER)) {
                    if (match.start() < covered.length && covered[match.start()]) {
                        continue;
                    }
                    PsiElement leaf = PhpTextUtil.leafAt(file, match.start());
                    if (leaf == null) {
                        continue;
                    }
                    // Recover real text from original (code mask blanks strings/comments).
                    String which = text.substring(match.start(), match.end());
                    String hint = switch (which) {
                        case "$_GET" -> "\\Xmf\\Request::getString('…', '', 'GET') when key is known";
                        case "$_POST" -> "\\Xmf\\Request::getString('…', '', 'POST') when key is known";
                        case "$_COOKIE" -> "\\Xmf\\Request::getString('…', '', 'COOKIE') when key is known";
                        default -> "\\Xmf\\Request with an explicit source (avoid $_REQUEST)";
                    };
                    // Bare superglobal — warning only, no quick fix.
                    holder.registerProblem(
                            leaf,
                            "XOOPS: prefer " + hint + " over bare " + which
                                    + " (Alt+Enter only for keyed $_GET/$_POST/$_COOKIE['key'])"
                    );
                }
            }
        };
    }
}
