package com.biteasier.folio;

import static org.junit.Assert.*;

import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class CoreTest {
    private static String html(String md) { return Renderer.markdown(md); }

    private static void has(String md, String part) {
        String h = html(md);
        assertTrue(h, h.contains(part));
    }

    private static String dec(String s, String cs) {
        return TextDecoder.decode(s.getBytes(Charset.forName(cs)), null, false).text;
    }

    @Test public void gfm() {
        has("# Hello World", "<h1 id=\"hello-world\">");
        has("- [x] a\n- [ ] b", "type=\"checkbox\"");
        has("|a|b|\n|:-:|-:|\n|1|2|", "<td align=\"center\">1</td>");
        has("~~x~~", "<del>x</del>");
        has("see https://a.example/x.", "href=\"https://a.example/x\"");
        has("> [!NOTE]\n> hi", "markdown-alert-note");
        has("a[^1]\n\n[^1]: note", "footnotes");
        has("---\ntitle: x\n---\n\nbody", "<p>body</p>");
        assertFalse(html("---\ntitle: x\n---\n\nbody").contains("title"));
        has("```js\nlet a = 1 < 2\n```", "<pre><code class=\"language-js\">let a = 1 &lt; 2");
        has("&eacute; &copy;", "é ©");
        has("- a\n  - b\n    1. c", "<ol>");
    }

    @Test public void unsafeTagsShownAsText() {
        for (String t : new String[]{"script", "style", "iframe", "meta", "base", "form", "title", "plaintext"}) {
            assertFalse(html("<" + t + ">x</" + t + ">").toLowerCase().contains("<" + t));
            assertFalse(html("a <" + t.toUpperCase() + " y=1> b").toLowerCase().contains("<" + t));
        }
        has("<details><summary>s</summary>\n\nx\n\n</details>", "<details>");
        has("H<sub>2</sub>O", "<sub>2</sub>");
    }

    @Test public void hostileMarkdown() {
        html("> ".repeat(20000) + "x");
        html("- ".repeat(20000) + "x");
        html("[".repeat(50000));
        html("*a _b ".repeat(20000));
        html("`".repeat(50000));
        html("<".repeat(50000));
        html("\0\uD800 broken \uDC00");
        html("|".repeat(5000) + "\n" + "-|".repeat(5000));
    }

    @Test public void plainKeepsEveryCharacter() {
        String s = ("line <b> & \"q\"\n").repeat(1000) + "z".repeat(100000) + "😀\n\nend";
        String p = Renderer.plain(s).replace("<pre class=t>\n", "").replace("</pre>", "")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&");
        assertEquals(s, p);
        assertTrue(Renderer.plain("").contains("<pre"));
    }

    @Test public void encodings() {
        String s = "Grüße, café, مرحبا, 日本語 😀";
        for (String cs : new String[]{"UTF-8", "UTF-16", "UTF-32LE"}) {
            String in = cs.equals("UTF-32LE") ? "﻿" + s : s;
            assertEquals(s, dec(in, cs));
        }
        assertEquals("plain ascii text", dec("plain ascii text", "UTF-16LE"));
        assertEquals("plain ascii text", dec("plain ascii text", "UTF-16BE"));
        assertEquals("café", dec("café", "windows-1252"));
        assertEquals("hi", dec("﻿hi", "UTF-8"));
        assertEquals("", TextDecoder.decode(new byte[0], null, false).text);
        byte[] ru = "Привет".getBytes(Charset.forName("windows-1251"));
        assertEquals("Привет", TextDecoder.decode(ru, "windows-1251", false).text);
    }

    @Test public void cutUtf8IsStillUtf8() {
        byte[] b = "aé".getBytes(StandardCharsets.UTF_8);
        TextDecoder.Result r = TextDecoder.decode(Arrays.copyOf(b, 2), null, true);
        assertEquals("UTF-8", r.charset);
        assertEquals("a", r.text);
    }

    @Test public void binaryIsDetected() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13, 'I', 'H', 'D', 'R', 0, 0, 1, 0};
        assertTrue(TextDecoder.decode(png, null, false).binary);
        assertFalse(TextDecoder.decode("hi".getBytes(), null, false).binary);
    }

    @Test public void names() {
        assertTrue(Renderer.looksLikeMarkdown("README.MD", null));
        assertTrue(Renderer.looksLikeMarkdown("a.markdown", "application/octet-stream"));
        assertFalse(Renderer.looksLikeMarkdown("a.txt", "text/markdown"));
        assertTrue(Renderer.looksLikeMarkdown("msf:33", "text/markdown"));
        assertFalse(Renderer.looksLikeMarkdown(null, null));
    }

    @Test public void paths() {
        assertTrue(Paths.isInside("primary:", "primary:a/b.md"));
        assertTrue(Paths.isInside("primary:a", "primary:a/b.md"));
        assertFalse(Paths.isInside("primary:a", "primary:ab/c.md"));
        assertArrayEquals(new String[]{"primary", "a", "b c.md"}, Paths.toSegments("primary:a/b c.md"));
        assertNull(Paths.toSegments("33"));
        assertEquals("primary:a/b.md", Paths.toDocId(Arrays.asList("primary", "a", "b.md")));
        assertNull(Paths.toDocId(Arrays.asList("primary", "..", "b.md")));
        assertNull(Paths.toDocId(Arrays.asList("primary", "x/../y")));
    }
}
