package org.xoops.support;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;
import org.xoops.support.settings.XoopsSettingsState;

/**
 * Notifies once when a XOOPS-shaped project is opened.
 *
 * <p>Post-startup runs on a background dispatcher without a read lock. Detection uses
 * {@link com.intellij.psi.search.FilenameIndex}, so work is deferred until indexes are
 * smart ({@link DumbService#runWhenSmart}) and the service wraps index access in a read action.
 */
public final class XoopsStartupActivity implements StartupActivity {

    @Override
    public void runActivity(@NotNull Project project) {
        if (project.isDisposed()) {
            return;
        }
        XoopsSettingsState settings = XoopsSettingsState.getInstance(project);
        if (!settings.enabled || settings.suppressStartupNotification) {
            return;
        }

        // FilenameIndex is not reliable (and may throw) while dumb; wait for smart mode.
        DumbService.getInstance(project).runWhenSmart(() -> {
            if (project.isDisposed()) {
                return;
            }
            XoopsSettingsState current = XoopsSettingsState.getInstance(project);
            if (!current.enabled || current.suppressStartupNotification) {
                return;
            }
            XoopsProjectService service = XoopsProjectService.getInstance(project);
            if (!service.isXoopsProject()) {
                return;
            }
            int modules = service.findModuleDirnames().size();
            NotificationGroupManager.getInstance()
                    .getNotificationGroup("XOOPS Support")
                    .createNotification(
                            "XOOPS Support active",
                            "Detected XOOPS markers (" + modules + " module(s) with xoops_version.php). "
                                    + "See Settings → Editor → Inspections → PHP → XOOPS, "
                                    + "and Tools → XOOPS Support.",
                            NotificationType.INFORMATION
                    )
                    .notify(project);
        });
    }
}
