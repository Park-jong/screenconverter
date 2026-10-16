package com.converter.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class EffectOptionTest {

    @Test
    void integerRangeDefaultAndClamp() {
        EffectOption o = EffectOption.integer("블록 크기", 2, 64, 2, 10);
        assertEquals(EffectOption.Kind.INT, o.kind());
        assertEquals(2, o.min());
        assertEquals(64, o.max());
        assertEquals(10, o.defaultValue());
        assertEquals(2, o.clamp(-5));
        assertEquals(64, o.clamp(1000));
        assertEquals(33, o.clamp(33));
        assertTrue(o.affectsEffect());
        assertTrue(o.steppable());
        assertEquals("12", o.format(12));
    }

    @Test
    void defaultIsClampedIntoRange() {
        assertEquals(4, EffectOption.integer("x", 4, 64, 2, 1).defaultValue());
        assertEquals(64, EffectOption.integer("x", 4, 64, 2, 99).defaultValue());
    }

    @Test
    void steppingMovesByStepAndClampsAtTheEnds() {
        EffectOption o = EffectOption.integer("임계값", 0, 255, 8, 128);
        assertEquals(136, o.stepped(128, 1));
        assertEquals(120, o.stepped(128, -1));
        assertEquals(255, o.stepped(250, 1));
        assertEquals(255, o.stepped(255, 1));
        assertEquals(0, o.stepped(3, -1));
        assertEquals(0, o.stepped(0, -1));
        assertEquals(128, o.stepped(128, 0));
    }

    @Test
    void invalidDefinitionsRejected() {
        assertThrows(IllegalArgumentException.class, () -> EffectOption.integer("x", 5, 4, 1, 4));
        assertThrows(IllegalArgumentException.class, () -> EffectOption.integer("x", 0, 4, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> EffectOption.choice("x", List.of(), 0));
    }

    @Test
    void choiceUsesIndicesAndLabels() {
        EffectOption o = EffectOption.choice("칸 크기", List.of("8x16", "6x12", "4x8"), 0);
        assertEquals(EffectOption.Kind.CHOICE, o.kind());
        assertEquals(0, o.min());
        assertEquals(2, o.max());
        assertEquals(1, o.step());
        assertEquals("6x12", o.format(1));
        assertEquals(2, o.stepped(2, 1));
        assertEquals(0, o.stepped(0, -1));
        assertEquals(1, o.stepped(0, 1));
        assertTrue(o.steppable());
    }

    @Test
    void boolOptionTargetsAndFormat() {
        EffectOption o = EffectOption.bool("줄 끝 공백 제거", false,
                EffectOption.Target.COPY_TRIM_TRAILING);
        assertEquals(EffectOption.Kind.BOOL, o.kind());
        assertEquals(0, o.defaultValue());
        assertFalse(o.affectsEffect());
        assertFalse(o.steppable());
        assertEquals("켜짐", o.format(1));
        assertEquals("꺼짐", o.format(0));
        assertEquals(1, o.clamp(7));
    }
}
