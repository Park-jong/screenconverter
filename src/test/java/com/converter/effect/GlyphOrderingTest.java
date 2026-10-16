package com.converter.effect;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GlyphOrderingTest {

    private static boolean[] ink(int n) {
        boolean[] m = new boolean[8];
        for (int i = 0; i < n; i++) {
            m[i] = true;
        }
        return m;
    }

    @Test
    void sortsAscendingAndKeepsSmallerCodeOnTies() {
        char[] chars = {'b', 'a', 'c', 'd'};
        boolean[][] masks = {ink(2), ink(2), ink(0), ink(5)};
        // c(0), a(2: 'a' < 'b' kept), d(5)
        assertArrayEquals(new int[] {2, 1, 3}, GlyphOrdering.order(chars, masks));
    }

    @Test
    void blankComesFirstAndInputOrderIsIrrelevant() {
        char[] chars = {'#', '.', ' ', '-'};
        boolean[][] masks = {ink(8), ink(1), ink(0), ink(4)};
        int[] o = GlyphOrdering.order(chars, masks);
        assertEquals(' ', chars[o[0]]);
        assertArrayEquals(new int[] {2, 1, 3, 0}, o);
    }

    @Test
    void mismatchedLengthsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> GlyphOrdering.order(new char[] {'a'}, new boolean[0][]));
    }
}
