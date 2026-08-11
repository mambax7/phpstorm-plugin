package org.xoops.support.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flags raw $_GET/$_POST/$_REQUEST/$_COOKIE in module code.
 */
public final class XoopsSuperglobalInspection extends LocalInspectionTool {

    private static final Pattern SUPER = Pattern.compile("\\$_(GET|POST|REQUEST|COOKIE)\\b");
    // $_GET['key'] or $_POST["key"]
    private static final Pattern SUPER_KEY = Pattern.compile(
            "\\$_(GET|POST|REQUEST|COOKIE)\\s*\\[\\s*['\"]([^'\"]+)['\"]\\s*\\]"
    );

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitFile(@NotNull PsiFile file) {
                if (!PhpTextUtil.isPhpFile(file) || PhpTextUtil.looksLikeVendorOrCache(file)) {
                    return;
                }
                String path = file.getVirtualFile() != null
                        ? file.getVirtualFile().getPath().replace('\\', '/').toLowerCase()
                        : "";
                if (!path.contains("/modules/") && !path.contains("/xoops_lib/vendor/xoops/")) {
                    return;
                }
                String text = file.getText();

                // Prefer key-aware replacements first (longer match).
                Matcher keyed = SUPER_KEY.matcher(text);
                boolean[] covered = new boolean[text.length() + 1];
                while (keyed.find()) {
                    String source = keyed.group(1).toUpperCase();
                    String key = keyed.group(2);
                    String methodSource = switch (source) {
                        case "POST" -> "POST";
                        case "COOKIE" -> "COOKIE";
                        case "REQUEST" -> "GET"; // Request::getString still needs a source; GET is safest default
                        default -> "GET";
                    };
                    String replacement = "\\Xmf\\Request::getString('" + key + "', '', '" + methodSource + "')";
                    PsiElement leaf = PhpTextUtil.leafAt(file, keyed.start());
                    if (leaf == null) {
                        continue;
                    }
                    for (int i = keyed.start(); i < keyed.end(); i++) {
                        covered[i] = true;
                    }
                    String msg = "XOOPS: prefer \\Xmf\\Request over $_" + source;
                    holder.registerProblem(
                            leaf,
                            msg,
                            new ReplaceRangeQuickFix(
                                    "Replace with Xmf\\Request::getString()",
                                    keyed.start(),
                                    keyed.end(),
                                    replacement
                            )
                    );
                }

                for (PhpTextUtil.Match match : PhpTextUtil.findAll(text, SUPER)) {
                    if (match.start() < covered.length && covered[match.start()]) {
                        continue;
                    }
                    PsiElement leaf = PhpTextUtil.leafAt(file, match.start());
                    if (leaf == null) {
                        continue;
                    }
                    String which = match.text();
                    String hint = switch (which) {
                        case "$_GET" -> "\\Xmf\\Request::getString('…', '', 'GET')";
                        case "$_POST" -> "\\Xmf\\Request::getString('…', '', 'POST')";
                        case "$_COOKIE" -> "\\Xmf\\Request::getString('…', '', 'COOKIE')";
                        default -> "\\Xmf\\Request (avoid $_REQUEST)";
                    };
                    // No safe mechanical rewrite without a key — informational only.
                    holder.registerProblem(
                            leaf,
                            "XOOPS: prefer " + hint + " over " + which
                                    + " (Alt+Enter when written as " + which + "['key'])"
                    );
                }
            }
        };
    }
}
