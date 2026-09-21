package org.xoops.support.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.xoops.support.XoopsSupportPlugin;
import org.xoops.support.settings.XoopsSettingsState;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code XOBJ_DTYPE_UNICODE_*} was deprecated in XOOPS 2.7.3 (#164) with removal
 * scheduled for 4.0. Rename-only quick-fix; value migration stays a core concern.
 * Silent when the project Core Version is explicitly 2.5.
 */
public final class XoopsDeprecatedUnicodeDtypeInspection extends LocalInspectionTool {

    static final Map<String, String> SUCCESSOR = Map.of(
            "XOBJ_DTYPE_UNICODE_TXTBOX", "XOBJ_DTYPE_TXTBOX",
            "XOBJ_DTYPE_UNICODE_TXTAREA", "XOBJ_DTYPE_TXTAREA",
            "XOBJ_DTYPE_UNICODE_URL", "XOBJ_DTYPE_URL",
            "XOBJ_DTYPE_UNICODE_EMAIL", "XOBJ_DTYPE_EMAIL",
            "XOBJ_DTYPE_UNICODE_ARRAY", "XOBJ_DTYPE_ARRAY",
            "XOBJ_DTYPE_UNICODE_OTHER", "XOBJ_DTYPE_OTHER"
    );

    private static final Pattern UNICODE_DTYPE = Pattern.compile(
            "\\b(XOBJ_DTYPE_UNICODE_(?:TXTBOX|TXTAREA|URL|EMAIL|ARRAY|OTHER))\\b"
    );

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitFile(@NotNull PsiFile file) {
                if (!XoopsSupportPlugin.isEnabled(file) || !PhpTextUtil.isPrimaryPsiFile(file)) {
                    return;
                }
                if (!PhpTextUtil.isPhpFile(file) || PhpTextUtil.looksLikeVendorOrCache(file)) {
                    return;
                }
                String coreVersion = XoopsSettingsState.getInstance(file.getProject()).coreVersion;
                if ("2.5".equals(coreVersion)) {
                    return;
                }
                String text = file.getText();
                String code = PhpTextUtil.maskCommentsAndStrings(text);
                Matcher m = UNICODE_DTYPE.matcher(code);
                while (m.find()) {
                    String old = m.group(1);
                    String next = SUCCESSOR.get(old);
                    if (next == null) {
                        continue;
                    }
                    PsiElement leaf = PhpTextUtil.leafAt(file, m.start(1));
                    if (leaf == null) {
                        continue;
                    }
                    holder.registerProblem(
                            leaf,
                            "XOOPS: " + old + " is deprecated since 2.7.3; use " + next,
                            new ReplacePsiTextQuickFix("Replace with " + next, next)
                    );
                }
            }
        };
    }
}
