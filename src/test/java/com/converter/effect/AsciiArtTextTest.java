package com.converter.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;
import java.util.Random;
import org.junit.jupiter.api.Test;

class AsciiArtTextTest {

    private static void fill(BufferedImage img, int x0, int y0, int x1, int y1, int gray) {
        int rgb = (gray << 16) | (gray << 8) | gray;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                img.setRGB(x, y, rgb);
            }
        }
    }

    @Test
    void noFrameYieldsEmptyString() {
        assertEquals("", new AsciiArtEffect(new FakeGlyphSet()).lastText());
    }

    @Test
    void handComputedGridWithPartialCells() {
        // 10x7 image, 4x4 cells -> 3 cols x 2 rows. Glyph by L: 255..192 blank, 191..128 dot,
        // 127..64 dash, 63..0 hash.
        BufferedImage img = new BufferedImage(10, 7, BufferedImage.TYPE_INT_RGB);
        fill(img, 0, 0, 10, 7, 255);
        fill(img, 0, 0, 4, 4, 0);      // cell (0,0): black -> '#'
        fill(img, 8, 0, 10, 4, 160);   // cell (2,0): partial 2 wide -> '.'
        fill(img, 0, 4, 4, 7, 100);    // cell (0,1): partial 3 tall -> '-'
        fill(img, 4, 4, 8, 7, 160);    // cell (1,1) -> '.'
        fill(img, 8, 4, 10, 7, 0);     // cell (2,1): partial corner -> '#'
        AsciiArtEffect e = new AsciiArtEffect(new FakeGlyphSet());
        e.apply(img);
        assertEquals("# .\n-.#", e.lastText());
    }

    @Test
    void blankRowsAndTrailingSpacesArePreserved() {
        BufferedImage img = new BufferedImage(10, 7, BufferedImage.TYPE_INT_RGB);
        fill(img, 0, 0, 10, 7, 255);
        AsciiArtEffect e = new AsciiArtEffect(new FakeGlyphSet());
        e.apply(img);
        assertEquals("   \n   ", e.lastText());
    }

    @Test
    void textMatchesRenderedGlyphMasksCellByCell() {
        Random rnd = new Random(7);
        int w = 23;
        int h = 18; // 6 cols x 5 rows, partial right/bottom
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, rnd.nextInt(0x1000000));
            }
        }
        AsciiArtEffect e = new AsciiArtEffect(new FakeGlyphSet());
        BufferedImage out = e.apply(img);
        String[] lines = e.lastText().split("\n", -1);
        assertEquals(5, lines.length);
        for (int cy = 0; cy < 5; cy++) {
            assertEquals(6, lines[cy].length());
            for (int cx = 0; cx < 6; cx++) {
                int k = new String(FakeGlyphSet.CHARS).indexOf(lines[cy].charAt(cx));
                for (int y = cy * 4; y < Math.min(cy * 4 + 4, h); y++) {
                    for (int x = cx * 4; x < Math.min(cx * 4 + 4, w); x++) {
                        boolean ink = FakeGlyphSet.MASKS[k][(y - cy * 4) * 4 + (x - cx * 4)];
                        assertEquals(ink ? 0x000000 : 0xFFFFFF, out.getRGB(x, y) & 0xFFFFFF,
                                "pixel " + x + "," + y);
                    }
                }
            }
        }
    }

    @Test
    void latestFrameWins() {
        AsciiArtEffect e = new AsciiArtEffect(new FakeGlyphSet());
        BufferedImage a = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        fill(a, 0, 0, 4, 4, 0);
        BufferedImage b = new BufferedImage(8, 4, BufferedImage.TYPE_INT_RGB);
        fill(b, 0, 0, 8, 4, 255);
        e.apply(a);
        assertEquals("#", e.lastText());
        e.apply(b);
        assertEquals("  ", e.lastText());
    }
}
