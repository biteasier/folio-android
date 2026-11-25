package com.biteasier.folio;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Turns bytes into text: byte order mark first, then strict UTF-8, then a guess. */
final class TextDecoder {
    static final class Result {
        final String text;
        final String charset;
        final boolean binary;

        Result(String text, String charset, boolean binary) {
            this.text = text;
            this.charset = charset;
            this.binary = binary;
        }
    }

    private TextDecoder() {}

    /** @param forced charset name chosen by the reader, or null to detect it
     *  @param truncated true if the bytes were cut, so the last character may be incomplete */
    static Result decode(byte[] b, String forced, boolean truncated) {
        int n = b.length;
        if (forced != null) return make(b, 0, forced, false);
        if (starts(b, 0xEF, 0xBB, 0xBF)) return make(b, 3, "UTF-8", false);
        if (starts(b, 0xFF, 0xFE, 0, 0)) return make(b, 4, "UTF-32LE", false);
        if (starts(b, 0, 0, 0xFE, 0xFF)) return make(b, 4, "UTF-32BE", false);
        if (starts(b, 0xFF, 0xFE)) return make(b, 2, "UTF-16LE", false);
        if (starts(b, 0xFE, 0xFF)) return make(b, 2, "UTF-16BE", false);

        int sample = Math.min(n, 8192), evenZero = 0, oddZero = 0;
        for (int i = 0; i < sample; i++) {
            if (b[i] == 0) {
                if ((i & 1) == 0) evenZero++;
                else oddZero++;
            }
        }
        if (evenZero + oddZero > 0) {
            int half = Math.max(1, sample / 2);
            if (n % 2 == 0 || truncated) {
                if (oddZero > half * 0.4 && evenZero < half * 0.05) return make(b, 0, "UTF-16LE", false);
                if (evenZero > half * 0.4 && oddZero < half * 0.05) return make(b, 0, "UTF-16BE", false);
            }
            return make(b, 0, "windows-1252", true);
        }
        String utf8 = strictUtf8(b, truncated);
        if (utf8 != null) return new Result(utf8, "UTF-8", false);
        return make(b, 0, "windows-1252", false);
    }

    private static Result make(byte[] b, int skip, String charset, boolean binary) {
        if (skip == 0 && charset.equals("UTF-8") && starts(b, 0xEF, 0xBB, 0xBF)) skip = 3;
        if (skip == 0 && (charset.equals("UTF-16LE") && starts(b, 0xFF, 0xFE)
                || charset.equals("UTF-16BE") && starts(b, 0xFE, 0xFF))) skip = 2;
        return new Result(new String(b, skip, b.length - skip, Charset.forName(charset)), charset, binary);
    }

    private static String strictUtf8(byte[] b, boolean truncated) {
        CharsetDecoder d = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        CharBuffer out = CharBuffer.allocate(b.length + 1);
        // endOfInput=false leaves an incomplete final character undecoded instead of failing.
        CoderResult r = d.decode(ByteBuffer.wrap(b), out, !truncated);
        if (r.isError()) return null;
        return out.flip().toString();
    }

    private static boolean starts(byte[] b, int... sig) {
        if (b.length < sig.length) return false;
        for (int i = 0; i < sig.length; i++) if ((b[i] & 0xFF) != sig[i]) return false;
        return true;
    }
}
