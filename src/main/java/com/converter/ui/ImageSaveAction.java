package com.converter.ui;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import javax.imageio.ImageIO;

/**
 * UI-independent "save shown image" logic: image -> PNG bytes -> new file in the save folder ->
 * status message. The folder and the clock are injected; nothing here touches Swing.
 */
public final class ImageSaveAction {

    static final String NO_IMAGE = "저장할 화면 없음";
    static final String BUSY = "저장 중...";
    /** Property that overrides the save folder. */
    public static final String DIR_PROPERTY = "converter.saveDir";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int MAX_NAME_TRIES = 10_000;

    /** Resolves (and creates) the folder to save into. */
    @FunctionalInterface
    public interface FolderProvider {
        Path get() throws IOException;
    }

    /** Outcome of one save attempt; {@code path} is null unless it succeeded. */
    public record Result(boolean success, String message, Path path) {
    }

    private final FolderProvider folder;
    private final Clock clock;

    public ImageSaveAction(FolderProvider folder, Clock clock) {
        this.folder = Objects.requireNonNull(folder, "folder");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Default folder: {@code converter.saveDir} if set, else {@code <home>/Pictures/Converter},
     * else {@code <home>/Converter}. Directories are created as needed.
     */
    public static Path defaultFolder(String override, Path home) throws IOException {
        if (override != null && !override.isBlank()) {
            return Files.createDirectories(Path.of(override));
        }
        try {
            return Files.createDirectories(home.resolve("Pictures").resolve("Converter"));
        } catch (IOException e) {
            return Files.createDirectories(home.resolve("Converter"));
        }
    }

    /** Independent pixel-exact copy, so later changes to the source cannot affect the saved data. */
    public static BufferedImage copyOf(BufferedImage src) {
        ColorModel cm = src.getColorModel();
        return new BufferedImage(cm, src.copyData(null), cm.isAlphaPremultiplied(), null);
    }

    /** Saves the image as a PNG (blocking; call off the EDT). Never throws. */
    public Result save(BufferedImage image) {
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            return new Result(false, NO_IMAGE, null);
        }
        Path dir;
        try {
            dir = folder.get();
        } catch (IOException | RuntimeException e) {
            return fail("폴더 사용 불가");
        }
        byte[] png;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", out)) {
                return fail("PNG 변환 불가");
            }
            png = out.toByteArray();
        } catch (IOException | RuntimeException e) {
            return fail("PNG 변환 오류");
        }
        String base = "converter-" + LocalDateTime.now(clock).format(STAMP);
        try {
            for (int i = 0; i < MAX_NAME_TRIES; i++) {
                Path target = dir.resolve(i == 0 ? base + ".png" : base + "-" + i + ".png");
                try {
                    Files.write(target, png, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                } catch (FileAlreadyExistsException exists) {
                    continue;
                }
                return new Result(true, "저장됨 (" + label(dir) + ")", target);
            }
            return fail("파일명 중복");
        } catch (IOException | RuntimeException e) {
            return fail("쓰기 오류");
        }
    }

    private static Result fail(String reason) {
        return new Result(false, "저장 실패 (" + reason + ")", null);
    }

    /** Short folder label such as "Pictures\Converter", fitted to the status line by pixel width on display. */
    static String label(Path dir) {
        int n = dir.getNameCount();
        String s;
        if (n == 0) {
            s = String.valueOf(dir.getRoot());
        } else if (n == 1) {
            s = dir.getName(0).toString();
        } else {
            s = dir.getName(n - 2) + "\\" + dir.getName(n - 1);
        }
        return s;
    }
}
