package com.converter.win32;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import java.awt.Component;

/** {@link DisplayAffinity} using user32 SetWindowDisplayAffinity (Windows 10 2004+). */
public final class WindowsDisplayAffinity implements DisplayAffinity {

    private interface User32Affinity extends StdCallLibrary {
        boolean SetWindowDisplayAffinity(WinDef.HWND hWnd, int affinity);

        boolean GetWindowDisplayAffinity(WinDef.HWND hWnd, IntByReference affinity);
    }

    @Override
    public boolean excludeFromCapture(Component window) {
        try {
            if (window == null
                    || !System.getProperty("os.name", "").toLowerCase().contains("windows")) {
                return false;
            }
            Pointer p = Native.getComponentPointer(window);
            if (p == null) {
                System.err.println("DisplayAffinity: no native window handle");
                return false;
            }
            WinDef.HWND hwnd = new WinDef.HWND(p);
            User32Affinity user32 = Native.load("user32", User32Affinity.class,
                    W32APIOptions.DEFAULT_OPTIONS);
            if (!user32.SetWindowDisplayAffinity(hwnd, AffinityFlags.WDA_EXCLUDEFROMCAPTURE)) {
                System.err.println("DisplayAffinity: SetWindowDisplayAffinity failed, error "
                        + Kernel32.INSTANCE.GetLastError());
                return false;
            }
            IntByReference actual = new IntByReference();
            if (!user32.GetWindowDisplayAffinity(hwnd, actual)) {
                System.err.println("DisplayAffinity: GetWindowDisplayAffinity failed, error "
                        + Kernel32.INSTANCE.GetLastError());
                return false;
            }
            return AffinityFlags.isExcluded(actual.getValue());
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            return false;
        }
    }
}
