/**
 * effect package: pure image transformations.
 *
 * <p>Import rule (allowlist): every qualified JDK/library name used in code, in imports or in
 * bodies, must be one of java.awt.Color, Font, FontMetrics, Graphics2D, RenderingHints, or start
 * with java.awt.image., java.awt.font., java.util. (including function), java.lang., java.io.,
 * java.nio. or com.converter.effect. Everything else is rejected, e.g. javax.swing.*, the
 * java.awt.Component family, Toolkit, Robot, com.sun.jna.*, com.converter.ui, com.converter.win32
 * (java2d image rendering is not a component). Enforced by EffectPackageImportsTest.
 */
package com.converter.effect;
