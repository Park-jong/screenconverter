package com.converter.effect;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

class MosaicEffectTest {

    private static BufferedImage make(int w, int h, BiFunction<Integer, Integer, Integer> f) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, f.apply(x, y));
            }
        }
        return img;
    }

    private static int rgb(int r, int g, int b) {
        return (r << 16) | (g << 8) | b;
    }

    @Test
    void fourByFourBlockTwoHandComputed() {
        // Top-left block: R 0,4,8,12 -> sum 24 / 4 = 6.
        // G 1,1,1,2 -> sum 5 -> (5+2)/4 = 1 (1.25 rounds to 1).
        // B 0,0,0,2 -> sum 2 -> (2+2)/4 = 1 (0.5 rounds up).
        int[][] r = {{0, 4, 10, 10}, {8, 12, 10, 10}, {20, 20, 0, 0}, {20, 20, 0, 255}};
        int[][] g = {{1, 1, 0, 0}, {1, 2, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}};
        int[][] b = {{0, 0, 7, 7}, {0, 2, 7, 7}, {0, 0, 0, 0}, {0, 0, 0, 0}};
        BufferedImage src = make(4, 4, (x, y) -> rgb(r[y][x], g[y][x], b[y][x]));
        BufferedImage out = new MosaicEffect(2).apply(src);
        int tl = rgb(6, 1, 1);
        int tr = rgb(10, 0, 7);
        int bl = rgb(20, 0, 0);
        int br = rgb(64, 0, 0); // (255 + 2) / 4 = 64 (63.75 rounds to 64)
        int[][] exp = {{tl, tl, tr, tr}, {tl, tl, tr, tr}, {bl, bl, br, br}, {bl, bl, br, br}};
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                assertEquals(exp[y][x], out.getRGB(x, y) & 0xFFFFFF, "pixel " + x + "," + y);
            }
        }
    }

    @Test
    void minimumBlockSizeTwoOnTwoByTwoIsWholeAverage() {
        BufferedImage src = make(2, 2, (x, y) -> rgb(x * 100, y * 100, 50));
        BufferedImage out = new MosaicEffect(2).apply(src);
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) {
                assertEquals(rgb(50, 50, 50), out.getRGB(x, y) & 0xFFFFFF);
            }
        }
    }

    @Test
    void blockLargerThanImageGivesSingleAverage() {
        BufferedImage src = make(3, 2, (x, y) -> rgb(x + 3 * y, 0, 0)); // 0..5 -> sum 15, count 6
        BufferedImage out = new MosaicEffect(50).apply(src);
        int expected = rgb((15 + 3) / 6, 0, 0); // 3
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 3; x++) {
                assertEquals(expected, out.getRGB(x, y) & 0xFFFFFF);
            }
        }
    }

    @Test
    void nonMultipleSizePartialBlocksUseActualPixelCount() {
        // 10x7, block 4: columns {0-3,4-7,8-9}, rows {0-3,4-6}; red = x + 10*y.
        BufferedImage src = make(10, 7, (x, y) -> rgb(x + 10 * y, 0, 0));
        BufferedImage out = new MosaicEffect(4).apply(src);
        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < 10; x++) {
                int x0 = x / 4 * 4;
                int y0 = y / 4 * 4;
                int x1 = Math.min(x0 + 4, 10);
                int y1 = Math.min(y0 + 4, 7);
                long sum = 0;
                for (int yy = y0; yy < y1; yy++) {
                    for (int xx = x0; xx < x1; xx++) {
                        sum += xx + 10 * yy;
                    }
                }
                long count = (long) (x1 - x0) * (y1 - y0);
                assertEquals((int) ((sum + count / 2) / count), (out.getRGB(x, y) >> 16) & 0xFF,
                        "pixel " + x + "," + y);
            }
        }
        // Hand value, 2x3 corner block (x 8-9, y 4-6): 48,49,58,59,68,69 -> 351/6 = 58.5 -> 59.
        assertEquals(59, (out.getRGB(9, 6) >> 16) & 0xFF);
    }

    @Test
    void solidColorUnchanged() {
        BufferedImage src = make(13, 9, (x, y) -> rgb(17, 99, 201));
        BufferedImage out = new MosaicEffect(10).apply(src);
        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 13; x++) {
                assertEquals(rgb(17, 99, 201), out.getRGB(x, y) & 0xFFFFFF);
            }
        }
    }

    @Test
    void sizeAndTypeKeptAndInputUnchanged() {
        BufferedImage src = make(11, 5, (x, y) -> rgb(x * 20, y * 40, (x * y) % 256));
        int[] before = src.getRGB(0, 0, 11, 5, null, 0, 11);
        BufferedImage out = new MosaicEffect(4).apply(src);
        assertNotSame(src, out);
        assertEquals(11, out.getWidth());
        assertEquals(5, out.getHeight());
        assertEquals(BufferedImage.TYPE_INT_RGB, out.getType());
        assertArrayEquals(before, src.getRGB(0, 0, 11, 5, null, 0, 11));
    }

    @Test
    void nonRgbInputTypeIsAccepted() {
        BufferedImage src = new BufferedImage(4, 4, BufferedImage.TYPE_3BYTE_BGR);
        src.setRGB(0, 0, rgb(200, 100, 50));
        BufferedImage out = new MosaicEffect(4).apply(src);
        assertEquals(BufferedImage.TYPE_INT_RGB, out.getType());
        assertEquals(rgb((200 + 8) / 16, (100 + 8) / 16, (50 + 8) / 16),
                out.getRGB(3, 3) & 0xFFFFFF);
    }

    @Test
    void identityEffectCopiesExactly() {
        BufferedImage src = make(7, 6, (x, y) -> rgb(x * 30, y * 40, x + y));
        BufferedImage out = new IdentityEffect().apply(src);
        assertNotSame(src, out);
        assertEquals(BufferedImage.TYPE_INT_RGB, out.getType());
        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 7; x++) {
                assertEquals(src.getRGB(x, y), out.getRGB(x, y));
            }
        }
    }

    @Test
    void invalidBlockSizeRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MosaicEffect(1));
        assertThrows(IllegalArgumentException.class, () -> new MosaicEffect(0));
        assertThrows(IllegalArgumentException.class, () -> new MosaicEffect(-5));
        assertEquals(10, new MosaicEffect().blockSize());
    }
}
