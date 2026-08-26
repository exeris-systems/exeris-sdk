package eu.exeris.sdk.tck;

import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.FieldMetadata;
import eu.exeris.sdk.sourcemodel.ast.RelationshipMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract every reader of {@code @ExerisDomain} source must satisfy.
 *
 * <p>Extend it, implement {@link #read(String)} against your reader, and the suite runs. A binder
 * that reads from compiled elements rather than source text (an annotation processor, say) can
 * implement the method by driving javac over the given text — the kit never assumes how.
 *
 * <pre>{@code
 * class MyReaderTckTest extends AbstractMetadataReaderTck {
 *     protected DomainMetadata read(String entitySource) {
 *         return new MyReader().read(entitySource);
 *     }
 * }
 * }</pre>
 *
 * <p>The cases are not a wish list. Each one is a defect this ecosystem actually shipped: a reader
 * inventing an entity name from an attribute the annotation never declared, constraint values
 * landing on a carrier no generator reads, a zero bound dropped as if it were absent, a
 * relationship cardinality read under the wrong key so every edge came back as the builder default.
 */
/*
 * S5960 suppressed: in a TCK the assertions are the shipped artifact, not residue. Full rationale
 * on {@code AbstractExerisTck}.
 */
@SuppressWarnings("java:S5960")
public abstract class AbstractMetadataReaderTck extends AbstractExerisTck {

    /**
     * Reads one entity's source into the AST.
     *
     * @param entitySource Java source text of a single {@code @ExerisDomain} class
     * @return the metadata your reader produces for it
     */
    protected abstract DomainMetadata read(String entitySource);

    @Test
    @DisplayName("the entity name is the class simple name")
    void entityNameIsTheClassSimpleName() {
        requireSupported(Facet.IDENTITY);
        for (String name : TckCorpus.entityNames()) {
            DomainMetadata metadata = read(TckCorpus.sourceOf(name));
            assertThat(metadata.entityName())
                    .withFailMessage(
                            "Read %s as entityName '%s'. Under Entity-First (ADR-003) the class IS "
                                    + "the identity: the name is the class simple name, and "
                                    + "@ExerisDomain declares no attribute that overrides it. A "
                                    + "reader that sources the name from anywhere else can hand back "
                                    + "a different identity than the producer for the same file.",
                            name, metadata.entityName())
                    .isEqualTo(name);
            assertThat(metadata.packageName()).isEqualTo(TckCorpus.packageName());
        }
    }

    @Test
    @DisplayName("@Field(required) is carried on the field")
    void requiredIsCarriedOnTheField() {
        requireSupported(Facet.FIELDS);
        DomainMetadata order = read(TckCorpus.sourceOf(TckCorpus.ORDER));
        assertThat(field(order, "reference").required())
                .withFailMessage("@Field(required = true) on Order.reference did not reach FieldMetadata.required().")
                .isTrue();
        assertThat(field(order, "note").required())
                .withFailMessage("Order.note declares no required = true, yet it was read as required.")
                .isFalse();
    }

    @Test
    @DisplayName("@Validation constraints land on FieldMetadata, the single carrier")
    void validationConstraintsLandOnFieldMetadata() {
        requireSupported(Facet.VALIDATION_BOUNDS);
        FieldMetadata reference = field(read(TckCorpus.sourceOf(TckCorpus.ORDER)), "reference");
        assertThat(reference.minLength())
                .withFailMessage(
                        "@Validation(minLength = 3) did not reach FieldMetadata.minLength(). Since "
                                + "ADR-054 @Validation is the sole declaration site for constraint "
                                + "rules and FieldMetadata their sole carrier — there is no second "
                                + "place for a reader to put them.")
                .isEqualTo(3);
        assertThat(reference.maxLength()).isEqualTo(32);
        assertThat(reference.pattern()).isEqualTo("^ORD-[0-9]+$");
    }

    @Test
    @DisplayName("a zero-valued bound is read as a bound, not as absent")
    void aZeroValuedBoundIsNotLostToItsOwnValue() {
        requireSupported(Facet.VALIDATION_BOUNDS);
        FieldMetadata total = field(read(TckCorpus.sourceOf(TckCorpus.ORDER)), "totalCents");
        assertThat(total.min())
                .withFailMessage(
                        "@Validation(min = 0) on Order.totalCents read back as %s. Zero is a real "
                                + "bound — a non-negativity floor — and is exactly the value a "
                                + "class-level @JsonInclude(NON_DEFAULT) drops, treating boxed zero "
                                + "as empty. Losing it turns 'not negative' into 'unbounded' with no "
                                + "diagnostic anywhere.",
                        total.min())
                .isEqualTo(0L);
        assertThat(total.max()).isEqualTo(1_000_000L);
    }

    @Test
    @DisplayName("the relationship carries the cardinality the source declares")
    void relationshipCardinalityIsTheDeclaredOne() {
        requireSupported(Facet.RELATIONSHIPS);
        DomainMetadata customer = read(TckCorpus.sourceOf(TckCorpus.CUSTOMER));
        assertThat(customer.relationships())
                .withFailMessage("Customer declares one @Relationship; the reader returned none.")
                .isNotEmpty();
        RelationshipMetadata orders = customer.relationships().stream()
                .filter(r -> "orders".equals(r.fieldName()) || "orders".equals(r.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No relationship read for Customer.orders; got " + customer.relationships()));
        assertThat(orders.type())
                .withFailMessage(
                        "Customer.orders declares relationshipType = ONE_TO_MANY and was read as %s. "
                                + "A cardinality read under the wrong attribute key does not fail — "
                                + "it silently yields the builder default, and the inverse side then "
                                + "generates the foreign key, its index, its constraint and a finder "
                                + "that all belong to the other side.",
                        orders.type())
                .isEqualTo(RelationshipMetadata.RelationType.ONE_TO_MANY);
    }

    @Test
    @DisplayName("@Action is read under the name the annotation declares")
    void actionIsReadUnderTheDeclaredName() {
        requireSupported(Facet.ACTIONS);
        DomainMetadata order = read(TckCorpus.sourceOf(TckCorpus.ORDER));
        assertThat(order.actions())
                .withFailMessage("Order declares @Action(name = \"submit\"); the reader returned none.")
                .isNotEmpty();
        assertThat(order.actions().stream().map(a -> a.name()).toList())
                .withFailMessage(
                        "Order's action did not come back under its declared name 'submit'. The "
                                + "@Action(name) is the action identity and may differ from the Java "
                                + "method name by design.")
                .contains("submit");
    }

    /**
     * @param metadata the entity to look in
     * @param fieldName the field to find
     * @return that field's metadata
     * @throws AssertionError when the reader returned no such field
     */
    private static FieldMetadata field(DomainMetadata metadata, String fieldName) {
        List<FieldMetadata> fields = metadata.fields() == null ? List.of() : metadata.fields();
        Optional<FieldMetadata> found = fields.stream()
                .filter(f -> fieldName.equals(f.name()))
                .findFirst();
        if (found.isEmpty()) {
            throw new AssertionError("No field '" + fieldName + "' was read from "
                    + metadata.entityName() + "; got " + fields.stream().map(FieldMetadata::name).toList());
        }
        return found.get();
    }
}
