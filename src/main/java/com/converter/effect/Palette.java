package com.converter.effect;

import java.util.Arrays;

/**
 * Immutable colour palette for quantisation. Either {@link Kind#NONE} (quantisation off) or
 * {@link Kind#FIXED} (a fixed list of 0xRRGGBB colours).
 *
 * <p>Nearest colour: squared RGB Euclidean distance in integers (no floating point); ties go
 * to the earlier (lower index) palette colour.
 */
public final class Palette {

    public enum Kind { NONE, FIXED }

    /** Quantisation off. */
    public static final Palette NONE = new Palette(Kind.NONE, new int[0]);

    /** Game Boy 4 greens, darkest to lightest. */
    public static final Palette GAMEBOY_4 =
            fixed(new int[] {0x0F380F, 0x306230, 0x8BAC0F, 0x9BBC0F});

    /** Standard EGA/CGA 16 colours. */
    public static final Palette EGA_16 = fixed(new int[] {
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA,
        0xAA0000, 0xAA00AA, 0xAA5500, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF,
        0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF});

    private final Kind kind;
    private final int[] colors;

    private Palette(Kind kind, int[] colors) {
        this.kind = kind;
        this.colors = colors;
    }

    /**
     * Fixed palette from 0xRRGGBB values (defensively copied; high byte ignored).
     *
     * @throws IllegalArgumentException if rgb is null or empty
     */
    public static Palette fixed(int[] rgb) {
        if (rgb == null || rgb.length == 0) {
            throw new IllegalArgumentException("palette needs at least one colour");
        }
        int[] copy = rgb.clone();
        for (int i = 0; i < copy.length; i++) {
            copy[i] &= 0xFFFFFF;
        }
        return new Palette(Kind.FIXED, copy);
    }

    public Kind kind() {
        return kind;
    }

    public boolean isNone() {
        return kind == Kind.NONE;
    }

    /** Number of colours (0 for NONE). */
    public int size() {
        return colors.length;
    }

    /** Copy of the colours (empty for NONE). */
    public int[] colors() {
        return colors.clone();
    }

    /** True if the palette contains this 0xRRGGBB colour. */
    public boolean contains(int rgb) {
        int c = rgb & 0xFFFFFF;
        for (int p : colors) {
            if (p == c) {
                return true;
            }
        }
        return false;
    }

    /**
     * Nearest palette colour (0xRRGGBB) to the given colour; ties pick the lower index.
     *
     * @throws IllegalStateException for NONE
     */
    public int nearest(int rgb) {
        if (colors.length == 0) {
            throw new IllegalStateException("NONE palette has no colours");
        }
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int best = colors[0];
        int bestD = Integer.MAX_VALUE;
        for (int c : colors) {
            int dr = r - ((c >> 16) & 0xFF);
            int dg = g - ((c >> 8) & 0xFF);
            int db = b - (c & 0xFF);
            int d = dr * dr + dg * dg + db * db;
            if (d < bestD) { // strict: earlier colour wins ties
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Palette p && kind == p.kind && Arrays.equals(colors, p.colors);
    }

    @Override
    public int hashCode() {
        return 31 * kind.hashCode() + Arrays.hashCode(colors);
    }
}
