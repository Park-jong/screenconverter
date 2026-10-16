package com.converter.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PauseStateTest {

    private static BufferedImage img() {
        return new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
    }

    @Test
    void runningStateAlwaysCaptures() {
        PauseState p = new PauseState();
        assertFalse(p.isPaused());
        assertEquals(PauseState.Kind.CAPTURE, p.poll().kind());
        assertTrue(p.recordSource(img()));
        assertEquals(PauseState.Kind.CAPTURE, p.poll().kind());
    }

    @Test
    void pauseNeedsAFrameFirst() {
        PauseState p = new PauseState();
        assertFalse(p.pause());
        assertFalse(p.isPaused());
        p.recordSource(img());
        assertTrue(p.pause());
        assertTrue(p.isPaused());
        assertFalse(p.pause()); // already paused
    }

    @Test
    void pausedHoldsLastSourceAndReappliesOnceUntilInvalidated() {
        PauseState p = new PauseState();
        BufferedImage a = img();
        BufferedImage b = img();
        p.recordSource(a);
        p.recordSource(b);
        assertTrue(p.pause());
        PauseState.Step s = p.poll();
        assertTrue(s.reapply());
        assertSame(b, s.source()); // the newest source frame, not an older one
        assertNull(p.poll()); // clean: the worker waits, no capturing
        assertNull(p.poll());
        p.invalidate(); // effect or option changed
        PauseState.Step again = p.poll();
        assertTrue(again.reapply());
        assertSame(b, again.source());
        assertNull(p.poll());
    }

    @Test
    void lateFrameAfterPauseBecomesTheHeldFrame() {
        PauseState p = new PauseState();
        BufferedImage a = img();
        BufferedImage late = img();
        p.recordSource(a);
        p.pause();
        assertSame(a, p.poll().source());
        assertFalse(p.recordSource(late)); // not shown normally while paused
        PauseState.Step s = p.poll();
        assertTrue(s.reapply());
        assertSame(late, s.source());
    }

    @Test
    void invalidateWhileRunningDoesNothingLater() {
        PauseState p = new PauseState();
        p.recordSource(img());
        p.invalidate();
        assertEquals(PauseState.Kind.CAPTURE, p.poll().kind());
        p.pause();
        assertTrue(p.poll().reapply()); // from pause() itself
        assertNull(p.poll());
    }

    @Test
    void resumeReleasesHeldFrameAndCapturesAgain() {
        PauseState p = new PauseState();
        p.recordSource(img());
        p.pause();
        assertTrue(p.resume());
        assertFalse(p.isPaused());
        assertEquals(PauseState.Kind.CAPTURE, p.poll().kind());
        assertFalse(p.resume()); // not paused any more
        p.invalidate(); // no effect while running
        assertEquals(PauseState.Kind.CAPTURE, p.poll().kind());
        // pausing again holds the newest frame again
        BufferedImage n = img();
        p.recordSource(n);
        assertTrue(p.pause());
        assertSame(n, p.poll().source());
    }

    @Test
    void windowMoveReleasesAPauseAndIsHarmlessWhenRunning() {
        // The window calls resume() when the capture area changes; it reports whether it ended a pause.
        PauseState p = new PauseState();
        assertFalse(p.resume());
        p.recordSource(img());
        p.pause();
        assertTrue(p.resume());
        assertEquals(PauseState.Kind.CAPTURE, p.poll().kind());
    }

    @Test
    void awaitStepWakesUpOnInvalidateAndResume() throws Exception {
        PauseState p = new PauseState();
        BufferedImage a = img();
        p.recordSource(a);
        p.pause();
        assertTrue(p.awaitStep().reapply()); // dirty from pause()
        AtomicReference<PauseState.Step> got = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            try {
                started.countDown();
                got.set(p.awaitStep());
            } catch (InterruptedException e) {
                // test fails below
            }
            done.countDown();
        });
        t.start();
        assertTrue(started.await(2, TimeUnit.SECONDS));
        assertFalse(done.await(150, TimeUnit.MILLISECONDS)); // blocked, nothing to do
        p.invalidate();
        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertTrue(got.get().reapply());
        assertSame(a, got.get().source());

        CountDownLatch done2 = new CountDownLatch(1);
        Thread t2 = new Thread(() -> {
            try {
                got.set(p.awaitStep());
            } catch (InterruptedException e) {
                // test fails below
            }
            done2.countDown();
        });
        t2.start();
        assertFalse(done2.await(150, TimeUnit.MILLISECONDS));
        p.resume();
        assertTrue(done2.await(2, TimeUnit.SECONDS));
        assertEquals(PauseState.Kind.CAPTURE, got.get().kind());
    }

    @Test
    void awaitStepIsInterruptible() throws Exception {
        PauseState p = new PauseState();
        p.recordSource(img());
        p.pause();
        p.poll(); // consume the first re-apply; now the worker would wait
        AtomicReference<Boolean> interrupted = new AtomicReference<>(false);
        CountDownLatch done = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            try {
                p.awaitStep();
            } catch (InterruptedException e) {
                interrupted.set(true);
            }
            done.countDown();
        });
        t.start();
        Thread.sleep(100);
        t.interrupt();
        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertTrue(interrupted.get());
    }

    @Test
    void titleSuffixText() {
        assertEquals(" [일시정지]", PauseState.TITLE_SUFFIX);
        assertEquals("Converter - 모자이크 [일시정지] (제외 실패)",
                ExclusionNotice.title("Converter - 모자이크" + PauseState.TITLE_SUFFIX, false));
    }
}
