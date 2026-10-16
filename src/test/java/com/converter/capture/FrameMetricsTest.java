package com.converter.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class FrameMetricsTest {

    private static final long MS = 1_000_000L;
    private static final long WINDOW = 1_000 * MS;

    @Test
    void emptyStateIsAllZero() {
        FrameMetrics m = new FrameMetrics(8);
        FrameMetrics.Snapshot s = m.snapshot(500 * MS, WINDOW);
        assertEquals(0, s.fps());
        assertEquals(0, s.frames());
        assertEquals(0, s.interval().count());
        assertEquals(0, s.interval().avg());
        assertEquals(0, s.interval().p95());
        assertEquals(0, s.interval().max());
        assertEquals(0, s.attempts());
        assertEquals(0, s.displayed());
    }

    @Test
    void intervalAndFpsFromHandComputedValues() {
        FrameMetrics m = new FrameMetrics(8);
        long[] at = {0, 10, 30, 60, 100}; // ms -> intervals 10,20,30,40
        for (long t : at) {
            m.recordAttempt();
            m.recordDisplayed(t * MS - 5 * MS, 2 * MS, 3 * MS, t * MS);
        }
        FrameMetrics.Snapshot s = m.snapshot(100 * MS, WINDOW);
        assertEquals(4, s.interval().count());
        assertEquals(25.0 * MS, s.interval().avg());
        assertEquals(40 * MS, s.interval().p95()); // ceil(0.95*4)=4th value
        assertEquals(40 * MS, s.interval().max());
        assertEquals(50.0, s.fps(), 1e-9); // 5 frames / 0.1 s
        assertEquals(2.0 * MS, s.grab().avg());
        assertEquals(3.0 * MS, s.effect().avg());
        assertEquals(5.0 * MS, s.latency().avg());
        assertEquals(5 * MS, s.latency().p95());
        assertEquals(5, s.attempts());
        assertEquals(5, s.displayed());
    }

    @Test
    void windowExcludesOldFrames() {
        FrameMetrics m = new FrameMetrics(8);
        for (long t : new long[] {0, 100, 2000, 2500}) {
            m.recordDisplayed(t * MS, MS, MS, t * MS);
        }
        // window 1000 ms before now=2500 ms keeps frames at 2000 and 2500
        FrameMetrics.Snapshot s = m.snapshot(2500 * MS, WINDOW);
        assertEquals(2, s.frames());
        assertEquals(2, s.interval().count()); // 1900 ms (from the frame outside) and 500 ms
        assertEquals(1900 * MS, s.interval().max());
        assertEquals(4, s.displayed());
        assertEquals(2 * 1e9 / (500 * MS), s.fps(), 1e-6); // 2 frames / 0.5 s
    }

    @Test
    void attemptsCanExceedDisplayedFrames() {
        FrameMetrics m = new FrameMetrics(4);
        for (int i = 0; i < 10; i++) {
            m.recordAttempt();
        }
        m.recordDisplayed(0, MS, MS, 5 * MS);
        FrameMetrics.Snapshot s = m.snapshot(5 * MS, WINDOW);
        assertEquals(10, s.attempts());
        assertEquals(1, s.displayed());
        assertEquals(0, s.interval().count()); // first frame has no interval
    }

    @Test
    void ringBufferOverflowKeepsNewestFrames() {
        FrameMetrics m = new FrameMetrics(3);
        for (long t = 0; t <= 40; t += 10) { // 5 frames, 0..40 ms
            m.recordDisplayed(t * MS, MS, MS, t * MS);
        }
        FrameMetrics.Snapshot s = m.snapshot(40 * MS, WINDOW);
        assertEquals(3, s.frames()); // 20, 30, 40 retained
        assertEquals(5, s.displayed());
        assertEquals(3, s.interval().count());
        assertEquals(10.0 * MS, s.interval().avg());
        assertEquals(10 * MS, s.interval().max());
        assertEquals(3 * 1e9 / (20 * MS), s.fps(), 1e-6);
    }

    @Test
    void percentileIsNearestRank() {
        long[] v = new long[20];
        for (int i = 0; i < 20; i++) {
            v[i] = i + 1;
        }
        assertEquals(19, FrameMetrics.percentile(v, 95)); // ceil(19.0) = 19
        assertEquals(20, FrameMetrics.percentile(v, 100));
        assertEquals(1, FrameMetrics.percentile(v, 0));
        assertEquals(7, FrameMetrics.percentile(new long[] {7}, 95));
        assertEquals(0, FrameMetrics.percentile(new long[0], 95));
        assertEquals(2.5, FrameMetrics.mean(new long[] {1, 2, 3, 4}));
        assertEquals(0, FrameMetrics.mean(new long[0]));
    }

    @Test
    void formatLineContainsAllFieldsInMilliseconds() {
        FrameMetrics m = new FrameMetrics(4);
        m.recordAttempt();
        m.recordDisplayed(0, 2 * MS, 3 * MS, 10 * MS);
        m.recordAttempt();
        m.recordDisplayed(20 * MS, 2 * MS, 3 * MS, 40 * MS);
        m.setSkipped(3);
        m.recordReplaced();
        String line = m.snapshot(40 * MS, WINDOW).format();
        assertEquals("[metrics] fps=66.7 interval(ms) avg=30.0 p95=30.0 max=30.0"
                + " grab(ms) avg=2.0 p95=2.0 effect(ms) avg=3.0 p95=3.0"
                + " latency(ms) avg=15.0 p95=20.0 attempts=2 displayed=2 skipped=3 replaced=1",
                line);
    }

    @Test
    void disabledWhenPropertyAbsent() {
        String old = System.getProperty(FrameMetrics.PROPERTY);
        try {
            System.clearProperty(FrameMetrics.PROPERTY);
            if (System.getenv("CONVERTER_METRICS") == null) {
                assertNull(FrameMetrics.fromEnvironment());
            }
        } finally {
            if (old != null) {
                System.setProperty(FrameMetrics.PROPERTY, old);
            }
        }
    }

    @Test
    void concurrentRecordingAndReadingDoesNotThrow() throws Exception {
        FrameMetrics m = new FrameMetrics(64);
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(3);
        int n = 20_000;
        Runnable worker = () -> {
            try {
                for (int i = 0; i < n; i++) {
                    m.recordAttempt();
                }
            } catch (Throwable t) {
                error.set(t);
            } finally {
                done.countDown();
            }
        };
        Runnable edt = () -> {
            try {
                for (int i = 0; i < n; i++) {
                    m.recordDisplayed(i, 1, 1, i + 10L);
                }
            } catch (Throwable t) {
                error.set(t);
            } finally {
                done.countDown();
            }
        };
        Runnable reader = () -> {
            try {
                for (int i = 0; i < 2_000; i++) {
                    m.snapshot(Long.MAX_VALUE / 2, Long.MAX_VALUE / 2).format();
                }
            } catch (Throwable t) {
                error.set(t);
            } finally {
                done.countDown();
            }
        };
        new Thread(worker).start();
        new Thread(edt).start();
        new Thread(reader).start();
        done.await();
        assertNull(error.get());
        assertEquals(n, m.attempts());
        assertEquals(n, m.displayedTotal());
    }
}
