package com.converter.effect;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PixelDotEffectTest {

    private static final int G = PixelDotEffect.GRID_COLOR;

    private static BufferedImage random(int w, int h, long seed) {
        Random rnd = new Random(seed);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, rnd.nextInt(0x1000000));
            }
        }
        return img;
    }

    private static int[] pixels(BufferedImage img) {
        return img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
    }

    private static int px(BufferedImage img, int x, int y) {
        return img.getRGB(x, y) & 0xFFFFFF;
    }

    private static boolean isGrid(int x, int y, int bs) {
        return x % bs == 0 || y % bs == 0;
    }

    private static int rgb(int r, int g, int b) {
        return (r << 16) | (g << 8) | b;
    }

    /** 4x4 image of four 2x2 blocks averaging (200,0,0) (0,254,0) (250,250,100) (60,60,60). */
    private static BufferedImage handImage() {
        int[][] c = {
            {rgb(190, 0, 0), rgb(210, 0, 0), rgb(0, 250, 0), rgb(0, 255, 0)},
            {rgb(190, 0, 0), rgb(210, 0, 0), rgb(0, 255, 0), rgb(0, 255, 0)},
            {rgb(250, 250, 102), rgb(250, 250, 98), rgb(58, 60, 60), rgb(62, 60, 60)},
            {rgb(250, 250, 100), rgb(250, 250, 100), rgb(58, 60, 60), rgb(62, 60, 60)}};
        BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                img.setRGB(x, y, c[y][x]);
            }
        }
        return img;
    }

    @Test
    void noPaletteNoGridEqualsMosaic() {
        int[][] sizes = {{37, 23}, {10, 7}, {64, 64}, {5, 3}, {1, 1}, {100, 9}};
        int[] blocks = {2, 3, 4, 8, 10, 16, 200};
        for (int[] s : sizes) {
            BufferedImage src = random(s[0], s[1], s[0] * 31L + s[1]);
            for (int bs : blocks) {
                BufferedImage a = new PixelDotEffect(bs, Palette.NONE, false).apply(src);
                BufferedImage b = new MosaicEffect(bs).apply(src);
                assertArrayEquals(pixels(b), pixels(a), s[0] + "x" + s[1] + " bs=" + bs);
            }
        }
    }

    @Test
    void handComputedEgaBlockTwo() {
        BufferedImage out = new PixelDotEffect(2, Palette.EGA_16, false).apply(handImage());
        // averages (200,0,0) -> AA0000; (0,254,0) -> 00AA00 (d=7056 vs 55FF55 d=14451);
        // (250,250,100) -> FFFF55 (d=2075); (60,60,60) -> 555555 (d=75)
        int[][] exp = {
            {0xAA0000, 0xAA0000, 0x00AA00, 0x00AA00},
            {0xAA0000, 0xAA0000, 0x00AA00, 0x00AA00},
            {0xFFFF55, 0xFFFF55, 0x555555, 0x555555},
            {0xFFFF55, 0xFFFF55, 0x555555, 0x555555}};
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                assertEquals(exp[y][x], px(out, x, y), "pixel " + x + "," + y);
            }
        }
    }

    @Test
    void handComputedEgaBlockFourWithGrid() {
        BufferedImage out = new PixelDotEffect(4, Palette.EGA_16, true).apply(handImage());
        // whole-image average (128,141,40) -> AA5500 (d=1764+3136+1600=6500; 555555 is 7010)
        int f = 0xAA5500;
        int[][] exp = {{G, G, G, G}, {G, f, f, f}, {G, f, f, f}, {G, f, f, f}};
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                assertEquals(exp[y][x], px(out, x, y), "pixel " + x + "," + y);
            }
        }
    }

    @Test
    void handComputedGameBoyBlockTwo() {
        // Blocks: black -> 0F380F, white -> 9BBC0F, grey (127,129,127)x/(129,127,129) avg
        // 128 grey -> 306230, red -> 8BAC0F (see PaletteTest for distances).
        int[][] c = {
            {0x000000, 0x000000, 0xFFFFFF, 0xFFFFFF},
            {0x000000, 0x000000, 0xFFFFFF, 0xFFFFFF},
            {rgb(127, 129, 127), rgb(129, 127, 129), 0xFF0000, 0xFF0000},
            {rgb(129, 127, 129), rgb(127, 129, 127), 0xFF0000, 0xFF0000}};
        BufferedImage src = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                src.setRGB(x, y, c[y][x]);
            }
        }
        BufferedImage out = new PixelDotEffect(2, Palette.GAMEBOY_4, false).apply(src);
        int[][] exp = {
            {0x0F380F, 0x0F380F, 0x9BBC0F, 0x9BBC0F},
            {0x0F380F, 0x0F380F, 0x9BBC0F, 0x9BBC0F},
            {0x306230, 0x306230, 0x8BAC0F, 0x8BAC0F},
            {0x306230, 0x306230, 0x8BAC0F, 0x8BAC0F}};
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                assertEquals(exp[y][x], px(out, x, y), "pixel " + x + "," + y);
            }
        }
    }

    @Test
    void gridOnlyDiffersAtGridPositions() {
        BufferedImage src = random(37, 23, 7);
        for (Palette p : new Palette[] {Palette.NONE, Palette.EGA_16, Palette.GAMEBOY_4}) {
            for (int bs : new int[] {4, 5, 8, 10}) {
                BufferedImage off = new PixelDotEffect(bs, p, false).apply(src);
                BufferedImage on = new PixelDotEffect(bs, p, true).apply(src);
                for (int y = 0; y < 23; y++) {
                    for (int x = 0; x < 37; x++) {
                        if (isGrid(x, y, bs)) {
                            assertEquals(G, px(on, x, y), "grid " + x + "," + y + " bs=" + bs);
                        } else {
                            assertEquals(px(off, x, y), px(on, x, y), "non-grid " + x + "," + y);
                        }
                    }
                }
            }
        }
    }

    @Test
    void gridOnPartialBlocks() {
        // 10x7, block 4: columns 0,4,8 and rows 0,4 are grid; the last block column is 2 wide,
        // the last block row is 3 tall.
        BufferedImage src = random(10, 7, 3);
        BufferedImage out = new PixelDotEffect(4, Palette.NONE, true).apply(src);
        assertEquals(10, out.getWidth());
        assertEquals(7, out.getHeight());
        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < 10; x++) {
                boolean grid = x == 0 || x == 4 || x == 8 || y == 0 || y == 4;
                if (grid) {
                    assertEquals(G, px(out, x, y), x + "," + y);
                }
            }
        }
        // Non-grid pixels of the partial bottom-right block (x 9, y 5..6) hold its 2x3 average
        // (count 6, rounding (sum + 3) / 6).
        long sr = 0;
        long sg = 0;
        long sb = 0;
        for (int y = 4; y < 7; y++) {
            for (int x = 8; x < 10; x++) {
                int p = px(src, x, y);
                sr += (p >> 16) & 0xFF;
                sg += (p >> 8) & 0xFF;
                sb += p & 0xFF;
            }
        }
        int exp = rgb((int) ((sr + 3) / 6), (int) ((sg + 3) / 6), (int) ((sb + 3) / 6));
        assertEquals(exp, px(out, 9, 5));
        assertEquals(exp, px(out, 9, 6));
    }

    @Test
    void paletteOutputsOnlyPaletteColoursOutsideGrid() {
        BufferedImage src = random(61, 29, 11);
        for (Palette p : new Palette[] {Palette.EGA_16, Palette.GAMEBOY_4}) {
            for (boolean grid : new boolean[] {false, true}) {
                BufferedImage out = new PixelDotEffect(8, p, grid).apply(src);
                for (int y = 0; y < 29; y++) {
                    for (int x = 0; x < 61; x++) {
                        if (grid && isGrid(x, y, 8)) {
                            continue;
                        }
                        assertTrue(p.contains(px(out, x, y)), x + "," + y);
                    }
                }
            }
        }
    }

    @Test
    void blockInteriorIsUniform() {
        BufferedImage src = random(45, 30, 5);
        int bs = 8;
        BufferedImage out = new PixelDotEffect(bs, Palette.EGA_16, true).apply(src);
        for (int by = 0; by < 30; by += bs) {
            for (int bx = 0; bx < 45; bx += bs) {
                if (bx + 1 >= 45 || by + 1 >= 30) {
                    continue;
                }
                int ref = px(out, bx + 1, by + 1);
                for (int y = by + 1; y < Math.min(by + bs, 30); y++) {
                    for (int x = bx + 1; x < Math.min(bx + bs, 45); x++) {
                        assertEquals(ref, px(out, x, y), x + "," + y);
                    }
                }
            }
        }
    }

    @Test
    void inputUnchangedSizeAndType() {
        BufferedImage src = random(33, 17, 9);
        int[] before = pixels(src);
        BufferedImage out = new PixelDotEffect(8, Palette.EGA_16, true).apply(src);
        assertNotSame(src, out);
        assertArrayEquals(before, pixels(src));
        assertEquals(33, out.getWidth());
        assertEquals(17, out.getHeight());
        assertEquals(BufferedImage.TYPE_INT_RGB, out.getType());
    }

    @Test
    void otherInputTypesGiveSameResult() {
        BufferedImage a = random(21, 13, 4);
        BufferedImage b = new BufferedImage(21, 13, BufferedImage.TYPE_3BYTE_BGR);
        b.getGraphics().drawImage(a, 0, 0, null);
        PixelDotEffect fx = new PixelDotEffect(4, Palette.GAMEBOY_4, true);
        assertArrayEquals(pixels(fx.apply(a)), pixels(fx.apply(b)));
    }

    @Test
    void constructorValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> new PixelDotEffect(1, Palette.NONE, false));
        assertThrows(IllegalArgumentException.class,
                () -> new PixelDotEffect(0, Palette.NONE, false));
        assertThrows(IllegalArgumentException.class,
                () -> new PixelDotEffect(3, Palette.EGA_16, true));
        assertThrows(IllegalArgumentException.class,
                () -> new PixelDotEffect(8, null, false));
        new PixelDotEffect(2, Palette.NONE, false);
        new PixelDotEffect(4, Palette.NONE, true);
        assertEquals(8, PixelDotEffect.DEFAULT_BLOCK_SIZE);
    }
}
