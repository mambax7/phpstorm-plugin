package org.xoops.support;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;
import org.xoops.support.settings.XoopsSettingsState;

/**
 * Notifies once when a XOOPS-shaped project is opened.
 *
 * <p>Detection uses {@link com.intellij.psi.search.FilenameIndex}, so work waits for smart mode
 * and runs on a background thread (not the EDT) under a read action inside the service.
 *
 * <p>All deferred work is expired with {@link Project#getDisposed()} so pending callbacks
 * do not pin the plugin classloader across unload.
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

        DumbService.getInstance(project).runWhenSmart(() -> {
            if (project.isDisposed()) {
                return;
            }
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
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
                ApplicationManager.getApplication().invokeLater(
                        () -> {
                            if (project.isDisposed()) {
                                return;
                            }
                            NotificationGroupManager.getInstance()
                                    .getNotificationGroup("XOOPS Support")
                                    .createNotification(
                                            "XOOPS Support active",
                                            "Detected XOOPS markers (" + modules + " module(s) with xoops_version.php). "
                                                    + "See Settings → Editor → Inspections → XOOPS, "
                                                    + "and Tools → XOOPS Support.",
                                            NotificationType.INFORMATION
                                    )
                                    .notify(project);
                        },
                        ModalityState.nonModal(),
                        project.getDisposed()
                );
            });
        });
    }
}
