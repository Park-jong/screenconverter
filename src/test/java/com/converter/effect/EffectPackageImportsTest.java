package com.converter.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Allowlist check for the effect package sources (Maven runs from the project root): every
 * qualified JDK/library name that appears in code (imports and fully qualified uses in bodies)
 * must be on the allowlist; anything else fails.
 */
class EffectPackageImportsTest {

    private static final Path DIR = Path.of("src/main/java/com/converter/effect");

    /** Allowed exactly (the class itself or its members, e.g. java.awt.Color.RED). */
    private static final Set<String> ALLOWED_EXACT = Set.of(
            "java.awt.Color", "java.awt.Font", "java.awt.FontMetrics", "java.awt.Graphics2D",
            "java.awt.RenderingHints");
    /** Allowed by package prefix. */
    private static final List<String> ALLOWED_PREFIXES = List.of(
            "java.awt.image.", "java.awt.font.", "java.util.", "java.lang.", "java.io.",
            "java.nio.", "com.converter.effect.");

    private static final Pattern QUALIFIED = Pattern.compile(
            "\\b(java|javax|com|org|jdk|sun)\\.[a-z]+(\\.[A-Za-z_*][\\w*]*)+");

    @Test
    void effectPackageUsesOnlyAllowedNames() throws IOException {
        assertTrue(Files.isDirectory(DIR), "run from the project root: " + DIR.toAbsolutePath());
        List<Path> sources;
        try (Stream<Path> s = Files.list(DIR)) {
            sources = s.filter(f -> f.toString().endsWith(".java")).collect(Collectors.toList());
        }
        assertFalse(sources.isEmpty(), "no sources found in " + DIR.toAbsolutePath());
        List<String> violations = new ArrayList<>();
        for (Path p : sources) {
            for (String v : findViolations(Files.readString(p))) {
                violations.add(p.getFileName() + ": " + v);
            }
        }
        if (!violations.isEmpty()) {
            fail("names outside the allowlist: " + violations);
        }
    }

    @Test
    void detectorFlagsNamesOutsideTheAllowlist() {
        assertEquals(List.of("java.awt.Button"), findViolations("import java.awt.Button;"));
        assertEquals(List.of("java.awt.Toolkit.getDefaultToolkit"),
                findViolations("class A { void f() { java.awt.Toolkit.getDefaultToolkit(); } }"));
        assertEquals(List.of("javax.swing.JPanel"), findViolations("import javax.swing.JPanel;"));
        assertEquals(List.of("com.sun.jna.Native"), findViolations("import com.sun.jna.Native;"));
        assertEquals(List.of("com.converter.ui.X"), findViolations("import com.converter.ui.X;"));
        assertEquals(List.of("java.awt.*"), findViolations("import java.awt.*;"));
        assertEquals(List.of("java.awt.Robot"), findViolations("import static java.awt.Robot.X;"));
    }

    @Test
    void detectorSplitsStatementsOnOneLine() {
        assertEquals(List.of("java.awt.Button", "javax.swing.JPanel"),
                findViolations("import java.awt.Button; import javax.swing.JPanel;"));
        assertEquals(List.of("javax.swing.JPanel"),
                findViolations("import java.awt.Color; import javax.swing.JPanel;"));
    }

    @Test
    void detectorIgnoresCommentsAndStrings() {
        String quote = "\"";
        String src = "// import java.awt.Button;\n"
                + "/* javax.swing.JPanel */\n"
                + "/** see java.awt.Toolkit.getDefaultToolkit() */\n"
                + "class A { String s = " + quote + "com.sun.jna.Native \\" + quote
                + " javax.swing.JFrame" + quote + ";\n"
                + " char c = '" + quote + "'; String t = " + quote + quote + quote
                + "\n java.awt.Robot.x\n " + quote + quote + quote + "; }";
        assertEquals(List.of(), findViolations(src));
    }

    @Test
    void detectorAcceptsAllowedNames() {
        String src = "package com.converter.effect;\n"
                + "import java.awt.Color; import java.awt.Font; import java.awt.FontMetrics;\n"
                + "import java.awt.Graphics2D; import java.awt.RenderingHints;\n"
                + "import java.awt.image.BufferedImage; import java.awt.font.FontRenderContext;\n"
                + "import java.util.function.IntUnaryOperator; import java.util.List;\n"
                + "import java.io.IOException; import java.nio.charset.StandardCharsets;\n"
                + "import static java.awt.RenderingHints.KEY_ANTIALIASING;\n"
                + "import static java.lang.Math.*;\n"
                + "class A { Object o = java.awt.Color.RED; int[] a = new int[1];\n"
                + " void f() { java.util.Arrays.fill(a, 0); } }";
        assertEquals(List.of(), findViolations(src));
    }

    /** Qualified names in {@code source} (comments and string literals removed) not allowed. */
    static List<String> findViolations(String source) {
        List<String> violations = new ArrayList<>();
        for (String statement : stripCommentsAndStrings(source).split(";")) {
            String head = statement.trim();
            if (head.startsWith("package ")) {
                continue;
            }
            boolean staticImport = head.startsWith("import static ");
            Matcher m = QUALIFIED.matcher(statement);
            while (m.find()) {
                String name = m.group();
                if (staticImport) {
                    name = name.substring(0, name.lastIndexOf('.')); // drop the static member
                }
                if (!isAllowed(name)) {
                    violations.add(name);
                }
            }
        }
        return violations;
    }

    static boolean isAllowed(String name) {
        for (String exact : ALLOWED_EXACT) {
            if (name.equals(exact) || name.startsWith(exact + ".")) {
                return true;
            }
        }
        for (String prefix : ALLOWED_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** Replaces comments, string/char literals and text blocks with spaces. */
    static String stripCommentsAndStrings(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (s.startsWith("//", i)) {
                while (i < n && s.charAt(i) != '\n') {
                    i++;
                }
            } else if (s.startsWith("/*", i)) {
                int end = s.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
                out.append(' ');
            } else if (s.startsWith("\"\"\"", i)) {
                i += 3;
                while (i < n && !s.startsWith("\"\"\"", i)) {
                    i += s.charAt(i) == '\\' ? 2 : 1;
                }
                i = Math.min(n, i + 3);
                out.append(' ');
            } else if (c == '"' || c == '\'') {
                i++;
                while (i < n && s.charAt(i) != c && s.charAt(i) != '\n') {
                    i += s.charAt(i) == '\\' ? 2 : 1;
                }
                i++;
                out.append(' ');
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}
