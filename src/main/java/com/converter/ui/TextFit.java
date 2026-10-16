package com.converter.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** Pure helper that shortens text to a pixel width, keeping the start and ending with "…". */
final class TextFit {
    static final String ELLIPSIS = "…";

    private TextFit() {
    }

    /**
     * Returns {@code text} unchanged if it fits in {@code maxPx}; otherwise the longest prefix
     * (cut on grapheme-ish boundaries: never inside a surrogate pair or before a combining mark /
     * joiner) followed by "…" whose width is at most {@code maxPx}. If even "…" alone is wider than
     * {@code maxPx}, "…" is returned as the minimum. Null becomes "".
     */
    static String fit(String text, int maxPx, ToIntFunction<String> widthOf) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (widthOf.applyAsInt(text) <= maxPx) {
            return text;
        }
        List<Integer> ends = clusterEnds(text);
        int lo = 0; // number of clusters kept; 0 always acceptable (ellipsis only)
        int hi = ends.size() - 1; // keeping all clusters + "…" never fits (text itself did not)
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            String candidate = text.substring(0, ends.get(mid - 1)) + ELLIPSIS;
            if (widthOf.applyAsInt(candidate) <= maxPx) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo == 0 ? ELLIPSIS : text.substring(0, ends.get(lo - 1)) + ELLIPSIS;
    }

    /** End offsets of each cluster: a code point plus following marks, joiners and their targets. */
    private static List<Integer> clusterEnds(String s) {
        List<Integer> ends = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            while (i < s.length()) {
                int next = s.codePointAt(i);
                if (next == 0x200D && i + Character.charCount(next) < s.length()) {
                    i += Character.charCount(next);
                    i += Character.charCount(s.codePointAt(i));
                } else if (isExtender(next)) {
                    i += Character.charCount(next);
                } else {
                    break;
                }
            }
            ends.add(i);
        }
        return ends;
    }

    private static boolean isExtender(int cp) {
        int t = Character.getType(cp);
        return t == Character.NON_SPACING_MARK || t == Character.ENCLOSING_MARK
                || t == Character.COMBINING_SPACING_MARK || cp == 0x200D
                || (cp >= 0x1F3FB && cp <= 0x1F3FF);
    }
}
