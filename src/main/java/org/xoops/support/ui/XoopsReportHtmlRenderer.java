package org.xoops.support.ui;

import org.jetbrains.annotations.NotNull;
import org.xoops.support.scanner.XoopsFinding;
import org.xoops.support.scanner.XoopsModuleInfo;
import org.xoops.support.scanner.XoopsProjectReport;

/**
 * HTML overview for the tool window (adapted filesystem).
 *
 * <p>Status pages (idle / disabled / cancelled / failed) share {@link #wrapBody(String)} so
 * padding and font styling live in one place.
 */
public final class XoopsReportHtmlRenderer {

    private static final String BODY_OPEN = "<html><body style='font-family:sans-serif;padding:8px'>";
    private static final String BODY_CLOSE = "</body></html>";

    /** Shared chrome for tool-window HTML snippets. */
    public static @NotNull String wrapBody(@NotNull String innerHtml) {
        return BODY_OPEN + innerHtml + BODY_CLOSE;
    }

    public static @NotNull String disabledHtml() {
        return wrapBody(
                "<p><b>XOOPS Support is disabled</b> for this project.</p>"
                        + "<p>Settings → Languages &amp; Frameworks → XOOPS Support.</p>"
        );
    }

    public static @NotNull String idleHtml() {
        return wrapBody(
                "<p><b>Overview is idle</b> — no automatic project scan.</p>"
                        + "<p>Click <b>Refresh</b> (or <b>Tools → XOOPS Support → Refresh XOOPS Overview</b>) "
                        + "to scan modules for convention findings. "
                        + "Full-tree scans read every module <code>.php</code>/<code>.tpl</code> and are "
                        + "expensive on large monorepos.</p>"
                        + "<p>Optional: Settings → XOOPS Support → "
                        + "<i>Auto-scan project when Overview tool window opens</i> (off by default).</p>"
        );
    }

    public static @NotNull String cancelledHtml() {
        return wrapBody(
                "<p><b>Scan cancelled.</b></p>"
                        + "<p>Click <b>Refresh</b> to try again.</p>"
        );
    }

    public static @NotNull String scanningHtml() {
        return wrapBody("Scanning XOOPS project…");
    }

    public static @NotNull String noPathHtml() {
        return wrapBody("<p>No project path is available.</p>");
    }

    public static @NotNull String failedHtml(@NotNull String message) {
        return wrapBody("<p><b>Scan failed</b></p><p>" + escape(message) + "</p>");
    }

    public String render(XoopsProjectReport report) {
        if (!report.xoopsProject()) {
            return wrapBody(
                    "<h2>XOOPS not detected</h2>"
                            + "<p>Open a XOOPS core (with <code>mainfile.php</code>) or a module containing "
                            + "<code>xoops_version.php</code>.</p>"
            );
        }

        StringBuilder html = new StringBuilder(4096);
        html.append(BODY_OPEN)
                .append("<h2>").append(escape(report.profile().displayName())).append("</h2>")
                .append("<p><b>Web root:</b> <code>")
                .append(escape(report.webRoot().toString()))
                .append("</code></p>")
                .append("<h3>Modules (").append(report.modules().size()).append(")</h3>");

        if (report.modules().isEmpty()) {
            html.append("<p>No module manifests were found.</p>");
        } else {
            html.append("<table cellspacing='0' cellpadding='4' border='0'>")
                    .append("<tr style='background:#e3f2fd'><th align='left'>Module</th><th align='left'>Manifest</th>")
                    .append("<th>TPL</th><th>Lang</th><th>Pre</th><th>Cls</th></tr>");
            for (XoopsModuleInfo module : report.modules()) {
                html.append("<tr><td><b>").append(escape(module.dirname())).append("</b></td>")
                        .append("<td>").append(escape(module.manifestLabel())).append("</td>")
                        .append("<td align='right'>").append(module.templateCount()).append("</td>")
                        .append("<td align='right'>").append(module.languageFileCount()).append("</td>")
                        .append("<td align='right'>").append(module.preloadCount()).append("</td>")
                        .append("<td align='right'>").append(module.classCount()).append("</td></tr>");
            }
            html.append("</table>");
        }

        html.append("<h3>Findings (").append(report.findings().size()).append(")</h3>");
        if (report.findings().isEmpty()) {
            html.append("<p>No high-confidence XOOPS convention problems found in a sample scan.</p>");
        } else {
            html.append("<ul>");
            int shown = 0;
            for (XoopsFinding finding : report.findings()) {
                if (shown++ >= 80) {
                    html.append("<li>... more findings truncated (refresh after fixes)</li>");
                    break;
                }
                String href = finding.path().toUri() + "#" + finding.line();
                html.append("<li><b>").append(escape(finding.kind())).append(":</b> ")
                        .append(escape(finding.message()))
                        .append(" - <a href='").append(escape(href)).append("'>")
                        .append(escape(finding.path().getFileName() + ":" + finding.line()))
                        .append("</a></li>");
            }
            html.append("</ul>");
        }

        html.append("<hr/><p style='color:#666;font-size:90%'>XOOPS Support scanner (modules + sample findings). ")
                .append("Editor inspections run separately under PHP / XOOPS.</p>");
        return html.append(BODY_CLOSE).toString();
    }

    private static String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
