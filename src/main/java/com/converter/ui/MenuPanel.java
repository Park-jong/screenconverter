package com.converter.ui;

import com.converter.effect.EffectOption;
import com.converter.effect.EffectRegistry;
import com.converter.effect.OptionState;
import com.converter.effect.TextProducingEffect;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.Timer;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicSliderUI;

/**
 * Fixed-width side panel listing the registered effects ("1  원본" ...). The selected row is
 * highlighted and mirrored in a "현재: ..." header. Clicking a row notifies the listener with the
 * 1-based effect number; {@link #setSelected} only updates the display and never calls the listener.
 * Below the list an "옵션" area shows the controls of the selected effect only (slider for numbers,
 * buttons for discrete choices, a check toggle for on/off); its height is fixed for the effect
 * with the most options, so the menu never changes size. A pause toggle and the copy button follow.
 * Nothing here is focusable, so the window's ESC/digit/Space key bindings keep working after a click.
 */
public final class MenuPanel extends JPanel {

    /** Called with the option's index (in the selected effect) and its new value. */
    public interface OptionListener {
        void onChanged(int optionIndex, int value);
    }

    /** Single source of the menu column width (read by MinimumWindowSize via preferred size). */
    private static final int WIDTH_PX = 180;
    private static final int ROW_HEIGHT = 18;
    private static final int HEADER_HEIGHT = 18;
    /** Rows the list always shows without scrolling (the preferred height is sized for these). */
    private static final int VISIBLE_ROWS = 9;
    private static final int BUTTON_HEIGHT = 24;
    private static final int STATUS_HEIGHT = 18;
    private static final int SOUTH_PAD = 4;
    private static final int GAP = 4;
    private static final int STATUS_CLEAR_MS = 2500;
    /** Content width inside the 6px side margins. */
    private static final int INNER_WIDTH = WIDTH_PX - 12;
    private static final int OPTION_TITLE_HEIGHT = 16;
    private static final int OPTION_LABEL_HEIGHT = 16;
    private static final int SLIDER_HEIGHT = 18;
    private static final int CHOICE_HEIGHT = 20;
    private static final int TOGGLE_HEIGHT = 22;
    private static final int NO_OPTIONS_HEIGHT = 18;
    static final String COPY_TEXT = "텍스트 복사 (Ctrl+C)";
    static final String SAVE_TEXT = "이미지 저장";
    static final String PAUSE_TEXT ="일시정지 (Space)";
    static final String PAUSED_TEXT = "● 일시정지 중 (Space)";
    static final String TOP_ON_TEXT = "항상 위: 켜짐";
    static final String TOP_OFF_TEXT = "항상 위: 꺼짐";
    static final String NO_OPTIONS_TEXT = "옵션 없음";
    static final String COPY_DISABLED_TIP = "ASCII 아트(9번)에서 사용";
    private static final Color BUTTON_BG = new Color(70, 70, 70);
    private static final Color BUTTON_BG_DISABLED = new Color(45, 45, 45);
    private static final Color BUTTON_FG_DISABLED = new Color(110, 110, 110);

    private static final Color BACKGROUND = new Color(30, 30, 30);
    private static final Color TEXT = new Color(200, 200, 200);
    private static final Color SELECTED_BG = new Color(38, 110, 200);
    private static final Color HOVER_BG = new Color(60, 60, 60);
    private static final Font FONT = new Font("Malgun Gothic", Font.PLAIN, 12);

    private final List<EffectRegistry.Entry> entries;
    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final JList<String> list = new JList<>(model);
    private final JLabel current = new JLabel();
    private final FlatButton copyButton = new FlatButton(COPY_TEXT);
    private final FlatButton saveButton = new FlatButton(SAVE_TEXT);
    private Runnable saveListener = () -> { };
    private final FlatButton pauseButton = new FlatButton(PAUSE_TEXT);
    private final FlatButton topButton = new FlatButton(TOP_ON_TEXT);
    private boolean alwaysOnTop = true;
    private Consumer<Boolean> alwaysOnTopListener = v -> { };
    private final JLabel status = new JLabel(" ");
    private final Timer statusTimer = new Timer(STATUS_CLEAR_MS, e -> status.setText(" "));
    private final OptionState optionState;
    private final JPanel optionRowsHost = new JPanel();
    private final List<OptionRow> optionRows = new ArrayList<>();
    private OptionListener optionListener = (i, v) -> { };
    private Runnable pauseListener = () -> { };
    private Runnable copyListener = () -> { };
    private IntConsumer listener = n -> { };
    /** True while the display is updated programmatically, so the listener is not called. */
    private boolean updating;
    private int hoverIndex = -1;
    private int selectedNumber;

    public MenuPanel(EffectRegistry registry) {
        this(registry, new OptionState(registry));
    }

    /** @param optionState current option values, shared with whoever applies the options */
    public MenuPanel(EffectRegistry registry, OptionState optionState) {
        super(new BorderLayout());
        entries = List.copyOf(Objects.requireNonNull(registry, "registry").entries());
        this.optionState = Objects.requireNonNull(optionState, "optionState");
        setBackground(BACKGROUND);
        int rows = Math.min(Math.max(entries.size(), 1), VISIBLE_ROWS);
        // Height = everything that must be visible at once; MinimumWindowSize reads it.
        setPreferredSize(new Dimension(WIDTH_PX, HEADER_HEIGHT + rows * ROW_HEIGHT
                + SOUTH_PAD + optionAreaHeight() + GAP + BUTTON_HEIGHT + GAP + BUTTON_HEIGHT
                + GAP + BUTTON_HEIGHT + GAP + BUTTON_HEIGHT + STATUS_HEIGHT));

        for (int i = 0; i < entries.size(); i++) {
            model.addElement(label(numberOf(i), entries.get(i).name()));
        }

        current.setFont(FONT.deriveFont(Font.BOLD));
        current.setForeground(Color.WHITE);
        current.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        current.setPreferredSize(new Dimension(WIDTH_PX, HEADER_HEIGHT));
        add(current, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFocusable(false); // keep keyboard focus out of the menu (no type-ahead/arrow keys)
        list.setBackground(BACKGROUND);
        list.setForeground(TEXT);
        list.setFont(FONT);
        list.setFixedCellHeight(ROW_HEIGHT);
        list.setCellRenderer(new Renderer());
        list.addListSelectionListener(e -> {
            int index = list.getSelectedIndex();
            if (!updating && !e.getValueIsAdjusting() && index >= 0) {
                listener.accept(numberOf(index));
            }
        });
        MouseAdapter hover = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int i = list.locationToIndex(e.getPoint());
                boolean inside = i >= 0 && list.getCellBounds(i, i).contains(e.getPoint());
                setHover(inside ? i : -1);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setHover(-1);
            }
        };
        list.addMouseMotionListener(hover);
        list.addMouseListener(hover);

        JScrollPane scroll = new JScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(BACKGROUND);
        scroll.setFocusable(false);
        styleScrollBar(scroll.getVerticalScrollBar());
        add(scroll, BorderLayout.CENTER);

        copyButton.setFocusable(false); // keep keyboard focus so digit keys/ESC/Ctrl+C keep working
        copyButton.setFont(FONT);
        copyButton.addActionListener(e -> copyListener.run());
        saveButton.setFocusable(false); // no shortcut; button only
        saveButton.setFont(FONT);
        saveButton.addActionListener(e -> saveListener.run());
        pauseButton.setFocusable(false);
        pauseButton.setFont(FONT);
        pauseButton.addActionListener(e -> pauseListener.run());
        topButton.setFocusable(false);
        topButton.setFont(FONT);
        topButton.setActive(true);
        topButton.addActionListener(e -> {
            setAlwaysOnTopState(!alwaysOnTop);
            alwaysOnTopListener.accept(alwaysOnTop);
        });
        status.setFont(FONT);
        status.setForeground(TEXT);
        statusTimer.setRepeats(false);

        JLabel optionTitle = new JLabel("옵션");
        optionTitle.setFont(FONT.deriveFont(Font.BOLD));
        optionTitle.setForeground(Color.WHITE);
        optionRowsHost.setLayout(new BoxLayout(optionRowsHost, BoxLayout.Y_AXIS));
        optionRowsHost.setOpaque(false);
        JPanel optionArea = new JPanel(new BorderLayout());
        optionArea.setBackground(BACKGROUND);
        optionArea.add(optionTitle, BorderLayout.NORTH);
        optionArea.add(optionRowsHost, BorderLayout.CENTER);

        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.setBackground(BACKGROUND);
        south.setBorder(BorderFactory.createEmptyBorder(SOUTH_PAD, 6, 0, 6));
        south.add(fixed(optionArea, INNER_WIDTH, optionAreaHeight()));
        south.add(spacer(GAP));
        south.add(fixed(pauseButton, INNER_WIDTH, BUTTON_HEIGHT));
        south.add(spacer(GAP));
        south.add(fixed(copyButton, INNER_WIDTH, BUTTON_HEIGHT));
        south.add(spacer(GAP));
        south.add(fixed(saveButton, INNER_WIDTH, BUTTON_HEIGHT));
        south.add(spacer(GAP));
        south.add(fixed(topButton, INNER_WIDTH, BUTTON_HEIGHT));
        south.add(fixed(status, INNER_WIDTH, STATUS_HEIGHT));
        add(south, BorderLayout.SOUTH);

        setSelected(entries.isEmpty() ? 0 : 1); // matches the window's initial (original) effect
    }

    /** Called with the 1-based effect number when the user picks a row. */
    public void setSelectionListener(IntConsumer listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    /**
     * Updates the highlighted row and the "현재" header without notifying the listener.
     * Unregistered numbers are ignored. Call on the EDT.
     *
     * @return true if the number was registered and is now shown as selected
     */
    public boolean setSelected(int number) {
        if (number < 1 || number > entries.size()) {
            return false;
        }
        updating = true;
        try {
            list.setSelectedIndex(indexOf(number));
            list.ensureIndexIsVisible(indexOf(number));
        } finally {
            updating = false;
        }
        EffectRegistry.Entry entry = entries.get(indexOf(number));
        current.setText("현재: " + entry.name());
        setCopyAvailable(entry.effect() instanceof TextProducingEffect);
        selectedNumber = number;
        rebuildOptions(number);
        return true;
    }

    // ---- options area ----

    /** Called when the user changes an option control (not by {@link #setOptionValue}). */
    public void setOptionListener(OptionListener listener) {
        this.optionListener = Objects.requireNonNull(listener, "listener");
    }

    /** Shows a new value in an option control of the selected effect without notifying. */
    public void setOptionValue(int optionIndex, int value) {
        if (optionIndex < 0 || optionIndex >= optionRows.size()) {
            return;
        }
        updating = true;
        try {
            optionRows.get(optionIndex).show(value);
        } finally {
            updating = false;
        }
    }

    /** Called when the pause toggle is pressed (the window also handles Space itself). */
    public void setPauseListener(Runnable listener) {
        this.pauseListener = Objects.requireNonNull(listener, "listener");
    }

    /** Shows the pause toggle as pressed/released; does not notify. */
    public void setPaused(boolean paused) {
        pauseButton.setActive(paused);
        pauseButton.setText(paused ? PAUSED_TEXT : PAUSE_TEXT);
    }

    /** Called with the new value when the user presses the always-on-top button (no shortcut). */
    public void setAlwaysOnTopListener(Consumer<Boolean> listener) {
        this.alwaysOnTopListener = Objects.requireNonNull(listener, "listener");
    }

    /** Shows the always-on-top toggle as on/off; does not notify. */
    public void setAlwaysOnTopState(boolean on) {
        alwaysOnTop = on;
        topButton.setActive(on);
        topButton.setText(on ? TOP_ON_TEXT : TOP_OFF_TEXT);
    }

    /** The always-on-top toggle (package-private for tests). */
    JButton alwaysOnTopButton() {
        return topButton;
    }

    /** Height of the option area: title plus the tallest option set of any effect. */
    private int optionAreaHeight() {
        int tallest = NO_OPTIONS_HEIGHT;
        for (int n = 1; n <= entries.size(); n++) {
            int sum = 0;
            for (EffectOption o : optionState.options(n)) {
                sum += OptionRow.heightOf(o);
            }
            tallest = Math.max(tallest, sum);
        }
        return OPTION_TITLE_HEIGHT + tallest;
    }

    /** Replaces the option controls with those of the given effect, showing its stored values. */
    private void rebuildOptions(int number) {
        optionRowsHost.removeAll();
        optionRows.clear();
        List<EffectOption> opts = optionState.options(number);
        if (opts.isEmpty()) {
            JLabel none = new JLabel(NO_OPTIONS_TEXT);
            none.setFont(FONT);
            none.setForeground(BUTTON_FG_DISABLED);
            optionRowsHost.add(fixed(none, INNER_WIDTH, NO_OPTIONS_HEIGHT));
        }
        for (int i = 0; i < opts.size(); i++) {
            final int index = i;
            OptionRow row = new OptionRow(opts.get(i), v -> {
                if (!updating) {
                    optionListener.onChanged(index, v);
                }
            });
            updating = true;
            try {
                row.show(optionState.get(number, i));
            } finally {
                updating = false;
            }
            optionRows.add(row);
            optionRowsHost.add(row.component());
        }
        optionRowsHost.revalidate();
        optionRowsHost.repaint();
    }

    private static <T extends JComponent> T fixed(T c, int w, int h) {
        Dimension d = new Dimension(w, h);
        c.setPreferredSize(d);
        c.setMinimumSize(d);
        c.setMaximumSize(d);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private static Component spacer(int h) {
        return javax.swing.Box.createRigidArea(new Dimension(0, h));
    }

    /** One option's control(s): label + slider, label + choice buttons, or one check toggle. */
    private static final class OptionRow {
        private final EffectOption option;
        private final java.util.function.IntConsumer onUser;
        private final JPanel panel = new JPanel(new BorderLayout());
        private JLabel label;
        private JSlider slider;
        private final List<JToggleButton> buttons = new ArrayList<>();
        /** Value currently shown. */
        private int shown = Integer.MIN_VALUE;

        OptionRow(EffectOption option, java.util.function.IntConsumer onUser) {
            this.option = option;
            this.onUser = onUser;
            panel.setOpaque(false);
            switch (option.kind()) {
                case INT -> buildSlider();
                case CHOICE -> buildChoice();
                case BOOL -> buildToggle();
            }
            fixed(panel, INNER_WIDTH, heightOf(option));
        }

        static int heightOf(EffectOption o) {
            return switch (o.kind()) {
                case INT -> OPTION_LABEL_HEIGHT + SLIDER_HEIGHT;
                case CHOICE -> OPTION_LABEL_HEIGHT + CHOICE_HEIGHT;
                case BOOL -> TOGGLE_HEIGHT;
            };
        }

        JComponent component() {
            return panel;
        }

        JSlider slider() {
            return slider;
        }

        List<JToggleButton> buttons() {
            return buttons;
        }

        String labelText() {
            return label != null ? label.getText() : buttons.get(0).getText();
        }

        private void newLabel() {
            label = new JLabel();
            label.setFont(FONT);
            label.setForeground(TEXT);
            label.setPreferredSize(new Dimension(INNER_WIDTH, OPTION_LABEL_HEIGHT));
            panel.add(label, BorderLayout.NORTH);
        }

        private void buildSlider() {
            newLabel();
            slider = new JSlider(option.min(), option.max(), option.defaultValue());
            slider.setFocusable(false);
            slider.setOpaque(false);
            slider.setUI(new DarkSliderUI(slider, option.min(), option.max(), option.step()));
            slider.addChangeListener(e -> {
                label.setText(option.name() + ": " + option.format(slider.getValue()));
                onUser.accept(slider.getValue());
            });
            panel.add(slider, BorderLayout.CENTER);
        }

        private void buildChoice() {
            newLabel();
            JPanel row = new JPanel(new GridLayout(1, option.labels().size(), 2, 0));
            row.setOpaque(false);
            for (int i = 0; i < option.labels().size(); i++) {
                final int value = i;
                JToggleButton b = new FlatToggle(option.labels().get(i), false);
                b.addActionListener(e -> {
                    boolean changed = value != shown;
                    show(value);
                    if (changed) {
                        onUser.accept(value);
                    }
                });
                buttons.add(b);
                row.add(b);
            }
            panel.add(row, BorderLayout.CENTER);
        }

        private void buildToggle() {
            JToggleButton b = new FlatToggle(option.name(), true);
            b.addActionListener(e -> onUser.accept(b.isSelected() ? 1 : 0));
            buttons.add(b);
            panel.add(b, BorderLayout.CENTER);
        }

        /** Shows a value in the control (no listener call from here). */
        void show(int value) {
            int v = option.clamp(value);
            shown = v;
            switch (option.kind()) {
                case INT -> {
                    if (slider.getValue() != v) {
                        slider.setValue(v);
                    }
                    label.setText(option.name() + ": " + option.format(v));
                }
                case CHOICE -> {
                    for (int i = 0; i < buttons.size(); i++) {
                        buttons.get(i).setSelected(i == v);
                    }
                    label.setText(option.name() + ": " + option.format(v));
                }
                case BOOL -> buttons.get(0).setSelected(v != 0);
            }
        }
    }

    // ---- test access ----

    /** Number of option controls currently shown. */
    int optionCount() {
        return optionRows.size();
    }

    /** True if the "옵션 없음" line is shown instead of controls. */
    boolean noOptionsShown() {
        return optionRows.isEmpty() && optionRowsHost.getComponentCount() == 1;
    }

    /** Slider of INT option {@code i}, or null for other kinds. */
    JSlider optionSlider(int i) {
        return optionRows.get(i).slider();
    }

    /** Buttons of option {@code i}: the choice buttons, or the single check toggle of a BOOL. */
    List<JToggleButton> optionButtons(int i) {
        return optionRows.get(i).buttons();
    }

    /** Label text of option {@code i} ("블록 크기: 10", "칸 크기: 8x16", toggle text). */
    String optionLabel(int i) {
        return optionRows.get(i).labelText();
    }

    /** The pause toggle. */
    JButton pauseButton() {
        return pauseButton;
    }

    /** Currently selected effect number (for tests). */
    int selectedNumber() {
        return selectedNumber;
    }

    /** All option controls' focusability, for the "nothing steals keys" check. */
    boolean allOptionControlsUnfocusable() {
        return !anyFocusable(optionRowsHost);
    }

    private static boolean anyFocusable(java.awt.Container c) {
        for (Component k : c.getComponents()) {
            if (k.isFocusable() && (k instanceof AbstractButton || k instanceof JSlider)) {
                return true;
            }
            if (k instanceof java.awt.Container cc && anyFocusable(cc)) {
                return true;
            }
        }
        return false;
    }

    /** Called when the copy button is pressed (the window also calls it for Ctrl+C itself). */
    public void setCopyListener(Runnable listener) {
        this.copyListener = Objects.requireNonNull(listener, "listener");
    }

    /** Enables/disables the copy button; disabled shows a tooltip explaining where it works. */
    private void setCopyAvailable(boolean available) {
        copyButton.setEnabled(available);
        copyButton.setToolTipText(available ? null : COPY_DISABLED_TIP);
    }

    /** Shows a short message under the button and clears it after about 2.5 s. Call on the EDT. */
    public void showStatus(String message) {
        String shown = message == null || message.isEmpty() ? " "
                : TextFit.fit(message, INNER_WIDTH, status.getFontMetrics(status.getFont())::stringWidth);
        status.setText(shown);
        status.setToolTipText(message);
        statusTimer.restart();
    }

    /** Current status line ("" when cleared). */
    String statusText() {
        return status.getText().trim();
    }

    /** Called when the save-image button is pressed (button only, no shortcut). */
    public void setSaveListener(Runnable listener) {
        this.saveListener = Objects.requireNonNull(listener, "listener");
    }

    /** Shows the full path of the last saved file in the save button's tooltip. */
    public void setLastSavedPath(String path) {
        saveButton.setToolTipText("마지막 저장: " + path);
    }

    /** The save-image button (package-private for tests). */
    JButton saveButton() {
        return saveButton;
    }

    /** The copy button (package-private for tests). */
    JButton copyButton() {
        return copyButton;
    }

    /** Currently shown 1-based number, or 0 if none. */
    public int getSelected() {
        return list.getSelectedIndex() < 0 ? 0 : numberOf(list.getSelectedIndex());
    }

    /** The "현재: ..." header text. */
    String currentText() {
        return current.getText();
    }

    /** The list component (package-private for tests). */
    JList<String> effectList() {
        return list;
    }

    /** 1-based effect number to list index. */
    static int indexOf(int number) {
        return number - 1;
    }

    /** List index to 1-based effect number. */
    static int numberOf(int index) {
        return index + 1;
    }

    /** Row text, e.g. "2  모자이크". */
    static String label(int number, String name) {
        return number + "  " + name;
    }

    private void setHover(int index) {
        if (index != hoverIndex) {
            hoverIndex = index;
            list.repaint();
        }
    }

    private static void styleScrollBar(JScrollBar bar) {
        bar.setPreferredSize(new Dimension(8, 0));
        bar.setFocusable(false);
        bar.setBackground(BACKGROUND);
        bar.setUI(new BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                thumbColor = new Color(110, 110, 110);
                trackColor = BACKGROUND;
            }

            @Override
            protected JButton createDecreaseButton(int orientation) {
                return zeroButton();
            }

            @Override
            protected JButton createIncreaseButton(int orientation) {
                return zeroButton();
            }

            private JButton zeroButton() {
                JButton b = new JButton();
                b.setPreferredSize(new Dimension(0, 0));
                b.setFocusable(false);
                return b;
            }
        });
    }

    /** Flat dark button; painted by hand so disabled state looks the same on every look and feel. */
    private static final class FlatButton extends JButton {
        /** Drawn like a pressed toggle (e.g. pause is on). */
        private boolean active;

        FlatButton(String text) {
            super(text);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
        }

        void setActive(boolean active) {
            this.active = active;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                boolean on = isEnabled();
                g2.setColor(on ? (active || getModel().isPressed() ? SELECTED_BG : BUTTON_BG)
                        : BUTTON_BG_DISABLED);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(on ? Color.WHITE : BUTTON_FG_DISABLED);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(getText(), x, y);
            } finally {
                g2.dispose();
            }
        }
    }

    /**
     * Flat dark toggle. With {@code check} it draws a small check box and the text left-aligned
     * (on/off option); otherwise a centred segment button that turns blue when selected.
     */
    private static final class FlatToggle extends JToggleButton {
        private final boolean check;

        FlatToggle(String text, boolean check) {
            super(text);
            this.check = check;
            setFocusable(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setFont(FONT);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int ty = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                if (check) {
                    int box = 12;
                    int by = (getHeight() - box) / 2;
                    g2.setColor(isSelected() ? SELECTED_BG : BUTTON_BG);
                    g2.fillRect(0, by, box, box);
                    g2.setColor(TEXT);
                    g2.drawRect(0, by, box - 1, box - 1);
                    if (isSelected()) {
                        g2.setColor(Color.WHITE);
                        g2.drawLine(2, by + 6, 4, by + 8);
                        g2.drawLine(4, by + 8, 9, by + 3);
                        g2.drawLine(2, by + 7, 4, by + 9);
                        g2.drawLine(4, by + 9, 9, by + 4);
                    }
                    g2.setColor(TEXT);
                    g2.drawString(getText(), box + 6, ty);
                } else {
                    g2.setColor(isSelected() ? SELECTED_BG : BUTTON_BG);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.setColor(isSelected() ? Color.WHITE : TEXT);
                    g2.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2, ty);
                }
            } finally {
                g2.dispose();
            }
        }
    }

    /**
     * Dark horizontal slider: thin track, filled part in the accent colour, square thumb. A click
     * or drag anywhere on the track jumps to the nearest step (values are min + k * step).
     */
    private static final class DarkSliderUI extends BasicSliderUI {
        private final int min;
        private final int max;
        private final int step;

        DarkSliderUI(JSlider slider, int min, int max, int step) {
            super(slider);
            this.min = min;
            this.max = max;
            this.step = step;
        }

        @Override
        protected Dimension getThumbSize() {
            return new Dimension(8, 14);
        }

        @Override
        public void paintTrack(Graphics g) {
            Rectangle t = trackRect;
            int y = t.y + t.height / 2 - 2;
            g.setColor(BUTTON_BG);
            g.fillRect(t.x, y, t.width, 4);
            g.setColor(SELECTED_BG);
            g.fillRect(t.x, y, thumbRect.x + thumbRect.width / 2 - t.x, 4);
        }

        @Override
        public void paintThumb(Graphics g) {
            g.setColor(Color.WHITE);
            g.fillRect(thumbRect.x, thumbRect.y, thumbRect.width, thumbRect.height);
        }

        @Override
        public void paintFocus(Graphics g) {
            // never focused
        }

        @Override
        protected TrackListener createTrackListener(JSlider s) {
            return new TrackListener() {
                @Override
                public void mousePressed(MouseEvent e) {
                    slider.setValueIsAdjusting(true);
                    move(e);
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    move(e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    slider.setValueIsAdjusting(false);
                }

                private void move(MouseEvent e) {
                    int raw = valueForXPosition(e.getX());
                    int snapped = min + Math.round((float) (raw - min) / step) * step;
                    slider.setValue(Math.max(min, Math.min(max, snapped)));
                }
            };
        }
    }

    private final class Renderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> l, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(l, value, index, isSelected, false);
            setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
            if (isSelected) {
                setBackground(SELECTED_BG);
                setForeground(Color.WHITE);
                setFont(FONT.deriveFont(Font.BOLD));
            } else {
                setBackground(index == hoverIndex ? HOVER_BG : BACKGROUND);
                setForeground(TEXT);
                setFont(FONT);
            }
            return this;
        }
    }
}
