package com.converter.effect;

import java.awt.image.BufferedImage;

/** Returns an exact copy of the input (restores the stage-3 "show original" behaviour). */
public final class IdentityEffect implements Effect {

    @Override
    public BufferedImage apply(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        out.setRGB(0, 0, w, h, src.getRGB(0, 0, w, h, null, 0, w), 0, w);
        return out;
    }
}
