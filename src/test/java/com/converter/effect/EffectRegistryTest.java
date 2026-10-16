package com.converter.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EffectRegistryTest {

    @Test
    void defaultsHaveNineInOrder() {
        EffectRegistry r = EffectRegistry.defaults();
        assertEquals(9, r.size());
        assertEquals("ASCII 아트", r.get(9).get().name());
        assertTrue(r.get(9).get().effect() instanceof AsciiArtEffect);
        assertEquals("ASCII 아트", r.entries().get(8).name());
        assertEquals("도트 16색", r.get(5).get().name());
        assertEquals("도트 게임보이", r.get(6).get().name());
        assertEquals("도트 격자", r.get(7).get().name());
        assertEquals("도트 16색+격자", r.get(8).get().name());
        assertDot(r, 5, Palette.EGA_16, false);
        assertDot(r, 6, Palette.GAMEBOY_4, false);
        assertDot(r, 7, Palette.NONE, true);
        assertDot(r, 8, Palette.EGA_16, true);
        assertEquals("도트 16색+격자", r.entries().get(7).name());
        assertEquals("원본", r.get(1).get().name());
        assertEquals("모자이크", r.get(2).get().name());
        assertEquals("흑백", r.get(3).get().name());
        assertEquals("흑백 2색", r.get(4).get().name());
        assertTrue(r.get(1).get().effect() instanceof IdentityEffect);
        assertEquals(10, ((MosaicEffect) r.get(2).get().effect()).blockSize());
        assertFalse(((GrayscaleEffect) r.get(3).get().effect()).isThreshold());
        assertTrue(((GrayscaleEffect) r.get(4).get().effect()).isThreshold());
        assertEquals("원본", r.entries().get(0).name());
        assertEquals("흑백 2색", r.entries().get(3).name());
    }

    @Test
    void unregisteredNumbersAreEmpty() {
        EffectRegistry r = EffectRegistry.defaults();
        assertTrue(r.get(0).isEmpty());
        assertTrue(r.get(10).isEmpty());
        assertTrue(r.get(-1).isEmpty());
    }

    private static void assertDot(EffectRegistry r, int n, Palette p, boolean grid) {
        PixelDotEffect e = (PixelDotEffect) r.get(n).get().effect();
        assertEquals(8, e.blockSize());
        assertEquals(p, e.palette());
        assertEquals(grid, e.gridLines());
    }

    @Test
    void registerAppendsWithNextNumber() {
        EffectRegistry r = EffectRegistry.defaults();
        Effect e = new IdentityEffect();
        r.register("새 효과", e);
        assertEquals(10, r.size());
        assertSame(e, r.get(10).get().effect());
        assertEquals("새 효과", r.entries().get(9).name());
    }
}
