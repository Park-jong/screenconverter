package com.converter.ui;

/** Writes plain text to a clipboard. */
public interface TextClipboard {

    /** @return true if the text was placed on the clipboard, false on any failure */
    boolean setText(String text);
}
