package com.converter.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.Insets;
import java.awt.Rectangle;
import org.junit.jupiter.api.Test;

class CaptureRegionTest {

    @Test
    void innerExcludesInsets() {
        Rectangle r = CaptureRegion.inner(100, 200, 400, 300, new Insets(1, 2, 3, 4));
        assertEquals(new Rectangle(102, 201, 394, 296), r);
    }

    @Test
    void innerWithNoInsetsIsWholePanel() {
        assertEquals(new Rectangle(-10, 5, 60, 60),
                CaptureRegion.inner(-10, 5, 60, 60, new Insets(0, 0, 0, 0)));
    }

    @Test
    void emptyInnerReturnsNull() {
        assertNull(CaptureRegion.inner(0, 0, 2, 100, new Insets(1, 1, 1, 1)));
        assertNull(CaptureRegion.inner(0, 0, 100, 2, new Insets(1, 1, 1, 1)));
        assertNull(CaptureRegion.inner(0, 0, 0, 0, new Insets(0, 0, 0, 0)));
    }
}
