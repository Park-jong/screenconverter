package com.converter.effect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Ordered list of named effects, looked up by 1-based number (the digit key the user presses).
 * New effects are added in {@link #defaults()} (or via {@link #register}). Pure logic.
 *
 * <p>An entry may declare adjustable {@link EffectOption}s and an {@link EffectFactory} that
 * builds a new (immutable) effect from option values; {@link Entry#effect()} is always the
 * instance made from the default values.
 */
public final class EffectRegistry {

    /** One registered effect. */
    public record Entry(String name, Effect effect, List<EffectOption> options,
            EffectFactory factory) {
        public Entry {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(effect, "effect");
            options = List.copyOf(options);
            Objects.requireNonNull(factory, "factory");
        }

        /** Fixed effect without options. */
        public Entry(String name, Effect effect) {
            this(name, effect, List.of(), v -> effect);
        }

        /** Default value of every option, in option order. */
        public int[] defaultValues() {
            int[] v = new int[options.size()];
            for (int i = 0; i < v.length; i++) {
                v[i] = options.get(i).defaultValue();
            }
            return v;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    /** Appends an effect; it gets the next number (size after adding). */
    public EffectRegistry register(String name, Effect effect) {
        entries.add(new Entry(name, effect));
        return this;
    }

    /** Appends an effect built by {@code factory}; its default instance uses the default values. */
    public EffectRegistry register(String name, List<EffectOption> options, EffectFactory factory) {
        int[] defaults = new int[options.size()];
        for (int i = 0; i < defaults.length; i++) {
            defaults[i] = options.get(i).defaultValue();
        }
        entries.add(new Entry(name, factory.create(defaults), options, factory));
        return this;
    }

    /** Number of registered effects. */
    public int size() {
        return entries.size();
    }

    /** Entry for a 1-based number, or empty if the number is not registered. */
    public Optional<Entry> get(int number) {
        if (number < 1 || number > entries.size()) {
            return Optional.empty();
        }
        return Optional.of(entries.get(number - 1));
    }

    /** Entries in registration order (read-only). */
    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    /** Option definitions used by {@link #defaults()}. */
    static final int BLOCK_MIN = 2;
    static final int BLOCK_MAX = 64;
    static final int BLOCK_STEP = 2;

    private static List<EffectOption> blockSize(int min, int def) {
        return List.of(EffectOption.integer("블록 크기", min, BLOCK_MAX, BLOCK_STEP, def));
    }

    private static EffectFactory dot(Palette palette, boolean grid) {
        return v -> new PixelDotEffect(v[0], palette, grid);
    }

    /** Default set: 1 original, 2 mosaic, 3 grayscale, 4 two-tone grayscale,
     * 5-8 pixel-dot variants 9 ASCII art. Add new effects here. */
    public static EffectRegistry defaults() {
        List<String> cellLabels = new ArrayList<>();
        for (int[] c : AsciiGlyphCache.CELL_SIZES) {
            cellLabels.add(c[0] + "x" + c[1]);
        }
        AsciiGlyphCache glyphs = new AsciiGlyphCache();
        EffectFactory ascii = new EffectFactory() {
            @Override
            public Effect create(int[] v) {
                int[] c = AsciiGlyphCache.CELL_SIZES.get(v[0]);
                return new AsciiArtEffect(glyphs.get(c[0], c[1]));
            }

            @Override
            public boolean isReady(int[] v) {
                int[] c = AsciiGlyphCache.CELL_SIZES.get(v[0]);
                return glyphs.isReady(c[0], c[1]);
            }

            @Override
            public void prefetch() {
                glyphs.prefetchAll();
            }
        };
        return new EffectRegistry()
                .register("원본", new IdentityEffect())
                .register("모자이크", blockSize(BLOCK_MIN, MosaicEffect.DEFAULT_BLOCK_SIZE),
                        v -> new MosaicEffect(v[0]))
                .register("흑백", new GrayscaleEffect())
                .register("흑백 2색", List.of(EffectOption.integer("임계값",
                        GrayscaleEffect.MIN_THRESHOLD, GrayscaleEffect.MAX_THRESHOLD, 8, 128)),
                        v -> new GrayscaleEffect(v[0]))
                .register("도트 16색", blockSize(BLOCK_MIN, PixelDotEffect.DEFAULT_BLOCK_SIZE),
                        dot(Palette.EGA_16, false))
                .register("도트 게임보이", blockSize(BLOCK_MIN, PixelDotEffect.DEFAULT_BLOCK_SIZE),
                        dot(Palette.GAMEBOY_4, false))
                .register("도트 격자", blockSize(PixelDotEffect.MIN_GRID_BLOCK_SIZE,
                        PixelDotEffect.DEFAULT_BLOCK_SIZE), dot(Palette.NONE, true))
                .register("도트 16색+격자", blockSize(PixelDotEffect.MIN_GRID_BLOCK_SIZE,
                        PixelDotEffect.DEFAULT_BLOCK_SIZE), dot(Palette.EGA_16, true))
                .register("ASCII 아트", List.of(
                        EffectOption.choice("칸 크기", cellLabels, 0),
                        EffectOption.bool("줄 끝 공백 제거", false,
                                EffectOption.Target.COPY_TRIM_TRAILING)), ascii);
    }
}
