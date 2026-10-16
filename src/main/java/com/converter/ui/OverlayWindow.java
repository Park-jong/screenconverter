package com.converter.ui;

import com.converter.capture.CaptureRegion;
import com.converter.capture.ErrorThrottle;
import com.converter.capture.FrameMetrics;
import com.converter.capture.FramePacer;
import com.converter.capture.RobotScreenGrabber;
import com.converter.capture.ScreenGrabber;
import com.converter.effect.Effect;
import com.converter.effect.EffectRegistry;
import com.converter.effect.EffectOption;
import com.converter.effect.IdentityEffect;
import com.converter.effect.OptionState;
import com.converter.effect.TextGrid;
import com.converter.effect.TextProducingEffect;
import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * Always-on-top window with the OS title bar and frame. The capture area is the centre panel;
 * a placeholder menu panel sits on the right. Move/resize are handled by the OS.
 */
public final class OverlayWindow extends JFrame {

    /** Smallest capture area the window must always leave. */
    private static final Dimension MIN_CAPTURE = new Dimension(320, 180);
    private static final Dimension INITIAL_SIZE = new Dimension(640, 360);
    private static final Color FILL = new Color(40, 40, 40, 255);
    private static final Color BORDER = new Color(200, 200, 200);

    /** Identical capture errors are printed at most this often. */
    private static final long ERROR_LOG_INTERVAL_NANOS = 5_000_000_000L;

    private final CapturePanel content = new CapturePanel();
    private final EffectRegistry registry = EffectRegistry.defaults();
    private final OptionState optionState = new OptionState(registry);
    private final MenuPanel menu = new MenuPanel(registry, optionState);
    private final AsciiCopyAction copyAction = new AsciiCopyAction(() -> this.effect,
            this::shownText, () -> optionState.trimTrailing(this.selectedNumber),
            new SystemTextClipboard());
    private final ImageSaveAction saveAction = new ImageSaveAction(
            () -> ImageSaveAction.defaultFolder(System.getProperty(ImageSaveAction.DIR_PROPERTY),
                    Path.of(System.getProperty("user.home"))),
            Clock.systemDefaultZone());
    private final AtomicBoolean saving = new AtomicBoolean();
    private final ExecutorService saver = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "image-saver");
        t.setDaemon(true);
        return t;
    });
    /** Pause logic shared with the capture worker. */
    private final PauseState pause = new PauseState();
    /** Text grid of the frame currently shown in the capture panel (null if it has none). */
    private volatile TextGrid shownGrid;
    /** Currently selected registry number (EDT only); 0 before the first selection. */
    private int selectedNumber;
    /** Increases on every effect (re)build request; stale background builds are dropped. */
    private int buildSeq;
    /** Builds slow effects (ASCII glyph sets) off the EDT; daemon thread, created on first use. */
    private final ExecutorService builder = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "effect-builder");
        t.setDaemon(true);
        return t;
    });
    private final JPopupMenu popup = new JPopupMenu();

    /** Immutable-by-convention copy of the inner screen rectangle; written on EDT, read by worker. */
    private volatile Rectangle captureRect;
    private Thread captureThread;
    /** Newest finished frame awaiting display (worker writes, EDT takes). */
    private final AtomicReference<Frame> latestFrame = new AtomicReference<>();
    /** True while an EDT display task is scheduled and has not started yet. */
    private final AtomicBoolean displayPending = new AtomicBoolean();
    /** Base title (without the exclusion-failure suffix). */
    private String baseTitle = "Converter";
    /** True once the shown hook has run, so {@link #captureExcluded} is meaningful. */
    private boolean exclusionChecked;
    private Runnable shownHook;
    /** Result of the capture-exclusion attempt made in the shown hook; false until it succeeds. */
    private volatile boolean captureExcluded;
    /** Effect applied to each frame on the worker thread; replaceable at any time. */
    private volatile Effect effect = new IdentityEffect();
    /** Null unless -Dconverter.metrics=true; every instrumentation point is one null check. */
    private final FrameMetrics metrics = FrameMetrics.fromEnvironment();

    public OverlayWindow() {
        super("Converter");
        // OS decorations are kept; never change decoration after construction (HWND recreation).
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setAlwaysOnTop(true);
        // Minimum size depends on decoration insets, so it is set in onShown() once displayable.
        setSize(INITIAL_SIZE);
        setLocationRelativeTo(null);

        content.setBackground(FILL);
        content.setBorder(BorderFactory.createLineBorder(BORDER, 1));
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(content, BorderLayout.CENTER);
        getContentPane().add(menu, BorderLayout.EAST);

        menu.setSelectionListener(this::selectEffect);
        menu.setCopyListener(this::copyText);
        menu.setSaveListener(this::saveImage);
        menu.setOptionListener(this::onOptionChanged);
        menu.setPauseListener(this::togglePause);
        menu.setAlwaysOnTopState(isAlwaysOnTop());
        menu.setAlwaysOnTopListener(this::applyAlwaysOnTop);
        installClose();
        installEffectKeys();
        installCopyKey();
        installPauseKey();
        installOptionKeys();
        installPopup();

        ComponentAdapter rectUpdater = new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                updateCaptureRect();
            }

            @Override
            public void componentResized(ComponentEvent e) {
                updateCaptureRect();
            }
        };
        addComponentListener(rectUpdater);
        content.addComponentListener(rectUpdater);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeApp();
            }
        });
    }

    /**
     * Replaces the per-frame effect (thread-safe; takes effect from the next frame). While paused
     * the held source frame is processed again with the new effect.
     */
    public void setEffect(Effect effect) {
        this.effect = java.util.Objects.requireNonNull(effect, "effect");
        pause.invalidate();
    }

    /**
     * Switches to the registry effect with the given 1-based number and shows its name in the
     * title ("Converter - 모자이크"). The effect is built from the number's remembered option
     * values; if it is not ready yet (ASCII glyph set still being built) the previous effect stays
     * until it is. Unregistered numbers are ignored. Runs on the EDT (queued if called elsewhere).
     *
     * @return true if the number is registered
     */
    public boolean selectEffect(int number) {
        if (registry.get(number).isEmpty()) {
            return false;
        }
        if (SwingUtilities.isEventDispatchThread()) {
            doSelect(number);
        } else {
            SwingUtilities.invokeLater(() -> doSelect(number));
        }
        return true;
    }

    private void doSelect(int number) {
        registry.get(number).ifPresent(entry -> {
            selectedNumber = number;
            baseTitle = "Converter - " + entry.name();
            refreshTitle(); // title only; no decoration change
            menu.setSelected(number);
            applyCurrentEffect();
        });
    }

    /**
     * Builds the selected effect from its option values and installs it. Fast effects are built
     * right here; one that is not ready is built on the builder thread and installed on the EDT
     * if no newer request was made meanwhile (the old effect keeps running until then).
     */
    private void applyCurrentEffect() {
        final int number = selectedNumber;
        final int seq = ++buildSeq;
        if (optionState.isReady(number)) {
            setEffect(optionState.create(number));
            return;
        }
        menu.showStatus("준비 중...");
        final int[] values = optionState.values(number);
        builder.execute(() -> {
            final Effect built;
            try {
                built = optionState.create(number, values);
            } catch (RuntimeException e) {
                return; // keep the previous effect
            }
            SwingUtilities.invokeLater(() -> {
                if (seq == buildSeq) {
                    setEffect(built);
                    menu.showStatus("");
                }
            });
        });
    }

    /** Menu control changed an option of the selected effect (EDT). */
    private void onOptionChanged(int optionIndex, int value) {
        List<EffectOption> opts = optionState.options(selectedNumber);
        if (optionIndex < 0 || optionIndex >= opts.size()) {
            return;
        }
        optionState.set(selectedNumber, optionIndex, value);
        if (opts.get(optionIndex).affectsEffect()) {
            applyCurrentEffect();
        }
    }

    /** +/- keys: one step of the selected effect's first number/choice option (EDT). */
    void stepFirstOption(int direction) {
        int idx = optionState.firstSteppable(selectedNumber);
        if (idx < 0) {
            return;
        }
        int before = optionState.get(selectedNumber, idx);
        int after = optionState.step(selectedNumber, idx, direction);
        menu.setOptionValue(idx, after);
        if (after != before && optionState.options(selectedNumber).get(idx).affectsEffect()) {
            applyCurrentEffect();
        }
    }

    /** Always-on-top toggle (button only, no shortcut). Only setAlwaysOnTop; the window is never recreated. */
    private void applyAlwaysOnTop(boolean value) {
        try {
            setAlwaysOnTop(value);
        } catch (RuntimeException ex) {
            System.err.println("always-on-top change failed: " + ex);
            menu.setAlwaysOnTopState(isAlwaysOnTop());
            menu.showStatus("항상 위 변경 실패");
        }
    }

    /** Pause toggle (button and Space). EDT only. */
    void togglePause() {
        if (pause.isPaused()) {
            pause.resume();
            menu.showStatus("재개");
        } else if (!pause.pause()) {
            menu.showStatus("아직 캡처된 화면 없음");
        }
        syncPauseUi();
    }

    private void syncPauseUi() {
        menu.setPaused(pause.isPaused());
        refreshTitle();
    }

    /** Text of the frame shown right now ("" if none); what the copy action copies. */
    private String shownText() {
        TextGrid g = shownGrid;
        return g == null ? "" : g.text();
    }

    /**
     * TEMPORARY: digit keys 1..9 switch effects. Stage 6 replaces this with the menu-panel UI.
     * Same binding style as ESC (WHEN_IN_FOCUSED_WINDOW).
     */
    private void installEffectKeys() {
        for (int n = 1; n <= 9; n++) {
            final int number = n;
            String key = "selectEffect" + n;
            content.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_0 + n, 0), key);
            content.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0 + n, 0), key);
            content.getActionMap().put(key, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    selectEffect(number);
                }
            });
        }
    }

    /** Ctrl+C copies the ASCII text; same binding style as ESC (WHEN_IN_FOCUSED_WINDOW). */
    private void installCopyKey() {
        content.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), "copyText");
        content.getActionMap().put("copyText", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                copyText();
            }
        });
    }

    /** Space toggles pause; same binding style as ESC (WHEN_IN_FOCUSED_WINDOW). */
    private void installPauseKey() {
        content.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "togglePause");
        content.getActionMap().put("togglePause", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                togglePause();
            }
        });
    }

    /** + and - (main keyboard and numpad) step the first option of the selected effect. */
    private void installOptionKeys() {
        int shift = InputEvent.SHIFT_DOWN_MASK;
        KeyStroke[] up = {KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, 0),
            KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, shift),
            KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, 0),
            KeyStroke.getKeyStroke(KeyEvent.VK_ADD, 0)};
        KeyStroke[] down = {KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, 0),
            KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, 0)};
        bindStep(up, "optionUp", 1);
        bindStep(down, "optionDown", -1);
    }

    private void bindStep(KeyStroke[] keys, String name, int direction) {
        for (KeyStroke k : keys) {
            content.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(k, name);
        }
        content.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                stepFirstOption(direction);
            }
        });
    }

    /** Copies the latest frame's text (button and Ctrl+C) and shows the outcome in the menu. EDT only. */
    void copyText() {
        menu.showStatus(copyAction.copy().message());
    }

    /**
     * Saves the image shown right now (button only). The pixels are copied here on the EDT; PNG
     * encoding and the file write run on a single daemon thread. A press while a save is still
     * running is ignored with a status message.
     */
    void saveImage() {
        BufferedImage shown = content.image();
        if (shown == null || shown.getWidth() <= 0 || shown.getHeight() <= 0) {
            menu.showStatus(ImageSaveAction.NO_IMAGE);
            return;
        }
        if (!saving.compareAndSet(false, true)) {
            menu.showStatus(ImageSaveAction.BUSY);
            return;
        }
        final BufferedImage copy = ImageSaveAction.copyOf(shown);
        saver.execute(() -> {
            ImageSaveAction.Result r;
            try {
                r = saveAction.save(copy);
            } catch (Throwable t) { // NOSONAR: never leave the busy flag set
                r = new ImageSaveAction.Result(false, "저장 실패 (오류)", null);
            }
            final ImageSaveAction.Result result = r;
            if (!result.success()) {
                System.err.println("image save failed: " + result.message());
            }
            SwingUtilities.invokeLater(() -> {
                saving.set(false);
                if (result.success()) {
                    menu.setLastSavedPath(result.path().toString());
                }
                menu.showStatus(result.message());
            });
        });
    }

    /** Records whether this window is excluded from screen capture (set by the shown hook). */
    public void setCaptureExcluded(boolean excluded) {
        this.captureExcluded = excluded;
    }

    /** True if the OS confirmed the window is excluded from capture; the app runs either way. */
    public boolean isCaptureExcluded() {
        return captureExcluded;
    }

    /** Sets a callback run on the EDT by {@link #onShown()} (e.g. excluding the window from capture). */
    public void setShownHook(Runnable hook) {
        this.shownHook = hook;
    }

    /**
     * Call on the EDT right after setVisible(true): the window is displayable now.
     * Runs the shown hook, then starts capturing.
     */
    public void onShown() {
        applyMinimumSize();
        if (shownHook != null) {
            shownHook.run();
        }
        exclusionChecked = true;
        if (!captureExcluded) {
            System.err.println(ExclusionNotice.MESSAGE);
        }
        refreshTitle();
        updateCaptureRect();
        startCapture(createGrabber());
        builder.execute(optionState::prefetchAll); // ASCII glyph sets for every cell size
        if (metrics != null) {
            metrics.startReporter(System.out, 5000);
        }
    }

    /** Applies the base title plus the pause and exclusion-failure suffixes (once the check has run). */
    private void refreshTitle() {
        String base = pause.isPaused() ? baseTitle + PauseState.TITLE_SUFFIX : baseTitle;
        setTitle(ExclusionNotice.title(base, !exclusionChecked || captureExcluded));
    }

    /** Derives the minimum outer size from the real decoration insets so the capture area stays usable. */
    private void applyMinimumSize() {
        Dimension min = MinimumWindowSize.compute(getInsets(), menu.getPreferredSize().width,
                content.getInsets(), MIN_CAPTURE,
                menu.getPreferredSize().height);
        setMinimumSize(min); // enlarges the window itself if it is currently smaller
    }

    private static ScreenGrabber createGrabber() {
        try {
            return new RobotScreenGrabber();
        } catch (AWTException e) {
            throw new IllegalStateException("Screen capture unavailable", e);
        }
    }

    /**
     * Copies the capture panel's inner screen rectangle into a plain value (EDT only). If the
     * area really changed while paused, the held frame no longer matches what is under the
     * window, so the pause is released automatically and the status line says so.
     */
    private void updateCaptureRect() {
        if (!content.isShowing()) {
            return;
        }
        Point p = content.getLocationOnScreen();
        Rectangle next = CaptureRegion.inner(p.x, p.y, content.getWidth(), content.getHeight(),
                content.getInsets());
        Rectangle old = captureRect;
        captureRect = next;
        if (old != null && !old.equals(next) && pause.resume()) {
            syncPauseUi();
            menu.showStatus("창 변경: 일시정지 해제");
        }
    }

    private void startCapture(ScreenGrabber grabber) {
        if (captureThread != null) {
            return;
        }
        FramePacer pacer = FramePacer.fromSystemProperty();
        Thread t = new Thread(() -> captureLoop(grabber, pacer), "capture-worker");
        t.setDaemon(true);
        captureThread = t;
        t.start();
    }

    /**
     * Worker thread loop: capture, then wait only until the next frame time (no wait if late).
     * Any Throwable except interruption is logged (throttled) and the loop continues.
     */
    private void captureLoop(ScreenGrabber grabber, FramePacer pacer) {
        final ErrorThrottle throttle = new ErrorThrottle(ERROR_LOG_INTERVAL_NANOS);
        pacer.start(System.nanoTime());
        boolean lastWasCapture = true;
        while (!Thread.currentThread().isInterrupted()) {
            boolean failed;
            boolean reapplied = false;
            try {
                // Blocks (wait/notify, no polling) while paused and nothing needs re-applying.
                PauseState.Step step = pause.awaitStep();
                if (step.reapply()) {
                    lastWasCapture = false;
                    reapplied = true;
                    failed = !reapplyOnce(step.source());
                } else {
                    if (!lastWasCapture) {
                        pacer.start(System.nanoTime()); // after a pause: no burst of skipped slots
                        lastWasCapture = true;
                    }
                    failed = !captureOnce(grabber);
                }
            } catch (Throwable e) { // NOSONAR: keep capturing after Errors too
                if (e instanceof InterruptedException) {
                    break;
                }
                failed = true;
                long now = System.nanoTime();
                if (throttle.shouldLog(ErrorThrottle.keyOf(e), now)) {
                    long repeats = throttle.takeSuppressed();
                    if (repeats > 0) {
                        System.err.println("capture-worker: " + repeats + " repeated errors suppressed");
                    }
                    long sameKey = throttle.takeSuppressedFor(ErrorThrottle.keyOf(e));
                    if (sameKey > 0) {
                        System.err.println("capture-worker: " + sameKey + " of them were this same error");
                    }
                    e.printStackTrace();
                }
            }
            if (reapplied && !failed) {
                continue; // paused re-apply: not a paced capture, wait for the next change
            }
            long now = System.nanoTime();
            long wait = pacer.afterFrame(now);
            FrameMetrics m = metrics;
            if (m != null) {
                m.setSkipped(pacer.skipped());
            }
            wait = FramePacer.withBackoff(wait, failed);
            if (wait > 0) {
                try {
                    FramePacer.awaitUntil(now + wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /** Worker thread: the single place where a frame is captured.
     * The window is kept out of the capture by display affinity (set in the shown hook), so no
     * hiding happens here; a hide-around-grab fallback would go in this method. */
    private boolean captureOnce(ScreenGrabber grabber) {
        final FrameMetrics m = metrics;
        final long t0 = m != null ? System.nanoTime() : 0;
        if (m != null) {
            m.recordAttempt();
        }
        Rectangle rect = captureRect;
        if (rect == null) {
            return false; // nothing to capture: the caller backs off
        }
        BufferedImage img = grabber.grab(new Rectangle(rect));
        final long t1 = m != null ? System.nanoTime() : 0;
        if (img == null) {
            return false;
        }
        if (!pause.recordSource(img)) {
            return true; // paused meanwhile: the frame becomes the held one and is re-applied
        }
        // Effect runs here on the worker; only the finished image goes to the EDT.
        Effect e = effect;
        BufferedImage result = e.apply(img);
        TextGrid grid = e instanceof TextProducingEffect t ? t.lastGrid() : null;
        final long t2 = m != null ? System.nanoTime() : 0;
        publish(new Frame(result, t0, t1 - t0, t2 - t1, grid, false));
        return true;
    }

    /** Worker thread, paused: applies the current effect to the held source frame again. */
    private boolean reapplyOnce(BufferedImage source) {
        Effect e = effect;
        BufferedImage result = e.apply(source);
        TextGrid grid = e instanceof TextProducingEffect t ? t.lastGrid() : null;
        publish(new Frame(result, 0, 0, 0, grid, true));
        return true;
    }

    /** Latest finished frame plus its timings; replaced (not queued) while the EDT is busy. */
    /** {@code grid} is the text of exactly this image (null for non-text effects); {@code reapplied}
     * frames (made while paused) are not counted in the metrics. */
    private record Frame(BufferedImage image, long startNanos, long grabNanos, long effectNanos,
            TextGrid grid, boolean reapplied) {
    }

    /** Worker thread: keeps only the newest frame and schedules at most one EDT task at a time. */
    private void publish(Frame frame) {
        Frame old = latestFrame.getAndSet(frame);
        if (old != null && metrics != null) {
            metrics.recordReplaced();
        }
        if (displayPending.compareAndSet(false, true)) {
            SwingUtilities.invokeLater(this::showLatest);
        }
    }

    /** EDT: shows the newest frame. The flag is cleared before the slot is read, so a frame
     * published meanwhile either is picked up here or schedules a new task. */
    private void showLatest() {
        displayPending.set(false);
        Frame f = latestFrame.getAndSet(null);
        if (f == null) {
            return;
        }
        FrameMetrics m = metrics;
        if (m != null && !f.reapplied()) {
            m.recordDisplayed(f.startNanos(), f.grabNanos(), f.effectNanos(), System.nanoTime());
        }
        shownGrid = f.grid(); // image and its text are published together
        content.setImage(f.image());
    }

    private void installClose() {
        JMenuItem close = new JMenuItem("닫기");
        close.addActionListener(e -> closeApp());
        popup.add(close);

        content.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeOverlay");
        content.getActionMap().put("closeOverlay", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                closeApp();
            }
        });
    }

    private void closeApp() {
        if (captureThread != null) {
            captureThread.interrupt();
        }
        dispose();
        System.exit(0);
    }

    private void installPopup() {
        content.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybePopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybePopup(e);
            }

            private void maybePopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    popup.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
    }
}
