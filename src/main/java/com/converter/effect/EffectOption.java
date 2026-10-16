package com.converter.effect;

import java.util.List;
import java.util.Objects;

/**
 * Definition of one adjustable option of an effect. Every option value is an {@code int}:
 * an INT option holds the number itself (min..max, moved in {@code step}s), a CHOICE option the
 * index of one of its labels, a BOOL option 0 or 1. Immutable, pure logic.
 */
public final class EffectOption {

    /** How the option is edited. */
    public enum Kind { INT, CHOICE, BOOL }

    /** What the option changes. */
    public enum Target {
        /** The effect instance (a change builds a new effect). */
        EFFECT,
        /** Only the copied text: strip trailing spaces of every line. */
        COPY_TRIM_TRAILING
    }

    private final String name;
    private final Kind kind;
    private final int min;
    private final int max;
    private final int step;
    private final int defaultValue;
    private final List<String> labels;
    private final Target target;

    private EffectOption(String name, Kind kind, int min, int max, int step, int defaultValue,
            List<String> labels, Target target) {
        this.name = Objects.requireNonNull(name, "name");
        this.kind = kind;
        this.min = min;
        this.max = max;
        this.step = step;
        this.defaultValue = defaultValue;
        this.labels = labels;
        this.target = Objects.requireNonNull(target, "target");
    }

    /** Integer option; {@code defaultValue} is clamped into the range. */
    public static EffectOption integer(String name, int min, int max, int step, int defaultValue) {
        if (min > max) {
            throw new IllegalArgumentException("min " + min + " > max " + max);
        }
        if (step < 1) {
            throw new IllegalArgumentException("step must be >= 1 but was " + step);
        }
        return new EffectOption(name, Kind.INT, min, max, step,
                Math.max(min, Math.min(max, defaultValue)), List.of(), Target.EFFECT);
    }

    /** Discrete option; values are label indices 0..labels-1. */
    public static EffectOption choice(String name, List<String> labels, int defaultIndex) {
        if (labels.isEmpty()) {
            throw new IllegalArgumentException("labels must not be empty");
        }
        return new EffectOption(name, Kind.CHOICE, 0, labels.size() - 1, 1,
                Math.max(0, Math.min(labels.size() - 1, defaultIndex)), List.copyOf(labels),
                Target.EFFECT);
    }

    /** On/off option (0 = off, 1 = on). */
    public static EffectOption bool(String name, boolean defaultValue, Target target) {
        return new EffectOption(name, Kind.BOOL, 0, 1, 1, defaultValue ? 1 : 0, List.of(), target);
    }

    public String name() {
        return name;
    }

    public Kind kind() {
        return kind;
    }

    public int min() {
        return min;
    }

    public int max() {
        return max;
    }

    public int step() {
        return step;
    }

    public int defaultValue() {
        return defaultValue;
    }

    /** Labels of a CHOICE option (empty for other kinds). */
    public List<String> labels() {
        return labels;
    }

    public Target target() {
        return target;
    }

    /** True if changing this option must build a new effect instance. */
    public boolean affectsEffect() {
        return target == Target.EFFECT;
    }

    /** True for INT and CHOICE options, which the +/- keys can step (BOOL ones are toggled). */
    public boolean steppable() {
        return kind != Kind.BOOL;
    }

    /** Value limited to min..max. */
    public int clamp(int value) {
        return Math.max(min, Math.min(max, value));
    }

    /** One step up (direction &gt; 0) or down (&lt; 0) from {@code value}, clamped to the range. */
    public int stepped(int value, int direction) {
        if (direction == 0) {
            return clamp(value);
        }
        return clamp(value + (direction > 0 ? step : -step));
    }

    /** Text for a value: the number, the label, or "켜짐"/"꺼짐". */
    public String format(int value) {
        int v = clamp(value);
        return switch (kind) {
            case INT -> Integer.toString(v);
            case CHOICE -> labels.get(v);
            case BOOL -> v != 0 ? "켜짐" : "꺼짐";
        };
    }
}
