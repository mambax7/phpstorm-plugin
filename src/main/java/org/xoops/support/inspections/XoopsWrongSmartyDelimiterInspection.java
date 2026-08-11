package org.xoops.support.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.xoops.support.XoopsSupportPlugin;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * XOOPS templates use &lt;{ … }&gt;, not stock Smarty { … }.
 */
public final class XoopsWrongSmartyDelimiterInspection extends LocalInspectionTool {

    private static final Pattern WRONG_SMARTY = Pattern.compile(
            "(?i)(?<!<)\\{(?:\\$|/?(?:if|foreach|include|assign|block|literal|/if|/foreach)\\b)[^\\n}]*\\}"
    );

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitFile(@NotNull PsiFile file) {
                if (!XoopsSupportPlugin.isEnabled(file)) {
                    return;
                }
                String name = file.getName().toLowerCase(java.util.Locale.ROOT);
                if (!name.endsWith(".tpl")) {
                    return;
                }
                if (PhpTextUtil.looksLikeVendorOrCache(file)) {
                    return;
                }
                String text = file.getText();
                // Mask {* Smarty comments *} and <{literal}>...</{literal}> regions.
                String code = maskSmartyNoise(text);
                Matcher m = WRONG_SMARTY.matcher(code);
                int count = 0;
                while (m.find() && count < 20) {
                    PsiElement leaf = PhpTextUtil.leafAt(file, m.start());
                    if (leaf == null) {
                        continue;
                    }
                    String original = text.substring(m.start(), m.end());
                    // {if $x} -> <{if $x}>  |  {$foo} -> <{$foo}>
                    String fixed = "<" + original.substring(0, original.length() - 1) + "}>";
                    if (original.startsWith("{") && original.endsWith("}") && !original.startsWith("<{")) {
                        holder.registerProblem(
                                leaf,
                                "XOOPS: Smarty tags should use <{ ... }> delimiters, not bare { ... }",
                                new ReplaceRangeQuickFix(
                                        "Convert to XOOPS <{ }> delimiters",
                                        m.start(),
                                        m.end(),
                                        fixed,
                                        original
                                )
                        );
                        count++;
                    }
                }
            }
        };
    }

    /** Blank out {* ... *} comments and &lt;{literal}&gt; blocks so they cannot trigger fixes. */
    private static @NotNull String maskSmartyNoise(@NotNull String text) {
        char[] chars = text.toCharArray();
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        int i = 0;
        while (i < chars.length) {
            if (i + 1 < chars.length && chars[i] == '{' && chars[i + 1] == '*') {
                chars[i++] = ' ';
                chars[i++] = ' ';
                while (i + 1 < chars.length && !(chars[i] == '*' && chars[i + 1] == '}')) {
                    chars[i++] = ' ';
                }
                if (i + 1 < chars.length) {
                    chars[i++] = ' ';
                    chars[i++] = ' ';
                }
                continue;
            }
            int lit = lower.indexOf("<{literal}>", i);
            int close = lower.indexOf("</{literal}>", i);
            if (lit == i) {
                int end = close >= 0 ? close + "</{literal}>".length() : chars.length;
                while (i < end) {
                    chars[i++] = ' ';
                }
                continue;
            }
            i++;
        }
        return new String(chars);
    }
}
