package org.xoops.support;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xoops.support.settings.XoopsSettingsState;

/**
 * Shared enablement gate for inspections, completion, and other project features.
 */
public final class XoopsSupportPlugin {

    private XoopsSupportPlugin() {
    }

    public static boolean isEnabled(@Nullable Project project) {
        if (project == null || project.isDisposed()) {
            return false;
        }
        return XoopsSettingsState.getInstance(project).enabled;
    }

    public static boolean isEnabled(@NotNull com.intellij.psi.PsiFile file) {
        return isEnabled(file.getProject());
    }
}
