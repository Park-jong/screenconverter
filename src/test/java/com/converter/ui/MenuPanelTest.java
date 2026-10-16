package com.converter.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.converter.effect.EffectRegistry;
import com.converter.effect.IdentityEffect;
import com.converter.effect.OptionState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MenuPanelTest {

    @Test
    void listFollowsRegistryOrderAndNames() {
        EffectRegistry r = EffectRegistry.defaults();
        MenuPanel menu = new MenuPanel(r);
        assertEquals(r.size(), menu.effectList().getModel().getSize());
        for (int i = 0; i < r.size(); i++) {
            assertEquals((i + 1) + "  " + r.entries().get(i).name(),
                    menu.effectList().getModel().getElementAt(i));
        }
        assertEquals("1  원본", menu.effectList().getModel().getElementAt(0));
        assertEquals("9  ASCII 아트", menu.effectList().getModel().getElementAt(8));
    }

    @Test
    void newEffectsAppearAutomatically() {
        EffectRegistry r = EffectRegistry.defaults().register("새 효과", new IdentityEffect());
        MenuPanel menu = new MenuPanel(r);
        assertEquals(10, menu.effectList().getModel().getSize());
        assertEquals("10  새 효과", menu.effectList().getModel().getElementAt(9));
    }

    @Test
    void initialSelectionIsFirstAndHeaderShown() {
        MenuPanel menu = new MenuPanel(EffectRegistry.defaults());
        assertEquals(1, menu.getSelected());
        assertEquals("현재: 원본", menu.currentText());
    }

    @Test
    void setSelectedUpdatesDisplayWithoutNotifying() {
        MenuPanel menu = new MenuPanel(EffectRegistry.defaults());
        List<Integer> calls = new ArrayList<>();
        menu.setSelectionListener(calls::add);
        assertTrue(menu.setSelected(2));
        assertEquals(2, menu.getSelected());
        assertEquals(1, menu.effectList().getSelectedIndex());
        assertEquals("현재: 모자이크", menu.currentText());
        assertTrue(menu.setSelected(9));
        assertEquals("현재: ASCII 아트", menu.currentText());
        assertTrue(calls.isEmpty());
    }

    @Test
    void unregisteredNumbersAreIgnored() {
        MenuPanel menu = new MenuPanel(EffectRegistry.defaults());
        menu.setSelected(3);
        assertFalse(menu.setSelected(0));
        assertFalse(menu.setSelected(10));
        assertFalse(menu.setSelected(-1));
        assertEquals(3, menu.getSelected());
        assertEquals("현재: 흑백", menu.currentText());
    }

    @Test
    void pickingRowNotifiesListenerWithNumber() {
        MenuPanel menu = new MenuPanel(EffectRegistry.defaults());
        List<Integer> calls = new ArrayList<>();
        menu.setSelectionListener(calls::add);
        menu.effectList().setSelectedIndex(4);
        menu.effectList().setSelectedIndex(8);
        assertEquals(List.of(5, 9), calls);
    }

    @Test
    void listIsNotFocusableAndWidthIsFixed() {
        MenuPanel menu = new MenuPanel(EffectRegistry.defaults());
        assertFalse(menu.effectList().isFocusable());
        assertEquals(180, menu.getPreferredSize().width);
    }

    @Test
    void numberIndexConversion() {
        assertEquals(0, MenuPanel.indexOf(1));
        assertEquals(8, MenuPanel.indexOf(9));
        assertEquals(1, MenuPanel.numberOf(0));
        assertEquals(9, MenuPanel.numberOf(8));
        assertEquals("2  모자이크", MenuPanel.label(2, "모자이크"));
    }

    @Test
    void copyButtonEnabledOnlyForTextEffects() {
        MenuPanel menu = new MenuPanel(EffectRegistry.defaults());
        assertFalse(menu.copyButton().isEnabled());
        assertEquals(MenuPanel.COPY_DISABLED_TIP, menu.copyButton().getToolTipText());
        menu.setSelected(9);
        assertTrue(menu.copyButton().isEnabled());
        assertEquals(null, menu.copyButton().getToolTipText());
        menu.setSelected(2);
        assertFalse(menu.copyButton().isEnabled());
        menu.setSelected(9);
        menu.setSelected(1);
        assertFalse(menu.copyButton().isEnabled());
    }

    @Test
    void copyButtonClickCallsListenerAndIsNotFocusable() {
        MenuPanel menu = new MenuPanel(EffectRegistry.defaults());
        int[] calls = {0};
        menu.setCopyListener(() -> calls[0]++);
        menu.setSelected(9);
        assertFalse(menu.copyButton().isFocusable());
        menu.copyButton().doClick();
        assertEquals(1, calls[0]);
    }

    @Test
    void statusShowsMessageAndIsInitiallyEmpty() {
        MenuPanel menu = new MenuPanel(EffectRegistry.defaults());
        assertEquals("", menu.statusText());
        menu.showStatus("복사됨 (80x24자)");
        assertEquals("복사됨 (80x24자)", menu.statusText());
    }

    @Test
    void preferredHeightFitsHeaderNineRowsAndCopyArea() {
        MenuPanel menu = new MenuPanel(EffectRegistry.defaults());
        assertEquals(18 + 9 * 18 + 4 + (16 + 36 + 22) + 4 + 24 + 4 + 24 + 4 + 24 + 4 + 24 + 18,
                menu.getPreferredSize().height);
        assertEquals(180, menu.getPreferredSize().width);
        menu.setSize(menu.getPreferredSize());
        menu.doLayout();
        assertTrue(menu.effectList().getParent().getParent().getHeight() >= 9 * 18);
    }

    // ---- options area ----

    @Test
    void optionAreaFollowsTheSelectedEffect() {
        MenuPanel menu = new MenuPanel(EffectRegistry.defaults());
        assertTrue(menu.noOptionsShown()); // 1 original
        assertEquals(0, menu.optionCount());
        menu.setSelected(2);
        assertEquals(1, menu.optionCount());
        assertFalse(menu.noOptionsShown());
        assertEquals("블록 크기: 10", menu.optionLabel(0));
        assertEquals(10, menu.optionSlider(0).getValue());
        menu.setSelected(3);
        assertTrue(menu.noOptionsShown());
        menu.setSelected(4);
        assertEquals("임계값: 128", menu.optionLabel(0));
        menu.setSelected(7);
        assertEquals(4, menu.optionSlider(0).getMinimum());
        assertEquals("블록 크기: 8", menu.optionLabel(0));
        menu.setSelected(9);
        assertEquals(2, menu.optionCount());
        assertNull(menu.optionSlider(0));
        assertEquals("칸 크기: 8x16", menu.optionLabel(0));
        assertEquals(3, menu.optionButtons(0).size());
        assertTrue(menu.optionButtons(0).get(0).isSelected());
        assertFalse(menu.optionButtons(0).get(1).isSelected());
        assertEquals(1, menu.optionButtons(1).size());
        assertEquals("줄 끝 공백 제거", menu.optionButtons(1).get(0).getText());
        assertFalse(menu.optionButtons(1).get(0).isSelected());
        menu.setSelected(1);
        assertTrue(menu.noOptionsShown());
    }

    @Test
    void sliderChangeCallsListenerWithIndexAndValue() {
        MenuPanel menu = new MenuPanel(EffectRegistry.defaults());
        List<int[]> calls = new ArrayList<>();
        menu.setOptionListener((i, v) -> calls.add(new int[] {i, v}));
        menu.setSelected(2);
        assertTrue(calls.isEmpty()); // building the controls is silent
        menu.optionSlider(0).setValue(24);
        assertEquals(1, calls.size());
        assertEquals(0, calls.get(0)[0]);
        assertEquals(24, calls.get(0)[1]);
        assertEquals("블록 크기: 24", menu.optionLabel(0));
    }

    @Test
    void choiceAndToggleClicksCallListener() {
        MenuPanel menu = new MenuPanel(EffectRegistry.defaults());
        List<int[]> calls = new ArrayList<>();
        menu.setOptionListener((i, v) -> calls.add(new int[] {i, v}));
        menu.setSelected(9);
        menu.optionButtons(0).get(2).doClick();
        assertEquals(1, calls.size());
        assertArrayEqualsPair(calls.get(0), 0, 2);
        assertTrue(menu.optionButtons(0).get(2).isSelected());
        assertFalse(menu.optionButtons(0).get(0).isSelected());
        assertEquals("칸 크기: 4x8", menu.optionLabel(0));
        menu.optionButtons(0).get(2).doClick(); // same choice again: no change, no call
        assertEquals(1, calls.size());
        assertTrue(menu.optionButtons(0).get(2).isSelected());
        menu.optionButtons(1).get(0).doClick();
        assertEquals(2, calls.size());
        assertArrayEqualsPair(calls.get(1), 1, 1);
        assertTrue(menu.optionButtons(1).get(0).isSelected());
        menu.optionButtons(1).get(0).doClick();
        assertArrayEqualsPair(calls.get(2), 1, 0);
    }

    private static void assertArrayEqualsPair(int[] actual, int a, int b) {
        assertEquals(a, actual[0]);
        assertEquals(b, actual[1]);
    }

    @Test
    void setOptionValueAndSetSelectedDoNotNotify() {
        OptionState state = new OptionState(EffectRegistry.defaults());
        MenuPanel menu = new MenuPanel(EffectRegistry.defaults(), state);
        int[] calls = {0};
        menu.setOptionListener((i, v) -> calls[0]++);
        menu.setSelected(2);
        menu.setOptionValue(0, 40);
        assertEquals(40, menu.optionSlider(0).getValue());
        assertEquals("블록 크기: 40", menu.optionLabel(0));
        menu.setSelected(9);
        menu.setOptionValue(0, 1);
        assertTrue(menu.optionButtons(0).get(1).isSelected());
        menu.setOptionValue(1, 1);
        assertTrue(menu.optionButtons(1).get(0).isSelected());
        menu.setOptionValue(7, 3); // unknown index is ignored
        assertEquals(0, calls[0]);
    }

    @Test
    void controlsShowTheRememberedValuesWhenReturning() {
        EffectRegistry r = EffectRegistry.defaults();
        OptionState state = new OptionState(r);
        MenuPanel menu = new MenuPanel(r, state);
        state.set(2, 0, 30);
        state.set(9, 0, 1);
        state.set(9, 1, 1);
        menu.setSelected(2);
        assertEquals("블록 크기: 30", menu.optionLabel(0));
        menu.setSelected(9);
        assertTrue(menu.optionButtons(0).get(1).isSelected());
        assertTrue(menu.optionButtons(1).get(0).isSelected());
    }

    @Test
    void optionControlsAndButtonsAreNotFocusable() {
        MenuPanel menu = new MenuPanel(EffectRegistry.defaults());
        for (int n = 1; n <= 9; n++) {
            menu.setSelected(n);
            assertTrue(menu.allOptionControlsUnfocusable(), "effect " + n);
        }
        assertFalse(menu.pauseButton().isFocusable());
        assertFalse(menu.copyButton().isFocusable());
    }

    @Test
    void menuHeightDoesNotDependOnTheSelectedEffect() {
        MenuPanel menu = new MenuPanel(EffectRegistry.defaults());
        java.awt.Dimension before = menu.getPreferredSize();
        for (int n = 1; n <= 9; n++) {
            menu.setSelected(n);
            assertEquals(before, menu.getPreferredSize(), "effect " + n);
        }
        assertEquals(180, before.width);
        // fits the biggest option set (ASCII: choice + toggle) and every row stays visible
        menu.setSize(before);
        menu.setSelected(9);
        menu.doLayout();
        assertTrue(menu.effectList().getParent().getParent().getHeight() >= 9 * 18);
    }

    @Test
    void pauseButtonTogglesLookTextAndListener() {
        MenuPanel menu = new MenuPanel(EffectRegistry.defaults());
        int[] calls = {0};
        menu.setPauseListener(() -> calls[0]++);
        assertEquals(MenuPanel.PAUSE_TEXT, menu.pauseButton().getText());
        menu.pauseButton().doClick();
        assertEquals(1, calls[0]);
        menu.setPaused(true);
        assertEquals(MenuPanel.PAUSED_TEXT, menu.pauseButton().getText());
        assertEquals(1, calls[0]); // setPaused does not notify
        menu.setPaused(false);
        assertEquals(MenuPanel.PAUSE_TEXT, menu.pauseButton().getText());
    }

    @Test
    void alwaysOnTopButtonDefaultsOnAndNotifiesOnlyOnClick() {
        MenuPanel menu = new MenuPanel(EffectRegistry.defaults());
        java.util.List<Boolean> calls = new java.util.ArrayList<>();
        menu.setAlwaysOnTopListener(calls::add);
        assertEquals(MenuPanel.TOP_ON_TEXT, menu.alwaysOnTopButton().getText());
        assertFalse(menu.alwaysOnTopButton().isFocusable());
        menu.alwaysOnTopButton().doClick(0);
        assertEquals(java.util.List.of(false), calls);
        assertEquals(MenuPanel.TOP_OFF_TEXT, menu.alwaysOnTopButton().getText());
        menu.alwaysOnTopButton().doClick(0);
        assertEquals(java.util.List.of(false, true), calls);
        menu.setAlwaysOnTopState(false);
        assertEquals(2, calls.size()); // no notification
        assertEquals(MenuPanel.TOP_OFF_TEXT, menu.alwaysOnTopButton().getText());
        menu.setAlwaysOnTopState(true);
        assertEquals(MenuPanel.TOP_ON_TEXT, menu.alwaysOnTopButton().getText());
        assertEquals(2, calls.size());
    }

    @Test
    void alwaysOnTopTextFitsInnerWidth() {
        MenuPanel menu = new MenuPanel(EffectRegistry.defaults());
        java.awt.FontMetrics fm = menu.alwaysOnTopButton().getFontMetrics(menu.alwaysOnTopButton().getFont());
        assertTrue(fm.stringWidth(MenuPanel.TOP_ON_TEXT) <= 168);
        assertTrue(fm.stringWidth(MenuPanel.TOP_OFF_TEXT) <= 168);
    }
}
