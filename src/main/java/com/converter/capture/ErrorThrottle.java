package com.converter.capture;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Decides whether a repeated error should be logged.
 *
 * <p>Two limits apply, both driven by the caller-supplied nanoTime (so tests use a fake clock):
 * <ul>
 *   <li>per key (exception class + message): logged at most once per {@code intervalNanos};
 *   <li>overall: at most {@code maxLines} lines per sliding {@code windowNanos}, so ever-changing
 *       messages cannot flood the log.
 * </ul>
 * Last-log times are kept for at most {@code maxKeys} keys (the least recently logged is dropped).
 * Not thread safe (used by the single capture worker).
 */
public final class ErrorThrottle {

    public static final int DEFAULT_MAX_KEYS = 64;
    public static final int DEFAULT_MAX_LINES = 10;
    public static final long DEFAULT_WINDOW_NANOS = 5_000_000_000L;

    private final long intervalNanos;
    private final int maxKeys;
    private final int maxLines;
    private final long windowNanos;
    /** key -> {last logged time, suppressed count}; iteration order = least recently logged first. */
    private final LinkedHashMap<String, long[]> keys = new LinkedHashMap<>();
    private final Deque<Long> recentLogs = new ArrayDeque<>();
    private long suppressed;

    public ErrorThrottle(long intervalNanos) {
        this(intervalNanos, DEFAULT_MAX_KEYS, DEFAULT_MAX_LINES, DEFAULT_WINDOW_NANOS);
    }

    public ErrorThrottle(long intervalNanos, int maxKeys, int maxLines, long windowNanos) {
        if (maxKeys <= 0 || maxLines <= 0) {
            throw new IllegalArgumentException("limits must be positive");
        }
        this.intervalNanos = intervalNanos;
        this.maxKeys = maxKeys;
        this.maxLines = maxLines;
        this.windowNanos = windowNanos;
    }

    /** Key identifying "the same error": type plus message. */
    public static String keyOf(Throwable t) {
        return t.getClass().getName() + ":" + t.getMessage();
    }

    public boolean shouldLog(String key, long now) {
        long[] entry = keys.get(key);
        if (entry != null && now - entry[0] < intervalNanos) {
            entry[1]++;
            suppressed++;
            return false;
        }
        while (!recentLogs.isEmpty() && now - recentLogs.peekFirst() >= windowNanos) {
            recentLogs.pollFirst();
        }
        if (recentLogs.size() >= maxLines) {
            if (entry != null) {
                entry[1]++;
            }
            suppressed++;
            return false;
        }
        recentLogs.addLast(now);
        long carried = entry != null ? entry[1] : 0;
        keys.remove(key); // re-insert so the order is by last log time
        keys.put(key, new long[] {now, carried});
        if (keys.size() > maxKeys) {
            Iterator<Map.Entry<String, long[]>> it = keys.entrySet().iterator();
            it.next();
            it.remove();
        }
        return true;
    }

    /** Returns the total number of suppressed errors (all keys) since the last call and resets it. */
    public long takeSuppressed() {
        long s = suppressed;
        suppressed = 0;
        return s;
    }

    /** Returns the suppressed count of one key since its last call and resets it. */
    public long takeSuppressedFor(String key) {
        long[] entry = keys.get(key);
        if (entry == null) {
            return 0;
        }
        long s = entry[1];
        entry[1] = 0;
        return s;
    }
}
