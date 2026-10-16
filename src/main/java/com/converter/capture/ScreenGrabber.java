package com.converter.capture;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/** Grabs a screen region; implementations can be swapped (e.g. window-excluding capture in stage 3). */
public interface ScreenGrabber {

    /**
     * Captures the given region in screen coordinates.
     *
     * @return the captured image, or null if the region is empty or capture failed
     */
    BufferedImage grab(Rectangle screenRect);
}
