package com.converter.effect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Ink-density ordering of rendered glyphs (pure logic, font independent). */
final class GlyphOrdering {

    private GlyphOrdering() {
    }

    /** Number of ink (true) pixels in a mask. */
    static int ink(boolean[] mask) {
        int n = 0;
        for (boolean b : mask) {
            if (b) {
                n++;
            }
        }
        return n;
    }

    /**
     * Indices into {@code chars}/{@code masks} sorted by ink count ascending. Glyphs with the
     * same ink count are de-duplicated: the one with the smaller character code is kept.
     */
    static int[] order(char[] chars, boolean[][] masks) {
        if (chars.length != masks.length) {
            throw new IllegalArgumentException("chars and masks differ in length");
        }
        int n = chars.length;
        int[] inks = new int[n];
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            inks[i] = ink(masks[i]);
            idx.add(i);
        }
        idx.sort(Comparator.<Integer>comparingInt(i -> inks[i]).thenComparingInt(i -> chars[i]));
        List<Integer> kept = new ArrayList<>();
        int lastInk = -1;
        for (int i : idx) {
            if (inks[i] != lastInk) {
                kept.add(i);
                lastInk = inks[i];
            }
        }
        int[] result = new int[kept.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = kept.get(i);
        }
        return result;
    }
}
