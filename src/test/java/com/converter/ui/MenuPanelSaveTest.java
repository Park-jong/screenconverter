package com.converter.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.converter.effect.EffectRegistry;
import org.junit.jupiter.api.Test;

class MenuPanelSaveTest {

    @Test
    void saveButtonClickCallsListenerIsEnabledForAllEffectsAndNotFocusable() {
        MenuPanel menu = new MenuPanel(EffectRegistry.defaults());
        int[] calls = {0};
        menu.setSaveListener(() -> calls[0]++);
        assertEquals("이미지 저장", menu.saveButton().getText());
        assertFalse(menu.saveButton().isFocusable());
        for (int n = 1; n <= 9; n++) {
            menu.setSelected(n);
            assertTrue(menu.saveButton().isEnabled(), "effect " + n);
        }
        menu.saveButton().doClick(0);
        assertEquals(1, calls[0]);
    }

    @Test
    void lastSavedPathGoesToTooltip() {
        MenuPanel menu = new MenuPanel(EffectRegistry.defaults());
        menu.setLastSavedPath("C:\\x\\a.png");
        assertEquals("마지막 저장: C:\\x\\a.png", menu.saveButton().getToolTipText());
    }

    @Test
    void longStatusIsFittedToLabelWidthAndFullTextKeptInTooltip() {
        MenuPanel menu = new MenuPanel(EffectRegistry.defaults());
        String full = "저장됨 (" + ImageSaveAction.label(java.nio.file.Path.of("C:", "사진", "아주아주긴한글폴더이름입니다")) + ")";
        menu.showStatus(full);
        javax.swing.JLabel l = findStatus(menu);
        java.awt.FontMetrics fm = l.getFontMetrics(l.getFont());
        assertTrue(fm.stringWidth(l.getText()) <= 168, l.getText());
        assertTrue(l.getText().endsWith("…"));
        assertEquals(full, l.getToolTipText());

        menu.showStatus("복사됨");
        assertEquals("복사됨", menu.statusText());
    }

    private static javax.swing.JLabel findStatus(java.awt.Container c) {
        for (java.awt.Component k : c.getComponents()) {
            if (k instanceof javax.swing.JLabel l && l.getText() != null && l.getText().startsWith("저장됨")) {
                return l;
            }
            if (k instanceof java.awt.Container sub) {
                javax.swing.JLabel r = findStatus(sub);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }
}
