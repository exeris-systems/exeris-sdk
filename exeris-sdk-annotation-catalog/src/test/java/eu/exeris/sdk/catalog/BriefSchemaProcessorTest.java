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
 * Drives the brief emitter over synthetic brief-shaped records and synthetic annotation sources,
 * and reads back what it wrote.
 *
 * <p>The probe declares itself into {@code eu.exeris.sdk.brief} because the emitter keys on
 * {@code eu.exeris.sdk.brief.Brief} and on the rule annotations of that package. Whether the
 * schema matches the real brief belongs where the brief is: {@code BriefSchemaContractTest} in
 * {@code exeris-sdk-source-model}.
 */
@DisplayName("brief schema processor")
class BriefSchemaProcessorTest {

    private static final String RULES = """
            package eu.exeris.sdk.brief;

            import java.lang.annotation.*;

            final class SchemaRules {
                @Retention(RetentionPolicy.SOURCE) @Target(ElementType.RECORD_COMPONENT) @interface Required { }
                @Retention(RetentionPolicy.SOURCE) @Target(ElementType.RECORD_COMPONENT) @interface NonBlank { }
                @Retention(RetentionPolicy.SOURCE) @Target(ElementType.RECORD_COMPONENT) @interface Pattern { String value(); }
                @Retention(RetentionPolicy.SOURCE) @Target(ElementType.RECORD_COMPONENT) @interface MinItems { int value(); }
                @Retention(RetentionPolicy.SOURCE) @Target(ElementType.RECORD_COMPONENT) @interface Kinds { String[] value(); }
                @Retention(RetentionPolicy.SOURCE) @Target(ElementType.RECORD_COMPONENT) @interface ConcernHint { }
                @Retention(RetentionPolicy.SOURCE) @Target(ElementType.TYPE) @Repeatable(WhenAll.class)
                @interface When {
                    String property(); String[] values(); String[] require() default {};
                    String[] forbid() default {}; String patternProperty() default ""; String pattern() default "";
                }
                @Retention(RetentionPolicy.SOURCE) @Target(ElementType.TYPE) @interface WhenAll { When[] value(); }
            }
            """;

    private static final String BRIEF = """
            package eu.exeris.sdk.brief;

            import com.fasterxml.jackson.annotation.JsonProperty;
            import eu.exeris.sdk.brief.SchemaRules.*;
            import java.util.List;

            /**
             * A probed brief.
             *
             * <p>Notes that wrap.
             *
             * @param version the version
             * @param purpose the purpose
             * @param terms the terms
             * @param refs the refs
             * @param flag a flag
             * @param boxed a boxed flag
             * @param count an unmapped number
             * @param single a single-rule record
             */
            public record Brief(
                    @Required @Pattern("^[0-9]+$") String version,
                    @Required @NonBlank String purpose,
                    @MinItems(1) List<Term> terms,
                    @Kinds("entity") List<Ref> refs,
                    boolean flag,
                    Boolean boxed,
                    Integer count,
                    Single single) {

                /**
                 * A term.
                 *
                 * @param hints the hints
                 * @param labels the labels
                 */
                public record Term(@ConcernHint List<String> hints, @NonBlank List<String> labels) { }

                /**
                 * A reference.
                 *
                 * @param kind the kind
                 * @param ref the ref
                 */
                @When(property = "kind", values = "entity", require = "ref", patternProperty = "ref", pattern = "^x$")
                public record Ref(Kind kind, String ref) { }

                /**
                 * A rule-bearing record with a single rule and a forbidden property.
                 *
                 * @param kind the kind
                 * @param extra the extra
                 */
                @When(property = "kind", values = {"entity", "action"}, forbid = "extra")
                public record Single(Kind kind, String extra) { }

                /** A kind. */
                public enum Kind { @JsonProperty("entity") ENTITY, ACTION }
            }
            """;

    private static final String HELPER = """
            package eu.exeris.sdk.brief;

            /**
             * Not reached from the root, so never a definition.
             *
             * @param note a note
             */
            public record Helper(String note) { }
            """;

    @Test
    @DisplayName("records become closed objects with their constraints, enums keep their wire names")
    void emitsClosedConstrainedDefinitions(@TempDir Path out, @TempDir Path annotations) throws IOException {
        JsonNode schema = compileAndRead(out, annotations);
        JsonNode brief = def(schema, "Brief");

        assertThat(brief.get("additionalProperties").asBoolean()).isFalse();
        assertThat(brief.get("required")).map(JsonNode::asString).containsExactly("version", "purpose", "terms");
        JsonNode props = brief.get("properties");
        assertThat(props.get("version").get("pattern").asString()).isEqualTo("^[0-9]+$");
        assertThat(props.get("purpose").get("minLength").asInt()).isEqualTo(1);
        assertThat(props.get("terms").get("minItems").asInt()).isEqualTo(1);
        assertThat(props.get("terms").get("items").get("$ref").asString()).isEqualTo("#/$defs/Brief.Term");
        assertThat(props.get("flag").get("type").asString()).isEqualTo("boolean");
        assertThat(props.get("boxed").get("type").asString()).isEqualTo("boolean");
        assertThat(props.get("count").get("x-exeris-unmapped").asString()).isEqualTo("java.lang.Integer");
        assertThat(props.get("purpose").get("description").asString()).isEqualTo("the purpose");
        assertThat(brief.get("description").asString()).isEqualTo("A probed brief.");

        JsonNode narrowed = props.get("refs").get("items").get("allOf");
        assertThat(narrowed.get(0).get("$ref").asString()).isEqualTo("#/$defs/Brief.Ref");
        assertThat(narrowed.get(1).get("properties").get("kind").get("enum"))
                .map(JsonNode::asString).containsExactly("entity");

        JsonNode term = def(schema, "Brief.Term").get("properties");
        assertThat(term.get("hints").get("items").get("$ref").asString()).isEqualTo("#/$defs/ConcernHint");
        assertThat(term.get("labels").get("items").get("pattern").asString()).isEqualTo("\\S");

        assertThat(def(schema, "Brief.Kind").get("enum")).map(JsonNode::asString).containsExactly("entity", "ACTION");
        assertThat(schema.get("$defs").has("Helper")).as("only what Brief reaches is defined").isFalse();
    }

    @Test
    @DisplayName("@When becomes if/then: required, forbidden and patterned properties")
    void conditionalRules(@TempDir Path out, @TempDir Path annotations) throws IOException {
        JsonNode schema = compileAndRead(out, annotations);

        JsonNode ref = def(schema, "Brief.Ref").get("allOf").get(0);
        assertThat(ref.get("if").get("properties").get("kind").get("const").asString()).isEqualTo("entity");
        assertThat(ref.get("then").get("required")).map(JsonNode::asString).containsExactly("ref");
        assertThat(ref.get("then").get("properties").get("ref").get("pattern").asString()).isEqualTo("^x$");

        JsonNode single = def(schema, "Brief.Single").get("allOf").get(0);
        assertThat(single.get("if").get("properties").get("kind").get("enum"))
                .map(JsonNode::asString).containsExactly("entity", "action");
        assertThat(single.get("then").get("properties").get("extra").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("the hint vocabulary: top-level annotations, less containers and those leaving")
    void vocabularyFromSources(@TempDir Path out, @TempDir Path annotations) throws IOException {
        JsonNode schema = compileAndRead(out, annotations);
        assertThat(def(schema, "ConcernHint").get("enum")).map(JsonNode::asString)
                .containsExactly("Kept", "Repeated", "StillHere");
    }

    @Test
    @DisplayName("two runs over the same sources write byte-identical files")
    void deterministic(@TempDir Path first, @TempDir Path second, @TempDir Path annotations) throws IOException {
        compileAndRead(first, annotations);
        compileAndRead(second, annotations);
        assertThat(Files.readAllBytes(first.resolve(BriefSchemaProcessor.RESOURCE_PATH)))
                .isEqualTo(Files.readAllBytes(second.resolve(BriefSchemaProcessor.RESOURCE_PATH)));
        assertThat(Files.readString(first.resolve(BriefSchemaProcessor.RESOURCE_PATH)))
                .as("no path of the build machine reaches the output")
                .doesNotContain(annotations.toString());
    }

    @Test
    @DisplayName("the root carries the format integer and the SDK version, nothing that dates it")
    void root(@TempDir Path out, @TempDir Path annotations) throws IOException {
        JsonNode schema = compileAndRead(out, annotations);
        assertThat(schema.get("briefSchemaFormat").asInt()).isEqualTo(7);
        assertThat(schema.get("sdkVersion").asString()).isEqualTo("9.9.9");
        assertThat(schema.get("$id").asString()).endsWith("/brief/7.json");
        assertThat(schema.get("$ref").asString()).isEqualTo("#/$defs/Brief");
    }

    @Test
    @DisplayName("refuses without its options, with a bad format, or without annotation sources")
    void refusals(@TempDir Path out, @TempDir Path annotations) throws IOException {
        writeAnnotations(annotations);
        String cp = System.getProperty("java.class.path");

        assertThat(compile(out, List.of("-classpath", cp))).anyMatch(m -> m.contains(BriefSchemaProcessor.OPT_FORMAT));
        assertThat(compile(out, List.of("-classpath", cp, sdk(), "-A" + BriefSchemaProcessor.OPT_FORMAT + "=one",
                sources(annotations)))).anyMatch(m -> m.contains("must be an integer"));
        assertThat(compile(out, List.of("-classpath", cp, sdk(), format(),
                "-A" + BriefSchemaProcessor.OPT_ANNOTATION_SOURCES + "=" + annotations.resolve("absent"))))
                .anyMatch(m -> m.contains("names no directory"));

        Path empty = Files.createDirectories(annotations.resolve("empty"));
        assertThat(compile(out, List.of("-classpath", cp, sdk(), format(), sources(empty))))
                .anyMatch(m -> m.contains("No annotation type"));
        assertThat(out.resolve(BriefSchemaProcessor.RESOURCE_PATH)).doesNotExist();
    }

    @Test
    @DisplayName("a compilation without the brief root writes nothing")
    void silentWithoutTheRoot(@TempDir Path out) throws IOException {
        List<String> errors = compile(out, List.of("-classpath", System.getProperty("java.class.path")),
                List.of(new InMemorySource("Helper", HELPER)));
        assertThat(errors).isEmpty();
        assertThat(out.resolve(BriefSchemaProcessor.RESOURCE_PATH)).doesNotExist();
    }

    // ---- harness ---------------------------------------------------------

    private static void writeAnnotations(Path root) throws IOException {
        Path pkg = Files.createDirectories(root.resolve("probe/annotation"));
        Files.writeString(pkg.resolve("package-info.java"), "package probe.annotation;\n");
        Files.writeString(pkg.resolve("Kept.java"), """
                package probe.annotation;
                public @interface Kept { @interface Nested { } }
                """);
        Files.writeString(pkg.resolve("Leaving.java"), """
                package probe.annotation;
                @Deprecated(since = "1", forRemoval = true)
                public @interface Leaving { }
                """);
        Files.writeString(pkg.resolve("StillHere.java"), """
                package probe.annotation;
                @Deprecated(since = "1")
                public @interface StillHere { }
                """);
        Files.writeString(pkg.resolve("Repeated.java"), """
                package probe.annotation;
                @java.lang.annotation.Repeatable(value = Repeateds.class)
                public @interface Repeated { }
                """);
        Files.writeString(pkg.resolve("Repeateds.java"), """
                package probe.annotation;
                public @interface Repeateds { Repeated[] value(); }
                """);
        Files.writeString(pkg.resolve("NotAnAnnotation.java"), """
                package probe.annotation;
                public class NotAnAnnotation { }
                """);
    }

    private static String sdk() {
        return "-A" + BriefSchemaProcessor.OPT_SDK_VERSION + "=9.9.9";
    }

    private static String format() {
        return "-A" + BriefSchemaProcessor.OPT_FORMAT + "=7";
    }

    private static String sources(Path annotations) {
        return "-A" + BriefSchemaProcessor.OPT_ANNOTATION_SOURCES + "=" + annotations;
    }

    private static JsonNode def(JsonNode schema, String name) {
        JsonNode found = schema.get("$defs").get(name);
        assertThat(found).as("no definition for %s", name).isNotNull();
        return found;
    }

    private static JsonNode compileAndRead(Path out, Path annotations) throws IOException {
        if (!Files.exists(annotations.resolve("probe"))) {
            writeAnnotations(annotations);
        }
        List<String> options = List.of("-classpath", System.getProperty("java.class.path"),
                sdk(), format(), sources(annotations));
        assertThat(compile(out, options)).as("the probe must compile").isEmpty();
        Path written = out.resolve(BriefSchemaProcessor.RESOURCE_PATH);
        assertThat(written).as("the processor wrote no schema").exists();
        return JsonMapper.builder().build().readTree(Files.readString(written));
    }

    private static List<String> compile(Path out, List<String> options) throws IOException {
        return compile(out, options, List.of(
                new InMemorySource("SchemaRules", RULES), new InMemorySource("Brief", BRIEF),
                new InMemorySource("Helper", HELPER)));
    }

    /** @return every error javac reported, empty when it was happy */
    private static List<String> compile(Path out, List<String> options, List<JavaFileObject> sources)
            throws IOException {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = javac.getStandardFileManager(diagnostics, null, null)) {
            List<String> all = new ArrayList<>(options);
            all.addAll(List.of("-d", out.toString()));
            JavaCompiler.CompilationTask task = javac.getTask(null, files, diagnostics, all, null, sources);
            task.setProcessors(List.of(new BriefSchemaProcessor()));
            task.call();
        }
        return diagnostics.getDiagnostics().stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .map(d -> d.getMessage(null))
                .toList();
    }

    private static final class InMemorySource extends SimpleJavaFileObject {

        private final String code;

        InMemorySource(String name, String code) {
            super(URI.create("string:///eu/exeris/sdk/brief/" + name + ".java"), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
}
