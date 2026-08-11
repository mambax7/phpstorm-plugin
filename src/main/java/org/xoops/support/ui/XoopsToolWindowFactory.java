package org.xoops.support.ui;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public final class XoopsToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        XoopsToolWindowPanel panel = new XoopsToolWindowPanel(project);
        toolWindow.getContentManager().addContent(
                ContentFactory.getInstance().createContent(panel, "Overview", false)
        );
    }
}
