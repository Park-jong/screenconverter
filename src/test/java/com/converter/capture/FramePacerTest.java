package com.converter.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FramePacerTest {

    private static final long MS = 1_000_000L;

    @Test
    void earlyFinishWaitsOnlyTheRemainder() {
        FramePacer p = new FramePacer(33 * MS);
        p.start(0);
        assertEquals(23 * MS, p.afterFrame(10 * MS)); // due at 33, now 10
        assertEquals(33 * MS, p.nextFrameTime());
        // next frame starts at 33 and ends at 50: due at 66
        assertEquals(16 * MS, p.afterFrame(50 * MS));
        assertEquals(0, p.skipped());
    }

    @Test
    void lateFrameDoesNotWaitAndRebasesOnNow() {
        FramePacer p = new FramePacer(33 * MS);
        p.start(0);
        assertEquals(0, p.afterFrame(40 * MS)); // due 33, late by 7
        assertEquals(40 * MS, p.nextFrameTime());
        assertEquals(0, p.skipped()); // no whole slot missed
        // next due is 40 + 33 = 73
        assertEquals(23 * MS, p.afterFrame(50 * MS));
    }

    @Test
    void repeatedLatenessNeverBursts() {
        FramePacer p = new FramePacer(10 * MS);
        p.start(0);
        long now = 0;
        for (int i = 0; i < 5; i++) {
            now += 25 * MS; // every frame takes 25 ms
            assertEquals(0, p.afterFrame(now));
            assertEquals(now, p.nextFrameTime());
        }
        // after the slow phase one fast frame waits a full period minus its work, not zero-catchup
        assertEquals(6 * MS, p.afterFrame(now + 4 * MS));
    }

    @Test
    void skippedCountsWholeMissedSlots() {
        FramePacer p = new FramePacer(10 * MS);
        p.start(0);
        p.afterFrame(35 * MS); // due 10; slots 10,20,30 passed -> 2 whole slots after the due one
        assertEquals(2, p.skipped());
    }

    @Test
    void unlimitedNeverWaits() {
        FramePacer p = new FramePacer(0);
        p.start(0);
        assertEquals(0, p.afterFrame(1));
        assertEquals(0, p.afterFrame(2));
        assertEquals(0, p.skipped());
    }

    @Test
    void propertyParsingAndClamp() {
        long s = 1_000_000_000L;
        assertEquals(s / 30, FramePacer.periodNanosFor(null));
        assertEquals(s / 60, FramePacer.periodNanosFor("60"));
        assertEquals(s / 60, FramePacer.periodNanosFor(" 60 "));
        assertEquals(s / 240, FramePacer.periodNanosFor("1000"));
        assertEquals(s / 1, FramePacer.periodNanosFor("-5"));
        assertEquals(0, FramePacer.periodNanosFor("0"));
        assertEquals(0, FramePacer.periodNanosFor("max"));
        assertEquals(0, FramePacer.periodNanosFor("MAX"));
        assertEquals(s / 30, FramePacer.periodNanosFor("abc"));
        assertEquals(s / 30, FramePacer.periodNanosFor(""));
        assertEquals(s / 30, FramePacer.periodNanosFor("12.5"));
    }

    private static void assertPlan(long sleepMs, long spinNanos, long remainingNanos) {
        FramePacer.SleepPlan p = FramePacer.plan(remainingNanos);
        assertEquals(sleepMs, p.sleepMillis(), "sleep for " + remainingNanos);
        assertEquals(spinNanos, p.spinNanos(), "spin for " + remainingNanos);
    }

    @Test
    void planSplitsIntoSleepAndSpin() {
        assertPlan(24, 2_200_000L, 26_200_000L); // 26.2 - 2 = 24.2 -> sleep 24, spin 2.2
        assertPlan(1, 2_500_000L, 3_500_000L);
        assertPlan(2, 2_000_000L, 4_000_000L);
    }

    @Test
    void planAvoidsMultiplesOfTenMillis() {
        assertPlan(9, 3 * MS, 12 * MS); // 12 - 2 = 10 -> 9
        assertPlan(19, 2_500_000L, 21_500_000L); // 19.5 -> 19 (not a multiple of 10)
        assertPlan(19, 3_000_000L, 22 * MS); // 20 -> 19
        assertPlan(29, 2_000_000L + 1_000_000L, 32 * MS); // 30 -> 29
        assertPlan(31, 2 * MS, 33 * MS);
    }

    @Test
    void planSpinsOnlyForSmallOrNonPositiveRemainders() {
        assertPlan(0, 0, 0);
        assertPlan(0, 0, -5 * MS);
        assertPlan(0, 1, 1);
        assertPlan(0, 2 * MS, 2 * MS); // boundary: exactly the margin
        assertPlan(0, 2 * MS + 999_999L, 2 * MS + 999_999L); // under 1ms of sleep
        assertPlan(1, 2 * MS, 3 * MS); // first value that sleeps
    }

    @Test
    void planNeverSleepsMultipleOfTenAndKeepsTotal() {
        for (long r = 1; r <= 100 * MS; r += 37_000L) {
            FramePacer.SleepPlan p = FramePacer.plan(r);
            assertEquals(r, p.sleepMillis() * MS + p.spinNanos());
            if (p.sleepMillis() > 0) {
                org.junit.jupiter.api.Assertions.assertNotEquals(0, p.sleepMillis() % 10);
            }
        }
    }

    @Test
    void backoffAppliesOnlyAfterFailure() {
        assertEquals(0, FramePacer.withBackoff(0, false)); // max mode, normal frame
        assertEquals(FramePacer.ERROR_BACKOFF_NANOS, FramePacer.withBackoff(0, true));
        assertEquals(20 * MS, FramePacer.withBackoff(20 * MS, true)); // longer pacing wait wins
        assertEquals(5 * MS, FramePacer.withBackoff(5 * MS, false));
        assertEquals(FramePacer.ERROR_BACKOFF_NANOS, FramePacer.withBackoff(5 * MS, true));
    }
}
