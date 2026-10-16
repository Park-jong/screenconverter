package com.converter.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TextTrimTest {

    @Test
    void trimsTrailingSpacesOfEveryLine() {
        assertEquals("ab\n.-\n#", TextTrim.trimLineEnds("ab  \n.-  \n#  "));
    }

    @Test
    void allSpaceLineBecomesEmptyLineAndIsKept() {
        assertEquals("a\n\nb", TextTrim.trimLineEnds("a\n     \nb"));
        assertEquals("\n\n", TextTrim.trimLineEnds("  \n \n"));
    }

    @Test
    void linesWithoutTrailingSpacesAndLeadingSpacesStay() {
        assertEquals("  ab\ncd", TextTrim.trimLineEnds("  ab\ncd"));
        assertEquals("a b\n c", TextTrim.trimLineEnds("a b  \n c"));
    }

    @Test
    void emptyAndNull() {
        assertEquals("", TextTrim.trimLineEnds(""));
        assertEquals("", TextTrim.trimLineEnds(null));
        assertEquals("", TextTrim.trimLineEnds("    "));
    }

    @Test
    void lastLineWithoutNewlineIsTrimmed() {
        assertEquals("x\ny", TextTrim.trimLineEnds("x\ny   "));
        assertEquals("x\n", TextTrim.trimLineEnds("x  \n"));
    }

    @Test
    void gridTextRoundTripWithTrim() {
        TextGrid g = new TextGrid("#  -  ".toCharArray(), 3, 2);
        assertEquals("#  \n-  ", g.text());
        assertEquals("#\n-", TextTrim.trimLineEnds(g.text()));
        assertEquals("", new TextGrid(new char[0], 0, 0).text());
    }
}
