package com.converter;

import com.converter.ui.OverlayWindow;
import com.converter.win32.DisplayAffinity;
import com.converter.win32.WindowsDisplayAffinity;
import javax.swing.SwingUtilities;

/** Application entry point: shows the overlay window on the EDT. */
public final class Main {

    private Main() {
    }

    /**
     * Must run before any Swing/AWT class loads. Fixes stale menu repaint after widening the window
     * (see agent-summaries/창-폭-확대-후-메뉴-상태-오류-조사.md). An explicit user value is kept.
     */
    static void configureRendering() {
        if (System.getProperty("swing.bufferPerWindow") == null) {
            System.setProperty("swing.bufferPerWindow", "true");
        }
    }

    public static void main(String[] args) {
        configureRendering();
        SwingUtilities.invokeLater(() -> {
            OverlayWindow window = new OverlayWindow();
            window.selectEffect(2); // registry number 2 = mosaic (default); also sets the title
            DisplayAffinity affinity = new WindowsDisplayAffinity();
            // Runs inside onShown(), after the window is displayable and before capture starts.
            window.setShownHook(
                    () -> window.setCaptureExcluded(affinity.excludeFromCapture(window)));
            applyBoundsProperty(window);
            window.setVisible(true);
            window.onShown(); // applies min size, runs hook, then starts capture
        });
    }

    /** Optional "-Dconverter.bounds=x,y,w,h" for measurement tooling; absent means unchanged. */
    private static void applyBoundsProperty(OverlayWindow window) {
        String v = System.getProperty("converter.bounds");
        if (v == null) {
            return;
        }
        String[] p = v.split(",");
        if (p.length == 4) {
            window.setBounds(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()),
                    Integer.parseInt(p[2].trim()), Integer.parseInt(p[3].trim()));
        }
    }
}
