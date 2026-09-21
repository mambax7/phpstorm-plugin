package org.xoops.support.completion;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves a {@code _MI_}/{@code _AM_}/{@code _MD_}/{@code _CO_}/{@code _MB_} name
 * to its {@code define()} in a language PHP file.
 */
public final class XoopsLanguageConstantReference extends PsiReferenceBase<PsiElement> {

    private final String name;

    public XoopsLanguageConstantReference(@NotNull PsiElement element, @NotNull String name) {
        super(element, TextRange.from(0, element.getTextLength()), true);
        this.name = name;
    }

    public XoopsLanguageConstantReference(
            @NotNull PsiElement element,
            @NotNull TextRange rangeInElement,
            @NotNull String name
    ) {
        super(element, rangeInElement, true);
        this.name = name;
    }

    @Override
    public @Nullable PsiElement resolve() {
        PsiFile file = getElement().getContainingFile();
        return XoopsLanguageConstantsCache.getInstance(getElement().getProject())
                .resolve(name, file == null ? null : file.getVirtualFile());
    }

    @Override
    public @NotNull String getCanonicalText() {
        return name;
    }
}
