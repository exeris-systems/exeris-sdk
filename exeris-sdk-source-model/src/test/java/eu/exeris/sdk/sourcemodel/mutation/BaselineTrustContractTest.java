package eu.exeris.sdk.sourcemodel.mutation;

import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.FieldMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

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
