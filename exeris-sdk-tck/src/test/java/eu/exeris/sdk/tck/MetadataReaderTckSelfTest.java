package eu.exeris.sdk.tck;

import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.FieldMetadata;
import eu.exeris.sdk.sourcemodel.ast.RelationshipMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves {@link AbstractMetadataReaderTck}'s cases are not vacuous.
 *
 * <p>Each case is driven directly — no JUnit-in-JUnit — against a conforming binding and against
 * one broken in exactly the way the case describes. A case that passes both is worthless: a
 * non-conforming reader would pass the suite and the contract would read as enforced while nothing
 * enforced it. The fakes are {@code static} so JUnit does not discover them as suites of their own.
 */
@DisplayName("the reader TCK rejects a non-conforming reader")
class MetadataReaderTckSelfTest {

    @Test
    @DisplayName("a conforming reader passes every case")
    void conformingReaderPassesEveryCase() {
        Reader reader = new Reader(UnaryOperator.identity());
        assertThatCode(() -> {
            reader.entityNameIsTheClassSimpleName();
            reader.requiredIsCarriedOnTheField();
            reader.validationConstraintsLandOnFieldMetadata();
            reader.aZeroValuedBoundIsNotLostToItsOwnValue();
            reader.relationshipCardinalityIsTheDeclaredOne();
            reader.actionIsReadUnderTheDeclaredName();
            reader.mandatoryFacetsAreNotDeclaredUnsupported();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an entity name sourced from anywhere but the class is caught")
    void entityNameCaseIsNotVacuous() {
        Reader renaming = new Reader(m -> rebuild(m, m.entityName() + "Entity"));
        assertThatThrownBy(renaming::entityNameIsTheClassSimpleName)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Entity-First");
    }

    @Test
    @DisplayName("a dropped required flag is caught")
    void requiredCaseIsNotVacuous() {
        Reader dropping = new Reader(m -> withFields(m, f -> copy(f).required(false).build()));
        assertThatThrownBy(dropping::requiredIsCarriedOnTheField)
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("a constraint that never reaches FieldMetadata is caught")
    void validationCaseIsNotVacuous() {
        Reader dropping = new Reader(m -> withFields(m, f -> copy(f).pattern(null).build()));
        assertThatThrownBy(dropping::validationConstraintsLandOnFieldMetadata)
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("a zero bound dropped as if it were absent is caught")
    void zeroBoundCaseIsNotVacuous() {
        Reader dropping = new Reader(m -> withFields(m, f -> copy(f).min(null).build()));
        assertThatThrownBy(dropping::aZeroValuedBoundIsNotLostToItsOwnValue)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("non-negativity floor");
    }

    @Test
    @DisplayName("a cardinality silently left at the builder default is caught")
    void cardinalityCaseIsNotVacuous() {
        Reader defaulting = new Reader(m -> m.relationships() == null || m.relationships().isEmpty()
                ? m
                : DomainMetadata.builder(m.entityName(), m.packageName())
                        .fields(m.fields())
                        .actions(m.actions())
                        .relationships(List.of(RelationshipMetadata.manyToOne("orders", TckCorpus.ORDER)))
                        .build());
        assertThatThrownBy(defaulting::relationshipCardinalityIsTheDeclaredOne)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("MANY_TO_ONE");
    }

    @Test
    @DisplayName("an action read under the method name rather than the declared name is caught")
    void actionCaseIsNotVacuous() {
        Reader misnaming = new Reader(m -> m.actions() == null || m.actions().isEmpty()
                ? m
                : DomainMetadata.builder(m.entityName(), m.packageName())
                        .fields(m.fields())
                        .actions(List.of(eu.exeris.sdk.sourcemodel.ast.ActionMetadata.simple("submitOrder")))
                        .relationships(m.relationships())
                        .build());
        assertThatThrownBy(misnaming::actionIsReadUnderTheDeclaredName)
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("declaring a mandatory facet unsupported is caught")
    void theSkipTrapdoorIsClosed() {
        Reader optingOut = new Reader(UnaryOperator.identity()) {
            @Override
            protected Set<Facet> unsupportedFacets() {
                return Set.of(Facet.FIELDS, Facet.ACTIONS);
            }
        };
        assertThatThrownBy(optingOut::mandatoryFacetsAreNotDeclaredUnsupported)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("cannot be opted out of");

        // …and the facet that IS optional still skips rather than failing.
        assertThat(optingOut.unsupportedFacets()).contains(Facet.ACTIONS);
    }

    /** A reader binding whose output is the reference metadata put through one transformation. */
    static class Reader extends AbstractMetadataReaderTck {

        private final UnaryOperator<DomainMetadata> defect;

        Reader(UnaryOperator<DomainMetadata> defect) {
            this.defect = defect;
        }

        @Override
        protected DomainMetadata read(String entitySource) {
            return defect.apply(ReferenceBinding.readFrom(entitySource));
        }
    }

    private static DomainMetadata rebuild(DomainMetadata source, String entityName) {
        return DomainMetadata.builder(entityName, source.packageName())
                .fields(source.fields())
                .actions(source.actions())
                .relationships(source.relationships())
                .build();
    }

    private static DomainMetadata withFields(DomainMetadata source, UnaryOperator<FieldMetadata> perField) {
        List<FieldMetadata> mutated = new ArrayList<>();
        for (FieldMetadata field : source.fields()) {
            mutated.add(perField.apply(field));
        }
        return DomainMetadata.builder(source.entityName(), source.packageName())
                .fields(mutated)
                .actions(source.actions())
                .relationships(source.relationships())
                .build();
    }

    private static FieldMetadata.Builder copy(FieldMetadata field) {
        return FieldMetadata.builder(field.name(), field.type())
                .displayName(field.displayName())
                .required(field.required())
                .unique(field.unique())
                .minLength(field.minLength())
                .maxLength(field.maxLength())
                .min(field.min())
                .max(field.max())
                .pattern(field.pattern());
    }
}
