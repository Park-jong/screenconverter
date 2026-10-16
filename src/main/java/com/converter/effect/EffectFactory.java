package com.converter.effect;

/** Builds an effect from option values (one {@code int} per option, in option order). */
@FunctionalInterface
public interface EffectFactory {

    /** Creates a new effect instance; may be slow if {@link #isReady} is false. */
    Effect create(int[] values);

    /** True if {@link #create} returns quickly (no expensive resource still to be built). */
    default boolean isReady(int[] values) {
        return true;
    }

    /** Builds expensive resources ahead of time (call from a background thread). */
    default void prefetch() {
    }
}
