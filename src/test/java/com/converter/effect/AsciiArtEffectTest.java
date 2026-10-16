package com.converter.effect;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AsciiArtEffectTest {

    private static final int WHITE = 0xFFFFFF;
    private static final int BLACK = 0x000000;

    private final AsciiArtEffect fx = new AsciiArtEffect(new FakeGlyphSet());

    private static BufferedImage gray(int w, int h, int type, int v) {
        BufferedImage img = new BufferedImage(w, h, type);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        }
        return img;
    }

    /** Expected 4x4 cell pixels of glyph k. */
    private static int[] glyph(int k) {
        int[] g = new int[16];
        for (int i = 0; i < 16; i++) {
            g[i] = FakeGlyphSet.MASKS[k][i] ? BLACK : WHITE;
        }
        return g;
    }

    private static int[] cell(BufferedImage out, int cx, int cy) {
        int[] px = out.getRGB(cx * 4, cy * 4, 4, 4, null, 0, 4);
        for (int i = 0; i < px.length; i++) {
            px[i] &= 0xFFFFFF;
        }
        return px;
    }

    private int glyphFor(int lum) {
        BufferedImage out = fx.apply(gray(4, 4, BufferedImage.TYPE_INT_RGB, lum));
        int[] px = cell(out, 0, 0);
        for (int k = 0; k < 4; k++) {
            if (Arrays.equals(px, glyph(k))) {
                return k;
            }
        }
        throw new AssertionError("no glyph matches for L=" + lum);
    }

    @Test
    void brightnessToGlyphHandComputed() {
        // N=4, k = ((255-L)*4)/256
        assertEquals(0, glyphFor(255)); // 0
        assertEquals(0, glyphFor(192)); // 63*4=252 -> 0
        assertEquals(1, glyphFor(191)); // 64*4=256 -> 1
        assertEquals(1, glyphFor(128)); // 127*4=508 -> 1
        assertEquals(2, glyphFor(127)); // 128*4=512 -> 2
        assertEquals(2, glyphFor(64));  // 191*4=764 -> 2
        assertEquals(3, glyphFor(63));  // 192*4=768 -> 3
        assertEquals(3, glyphFor(0));   // 255*4=1020 -> 3 = N-1
    }

    @Test
    void cellsAreChosenIndependently() {
        BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        int[] lv = {255, 128, 100, 0}; // cells (0,0) (1,0) (0,1) (1,1)
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int v = lv[(y / 4) * 2 + x / 4];
                img.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        }
        BufferedImage out = fx.apply(img);
        assertArrayEquals(glyph(0), cell(out, 0, 0));
        assertArrayEquals(glyph(1), cell(out, 1, 0));
        assertArrayEquals(glyph(2), cell(out, 0, 1));
        assertArrayEquals(glyph(3), cell(out, 1, 1));
    }

    @Test
    void partialCellAverageUsesActualPixelCount() {
        // 5x3 image, cells 4x4: cell A = x0..3 (12 px), cell B = x4 (3 px).
        BufferedImage img = new BufferedImage(5, 3, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 4; x++) {
                int v = x < 2 ? 255 : 0; // 6 white + 6 black: (1530+6)/12 = 128 -> k=1
                img.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        }
        // cell B: 255, 255, 0 -> (510+1)/3 = 170 -> (85*4)/256 = 1
        img.setRGB(4, 0, 0xFFFFFF);
        img.setRGB(4, 1, 0xFFFFFF);
        img.setRGB(4, 2, 0x000000);
        BufferedImage out = fx.apply(img);
        // A: dot glyph (mask (0,0) black). A wrong denominator 16 would give k=2 (line on row 1).
        assertEquals(BLACK, out.getRGB(0, 0) & 0xFFFFFF);
        assertEquals(WHITE, out.getRGB(1, 1) & 0xFFFFFF);
        assertEquals(WHITE, out.getRGB(2, 1) & 0xFFFFFF);
        // B: dot glyph, mask (0,0) lands at x=4,y=0
        assertEquals(BLACK, out.getRGB(4, 0) & 0xFFFFFF);
        assertEquals(WHITE, out.getRGB(4, 1) & 0xFFFFFF);
        assertEquals(WHITE, out.getRGB(4, 2) & 0xFFFFFF);
    }

    @Test
    void partialCellsCutMaskAndStayInBounds() {
        // L=100 -> k=2 (line on mask row 1) everywhere; 10x7 with 4x4 cells.
        BufferedImage out = fx.apply(gray(10, 7, BufferedImage.TYPE_INT_RGB, 100));
        assertEquals(10, out.getWidth());
        assertEquals(7, out.getHeight());
        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < 10; x++) {
                boolean line = y == 1 || y == 5;
                assertEquals(line ? BLACK : WHITE, out.getRGB(x, y) & 0xFFFFFF,
                        "at " + x + "," + y);
            }
        }
        BufferedImage dark = fx.apply(gray(10, 7, BufferedImage.TYPE_INT_RGB, 0));
        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < 10; x++) {
                assertEquals(BLACK, dark.getRGB(x, y) & 0xFFFFFF);
            }
        }
        BufferedImage light = fx.apply(gray(10, 7, BufferedImage.TYPE_INT_RGB, 255));
        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < 10; x++) {
                assertEquals(WHITE, light.getRGB(x, y) & 0xFFFFFF);
            }
        }
    }

    @Test
    void imageSmallerThanOneCell() {
        BufferedImage out = fx.apply(gray(2, 1, BufferedImage.TYPE_INT_RGB, 0));
        assertEquals(BLACK, out.getRGB(0, 0) & 0xFFFFFF);
        assertEquals(BLACK, out.getRGB(1, 0) & 0xFFFFFF);
    }

    @Test
    void outputHasOnlyTwoColorsAndCorrectType() {
        BufferedImage img = new BufferedImage(13, 9, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 13; x++) {
                img.setRGB(x, y, (x * 19) << 16 | (y * 28) << 8 | ((x * y) & 0xFF));
            }
        }
        BufferedImage out = fx.apply(img);
        assertEquals(BufferedImage.TYPE_INT_RGB, out.getType());
        assertNotSame(img, out);
        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 13; x++) {
                int p = out.getRGB(x, y) & 0xFFFFFF;
                assertTrue(p == WHITE || p == BLACK);
            }
        }
    }

    @Test
    void inputIsNotModified() {
        BufferedImage img = gray(9, 6, BufferedImage.TYPE_INT_RGB, 77);
        int[] before = img.getRGB(0, 0, 9, 6, null, 0, 9);
        fx.apply(img);
        assertArrayEquals(before, img.getRGB(0, 0, 9, 6, null, 0, 9));
    }

    @Test
    void nonRgbInputTypesUseFallbackWithSameResult() {
        BufferedImage ref = fx.apply(gray(10, 7, BufferedImage.TYPE_INT_RGB, 100));
        int[] expected = ref.getRGB(0, 0, 10, 7, null, 0, 10);
        int[] types = {BufferedImage.TYPE_INT_ARGB, BufferedImage.TYPE_3BYTE_BGR,
            BufferedImage.TYPE_INT_BGR, BufferedImage.TYPE_BYTE_GRAY};
        for (int t : types) {
            BufferedImage out = fx.apply(gray(10, 7, t, 100));
            assertEquals(BufferedImage.TYPE_INT_RGB, out.getType());
            assertArrayEquals(expected, out.getRGB(0, 0, 10, 7, null, 0, 10), "type " + t);
        }
    }

    @Test
    void subimageInputUsesFallback() {
        BufferedImage big = gray(20, 20, BufferedImage.TYPE_INT_RGB, 255);
        BufferedImage sub = big.getSubimage(3, 3, 8, 8);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                sub.setRGB(x, y, 0);
            }
        }
        BufferedImage out = fx.apply(sub);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                assertEquals(BLACK, out.getRGB(x, y) & 0xFFFFFF);
            }
        }
    }

    @Test
    void constructorValidation() {
        assertThrows(IllegalArgumentException.class, () -> new AsciiArtEffect(null));
        assertThrows(IllegalArgumentException.class,
                () -> new AsciiArtEffect(new BadGlyphs(0, 4, 1, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> new AsciiArtEffect(new BadGlyphs(4, 4, 0, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> new AsciiArtEffect(new BadGlyphs(4, 4, 1, 5)));
    }

    /** Invalid GlyphSet: bad cell, no glyphs, or wrong mask length. */
    private record BadGlyphs(int w, int h, int n, int maskLen) implements GlyphSet {
        @Override public int cellWidth() { return w; }
        @Override public int cellHeight() { return h; }
        @Override public int size() { return n; }
        @Override public char charAt(int index) { return ' '; }
        @Override public boolean[] mask(int index) { return new boolean[maskLen]; }
    }

    @Test
    void defaultConstructorUses8x16Cells() {
        AsciiArtEffect d = new AsciiArtEffect();
        assertEquals(8, d.cellWidth());
        assertEquals(16, d.cellHeight());
    }
}
