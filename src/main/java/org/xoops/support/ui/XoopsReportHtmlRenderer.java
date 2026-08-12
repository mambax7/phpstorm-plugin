package org.xoops.support.ui;

import org.xoops.support.scanner.XoopsFinding;
import org.xoops.support.scanner.XoopsModuleInfo;
import org.xoops.support.scanner.XoopsProjectReport;

/**
 * HTML overview for the tool window (adapted filesystem).
 */
public final class XoopsReportHtmlRenderer {

    public String render(XoopsProjectReport report) {
        if (!report.xoopsProject()) {
            return "<html><body style='font-family:sans-serif;padding:8px'>"
                    + "<h2>XOOPS not detected</h2>"
                    + "<p>Open a XOOPS core (with <code>mainfile.php</code>) or a module containing "
                    + "<code>xoops_version.php</code>.</p>"
                    + "</body></html>";
        }

        StringBuilder html = new StringBuilder(4096);
        html.append("<html><body style='font-family:sans-serif;padding:8px'>")
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
        return html.append("</body></html>").toString();
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
