package com.converter.ui;

import java.awt.image.BufferedImage;

/**
 * Pause state machine shared by the UI thread and the capture worker (no clock, no AWT
 * components). While paused the worker does not capture; it keeps the last <b>source</b> frame
 * and, whenever the effect or its options changed (dirty), applies the effect to that frame again.
 *
 * <ul>
 *   <li>The worker reports every grabbed frame with {@link #recordSource}.</li>
 *   <li>{@link #pause()} holds the newest source frame and marks it dirty (so the display and
 *       its text belong to that exact frame). It does nothing before the first frame.</li>
 *   <li>{@link #invalidate()} (effect or option changed) marks dirty; only matters while paused.</li>
 *   <li>{@link #resume()} releases the held frame; used for the Space toggle and when the window
 *       moves or is resized (the held frame would no longer match the area under the window).</li>
 *   <li>The worker asks {@link #poll()} (non-blocking) or {@link #awaitStep()} (blocks with
 *       wait/notify, no polling) what to do next.</li>
 * </ul>
 */
final class PauseState {

    /** Appended to the window title while paused. */
    static final String TITLE_SUFFIX = " [일시정지]";

    /** What the worker does next. */
    enum Kind { CAPTURE, REAPPLY }

    /** Next worker step; {@code source} is the held frame for REAPPLY, otherwise null. */
    record Step(Kind kind, BufferedImage source) {
        boolean reapply() {
            return kind == Kind.REAPPLY;
        }
    }

    private static final Step CAPTURE = new Step(Kind.CAPTURE, null);

    private boolean paused;
    private BufferedImage lastSource;
    private BufferedImage held;
    private boolean dirty;

    synchronized boolean isPaused() {
        return paused;
    }

    /**
     * Worker: a frame was grabbed. Returns true if it should be processed and shown normally.
     * While paused it replaces the held frame instead (a frame that finished after the pause
     * request is the newest one) and returns false.
     */
    synchronized boolean recordSource(BufferedImage img) {
        lastSource = img;
        if (paused) {
            held = img;
            dirty = true;
            notifyAll();
            return false;
        }
        return true;
    }

    /** Starts the pause with the newest source frame; false if already paused or no frame yet. */
    synchronized boolean pause() {
        if (paused || lastSource == null) {
            return false;
        }
        paused = true;
        held = lastSource;
        dirty = true;
        notifyAll();
        return true;
    }

    /** Ends the pause and drops the held frame; false if it was not paused. */
    synchronized boolean resume() {
        if (!paused) {
            return false;
        }
        paused = false;
        held = null;
        dirty = false;
        notifyAll();
        return true;
    }

    /** Effect or option changed: re-apply to the held frame (no-op unless paused). */
    synchronized void invalidate() {
        if (paused) {
            dirty = true;
            notifyAll();
        }
    }

    /** Non-blocking: the next step, or null if the worker should wait (paused and clean). */
    synchronized Step poll() {
        if (!paused) {
            return CAPTURE;
        }
        if (dirty && held != null) {
            dirty = false;
            return new Step(Kind.REAPPLY, held);
        }
        return null;
    }

    /** Blocks (no polling) until there is something to do. */
    synchronized Step awaitStep() throws InterruptedException {
        Step s;
        while ((s = poll()) == null) {
            wait();
        }
        return s;
    }
}
