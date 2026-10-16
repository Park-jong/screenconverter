package com.converter.effect;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.awt.image.SinglePixelPackedSampleModel;
import java.util.Arrays;

/**
 * Black-and-white ASCII art. The image is split into cellW x cellH cells (grid origin (0,0));
 * each cell's average colour gives a brightness L, which selects a glyph; the glyph's binary mask
 * is composited onto a white background in black.
 *
 * <ul>
 *   <li>Cell average per channel: {@code (sum + count / 2) / count}, partial cells at the
 *       right/bottom edge use their actual pixel count.</li>
 *   <li>Brightness {@code L = (299R + 587G + 114B + 500) / 1000}.</li>
 *   <li>Glyph index {@code k = ((255 - L) * N) / 256} (integer division), N glyphs sorted by ink
 *       ascending. Brightness is split into N half-open intervals: glyph k covers the L values
 *       with k = floor((255-L)*N/256). L=255 gives k=0 (least ink, blank), L=0 gives k=N-1
 *       (densest). Dark cell = dense glyph.</li>
 *   <li>Partial cells draw only the part of the mask inside the image.</li>
 * </ul>
 * Output is a new TYPE_INT_RGB image with only 0xFFFFFF and 0x000000; the input is not modified.
 * Alpha is ignored. No drawString per frame: glyph pixels are pre-expanded to int[] once.
 */
public final class AsciiArtEffect implements Effect, TextProducingEffect {

    private static final int WHITE = 0xFFFFFF;
    private static final int BLACK = 0x000000;

    private final int cellW;
    private final int cellH;
    private final int n;
    /** Per glyph: cellW*cellH pixels (WHITE / BLACK), row-major. */
    private final int[][] glyphPixels;
    private final boolean[] blank;
    /** Per glyph: its character (same index as glyphPixels). */
    private final char[] glyphChars;
    /** Character grid of the most recently processed frame; null before the first frame. */
    private volatile TextGrid lastGrid;

    /** Default: {@link FontGlyphSet} with 8x16 cells. */
    public AsciiArtEffect() {
        this(new FontGlyphSet());
    }

    /** @throws IllegalArgumentException if glyphs is null, empty, or has an invalid cell/mask */
    public AsciiArtEffect(GlyphSet glyphs) {
        if (glyphs == null) {
            throw new IllegalArgumentException("glyphs must not be null");
        }
        cellW = glyphs.cellWidth();
        cellH = glyphs.cellHeight();
        n = glyphs.size();
        if (cellW < 1 || cellH < 1) {
            throw new IllegalArgumentException(
                    "cell size must be >= 1x1 but was " + cellW + "x" + cellH);
        }
        if (n < 1) {
            throw new IllegalArgumentException("glyph set must not be empty");
        }
        glyphPixels = new int[n][];
        blank = new boolean[n];
        glyphChars = new char[n];
        for (int i = 0; i < n; i++) {
            boolean[] mask = glyphs.mask(i);
            if (mask == null || mask.length != cellW * cellH) {
                throw new IllegalArgumentException("mask " + i + " must have length "
                        + cellW * cellH);
            }
            int[] px = new int[mask.length];
            boolean any = false;
            for (int j = 0; j < mask.length; j++) {
                px[j] = mask[j] ? BLACK : WHITE;
                any |= mask[j];
            }
            glyphPixels[i] = px;
            blank[i] = !any;
            glyphChars[i] = glyphs.charAt(i);
        }
    }

    public int cellWidth() {
        return cellW;
    }

    public int cellHeight() {
        return cellH;
    }

    public int glyphCount() {
        return n;
    }

    @Override
    public BufferedImage apply(BufferedImage src) {
        final int w = src.getWidth();
        final int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        if (w == 0 || h == 0) {
            lastGrid = new TextGrid(new char[0], 0, 0);
            return out;
        }
        int[] in = directPixels(src, w, h);
        if (in == null) {
            in = src.getRGB(0, 0, w, h, null, 0, w); // other image types: converted copy
        }
        int[] dst = ((DataBufferInt) out.getRaster().getDataBuffer()).getData();

        final int cols = (w + cellW - 1) / cellW;
        final int rows = (h + cellH - 1) / cellH;
        int[] avg = cellAverages(in, w, h, cols, rows);
        char[] cells = new char[cols * rows];

        for (int cy = 0; cy < rows; cy++) {
            int y0 = cy * cellH;
            int ch = Math.min(cellH, h - y0);
            for (int cx = 0; cx < cols; cx++) {
                int x0 = cx * cellW;
                int cw = Math.min(cellW, w - x0);
                int k = glyphIndex(brightness(avg[cy * cols + cx]));
                cells[cy * cols + cx] = glyphChars[k];
                if (blank[k]) {
                    for (int r = 0; r < ch; r++) {
                        int o = (y0 + r) * w + x0;
                        Arrays.fill(dst, o, o + cw, WHITE);
                    }
                } else {
                    int[] g = glyphPixels[k];
                    for (int r = 0; r < ch; r++) {
                        System.arraycopy(g, r * cellW, dst, (y0 + r) * w + x0, cw);
                    }
                }
            }
        }
        lastGrid = new TextGrid(cells, cols, rows);
        return out;
    }

    @Override
    public TextGrid lastGrid() {
        return lastGrid;
    }

    @Override
    public String lastText() {
        TextGrid g = lastGrid;
        return g == null ? "" : g.text();
    }

    /**
     * Average colour (0xRRGGBB) of every cell, row-major (cols*rows entries). Rounding
     * {@code (sum + count / 2) / count} per channel; count is the actual pixel count of the
     * (possibly partial) cell.
     */
    private int[] cellAverages(int[] in, int w, int h, int cols, int rows) {
        int[] avg = new int[cols * rows];
        long[] sr = new long[cols];
        long[] sg = new long[cols];
        long[] sb = new long[cols];
        for (int cy = 0; cy < rows; cy++) {
            int y0 = cy * cellH;
            int y1 = Math.min(y0 + cellH, h);
            Arrays.fill(sr, 0);
            Arrays.fill(sg, 0);
            Arrays.fill(sb, 0);
            for (int y = y0; y < y1; y++) {
                int idx = y * w;
                for (int c = 0; c < cols; c++) {
                    int x0 = c * cellW;
                    int x1 = Math.min(x0 + cellW, w);
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
            int rh = y1 - y0;
            for (int c = 0; c < cols; c++) {
                int x0 = c * cellW;
                int count = rh * (Math.min(x0 + cellW, w) - x0);
                int half = count / 2;
                int r = (int) ((sr[c] + half) / count);
                int g = (int) ((sg[c] + half) / count);
                int b = (int) ((sb[c] + half) / count);
                avg[cy * cols + c] = (r << 16) | (g << 8) | b;
            }
        }
        return avg;
    }

    /** Same integer formula as GrayscaleEffect (4-5 전수 검증된 식과 동일). */
    private static int brightness(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (299 * r + 587 * g + 114 * b + 500) / 1000;
    }

    /** k = ((255 - L) * N) / 256, in 0..N-1 for L in 0..255 (half-open intervals). */
    private int glyphIndex(int lum) {
        return ((255 - lum) * n) / 256;
    }

    /** See PixelDotEffect.directPixels (same conditions, duplicated on purpose). */
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
