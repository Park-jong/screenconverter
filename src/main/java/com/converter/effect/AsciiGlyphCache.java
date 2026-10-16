package com.converter.effect;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe cache of {@link FontGlyphSet}s per cell size. Building one takes about 90 ms, so
 * callers on the UI thread should check {@link #isReady} first and build on a background thread.
 */
public final class AsciiGlyphCache {

    /** Selectable cell sizes {width, height}; the first is the default. */
    public static final List<int[]> CELL_SIZES = List.of(
            new int[] {FontGlyphSet.DEFAULT_CELL_WIDTH, FontGlyphSet.DEFAULT_CELL_HEIGHT},
            new int[] {6, 12},
            new int[] {4, 8});

    private final Map<Long, GlyphSet> cache = new ConcurrentHashMap<>();

    private static long key(int w, int h) {
        return ((long) w << 32) | (h & 0xFFFFFFFFL);
    }

    /** True if the glyph set for this cell size is already built. */
    public boolean isReady(int w, int h) {
        return cache.containsKey(key(w, h));
    }

    /** The glyph set for this cell size, built on first use (blocks while building). */
    public GlyphSet get(int w, int h) {
        GlyphSet g = cache.get(key(w, h));
        if (g != null) {
            return g;
        }
        synchronized (this) {
            g = cache.get(key(w, h));
            if (g == null) {
                g = new FontGlyphSet(w, h, FontGlyphSet.DEFAULT_CHARS);
                cache.put(key(w, h), g);
            }
            return g;
        }
    }

    /** Builds every selectable cell size. */
    public void prefetchAll() {
        for (int[] size : CELL_SIZES) {
            get(size[0], size[1]);
        }
    }
}
