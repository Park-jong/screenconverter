package com.converter.effect;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * Grayscale: gray = 0.299 R + 0.587 G + 0.114 B, computed in integers as
 * {@code (299*R + 587*G + 114*B + 500) / 1000} (round half up; the weights sum to 1000, so
 * pure white stays 255). Alpha is ignored.
 *
 * <p>Decimal values quoted in comments/tests (e.g. 149.685) are the value before adding the
 * +500 rounding term. The double formula floor(0.299R+0.587G+0.114B+0.5) comes out 1 too low
 * for 3,464 combinations on the .5 boundary because of floating-point error, so the integer
 * formula is the reference (verified exhaustively in step 4-5).
 *
 * <p>Default mode is continuous gray (R = G = B = gray). With a threshold (0..255) the result
 * is two-tone: 255 if {@code gray >= threshold}, otherwise 0.
 */
public final class GrayscaleEffect implements Effect {

    public static final int MIN_THRESHOLD = 0;
    public static final int MAX_THRESHOLD = 255;

    /** -1 means continuous gray. */
    private final int threshold;

    /** Continuous grayscale. */
    public GrayscaleEffect() {
        this.threshold = -1;
    }

    /**
     * Two-tone mode.
     *
     * @throws IllegalArgumentException if threshold is outside 0..255
     */
    public GrayscaleEffect(int threshold) {
        if (threshold < MIN_THRESHOLD || threshold > MAX_THRESHOLD) {
            throw new IllegalArgumentException(
                    "threshold must be in 0..255 but was " + threshold);
        }
        this.threshold = threshold;
    }

    /** True if this instance outputs only black and white. */
    public boolean isThreshold() {
        return threshold >= 0;
    }

    @Override
    public BufferedImage apply(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] px = src.getRGB(0, 0, w, h, null, 0, w);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] dst = ((DataBufferInt) out.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int r = (p >> 16) & 0xFF;
            int g = (p >> 8) & 0xFF;
            int b = p & 0xFF;
            int gray = (299 * r + 587 * g + 114 * b + 500) / 1000;
            if (threshold >= 0) {
                gray = gray >= threshold ? 255 : 0;
            }
            dst[i] = (gray << 16) | (gray << 8) | gray;
        }
        return out;
    }
}
