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
 * Prefer include_once for header.php / footer.php.
 */
public final class XoopsIncludeOnceHeaderInspection extends LocalInspectionTool {

    // Capture bare "include" keyword (not include_once).
    private static final Pattern INCLUDE_HEADER = Pattern.compile(
            "\\b(include)(?!_once)\\s*(?:\\(?\\s*)?(?:XOOPS_ROOT_PATH\\s*\\.\\s*)?['\"][^'\"]*(?:header|footer)\\.php['\"]",
            Pattern.CASE_INSENSITIVE
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
                String text = file.getText();
                String code = PhpTextUtil.maskCommentsAndStrings(text);
                Matcher m = INCLUDE_HEADER.matcher(code);
                while (m.find()) {
                    int kwStart = m.start(1);
                    int kwEnd = m.end(1);
                    PsiElement leaf = PhpTextUtil.leafAt(file, kwStart);
                    if (leaf == null) {
                        continue;
                    }
                    String expected = text.substring(kwStart, kwEnd);
                    holder.registerProblem(
                            leaf,
                            "XOOPS: use include_once for header.php / footer.php",
                            new ReplaceRangeQuickFix(
                                    "Replace include with include_once",
                                    kwStart,
                                    kwEnd,
                                    "include_once",
                                    expected
                            )
                    );
                }
            }
        };
    }
}
