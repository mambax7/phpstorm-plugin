package org.xoops.support.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
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
        XoopsProjectReport report = new XoopsProjectScanner().scan(Path.of(project.getBasePath()));
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
        Messages.showInfoMessage(project, sb.toString(), "XOOPS Support - Project Info");
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
