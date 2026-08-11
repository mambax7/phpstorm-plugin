package org.xoops.support.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Completes _MI_ / _AM_ / _MD_ / _CO_ constants discovered from language/*.php define() lines.
 */
public final class XoopsLanguageConstantCompletionContributor extends CompletionContributor {

    public XoopsLanguageConstantCompletionContributor() {
        extend(
                CompletionType.BASIC,
                PlatformPatterns.psiElement(),
                new CompletionProvider<>() {
                    @Override
                    protected void addCompletions(
                            @NotNull CompletionParameters parameters,
                            @NotNull ProcessingContext context,
                            @NotNull CompletionResultSet result
                    ) {
                        Project project = parameters.getPosition().getProject();
                        String prefix = result.getPrefixMatcher().getPrefix();
                        if (prefix.length() < 2) {
                            return;
                        }
                        String upper = prefix.toUpperCase(Locale.ROOT);
                        if (!(upper.startsWith("_M") || upper.startsWith("_A") || upper.startsWith("_C"))) {
                            if (!prefix.startsWith("_")) {
                                return;
                            }
                        }
                        for (String name : XoopsLanguageConstantsCache.getInstance(project).getConstants()) {
                            if (result.getPrefixMatcher().prefixMatches(name)) {
                                result.addElement(
                                        LookupElementBuilder.create(name)
                                                .withTypeText("XOOPS lang", true)
                                                .withPresentableText(name)
                                );
                            }
                        }
                    }
                }
        );
    }
}
