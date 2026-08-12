package org.xoops.support.ui;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public final class XoopsToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        XoopsToolWindowPanel panel = new XoopsToolWindowPanel(project);
        Content content = ContentFactory.getInstance().createContent(panel, "Overview", false);
        // Dispose panel (listeners / pending UI) when the tool-window content is removed on unload.
        Disposer.register(content, panel);
        toolWindow.getContentManager().addContent(content);
    }
}
