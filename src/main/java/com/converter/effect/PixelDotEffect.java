package com.converter.effect;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.awt.image.SinglePixelPackedSampleModel;
import java.util.Arrays;

/**
 * Pixel-dot effect in a single pass: block average, then palette quantisation, then grid lines.
 *
 * <ol>
 *   <li>Block average: same rule as {@link MosaicEffect} (grid origin (0,0), partial blocks use
 *       their actual pixel count, rounding {@code (sum + count / 2) / count}).</li>
 *   <li>If the palette is not NONE, the average becomes the nearest palette colour.</li>
 *   <li>If gridLines, each block's left column and top row (1px) are painted
 *       {@link #GRID_COLOR}, independent of the palette, also on partial blocks, never beyond
 *       the image.</li>
 * </ol>
 * Integer arithmetic only. Output is a new TYPE_INT_RGB image; the input is not modified.
 */
public final class PixelDotEffect implements Effect {

    public static final int DEFAULT_BLOCK_SIZE = 8;
    public static final int MIN_BLOCK_SIZE = 2;
    /** Minimum block size when grid lines are on. */
    public static final int MIN_GRID_BLOCK_SIZE = 4;
    /** Fixed dark grid colour (0xRRGGBB), not part of any palette. */
    public static final int GRID_COLOR = 0x202020;

    private final int blockSize;
    private final Palette palette;
    private final boolean gridLines;

    /**
     * @throws IllegalArgumentException if blockSize &lt; 2, palette is null, or gridLines is
     *     true with blockSize &lt; 4
     */
    public PixelDotEffect(int blockSize, Palette palette, boolean gridLines) {
        if (blockSize < MIN_BLOCK_SIZE) {
            throw new IllegalArgumentException(
                    "blockSize must be >= " + MIN_BLOCK_SIZE + " but was " + blockSize);
        }
        if (palette == null) {
            throw new IllegalArgumentException("palette must not be null");
        }
        if (gridLines && blockSize < MIN_GRID_BLOCK_SIZE) {
            throw new IllegalArgumentException(
                    "gridLines requires blockSize >= " + MIN_GRID_BLOCK_SIZE
                            + " but was " + blockSize);
        }
        this.blockSize = blockSize;
        this.palette = palette;
        this.gridLines = gridLines;
    }

    public int blockSize() {
        return blockSize;
    }

    public Palette palette() {
        return palette;
    }

    public boolean gridLines() {
        return gridLines;
    }

    @Override
    public BufferedImage apply(BufferedImage src) {
        final int w = src.getWidth();
        final int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        if (w == 0 || h == 0) {
            return out;
        }
        int[] in = directPixels(src, w, h);
        if (in == null) {
            in = src.getRGB(0, 0, w, h, null, 0, w); // other image types: converted copy
        }
        int[] dst = ((DataBufferInt) out.getRaster().getDataBuffer()).getData();

        final boolean quantize = !palette.isNone();
        int cols = (w + blockSize - 1) / blockSize;
        long[] sr = new long[cols];
        long[] sg = new long[cols];
        long[] sb = new long[cols];
        int[] avg = new int[cols];

        for (int by = 0; by < h; by += blockSize) {
            int y1 = Math.min(by + blockSize, h);
            Arrays.fill(sr, 0);
            Arrays.fill(sg, 0);
            Arrays.fill(sb, 0);

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
                int count = rows * (Math.min(x0 + blockSize, w) - x0);
                int half = count / 2;
                int r = (int) ((sr[c] + half) / count);
                int g = (int) ((sg[c] + half) / count);
                int b = (int) ((sb[c] + half) / count);
                int color = (r << 16) | (g << 8) | b;
                avg[c] = quantize ? palette.nearest(color) : color;
            }

            for (int y = by; y < y1; y++) {
                int idx = y * w;
                boolean topRow = gridLines && y == by;
                for (int c = 0; c < cols; c++) {
                    int x0 = c * blockSize;
                    int x1 = Math.min(x0 + blockSize, w);
                    if (topRow) {
                        Arrays.fill(dst, idx + x0, idx + x1, GRID_COLOR);
                    } else {
                        Arrays.fill(dst, idx + x0, idx + x1, avg[c]);
                        if (gridLines) {
                            dst[idx + x0] = GRID_COLOR;
                        }
                    }
                }
            }
        }
        return out;
    }

    /**
     * The source's int[] when it is a plain INT_RGB/INT_ARGB image with a tight layout
     * (read only, no copy); otherwise null.
     */
    private static int[] directPixels(BufferedImage src, int w, int h) {
        int type = src.getType();
        if (type != BufferedImage.TYPE_INT_RGB && type != BufferedImage.TYPE_INT_ARGB) {
            return null;
        }
        DataBuffer db = src.getRaster().getDataBuffer();
        if (!(db instanceof DataBufferInt dbi) || dbi.getNumBanks() != 1
                || dbi.getOffset() != 0
                || !(src.getRaster().getSampleModel() instanceof SinglePixelPackedSampleModel sm)
                || sm.getScanlineStride() != w
                || src.getRaster().getSampleModelTranslateX() != 0
                || src.getRaster().getSampleModelTranslateY() != 0) {
            return null;
        }
        int[] data = dbi.getData();
        return data.length >= w * h ? data : null;
    }
}
