package com.converter.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

class TextFitTest {

    /** Hangul 12px, everything else 7px. */
    private static final ToIntFunction<String> FAKE = s -> s.codePoints()
            .map(c -> c >= 0xAC00 && c <= 0xD7A3 ? 12 : 7).sum();

    private static ToIntFunction<String> real() {
        Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        FontMetrics fm = g.getFontMetrics(new Font("Malgun Gothic", Font.PLAIN, 12));
        return fm::stringWidth;
    }

    private static void check(String text, int max, ToIntFunction<String> w) {
        String r = TextFit.fit(text, max, w);
        assertTrue(w.applyAsInt(r) <= max, r + " " + w.applyAsInt(r));
        assertTrue(r.endsWith("…"), r);
        assertTrue(text.startsWith(r.substring(0, r.length() - 1)), r);
    }

    @Test
    void fittingTextIsUnchanged() {
        assertEquals("저장됨 (abc)", TextFit.fit("저장됨 (abc)", 168, FAKE));
        assertEquals("abcd", TextFit.fit("abcd", 28, FAKE));
    }

    @Test
    void longTextsFitWithBothMeasurers() {
        String[] texts = {"a".repeat(60), "한".repeat(40), "저장됨 (" + "가나다abc".repeat(10) + ")",
            "x😀y".repeat(20), "e\u0301".repeat(40)};
        for (ToIntFunction<String> w : java.util.List.of(FAKE, real())) {
            for (String t : texts) {
                check(t, 168, w);
            }
        }
    }

    @Test
    void exactValuesWithFakeWidths() {
        // 14 Hangul = 168 -> 13 Hangul (156) + "…" (7) = 163; 14 would be 175.
        assertEquals("한".repeat(13) + "…", TextFit.fit("한".repeat(20), 168, FAKE));
    }

    @Test
    void neverSplitsSurrogatesOrCombiningMarks() {
        ToIntFunction<String> w = s -> s.length() * 7;
        for (int max = 7; max < 120; max++) {
            String r = TextFit.fit("a😀b😀c\u0301d".repeat(5), max, w);
            for (int i = 0; i < r.length(); i++) {
                assertTrue(!Character.isHighSurrogate(r.charAt(i))
                        || (i + 1 < r.length() && Character.isLowSurrogate(r.charAt(i + 1))), r);
            }
            assertTrue(!r.startsWith("\u0301") && !r.contains("c…"), r);
        }
    }

    @Test
    void emptyNullAndTinyWidth() {
        assertEquals("", TextFit.fit(null, 100, FAKE));
        assertEquals("", TextFit.fit("", 100, FAKE));
        assertEquals("…", TextFit.fit("abcdef", 0, FAKE));
        assertEquals("…", TextFit.fit("abcdef", 7, FAKE));
        assertEquals("a…", TextFit.fit("abcdef", 14, FAKE));
    }
}
