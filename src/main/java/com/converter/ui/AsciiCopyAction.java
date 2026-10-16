package com.converter.ui;

import com.converter.effect.Effect;
import com.converter.effect.TextProducingEffect;
import com.converter.effect.TextTrim;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** UI-independent copy logic: current effect -> its text -> clipboard -> status message. */
public final class AsciiCopyAction {

    static final String NOT_AVAILABLE = "ASCII 아트(9번)에서 사용";
    static final String NO_TEXT = "복사할 내용 없음";
    static final String CLIPBOARD_FAILED = "복사 실패 (클립보드 사용 불가)";

    /** Outcome of one copy attempt; {@code message} is meant to be shown to the user. */
    public record Result(boolean success, String message) {
    }

    private final Supplier<Effect> currentEffect;
    /** Text to copy, or null to use the current effect's latest text. */
    private final Supplier<String> textSource;
    private final BooleanSupplier trimTrailing;
    private final TextClipboard clipboard;

    /** Copies the current effect's latest text unchanged. */
    public AsciiCopyAction(Supplier<Effect> currentEffect, TextClipboard clipboard) {
        this(currentEffect, null, () -> false, clipboard);
    }

    /**
     * @param textSource   text of the frame that is currently shown (null: the effect's own latest)
     * @param trimTrailing whether to strip trailing spaces of every line, read on each copy
     */
    public AsciiCopyAction(Supplier<Effect> currentEffect, Supplier<String> textSource,
            BooleanSupplier trimTrailing, TextClipboard clipboard) {
        this.currentEffect = Objects.requireNonNull(currentEffect, "currentEffect");
        this.textSource = textSource;
        this.trimTrailing = Objects.requireNonNull(trimTrailing, "trimTrailing");
        this.clipboard = Objects.requireNonNull(clipboard, "clipboard");
    }

    public Result copy() {
        Effect e = currentEffect.get();
        if (!(e instanceof TextProducingEffect t)) {
            return new Result(false, NOT_AVAILABLE);
        }
        String text = textSource != null ? textSource.get() : t.lastText();
        if (text != null && trimTrailing.getAsBoolean()) {
            text = TextTrim.trimLineEnds(text);
        }
        if (text == null || text.isEmpty()) {
            return new Result(false, NO_TEXT);
        }
        boolean ok;
        try {
            ok = clipboard.setText(text);
        } catch (RuntimeException ex) {
            ok = false;
        }
        if (!ok) {
            return new Result(false, CLIPBOARD_FAILED);
        }
        return new Result(true, "복사됨 (" + columns(text) + "x" + lines(text) + "자)");
    }

    /** Number of lines (rows) in the text. */
    static int lines(String text) {
        int n = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }

    /** Length of the longest line (columns). */
    static int columns(String text) {
        int max = 0;
        int start = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == '\n') {
                max = Math.max(max, i - start);
                start = i + 1;
            }
        }
        return max;
    }
}
