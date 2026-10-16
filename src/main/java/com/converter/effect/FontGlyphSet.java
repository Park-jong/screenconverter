package com.converter.effect;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * {@link GlyphSet} pre-rendered with java2d. Font candidates: Consolas, Courier New, then
 * {@link Font#MONOSPACED}; a candidate is used only if the JVM resolved it to that family
 * (otherwise java2d silently substitutes "Dialog"). Each character is drawn black on white at a
 * size fitted to the cell height, cut to the cell size, and binarised with
 * {@link #INK_THRESHOLD}. Glyphs are sorted by ink count ascending, equal-ink duplicates removed
 * (see {@link GlyphOrdering}).
 */
public final class FontGlyphSet implements GlyphSet {

    public static final int DEFAULT_CELL_WIDTH = 8;
    public static final int DEFAULT_CELL_HEIGHT = 16;
    /** A rendered pixel is ink when its brightness is below this (0..255). */
    public static final int INK_THRESHOLD = 128;
    /** Candidate characters, blank included. */
    public static final String DEFAULT_CHARS =
            " .'`,:;\"^~-_=+<>!/\\|()[]{}?*iltrxnuvczsJLTYFCIfjo17#%&8$WMB@";

    private static final String[] FONT_CANDIDATES = {"Consolas", "Courier New"};

    private final int cellWidth;
    private final int cellHeight;
    private final String fontName;
    private final char[] chars;
    private final boolean[][] masks;

    public FontGlyphSet() {
        this(DEFAULT_CELL_WIDTH, DEFAULT_CELL_HEIGHT, DEFAULT_CHARS);
    }

    /** @throws IllegalArgumentException if the cell is smaller than 1x1 or candidates is empty */
    public FontGlyphSet(int cellWidth, int cellHeight, String candidates) {
        if (cellWidth < 1 || cellHeight < 1) {
            throw new IllegalArgumentException(
                    "cell size must be >= 1x1 but was " + cellWidth + "x" + cellHeight);
        }
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("candidates must not be empty");
        }
        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;

        Font font = resolveFont(cellHeight);
        this.fontName = font.getFontName();

        char[] cs = candidates.toCharArray();
        boolean[][] rendered = new boolean[cs.length][];
        BufferedImage img = new BufferedImage(cellWidth, cellHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                    RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics();
            int baseline = (cellHeight - (fm.getAscent() + fm.getDescent())) / 2 + fm.getAscent();
            for (int i = 0; i < cs.length; i++) {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, cellWidth, cellHeight);
                g.setColor(Color.BLACK);
                g.drawString(String.valueOf(cs[i]), 0, baseline); // clipped to the cell
                rendered[i] = binarise(img);
            }
        } finally {
            g.dispose();
        }

        int[] order = GlyphOrdering.order(cs, rendered);
        this.chars = new char[order.length];
        this.masks = new boolean[order.length][];
        for (int i = 0; i < order.length; i++) {
            chars[i] = cs[order[i]];
            masks[i] = rendered[order[i]];
        }
    }

    private boolean[] binarise(BufferedImage img) {
        boolean[] m = new boolean[cellWidth * cellHeight];
        for (int y = 0; y < cellHeight; y++) {
            for (int x = 0; x < cellWidth; x++) {
                int p = img.getRGB(x, y);
                int lum = (299 * ((p >> 16) & 0xFF) + 587 * ((p >> 8) & 0xFF)
                        + 114 * (p & 0xFF) + 500) / 1000;
                m[y * cellWidth + x] = lum < INK_THRESHOLD;
            }
        }
        return m;
    }

    /** First candidate family really resolved by the JVM; the largest size whose line fits. */
    private static Font resolveFont(int cellHeight) {
        Font base = null;
        for (String name : FONT_CANDIDATES) {
            Font f = new Font(name, Font.PLAIN, 12);
            if (f.getFamily().equalsIgnoreCase(name)) {
                base = f;
                break;
            }
        }
        if (base == null) {
            base = new Font(Font.MONOSPACED, Font.PLAIN, 12);
        }
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = probe.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                    RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
            for (int size = cellHeight; size >= 1; size--) {
                Font f = base.deriveFont((float) size);
                FontMetrics fm = g.getFontMetrics(f);
                if (fm.getAscent() + fm.getDescent() <= cellHeight) {
                    return f;
                }
            }
        } finally {
            g.dispose();
        }
        return base.deriveFont(1f);
    }

    /** Name of the font actually used (e.g. "Consolas"). */
    public String fontName() {
        return fontName;
    }

    @Override
    public int cellWidth() {
        return cellWidth;
    }

    @Override
    public int cellHeight() {
        return cellHeight;
    }

    @Override
    public int size() {
        return chars.length;
    }

    @Override
    public char charAt(int index) {
        return chars[index];
    }

    @Override
    public boolean[] mask(int index) {
        return masks[index];
    }
}
