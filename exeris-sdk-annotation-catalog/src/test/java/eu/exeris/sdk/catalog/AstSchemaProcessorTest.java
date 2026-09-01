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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the emitter over a synthetic AST-shaped record and reads back what it wrote.
 *
 * <p>The probe declares itself into {@code eu.exeris.sdk.sourcemodel.ast} because that
 * package is the emitter's filter. It is deliberately synthetic rather than a copy of a real
 * record: this test is about the emitter, and "does the schema match the AST" belongs where
 * the AST is — {@code AstSchemaContractTest} in {@code exeris-sdk-source-model}.
 */
@DisplayName("AST schema processor")
class AstSchemaProcessorTest {

    private static final String PROBE = """
            package eu.exeris.sdk.sourcemodel.ast;

            import com.fasterxml.jackson.annotation.JsonIgnore;
            import com.fasterxml.jackson.annotation.JsonInclude;
            import com.fasterxml.jackson.annotation.JsonProperty;
            import java.util.List;

            /**
             * A probed thing.
             *
             * <p>Notes that wrap across
             * two source lines.
             *
             * @param name the identity of the probe
             * @param bound a bound whose zero must survive the wire
             * @param loud whether to probe loudly
             */
            @JsonInclude(JsonInclude.Include.NON_DEFAULT)
            public record ProbeMetadata(
                    String name,
                    @JsonInclude(JsonInclude.Include.NON_NULL)
                    Long bound,
                    boolean loud,
                    List<String> tags,
                    @JsonProperty("renamed") String original,
                    Level level,
                    Inner inner
            ) {
                /** How loudly to probe. */
                public enum Level { QUIET, LOUD }

                /** A nested carrier. */
                public record Inner(String note) { }

                @JsonIgnore
                public boolean isLoud() { return loud; }
            }
            """;

    @Test
    @DisplayName("a component's own NON_NULL is reported, overriding the record's posture")
    void perComponentIncludeIsRead(@TempDir Path classes) throws IOException {
        JsonNode probe = def(compileAndRead(classes), "ProbeMetadata");

        assertThat(probe.get("x-exeris-json-include").asString()).isEqualTo("NON_DEFAULT");
        // The regression this guards is silent and was real: Jackson's @JsonInclude targets
        // do not include RECORD_COMPONENT, so the annotation never stays on the component
        // element. Reading it from there alone returned null for every bound in the real AST,
        // and the schema described the boxed-zero hazard as unmitigated.
        JsonNode boundInclude = probe.get("properties").get("bound").get("x-exeris-json-include");
        assertThat(boundInclude)
                .as("the component's own @JsonInclude must reach the schema; a null here is the "
                        + "naive component-only lookup, which returns nothing for Jackson's annotations")
                .isNotNull();
        assertThat(boundInclude.asString()).isEqualTo("NON_NULL");
        assertThat(probe.get("properties").get("name").get("x-exeris-json-include")).isNull();
    }

    @Test
    @DisplayName("types map to JSON Schema, and AST types become $refs")
    void typesAreMapped(@TempDir Path classes) throws IOException {
        JsonNode props = def(compileAndRead(classes), "ProbeMetadata").get("properties");

        assertThat(props.get("name").get("type").asString()).isEqualTo("string");
        assertThat(props.get("bound").get("type").asString()).isEqualTo("integer");
        assertThat(props.get("loud").get("type").asString()).isEqualTo("boolean");
        assertThat(props.get("loud").get("x-exeris-primitive").asBoolean()).isTrue();
        assertThat(props.get("tags").get("type").asString()).isEqualTo("array");
        assertThat(props.get("tags").get("items").get("type").asString()).isEqualTo("string");
        assertThat(props.get("level").get("$ref").asString()).isEqualTo("#/$defs/ProbeMetadata.Level");
        assertThat(props.get("inner").get("$ref").asString()).isEqualTo("#/$defs/ProbeMetadata.Inner");
    }

    @Test
    @DisplayName("@JsonProperty renames, @JsonIgnore is not a component, nested types get definitions")
    void namingAndNesting(@TempDir Path classes) throws IOException {
        JsonNode schema = compileAndRead(classes);
        JsonNode props = def(schema, "ProbeMetadata").get("properties");

        assertThat(props.has("renamed")).as("@JsonProperty names the wire key").isTrue();
        assertThat(props.has("original")).isFalse();
        assertThat(props.has("isLoud")).as("an @JsonIgnore accessor is not a record component").isFalse();

        JsonNode level = def(schema, "ProbeMetadata.Level");
        assertThat(level.get("type").asString()).isEqualTo("string");
        assertThat(level.get("enum")).map(JsonNode::asString).containsExactly("QUIET", "LOUD");
        assertThat(level.get("description").asString()).isEqualTo("How loudly to probe.");
    }

    @Test
    @DisplayName("prose comes from @param, and coverage is reported honestly")
    void proseAndCoverage(@TempDir Path classes) throws IOException {
        JsonNode schema = compileAndRead(classes);
        JsonNode probe = def(schema, "ProbeMetadata");

        assertThat(probe.get("description").asString()).isEqualTo("A probed thing.");
        assertThat(probe.get("x-exeris-notes").asString())
                .as("a wrapped paragraph is joined, not carried with its source line breaks")
                .contains("Notes that wrap across two source lines.");
        assertThat(probe.get("properties").get("name").get("description").asString())
                .isEqualTo("the identity of the probe");
        assertThat(probe.get("properties").get("inner").get("description"))
                .as("a component with no @param carries no invented prose")
                .isNull();

        JsonNode coverage = schema.get("x-exeris-prose-coverage");
        assertThat(coverage.get("properties").asInt())
                .as("seven on the record plus one on the nested Inner — coverage counts every "
                        + "definition in the document, not just the root")
                .isEqualTo(8);
        assertThat(coverage.get("describedProperties").asInt())
                .as("three of the eight have a @param")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("the emitter refuses to stamp a schema with no versions")
    void refusesWithoutVersions(@TempDir Path classes) throws IOException {
        // Refused rather than stamped "unknown": a consumer pairs a baseline against
        // astSchemaVersion, and it can only do that if the field is trustworthy.
        List<String> errors = compile(classes, List.of("-classpath", System.getProperty("java.class.path")));
        assertThat(errors).anyMatch(message -> message.contains("exeris.schema.astVersion"));
        assertThat(classes.resolve(AstSchemaProcessor.RESOURCE_PATH)).doesNotExist();
    }

    // ---- harness ---------------------------------------------------------

    private static JsonNode def(JsonNode schema, String name) {
        JsonNode found = schema.get("$defs").get(name);
        assertThat(found).as("no definition for %s", name).isNotNull();
        return found;
    }

    private static JsonNode compileAndRead(Path classes) throws IOException {
        List<String> options = List.of(
                "-classpath", System.getProperty("java.class.path"),
                "-A" + AstSchemaProcessor.OPT_SDK_VERSION + "=9.9.9",
                "-A" + AstSchemaProcessor.OPT_AST_VERSION + "=9.9.0");
        assertThat(compile(classes, options)).as("the probe must compile").isEmpty();
        Path written = classes.resolve(AstSchemaProcessor.RESOURCE_PATH);
        assertThat(written).as("the processor wrote no schema").exists();
        return JsonMapper.builder().build().readTree(Files.readString(written));
    }

    /** @return every error javac reported, empty when it was happy */
    private static List<String> compile(Path classes, List<String> options) throws IOException {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertThat(javac).as("run this on a JDK, not a JRE").isNotNull();

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = javac.getStandardFileManager(diagnostics, null, null)) {
            List<String> all = new ArrayList<>(options);
            all.addAll(List.of("-d", classes.toString()));
            JavaCompiler.CompilationTask task = javac.getTask(
                    null, files, diagnostics, all, null, List.of(new InMemorySource(PROBE)));
            task.setProcessors(List.of(new AstSchemaProcessor()));
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
            super(URI.create("string:///eu/exeris/sdk/sourcemodel/ast/ProbeMetadata.java"), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
}
