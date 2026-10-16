package com.converter.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.converter.effect.Effect;
import com.converter.effect.IdentityEffect;
import com.converter.effect.MosaicEffect;
import com.converter.effect.TextProducingEffect;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class AsciiCopyActionTest {

    private static final class FakeTextEffect implements Effect, TextProducingEffect {
        String text;

        FakeTextEffect(String text) {
            this.text = text;
        }

        @Override
        public BufferedImage apply(BufferedImage src) {
            return src;
        }

        @Override
        public String lastText() {
            return text;
        }
    }

    private static final class FakeClipboard implements TextClipboard {
        final List<String> set = new ArrayList<>();
        boolean ok = true;
        boolean throwing;

        @Override
        public boolean setText(String text) {
            if (throwing) {
                throw new IllegalStateException("busy");
            }
            set.add(text);
            return ok;
        }
    }

    @Test
    void successCopiesTextAndReportsSize() {
        FakeClipboard cb = new FakeClipboard();
        String text = "ab \n  c\n   ";
        AsciiCopyAction.Result r = new AsciiCopyAction(() -> new FakeTextEffect(text), cb).copy();
        assertTrue(r.success());
        assertEquals("복사됨 (3x3자)", r.message());
        assertEquals(List.of(text), cb.set);
    }

    @Test
    void largeGridReportsColumnsAndRows() {
        String row = " ".repeat(217) + "#";
        String text = String.join("\n", Collections.nCopies(63, row));
        FakeClipboard cb = new FakeClipboard();
        AsciiCopyAction.Result r = new AsciiCopyAction(() -> new FakeTextEffect(text), cb).copy();
        assertEquals("복사됨 (218x63자)", r.message());
        assertEquals(text, cb.set.get(0));
    }

    @Test
    void nonTextEffectsAreRejectedWithoutTouchingClipboard() {
        FakeClipboard cb = new FakeClipboard();
        for (Effect e : new Effect[] {new MosaicEffect(8), new IdentityEffect()}) {
            AsciiCopyAction.Result r = new AsciiCopyAction(() -> e, cb).copy();
            assertFalse(r.success());
            assertEquals(AsciiCopyAction.NOT_AVAILABLE, r.message());
        }
        assertTrue(cb.set.isEmpty());
    }

    @Test
    void emptyOrNullTextIsRejected() {
        FakeClipboard cb = new FakeClipboard();
        assertEquals(AsciiCopyAction.NO_TEXT,
                new AsciiCopyAction(() -> new FakeTextEffect(""), cb).copy().message());
        assertEquals(AsciiCopyAction.NO_TEXT,
                new AsciiCopyAction(() -> new FakeTextEffect(null), cb).copy().message());
        assertTrue(cb.set.isEmpty());
    }

    @Test
    void clipboardFailureOrExceptionGivesFailureMessage() {
        FakeClipboard cb = new FakeClipboard();
        cb.ok = false;
        AsciiCopyAction a = new AsciiCopyAction(() -> new FakeTextEffect("x"), cb);
        assertEquals(new AsciiCopyAction.Result(false, AsciiCopyAction.CLIPBOARD_FAILED), a.copy());
        cb.throwing = true;
        assertEquals(new AsciiCopyAction.Result(false, AsciiCopyAction.CLIPBOARD_FAILED), a.copy());
    }

    @Test
    void usesTheTextCurrentAtCopyTime() {
        FakeClipboard cb = new FakeClipboard();
        FakeTextEffect e = new FakeTextEffect("old");
        AsciiCopyAction a = new AsciiCopyAction(() -> e, cb);
        e.text = "new";
        a.copy();
        assertEquals(List.of("new"), cb.set);
    }

    @Test
    void linesAndColumns() {
        assertEquals(1, AsciiCopyAction.lines("abc"));
        assertEquals(3, AsciiCopyAction.lines("a\n\nb"));
        assertEquals(3, AsciiCopyAction.columns("a\n\nabc"));
    }
}
