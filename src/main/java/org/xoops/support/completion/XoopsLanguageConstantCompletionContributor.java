package org.xoops.support.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Completes _MI_ / _AM_ / _MD_ / _CO_ constants discovered from language/*.php define() lines.
 */
public final class XoopsLanguageConstantCompletionContributor extends CompletionContributor {

    private static final Pattern DEFINE = Pattern.compile(
            "define\\s*\\(\\s*['\"](_(?:MI|AM|MD|CO|MB)_[A-Z0-9_]+)['\"]",
            Pattern.CASE_INSENSITIVE
    );

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
                        String upper = prefix.toUpperCase();
                        if (!(upper.startsWith("_M") || upper.startsWith("_A") || upper.startsWith("_C"))) {
                            // Still allow mid-typing _MI etc.
                            if (!prefix.startsWith("_")) {
                                return;
                            }
                        }
                        for (String name : collectConstants(project)) {
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

    private static @NotNull Set<String> collectConstants(@NotNull Project project) {
        try {
            return ReadAction.compute(() -> collectConstantsUnderReadLock(project));
        } catch (IndexNotReadyException e) {
            return Collections.emptySet();
        }
    }

    private static @NotNull Set<String> collectConstantsUnderReadLock(@NotNull Project project) {
        Set<String> names = new LinkedHashSet<>();
        // Typical language file names
        for (String fileName : new String[]{"modinfo.php", "main.php", "admin.php", "blocks.php", "common.php"}) {
            Collection<VirtualFile> files = FilenameIndex.getVirtualFilesByName(
                    fileName,
                    GlobalSearchScope.projectScope(project)
            );
            PsiManager psiManager = PsiManager.getInstance(project);
            for (VirtualFile vf : files) {
                String path = vf.getPath().replace('\\', '/').toLowerCase();
                if (!path.contains("/language/") || path.contains("/vendor/")) {
                    continue;
                }
                PsiFile psi = psiManager.findFile(vf);
                if (psi == null) {
                    continue;
                }
                Matcher m = DEFINE.matcher(psi.getText());
                while (m.find()) {
                    names.add(m.group(1));
                    if (names.size() > 5000) {
                        return names;
                    }
                }
            }
        }
        return names;
    }
}
