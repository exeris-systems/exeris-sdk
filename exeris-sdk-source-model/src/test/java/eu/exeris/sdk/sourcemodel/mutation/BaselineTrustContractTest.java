package eu.exeris.sdk.sourcemodel.mutation;

import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.FieldMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The baseline-trust contract (ADR-042 slice 3): {@link SchemaVersion},
 * {@link SourceDigest}, and the {@link BaselineTrust} sibling-field design — the
 * two trust fields live in the same JSON object as the domain, each read blind
 * to the other.
 */
@DisplayName("Baseline-trust contract (SchemaVersion / SourceDigest / BaselineTrust)")
class BaselineTrustContractTest {

    private final ObjectMapper mapper = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
            .build();

    // ---- SchemaVersion ---------------------------------------------------

    @Test
    @DisplayName("SchemaVersion.isCurrent matches only the current stamp")
    void schemaVersionIsCurrent() {
        assertThat(SchemaVersion.CURRENT).isNotBlank();
        assertThat(SchemaVersion.isCurrent(SchemaVersion.CURRENT)).isTrue();
        assertThat(SchemaVersion.isCurrent("0.0.1")).isFalse();
        assertThat(SchemaVersion.isCurrent(null)).isFalse();   // absent stamp is not "compatible"
    }

    @Test
    @DisplayName("SchemaVersion.CURRENT is not a compile-time constant, so no consumer inlines it")
    void currentIsNotAConstantVariable(@TempDir Path classes) throws IOException {
        // An annotation element value of type String must be a constant expression (JLS 9.7.1),
        // so this probe compiles if and only if CURRENT is a constant variable — which is exactly
        // the property that lets javac bake the value into a consumer's own class file and keep it
        // there across an SDK bump. Asserting on the compiler is the only way to see this: a
        // ConstantValue attribute is invisible to reflection, and CURRENT reads identically either
        // way from inside this module, which is why the defect surfaced downstream and not here.
        List<String> reported = compile(classes, """
                package probe;

                import eu.exeris.sdk.sourcemodel.mutation.SchemaVersion;

                @SuppressWarnings(SchemaVersion.CURRENT)
                public class Probe {
                }
                """);

        assertThat(reported)
                .withFailMessage("SchemaVersion.CURRENT compiled where a constant expression is "
                        + "required, so it is a constant variable again and javac will inline it "
                        + "into every downstream compile site. A consumer that bumps the jar "
                        + "without recompiling then compares against the PREVIOUS value, and a "
                        + "baseline its own build just stamped reads back as SCHEMA_VERSION_SKEW. "
                        + "Keep the literal behind SchemaVersion.currentVersion().")
                .isNotEmpty();
        assertThat(String.join(" ", reported)).contains("constant");
    }

    @Test
    @DisplayName("the constant-expression probe compiles when handed a real constant")
    void theConstantProbeIsNotVacuous(@TempDir Path classes) throws IOException {
        // Same probe, same position, a genuine constant variable in place of CURRENT. Without this
        // the test above passes on any compile error at all — a typo in the probe would read as
        // proof of the property it is meant to measure.
        List<String> reported = compile(classes, """
                package probe;

                public class Probe {

                    static final String STAMP = "0.11.0";

                    @SuppressWarnings(STAMP)
                    void member() {
                    }
                }
                """);

        assertThat(reported)
                .withFailMessage("The probe fails to compile even with a real constant, so the "
                        + "assertion above measures nothing: %s", String.join("\n  ", reported))
                .isEmpty();
    }

    /**
     * @return every error javac reported, empty when it compiled
     */
    private static List<String> compile(Path classes, String source) throws IOException {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertThat(javac)
                .withFailMessage("No system Java compiler; run this on a JDK, not a JRE.")
                .isNotNull();

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = javac.getStandardFileManager(diagnostics, null, null)) {
            javac.getTask(
                    null,
                    files,
                    diagnostics,
                    // this module's own classes, so the probe can see SchemaVersion
                    List.of("-classpath", System.getProperty("java.class.path"),
                            "-d", classes.toString()),
                    null,
                    List.of(new InMemorySource(source))).call();
        }
        return diagnostics.getDiagnostics().stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .map(d -> d.getMessage(null))
                .toList();
    }

    /** A probe handed to javac without touching the filesystem. */
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

    // ---- SourceDigest ----------------------------------------------------

    @Test
    @DisplayName("SourceDigest is deterministic and normalization-stable")
    void sourceDigestNormalization() {
        String lf = "class A {\n    int x;\n}";
        String crlf = "class A {\r\n    int x;\r\n}";
        String trailingWs = "class A {   \n    int x; \n}   ";
        String trailingBlankLines = "class A {\n    int x;\n}\n\n\n";

        assertThat(SourceDigest.of(lf)).isEqualTo(SourceDigest.of(lf));            // deterministic
        assertThat(SourceDigest.of(crlf)).isEqualTo(SourceDigest.of(lf));          // CRLF == LF
        assertThat(SourceDigest.of(trailingWs)).isEqualTo(SourceDigest.of(lf));    // trailing ws neutral
        assertThat(SourceDigest.of(trailingBlankLines)).isEqualTo(SourceDigest.of(lf)); // EOF blank lines neutral
    }

    @Test
    @DisplayName("SourceDigest distinguishes meaningful content and is hex SHA-256")
    void sourceDigestDistinguishesContent() {
        assertThat(SourceDigest.of("int x;")).isNotEqualTo(SourceDigest.of("int y;"));
        assertThat(SourceDigest.of("anything")).hasSize(64).matches("[0-9a-f]{64}");
    }

    // ---- BaselineTrust ---------------------------------------------------

    @Test
    @DisplayName("BaselineTrust round-trips; current() stamps the current schema version")
    void baselineTrustRoundTrips() {
        BaselineTrust trust = BaselineTrust.current("deadbeef");
        assertThat(trust.schemaVersion()).isEqualTo(SchemaVersion.CURRENT);

        String json = mapper.writeValueAsString(trust);
        assertThat(mapper.readValue(json, BaselineTrust.class)).isEqualTo(trust);
    }

    @Test
    @DisplayName("trust fields and domain fields coexist in one JSON, each read blind to the other")
    void siblingFieldsCoexistInOneJson() {
        DomainMetadata domain = DomainMetadata.builder("Order", "com.acme")
                .fields(List.of(FieldMetadata.builder("amount", "BigDecimal").required(true).build()))
                .build();

        // codegen's emit shape: the domain JSON object + two trust fields at top level
        ObjectNode node = (ObjectNode) mapper.valueToTree(domain);
        node.put("sourceDigest", "abc123");
        node.put("schemaVersion", SchemaVersion.CURRENT);
        String baselineJson = node.toString();

        // a DomainMetadata read ignores the trust siblings...
        DomainMetadata readDomain = mapper.readValue(baselineJson, DomainMetadata.class);
        assertThat(readDomain).isEqualTo(domain);

        // ...and a BaselineTrust read of the SAME JSON ignores every domain field
        BaselineTrust readTrust = mapper.readValue(baselineJson, BaselineTrust.class);
        assertThat(readTrust).isEqualTo(new BaselineTrust("abc123", SchemaVersion.CURRENT));
    }
}
