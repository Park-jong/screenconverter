package com.converter.effect;

/** An effect that can also describe its most recent output as text (e.g. ASCII art). */
public interface TextProducingEffect {

    /**
     * The character grid of the most recently processed frame: one line per cell row joined with
     * {@code '\n'} (no trailing newline), trailing spaces preserved. Empty string if no frame has
     * been processed yet. Safe to call from any thread.
     */
    String lastText();

    /**
     * Character grid of the most recently processed frame, or null if none (or unsupported).
     * The window reads it right after {@code apply} to publish the text together with the frame's
     * image. Safe to call from any thread; the result is never modified afterwards.
     */
    default TextGrid lastGrid() {
        return null;
    }
}
