package org.xoops.support.completion;

import com.intellij.lang.Language;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.util.ProcessingContext;
import com.jetbrains.php.lang.psi.elements.ConstantReference;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.xoops.support.XoopsSupportPlugin;

/**
 * Ctrl+B / Find Usages for XOOPS language constants ({@code _MD_FOO}, {@code '_MI_BAR'}).
 */
public final class XoopsLanguageConstantReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        PsiReferenceProvider provider = new PsiReferenceProvider() {
            @Override
            public PsiReference @NotNull [] getReferencesByElement(
                    @NotNull PsiElement element,
                    @NotNull ProcessingContext context
            ) {
                if (!XoopsSupportPlugin.isEnabled(element.getProject())) {
                    return PsiReference.EMPTY_ARRAY;
                }
                String name = nameOf(element);
                if (name == null) {
                    return PsiReference.EMPTY_ARRAY;
                }
                return new PsiReference[]{new XoopsLanguageConstantReference(element, name)};
            }
        };
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(StringLiteralExpression.class), provider);
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(ConstantReference.class), provider);
        Language smarty = Language.findLanguageByID("Smarty");
        if (smarty != null) {
            registrar.registerReferenceProvider(
                    PlatformPatterns.psiElement().withLanguage(smarty),
                    provider
            );
        }
    }

    private static String nameOf(@NotNull PsiElement element) {
        if (element instanceof StringLiteralExpression stringLiteral) {
            return XoopsLanguageConstantParser.extractName(stringLiteral.getContents());
        }
        if (element instanceof ConstantReference constantReference) {
            String n = constantReference.getName();
            return n == null ? null : XoopsLanguageConstantParser.extractName(n);
        }
        // Smarty (and any other host): only leaves. A parent whose text is
        // <{$smarty.const._MI_FOO}> would otherwise double-count with the leaf.
        if (element.getChildren().length == 0) {
            return XoopsLanguageConstantParser.extractName(element.getText());
        }
        return null;
    }
}
