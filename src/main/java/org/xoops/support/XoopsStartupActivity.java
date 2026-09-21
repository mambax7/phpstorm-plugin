package org.xoops.support;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
public final class XoopsStartupActivity implements ProjectActivity {

    @Override
    public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        scheduleNotification(project);
        return Unit.INSTANCE;
    }

    private void scheduleNotification(@NotNull Project project) {
        if (project.isDisposed() || !notificationsEnabled(project)) {
            return;
        }
        DumbService.getInstance(project).runWhenSmart(() -> {
            if (project.isDisposed()) {
                return;
            }
            ApplicationManager.getApplication().executeOnPooledThread(() -> notifyIfXoops(project));
        });
    }

    private static boolean notificationsEnabled(@NotNull Project project) {
        XoopsSettingsState settings = XoopsSettingsState.getInstance(project);
        return settings.enabled && !settings.suppressStartupNotification;
    }

    private static void notifyIfXoops(@NotNull Project project) {
        if (project.isDisposed() || !notificationsEnabled(project)) {
            return;
        }
        XoopsProjectService service = XoopsProjectService.getInstance(project);
        if (!service.isXoopsProject()) {
            return;
        }
        int modules = service.findModuleDirnames().size();
        ApplicationManager.getApplication().invokeLater(
                () -> showBalloon(project, modules),
                ModalityState.nonModal(),
                project.getDisposed()
        );
    }

    private static void showBalloon(@NotNull Project project, int modules) {
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
    }
}
