package org.xoops.support.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flags raw $_GET/$_POST/$_REQUEST/$_COOKIE in module code.
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

                Matcher keyed = SUPER_KEY.matcher(text);
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
                    String msg = "XOOPS: prefer \\Xmf\\Request over $_" + source;
                    LocalQuickFix[] fixes;
                    if ("REQUEST".equals(source)) {
                        // Do not invent a single source — $_REQUEST merges GET/POST/COOKIE.
                        fixes = new LocalQuickFix[]{
                                replaceFix("Use Request GET", keyed.start(), keyed.end(), matched,
                                        "\\Xmf\\Request::getString('" + key + "', '', 'GET')"),
                                replaceFix("Use Request POST", keyed.start(), keyed.end(), matched,
                                        "\\Xmf\\Request::getString('" + key + "', '', 'POST')"),
                                replaceFix("Use Request COOKIE", keyed.start(), keyed.end(), matched,
                                        "\\Xmf\\Request::getString('" + key + "', '', 'COOKIE')"),
                        };
                        msg = "XOOPS: prefer \\Xmf\\Request over $_REQUEST "
                                + "(choose GET, POST, or COOKIE — $_REQUEST merges sources)";
                    } else {
                        String methodSource = switch (source) {
                            case "POST" -> "POST";
                            case "COOKIE" -> "COOKIE";
                            default -> "GET";
                        };
                        String replacement = "\\Xmf\\Request::getString('" + key + "', '', '" + methodSource + "')";
                        fixes = new LocalQuickFix[]{
                                replaceFix("Replace with Xmf\\Request::getString()",
                                        keyed.start(), keyed.end(), matched, replacement)
                        };
                    }
                    holder.registerProblem(leaf, msg, fixes);
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
                        default -> "\\Xmf\\Request with an explicit source (avoid $_REQUEST)";
                    };
                    holder.registerProblem(
                            leaf,
                            "XOOPS: prefer " + hint + " over " + which
                                    + " (Alt+Enter when written as " + which + "['key'])"
                    );
                }
            }
        };
    }

    private static ReplaceRangeQuickFix replaceFix(
            String family,
            int start,
            int end,
            String expected,
            String replacement
    ) {
        return new ReplaceRangeQuickFix(family, start, end, replacement, expected);
    }
}
