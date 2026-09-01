package eu.exeris.sdk.sourcemodel.ast;

import eu.exeris.sdk.sourcemodel.mutation.SchemaVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Holds {@code META-INF/exeris/ast-schema.json} to the contract it claims.
 *
 * <p>The schema is written by {@code AstSchemaProcessor} during this module's own
 * compilation, from the <em>sources</em>. This test derives its expectations from the
 * <em>compiled classes</em>, independently — so a bug in the emitter shows up as
 * disagreement rather than as two copies of one mistake. Same reason
 * {@code DeclaredAnnotations} exists on the annotations side.
 */
@DisplayName("AST schema contract: complete, resolvable, and it carries the reader requirements")
class AstSchemaContractTest {

    private static final String RESOURCE = "/META-INF/exeris/ast-schema.json";
    private static final String PACKAGE = "eu.exeris.sdk.sourcemodel.ast";

    private static JsonNode schema;

    @BeforeAll
    static void readSchema() throws Exception {
        try (InputStream in = AstSchemaContractTest.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                fail("%s is missing. It is emitted by AstSchemaProcessor on this module's "
                        + "annotation processor path; an absent file means the processor did not run.", RESOURCE);
            }
            schema = new ObjectMapper().readTree(in);
        }
    }

    @Test
    @DisplayName("the stamped AST version is the one SchemaVersion reports")
    void astVersionMatchesSchemaVersion() {
        // The emitter takes the version as a compiler argument from a POM property, because
        // SchemaVersion.CURRENT is deliberately not a compile-time constant and so is out of
        // reach of both the POM and the declaration model. That duplication is only safe
        // while something fails when the two drift. This is that something.
        assertThat(schema.get("astSchemaVersion").asString())
                .as("ast.schema.version in exeris-sdk-source-model/pom.xml must track "
                        + "SchemaVersion.CURRENT; bump both together")
                .isEqualTo(SchemaVersion.CURRENT);
    }

    @Test
    @DisplayName("every AST record and enum has a definition, and every $ref resolves")
    void coversTheAstPackage() throws Exception {
        Set<String> defined = new TreeSet<>();
        for (Iterator<String> names = schema.get("$defs").propertyNames().iterator(); names.hasNext(); ) {
            defined.add(names.next());
        }

        Set<String> declared = new TreeSet<>(definitionNamesUnder(PACKAGE));
        assertThat(declared)
                .as("an empty walk would make this test vacuous")
                .hasSizeGreaterThan(30);
        assertThat(defined)
                .as("the schema must describe exactly the AST package — no more, no less")
                .isEqualTo(declared);

        List<String> dangling = new ArrayList<>();
        collectRefs(schema, dangling);
        assertThat(dangling.stream().filter(ref -> !defined.contains(ref)).toList())
                .as("every $ref must point at a definition this document carries")
                .isEmpty();
    }

    @Test
    @DisplayName("no Java type reached the schema unmapped")
    void everyTypeIsMapped() {
        List<String> unmapped = new ArrayList<>();
        collectUnmapped(schema, unmapped);
        assertThat(unmapped)
                .as("an unmapped type is a gap in the emitter's type switch, not a property shape a "
                        + "consumer can act on — it must be added there rather than shipped")
                .isEmpty();
    }

    @Test
    @DisplayName("the two Jackson reader requirements are carried in the document")
    void carriesTheReaderRequirements() {
        JsonNode requirements = schema.get("x-exeris-reader-requirements");
        Set<String> ids = new TreeSet<>();
        requirements.forEach(each -> ids.add(each.get("id").asString()));
        assertThat(ids).containsExactlyInAnyOrder(
                "FAIL_ON_NULL_FOR_PRIMITIVES", "NON_DEFAULT_DROPS_BOXED_ZERO");
        requirements.forEach(each -> {
            assertThat(each.get("statement").asString()).isNotBlank();
            assertThat(each.get("why").asString()).isNotBlank();
        });
    }

    @Test
    @DisplayName("FieldMetadata's bounds carry their own NON_NULL, and pattern does not")
    void perComponentIncludeSurvivesIntoTheSchema() {
        JsonNode field = schema.get("$defs").get("FieldMetadata");
        assertThat(field.get("x-exeris-json-include").asString()).isEqualTo("NON_DEFAULT");

        // The concrete instance of reader requirement 2, and the reason this file is worth
        // generating: under the record's NON_DEFAULT a boxed zero is dropped, so `min = 0`
        // as a non-negativity floor survives the wire only because the component overrides
        // the posture. A schema that reported these four as ordinary NON_DEFAULT properties
        // would describe the exact bug the override exists to prevent as if it were present.
        for (String bound : List.of("min", "max", "minLength", "maxLength")) {
            assertThat(field.get("properties").get(bound).get("x-exeris-json-include"))
                    .as("%s must be reported as overriding to NON_NULL", bound)
                    .isNotNull();
            assertThat(field.get("properties").get(bound).get("x-exeris-json-include").asString())
                    .isEqualTo("NON_NULL");
        }
        assertThat(field.get("properties").get("pattern").get("x-exeris-json-include"))
                .as("pattern stays under the record's posture — a string has no zero-analog hazard")
                .isNull();
    }

    @Test
    @DisplayName("primitive properties are flagged, which is what makes requirement 1 actionable")
    void primitivesAreFlagged() {
        JsonNode action = schema.get("$defs").get("ActionMetadata").get("properties");
        assertThat(action.get("async").get("x-exeris-primitive").asBoolean()).isTrue();
        assertThat(action.get("name").get("x-exeris-primitive"))
                .as("a String property is not primitive and must not be flagged")
                .isNull();
    }

    @Test
    @DisplayName("the document is reproducible — nothing dates it")
    void isReproducible() {
        Set<String> rootKeys = new TreeSet<>();
        for (Iterator<String> names = schema.propertyNames().iterator(); names.hasNext(); ) {
            rootKeys.add(names.next());
        }
        // Pinned exactly: a generatedAt-shaped key is the one thing that would make the jar
        // unreproducible, and it would arrive as an addition rather than as a failure.
        assertThat(rootKeys).containsExactly(
                "$defs", "$id", "$ref", "$schema", "astSchemaVersion", "definitionCount",
                "description", "schemaFormat", "sdkVersion", "title",
                "x-exeris-prose-coverage", "x-exeris-reader-requirements");
    }

    // ---- helpers ---------------------------------------------------------

    private static void collectRefs(JsonNode node, List<String> into) {
        if (node.isObject()) {
            JsonNode ref = node.get("$ref");
            if (ref != null && ref.asString().startsWith("#/$defs/")) {
                into.add(ref.asString().substring("#/$defs/".length()));
            }
        }
        node.forEach(child -> collectRefs(child, into));
    }

    private static void collectUnmapped(JsonNode node, List<String> into) {
        if (node.isObject() && node.get("x-exeris-unmapped") != null) {
            into.add(node.get("x-exeris-unmapped").asString());
        }
        node.forEach(child -> collectUnmapped(child, into));
    }

    /**
     * Every record and enum under {@code pkg}, named the way the schema names them —
     * {@code Outer.Inner} for a nested type. Walks compiled classes, so it is derived
     * independently of the source-driven emitter.
     */
    private static List<String> definitionNamesUnder(String pkg) throws Exception {
        String pkgPath = pkg.replace('.', '/');
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        var urls = cl.getResources(pkgPath);
        List<String> out = new ArrayList<>();
        while (urls.hasMoreElements()) {
            URL root = urls.nextElement();
            Path dir = Paths.get(URLDecoder.decode(root.getPath(), StandardCharsets.UTF_8));
            if (!Files.isDirectory(dir)) continue;
            // Under Maven the test classloader exposes this package from two roots —
            // target/classes and target/test-classes — and this package has test fixtures
            // (AstJsonRoundTripTest's probe records) declared into it. The emitter runs with
            // <proc>none</proc> on testCompile precisely so they never reach the schema, so
            // counting them here would fail the comparison against a correct document.
            if (dir.toString().contains("test-classes")) continue;
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path p : (Iterable<Path>) files::iterator) {
                    if (!Files.isRegularFile(p)) continue;
                    String fname = p.getFileName().toString();
                    if (!fname.endsWith(".class") || fname.equals("package-info.class")) continue;
                    String rel = dir.relativize(p).toString().replace(File.separatorChar, '.');
                    String binary = pkg + "." + rel.substring(0, rel.length() - ".class".length());
                    Class<?> c = Class.forName(binary, false, cl);
                    if (c.isRecord() || c.isEnum()) {
                        out.add(c.getName().substring(pkg.length() + 1).replace('$', '.'));
                    }
                }
            }
        }
        return out;
    }
}
