package org.xoops.support.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
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
import javax.swing.event.HyperlinkListener;
import java.awt.BorderLayout;
import java.awt.event.ActionListener;
import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Background scan + clickable HTML findings.
 *
 * <p>Implements {@link Disposable} so listeners and deferred UI updates do not pin the
 * plugin classloader after tool-window content is disposed on plugin unload.
 *
 * <p>Each {@link #refresh()} bumps a request generation so older background scans cannot
 * overwrite a newer result (toolbar Refresh and Tools → Refresh XOOPS Overview share this path).
 */
public final class XoopsToolWindowPanel extends JPanel implements Disposable {

    private final Project project;
    private final JEditorPane overview = new JEditorPane("text/html",
            "<html><body style='font-family:sans-serif;padding:8px'>Scanning XOOPS project…</body></html>");
    private final JLabel status = new JLabel("Ready");
    private final JButton refreshButton = new JButton("Refresh");
    private final AtomicBoolean disposed = new AtomicBoolean(false);
    private final AtomicLong scanGeneration = new AtomicLong();
    private final HyperlinkListener hyperlinkListener = this::openFinding;
    private final ActionListener refreshListener = event -> refresh();

    public XoopsToolWindowPanel(Project project) {
        super(new BorderLayout());
        this.project = project;

        overview.setEditable(false);
        overview.setContentType("text/html");
        overview.addHyperlinkListener(hyperlinkListener);

        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        refreshButton.addActionListener(refreshListener);
        toolbar.add(refreshButton);
        toolbar.addSeparator();
        toolbar.add(status);

        add(toolbar, BorderLayout.NORTH);
        add(new JBScrollPane(overview), BorderLayout.CENTER);

        // Never walk the module tree on tool-window create by default — monorepos freeze boot.
        XoopsSettingsState settings = XoopsSettingsState.getInstance(project);
        if (!settings.enabled) {
            showDisabled();
        } else if (settings.autoScanOnToolWindowOpen) {
            refresh();
        } else {
            showIdlePrompt();
        }
    }

    private void showDisabled() {
        overview.setText("<html><body style='font-family:sans-serif;padding:8px'>"
                + "<p><b>XOOPS Support is disabled</b> for this project.</p>"
                + "<p>Settings → Languages &amp; Frameworks → XOOPS Support.</p>"
                + "</body></html>");
        status.setText("Disabled");
        refreshButton.setEnabled(true);
    }

    private void showIdlePrompt() {
        overview.setText("<html><body style='font-family:sans-serif;padding:8px'>"
                + "<p><b>Overview is idle</b> — no automatic project scan.</p>"
                + "<p>Click <b>Refresh</b> (or <b>Tools → XOOPS Support → Refresh XOOPS Overview</b>) "
                + "to scan modules for convention findings. "
                + "Full-tree scans read every module <code>.php</code>/<code>.tpl</code> and are "
                + "expensive on large monorepos.</p>"
                + "<p>Optional: Settings → XOOPS Support → "
                + "<i>Auto-scan project when Overview tool window opens</i> (off by default).</p>"
                + "</body></html>");
        status.setText("Idle — click Refresh to scan");
        refreshButton.setEnabled(true);
    }

    public void refresh() {
        if (disposed.get() || project.isDisposed()) {
            return;
        }
        String basePath = project.getBasePath();
        if (basePath == null) {
            overview.setText("<html><body>No project path is available.</body></html>");
            status.setText("No path");
            refreshButton.setEnabled(true);
            return;
        }
        if (!XoopsSettingsState.getInstance(project).enabled) {
            showDisabled();
            return;
        }

        final long requestId = scanGeneration.incrementAndGet();
        refreshButton.setEnabled(false);
        status.setText("Scanning…");

        // canBeCancelled = true so the user can stop a runaway monorepo walk.
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Scanning XOOPS project", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    indicator.setText("Scanning XOOPS modules (cancellable)…");
                    indicator.checkCanceled();
                    XoopsProjectReport report = new XoopsProjectScanner().scan(Path.of(basePath));
                    indicator.checkCanceled();
                    String html = new XoopsReportHtmlRenderer().render(report);
                    ApplicationManager.getApplication().invokeLater(
                            () -> applyReport(report, html, requestId),
                            ModalityState.any(),
                            __ -> disposed.get() || project.isDisposed() || requestId != scanGeneration.get()
                    );
                } catch (ProcessCanceledException e) {
                    ApplicationManager.getApplication().invokeLater(
                            () -> applyCancelled(requestId),
                            ModalityState.any(),
                            __ -> disposed.get() || project.isDisposed() || requestId != scanGeneration.get()
                    );
                } catch (Exception e) {
                    String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    String html = "<html><body style='font-family:sans-serif;padding:8px'>"
                            + "<p><b>Scan failed</b></p><p>"
                            + escape(msg)
                            + "</p></body></html>";
                    ApplicationManager.getApplication().invokeLater(
                            () -> applyError(html, requestId),
                            ModalityState.any(),
                            __ -> disposed.get() || project.isDisposed() || requestId != scanGeneration.get()
                    );
                }
            }
        });
    }

    private void applyCancelled(long requestId) {
        if (disposed.get() || project.isDisposed() || requestId != scanGeneration.get()) {
            return;
        }
        overview.setText("<html><body style='font-family:sans-serif;padding:8px'>"
                + "<p><b>Scan cancelled.</b></p>"
                + "<p>Click <b>Refresh</b> to try again.</p></body></html>");
        status.setText("Cancelled");
        refreshButton.setEnabled(true);
    }

    private void applyReport(XoopsProjectReport report, String html, long requestId) {
        if (disposed.get() || project.isDisposed() || requestId != scanGeneration.get()) {
            return;
        }
        overview.setText(html);
        overview.setCaretPosition(0);
        status.setText(report.modules().size() + " modules, " + report.findings().size() + " findings");
        refreshButton.setEnabled(true);
    }

    private void applyError(String html, long requestId) {
        if (disposed.get() || project.isDisposed() || requestId != scanGeneration.get()) {
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
        if (disposed.get() || event.getEventType() != HyperlinkEvent.EventType.ACTIVATED
                || event.getDescription() == null) {
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

    @Override
    public void dispose() {
        if (!disposed.compareAndSet(false, true)) {
            return;
        }
        scanGeneration.incrementAndGet(); // invalidate any in-flight scan callbacks
        overview.removeHyperlinkListener(hyperlinkListener);
        refreshButton.removeActionListener(refreshListener);
    }
}
