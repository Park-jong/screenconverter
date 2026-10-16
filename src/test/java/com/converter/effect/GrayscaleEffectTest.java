package com.converter.effect;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class GrayscaleEffectTest {

    private static BufferedImage one(int rgb) {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, rgb);
        return img;
    }

    private static int gray(int v) {
        return (v << 16) | (v << 8) | v;
    }

    private static int px(BufferedImage img) {
        return img.getRGB(0, 0) & 0xFFFFFF;
    }

    @Test
    void pureColorsHandComputed() {
        GrayscaleEffect e = new GrayscaleEffect();
        // R: 0.299*255 = 76.245 -> 76; G: 0.587*255 = 149.685 -> 150; B: 0.114*255 = 29.07 -> 29
        assertEquals(gray(76), px(e.apply(one(0xFF0000))));
        assertEquals(gray(150), px(e.apply(one(0x00FF00))));
        assertEquals(gray(29), px(e.apply(one(0x0000FF))));
        assertEquals(gray(255), px(e.apply(one(0xFFFFFF))));
        assertEquals(gray(0), px(e.apply(one(0x000000))));
    }

    @Test
    void roundsHalfUp() {
        GrayscaleEffect e = new GrayscaleEffect();
        // B=5: 0.57 -> 1; B=4: 0.456 -> 0; (1,1,0): 0.886 -> 1; B=0,G=1... G=1: 0.587 -> 1.
        assertEquals(gray(1), px(e.apply(one(0x000005))));
        assertEquals(gray(0), px(e.apply(one(0x000004))));
        assertEquals(gray(1), px(e.apply(one(0x010100))));
        assertEquals(gray(1), px(e.apply(one(0x000100))));
        // R=2: 0.598 -> 1; R=1: 0.299 -> 0.
        assertEquals(gray(1), px(e.apply(one(0x020000))));
        assertEquals(gray(0), px(e.apply(one(0x010000))));
    }

    @Test
    void alreadyGrayUnchanged() {
        for (int v : new int[] {0, 1, 77, 128, 200, 254, 255}) {
            assertEquals(gray(v), px(new GrayscaleEffect().apply(one(gray(v)))), "v=" + v);
        }
    }

    @Test
    void thresholdBoundary() {
        GrayscaleEffect at = new GrayscaleEffect(128);
        assertEquals(gray(255), px(at.apply(one(gray(128))))); // gray == threshold -> white
        assertEquals(gray(0), px(at.apply(one(gray(127)))));
        assertEquals(gray(0), px(new GrayscaleEffect(129).apply(one(gray(128)))));
        assertEquals(gray(255), px(new GrayscaleEffect(129).apply(one(gray(129)))));
        assertEquals(gray(255), px(new GrayscaleEffect(0).apply(one(0x000000))));
        assertEquals(gray(0), px(new GrayscaleEffect(255).apply(one(gray(254)))));
        assertEquals(gray(255), px(new GrayscaleEffect(255).apply(one(gray(255)))));
        // pure red has gray 76.
        assertEquals(gray(255), px(new GrayscaleEffect(76).apply(one(0xFF0000))));
        assertEquals(gray(0), px(new GrayscaleEffect(77).apply(one(0xFF0000))));
    }

    @Test
    void inputUnchangedSizeAndTypeKept() {
        BufferedImage src = new BufferedImage(5, 3, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 5; x++) {
                src.setRGB(x, y, 0xFF000000 | (x * 50) << 16 | (y * 100) << 8 | 7);
            }
        }
        int[] before = src.getRGB(0, 0, 5, 3, null, 0, 5);
        BufferedImage out = new GrayscaleEffect().apply(src);
        assertNotSame(src, out);
        assertEquals(5, out.getWidth());
        assertEquals(3, out.getHeight());
        assertEquals(BufferedImage.TYPE_INT_RGB, out.getType());
        assertArrayEquals(before, src.getRGB(0, 0, 5, 3, null, 0, 5));
    }

    @Test
    void invalidThresholdRejected() {
        assertThrows(IllegalArgumentException.class, () -> new GrayscaleEffect(-1));
        assertThrows(IllegalArgumentException.class, () -> new GrayscaleEffect(256));
    }
}
