package org.xoops.support.inspections;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Template registrations in {@code xoops_version.php}, shared by the two template
 * inspections and the project scanner so they agree on what counts as registered.
 *
 * <p>A registration is a {@code 'file' => 'x.tpl'} / {@code 'template' => 'x.tpl'} pair, or the
 * {@code ['file'] = 'x.tpl'} / {@code ['template'] = 'x.tpl'} assignment form, that sits inside a
 * statement starting with {@code $modversion['templates']} or {@code $modversion['blocks']}.
 * Registration-like text in an unrelated string ({@code $help = "'file' => 'x.tpl'";}) is ignored.
 */
public final class XoopsManifestTemplates {

    /** A registered template name and the offset of that name in the manifest text. */
    public record Registration(@NotNull String name, int nameOffset) {
    }

    private static final Pattern MODVERSION_TEMPLATES = Pattern.compile(
            "(?i)\\$modversion\\s*\\[\\s*['\"](?:templates|blocks)['\"]\\s*\\]"
    );
    private static final Pattern FILE_OR_TEMPLATE = Pattern.compile(
            "(?is)['\"](?:file|template)['\"]\\s*\\]?\\s*=>?\\s*['\"]([^'\"]+\\.tpl)['\"]"
    );

    private XoopsManifestTemplates() {
    }

    /**
     * @param commentMasked manifest source with comments masked and strings kept
     *                      ({@link PhpTextUtil#maskCommentsOnly(String)}); offsets are preserved
     */
    public static @NotNull List<Registration> find(@NotNull String commentMasked) {
        List<Registration> out = new ArrayList<>();
        Set<Integer> seen = new LinkedHashSet<>();
        Matcher stmt = MODVERSION_TEMPLATES.matcher(commentMasked);
        int searchFrom = 0;
        while (stmt.find(searchFrom)) {
            int end = statementEnd(commentMasked, stmt.end());
            Matcher m = FILE_OR_TEMPLATE.matcher(commentMasked);
            m.region(stmt.end(), end);
            while (m.find()) {
                if (seen.add(m.start(1))) {
                    out.add(new Registration(m.group(1).replace('\\', '/'), m.start(1)));
                }
            }
            searchFrom = Math.max(end, stmt.end());
            if (searchFrom >= commentMasked.length()) {
                break;
            }
        }
        return out;
    }

    /**
     * Lower-case lookup keys for every registration in {@code manifestText} (raw source).
     * Each name is added as spelled, and also relative to {@code templates/} and to
     * {@code blocks/}, so the XOOPS bare-name convention and module-root spellings both match.
     */
    public static @NotNull Set<String> keys(@NotNull String manifestText) {
        Set<String> out = new LinkedHashSet<>();
        for (Registration r : find(PhpTextUtil.maskCommentsOnly(manifestText))) {
            String key = r.name().toLowerCase(Locale.ROOT);
            out.add(key);
            if (key.startsWith("templates/")) {
                key = key.substring("templates/".length());
                out.add(key);
            }
            if (key.startsWith("blocks/")) {
                out.add(key.substring("blocks/".length()));
            }
        }
        return out;
    }

    /** Offset just past the {@code ;} that ends the statement, skipping {@code ;} inside quotes. */
    private static int statementEnd(@NotNull String text, int from) {
        char quote = 0;
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == ';') {
                return i + 1;
            }
        }
        return text.length();
    }
}
