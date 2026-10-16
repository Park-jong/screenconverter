package com.converter.effect;

import java.awt.image.BufferedImage;

/** A pure image transformation applied to each captured frame (no UI or native dependencies). */
public interface Effect {

    /**
     * Returns a new image of the same size and type {@link BufferedImage#TYPE_INT_RGB}.
     * The input is never modified.
     */
    BufferedImage apply(BufferedImage src);
}
