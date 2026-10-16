package com.converter.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.converter.win32.DisplayAffinity;
import java.awt.Component;
import org.junit.jupiter.api.Test;

class ExclusionNoticeTest {

    private static DisplayAffinity fake(boolean result) {
        return (Component window) -> result;
    }

    @Test
    void successKeepsTitle() {
        boolean excluded = fake(true).excludeFromCapture(null);
        assertEquals("Converter - 모자이크", ExclusionNotice.title("Converter - 모자이크", excluded));
    }

    @Test
    void failureAppendsSuffixOnce() {
        boolean excluded = fake(false).excludeFromCapture(null);
        assertEquals("Converter - 모자이크 (제외 실패)",
                ExclusionNotice.title("Converter - 모자이크", excluded));
    }
}
