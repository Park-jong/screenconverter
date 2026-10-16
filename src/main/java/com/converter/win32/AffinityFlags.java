package com.converter.win32;

/** Display-affinity constants and pure decision logic (no native access). */
final class AffinityFlags {

    static final int WDA_NONE = 0x0;
    static final int WDA_MONITOR = 0x1;
    static final int WDA_EXCLUDEFROMCAPTURE = 0x11;

    private AffinityFlags() {
    }

    /** True if the value read back from the OS means "excluded from capture". */
    static boolean isExcluded(int affinity) {
        return affinity == WDA_EXCLUDEFROMCAPTURE;
    }
}
