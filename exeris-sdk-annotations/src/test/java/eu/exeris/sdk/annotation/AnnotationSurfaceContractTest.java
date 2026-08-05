package eu.exeris.sdk.annotation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Semver gate for the annotation surface — the counterpart of the AST module's
 * {@code RecordComponentOrderTest}, and the reason this module runs no japicmp
 * execution (see its {@code pom.xml} for why).
 *
 * <p>Three rules, which together are what compatibility means for a
 * {@code @Retention(SOURCE)} annotation:
 *
 * <ol>
 *   <li><b>No element removed.</b> Existing annotated source stops compiling.</li>
 *   <li><b>No element retyped.</b> Same.</li>
 *   <li><b>A new element must declare a {@code default}.</b> This is the whole
 *       growth story: an element with a default leaves every existing usage
 *       compiling untouched, one without it breaks all of them at once. It is
 *       also the rule japicmp cannot express — it reports both cases as
 *       {@code METHOD_ABSTRACT_ADDED_TO_CLASS}.</li>
 * </ol>
 *
 * <p>Deprecation is deliberately not a violation here: marking an element
 * {@code @Deprecated(forRemoval = true)} is how the SDK opens a removal window
 * (see {@code MIGRATION.md}), and the removal itself lands at a major. The
 * snapshot records shape, not lifecycle.
 *
 * <p><b>The snapshot is meant to be edited — deliberately.</b> Add the new line
 * in the same commit that adds the element.
 */
@DisplayName("annotation surface contract: elements are additive, defaulted, and never retyped")
class AnnotationSurfaceContractTest {

    private static final String PACKAGE = "eu.exeris.sdk.annotation";
    private static final String SNAPSHOT = "/annotation-surface.txt";

    @Test
    @DisplayName("no element removed or retyped; every added element declares a default")
    void surfaceGrowsCompatiblyOnly() throws Exception {
        Map<String, String> recorded = readSnapshot();
        Map<String, String> actual = discoverSurface();

        assertThat(actual)
                .as("should discover the annotation elements; an empty walk would be vacuous")
                .hasSizeGreaterThanOrEqualTo(100)
                .containsKey("eu.exeris.sdk.annotation.ExerisDomain#dataScope");

        List<String> violations = new ArrayList<>();

        for (Map.Entry<String, String> e : new TreeMap<>(recorded).entrySet()) {
            String element = e.getKey();
            String before = e.getValue();
            String now = actual.get(element);
            if (now == null) {
                violations.add(("%s was removed. Existing annotated source stops compiling; a "
                        + "removal belongs at a major, behind a deprecation window "
                        + "(MIGRATION.md).").formatted(element));
            } else if (!typeOf(now).equals(typeOf(before))) {
                violations.add("%s changed type: %s -> %s. Retyping an element is a break."
                        .formatted(element, typeOf(before), typeOf(now)));
            }
        }

        for (Map.Entry<String, String> e : new TreeMap<>(actual).entrySet()) {
            String element = e.getKey();
            if (recorded.containsKey(element)) continue;
            if (!hasDefault(e.getValue())) {
                violations.add(("%s is new and declares no default, so every existing usage of "
                        + "%s stops compiling. Give it a default, or take the break "
                        + "deliberately at a major and re-baseline the snapshot.")
                        .formatted(element, simpleNameOf(element)));
            } else {
                violations.add(("%s is new and correctly defaulted, but is missing from the "
                        + "snapshot. Record it so the next change to it is gated:%n    %s=%s")
                        .formatted(element, element, e.getValue()));
            }
        }

        if (!violations.isEmpty()) {
            fail("Annotation surface contract violated (%d):%n%n%s",
                    violations.size(), String.join("%n%n".formatted(), violations));
        }
    }

    /** {@code a.b.C#el} → {@code @C} — for a message, not for keying. */
    private static String simpleNameOf(String element) {
        String type = element.substring(0, element.indexOf('#'));
        return "@" + type.substring(type.lastIndexOf('.') + 1);
    }

    private static String typeOf(String entry) {
        return entry.substring(0, entry.lastIndexOf(':'));
    }

    private static boolean hasDefault(String entry) {
        return entry.endsWith(":default");
    }

    /**
     * {@code fully.qualified.Annotation#element} → {@code type:default|required}.
     *
     * <p>Keyed by the qualified name, not the simple one: two annotations in
     * different subpackages may legally share a simple name, and a collision
     * would silently overwrite one of them here — the test would quietly stop
     * covering an annotation instead of failing, which is the opposite of what
     * it exists for.
     */
    private Map<String, String> discoverSurface() throws Exception {
        Map<String, String> out = new LinkedHashMap<>();
        for (Class<? extends Annotation> a : discoverAnnotations()) {
            List<Method> elements = new ArrayList<>(List.of(a.getDeclaredMethods()));
            elements.sort(Comparator.comparing(Method::getName));
            for (Method m : elements) {
                out.put(a.getName() + "#" + m.getName(),
                        m.getReturnType().getCanonicalName()
                                + (m.getDefaultValue() == null ? ":required" : ":default"));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<Class<? extends Annotation>> discoverAnnotations() throws Exception {
        String pkgPath = PACKAGE.replace('.', '/');
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        var urls = cl.getResources(pkgPath);
        if (!urls.hasMoreElements()) {
            fail("Package not found on classpath: %s", PACKAGE);
        }
        List<Class<? extends Annotation>> out = new ArrayList<>();
        while (urls.hasMoreElements()) {
            URL root = urls.nextElement();
            Path dir = Paths.get(URLDecoder.decode(root.getPath(), StandardCharsets.UTF_8));
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path p : (Iterable<Path>) files::iterator) {
                    if (!Files.isRegularFile(p)) continue;
                    String fname = p.getFileName().toString();
                    if (!fname.endsWith(".class") || fname.equals("package-info.class")) continue;
                    if (fname.contains("$")) continue;
                    String rel = dir.relativize(p).toString().replace(File.separatorChar, '.');
                    String fqn = PACKAGE + "." + rel.substring(0, rel.length() - ".class".length());
                    Class<?> c = Class.forName(fqn, false, cl);
                    if (c.isAnnotation()) {
                        out.add((Class<? extends Annotation>) c);
                    }
                }
            }
        }
        return out;
    }

    private Map<String, String> readSnapshot() throws Exception {
        Map<String, String> out = new LinkedHashMap<>();
        try (var in = AnnotationSurfaceContractTest.class.getResourceAsStream(SNAPSHOT)) {
            if (in == null) {
                fail("Snapshot resource missing: %s", SNAPSHOT);
            }
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList()) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int eq = trimmed.indexOf('=');
                if (eq < 0) {
                    fail("Malformed snapshot line (expected `Ann#element=type:default`): %s", trimmed);
                }
                out.put(trimmed.substring(0, eq), trimmed.substring(eq + 1));
            }
        }
        return out;
    }
}
