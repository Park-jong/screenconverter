package com.converter.effect;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * Mosaic: every blockSize x blockSize block is replaced by its average colour.
 *
 * <ul>
 *   <li>The block grid origin is the image's (0,0); the last column/row may be a partial block.</li>
 *   <li>Each block is averaged over its actual pixel count (never blockSize^2 for partial blocks).</li>
 *   <li>Rounding: per channel, integer sum then {@code (sum + count / 2) / count}
 *       (round half up). Alpha is ignored; output is opaque TYPE_INT_RGB.</li>
 *   <li>A block size larger than the image simply yields one block = whole-image average.</li>
 *   <li>blockSize below {@value #MIN_BLOCK_SIZE} is rejected with IllegalArgumentException
 *       (no clamping).</li>
 * </ul>
 */
public final class MosaicEffect implements Effect {

    public static final int DEFAULT_BLOCK_SIZE = 10;
    public static final int MIN_BLOCK_SIZE = 2;

    private final int blockSize;

    public MosaicEffect() {
        this(DEFAULT_BLOCK_SIZE);
    }

    /** @throws IllegalArgumentException if blockSize is less than {@value #MIN_BLOCK_SIZE} */
    public MosaicEffect(int blockSize) {
        if (blockSize < MIN_BLOCK_SIZE) {
            throw new IllegalArgumentException(
                    "blockSize must be >= " + MIN_BLOCK_SIZE + " but was " + blockSize);
        }
        this.blockSize = blockSize;
    }

    public int blockSize() {
        return blockSize;
    }

    @Override
    public BufferedImage apply(BufferedImage src) {
        final int w = src.getWidth();
        final int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        if (w == 0 || h == 0) {
            return out;
        }
        int[] in = src.getRGB(0, 0, w, h, null, 0, w); // ARGB, row-major, copy (src untouched)
        int[] dst = ((DataBufferInt) out.getRaster().getDataBuffer()).getData();

        int cols = (w + blockSize - 1) / blockSize;
        long[] sr = new long[cols];
        long[] sg = new long[cols];
        long[] sb = new long[cols];
        int[] avg = new int[cols];

        for (int by = 0; by < h; by += blockSize) {
            int y1 = Math.min(by + blockSize, h);
            java.util.Arrays.fill(sr, 0);
            java.util.Arrays.fill(sg, 0);
            java.util.Arrays.fill(sb, 0);

            for (int y = by; y < y1; y++) {
                int idx = y * w;
                for (int c = 0; c < cols; c++) {
                    int x0 = c * blockSize;
                    int x1 = Math.min(x0 + blockSize, w);
                    long r = 0;
                    long g = 0;
                    long b = 0;
                    for (int x = x0; x < x1; x++) {
                        int p = in[idx + x];
                        r += (p >> 16) & 0xFF;
                        g += (p >> 8) & 0xFF;
                        b += p & 0xFF;
                    }
                    sr[c] += r;
                    sg[c] += g;
                    sb[c] += b;
                }
            }

            int rows = y1 - by;
            for (int c = 0; c < cols; c++) {
                int x0 = c * blockSize;
                int count = rows * (Math.min(x0 + blockSize, w) - x0); // actual pixel count
                int half = count / 2;
                int r = (int) ((sr[c] + half) / count);
                int g = (int) ((sg[c] + half) / count);
                int b = (int) ((sb[c] + half) / count);
                avg[c] = (r << 16) | (g << 8) | b;
            }

            for (int y = by; y < y1; y++) {
                int idx = y * w;
                for (int c = 0; c < cols; c++) {
                    int x0 = c * blockSize;
                    int x1 = Math.min(x0 + blockSize, w);
                    java.util.Arrays.fill(dst, idx + x0, idx + x1, avg[c]);
                }
            }
        }
        return out;
    }
}
