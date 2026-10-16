package com.converter.ui;

/** Pure helpers for telling the user that the window could not be excluded from capture. */
final class ExclusionNotice {

    static final String SUFFIX = " (제외 실패)";
    static final String MESSAGE =
            "warning: window could not be excluded from screen capture; the overlay may capture itself";

    private ExclusionNotice() {
    }

    /** Title with the failure suffix appended when exclusion is not confirmed. */
    static String title(String base, boolean captureExcluded) {
        return captureExcluded ? base : base + SUFFIX;
    }
}
