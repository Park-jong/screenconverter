package com.converter.capture;

/**
 * Pure frame-pacing logic (no threads, no clock): the caller passes nanoTime values.
 *
 * <p>The pacer keeps {@code nextFrameTime}. After a frame is done at {@code now}, the next frame is
 * due one period after the previous due time. If that moment has not come yet, the caller waits
 * only the remainder. If it already passed (the frame was slow), there is no wait and the schedule
 * is re-based on {@code now}, so late frames are skipped rather than run back to back.
 */
public final class FramePacer {

    public static final String PROPERTY = "converter.fps";
    public static final int DEFAULT_FPS = 30;
    public static final int MIN_FPS = 1;
    public static final int MAX_FPS = 240;

    private final long periodNanos; // 0 = unlimited
    private long nextFrameTime;
    private long skipped;

    /** @param periodNanos target frame period; 0 or less means no limit (never waits) */
    public FramePacer(long periodNanos) {
        this.periodNanos = Math.max(0, periodNanos);
    }

    /** Pacer for the {@code converter.fps} system property. */
    public static FramePacer fromSystemProperty() {
        return new FramePacer(periodNanosFor(System.getProperty(PROPERTY)));
    }

    /**
     * Period for a property value: null/invalid gives the default, "0" or "max" gives 0
     * (unlimited), numbers are clamped to 1..240 fps.
     */
    public static long periodNanosFor(String value) {
        int fps = DEFAULT_FPS;
        if (value != null) {
            String v = value.trim();
            if (v.equalsIgnoreCase("max") || v.equals("0")) {
                return 0;
            }
            try {
                fps = Math.max(MIN_FPS, Math.min(MAX_FPS, Integer.parseInt(v)));
            } catch (NumberFormatException e) {
                fps = DEFAULT_FPS;
            }
        }
        return 1_000_000_000L / fps;
    }

    public long periodNanos() {
        return periodNanos;
    }

    /** Marks the start of the first frame at {@code now}. */
    public void start(long now) {
        nextFrameTime = now;
        skipped = 0;
    }

    /**
     * Called when a frame finished at {@code now}.
     *
     * @return nanoseconds to wait before starting the next frame (0 = start immediately)
     */
    public long afterFrame(long now) {
        if (periodNanos == 0) {
            nextFrameTime = now;
            return 0;
        }
        long due = nextFrameTime + periodNanos;
        if (due > now) {
            nextFrameTime = due;
            return due - now;
        }
        skipped += (now - due) / periodNanos; // whole slots that passed without a frame
        nextFrameTime = now;
        return 0;
    }

    /** Spin margin: the last part of a wait is spun because Windows rounds sleeps up to ~15.6ms
     * ticks; 2ms leaves room for a 1ms-resolution sleep overshoot (measured in 5-3). */
    public static final long SPIN_MARGIN_NANOS = 2_000_000L;

    /** Minimum pause after a failed or impossible (captureRect null) capture so the worker never
     * busy-loops, even in unlimited (max) mode. */
    public static final long ERROR_BACKOFF_NANOS = 10_000_000L;

    /** How to wait: sleep {@code sleepMillis}, then spin for the rest of the remaining time. */
    public record SleepPlan(long sleepMillis, long spinNanos) {
    }

    /**
     * Splits a remaining time into a sleep and a spin. Sleep covers (remaining - 2ms) in whole
     * ms; a sleep that is a multiple of 10ms is reduced by 1 because JDK 17 on Windows rounds
     * such sleeps up to the next 15.6ms tick (measured in 5-3). Remaining time of 2ms or less
     * (or negative) is spin only.
     */
    public static SleepPlan plan(long remainingNanos) {
        if (remainingNanos <= 0) {
            return new SleepPlan(0, 0);
        }
        long ms = (remainingNanos - SPIN_MARGIN_NANOS) / 1_000_000L;
        if (ms <= 0) {
            return new SleepPlan(0, remainingNanos);
        }
        if (ms % 10 == 0) {
            ms--;
        }
        return new SleepPlan(ms, remainingNanos - ms * 1_000_000L);
    }

    /** Wait time for the next attempt: at least the error backoff if the last attempt failed. */
    public static long withBackoff(long waitNanos, boolean attemptFailed) {
        return attemptFailed ? Math.max(waitNanos, ERROR_BACKOFF_NANOS) : Math.max(0, waitNanos);
    }

    /**
     * Waits until {@code deadlineNanos} (System.nanoTime based) using {@link #plan}.
     *
     * @throws InterruptedException if interrupted while sleeping or spinning
     */
    public static void awaitUntil(long deadlineNanos) throws InterruptedException {
        SleepPlan p = plan(deadlineNanos - System.nanoTime());
        if (p.sleepMillis() > 0) {
            Thread.sleep(p.sleepMillis());
        }
        while (deadlineNanos - System.nanoTime() > 0) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            Thread.onSpinWait();
        }
    }

    public long nextFrameTime() {
        return nextFrameTime;
    }

    /** Whole frame slots missed because frames took longer than the period. */
    public long skipped() {
        return skipped;
    }
}
