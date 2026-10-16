package com.converter.effect;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class OptionStateTest {

    private static BufferedImage randomImage(int w, int h, long seed) {
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

    /** Left half black, right half white. */
    private static BufferedImage halves(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, x < w / 2 ? 0x000000 : 0xFFFFFF);
            }
        }
        return img;
    }

    @Test
    void optionDefinitionsPerEffect() {
        EffectRegistry r = EffectRegistry.defaults();
        assertTrue(r.get(1).get().options().isEmpty());
        assertTrue(r.get(3).get().options().isEmpty());

        EffectOption mosaic = r.get(2).get().options().get(0);
        assertEquals("블록 크기", mosaic.name());
        assertEquals(2, mosaic.min());
        assertEquals(64, mosaic.max());
        assertEquals(10, mosaic.defaultValue());

        EffectOption thr = r.get(4).get().options().get(0);
        assertEquals("임계값", thr.name());
        assertEquals(0, thr.min());
        assertEquals(255, thr.max());
        assertEquals(128, thr.defaultValue());
        assertEquals(8, thr.step());

        for (int n : new int[] {5, 6}) {
            EffectOption dot = r.get(n).get().options().get(0);
            assertEquals(1, r.get(n).get().options().size());
            assertEquals(2, dot.min());
            assertEquals(64, dot.max());
            assertEquals(8, dot.defaultValue());
        }
        for (int n : new int[] {7, 8}) {
            EffectOption dot = r.get(n).get().options().get(0);
            assertEquals(1, r.get(n).get().options().size());
            assertEquals(PixelDotEffect.MIN_GRID_BLOCK_SIZE, dot.min());
            assertEquals(4, dot.min());
            assertEquals(64, dot.max());
            assertEquals(8, dot.defaultValue());
        }

        List<EffectOption> ascii = r.get(9).get().options();
        assertEquals(2, ascii.size());
        assertEquals(EffectOption.Kind.CHOICE, ascii.get(0).kind());
        assertEquals(List.of("8x16", "6x12", "4x8"), ascii.get(0).labels());
        assertEquals(0, ascii.get(0).defaultValue());
        assertEquals(EffectOption.Kind.BOOL, ascii.get(1).kind());
        assertEquals(0, ascii.get(1).defaultValue());
        assertEquals(EffectOption.Target.COPY_TRIM_TRAILING, ascii.get(1).target());
    }

    @Test
    void defaultOptionsReproduceTheRegistryDefaultEffects() {
        EffectRegistry r = EffectRegistry.defaults();
        OptionState s = new OptionState(r);
        BufferedImage in = randomImage(37, 23, 5);
        for (int n = 1; n <= r.size(); n++) {
            Effect made = s.create(n);
            Effect def = r.get(n).get().effect();
            assertEquals(def.getClass(), made.getClass(), "effect " + n);
            assertArrayEquals(pixels(def.apply(in)), pixels(made.apply(in)), "effect " + n);
        }
    }

    @Test
    void creatingBuildsAFreshInstanceEachTime() {
        OptionState s = new OptionState(EffectRegistry.defaults());
        assertNotSame(s.create(2), s.create(2));
        assertNotSame(s.create(9), s.create(9));
    }

    @Test
    void mosaicBlockSizeChangesOutputByHand() {
        OptionState s = new OptionState(EffectRegistry.defaults());
        BufferedImage in = halves(16, 16);
        s.set(2, 0, 4);
        BufferedImage small = s.create(2).apply(in);
        assertEquals(0x000000, small.getRGB(0, 0) & 0xFFFFFF);
        assertEquals(0x000000, small.getRGB(7, 15) & 0xFFFFFF);
        assertEquals(0xFFFFFF, small.getRGB(8, 0) & 0xFFFFFF);
        s.set(2, 0, 16);
        BufferedImage big = s.create(2).apply(in);
        // one block: (255 * 128 + 128) / 256 = 128
        assertEquals(0x808080, big.getRGB(0, 0) & 0xFFFFFF);
        assertEquals(0x808080, big.getRGB(15, 15) & 0xFFFFFF);
        assertNotEquals(small.getRGB(0, 0), big.getRGB(0, 0));
    }

    @Test
    void thresholdChangesTwoToneOutputByHand() {
        OptionState s = new OptionState(EffectRegistry.defaults());
        BufferedImage in = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) {
                in.setRGB(x, y, 0x646464); // gray 100
            }
        }
        s.set(4, 0, 96);
        assertEquals(0xFFFFFF, s.create(4).apply(in).getRGB(0, 0) & 0xFFFFFF);
        s.set(4, 0, 104);
        assertEquals(0x000000, s.create(4).apply(in).getRGB(0, 0) & 0xFFFFFF);
    }

    @Test
    void dotBlockSizeChangesOutput() {
        OptionState s = new OptionState(EffectRegistry.defaults());
        BufferedImage in = randomImage(32, 32, 9);
        s.set(5, 0, 4);
        int[] a = pixels(s.create(5).apply(in));
        s.set(5, 0, 16);
        int[] b = pixels(s.create(5).apply(in));
        assertFalse(java.util.Arrays.equals(a, b));
        assertEquals(16, ((PixelDotEffect) s.create(5)).blockSize());
    }

    @Test
    void gridDotsNeverGetBelowFour() {
        EffectRegistry r = EffectRegistry.defaults();
        OptionState s = new OptionState(r);
        for (int n : new int[] {7, 8}) {
            assertEquals(4, s.set(n, 0, 2));
            assertEquals(4, s.set(n, 0, -100));
            assertEquals(4, s.step(n, 0, -1));
            PixelDotEffect e = (PixelDotEffect) s.create(n); // would throw below 4
            assertEquals(4, e.blockSize());
            assertTrue(e.gridLines());
        }
        // non-grid dots may go down to 2
        assertEquals(2, s.set(5, 0, 0));
        assertEquals(2, ((PixelDotEffect) s.create(5)).blockSize());
        assertEquals(64, s.set(5, 0, 500));
    }

    @Test
    void valuesAreRememberedPerEntry() {
        OptionState s = new OptionState(EffectRegistry.defaults());
        s.set(2, 0, 30);
        s.set(5, 0, 12);
        s.set(9, 0, 2);
        s.set(9, 1, 1);
        // changing another entry changes nothing here
        s.set(3, 0, 99);
        assertEquals(30, s.get(2, 0));
        assertEquals(12, s.get(5, 0));
        assertEquals(8, s.get(6, 0));
        assertEquals(2, s.get(9, 0));
        assertEquals(1, s.get(9, 1));
        assertEquals(30, ((MosaicEffect) s.create(2)).blockSize());
        assertArrayEquals(new int[] {2, 1}, s.values(9));
        assertTrue(s.trimTrailing(9));
        s.set(9, 1, 0);
        assertFalse(s.trimTrailing(9));
        assertFalse(s.trimTrailing(2));
    }

    @Test
    void stepAndFirstSteppable() {
        OptionState s = new OptionState(EffectRegistry.defaults());
        assertEquals(-1, s.firstSteppable(1));
        assertEquals(-1, s.firstSteppable(3));
        assertEquals(0, s.firstSteppable(2));
        assertEquals(0, s.firstSteppable(9)); // the cell size, not the bool
        assertEquals(12, s.step(2, 0, 1));
        assertEquals(10, s.step(2, 0, -1));
        assertEquals(64, s.set(2, 0, 64));
        assertEquals(64, s.step(2, 0, 1));
        assertEquals(1, s.step(9, 0, 1));
        assertEquals(2, s.step(9, 0, 1));
        assertEquals(2, s.step(9, 0, 1));
        assertEquals(0, s.get(99, 0));
        assertEquals(0, s.set(99, 0, 5));
    }

    @Test
    void asciiCellSizeSetsTheTextGridSize() {
        OptionState s = new OptionState(EffectRegistry.defaults());
        BufferedImage in = randomImage(50, 30, 3);
        int[][] cells = {{8, 16}, {6, 12}, {4, 8}};
        for (int i = 0; i < cells.length; i++) {
            s.set(9, 0, i);
            AsciiArtEffect e = (AsciiArtEffect) s.create(9);
            assertEquals(cells[i][0], e.cellWidth());
            assertEquals(cells[i][1], e.cellHeight());
            e.apply(in);
            TextGrid g = e.lastGrid();
            assertEquals((50 + cells[i][0] - 1) / cells[i][0], g.cols());
            assertEquals((30 + cells[i][1] - 1) / cells[i][1], g.rows());
            String[] lines = e.lastText().split("\n", -1);
            assertEquals(g.rows(), lines.length);
            assertEquals(g.cols(), lines[0].length());
        }
    }

    @Test
    void smallestAsciiCellStillHasSeveralGlyphs() {
        AsciiGlyphCache cache = new AsciiGlyphCache();
        for (int[] c : AsciiGlyphCache.CELL_SIZES) {
            GlyphSet g = cache.get(c[0], c[1]);
            assertTrue(g.size() >= 2, c[0] + "x" + c[1] + " has " + g.size());
        }
    }

    @Test
    void glyphCacheReadinessAndSharing() {
        AsciiGlyphCache cache = new AsciiGlyphCache();
        assertFalse(cache.isReady(6, 12));
        GlyphSet a = cache.get(6, 12);
        assertTrue(cache.isReady(6, 12));
        assertTrue(a == cache.get(6, 12));
        assertFalse(cache.isReady(4, 8));
        cache.prefetchAll();
        assertTrue(cache.isReady(4, 8));
        assertTrue(cache.isReady(8, 16));
    }

    @Test
    void asciiBecomesReadyOnlyAfterItsGlyphSetExists() {
        OptionState s = new OptionState(EffectRegistry.defaults());
        assertTrue(s.isReady(9)); // 8x16 was built for the default instance
        s.set(9, 0, 2);
        assertFalse(s.isReady(9));
        s.create(9);
        assertTrue(s.isReady(9));
        assertTrue(s.isReady(2));
    }

    @Test
    void freshAsciiInstanceHasNoGridUntilItProcessesAFrame() {
        OptionState s = new OptionState(EffectRegistry.defaults());
        AsciiArtEffect e = (AsciiArtEffect) s.create(9);
        assertNull(e.lastGrid());
        assertEquals("", e.lastText());
        e.apply(randomImage(16, 16, 1));
        assertEquals(2, e.lastGrid().cols());
    }
}
