package com.converter.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.imageio.ImageIO;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageSaveActionTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-16T20:15:00Z"), ZoneOffset.UTC);

    @TempDir
    Path tmp;

    private static ImageSaveAction action(Path dir) {
        return new ImageSaveAction(() -> dir, CLOCK);
    }

    private static BufferedImage image(int type) {
        BufferedImage img = new BufferedImage(3, 2, type);
        int[] argb = {0xFF102030, 0x80FF0000, 0x0000FF00, 0xFFFFFFFF, 0xFF000000, 0xFF7F7F7F};
        for (int i = 0; i < argb.length; i++) {
            img.setRGB(i % 3, i / 3, argb[i]);
        }
        return img;
    }

    private static void assertSamePixels(BufferedImage a, BufferedImage b) {
        assertEquals(a.getWidth(), b.getWidth());
        assertEquals(a.getHeight(), b.getHeight());
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                assertEquals(a.getRGB(x, y), b.getRGB(x, y), x + "," + y);
            }
        }
    }

    @Test
    void fileNameFromClockAndCollisionSuffixes() {
        ImageSaveAction a = action(tmp);
        ImageSaveAction.Result r1 = a.save(image(BufferedImage.TYPE_INT_RGB));
        ImageSaveAction.Result r2 = a.save(image(BufferedImage.TYPE_INT_RGB));
        ImageSaveAction.Result r3 = a.save(image(BufferedImage.TYPE_INT_RGB));
        assertTrue(r1.success());
        assertEquals("converter-20261016-201500.png", r1.path().getFileName().toString());
        assertEquals("converter-20261016-201500-1.png", r2.path().getFileName().toString());
        assertEquals("converter-20261016-201500-2.png", r3.path().getFileName().toString());
    }

    @Test
    void existingFileIsNeverOverwritten() throws IOException {
        Path existing = tmp.resolve("converter-20261016-201500.png");
        Files.write(existing, new byte[] {1, 2, 3});
        ImageSaveAction.Result r = action(tmp).save(image(BufferedImage.TYPE_INT_RGB));
        assertTrue(r.success());
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(existing));
        assertEquals("converter-20261016-201500-1.png", r.path().getFileName().toString());
    }

    @Test
    void savedPngHasSamePixelsForArgbAndRgb() throws IOException {
        for (int type : new int[] {BufferedImage.TYPE_INT_ARGB, BufferedImage.TYPE_INT_RGB}) {
            BufferedImage src = image(type);
            ImageSaveAction.Result r = action(tmp).save(src);
            assertTrue(r.success());
            assertSamePixels(src, ImageIO.read(r.path().toFile()));
        }
    }

    @Test
    void copyIsIndependentAndPixelExact() {
        BufferedImage src = image(BufferedImage.TYPE_INT_ARGB);
        BufferedImage copy = ImageSaveAction.copyOf(src);
        assertSamePixels(src, copy);
        src.setRGB(0, 0, 0xFFABCDEF);
        assertEquals(0xFF102030, copy.getRGB(0, 0));
    }

    @Test
    void rejectsNullImage() {
        ImageSaveAction.Result r = action(tmp).save(null);
        assertFalse(r.success());
        assertEquals(ImageSaveAction.NO_IMAGE, r.message());
    }

    @Test
    void rejectsZeroSizedImage() {
        BufferedImage empty = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB) {
            @Override
            public int getWidth() {
                return 0;
            }
        };
        assertFalse(action(tmp).save(empty).success());
    }

    @Test
    void unwritableFolderGivesFailureMessage() throws IOException {
        Path file = Files.writeString(tmp.resolve("afile"), "x");
        ImageSaveAction.Result r = action(file).save(image(BufferedImage.TYPE_INT_RGB));
        assertFalse(r.success());
        assertTrue(r.message().startsWith("저장 실패 ("), r.message());
        ImageSaveAction.Result r2 = new ImageSaveAction(() -> {
            throw new IOException("no");
        }, CLOCK).save(image(BufferedImage.TYPE_INT_RGB));
        assertEquals("저장 실패 (폴더 사용 불가)", r2.message());
    }

    @Test
    void defaultFolderOrderAndFallback() throws IOException {
        Path home = tmp.resolve("home");
        Files.createDirectories(home);
        assertEquals(home.resolve("Pictures").resolve("Converter"),
                ImageSaveAction.defaultFolder(null, home));
        assertTrue(Files.isDirectory(home.resolve("Pictures").resolve("Converter")));

        Path home2 = tmp.resolve("home2");
        Files.createDirectories(home2);
        Files.writeString(home2.resolve("Pictures"), "not a folder");
        assertEquals(home2.resolve("Converter"), ImageSaveAction.defaultFolder("", home2));
        assertTrue(Files.isDirectory(home2.resolve("Converter")));

        Path custom = tmp.resolve("custom").resolve("sub");
        assertEquals(custom, ImageSaveAction.defaultFolder(custom.toString(), home));
        assertTrue(Files.isDirectory(custom));
    }

    @Test
    void messagesFitStatusWidth() {
        FontMetrics fm = new JLabel().getFontMetrics(new Font("Malgun Gothic", Font.PLAIN, 12));
        String ok = "저장됨 (" + ImageSaveAction.label(Path.of("C:\\Users\\park\\Pictures\\Converter")) + ")";
        assertEquals("저장됨 (Pictures\\Converter)", ok);
        // label() no longer cuts by character count; the status line fits by pixel width (TextFit).
        String longDir = TextFit.fit("저장됨 ("
                + ImageSaveAction.label(Path.of("C:\\aaaaaaaaaaaaaaaaaaaa\\bbbbbbbbbbbbbbbbbbbbbbbb")) + ")",
                168, fm::stringWidth);
        for (String s : new String[] {ok, longDir, ImageSaveAction.NO_IMAGE, ImageSaveAction.BUSY,
            "저장 실패 (폴더 사용 불가)", "저장 실패 (PNG 변환 불가)", "저장 실패 (쓰기 오류)"}) {
            assertTrue(fm.stringWidth(s) <= 168, s + " " + fm.stringWidth(s));
        }
    }
}
