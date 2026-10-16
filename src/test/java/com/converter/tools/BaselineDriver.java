package com.converter.tools;

import java.awt.Dimension;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Measurement tool (not part of the product jar). Starts {@link MovingPatternWindow} under the
 * overlay, then for each size x effect launches {@code java -Dconverter.metrics=true -jar
 * converter.jar}, selects the effect via a Robot key press, skips a 5 s warm-up and aggregates
 * the next three 5 s [metrics] lines plus process CPU time and GC activity over that period.
 *
 * <p>Usage: {@code BaselineDriver <jar> <workDir> <effects csv> <sizes csv WxH,...>}
 */
public final class BaselineDriver {

    private static final Pattern GC_LINE =
            Pattern.compile("^\\[(\\d+\\.\\d+)s\\].*Pause.* (\\d+\\.\\d+)ms$");
    private static final Pattern NUMBER = Pattern.compile("=(-?\\d+(?:\\.\\d+)?)");

    private BaselineDriver() {
    }

    public static void main(String[] args) throws Exception {
        File jar = new File(args[0]);
        File work = new File(args[1]);
        work.mkdirs();
        String[] effects = args[2].split(",");
        String[] sizes = args[3].split(",");
        String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator
                + "java";
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();

        File patLog = new File(work, "pattern.log");
        Process pattern = new ProcessBuilder(javaBin, "-cp", System.getProperty("java.class.path"),
                MovingPatternWindow.class.getName(), "0", "0",
                String.valueOf(screen.width), String.valueOf(screen.height))
                .redirectErrorStream(true).redirectOutput(patLog).start();
        Thread.sleep(2500);
        Robot robot = new Robot();
        System.out.println("screen " + screen.width + "x" + screen.height);
        System.out.println("| size | effect | fps | interval avg/p95/max ms | grab avg/p95 ms"
                + " | effect avg/p95 ms | latency avg/p95 ms | attempts / displayed"
                + " | CPU % of 1 core | GC count / ms |");
        try {
            for (String size : sizes) {
                String[] wh = size.split("x");
                int w = Integer.parseInt(wh[0]);
                int h = Integer.parseInt(wh[1]);
                for (String eff : effects) {
                    runOne(javaBin, jar, work, robot, 100, 60, w, h, Integer.parseInt(eff));
                }
            }
        } finally {
            pattern.destroyForcibly();
            pattern.waitFor();
        }
    }

    private static void runOne(String javaBin, File jar, File work, Robot robot, int x, int y,
            int w, int h, int effect) throws Exception {
        String tag = w + "x" + h + "-" + effect;
        File gcLog = new File(work, "gc-" + tag + ".log");
        File err = new File(work, "err-" + tag + ".log");
        Files.deleteIfExists(gcLog.toPath());
        long launch = System.nanoTime();
        List<String> cmd = new ArrayList<>(List.of(javaBin, "-Dconverter.metrics=true",
                "-Dconverter.bounds=" + x + "," + y + "," + w + "," + h,
                "-Xlog:gc:file=" + gcLog.getPath() + ":uptime"));
        String extra = System.getProperty("driver.jvmargs"); // e.g. -Ddriver.jvmargs=-Dconverter.fps=60
        if (extra != null && !extra.isBlank()) {
            cmd.addAll(List.of(extra.trim().split("\\s+")));
        }
        cmd.add("-jar");
        cmd.add(jar.getPath());
        Process p = new ProcessBuilder(cmd).redirectError(err).start();
        List<String> lines = new ArrayList<>();
        List<Long> arrivals = new ArrayList<>();
        Thread reader = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(),
                    StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    synchronized (lines) {
                        lines.add(line);
                        arrivals.add(System.nanoTime());
                    }
                }
            } catch (IOException ignored) {
                // process killed
            }
        });
        reader.setDaemon(true);
        reader.start();
        try {
            Thread.sleep(3500);
            robot.mouseMove(x + w / 3, y + 15); // title bar: focus the overlay
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            Thread.sleep(300);
            robot.keyPress(KeyEvent.VK_0 + effect);
            robot.keyRelease(KeyEvent.VK_0 + effect);
            long key = System.nanoTime();

            long cpu1 = -1;
            long cpu3 = -1;
            long t1 = 0;
            long t3 = 0;
            int taken = 0;
            int seen = 0;
            double[][] vals = new double[3][];
            long deadline = key + 40_000_000_000L;
            while (taken < 3 && System.nanoTime() < deadline) {
                Thread.sleep(100);
                String line = null;
                long at = 0;
                synchronized (lines) {
                    if (seen < lines.size()) {
                        line = lines.get(seen);
                        at = arrivals.get(seen);
                        seen++;
                    }
                }
                if (line == null || !line.startsWith("[metrics]") || at < key + 5_000_000_000L) {
                    continue;
                }
                if (taken == 0) {
                    cpu1 = cpuNanos(p);
                    t1 = at;
                }
                vals[taken++] = parse(line);
                if (taken == 3) {
                    cpu3 = cpuNanos(p);
                    t3 = at;
                }
            }
            if (taken < 3) {
                System.out.println("| " + w + "x" + h + " | " + effect + " | INCOMPLETE |");
                return;
            }
            // value order: fps, ivAvg, ivP95, ivMax, grAvg, grP95, efAvg, efP95, laAvg, laP95,
            // attempts, displayed
            double[] a = new double[vals[0].length];
            for (int i = 0; i < a.length; i++) {
                for (double[] v : vals) {
                    a[i] += v[i] / 3;
                }
            }
            double maxInterval = Math.max(vals[0][3], Math.max(vals[1][3], vals[2][3]));
            double cpuPct = (cpu3 - cpu1) * 100.0 / (t3 - t1);
            double up1 = (t1 - launch) / 1e9 - 5.0;
            double up3 = (t3 - launch) / 1e9;
            int gcCount = 0;
            double gcMs = 0;
            if (gcLog.exists()) {
                for (String gl : Files.readAllLines(gcLog.toPath(), StandardCharsets.UTF_8)) {
                    Matcher m = GC_LINE.matcher(gl.trim());
                    if (m.find()) {
                        double up = Double.parseDouble(m.group(1));
                        if (up >= up1 && up <= up3) {
                            gcCount++;
                            gcMs += Double.parseDouble(m.group(2));
                        }
                    }
                }
            }
            System.out.printf(java.util.Locale.ROOT,
                    "| %dx%d | %d | %.1f | %.1f / %.1f / %.1f | %.1f / %.1f | %.1f / %.1f"
                            + " | %.1f / %.1f | %.0f / %.0f / %.0f / %.0f | %.0f | %d / %.1f |%n",
                    w, h, effect, a[0], a[1], a[2], maxInterval, a[4], a[5], a[6], a[7],
                    a[8], a[9], vals[2][10], vals[2][11], vals[2][12], vals[2][13], cpuPct,
                    gcCount, gcMs);
            System.out.flush();
        } finally {
            p.destroyForcibly();
            p.waitFor();
            Thread.sleep(500);
        }
        if (err.length() > 0) {
            System.out.println("STDERR NOT EMPTY: " + err + " (" + err.length() + " bytes)");
        }
    }

    private static long cpuNanos(Process p) {
        return p.info().totalCpuDuration().orElse(Duration.ZERO).toNanos();
    }

    private static double[] parse(String line) {
        List<Double> out = new ArrayList<>();
        Matcher m = NUMBER.matcher(line);
        while (m.find()) {
            out.add(Double.parseDouble(m.group(1)));
        }
        double[] r = new double[out.size()];
        for (int i = 0; i < r.length; i++) {
            r[i] = out.get(i);
        }
        return r;
    }
}
