package eu.exeris.sdk.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the processor over a synthetic annotation and reads back what it wrote.
 *
 * <p>The probe is deliberately small and self-contained rather than a copy of a real SDK
 * annotation: this test is about the processor, and the question "does the catalog match
 * the SDK surface" belongs where the surface is —
 * {@code AnnotationCatalogContractTest} in {@code exeris-sdk-annotations}.
 */
@DisplayName("annotation catalog processor")
class AnnotationCatalogProcessorTest {

    private static final String PROBE = """
            package probe;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            /**
             * Marks a thing as probed.
             *
             * <p>Second paragraph, wrapped across
             * two source lines, mentioning {@code someCode} and
             * {@link java.lang.String}.
             */
            @Retention(RetentionPolicy.SOURCE)
            @Target(ElementType.TYPE)
            public @interface Probe {

                /**
                 * The name, which has no default.
                 *
                 * @return the name
                 */
                String name();

                /**
                 * How loudly to probe.
                 *
                 * @return the level
                 */
                Level level() default Level.QUIET;

                /**
                 * The old way.
                 *
                 * @deprecated Use {@code name()} instead — one spelling of the same thing.
                 * @return the label
                 */
                @Deprecated(since = "1.2.3", forRemoval = true)
                String label() default "";

                enum Level { QUIET, LOUD }

                /**
                 * A part of a probe, usable only as a member value.
                 *
                 * @return nothing on its own
                 */
                @Retention(RetentionPolicy.SOURCE)
                @Target({})
                @interface Part {
                    /**
                     * The part's name.
                     *
                     * @return the part name
                     */
                    String value();
                }
            }
            """;

    @Test
    @DisplayName("describes the surface it compiled, nested declarations included")
    void describesTheCompiledSurface(@TempDir Path classes) throws IOException {
        JsonNode catalog = compileAndRead(classes, List.of("-Aexeris.catalog.sdkVersion=9.9.9"));

        assertThat(catalog.get("sdkVersion").asString()).isEqualTo("9.9.9");
        assertThat(catalog.get("catalogFormat").asInt()).isEqualTo(AnnotationCatalogProcessor.CATALOG_FORMAT);
        assertThat(catalog.get("annotationCount").asInt()).isEqualTo(2);

        JsonNode outer = annotation(catalog, "probe.Probe");
        assertThat(outer.get("nested").asBoolean()).isFalse();
        assertThat(outer.get("retention").asString()).isEqualTo("SOURCE");
        assertThat(outer.get("targets").get(0).asString()).isEqualTo("TYPE");
        assertThat(outer.get("purpose").asString()).isEqualTo("Marks a thing as probed.");

        // The nested type is the half a classpath package walk cannot see.
        JsonNode nested = annotation(catalog, "probe.Probe.Part");
        assertThat(nested.get("nested").asBoolean()).isTrue();
        assertThat(nested.get("declaredIn").asString()).isEqualTo("probe.Probe");
        assertThat(nested.get("memberValueOnly").asBoolean()).isTrue();
        assertThat(nested.get("targets")).isEmpty();
    }

    @Test
    @DisplayName("carries per-attribute type, default, required-ness and admissible values")
    void describesAttributes(@TempDir Path classes) throws IOException {
        JsonNode outer = annotation(
                compileAndRead(classes, List.of("-Aexeris.catalog.sdkVersion=9.9.9")), "probe.Probe");

        JsonNode name = attribute(outer, "name");
        assertThat(name.get("type").asString()).isEqualTo("java.lang.String");
        assertThat(name.get("required").asBoolean()).isTrue();
        assertThat(name.get("default")).isNull();
        assertThat(name.get("purpose").asString()).isEqualTo("The name, which has no default.");

        JsonNode level = attribute(outer, "level");
        assertThat(level.get("required").asBoolean()).isFalse();
        // Bare constant name, which is what a use site writes; `type` and `enumConstants`
        // beside it make that unambiguous without repeating the qualifier on every default.
        assertThat(level.get("default").asString()).isEqualTo("QUIET");
        // Admissible values: an authoring agent otherwise has to guess the constant names.
        assertThat(level.get("enumConstants")).map(JsonNode::asString).containsExactly("QUIET", "LOUD");
    }

    @Test
    @DisplayName("a deprecation carries the replacement, which lives only in prose")
    void describesDeprecations(@TempDir Path classes) throws IOException {
        JsonNode label = attribute(annotation(
                compileAndRead(classes, List.of("-Aexeris.catalog.sdkVersion=9.9.9")), "probe.Probe"), "label");

        JsonNode deprecated = label.get("deprecated");
        assertThat(deprecated.get("forRemoval").asBoolean()).isTrue();
        assertThat(deprecated.get("since").asString()).isEqualTo("1.2.3");
        // @Deprecated has nowhere to put this; the @deprecated block tag does.
        assertThat(deprecated.get("replacement").asString())
                .isEqualTo("Use name() instead — one spelling of the same thing.");
    }

    @Test
    @DisplayName("prose is rendered, not passed through as javadoc markup")
    void rendersProse(@TempDir Path classes) throws IOException {
        String description = annotation(
                compileAndRead(classes, List.of("-Aexeris.catalog.sdkVersion=9.9.9")), "probe.Probe")
                .get("description").asString();

        assertThat(description)
                .as("inline tags become their text, and source line wrapping is not content")
                .contains("mentioning someCode and java.lang.String")
                .doesNotContain("{@code", "{@link", "<p>");
        assertThat(description.split("\n\n"))
                .as("<p> is what makes a paragraph, so the two survive as two")
                .hasSize(2);
    }

    @Test
    @DisplayName("without an SDK version it refuses rather than stamping one it does not know")
    void refusesWithoutAVersion(@TempDir Path classes) throws IOException {
        List<String> errors = compile(classes, List.of());

        assertThat(errors)
                .as("a catalog that cannot say which SDK it describes would let a consumer "
                        + "answer confidently from the wrong contract")
                .isNotEmpty();
        assertThat(String.join(" ", errors)).contains(AnnotationCatalogProcessor.OPT_SDK_VERSION);
        assertThat(classes.resolve(AnnotationCatalogProcessor.RESOURCE_PATH))
                .as("nothing should have been written")
                .doesNotExist();
    }

    // ---- harness ---------------------------------------------------------

    private static JsonNode annotation(JsonNode catalog, String qualifiedName) {
        for (JsonNode each : catalog.get("annotations")) {
            if (qualifiedName.equals(each.get("qualifiedName").asString())) {
                return each;
            }
        }
        throw new AssertionError("no catalog entry for " + qualifiedName);
    }

    private static JsonNode attribute(JsonNode annotation, String name) {
        for (JsonNode each : annotation.get("attributes")) {
            if (name.equals(each.get("name").asString())) {
                return each;
            }
        }
        throw new AssertionError("no attribute " + name + " on " + annotation.get("qualifiedName"));
    }

    private static JsonNode compileAndRead(Path classes, List<String> options) throws IOException {
        assertThat(compile(classes, options)).as("the probe must compile").isEmpty();
        Path written = classes.resolve(AnnotationCatalogProcessor.RESOURCE_PATH);
        assertThat(written).as("the processor wrote no catalog").exists();
        return JsonMapper.builder().build().readTree(Files.readString(written));
    }

    /** @return every error javac reported, empty when it was happy */
    private static List<String> compile(Path classes, List<String> options) throws IOException {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertThat(javac).as("run this on a JDK, not a JRE").isNotNull();

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = javac.getStandardFileManager(diagnostics, null, null)) {
            List<String> all = new java.util.ArrayList<>(options);
            all.addAll(List.of("-d", classes.toString()));
            JavaCompiler.CompilationTask task = javac.getTask(
                    null, files, diagnostics, all, null, List.of(new InMemorySource(PROBE)));
            task.setProcessors(List.of(new AnnotationCatalogProcessor()));
            task.call();
        }
        return diagnostics.getDiagnostics().stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .map(d -> d.getMessage(null))
                .toList();
    }

    private static final class InMemorySource extends SimpleJavaFileObject {

        private final String code;

        InMemorySource(String code) {
            super(URI.create("string:///probe/Probe.java"), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
}
