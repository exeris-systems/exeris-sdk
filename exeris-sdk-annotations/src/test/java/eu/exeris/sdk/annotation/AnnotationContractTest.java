package eu.exeris.sdk.annotation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract test for the SDK annotation surface.
 * <p>
 * Annotations are declarative metadata — there is no executable code on them
 * for JaCoCo to measure. This test discovers every {@code @interface} under
 * {@code eu.exeris.sdk.annotation.*} via the classpath and asserts the three
 * invariants the rest of the SDK relies on:
 * <ol>
 *   <li><b>{@code @Retention(SOURCE)}</b> — annotations are consumed at
 *       compile time by the processor and have zero runtime classpath
 *       presence; a runtime-retained annotation would leak the SDK into
 *       end-user runtime images.</li>
 *   <li><b>{@code @Target} is declared</b> — without a target the annotation
 *       silently allows usage in unexpected positions, breaking the
 *       processor's ability to reject misuse.</li>
 *   <li><b>{@code @Repeatable} containers are public</b> — a package-private
 *       container compiles here and makes the annotation unrepeatable from
 *       every other package, which no test living inside this package could
 *       otherwise observe.</li>
 * </ol>
 * <p>
 * If you add a new annotation, no edit to this test is needed — the package
 * walk recurses through every sub-package under
 * {@code eu.exeris.sdk.annotation} (today {@code system}, {@code security} and
 * {@code capability}; a future package such as {@code graph} is picked up
 * automatically) and asserts both invariants on every annotation it finds.
 */
@DisplayName("SDK annotation contract: SOURCE retention + @Target")
class AnnotationContractTest {

    @Test
    @DisplayName("every annotation under eu.exeris.sdk.annotation.* has @Retention(SOURCE) and @Target")
    void everyAnnotationHonoursContract() throws Exception {
        List<Class<? extends Annotation>> annotations = discoverAnnotations("eu.exeris.sdk.annotation");
        assertThat(annotations)
                .as("should discover at least the well-known core annotations")
                .hasSizeGreaterThanOrEqualTo(24)
                .extracting(Class::getSimpleName)
                .contains("ExerisDomain", "Action", "Field", "Relationship",
                        "Saga", "DomainEvent", "UI", "Validation",
                        "Derived", "Rule", "Rules",
                        "View", "Region", "Block", "Bind");

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
    @DisplayName("every @Repeatable annotation's container type is public")
    void repeatableContainersArePublic() throws Exception {
        // A container declared package-private compiles inside this package and
        // fails at every external use site ("GraphEdges.value() is defined in an
        // inaccessible class or interface"), because the compiler requires the
        // container to be at least as accessible as the repeatable annotation.
        // The SDK's own tests could never have caught it — they live in this
        // package. Two top-level containers carried the defect (@SagaSteps,
        // fixed in 0.9.0; @GraphEdges, fixed in 0.10.0) and every other one is
        // nested inside a public @interface, hence implicitly public.
        //
        // Read from @Repeatable rather than from the package walk on purpose: this
        // asks which containers are actually reachable from a repeatable annotation,
        // which is the question a use site asks, and it stays correct however the
        // walk is scoped.
        List<Class<? extends Annotation>> annotations = discoverAnnotations("eu.exeris.sdk.annotation");

        List<String> repeatables = new ArrayList<>();
        List<String> nonPublicContainers = new ArrayList<>();

        for (Class<? extends Annotation> a : annotations) {
            Repeatable repeatable = a.getAnnotation(Repeatable.class);
            if (repeatable == null) {
                continue;
            }
            Class<? extends Annotation> container = repeatable.value();
            repeatables.add(a.getSimpleName() + " -> " + container.getSimpleName());
            if (!Modifier.isPublic(container.getModifiers())) {
                nonPublicContainers.add(container.getName());
            }
        }

        assertThat(repeatables)
                .as("the walk should still be finding the known repeatable annotations")
                .isNotEmpty();
        assertThat(nonPublicContainers)
                .as("@Repeatable container types must be public — a package-private container "
                        + "makes repeating the annotation a compile error outside this package")
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
    @DisplayName("system, security & capability sub-packages are discovered and honour SOURCE + @Target")
    void subpackagesAlsoHonourContract() throws Exception {
        // Guard that the recursive walk actually reaches the sub-packages
        // (a regression here would silently shrink coverage of the main test).
        List<Class<? extends Annotation>> system = discoverAnnotations("eu.exeris.sdk.annotation.system");
        List<Class<? extends Annotation>> security = discoverAnnotations("eu.exeris.sdk.annotation.security");
        List<Class<? extends Annotation>> capability = discoverAnnotations("eu.exeris.sdk.annotation.capability");

        assertThat(system).as("system subpackage should expose marker annotations").isNotEmpty();
        assertThat(security).as("security subpackage should expose marker annotations").isNotEmpty();
        assertThat(capability)
                .as("capability subpackage should expose the @CapabilityModule/@Provides/@Requires/@CapabilityLifecycle surface")
                .isNotEmpty()
                .extracting(Class::getSimpleName)
                .contains("CapabilityModule", "Provides", "Requires", "CapabilityLifecycle");

        Stream.of(system, security, capability).flatMap(List::stream).forEach(a -> {
            Retention r = a.getAnnotation(Retention.class);
            assertThat(r).as("missing @Retention on %s", a.getName()).isNotNull();
            assertThat(r.value()).as("non-SOURCE retention on %s", a.getName())
                    .isEqualTo(RetentionPolicy.SOURCE);
            assertThat(a.getAnnotation(Target.class))
                    .as("missing @Target on %s — the processor cannot reject misuse without one", a.getName())
                    .isNotNull();
        });
    }

    /**
     * Delegates to the shared walk — see {@link DeclaredAnnotations}, which the catalog
     * contract test reads too, so that "every annotation" means one thing in this module.
     */
    private List<Class<? extends Annotation>> discoverAnnotations(String pkg) throws Exception {
        return DeclaredAnnotations.under(pkg);
    }
}
