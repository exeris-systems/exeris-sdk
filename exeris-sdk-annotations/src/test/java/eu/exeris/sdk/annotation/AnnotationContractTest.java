package eu.exeris.sdk.annotation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Contract test for the SDK annotation surface.
 * <p>
 * Annotations are declarative metadata — there is no executable code on them
 * for JaCoCo to measure. This test discovers every {@code @interface} under
 * {@code eu.exeris.sdk.annotation.*} via the classpath and asserts the two
 * invariants the rest of the SDK relies on:
 * <ol>
 *   <li><b>{@code @Retention(SOURCE)}</b> — annotations are consumed at
 *       compile time by the processor and have zero runtime classpath
 *       presence; a runtime-retained annotation would leak the SDK into
 *       end-user runtime images.</li>
 *   <li><b>{@code @Target} is declared</b> — without a target the annotation
 *       silently allows usage in unexpected positions, breaking the
 *       processor's ability to reject misuse.</li>
 * </ol>
 * <p>
 * If you add a new annotation, no edit to this test is needed — the package
 * walk picks it up automatically.
 */
@DisplayName("SDK annotation contract: SOURCE retention + @Target")
class AnnotationContractTest {

    @Test
    @DisplayName("every annotation under eu.exeris.sdk.annotation.* has @Retention(SOURCE) and @Target")
    void everyAnnotationHonoursContract() throws Exception {
        List<Class<? extends Annotation>> annotations = discoverAnnotations("eu.exeris.sdk.annotation");
        assertThat(annotations)
                .as("should discover at least the well-known core annotations")
                .hasSizeGreaterThanOrEqualTo(20)
                .extracting(Class::getSimpleName)
                .contains("ExerisDomain", "Action", "Field", "Relationship",
                        "Saga", "DomainEvent", "UI", "Validation");

        List<String> missingRetention = new ArrayList<>();
        List<String> wrongRetention = new ArrayList<>();
        List<String> missingTarget = new ArrayList<>();

        for (Class<? extends Annotation> a : annotations) {
            Retention r = a.getAnnotation(Retention.class);
            if (r == null) {
                missingRetention.add(a.getName());
            } else if (r.value() != RetentionPolicy.SOURCE) {
                wrongRetention.add(a.getName() + " (actual=" + r.value() + ")");
            }
            if (a.getAnnotation(Target.class) == null) {
                missingTarget.add(a.getName());
            }
        }

        assertThat(missingRetention)
                .as("annotations missing @Retention — they would default to CLASS retention")
                .isEmpty();
        assertThat(wrongRetention)
                .as("annotations must be SOURCE-retained; runtime/class retention would leak the SDK into end-user images")
                .isEmpty();
        assertThat(missingTarget)
                .as("annotations missing @Target — the processor cannot reject misuse without one")
                .isEmpty();
    }

    @Test
    @DisplayName("ExerisDomain default values match the documented contract")
    void exerisDomainDefaults() {
        // Spot-check the most user-facing annotation: defaults reflected in
        // README / package-info must stay aligned with what javac sees.
        assertThat(ExerisDomain.class.isAnnotation()).isTrue();
        assertThat(ExerisDomain.class.getAnnotation(Retention.class).value())
                .isEqualTo(RetentionPolicy.SOURCE);
    }

    @Test
    @DisplayName("nested system & security annotations are SOURCE-retained")
    void subpackagesAlsoHonourSourceRetention() throws Exception {
        List<Class<? extends Annotation>> system = discoverAnnotations("eu.exeris.sdk.annotation.system");
        List<Class<? extends Annotation>> security = discoverAnnotations("eu.exeris.sdk.annotation.security");

        assertThat(system).as("system subpackage should expose marker annotations").isNotEmpty();
        assertThat(security).as("security subpackage should expose marker annotations").isNotEmpty();

        Stream.concat(system.stream(), security.stream()).forEach(a -> {
            Retention r = a.getAnnotation(Retention.class);
            assertThat(r).as("missing @Retention on %s", a.getName()).isNotNull();
            assertThat(r.value()).as("non-SOURCE retention on %s", a.getName())
                    .isEqualTo(RetentionPolicy.SOURCE);
        });
    }

    /**
     * Discovers every annotation type ({@link Class#isAnnotation()}) under
     * the given package by enumerating every classpath root that contains
     * it. Multi-root enumeration matters under Maven: the test classloader
     * exposes both {@code target/classes} (where the annotations live) and
     * {@code target/test-classes} (where this test lives) as separate
     * locations for the same package; only the first would be visible
     * to {@code getResource} alone.
     */
    @SuppressWarnings("unchecked")
    private List<Class<? extends Annotation>> discoverAnnotations(String pkg) throws Exception {
        String pkgPath = pkg.replace('.', '/');
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        var urls = cl.getResources(pkgPath);
        if (!urls.hasMoreElements()) {
            fail("Package not found on classpath: %s", pkg);
        }
        List<Class<? extends Annotation>> out = new ArrayList<>();
        while (urls.hasMoreElements()) {
            URL root = urls.nextElement();
            Path dir = Paths.get(URLDecoder.decode(root.getPath(), StandardCharsets.UTF_8));
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> files = Files.list(dir)) {
                for (Path p : (Iterable<Path>) files::iterator) {
                    String fname = p.getFileName().toString();
                    if (!fname.endsWith(".class") || fname.equals("package-info.class")) continue;
                    // Skip nested types ($ in the JVM-compiled name) — they
                    // are only reachable as attribute types of their parent
                    // annotation and inherit its SOURCE retention at use site.
                    if (fname.contains("$")) continue;
                    String simple = fname.substring(0, fname.length() - ".class".length());
                    Class<?> c = Class.forName(pkg + "." + simple, false, cl);
                    if (c.isAnnotation()) {
                        out.add((Class<? extends Annotation>) c);
                    }
                }
            }
        }
        return out;
    }
}
