package org.xoops.support.completion;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Project-level cache of XOOPS language constants from language/*.php define() lines.
 * Invalidated when language files change under VFS.
 */
@Service(Service.Level.PROJECT)
public final class XoopsLanguageConstantsCache {

    private static final Pattern DEFINE = Pattern.compile(
            "define\\s*\\(\\s*['\"](_(?:MI|AM|MD|CO|MB)_[A-Z0-9_]+)['\"]",
            Pattern.CASE_INSENSITIVE
    );

    private static final String[] LANGUAGE_FILES = {
            "modinfo.php", "main.php", "admin.php", "blocks.php", "common.php"
    };

    private final Project project;
    private final AtomicReference<Set<String>> cached = new AtomicReference<>(null);

    public XoopsLanguageConstantsCache(@NotNull Project project) {
        this.project = project;
        VirtualFileManager.getInstance().addAsyncFileListener(
                events -> {
                    if (project.isDisposed()) {
                        return null;
                    }
                    boolean hit = false;
                    for (VFileEvent event : events) {
                        VirtualFile file = event.getFile();
                        if (file != null && isLanguagePath(file.getPath())) {
                            hit = true;
                            break;
                        }
                        String path = event.getPath();
                        if (path != null && isLanguagePath(path)) {
                            hit = true;
                            break;
                        }
                    }
                    if (!hit) {
                        return null;
                    }
                    return new AsyncFileListener.ChangeApplier() {
                        @Override
                        public void afterVfsChange() {
                            invalidate();
                        }
                    };
                },
                project
        );
    }

    public static @NotNull XoopsLanguageConstantsCache getInstance(@NotNull Project project) {
        return project.getService(XoopsLanguageConstantsCache.class);
    }

    public void invalidate() {
        cached.set(null);
    }

    public @NotNull Set<String> getConstants() {
        Set<String> hit = cached.get();
        if (hit != null) {
            return hit;
        }
        try {
            Set<String> rebuilt = ReadAction.compute(this::collectUnderReadLock);
            cached.compareAndSet(null, rebuilt);
            Set<String> after = cached.get();
            return after != null ? after : rebuilt;
        } catch (IndexNotReadyException e) {
            return Collections.emptySet();
        }
    }

    @RequiresReadLock
    private @NotNull Set<String> collectUnderReadLock() {
        Set<String> names = new LinkedHashSet<>();
        PsiManager psiManager = PsiManager.getInstance(project);
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        for (String fileName : LANGUAGE_FILES) {
            Collection<VirtualFile> files = FilenameIndex.getVirtualFilesByName(fileName, scope);
            for (VirtualFile vf : files) {
                String path = vf.getPath().replace('\\', '/').toLowerCase(Locale.ROOT);
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
                        return Collections.unmodifiableSet(names);
                    }
                }
            }
        }
        return Collections.unmodifiableSet(names);
    }

    private static boolean isLanguagePath(@Nullable String path) {
        if (path == null) {
            return false;
        }
        String p = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        return p.contains("/language/")
                && (p.endsWith(".php") || p.endsWith("/language") || !p.contains("."));
    }
}
