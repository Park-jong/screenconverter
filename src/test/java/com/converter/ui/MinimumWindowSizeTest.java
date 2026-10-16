package com.converter.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Dimension;
import java.awt.Insets;
import org.junit.jupiter.api.Test;

class MinimumWindowSizeTest {

    @Test
    void addsDecorationMenuBorderAndTarget() {
        Dimension d = MinimumWindowSize.compute(new Insets(31, 8, 8, 8), 180,
                new Insets(1, 1, 1, 1), new Dimension(320, 180), 0);
        assertEquals(8 + 8 + 180 + 2 + 320, d.width);
        assertEquals(31 + 8 + 2 + 180, d.height);
    }

    @Test
    void zeroInsetsGiveMenuPlusTarget() {
        Dimension d = MinimumWindowSize.compute(new Insets(0, 0, 0, 0), 100,
                new Insets(0, 0, 0, 0), new Dimension(320, 180), 0);
        assertEquals(new Dimension(420, 180), d);
    }

    @Test
    void menuWidthChangeIsFollowed() {
        Insets none = new Insets(0, 0, 0, 0);
        Dimension a = MinimumWindowSize.compute(none, 180, none, new Dimension(320, 180), 0);
        Dimension b = MinimumWindowSize.compute(none, 240, none, new Dimension(320, 180), 0);
        assertEquals(60, b.width - a.width);
    }

    @Test
    void tallMenuRaisesHeightButNotWidth() {
        Insets none = new Insets(0, 0, 0, 0);
        Dimension d = MinimumWindowSize.compute(new Insets(31, 8, 8, 8), 180,
                new Insets(1, 1, 1, 1), new Dimension(320, 180), 230);
        assertEquals(31 + 8 + 230, d.height);
        assertEquals(8 + 8 + 180 + 2 + 320, d.width);
        assertEquals(new Dimension(500, 200),
                MinimumWindowSize.compute(none, 180, none, new Dimension(320, 200), 150));
    }

    @Test
    void captureDominatesWhenMenuIsShorter() {
        Insets one = new Insets(1, 1, 1, 1);
        Insets none = new Insets(0, 0, 0, 0);
        assertEquals(182, MinimumWindowSize.compute(none, 180, one, new Dimension(320, 180), 182).height);
        assertEquals(182, MinimumWindowSize.compute(none, 180, one, new Dimension(320, 180), 100).height);
    }

    @Test
    void realMenuHeightIsFollowedAndStaysReasonable() {
        MenuPanel menu = new MenuPanel(com.converter.effect.EffectRegistry.defaults());
        Dimension pref = menu.getPreferredSize();
        Insets frame = new Insets(31, 8, 8, 8);
        Dimension d = MinimumWindowSize.compute(frame, pref.width, new Insets(1, 1, 1, 1),
                new Dimension(320, 180), pref.height);
        assertEquals(frame.top + frame.bottom + pref.height, d.height);
        assertEquals(frame.left + frame.right + pref.width + 2 + 320, d.width);
        assertTrue(d.height <= 430, "minimum window height " + d.height);
    }
}
