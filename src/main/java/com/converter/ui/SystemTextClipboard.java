package com.converter.ui;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

/** {@link TextClipboard} backed by the system clipboard. Failures are reported, never thrown. */
public final class SystemTextClipboard implements TextClipboard {

    @Override
    public boolean setText(String text) {
        try {
            StringSelection sel = new StringSelection(text);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
            return true;
        } catch (Exception e) { // HeadlessException, IllegalStateException (clipboard busy), ...
            return false;
        }
    }
}
