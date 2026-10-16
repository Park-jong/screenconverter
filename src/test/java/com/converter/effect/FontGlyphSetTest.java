package com.converter.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Loose, environment independent properties of the product glyph set. */
class FontGlyphSetTest {

    @Test
    void defaultPropertiesHold() {
        FontGlyphSet g = new FontGlyphSet();
        assertEquals(8, g.cellWidth());
        assertEquals(16, g.cellHeight());
        assertTrue(g.size() >= 2);
        assertNotNull(g.fontName());
        assertTrue(!g.fontName().isEmpty());
        assertEquals(' ', g.charAt(0));
        int prev = -1;
        for (int i = 0; i < g.size(); i++) {
            boolean[] m = g.mask(i);
            assertEquals(8 * 16, m.length);
            int ink = GlyphOrdering.ink(m);
            if (i == 0) {
                assertEquals(0, ink);
            }
            assertTrue(ink >= prev, "ink must not decrease at " + i);
            prev = ink;
        }
    }

    @Test
    void customCellSize() {
        FontGlyphSet g = new FontGlyphSet(6, 12, " .#@");
        assertEquals(6, g.cellWidth());
        assertEquals(12, g.cellHeight());
        assertEquals(6 * 12, g.mask(0).length);
        assertTrue(g.size() >= 2);
    }

    @Test
    void invalidArgumentsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new FontGlyphSet(0, 16, " ."));
        assertThrows(IllegalArgumentException.class, () -> new FontGlyphSet(8, 0, " ."));
        assertThrows(IllegalArgumentException.class, () -> new FontGlyphSet(8, 16, ""));
        assertThrows(IllegalArgumentException.class, () -> new FontGlyphSet(8, 16, null));
    }
}
