package com.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MainTest {

    private static final String KEY = "swing.bufferPerWindow";

    private static void withProperty(String initial, Runnable body) {
        String original = System.getProperty(KEY);
        try {
            if (initial == null) {
                System.clearProperty(KEY);
            } else {
                System.setProperty(KEY, initial);
            }
            body.run();
        } finally {
            if (original == null) {
                System.clearProperty(KEY);
            } else {
                System.setProperty(KEY, original);
            }
        }
    }

    @Test
    void setsBufferPerWindowWhenAbsent() {
        withProperty(null, () -> {
            Main.configureRendering();
            assertEquals("true", System.getProperty(KEY));
        });
    }

    @Test
    void keepsExplicitUserValue() {
        withProperty("false", () -> {
            Main.configureRendering();
            assertEquals("false", System.getProperty(KEY));
        });
    }
}
