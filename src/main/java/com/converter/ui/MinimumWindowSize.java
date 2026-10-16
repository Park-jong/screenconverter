package com.converter.ui;

import java.awt.Dimension;
import java.awt.Insets;

/** Pure calculation of the smallest outer window size that still leaves a target capture area. */
public final class MinimumWindowSize {

    private MinimumWindowSize() {
    }

    /**
     * @param frameInsets   window decoration insets (OS border and title bar)
     * @param menuWidth     width of the side menu panel
     * @param captureInsets border/insets of the capture panel
     * @param targetCapture desired inner capture area size
     * @param menuHeight    preferred height of the side menu panel (must fit without clipping)
     * @return outer window size needed to fit all of the above
     */
    public static Dimension compute(Insets frameInsets, int menuWidth, Insets captureInsets,
            Dimension targetCapture, int menuHeight) {
        int w = frameInsets.left + frameInsets.right
                + menuWidth
                + captureInsets.left + captureInsets.right
                + targetCapture.width;
        int captureHeight = captureInsets.top + captureInsets.bottom + targetCapture.height;
        int h = frameInsets.top + frameInsets.bottom + Math.max(captureHeight, menuHeight);
        return new Dimension(w, h);
    }
}
