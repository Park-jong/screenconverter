package com.converter.capture;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Locale;

/**
 * Thread-safe per-frame timing recorder (no Swing dependency). The capture worker records
 * attempts, the EDT records displayed frames; any thread may read statistics.
 *
 * <p>All durations are nanoseconds. Per-frame data lives in fixed-size ring buffers, so the
 * statistics describe the most recent {@code capacity} displayed frames.
 */
public final class FrameMetrics {

    /** System property (or env var CONVERTER_METRICS) that enables metrics; off by default. */
    public static final String PROPERTY = "converter.metrics";
    public static final int DEFAULT_CAPACITY = 1024;
    public static final long DEFAULT_WINDOW_NANOS = 5_000_000_000L;

    private final Object lock = new Object();
    private final int capacity;
    private final long[] displayedAt;
    private final long[] interval; // -1 for the very first displayed frame
    private final long[] grab;
    private final long[] effect;
    private final long[] latency;
    private int next; // write index
    private int size; // valid entries
    private long attempts;
    private long displayed;
    private long skipped;
    private long replaced;
    private long lastDisplayedAt;

    public FrameMetrics(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        displayedAt = new long[capacity];
        interval = new long[capacity];
        grab = new long[capacity];
        effect = new long[capacity];
        latency = new long[capacity];
    }

    /** Returns a new instance if metrics are enabled by property/env, otherwise null. */
    public static FrameMetrics fromEnvironment() {
        String v = System.getProperty(PROPERTY);
        if (v == null) {
            v = System.getenv("CONVERTER_METRICS");
        }
        return "true".equalsIgnoreCase(v) ? new FrameMetrics(DEFAULT_CAPACITY) : null;
    }

    /** Worker thread: a capture cycle started (counted even if no frame results). */
    public void recordAttempt() {
        synchronized (lock) {
            attempts++;
        }
    }

    /**
     * EDT: a frame was displayed at {@code displayedAtNanos}.
     *
     * @param workerStartNanos worker timestamp when this frame's capture began
     * @param grabNanos        time spent grabbing
     * @param effectNanos      time spent applying the effect
     */
    public void recordDisplayed(long workerStartNanos, long grabNanos, long effectNanos,
            long displayedAtNanos) {
        synchronized (lock) {
            interval[next] = displayed == 0 ? -1 : displayedAtNanos - lastDisplayedAt;
            displayedAt[next] = displayedAtNanos;
            grab[next] = grabNanos;
            effect[next] = effectNanos;
            latency[next] = displayedAtNanos - workerStartNanos;
            lastDisplayedAt = displayedAtNanos;
            next = (next + 1) % capacity;
            if (size < capacity) {
                size++;
            }
            displayed++;
        }
    }

    /** Worker thread: total frame slots the pacer skipped because capture ran late. */
    public void setSkipped(long total) {
        synchronized (lock) {
            skipped = total;
        }
    }

    /** Worker thread: a finished frame was replaced by a newer one before the EDT showed it. */
    public void recordReplaced() {
        synchronized (lock) {
            replaced++;
        }
    }

    public long attempts() {
        synchronized (lock) {
            return attempts;
        }
    }

    public long displayedTotal() {
        synchronized (lock) {
            return displayed;
        }
    }

    /**
     * Statistics over the retained frames displayed within {@code windowNanos} before
     * {@code nowNanos}. fps = frames in window / min(window, now - first frame in window).
     */
    public Snapshot snapshot(long nowNanos, long windowNanos) {
        long[] iv;
        long[] gr;
        long[] ef;
        long[] la;
        long att;
        long disp;
        long skip;
        long repl;
        long first = 0;
        int count = 0;
        synchronized (lock) {
            att = attempts;
            disp = displayed;
            skip = skipped;
            repl = replaced;
            iv = new long[size];
            gr = new long[size];
            ef = new long[size];
            la = new long[size];
            int ivCount = 0;
            for (int i = 0; i < size; i++) {
                int idx = Math.floorMod(next - size + i, capacity); // oldest -> newest
                if (displayedAt[idx] < nowNanos - windowNanos) {
                    continue;
                }
                if (count == 0) {
                    first = displayedAt[idx];
                }
                gr[count] = grab[idx];
                ef[count] = effect[idx];
                la[count] = latency[idx];
                count++;
                if (interval[idx] >= 0) {
                    iv[ivCount++] = interval[idx];
                }
            }
            iv = Arrays.copyOf(iv, ivCount);
        }
        gr = Arrays.copyOf(gr, count);
        ef = Arrays.copyOf(ef, count);
        la = Arrays.copyOf(la, count);
        double fps = 0;
        if (count > 0) {
            long span = Math.min(windowNanos, nowNanos - first);
            if (span > 0) {
                fps = count * 1e9 / span;
            }
        }
        return new Snapshot(fps, count, att, disp, skip, repl, Stat.of(iv), Stat.of(gr),
                Stat.of(ef), Stat.of(la));
    }

    /** Mean, nearest-rank p95 and max of a sample, in nanoseconds. */
    public record Stat(long count, double avg, long p95, long max) {
        static final Stat EMPTY = new Stat(0, 0, 0, 0);

        public static Stat of(long[] values) {
            if (values.length == 0) {
                return EMPTY;
            }
            long[] sorted = values.clone();
            Arrays.sort(sorted);
            return new Stat(sorted.length, mean(sorted), percentile(sorted, 95),
                    sorted[sorted.length - 1]);
        }
    }

    /** Arithmetic mean; 0 for an empty array. */
    public static double mean(long[] values) {
        if (values.length == 0) {
            return 0;
        }
        double sum = 0;
        for (long v : values) {
            sum += v;
        }
        return sum / values.length;
    }

    /** Nearest-rank percentile of an ascending-sorted array: element at ceil(p/100*n)-1. */
    public static long percentile(long[] sorted, double p) {
        if (sorted.length == 0) {
            return 0;
        }
        int rank = (int) Math.ceil(p / 100.0 * sorted.length);
        return sorted[Math.max(1, Math.min(sorted.length, rank)) - 1];
    }

    /** Immutable statistics at one point in time. */
    public record Snapshot(double fps, int frames, long attempts, long displayed, long skipped,
            long replaced, Stat interval,
            Stat grab, Stat effect, Stat latency) {

        /** One summary line; times in milliseconds. */
        public String format() {
            return String.format(Locale.ROOT,
                    "[metrics] fps=%.1f interval(ms) avg=%.1f p95=%.1f max=%.1f"
                            + " grab(ms) avg=%.1f p95=%.1f effect(ms) avg=%.1f p95=%.1f"
                            + " latency(ms) avg=%.1f p95=%.1f attempts=%d displayed=%d skipped=%d replaced=%d",
                    fps, ms(interval.avg()), ms(interval.p95()), ms(interval.max()),
                    ms(grab.avg()), ms(grab.p95()), ms(effect.avg()), ms(effect.p95()),
                    ms(latency.avg()), ms(latency.p95()), attempts, displayed, skipped, replaced);
        }

        private static double ms(double nanos) {
            return nanos / 1e6;
        }
    }

    /** Starts a daemon thread printing {@link Snapshot#format()} to {@code out} every period. */
    public void startReporter(PrintStream out, long periodMillis) {
        Thread t = new Thread(() -> {
            try {
                while (true) {
                    Thread.sleep(periodMillis);
                    out.println(snapshot(System.nanoTime(), DEFAULT_WINDOW_NANOS).format());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "frame-metrics-reporter");
        t.setDaemon(true);
        t.start();
    }
}
