package eu.exeris.sdk.sourcemodel.io;

import eu.exeris.sdk.sourcemodel.ast.ActionMetadata;
import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.FieldMetadata;
import eu.exeris.sdk.sourcemodel.ast.RelationshipMetadata;
import eu.exeris.sdk.sourcemodel.mutation.MutationOp;
import eu.exeris.sdk.sourcemodel.mutation.MutationPath;
import eu.exeris.sdk.sourcemodel.mutation.MutationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Three-way drift detection (ADR-042 slice 2). Hand-built baseline/current pairs
 * pin the SUCCESS / CONFLICT / convergent / sibling matrix precisely; a
 * corpus-backed case exercises the detector against a real budgetHQ entity read
 * by {@link SourceModelReader}.
 */
@DisplayName("SourceModelConflictDetector — AST-level three-way drift")
class SourceModelConflictDetectorTest {

    private static final String ENTITY = "Order";
    private static final String PKG = "com.acme.domain";

    private final SourceModelConflictDetector detector = new SourceModelConflictDetector();

    // ---- field ops -------------------------------------------------------

    @Test
    @DisplayName("addField on an unedited entity applies cleanly (no drift)")
    void addFieldNoDrift() {
        DomainMetadata baseline = domainWithFields(field("amount", "BigDecimal"));
        DomainMetadata current = domainWithFields(field("amount", "BigDecimal"));
        MutationOp op = new MutationOp.AddField(
                MutationPath.field(ENTITY, "discount").toString(), field("discount", "BigDecimal"));

        assertThat(detector.detect(op, baseline, current)).isInstanceOf(MutationResult.Success.class);
    }

    @Test
    @DisplayName("addField is convergent when the user already added the identical field")
    void addFieldConvergent() {
        FieldMetadata discount = field("discount", "BigDecimal");
        DomainMetadata baseline = domainWithFields(field("amount", "BigDecimal"));
        DomainMetadata current = domainWithFields(field("amount", "BigDecimal"), discount);
        MutationOp op = new MutationOp.AddField(MutationPath.field(ENTITY, "discount").toString(), discount);

        assertThat(detector.detect(op, baseline, current)).isInstanceOf(MutationResult.Success.class);
    }

    @Test
    @DisplayName("addField conflicts when the user added a different field of the same name")
    void addFieldConflict() {
        DomainMetadata baseline = domainWithFields(field("amount", "BigDecimal"));
        DomainMetadata current = domainWithFields(field("amount", "BigDecimal"), field("discount", "Integer"));
        MutationOp op = new MutationOp.AddField(
                MutationPath.field(ENTITY, "discount").toString(), field("discount", "BigDecimal"));

        MutationResult result = detector.detect(op, baseline, current);
        assertThat(result).isInstanceOf(MutationResult.Conflict.class);
        MutationResult.Conflict conflict = (MutationResult.Conflict) result;
        assertThat(conflict.path()).isEqualTo("/entities/Order/fields/discount");
        assertThat(conflict.baselineValue()).isNull();              // absent in baseline
        assertThat(conflict.currentValue()).contains("Integer");    // user's drifted field
    }

    @Test
    @DisplayName("changeFieldType is convergent when the user already retyped to the target type")
    void changeFieldTypeConvergent() {
        DomainMetadata baseline = domainWithFields(field("amount", "BigDecimal"));
        DomainMetadata current = domainWithFields(field("amount", "Long"));
        MutationOp op = new MutationOp.ChangeFieldType(MutationPath.field(ENTITY, "amount").toString(), "Long");

        assertThat(detector.detect(op, baseline, current)).isInstanceOf(MutationResult.Success.class);
    }

    @Test
    @DisplayName("changeFieldType conflicts when the user retyped to a different type")
    void changeFieldTypeConflict() {
        DomainMetadata baseline = domainWithFields(field("amount", "BigDecimal"));
        DomainMetadata current = domainWithFields(field("amount", "Long"));
        MutationOp op = new MutationOp.ChangeFieldType(MutationPath.field(ENTITY, "amount").toString(), "BigDecimal");

        MutationResult result = detector.detect(op, baseline, current);
        assertThat(result).isInstanceOf(MutationResult.Conflict.class);
        MutationResult.Conflict conflict = (MutationResult.Conflict) result;
        assertThat(conflict.baselineValue()).contains("BigDecimal");
        assertThat(conflict.currentValue()).contains("Long");
        assertThat(conflict.intendedValue()).isEqualTo("type=BigDecimal");
    }

    @Test
    @DisplayName("changeFieldType with no user edit applies cleanly")
    void changeFieldTypeNoDrift() {
        DomainMetadata baseline = domainWithFields(field("amount", "BigDecimal"));
        DomainMetadata current = domainWithFields(field("amount", "BigDecimal"));
        MutationOp op = new MutationOp.ChangeFieldType(MutationPath.field(ENTITY, "amount").toString(), "Long");

        assertThat(detector.detect(op, baseline, current)).isInstanceOf(MutationResult.Success.class);
    }

    @Test
    @DisplayName("removeField is convergent when the user already removed it")
    void removeFieldConvergent() {
        DomainMetadata baseline = domainWithFields(field("amount", "BigDecimal"), field("legacy", "String"));
        DomainMetadata current = domainWithFields(field("amount", "BigDecimal"));
        MutationOp op = new MutationOp.RemoveField(MutationPath.field(ENTITY, "legacy").toString());

        assertThat(detector.detect(op, baseline, current)).isInstanceOf(MutationResult.Success.class);
    }

    @Test
    @DisplayName("removeField conflicts when the user edited the field being removed")
    void removeFieldConflict() {
        DomainMetadata baseline = domainWithFields(field("legacy", "String"));
        DomainMetadata current = domainWithFields(field("legacy", "Integer"));
        MutationOp op = new MutationOp.RemoveField(MutationPath.field(ENTITY, "legacy").toString());

        assertThat(detector.detect(op, baseline, current)).isInstanceOf(MutationResult.Conflict.class);
    }

    @Test
    @DisplayName("a sibling-field edit never conflicts with an op on another field")
    void siblingFieldDriftIsNotAConflict() {
        DomainMetadata baseline = domainWithFields(field("amount", "BigDecimal"), field("note", "String"));
        DomainMetadata current = domainWithFields(field("amount", "Long"), field("note", "String"));
        MutationOp op = new MutationOp.RemoveField(MutationPath.field(ENTITY, "note").toString());

        assertThat(detector.detect(op, baseline, current)).isInstanceOf(MutationResult.Success.class);
    }

    // ---- relationship ops ------------------------------------------------

    @Test
    @DisplayName("changeRelationshipCardinality is convergent / conflicting on the cardinality")
    void changeRelationshipCardinality() {
        RelationshipMetadata mto = rel("customer", RelationshipMetadata.RelationType.MANY_TO_ONE);
        RelationshipMetadata otm = rel("customer", RelationshipMetadata.RelationType.ONE_TO_MANY);
        DomainMetadata baseline = domainWithRelationships(mto);
        DomainMetadata current = domainWithRelationships(otm);
        String path = MutationPath.relationship(ENTITY, "customer").toString();

        assertThat(detector.detect(
                new MutationOp.ChangeRelationshipCardinality(path, RelationshipMetadata.RelationType.ONE_TO_MANY),
                baseline, current)).isInstanceOf(MutationResult.Success.class);
        assertThat(detector.detect(
                new MutationOp.ChangeRelationshipCardinality(path, RelationshipMetadata.RelationType.MANY_TO_MANY),
                baseline, current)).isInstanceOf(MutationResult.Conflict.class);
    }

    @Test
    @DisplayName("removeRelationship conflicts when the user retargeted it")
    void removeRelationshipConflict() {
        DomainMetadata baseline = domainWithRelationships(rel("customer", "Customer"));
        DomainMetadata current = domainWithRelationships(rel("customer", "Account"));
        MutationOp op = new MutationOp.RemoveRelationship(MutationPath.relationship(ENTITY, "customer").toString());

        assertThat(detector.detect(op, baseline, current)).isInstanceOf(MutationResult.Conflict.class);
    }

    // ---- action ops ------------------------------------------------------

    @Test
    @DisplayName("removeAction is convergent when already gone, conflicting when edited")
    void removeAction() {
        ActionMetadata shipPost = ActionMetadata.builder("ship").httpMethod("POST").build();
        ActionMetadata shipPut = ActionMetadata.builder("ship").httpMethod("PUT").build();
        String path = MutationPath.action(ENTITY, "ship").toString();

        assertThat(detector.detect(new MutationOp.RemoveAction(path),
                domainWithActions(shipPost), domainWithActions()))
                .isInstanceOf(MutationResult.Success.class);
        assertThat(detector.detect(new MutationOp.RemoveAction(path),
                domainWithActions(shipPost), domainWithActions(shipPut)))
                .isInstanceOf(MutationResult.Conflict.class);
    }

    // ---- renameField -----------------------------------------------------

    @Test
    @DisplayName("renameField applies cleanly when the field is untouched")
    void renameFieldClean() {
        DomainMetadata baseline = domainWithFields(field("amount", "BigDecimal"));
        DomainMetadata current = domainWithFields(field("amount", "BigDecimal"));
        MutationOp op = new MutationOp.RenameField(MutationPath.field(ENTITY, "amount").toString(), "total");

        assertThat(detector.detect(op, baseline, current)).isInstanceOf(MutationResult.Success.class);
    }

    @Test
    @DisplayName("renameField is convergent when the user already performed the rename")
    void renameFieldConvergent() {
        DomainMetadata baseline = domainWithFields(field("amount", "BigDecimal"));
        DomainMetadata current = domainWithFields(field("total", "BigDecimal"));   // amount renamed to total
        MutationOp op = new MutationOp.RenameField(MutationPath.field(ENTITY, "amount").toString(), "total");

        assertThat(detector.detect(op, baseline, current)).isInstanceOf(MutationResult.Success.class);
    }

    @Test
    @DisplayName("renameField conflicts when the field being renamed was edited")
    void renameFieldConflictOnEditedSource() {
        DomainMetadata baseline = domainWithFields(field("amount", "BigDecimal"));
        DomainMetadata current = domainWithFields(field("amount", "Long"));   // user retyped, didn't rename
        MutationOp op = new MutationOp.RenameField(MutationPath.field(ENTITY, "amount").toString(), "total");

        MutationResult result = detector.detect(op, baseline, current);
        assertThat(result).isInstanceOf(MutationResult.Conflict.class);
        assertThat(((MutationResult.Conflict) result).path()).isEqualTo("/entities/Order/fields/amount");
    }

    @Test
    @DisplayName("renameField conflicts when the target name is already occupied")
    void renameFieldConflictOnOccupiedTarget() {
        DomainMetadata baseline = domainWithFields(field("amount", "BigDecimal"));
        DomainMetadata current = domainWithFields(field("amount", "BigDecimal"), field("total", "Integer"));
        MutationOp op = new MutationOp.RenameField(MutationPath.field(ENTITY, "amount").toString(), "total");

        MutationResult result = detector.detect(op, baseline, current);
        assertThat(result).isInstanceOf(MutationResult.Conflict.class);
        assertThat(((MutationResult.Conflict) result).path()).isEqualTo("/entities/Order/fields/total");
    }

    // ---- structural validation errors ------------------------------------

    @Test
    @DisplayName("an unparseable target path is a VALIDATION_ERROR")
    void unparseablePathIsValidationError() {
        MutationOp op = new MutationOp.RemoveField("not-a-path");
        MutationResult result = detector.detect(op, domainWithFields(), domainWithFields());
        assertThat(result).isInstanceOf(MutationResult.ValidationError.class);
        assertThat(((MutationResult.ValidationError) result).message()).contains("unresolvable target path");
    }

    @Test
    @DisplayName("a path of the wrong kind for the op is a VALIDATION_ERROR")
    void pathKindMismatchIsValidationError() {
        // a field op pointed at a relationship path
        MutationOp op = new MutationOp.RemoveField(MutationPath.relationship(ENTITY, "customer").toString());
        MutationResult result = detector.detect(op, domainWithFields(), domainWithFields());
        assertThat(result).isInstanceOf(MutationResult.ValidationError.class);
        assertThat(((MutationResult.ValidationError) result).message()).contains("FIELD path");
    }

    @Test
    @DisplayName("a path targeting a different entity is a VALIDATION_ERROR")
    void wrongEntityIsValidationError() {
        MutationOp op = new MutationOp.RemoveField(MutationPath.field("Customer", "amount").toString());
        MutationResult result = detector.detect(op, domainWithFields(), domainWithFields());
        assertThat(result).isInstanceOf(MutationResult.ValidationError.class);
        assertThat(((MutationResult.ValidationError) result).message()).contains("Customer");
    }

    // ---- batch -----------------------------------------------------------

    @Test
    @DisplayName("batch detect preserves order and per-op verdicts")
    void batchDetect() {
        DomainMetadata baseline = domainWithFields(field("amount", "BigDecimal"));
        DomainMetadata current = domainWithFields(field("amount", "Long"));
        List<MutationOp> ops = List.of(
                new MutationOp.ChangeFieldType(MutationPath.field(ENTITY, "amount").toString(), "Long"),     // convergent
                new MutationOp.ChangeFieldType(MutationPath.field(ENTITY, "amount").toString(), "Instant"));  // conflict

        List<MutationResult> results = detector.detect(ops, baseline, current);
        assertThat(results).hasSize(2);
        assertThat(results.get(0)).isInstanceOf(MutationResult.Success.class);
        assertThat(results.get(1)).isInstanceOf(MutationResult.Conflict.class);
    }

    // ---- corpus-backed ---------------------------------------------------

    @Test
    @DisplayName("detects drift on a real budgetHQ entity read by the reader")
    void corpusBackedDrift() {
        DomainMetadata baseline = readCorpus("BankAccount.java");
        FieldMetadata first = baseline.fields().getFirst();

        // simulate a user edit: the first field retyped to BigInteger
        List<FieldMetadata> driftedFields = new ArrayList<>(baseline.fields());
        driftedFields.set(0, field(first.name(), "BigInteger"));
        DomainMetadata current = DomainMetadata.builder(baseline.entityName(), baseline.packageName())
                .fields(driftedFields).build();

        String path = MutationPath.field(baseline.entityName(), first.name()).toString();

        // op lands on the same type the user chose -> convergent
        assertThat(detector.detect(new MutationOp.ChangeFieldType(path, "BigInteger"), baseline, current))
                .isInstanceOf(MutationResult.Success.class);
        // op wants a third type -> conflict
        assertThat(detector.detect(new MutationOp.ChangeFieldType(path, "Instant"), baseline, current))
                .isInstanceOf(MutationResult.Conflict.class);
    }

    // ---- helpers ---------------------------------------------------------

    private static FieldMetadata field(String name, String type) {
        return FieldMetadata.builder(name, type).build();
    }

    private static RelationshipMetadata rel(String name, RelationshipMetadata.RelationType type) {
        return RelationshipMetadata.builder(name, "Customer").type(type).build();
    }

    private static RelationshipMetadata rel(String name, String target) {
        return RelationshipMetadata.builder(name, target).type(RelationshipMetadata.RelationType.MANY_TO_ONE).build();
    }

    private static DomainMetadata domainWithFields(FieldMetadata... fields) {
        return DomainMetadata.builder(ENTITY, PKG).fields(List.of(fields)).build();
    }

    private static DomainMetadata domainWithRelationships(RelationshipMetadata... rels) {
        return DomainMetadata.builder(ENTITY, PKG).relationships(List.of(rels)).build();
    }

    private static DomainMetadata domainWithActions(ActionMetadata... actions) {
        return DomainMetadata.builder(ENTITY, PKG).actions(List.of(actions)).build();
    }

    private static DomainMetadata readCorpus(String fileName) {
        Path file = Path.of("src", "test", "resources", "corpus", "budgethq", fileName);
        try {
            return new SourceModelReader().read(Files.readString(file)).orElseThrow();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
