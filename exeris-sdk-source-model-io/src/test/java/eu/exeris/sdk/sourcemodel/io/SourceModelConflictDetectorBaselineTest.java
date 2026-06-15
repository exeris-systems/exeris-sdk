package eu.exeris.sdk.sourcemodel.io;

import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.FieldMetadata;
import eu.exeris.sdk.sourcemodel.mutation.MutationOp;
import eu.exeris.sdk.sourcemodel.mutation.MutationPath;
import eu.exeris.sdk.sourcemodel.mutation.MutationResult;
import eu.exeris.sdk.sourcemodel.mutation.SchemaVersion;
import eu.exeris.sdk.sourcemodel.mutation.SourceDigest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The baseline-trust gate and JSON/source detect overloads (ADR-042 slice 3).
 * The gate rejects untrustworthy baselines (missing / unparseable / schema skew)
 * before any drift comparison; a trustworthy baseline delegates to the pure
 * three-way detection of {@link SourceModelConflictDetectorTest}.
 */
@DisplayName("SourceModelConflictDetector — baseline-trust gate (slice 3)")
class SourceModelConflictDetectorBaselineTest {

    private static final String SOURCE = """
            package com.acme;
            @ExerisDomain(name = "Order")
            public class Order {
                @Field private String amount;
            }
            """;
    private static final String PATH = MutationPath.field("Order", "amount").toString();

    private final ObjectMapper mapper = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
            .build();
    private final SourceModelConflictDetector detector = new SourceModelConflictDetector();

    // ---- gate ------------------------------------------------------------

    @Test
    @DisplayName("missing / blank / unparseable baseline → NO_BASELINE(MISSING_BASELINE)")
    void missingBaseline() {
        assertNoBaseline(detector.checkBaselineTrust(null), MutationResult.NoBaselineCause.MISSING_BASELINE);
        assertNoBaseline(detector.checkBaselineTrust("   "), MutationResult.NoBaselineCause.MISSING_BASELINE);
        assertNoBaseline(detector.checkBaselineTrust("{not json"), MutationResult.NoBaselineCause.MISSING_BASELINE);
    }

    @Test
    @DisplayName("wrong or absent schemaVersion → NO_BASELINE(SCHEMA_VERSION_SKEW)")
    void schemaSkew() {
        assertNoBaseline(detector.checkBaselineTrust(baselineJson("amount", "String", "0.0.1")),
                MutationResult.NoBaselineCause.SCHEMA_VERSION_SKEW);
        assertNoBaseline(detector.checkBaselineTrust(baselineJson("amount", "String", null)),
                MutationResult.NoBaselineCause.SCHEMA_VERSION_SKEW);
    }

    @Test
    @DisplayName("current schemaVersion → trustworthy (gate returns empty)")
    void trustworthy() {
        assertThat(detector.checkBaselineTrust(baselineJson("amount", "String", SchemaVersion.CURRENT))).isEmpty();
    }

    // ---- JSON/source detect overload -------------------------------------

    @Test
    @DisplayName("detect short-circuits to NO_BASELINE for an untrusted baseline")
    void detectShortCircuitsOnUntrusted() {
        MutationOp op = new MutationOp.ChangeFieldType(PATH, "BigDecimal");
        assertThat(detector.detect(op, null, SOURCE)).isInstanceOf(MutationResult.NoBaseline.class);
        assertThat(detector.detect(op, baselineJson("amount", "String", "0.0.1"), SOURCE))
                .isInstanceOf(MutationResult.NoBaseline.class);
    }

    @Test
    @DisplayName("a trustworthy baseline delegates to three-way detection (SUCCESS / CONFLICT)")
    void trustworthyDelegatesToDetection() {
        // baseline agrees with the source (amount:String) -> no drift -> SUCCESS
        String inSync = baselineJson("amount", "String", SchemaVersion.CURRENT);
        assertThat(detector.detect(new MutationOp.ChangeFieldType(PATH, "BigDecimal"), inSync, SOURCE))
                .isInstanceOf(MutationResult.Success.class);

        // baseline says amount:Long but the source says String (user drift) -> non-convergent -> CONFLICT
        String drifted = baselineJson("amount", "Long", SchemaVersion.CURRENT);
        assertThat(detector.detect(new MutationOp.ChangeFieldType(PATH, "BigDecimal"), drifted, SOURCE))
                .isInstanceOf(MutationResult.Conflict.class);
    }

    @Test
    @DisplayName("a stale sourceDigest does NOT block detection (it is an apply-time token, not a gate)")
    void staleDigestDoesNotBlockDetection() {
        // digest doesn't match the current source, but schemaVersion is current:
        // detection must still run (this is what makes three-way detection useful).
        String wrongDigest = baselineJsonWithDigest("amount", "String", SchemaVersion.CURRENT, "deadbeefstale");
        assertThat(detector.detect(new MutationOp.ChangeFieldType(PATH, "BigDecimal"), wrongDigest, SOURCE))
                .isInstanceOf(MutationResult.Success.class);   // not NO_BASELINE
    }

    @Test
    @DisplayName("valid JSON that is not a DomainMetadata → NO_BASELINE(MISSING_BASELINE)")
    void corruptBaselineShape() {
        // passes the BaselineTrust gate (current schemaVersion) but fails DomainMetadata
        // deserialization ("fields" is a string, not an array)
        String corrupt = "{\"schemaVersion\":\"" + SchemaVersion.CURRENT
                + "\",\"sourceDigest\":\"x\",\"entityName\":\"Order\",\"fields\":\"notAnArray\"}";
        MutationResult result = detector.detect(new MutationOp.ChangeFieldType(PATH, "BigDecimal"), corrupt, SOURCE);
        assertThat(result).isInstanceOf(MutationResult.NoBaseline.class);
        assertThat(((MutationResult.NoBaseline) result).cause())
                .isEqualTo(MutationResult.NoBaselineCause.MISSING_BASELINE);
    }

    @Test
    @DisplayName("current source with no @ExerisDomain type → VALIDATION_ERROR")
    void currentSourceWithoutEntity() {
        String inSync = baselineJson("amount", "String", SchemaVersion.CURRENT);
        MutationResult result = detector.detect(new MutationOp.ChangeFieldType(PATH, "BigDecimal"),
                inSync, "package com.acme; public class Plain {}");
        assertThat(result).isInstanceOf(MutationResult.ValidationError.class);
    }

    @Test
    @DisplayName("batch detect applies the gate once and delegates per op")
    void batchDetect() {
        String drifted = baselineJson("amount", "Long", SchemaVersion.CURRENT);
        List<MutationOp> ops = List.of(
                new MutationOp.ChangeFieldType(PATH, "String"),       // convergent with the source
                new MutationOp.ChangeFieldType(PATH, "BigDecimal"));  // conflict
        List<MutationResult> results = detector.detect(ops, drifted, SOURCE);
        assertThat(results).hasSize(2);
        assertThat(results.get(0)).isInstanceOf(MutationResult.Success.class);
        assertThat(results.get(1)).isInstanceOf(MutationResult.Conflict.class);

        // untrusted baseline → every op is NO_BASELINE
        List<MutationResult> all = detector.detect(ops, null, SOURCE);
        assertThat(all).hasSize(2).allMatch(r -> r instanceof MutationResult.NoBaseline);
    }

    // ---- helpers ---------------------------------------------------------

    /** The codegen emit shape: a serialized DomainMetadata plus the two trust fields at top level. */
    private String baselineJson(String fieldName, String fieldType, String schemaVersion) {
        return baselineJsonWithDigest(fieldName, fieldType, schemaVersion, SourceDigest.of(SOURCE));
    }

    private String baselineJsonWithDigest(String fieldName, String fieldType, String schemaVersion, String digest) {
        DomainMetadata domain = DomainMetadata.builder("Order", "com.acme")
                .fields(List.of(FieldMetadata.builder(fieldName, fieldType).build()))
                .build();
        ObjectNode node = (ObjectNode) mapper.valueToTree(domain);
        node.put("sourceDigest", digest);
        if (schemaVersion != null) {
            node.put("schemaVersion", schemaVersion);
        }
        return node.toString();
    }

    private void assertNoBaseline(java.util.Optional<MutationResult.NoBaseline> result,
                                  MutationResult.NoBaselineCause cause) {
        assertThat(result).isPresent();
        assertThat(result.get().cause()).isEqualTo(cause);
    }
}
