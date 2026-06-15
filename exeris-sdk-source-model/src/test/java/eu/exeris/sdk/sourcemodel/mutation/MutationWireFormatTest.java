package eu.exeris.sdk.sourcemodel.mutation;

import eu.exeris.sdk.sourcemodel.ast.ActionMetadata;
import eu.exeris.sdk.sourcemodel.ast.FieldMetadata;
import eu.exeris.sdk.sourcemodel.ast.RelationshipMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wire-format guard for the 0.5.0 mutation surface (ADR-042 slice 1), the
 * sibling of {@code AstJsonRoundTripTest} for {@link MutationOp} /
 * {@link MutationResult}. Each value is serialized and deserialized
 * <strong>through its sealed-interface type</strong> — the way the transport
 * carries it — so the polymorphic {@code "op"} / {@code "outcome"} discriminator
 * is exercised and the concrete subtype must be recovered intact.
 */
@DisplayName("Mutation surface survives polymorphic JSON round-trip")
class MutationWireFormatTest {

    /** Same config the AST wire-format guard and downstream consumers must use. */
    private final ObjectMapper mapper = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
            .build();

    // ---- MutationOp ------------------------------------------------------

    @Test
    @DisplayName("addField carries the new field shape")
    void addFieldRoundTrips() {
        MutationOp op = new MutationOp.AddField(
                MutationPath.field("Order", "total").toString(),
                FieldMetadata.builder("total", "BigDecimal").required(true).build());
        assertOpRoundTrip(op, MutationOp.AddField.class, "addField");
    }

    @Test
    @DisplayName("removeField carries only its path")
    void removeFieldRoundTrips() {
        MutationOp op = new MutationOp.RemoveField(MutationPath.field("Order", "legacyCode").toString());
        assertOpRoundTrip(op, MutationOp.RemoveField.class, "removeField");
    }

    @Test
    @DisplayName("renameField carries the new name")
    void renameFieldRoundTrips() {
        MutationOp op = new MutationOp.RenameField(MutationPath.field("Order", "amount").toString(), "totalAmount");
        assertOpRoundTrip(op, MutationOp.RenameField.class, "renameField");
    }

    @Test
    @DisplayName("changeFieldType carries the new type")
    void changeFieldTypeRoundTrips() {
        MutationOp op = new MutationOp.ChangeFieldType(MutationPath.field("Order", "amount").toString(), "BigDecimal");
        assertOpRoundTrip(op, MutationOp.ChangeFieldType.class, "changeFieldType");
    }

    @Test
    @DisplayName("addRelationship carries the relationship shape")
    void addRelationshipRoundTrips() {
        MutationOp op = new MutationOp.AddRelationship(
                MutationPath.relationship("Order", "customer").toString(),
                RelationshipMetadata.builder("customer", "Customer")
                        .type(RelationshipMetadata.RelationType.MANY_TO_ONE)
                        .build());
        assertOpRoundTrip(op, MutationOp.AddRelationship.class, "addRelationship");
    }

    @Test
    @DisplayName("removeRelationship carries only its path")
    void removeRelationshipRoundTrips() {
        MutationOp op = new MutationOp.RemoveRelationship(MutationPath.relationship("Order", "customer").toString());
        assertOpRoundTrip(op, MutationOp.RemoveRelationship.class, "removeRelationship");
    }

    @Test
    @DisplayName("changeRelationshipCardinality round-trips the enum")
    void changeRelationshipCardinalityRoundTrips() {
        MutationOp op = new MutationOp.ChangeRelationshipCardinality(
                MutationPath.relationship("Order", "customer").toString(),
                RelationshipMetadata.RelationType.ONE_TO_MANY);
        assertOpRoundTrip(op, MutationOp.ChangeRelationshipCardinality.class, "changeRelationshipCardinality");
    }

    @Test
    @DisplayName("addAction carries the action shape")
    void addActionRoundTrips() {
        MutationOp op = new MutationOp.AddAction(
                MutationPath.action("Order", "ship").toString(),
                ActionMetadata.builder("ship").httpMethod("POST").build());
        assertOpRoundTrip(op, MutationOp.AddAction.class, "addAction");
    }

    @Test
    @DisplayName("removeAction carries only its path")
    void removeActionRoundTrips() {
        MutationOp op = new MutationOp.RemoveAction(MutationPath.action("Order", "ship").toString());
        assertOpRoundTrip(op, MutationOp.RemoveAction.class, "removeAction");
    }

    // ---- MutationResult --------------------------------------------------

    @Test
    @DisplayName("SUCCESS round-trips")
    void successRoundTrips() {
        MutationResult result = new MutationResult.Success(MutationPath.field("Order", "total").toString());
        assertResultRoundTrip(result, MutationResult.Success.class, "SUCCESS");
        assertThat(result.successful()).isTrue();
    }

    @Test
    @DisplayName("CONFLICT carries baseline / current / intended values")
    void conflictRoundTrips() {
        MutationResult result = new MutationResult.Conflict(
                MutationPath.field("Order", "amount").toString(), "BigDecimal", "Double", "BigDecimal");
        assertResultRoundTrip(result, MutationResult.Conflict.class, "CONFLICT");
        assertThat(result.successful()).isFalse();
    }

    @Test
    @DisplayName("CONFLICT tolerates a null side (absent in baseline)")
    void conflictWithNullSideRoundTrips() {
        // currentValue null -> dropped by NON_NULL, read back as null
        MutationResult result = new MutationResult.Conflict(
                MutationPath.field("Order", "total").toString(), null, null, "BigDecimal");
        assertResultRoundTrip(result, MutationResult.Conflict.class, "CONFLICT");
    }

    @Test
    @DisplayName("VALIDATION_ERROR round-trips (null path tolerated)")
    void validationErrorRoundTrips() {
        MutationResult withPath = new MutationResult.ValidationError(
                MutationPath.field("Order", "total").toString(), "duplicate field name");
        assertResultRoundTrip(withPath, MutationResult.ValidationError.class, "VALIDATION_ERROR");

        // an unresolvable/syntactically-invalid path can leave path null
        MutationResult noPath = new MutationResult.ValidationError(null, "unparseable target path");
        assertResultRoundTrip(noPath, MutationResult.ValidationError.class, "VALIDATION_ERROR");
    }

    @Test
    @DisplayName("NO_BASELINE round-trips each cause")
    void noBaselineRoundTrips() {
        for (MutationResult.NoBaselineCause cause : MutationResult.NoBaselineCause.values()) {
            MutationResult result = new MutationResult.NoBaseline(cause, cause.name() + " detail");
            assertResultRoundTrip(result, MutationResult.NoBaseline.class, "NO_BASELINE");
        }
        // detail null -> dropped, read back as null
        assertResultRoundTrip(
                new MutationResult.NoBaseline(MutationResult.NoBaselineCause.MISSING_BASELINE, null),
                MutationResult.NoBaseline.class, "NO_BASELINE");
    }

    // ---- helpers ---------------------------------------------------------

    private void assertOpRoundTrip(MutationOp original, Class<? extends MutationOp> concrete, String discriminator) {
        String json = mapper.writeValueAsString(original);
        assertThat(json).contains("\"op\":\"" + discriminator + "\"");
        MutationOp parsed = mapper.readValue(json, MutationOp.class);
        assertThat(parsed)
                .as("round-tripped %s (json=%s)", concrete.getSimpleName(), json)
                .isInstanceOf(concrete)
                .usingRecursiveComparison()
                .isEqualTo(original);
        assertThat(parsed.path()).isEqualTo(original.path());
    }

    private void assertResultRoundTrip(MutationResult original, Class<? extends MutationResult> concrete,
                                       String discriminator) {
        String json = mapper.writeValueAsString(original);
        assertThat(json).contains("\"outcome\":\"" + discriminator + "\"");
        MutationResult parsed = mapper.readValue(json, MutationResult.class);
        assertThat(parsed)
                .as("round-tripped %s (json=%s)", concrete.getSimpleName(), json)
                .isInstanceOf(concrete)
                .usingRecursiveComparison()
                .isEqualTo(original);
        assertThat(parsed.successful()).isEqualTo(original.successful());
    }
}
