package com.converter.effect;

/**
 * Immutable-by-convention snapshot of the characters of one processed frame (row-major,
 * {@code cols * rows} cells). Callers must not modify {@code cells}.
 */
public record TextGrid(char[] cells, int cols, int rows) {

    /** Lines joined with {@code '\n'} (no trailing newline), trailing spaces kept; "" if no rows. */
    public String text() {
        if (rows == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(cells.length + rows);
        for (int r = 0; r < rows; r++) {
            if (r > 0) {
                sb.append('\n');
            }
            sb.append(cells, r * cols, cols);
        }
        return sb.toString();
    }
}
