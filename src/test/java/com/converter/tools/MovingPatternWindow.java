package com.converter.tools;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.image.BufferStrategy;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * Measurement tool (not part of the product jar): an undecorated window that redraws moving
 * stripes, shapes and gradients as fast as possible (~120 Hz requested) to imitate video/game
 * content. Usage: {@code MovingPatternWindow [x y w h]}; default is a large centred window.
 * Prints its own render frame count once per second (independent of the overlay metrics).
 */
public final class MovingPatternWindow {

    private MovingPatternWindow() {
    }

    public static void main(String[] args) throws Exception {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        Rectangle b = new Rectangle(screen.width / 10, screen.height / 10,
                screen.width * 8 / 10, screen.height * 8 / 10);
        if (args.length >= 4) {
            b = new Rectangle(Integer.parseInt(args[0]), Integer.parseInt(args[1]),
                    Integer.parseInt(args[2]), Integer.parseInt(args[3]));
        }
        final Rectangle bounds = b;
        JFrame[] holder = new JFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            JFrame f = new JFrame("pattern");
            f.setUndecorated(true);
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setIgnoreRepaint(true);
            f.setBounds(bounds);
            f.setVisible(true);
            f.createBufferStrategy(2);
            holder[0] = f;
        });
        JFrame frame = holder[0];
        BufferStrategy strategy = frame.getBufferStrategy();
        long start = System.nanoTime();
        long frames = 0;
        long lastReport = start;
        long lastReportFrames = 0;
        final long period = 1_000_000_000L / 120;
        long nextFrame = start;
        while (true) {
            double t = (System.nanoTime() - start) / 1e9;
            do {
                Graphics2D g = (Graphics2D) strategy.getDrawGraphics();
                draw(g, bounds.width, bounds.height, t);
                g.dispose();
            } while (strategy.contentsRestored() || strategy.contentsLost());
            strategy.show();
            frames++;
            long now = System.nanoTime();
            if (now - lastReport >= 1_000_000_000L) {
                System.out.printf("[pattern] render fps=%.1f total=%d%n",
                        (frames - lastReportFrames) * 1e9 / (now - lastReport), frames);
                lastReport = now;
                lastReportFrames = frames;
            }
            nextFrame += period;
            long sleep = nextFrame - System.nanoTime();
            if (sleep > 0) {
                Thread.sleep(sleep / 1_000_000L, (int) (sleep % 1_000_000L));
            } else {
                nextFrame = System.nanoTime();
            }
        }
    }

    private static void draw(Graphics2D g, int w, int h, double t) {
        // moving colour stripes
        int stripe = 40;
        int shift = (int) (t * 400);
        for (int x = -stripe * 2; x < w + stripe; x += stripe) {
            int idx = Math.floorMod((x + shift) / stripe, 6);
            float hue = (float) ((idx / 6.0 + t * 0.25) % 1.0);
            g.setColor(Color.getHSBColor(hue, 0.9f, 0.95f));
            g.fillRect(x - (shift % stripe), 0, stripe, h);
        }
        // moving gradient band
        int gy = (int) ((Math.sin(t * 1.7) * 0.5 + 0.5) * (h - 120));
        g.setPaint(new GradientPaint(0, gy, Color.BLACK, w, gy + 120, Color.WHITE));
        g.fillRect(0, gy, w, 120);
        // bouncing shapes
        for (int i = 0; i < 6; i++) {
            double px = (Math.sin(t * (0.9 + i * 0.37) + i) * 0.5 + 0.5) * (w - 140);
            double py = (Math.cos(t * (1.3 + i * 0.29) + i * 2) * 0.5 + 0.5) * (h - 140);
            g.setColor(Color.getHSBColor((float) ((i / 6.0 + t * 0.5) % 1.0), 1f, 1f));
            if (i % 2 == 0) {
                g.fillOval((int) px, (int) py, 140, 140);
            } else {
                g.fillRect((int) px, (int) py, 120, 120);
            }
        }
    }
}
