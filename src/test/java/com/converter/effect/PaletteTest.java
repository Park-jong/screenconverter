package com.converter.effect;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PaletteTest {

    @Test
    void gameBoyConstantValues() {
        assertArrayEquals(new int[] {0x0F380F, 0x306230, 0x8BAC0F, 0x9BBC0F},
                Palette.GAMEBOY_4.colors());
        assertEquals(Palette.Kind.FIXED, Palette.GAMEBOY_4.kind());
    }

    @Test
    void egaConstantValues() {
        assertArrayEquals(new int[] {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xAA5500, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF},
                Palette.EGA_16.colors());
    }

    @Test
    void noneHasNoColours() {
        assertEquals(Palette.Kind.NONE, Palette.NONE.kind());
        assertTrue(Palette.NONE.isNone());
        assertEquals(0, Palette.NONE.size());
        assertThrows(IllegalStateException.class, () -> Palette.NONE.nearest(0));
    }

    @Test
    void gameBoyNearestHandComputed() {
        // black: d(0F380F)=15^2+56^2+15^2=3586 < d(306230)=14212
        assertEquals(0x0F380F, Palette.GAMEBOY_4.nearest(0x000000));
        // white: d(9BBC0F)=72089 < d(8BAC0F)=77945
        assertEquals(0x9BBC0F, Palette.GAMEBOY_4.nearest(0xFFFFFF));
        // grey 128: 306230 -> 13700, 8BAC0F -> 14826, 9BBC0F -> 17098, 0F380F -> 30722
        assertEquals(0x306230, Palette.GAMEBOY_4.nearest(0x808080));
        // red: 8BAC0F -> 43265, 9BBC0F -> 45569, 306230 -> 54757, 0F380F -> 60961
        assertEquals(0x8BAC0F, Palette.GAMEBOY_4.nearest(0xFF0000));
    }

    @Test
    void egaNearestHandComputed() {
        assertEquals(0x000000, Palette.EGA_16.nearest(0x000000));
        assertEquals(0xFFFFFF, Palette.EGA_16.nearest(0xFFFFFF));
        // grey 128: AAAAAA -> 3*42^2=5292, 555555 -> 3*43^2=5547
        assertEquals(0xAAAAAA, Palette.EGA_16.nearest(0x808080));
        // red: AA0000 -> 85^2=7225, FF5555 -> 2*85^2=14450
        assertEquals(0xAA0000, Palette.EGA_16.nearest(0xFF0000));
        assertEquals(0xAA5500, Palette.EGA_16.nearest(0xAA5500));
    }

    @Test
    void tiePicksEarlierColour() {
        // 0x000001 is at squared distance 1 from both 0x000000 and 0x000002.
        assertEquals(0x000000,
                Palette.fixed(new int[] {0x000000, 0x000002}).nearest(0x000001));
        assertEquals(0x000002,
                Palette.fixed(new int[] {0x000002, 0x000000}).nearest(0x000001));
        // (1,1,0) is at squared distance 1 from both (1,0,0) and (0,1,0).
        assertEquals(0x010000,
                Palette.fixed(new int[] {0x010000, 0x000100}).nearest(0x010100));
        assertEquals(0x000100,
                Palette.fixed(new int[] {0x000100, 0x010000}).nearest(0x010100));
    }

    @Test
    void rejectsNullAndEmpty() {
        assertThrows(IllegalArgumentException.class, () -> Palette.fixed(null));
        assertThrows(IllegalArgumentException.class, () -> Palette.fixed(new int[0]));
    }

    @Test
    void defensiveCopies() {
        int[] src = {0x112233, 0x445566};
        Palette p = Palette.fixed(src);
        src[0] = 0;
        assertEquals(0x112233, p.colors()[0]);
        int[] out = p.colors();
        out[1] = 0;
        assertEquals(0x445566, p.colors()[1]);
        assertTrue(p.contains(0x445566));
        assertFalse(p.contains(0));
    }
}
