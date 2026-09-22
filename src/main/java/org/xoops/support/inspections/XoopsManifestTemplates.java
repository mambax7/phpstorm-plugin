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

    public enum Section {
        TEMPLATES,
        BLOCKS
    }

    /** A registered template name and the offset of that name in the manifest text. */
    public record Registration(@NotNull String name, int nameOffset, @NotNull Section section) {
        public boolean block() {
            return section == Section.BLOCKS;
        }
    }

    private static final Pattern MODVERSION_TEMPLATES = Pattern.compile(
            "(?i)\\$modversion\\s*\\[\\s*['\"](templates|blocks)['\"]\\s*\\]"
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
        String code = PhpTextUtil.maskCommentsAndStrings(commentMasked);
        List<Registration> out = new ArrayList<>();
        Set<Integer> seen = new LinkedHashSet<>();
        Matcher stmt = MODVERSION_TEMPLATES.matcher(commentMasked);
        int searchFrom = 0;
        while (stmt.find(searchFrom)) {
            if (code.charAt(stmt.start()) != '$') {
                searchFrom = stmt.end();
                continue;
            }
            int end = statementEnd(commentMasked, stmt.end());
            Section section = "blocks".equalsIgnoreCase(stmt.group(1)) ? Section.BLOCKS : Section.TEMPLATES;
            Matcher m = FILE_OR_TEMPLATE.matcher(commentMasked);
            m.region(stmt.end(), end);
            while (m.find()) {
                int assignment = commentMasked.indexOf('=', m.start());
                if (code.charAt(assignment) == '=' && seen.add(m.start(1))) {
                    out.add(new Registration(m.group(1).replace('\\', '/'), m.start(1), section));
                }
            }
            searchFrom = Math.max(end, stmt.end());
            if (searchFrom >= commentMasked.length()) {
                break;
            }
        }
        return out;
    }

    /** Module-root-relative, lower-case disk paths, preserving block/page identity. */
    public static @NotNull Set<String> keys(@NotNull String manifestText) {
        Set<String> out = new LinkedHashSet<>();
        for (Registration r : find(PhpTextUtil.maskCommentsOnly(manifestText))) {
            out.add(diskPath(r.name(), r.block()).toLowerCase(Locale.ROOT));
        }
        return out;
    }

    /**
     * Module-root-relative path where a missing registered file should be created.
     * Bare block names go under {@code templates/blocks/}; already-prefixed names stay as spelled.
     */
    public static @NotNull String diskPath(@NotNull String name, boolean block) {
        String n = name.replace('\\', '/');
        while (n.startsWith("/")) {
            n = n.substring(1);
        }
        if (n.startsWith("templates/") || n.startsWith("blocks/")) {
            return n;
        }
        return block ? "templates/blocks/" + n : "templates/" + n;
    }

    /** Shortest registration spelling that still resolves to the same module-relative path. */
    public static @NotNull String registrationName(@NotNull String relativePath) {
        String normalized = relativePath.replace('\\', '/');
        String candidate = normalized.startsWith("templates/")
                ? normalized.substring("templates/".length()) : normalized;
        return diskPath(candidate, false).equals(normalized) ? candidate : normalized;
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
