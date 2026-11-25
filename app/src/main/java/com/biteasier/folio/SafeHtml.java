package com.biteasier.folio;

import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Node;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlWriter;

import java.util.Set;

/** Writes HTML found inside Markdown, with unsafe tags shown as text. */
final class SafeHtml implements NodeRenderer {
    private final HtmlWriter out;

    SafeHtml(HtmlWriter out) {
        this.out = out;
    }

    @Override
    public Set<Class<? extends Node>> getNodeTypes() {
        return Set.of(HtmlBlock.class, HtmlInline.class);
    }

    @Override
    public void render(Node node) {
        if (node instanceof HtmlBlock) {
            out.line();
            out.raw(Renderer.filterTags(((HtmlBlock) node).getLiteral()));
            out.line();
        } else {
            out.raw(Renderer.filterTags(((HtmlInline) node).getLiteral()));
        }
    }
}
