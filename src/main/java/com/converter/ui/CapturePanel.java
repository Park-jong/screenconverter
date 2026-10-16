package com.converter.ui;

import java.awt.Graphics;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;

/** Panel that only paints the most recent captured image inside its insets; never captures itself. */
public final class CapturePanel extends JPanel {

    private volatile BufferedImage image;

    /** Stores the latest frame. Call on the EDT, then repaint. */
    public void setImage(BufferedImage next) {
        this.image = next;
        repaint();
    }

    /** The image currently shown, or null before the first frame. */
    public BufferedImage image() {
        return image;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g); // fills background (fallback when no image yet)
        BufferedImage img = image;
        if (img == null) {
            return;
        }
        Insets in = getInsets();
        int w = getWidth() - in.left - in.right;
        int h = getHeight() - in.top - in.bottom;
        if (w <= 0 || h <= 0) {
            return;
        }
        g.drawImage(img, in.left, in.top, w, h, null);
    }
}
