package com.converter.effect;

/** Pure text helpers for the copied ASCII text. */
public final class TextTrim {

    private TextTrim() {
    }

    /**
     * Removes the spaces at the end of every line ({@code '\n'} separated). A line made only of
     * spaces becomes an empty line (it is kept); the text is otherwise unchanged. Null gives "".
     */
    public static String trimLineEnds(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        int start = 0;
        while (true) {
            int nl = text.indexOf('\n', start);
            int end = nl < 0 ? text.length() : nl;
            int e = end;
            while (e > start && text.charAt(e - 1) == ' ') {
                e--;
            }
            sb.append(text, start, e);
            if (nl < 0) {
                break;
            }
            sb.append('\n');
            start = nl + 1;
        }
        return sb.toString();
    }
}
