package com.converter.effect;

import java.util.List;
import java.util.Objects;

/**
 * Current option values of every registry entry, remembered per entry (switching to another
 * effect and back keeps the values). Starts at the option defaults. Not thread-safe: use on one
 * thread (the UI thread); {@link #create(int, int[])} itself only reads its arguments.
 */
public final class OptionState {

    private final EffectRegistry registry;
    private final int[][] values;

    public OptionState(EffectRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
        values = new int[registry.size()][];
        for (int i = 0; i < values.length; i++) {
            values[i] = registry.entries().get(i).defaultValues();
        }
    }

    /** Option definitions of a 1-based entry number (empty for unknown numbers). */
    public List<EffectOption> options(int number) {
        return registry.get(number).map(EffectRegistry.Entry::options).orElse(List.of());
    }

    /** Current value of an option; the default for an out-of-range option index. */
    public int get(int number, int optionIndex) {
        List<EffectOption> opts = options(number);
        if (optionIndex < 0 || optionIndex >= opts.size()) {
            return 0;
        }
        return values[number - 1][optionIndex];
    }

    /** Stores the value clamped into the option's range; returns the stored value. */
    public int set(int number, int optionIndex, int value) {
        List<EffectOption> opts = options(number);
        if (optionIndex < 0 || optionIndex >= opts.size()) {
            return 0;
        }
        int v = opts.get(optionIndex).clamp(value);
        values[number - 1][optionIndex] = v;
        return v;
    }

    /** One step up/down for an option (clamped); returns the stored value. */
    public int step(int number, int optionIndex, int direction) {
        List<EffectOption> opts = options(number);
        if (optionIndex < 0 || optionIndex >= opts.size()) {
            return 0;
        }
        return set(number, optionIndex, opts.get(optionIndex).stepped(get(number, optionIndex),
                direction));
    }

    /** Index of the first INT/CHOICE option of the entry (the one +/- steps), or -1. */
    public int firstSteppable(int number) {
        List<EffectOption> opts = options(number);
        for (int i = 0; i < opts.size(); i++) {
            if (opts.get(i).steppable()) {
                return i;
            }
        }
        return -1;
    }

    /** Copy of the current values of an entry (empty array for unknown numbers). */
    public int[] values(int number) {
        return registry.get(number).isPresent() ? values[number - 1].clone() : new int[0];
    }

    /** True if the effect for the current values can be built quickly. */
    public boolean isReady(int number) {
        return registry.get(number).map(e -> e.factory().isReady(values(number))).orElse(true);
    }

    /** Builds a new effect from the current values (may be slow if not {@link #isReady}). */
    public Effect create(int number) {
        return create(number, values(number));
    }

    /** Builds a new effect of the entry from explicit values; thread-safe. */
    public Effect create(int number, int[] vals) {
        EffectRegistry.Entry e = registry.get(number)
                .orElseThrow(() -> new IllegalArgumentException("unknown effect " + number));
        return e.factory().create(vals);
    }

    /** True if any option with target {@link EffectOption.Target#COPY_TRIM_TRAILING} is on. */
    public boolean trimTrailing(int number) {
        List<EffectOption> opts = options(number);
        for (int i = 0; i < opts.size(); i++) {
            if (opts.get(i).target() == EffectOption.Target.COPY_TRIM_TRAILING
                    && get(number, i) != 0) {
                return true;
            }
        }
        return false;
    }

    /** Builds expensive resources of every entry ahead of time (call off the UI thread). */
    public void prefetchAll() {
        for (EffectRegistry.Entry e : registry.entries()) {
            e.factory().prefetch();
        }
    }
}
