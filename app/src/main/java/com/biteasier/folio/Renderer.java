package com.biteasier.folio;

import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.footnotes.FootnotesExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.gfm.alerts.AlertsExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Turns text into the HTML body shown by the reader. No Android classes, so it is unit tested. */
final class Renderer {
    private static final List<Extension> EXTENSIONS = Arrays.asList(
            TablesExtension.create(), StrikethroughExtension.create(), AlertsExtension.create(),
            TaskListItemsExtension.create(), AutolinkExtension.create(), HeadingAnchorExtension.create(),
            FootnotesExtension.create(), YamlFrontMatterExtension.create());

    /** The tags GitHub also disables, plus ones that could cover or restyle the page. */
    private static final Pattern UNSAFE_TAG = Pattern.compile(
            "<(?=/?(?:title|textarea|style|xmp|iframe|noembed|noframes|script|plaintext|meta|base|link"
                    + "|object|embed|form|input|button|select|template|frameset|frame|head|body|html|main"
                    + "|audio|video|dialog)(?:[\\s/>]|$))",
            Pattern.CASE_INSENSITIVE);

    /** Nesting deeper than this is no longer readable on a phone, and could exhaust the stack. */
    private static final int MAX_NESTING = 60;
    private static final int LINES_PER_CHUNK = 150;
    private static final int CHARS_PER_CHUNK = 20000;

    private Renderer() {}

    static boolean looksLikeMarkdown(String name, String mime) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.matches(".*\\.(md|markdown|mdown|mkdn|mkd|mdwn|mdtxt|mdtext|rmd|qmd|mdx)")) return true;
        if (n.matches(".*\\.[a-z0-9]{1,8}")) return false;
        return mime != null && mime.contains("markdown");
    }

    static String markdown(String text) {
        Node doc = Parser.builder().extensions(EXTENSIONS).maxOpenBlockParsers(MAX_NESTING).build().parse(text);
        return HtmlRenderer.builder()
                .extensions(EXTENSIONS)
                .sanitizeUrls(false) // links are checked when tapped; scripts are blocked by policy
                .nodeRendererFactory(c -> new SafeHtml(c.getWriter()))
                .attributeProviderFactory(c -> (node, tag, attrs) -> {
                    if (node instanceof TableBlock && tag.equals("table")) attrs.put("dir", "auto");
                })
                .build().render(doc);
    }

    static String plain(String text) {
        StringBuilder out = new StringBuilder(text.length() + text.length() / 8 + 64);
        int start = 0, lines = 0;
        final int n = text.length();
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            boolean cut = false;
            if (c == '\n' && ++lines >= LINES_PER_CHUNK) cut = true;
            if (i - start >= CHARS_PER_CHUNK && !Character.isHighSurrogate(c)) cut = true;
            if (cut) {
                chunk(out, text, start, i + 1);
                start = i + 1;
                lines = 0;
            }
        }
        if (start < n || n == 0) chunk(out, text, start, n);
        return out.toString();
    }

    private static void chunk(StringBuilder out, String s, int from, int to) {
        out.append("<pre class=t>");
        // HTML drops one newline right after <pre>, so always give it one to drop.
        out.append('\n');
        escape(out, s, from, to);
        out.append("</pre>");
    }

    static String escape(String s) {
        StringBuilder b = new StringBuilder();
        escape(b, s, 0, s.length());
        return b.toString();
    }

    private static void escape(StringBuilder out, String s, int from, int to) {
        for (int i = from; i < to; i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': out.append("&amp;"); break;
                case '<': out.append("&lt;"); break;
                case '>': out.append("&gt;"); break;
                case '"': out.append("&quot;"); break;
                case 0: out.append('�'); break;
                default: out.append(c);
            }
        }
    }

    static String filterTags(String html) {
        return UNSAFE_TAG.matcher(html).replaceAll("&lt;");
    }
}
