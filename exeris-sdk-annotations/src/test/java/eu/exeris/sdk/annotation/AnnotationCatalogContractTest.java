package eu.exeris.sdk.annotation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The generated annotation catalog is a published contract, and this is what holds it to
 * the surface it claims to describe.
 *
 * <p>The catalog is built by {@code AnnotationCatalogProcessor} from the module's
 * <em>sources</em>, reading javac's declaration model and doc comments. This test checks it
 * against the <em>compiled classes</em> through reflection. The two mechanisms share
 * nothing, which is the point: a defect in either shows up as disagreement instead of as
 * two copies of one mistake. A test that re-ran the processor and compared the answer to
 * itself would pass for any processor at all.
 *
 * <p>What reflection cannot supply is checked as present rather than as equal — the prose,
 * and a deprecation's canonical replacement. Those are the reason the catalog is generated
 * from sources in the first place, so a catalog that lost them would still satisfy every
 * structural assertion here while being worthless to its consumer.
 */
@DisplayName("annotation catalog: covers the surface, and carries what a class file cannot")
class AnnotationCatalogContractTest {

    private static final String RESOURCE = "META-INF/exeris/annotation-catalog.json";
    private static final String PACKAGE = "eu.exeris.sdk.annotation";

    private final JsonNode catalog = readCatalog();

    @Test
    @DisplayName("every annotation the module declares appears, and nothing else does")
    void coversExactlyTheDeclaredSurface() throws Exception {
        // Derived, never written down. A hard-coded count is wrong one release later —
        // the surface grew 49 -> 51 top-level between 0.10.0 and 0.11.0 — and a hard-coded
        // count that someone updates to match a broken catalog guards nothing.
        Set<String> declared = new TreeSet<>();
        for (Class<? extends Annotation> type : DeclaredAnnotations.under(PACKAGE)) {
            declared.add(type.getCanonicalName());
        }

        Set<String> catalogued = new TreeSet<>();
        catalog.get("annotations").forEach(node -> catalogued.add(node.get("qualifiedName").asString()));

        assertThat(catalogued)
                .withFailMessage(
                        "The catalog does not describe the annotation surface.%n"
                                + "  missing from the catalog: %s%n"
                                + "  in the catalog but not declared: %s%n%n"
                                + "A partial catalog is worse than none, because its consumer cannot tell "
                                + "which part it received. Nested types are the usual cause — they are "
                                + "roughly a quarter of the surface and a package walk does not see them.",
                        difference(declared, catalogued), difference(catalogued, declared))
                .isEqualTo(declared);

        assertThat(catalog.get("annotationCount").asInt())
                .as("the count the catalog states must match the list it carries")
                .isEqualTo(catalogued.size());
    }

    @Test
    @DisplayName("every declaration site carries the prose a class file cannot")
    void carriesTheProse() {
        List<String> withoutPurpose = new ArrayList<>();
        for (JsonNode annotation : catalog.get("annotations")) {
            if (blank(annotation, "purpose")) {
                withoutPurpose.add(annotation.get("qualifiedName").asString());
            }
            for (JsonNode attribute : annotation.get("attributes")) {
                if (blank(attribute, "purpose")) {
                    withoutPurpose.add(annotation.get("qualifiedName").asString()
                            + "." + attribute.get("name").asString());
                }
            }
        }

        assertThat(withoutPurpose)
                .withFailMessage(
                        "Declaration sites with no javadoc, so the catalog describes them to a "
                                + "consumer as a name and a type and nothing else:%n  %s%n%n"
                                + "The prose is the whole reason this catalog is generated from sources "
                                + "rather than assembled by reflection. Add javadoc at the site rather "
                                + "than relaxing this.",
                        String.join("\n  ", withoutPurpose))
                .isEmpty();
    }

    @Test
    @DisplayName("every deprecated-for-removal element names its replacement")
    void deprecationsNameTheirReplacement() throws Exception {
        // @Deprecated has two elements, since and forRemoval, and nowhere to put the
        // replacement — so it lives in @deprecated prose. This is the one field a
        // reflection-built catalog could never carry, and the downstream ask names it.
        List<String> expected = new ArrayList<>();
        for (Class<? extends Annotation> type : DeclaredAnnotations.under(PACKAGE)) {
            for (Method attribute : type.getDeclaredMethods()) {
                Deprecated deprecated = attribute.getAnnotation(Deprecated.class);
                if (deprecated != null && deprecated.forRemoval()) {
                    expected.add(type.getCanonicalName() + "." + attribute.getName());
                }
            }
        }

        assertThat(expected)
                .withFailMessage("No deprecated-for-removal attribute remains, so this test now "
                        + "measures nothing. If the last deprecation was removed at a major version, "
                        + "delete this test with it rather than leaving it green and empty.")
                .isNotEmpty();

        List<String> withoutReplacement = new ArrayList<>();
        for (String qualified : expected) {
            JsonNode attribute = attributeNode(qualified);
            assertThat(attribute)
                    .as("the catalog has no entry for the deprecated attribute %s", qualified)
                    .isNotNull();
            JsonNode deprecated = attribute.get("deprecated");
            if (deprecated == null || blank(deprecated, "replacement")) {
                withoutReplacement.add(qualified);
            }
        }

        assertThat(withoutReplacement)
                .withFailMessage(
                        "Deprecated-for-removal attributes whose catalog entry names no replacement:"
                                + "%n  %s%n%nAn agent reading this catalog is told the attribute is going "
                                + "away and not what to write instead — which is the half that matters. "
                                + "Write it into the @deprecated javadoc at the declaration.",
                        String.join("\n  ", withoutReplacement))
                .isEmpty();
    }

    @Test
    @DisplayName("the catalog is versioned, self-describing, and reproducible")
    void isVersionedAndReproducible() {
        assertThat(catalog.get("catalogFormat").asInt())
                .as("the catalog's own format version, which a consumer keys its parser on")
                .isEqualTo(1);
        assertThat(catalog.get("sdkVersion").asString())
                .as("a catalog that cannot say which SDK it describes lets a consumer answer "
                        + "confidently from the wrong contract")
                .isNotBlank();

        Set<String> keys = new TreeSet<>();
        catalog.propertyNames().forEach(keys::add);
        assertThat(keys)
                .withFailMessage(
                        "Unexpected top-level keys: %s.%n%nThis assertion exists mainly to catch a "
                                + "generatedAt-style field. The catalog is a pure function of the sources "
                                + "and the version, so two builds of one commit produce identical bytes; a "
                                + "timestamp would be the single thing making the jar unreproducible.",
                        keys)
                .containsExactly("annotationCount", "annotations", "catalogFormat", "packages", "sdkVersion");

        // The rules no single annotation owns live in package prose — the @Field vs
        // @Validation scoping split above all, which is the SDK's most regression-prone
        // area and is stated authoritatively only there.
        JsonNode rootPackage = null;
        for (JsonNode each : catalog.get("packages")) {
            if (PACKAGE.equals(each.get("name").asString())) {
                rootPackage = each;
            }
        }
        assertThat(rootPackage).as("the root annotation package must be described").isNotNull();
        assertThat(blank(rootPackage, "description"))
                .as("package-info prose is missing, so the scoping rules reach no consumer")
                .isFalse();
    }

    // ---- helpers ---------------------------------------------------------

    private JsonNode attributeNode(String qualifiedAttribute) {
        int split = qualifiedAttribute.lastIndexOf('.');
        String owner = qualifiedAttribute.substring(0, split);
        String name = qualifiedAttribute.substring(split + 1);
        for (JsonNode annotation : catalog.get("annotations")) {
            if (!owner.equals(annotation.get("qualifiedName").asString())) {
                continue;
            }
            for (JsonNode attribute : annotation.get("attributes")) {
                if (name.equals(attribute.get("name").asString())) {
                    return attribute;
                }
            }
        }
        return null;
    }

    private static boolean blank(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.asString().isBlank();
    }

    private static Set<String> difference(Set<String> from, Set<String> minus) {
        Set<String> out = new TreeSet<>(from);
        out.removeAll(minus);
        return out;
    }

    private static JsonNode readCatalog() {
        try (InputStream in = AnnotationCatalogContractTest.class.getClassLoader()
                .getResourceAsStream(RESOURCE)) {
            assertThat(in)
                    .withFailMessage("%s is not on the classpath. It is generated during this "
                            + "module's own compilation by exeris-sdk-annotation-catalog on the "
                            + "annotation processor path — check that the path is still wired in "
                            + "the pom rather than adjusting this test.", RESOURCE)
                    .isNotNull();
            return JsonMapper.builder().build().readTree(in);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read " + RESOURCE, e);
        }
    }
}
