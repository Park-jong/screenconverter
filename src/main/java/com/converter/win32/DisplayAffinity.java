package com.converter.win32;

import java.awt.Component;

/** Controls whether a window appears in screen captures. No native types are exposed. */
public interface DisplayAffinity {

    /**
     * Excludes the (already displayable) window from screen capture.
     *
     * @return true only if the OS reports the exclude-from-capture affinity afterwards
     */
    boolean excludeFromCapture(Component window);
}
