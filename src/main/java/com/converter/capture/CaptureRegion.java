package com.converter.capture;

import java.awt.Insets;
import java.awt.Rectangle;

/** Pure calculation of the capture rectangle from a panel's screen position, size and insets. */
public final class CaptureRegion {

    private CaptureRegion() {
    }

    /**
     * Returns the inner area (panel minus insets) in screen coordinates, or null if it is empty.
     *
     * @param screenX screen x of the panel's top-left corner
     * @param screenY screen y of the panel's top-left corner
     */
    public static Rectangle inner(int screenX, int screenY, int width, int height, Insets insets) {
        int w = width - insets.left - insets.right;
        int h = height - insets.top - insets.bottom;
        if (w <= 0 || h <= 0) {
            return null;
        }
        return new Rectangle(screenX + insets.left, screenY + insets.top, w, h);
    }
}
