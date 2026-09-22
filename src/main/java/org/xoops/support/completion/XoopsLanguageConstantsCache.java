package org.xoops.support.completion;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Project-level cache of XOOPS language constants from every PHP file under
 * {@code language/}. Depends on language PSI files and relevant VFS changes;
 * VFS listener parent is this service (not the project) so plugin unload disposes it.
 */
@Service(Service.Level.PROJECT)
public final class XoopsLanguageConstantsCache implements Disposable {

    private static final Key<CachedValue<Index>> CACHE_KEY =
            Key.create("xoops.support.languageConstants");

    private static final int MAX_CONSTANTS = 5000;

    private final Project project;
    private final SimpleModificationTracker languageChanges = new SimpleModificationTracker();
    private volatile boolean disposed;

    public XoopsLanguageConstantsCache(@NotNull Project project) {
        this.project = project;
        VirtualFileManager.getInstance().addAsyncFileListener(
                events -> {
                    if (disposed || project.isDisposed()) {
                        return null;
                    }
                    boolean hit = false;
                    for (VFileEvent event : events) {
                        VirtualFile file = event.getFile();
                        // ponytail: directory events conservatively invalidate; subtree tracking only if needed.
                        if (file != null && (file.isDirectory()
                                || XoopsLanguageConstantParser.isLanguagePath(file.getPath()))) {
                            hit = true;
                            break;
                        }
                        if (event instanceof VFileMoveEvent move
                                && XoopsLanguageConstantParser.isLanguagePath(
                                        move.getNewParent().getPath() + "/" + move.getFile().getName())) {
                            hit = true;
                            break;
                        }
                        if (event instanceof VFilePropertyChangeEvent rename
                                && XoopsLanguageConstantParser.isLanguagePath(rename.getNewPath())) {
                            hit = true;
                            break;
                        }
                        String path = event.getPath();
                        if (path != null && XoopsLanguageConstantParser.isLanguagePath(path)) {
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
                            if (!disposed) {
                                invalidate();
                            }
                        }
                    };
                },
                this
        );
    }

    public static @NotNull XoopsLanguageConstantsCache getInstance(@NotNull Project project) {
        return project.getService(XoopsLanguageConstantsCache.class);
    }

    public void invalidate() {
        if (!project.isDisposed()) {
            languageChanges.incModificationCount();
        }
    }

    public @NotNull Set<String> getConstants() {
        Index index = getIndex();
        return index == null ? Collections.emptySet() : index.names;
    }

    /**
     * PSI element of the {@code define('_FOO_'} name in a language file (prefers {@code /english/}).
     * Exact spelling only: {@code _MI_FOO} does not resolve to {@code define('_mi_foo')}.
     */
    public @Nullable PsiElement resolve(@NotNull String name) {
        return resolve(name, null);
    }

    /**
     * Same as {@link #resolve(String)}, preferring a definition in the same module as
     * {@code from} (the {@code /modules/<dirname>/} segment) when several modules define the name.
     */
    public @Nullable PsiElement resolve(@NotNull String name, @Nullable VirtualFile from) {
        Index index = getIndex();
        if (index == null) {
            return null;
        }
        List<Def> defs = index.defs.get(name);
        if (defs == null || defs.isEmpty()) {
            return null;
        }
        Def chosen = pickDefinition(defs, from == null ? null : moduleSegment(from.getPath()));
        if (project.isDisposed()) {
            return null;
        }
        PsiFile psi = PsiManager.getInstance(project).findFile(chosen.file);
        if (psi == null) {
            return null;
        }
        return psi.findElementAt(chosen.offset);
    }

    @Override
    public void dispose() {
        disposed = true;
        project.putUserData(CACHE_KEY, null);
    }

    private @Nullable Index getIndex() {
        if (disposed || project.isDisposed()) {
            return null;
        }
        try {
            return ReadAction.compute(() -> {
                if (disposed || project.isDisposed()) {
                    return null;
                }
                CachedValue<Index> cached = project.getUserData(CACHE_KEY);
                if (cached == null) {
                    cached = CachedValuesManager.getManager(project).createCachedValue(
                            this::collectUnderReadLock,
                            false
                    );
                    project.putUserData(CACHE_KEY, cached);
                }
                return cached.getValue();
            });
        } catch (IndexNotReadyException e) {
            return null;
        }
    }

    @RequiresReadLock
    private @NotNull CachedValueProvider.Result<Index> collectUnderReadLock() {
        List<Object> dependencies = new ArrayList<>();
        dependencies.add(languageChanges);
        Set<String> names = new LinkedHashSet<>();
        Map<String, List<Def>> defs = new LinkedHashMap<>();
        PsiManager psiManager = PsiManager.getInstance(project);
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        Collection<VirtualFile> files = FilenameIndex.getAllFilesByExt(project, "php", scope);
        for (VirtualFile vf : files) {
            if (!XoopsLanguageConstantParser.isLanguagePath(vf.getPath())
                    || vf.getPath().replace('\\', '/').toLowerCase(Locale.ROOT).contains("/vendor/")) {
                continue;
            }
            PsiFile psi = psiManager.findFile(vf);
            if (psi == null) {
                continue;
            }
            // PSI dependencies include committed editor changes, even before saving to disk.
            dependencies.add(psi);
            for (XoopsLanguageConstantParser.Occurrence occ : XoopsLanguageConstantParser.parse(psi.getText())) {
                if (!names.contains(occ.name()) && names.size() >= MAX_CONSTANTS) {
                    continue; // cap new names; definitions of known names are still recorded
                }
                names.add(occ.name());
                defs.computeIfAbsent(occ.name(), k -> new ArrayList<>()).add(new Def(vf, occ.offset()));
            }
        }
        return CachedValueProvider.Result.create(
                new Index(Collections.unmodifiableSet(names), Map.copyOf(defs)), dependencies.toArray());
    }

    /** Same module and /english/ first, then same module, then any /english/, then the first. */
    static @NotNull Def pickDefinition(@NotNull List<Def> defs, @Nullable String moduleSegment) {
        Def sameModule = null;
        Def english = null;
        for (Def d : defs) {
            String path = d.file.getPath().replace('\\', '/').toLowerCase(Locale.ROOT);
            boolean inModule = moduleSegment != null && moduleSegment.equals(moduleSegment(path));
            boolean isEnglish = path.contains("/english/");
            if (inModule && isEnglish) {
                return d;
            }
            if (inModule && sameModule == null) {
                sameModule = d;
            }
            if (isEnglish && english == null) {
                english = d;
            }
        }
        return sameModule != null ? sameModule : (english != null ? english : defs.get(0));
    }

    /** {@code /modules/<dirname>/} of a path, lower-case, or null when not under modules/. */
    static @Nullable String moduleSegment(@NotNull String path) {
        String p = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        int i = p.lastIndexOf("/modules/");
        if (i < 0) {
            return null;
        }
        int end = p.indexOf('/', i + "/modules/".length());
        return end < 0 ? null : p.substring(i, end + 1);
    }

    private record Def(@NotNull VirtualFile file, int offset) {
    }

    private record Index(@NotNull Set<String> names, @NotNull Map<String, List<Def>> defs) {
    }
}
