package com.converter.effect;

/**
 * Source of ASCII-art glyphs: a fixed cell size and N characters, each with a binary mask of
 * exactly cell size. Implementations must return the glyphs <b>sorted by ink count ascending</b>
 * (index 0 = least ink, normally the blank; index size()-1 = densest), with no two glyphs
 * having the same ink count. Pure data, no UI dependencies.
 */
public interface GlyphSet {

    /** Cell width in pixels (&gt;= 1). */
    int cellWidth();

    /** Cell height in pixels (&gt;= 1). */
    int cellHeight();

    /** Number of glyphs N (&gt;= 1). */
    int size();

    /** The character of glyph {@code index} (0..size()-1). */
    char charAt(int index);

    /**
     * Mask of glyph {@code index}: row-major, length cellWidth()*cellHeight(), true = ink.
     * Callers must not modify the returned array.
     */
    boolean[] mask(int index);
}
