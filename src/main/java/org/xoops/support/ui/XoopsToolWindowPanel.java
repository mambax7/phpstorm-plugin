package org.xoops.support.ui;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;
import org.xoops.support.scanner.XoopsProjectReport;
import org.xoops.support.scanner.XoopsProjectScanner;
import org.xoops.support.settings.XoopsSettingsState;

import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.event.HyperlinkEvent;
import java.awt.BorderLayout;
import java.net.URI;
import java.nio.file.Path;

/**
 * Background scan + clickable HTML findings.
 */
public final class XoopsToolWindowPanel extends JPanel {

    private final Project project;
    private final JEditorPane overview = new JEditorPane("text/html",
            "<html><body style='font-family:sans-serif;padding:8px'>Scanning XOOPS project…</body></html>");
    private final JLabel status = new JLabel("Ready");
    private final JButton refreshButton = new JButton("Refresh");

    public XoopsToolWindowPanel(Project project) {
        super(new BorderLayout());
        this.project = project;

        overview.setEditable(false);
        overview.setContentType("text/html");
        overview.addHyperlinkListener(this::openFinding);

        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        refreshButton.addActionListener(event -> refresh());
        toolbar.add(refreshButton);
        toolbar.addSeparator();
        toolbar.add(status);

        add(toolbar, BorderLayout.NORTH);
        add(new JBScrollPane(overview), BorderLayout.CENTER);
        refresh();
    }

    public void refresh() {
        String basePath = project.getBasePath();
        if (basePath == null || project.isDisposed()) {
            overview.setText("<html><body>No project path is available.</body></html>");
            status.setText("No path");
            refreshButton.setEnabled(true);
            return;
        }
        if (!XoopsSettingsState.getInstance(project).enabled) {
            overview.setText("<html><body><p>XOOPS Support is disabled for this project "
                    + "(Settings → XOOPS Support).</p></body></html>");
            status.setText("Disabled");
            refreshButton.setEnabled(true);
            return;
        }

        refreshButton.setEnabled(false);
        status.setText("Scanning…");

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Scanning XOOPS project", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    XoopsProjectReport report = new XoopsProjectScanner().scan(Path.of(basePath));
                    String html = new XoopsReportHtmlRenderer().render(report);
                    ToolWindowManager.getInstance(project).invokeLater(() -> applyReport(report, html));
                } catch (Throwable t) {
                    String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                    String html = "<html><body style='font-family:sans-serif;padding:8px'>"
                            + "<p><b>Scan failed</b></p><p>"
                            + escape(msg)
                            + "</p></body></html>";
                    ToolWindowManager.getInstance(project).invokeLater(() -> applyError(html));
                }
            }
        });
    }

    private void applyReport(XoopsProjectReport report, String html) {
        if (project.isDisposed()) {
            return;
        }
        overview.setText(html);
        overview.setCaretPosition(0);
        status.setText(report.modules().size() + " modules, " + report.findings().size() + " findings");
        refreshButton.setEnabled(true);
    }

    private void applyError(String html) {
        if (project.isDisposed()) {
            return;
        }
        overview.setText(html);
        overview.setCaretPosition(0);
        status.setText("Scan failed");
        refreshButton.setEnabled(true);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void openFinding(HyperlinkEvent event) {
        if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED || event.getDescription() == null) {
            return;
        }
        try {
            URI link = URI.create(event.getDescription());
            int line = link.getFragment() == null ? 1 : Integer.parseInt(link.getFragment());
            URI fileUri = new URI(link.getScheme(), link.getAuthority(), link.getPath(), link.getQuery(), null);
            VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Path.of(fileUri));
            if (file != null) {
                new OpenFileDescriptor(project, file, Math.max(0, line - 1), 0).navigate(true);
            }
        } catch (Exception ignored) {
            status.setText("Could not open the selected finding");
        }
    }
}
