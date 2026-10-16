package com.converter.effect;

/** Deterministic 4x4 glyphs sorted by ink: blank(0), dot(1), line(4), full(16). */
final class FakeGlyphSet implements GlyphSet {

    static final int W = 4;
    static final int H = 4;
    static final char[] CHARS = {' ', '.', '-', '#'};
    static final boolean[][] MASKS = {
        new boolean[16],
        maskOf(0, 0),
        maskOf(0, 1, 1, 1, 2, 1, 3, 1),
        maskOf(0, 0, 1, 0, 2, 0, 3, 0, 0, 1, 1, 1, 2, 1, 3, 1,
                0, 2, 1, 2, 2, 2, 3, 2, 0, 3, 1, 3, 2, 3, 3, 3),
    };

    /** Mask from (x, y) pairs. */
    private static boolean[] maskOf(int... xy) {
        boolean[] m = new boolean[W * H];
        for (int i = 0; i < xy.length; i += 2) {
            m[xy[i + 1] * W + xy[i]] = true;
        }
        return m;
    }

    @Override
    public int cellWidth() {
        return W;
    }

    @Override
    public int cellHeight() {
        return H;
    }

    @Override
    public int size() {
        return CHARS.length;
    }

    @Override
    public char charAt(int index) {
        return CHARS[index];
    }

    @Override
    public boolean[] mask(int index) {
        return MASKS[index];
    }
}
