package com.converter.capture;

import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;

/** {@link ScreenGrabber} backed by a single reused {@link Robot}. */
public final class RobotScreenGrabber implements ScreenGrabber {

    private final Robot robot;

    public RobotScreenGrabber() throws AWTException {
        this.robot = new Robot();
    }

    @Override
    public BufferedImage grab(Rectangle screenRect) {
        if (screenRect == null || screenRect.width <= 0 || screenRect.height <= 0) {
            return null;
        }
        return robot.createScreenCapture(screenRect);
    }
}
