package com.converter.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.converter.effect.AsciiArtEffect;
import com.converter.effect.Effect;
import com.converter.effect.IdentityEffect;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Copy uses the text of the frame that is shown, optionally with trailing spaces removed. */
class AsciiCopyShownTextTest {

    private static final class Clip implements TextClipboard {
        final List<String> set = new ArrayList<>();

        @Override
        public boolean setText(String text) {
            set.add(text);
            return true;
        }
    }

    @Test
    void copiesTheShownTextNotTheEffectsNewerText() {
        // The effect has already processed a newer frame ("NEW"), but "OLD" is what is shown.
        Effect e = new AsciiArtEffect();
        String[] shown = {"OLD"};
        Clip clip = new Clip();
        AsciiCopyAction a = new AsciiCopyAction(() -> e, () -> shown[0], () -> false, clip);
        assertTrue(a.copy().success());
        assertEquals(List.of("OLD"), clip.set);
        shown[0] = "NEXT";
        a.copy();
        assertEquals("NEXT", clip.set.get(1));
    }

    @Test
    void emptyShownTextMeansNothingToCopyEvenWithANewEffect() {
        Clip clip = new Clip();
        AsciiCopyAction a = new AsciiCopyAction(AsciiArtEffect::new, () -> "", () -> false, clip);
        AsciiCopyAction.Result r = a.copy();
        assertFalse(r.success());
        assertEquals(AsciiCopyAction.NO_TEXT, r.message());
        assertTrue(clip.set.isEmpty());
    }

    @Test
    void trimOptionIsReadOnEveryCopy() {
        Clip clip = new Clip();
        boolean[] trim = {false};
        AsciiCopyAction a = new AsciiCopyAction(AsciiArtEffect::new, () -> "ab  \n    \nc ",
                () -> trim[0], clip);
        a.copy();
        trim[0] = true;
        AsciiCopyAction.Result r = a.copy();
        assertEquals("ab  \n    \nc ", clip.set.get(0));
        assertEquals("ab\n\nc", clip.set.get(1));
        assertEquals("복사됨 (2x3자)", r.message());
    }

    @Test
    void nonTextEffectStillRefused() {
        Clip clip = new Clip();
        AsciiCopyAction a = new AsciiCopyAction(IdentityEffect::new, () -> "x", () -> true, clip);
        assertEquals(AsciiCopyAction.NOT_AVAILABLE, a.copy().message());
        assertTrue(clip.set.isEmpty());
    }
}
