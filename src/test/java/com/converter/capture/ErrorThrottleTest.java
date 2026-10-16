package com.converter.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ErrorThrottleTest {

    private static final long SEC = 1_000_000_000L;

    @Test
    void firstLoggedThenSuppressedUntilInterval() {
        ErrorThrottle t = new ErrorThrottle(5 * SEC);
        assertTrue(t.shouldLog("a", 0));
        assertFalse(t.shouldLog("a", 1 * SEC));
        assertFalse(t.shouldLog("a", 4 * SEC));
        assertEquals(2, t.takeSuppressed());
        assertEquals(0, t.takeSuppressed());
        assertTrue(t.shouldLog("a", 5 * SEC));
        assertFalse(t.shouldLog("a", 9 * SEC)); // interval counts from the last logged one
        assertTrue(t.shouldLog("a", 10 * SEC));
    }

    @Test
    void differentKeyIsLoggedImmediately() {
        ErrorThrottle t = new ErrorThrottle(5 * SEC);
        assertTrue(t.shouldLog("a", 0));
        assertTrue(t.shouldLog("b", 1));
        assertFalse(t.shouldLog("a", 2)); // per-key memory: a is still within its interval
    }

    @Test
    void keyDistinguishesTypeAndMessage() {
        assertNotEquals(ErrorThrottle.keyOf(new OutOfMemoryError("x")),
                ErrorThrottle.keyOf(new IllegalStateException("x")));
        assertEquals(ErrorThrottle.keyOf(new IllegalStateException("x")),
                ErrorThrottle.keyOf(new IllegalStateException("x")));
    }

    private static final long MS = 1_000_000L;

    @Test
    void alternatingTwoKeysAreEachThrottled() {
        ErrorThrottle t = new ErrorThrottle(5 * SEC);
        assertTrue(t.shouldLog("a", 0));
        assertTrue(t.shouldLog("b", 10 * MS));
        for (int i = 1; i <= 100; i++) {
            assertFalse(t.shouldLog("a", i * 20 * MS));
            assertFalse(t.shouldLog("b", i * 20 * MS + 10 * MS));
        }
        assertEquals(200, t.takeSuppressed());
        assertTrue(t.shouldLog("a", 5 * SEC));
    }

    @Test
    void changingMessageIsCappedByOverallLimit() {
        ErrorThrottle t = new ErrorThrottle(5 * SEC);
        int logged = 0;
        for (int i = 0; i < 1000; i++) {
            if (t.shouldLog("err #" + i, i * 10 * MS)) { // 10s of errors, every 10ms
                logged++;
            }
        }
        // two 5s windows, at most 10 lines each
        assertEquals(20, logged);
        assertEquals(980, t.takeSuppressed());
    }

    @Test
    void sameKeyLoggedAgainAfterInterval() {
        ErrorThrottle t = new ErrorThrottle(5 * SEC);
        assertTrue(t.shouldLog("a", 0));
        assertFalse(t.shouldLog("a", 5 * SEC - 1));
        assertEquals(1, t.takeSuppressedFor("a"));
        assertEquals(0, t.takeSuppressedFor("a"));
        assertTrue(t.shouldLog("a", 5 * SEC));
    }

    @Test
    void keyMapIsBoundedAndOldestKeyIsDropped() {
        ErrorThrottle t = new ErrorThrottle(100 * SEC, 3, 1000, 100 * SEC);
        assertTrue(t.shouldLog("k1", 1));
        assertTrue(t.shouldLog("k2", 2));
        assertTrue(t.shouldLog("k3", 3));
        assertFalse(t.shouldLog("k1", 4)); // still remembered
        assertTrue(t.shouldLog("k4", 5)); // evicts the oldest (k1)
        assertTrue(t.shouldLog("k1", 6)); // forgotten, so logged again
        assertFalse(t.shouldLog("k4", 7));
    }

    @Test
    void overallLimitSlidesWithTime() {
        ErrorThrottle t = new ErrorThrottle(5 * SEC, 64, 2, 5 * SEC);
        assertTrue(t.shouldLog("a", 0));
        assertTrue(t.shouldLog("b", 1 * SEC));
        assertFalse(t.shouldLog("c", 2 * SEC));
        assertTrue(t.shouldLog("c", 5 * SEC)); // the line from t=0 left the window
        assertFalse(t.shouldLog("d", 5 * SEC + 1));
    }
}
