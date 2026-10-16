package com.converter.win32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AffinityFlagsTest {

    @Test
    void constantsMatchWin32() {
        assertEquals(0x0, AffinityFlags.WDA_NONE);
        assertEquals(0x1, AffinityFlags.WDA_MONITOR);
        assertEquals(0x11, AffinityFlags.WDA_EXCLUDEFROMCAPTURE);
    }

    @Test
    void onlyExcludeFromCaptureCountsAsExcluded() {
        assertTrue(AffinityFlags.isExcluded(0x11));
        assertFalse(AffinityFlags.isExcluded(AffinityFlags.WDA_NONE));
        assertFalse(AffinityFlags.isExcluded(AffinityFlags.WDA_MONITOR));
    }

    @Test
    void nullWindowIsFailureWithoutException() {
        assertFalse(new WindowsDisplayAffinity().excludeFromCapture(null));
    }
}
