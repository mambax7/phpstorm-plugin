package org.xoops.support.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.xoops.support.scanner.XoopsModuleInfo;
import org.xoops.support.scanner.XoopsProjectReport;
import org.xoops.support.scanner.XoopsProjectScanner;

import java.nio.file.Path;

public final class ShowXoopsProjectInfoAction extends AnAction implements DumbAware {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null || project.getBasePath() == null) {
            return;
        }
        String basePath = project.getBasePath();
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Scanning XOOPS project", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                XoopsProjectReport report;
                try {
                    report = new XoopsProjectScanner().scan(Path.of(basePath));
                } catch (Exception ex) {
    String msg = "Scan failed: " + ex.getMessage();
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(
                            () -> Messages.showErrorDialog(project, msg, "XOOPS Support - Project Info"),
                            com.intellij.openapi.application.ModalityState.nonModal(),
                            project.getDisposed()
                    );
                    return;
                }
                String message = formatReport(report);
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(
                        () -> Messages.showInfoMessage(project, message, "XOOPS Support - Project Info"),
                        com.intellij.openapi.application.ModalityState.nonModal(),
                        project.getDisposed()
                );
            }
        });
    }

    private static @NotNull String formatReport(XoopsProjectReport report) {
        StringBuilder sb = new StringBuilder();
        if (!report.xoopsProject()) {
            sb.append("No XOOPS markers found (mainfile.php / xoops_version.php).");
        } else {
            sb.append(report.profile().displayName()).append('\n');
            sb.append("Web root: ").append(report.webRoot()).append('\n');
            sb.append("Modules: ").append(report.modules().size()).append('\n');
            int i = 0;
            for (XoopsModuleInfo m : report.modules()) {
                if (i++ >= 30) {
                    sb.append("…\n");
                    break;
                }
                sb.append("  - ").append(m.dirname())
                        .append(" [").append(m.manifestLabel()).append("]")
                        .append(" tpl=").append(m.templateCount())
                        .append(" lang=").append(m.languageFileCount())
                        .append('\n');
            }
            sb.append("Sample findings: ").append(report.findings().size())
                    .append(" (see tool window for details)");
        }
        return sb.toString();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
