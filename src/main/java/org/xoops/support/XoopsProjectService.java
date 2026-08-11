package org.xoops.support;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Detects XOOPS-shaped trees in the open project (mainfile.php, xoops_version.php, xoops_lib).
 *
 * <p>All {@link FilenameIndex} lookups run inside a read action so callers on background
 * threads (e.g. post-startup activities) do not trip platform threading assertions.
 */
@Service(Service.Level.PROJECT)
public final class XoopsProjectService {

    private final Project project;

    public XoopsProjectService(@NotNull Project project) {
        this.project = project;
    }

    public static @NotNull XoopsProjectService getInstance(@NotNull Project project) {
        return project.getService(XoopsProjectService.class);
    }

    public boolean isXoopsProject() {
        return findMainfile() != null || !findXoopsVersionFiles().isEmpty();
    }

    public @Nullable VirtualFile findMainfile() {
        try {
            return ReadAction.compute(this::findMainfileUnderReadLock);
        } catch (IndexNotReadyException e) {
            return null;
        }
    }

    @RequiresReadLock
    private @Nullable VirtualFile findMainfileUnderReadLock() {
        Collection<VirtualFile> files = FilenameIndex.getVirtualFilesByName(
                "mainfile.php",
                GlobalSearchScope.projectScope(project)
        );
        for (VirtualFile file : files) {
            // Prefer site root mainfile over copies under docs/vendor.
            String path = file.getPath().replace('\\', '/').toLowerCase();
            if (path.contains("/vendor/") || path.contains("/docs/") || path.contains("/node_modules/")) {
                continue;
            }
            return file;
        }
        return files.isEmpty() ? null : files.iterator().next();
    }

    public @NotNull List<VirtualFile> findXoopsVersionFiles() {
        try {
            return ReadAction.compute(this::findXoopsVersionFilesUnderReadLock);
        } catch (IndexNotReadyException e) {
            return Collections.emptyList();
        }
    }

    @RequiresReadLock
    private @NotNull List<VirtualFile> findXoopsVersionFilesUnderReadLock() {
        Collection<VirtualFile> files = FilenameIndex.getVirtualFilesByName(
                "xoops_version.php",
                GlobalSearchScope.projectScope(project)
        );
        List<VirtualFile> out = new ArrayList<>();
        for (VirtualFile file : files) {
            String path = file.getPath().replace('\\', '/').toLowerCase();
            if (path.contains("/vendor/") || path.contains("/docs/phpstormplugins/")) {
                continue;
            }
            out.add(file);
        }
        return out;
    }

    /**
     * Module directory names inferred from …/modules/{dirname}/xoops_version.php
     */
    public @NotNull List<String> findModuleDirnames() {
        Set<String> names = new LinkedHashSet<>();
        for (VirtualFile versionFile : findXoopsVersionFiles()) {
            VirtualFile moduleDir = versionFile.getParent();
            if (moduleDir == null) {
                continue;
            }
            VirtualFile modulesRoot = moduleDir.getParent();
            if (modulesRoot != null && "modules".equalsIgnoreCase(modulesRoot.getName())) {
                names.add(moduleDir.getName());
            }
        }
        return new ArrayList<>(names);
    }

    public @Nullable VirtualFile findXoopsLib() {
        try {
            return ReadAction.compute(this::findXoopsLibUnderReadLock);
        } catch (IndexNotReadyException e) {
            return null;
        }
    }

    @RequiresReadLock
    private @Nullable VirtualFile findXoopsLibUnderReadLock() {
        Collection<VirtualFile> dirs = FilenameIndex.getVirtualFilesByName(
                "xoops_lib",
                GlobalSearchScope.projectScope(project)
        );
        for (VirtualFile file : dirs) {
            if (file.isDirectory()) {
                return file;
            }
        }
        VirtualFile main = findMainfileUnderReadLock();
        if (main != null && main.getParent() != null) {
            VirtualFile sibling = main.getParent().findChild("xoops_lib");
            if (sibling != null && sibling.isDirectory()) {
                return sibling;
            }
        }
        return null;
    }

    public @NotNull String describe() {
        if (!isXoopsProject()) {
            return "No XOOPS markers found (mainfile.php / xoops_version.php).";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("XOOPS project detected.\n");
        VirtualFile main = findMainfile();
        if (main != null) {
            sb.append("mainfile: ").append(main.getPath()).append('\n');
        }
        VirtualFile lib = findXoopsLib();
        if (lib != null) {
            sb.append("xoops_lib: ").append(lib.getPath()).append('\n');
        }
        List<String> modules = findModuleDirnames();
        sb.append("modules (").append(modules.size()).append("): ");
        if (modules.isEmpty()) {
            sb.append("(none indexed)");
        } else {
            sb.append(String.join(", ", modules.subList(0, Math.min(40, modules.size()))));
            if (modules.size() > 40) {
                sb.append(" …");
            }
        }
        return sb.toString();
    }
}
