package eu.exeris.sdk.sourcemodel.io;

import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.FieldMetadata;
import eu.exeris.sdk.sourcemodel.ast.RelationshipMetadata;
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
 * Conflict-aware application (ADR-042 slice 4): apply only on a clean verdict,
 * never over a non-convergent user edit; honour the concurrency token and the
 * apply-time {@code VALIDATION_ERROR}s.
 */
@DisplayName("SourceModelMutationApplier — conflict-aware application")
class SourceModelMutationApplierTest {

    private static final String SOURCE = """
            package com.acme;
            @ExerisDomain(name = "Order")
            public class Order {
                @Field private String amount;
            }
            """;

    private final ObjectMapper mapper = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
            .build();
    private final SourceModelMutationApplier applier = new SourceModelMutationApplier();

    @Test
    @DisplayName("a clean op is applied and the new source carries the change")
    void cleanApply() {
        String baseline = baseline(field("amount", "String"));   // in sync with the source
        MutationOp op = new MutationOp.AddField(
                MutationPath.field("Order", "discount").toString(), field("discount", "BigDecimal"));

        ApplyResult result = applier.apply(op, baseline, SOURCE);
        assertThat(result.applied()).isTrue();
        assertThat(result.source()).contains("discount").isNotEqualTo(SOURCE);
    }

    @Test
    @DisplayName("a conflicting op is not applied; the source is returned unchanged")
    void conflictLeavesSourceUnchanged() {
        // baseline says amount:Long, the source says String (user drift); op wants BigDecimal
        String baseline = baseline(field("amount", "Long"));
        MutationOp op = new MutationOp.ChangeFieldType(MutationPath.field("Order", "amount").toString(), "BigDecimal");

        ApplyResult result = applier.apply(op, baseline, SOURCE);
        assertThat(result.outcome()).isInstanceOf(MutationResult.Conflict.class);
        assertThat(result.applied()).isFalse();
        assertThat(result.source()).isEqualTo(SOURCE);
    }

    @Test
    @DisplayName("an untrusted baseline blocks the write (NO_BASELINE, source unchanged)")
    void noBaselineLeavesSourceUnchanged() {
        MutationOp op = new MutationOp.ChangeFieldType(MutationPath.field("Order", "amount").toString(), "BigDecimal");
        ApplyResult result = applier.apply(op, null, SOURCE);
        assertThat(result.outcome()).isInstanceOf(MutationResult.NoBaseline.class);
        assertThat(result.source()).isEqualTo(SOURCE);
    }

    @Test
    @DisplayName("a convergent op is an idempotent no-op success")
    void convergentNoOp() {
        // baseline amount:Long, source already amount:String, op targets String -> convergent
        String baseline = baseline(field("amount", "Long"));
        MutationOp op = new MutationOp.ChangeFieldType(MutationPath.field("Order", "amount").toString(), "String");

        ApplyResult result = applier.apply(op, baseline, SOURCE);
        assertThat(result.applied()).isTrue();
        assertThat(result.source()).isEqualTo(SOURCE);   // writer no-op: already String
    }

    // ---- concurrency token (STALE_DIGEST) --------------------------------

    @Test
    @DisplayName("a stale concurrency token rejects the write (STALE_DIGEST)")
    void staleTokenRejected() {
        String baseline = baseline(field("amount", "String"));
        MutationOp op = new MutationOp.AddField(
                MutationPath.field("Order", "discount").toString(), field("discount", "BigDecimal"));

        ApplyResult result = applier.apply(op, baseline, SOURCE, "a-stale-token");
        assertThat(result.outcome()).isInstanceOf(MutationResult.NoBaseline.class);
        assertThat(((MutationResult.NoBaseline) result.outcome()).cause())
                .isEqualTo(MutationResult.NoBaselineCause.STALE_DIGEST);
        assertThat(result.source()).isEqualTo(SOURCE);
    }

    @Test
    @DisplayName("a matching concurrency token lets the write proceed")
    void matchingTokenProceeds() {
        String baseline = baseline(field("amount", "String"));
        MutationOp op = new MutationOp.AddField(
                MutationPath.field("Order", "discount").toString(), field("discount", "BigDecimal"));

        ApplyResult result = applier.apply(op, baseline, SOURCE, SourceDigest.of(SOURCE));
        assertThat(result.applied()).isTrue();
        assertThat(result.source()).contains("discount");
    }

    // ---- apply-time VALIDATION_ERROR -------------------------------------

    @Test
    @DisplayName("changeRelationshipCardinality is rejected as inapplicable (no writer support)")
    void cardinalityUnsupported() {
        String baseline = baselineWithRelationship();
        MutationOp op = new MutationOp.ChangeRelationshipCardinality(
                MutationPath.relationship("Order", "customer").toString(),
                RelationshipMetadata.RelationType.ONE_TO_MANY);

        ApplyResult result = applier.apply(op, baseline, SOURCE);
        assertThat(result.outcome()).isInstanceOf(MutationResult.ValidationError.class);
        assertThat(((MutationResult.ValidationError) result.outcome()).message()).contains("cardinality");
        assertThat(result.source()).isEqualTo(SOURCE);
    }

    @Test
    @DisplayName("renaming a field absent from baseline and source is a vacuous no-op success (writer idempotent)")
    void renameAbsentFieldIsNoOp() {
        // Characterizes the documented slice-4 gap: the strict "rename of a
        // non-existent field" VALIDATION_ERROR is deferred; today the writer
        // no-ops and detection sees no drift, so the verdict is Success.
        String baseline = baseline(field("amount", "String"));
        MutationOp op = new MutationOp.RenameField(MutationPath.field("Order", "ghost").toString(), "phantom");

        ApplyResult result = applier.apply(op, baseline, SOURCE);
        assertThat(result.applied()).isTrue();
        assertThat(result.source()).isEqualTo(SOURCE);   // nothing renamed
    }

    @Test
    @DisplayName("an unparseable current source maps to VALIDATION_ERROR, not a thrown exception")
    void invalidSourceIsValidationError() {
        String baseline = baseline(field("amount", "String"));
        MutationOp op = new MutationOp.ChangeFieldType(MutationPath.field("Order", "amount").toString(), "Long");

        ApplyResult result = applier.apply(op, baseline, "this is not valid java @@@");
        assertThat(result.outcome()).isInstanceOf(MutationResult.ValidationError.class);
    }

    // ---- helpers ---------------------------------------------------------

    private static FieldMetadata field(String name, String type) {
        return FieldMetadata.builder(name, type).build();
    }

    private String baseline(FieldMetadata... fields) {
        DomainMetadata domain = DomainMetadata.builder("Order", "com.acme").fields(List.of(fields)).build();
        return withTrust(domain);
    }

    private String baselineWithRelationship() {
        DomainMetadata domain = DomainMetadata.builder("Order", "com.acme")
                .relationships(List.of(RelationshipMetadata.builder("customer", "Customer")
                        .type(RelationshipMetadata.RelationType.MANY_TO_ONE).build()))
                .build();
        return withTrust(domain);
    }

    private String withTrust(DomainMetadata domain) {
        ObjectNode node = (ObjectNode) mapper.valueToTree(domain);
        node.put("sourceDigest", SourceDigest.of(SOURCE));
        node.put("schemaVersion", SchemaVersion.CURRENT);
        return node.toString();
    }
}
