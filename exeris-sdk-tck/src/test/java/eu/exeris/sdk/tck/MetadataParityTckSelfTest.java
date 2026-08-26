package eu.exeris.sdk.tck;

import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.FieldMetadata;
import eu.exeris.sdk.sourcemodel.ast.RelationshipMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves {@link AbstractMetadataParityTck}'s cases are not vacuous.
 *
 * <p>Both halves here start from the same reference binding, so any failure is the injected
 * divergence and nothing else — which is the property the parity gate itself relies on. Note that
 * every defect below leaves both sides emitting perfectly well-formed metadata: that is the whole
 * difficulty of the bug class, and a self-test that broke one side into throwing would be proving
 * something easier than the real thing.
 */
@DisplayName("the parity TCK rejects a producer and a reader that disagree")
class MetadataParityTckSelfTest {

    @Test
    @DisplayName("two sides that agree pass every case")
    void agreeingSidesPassEveryCase() {
        Parity parity = new Parity(UnaryOperator.identity(), UnaryOperator.identity());
        assertThatCode(() -> {
            parity.identityAgrees();
            parity.fieldsAgree();
            parity.relationshipsAgree();
            parity.actionsAgree();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a cardinality read under the wrong key is caught")
    void cardinalityDivergenceIsCaught() {
        // The exact shape of the shipped defect: the reader takes the declared attribute, the
        // producer reads it under a name the annotation does not use and gets the builder default.
        Parity parity = new Parity(
                m -> m.relationships() == null || m.relationships().isEmpty()
                        ? m
                        : rebuild(m, List.of(RelationshipMetadata.manyToOne("orders", TckCorpus.ORDER))),
                UnaryOperator.identity());
        assertThatThrownBy(parity::relationshipsAgree)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("relationships/orders/type");
    }

    @Test
    @DisplayName("a field one side extracts and the other does not is caught")
    void missingFieldIsCaught() {
        Parity parity = new Parity(
                UnaryOperator.identity(),
                m -> withFields(m, m.fields().stream().filter(f -> !"note".equals(f.name())).toList()));
        assertThatThrownBy(parity::fieldsAgree)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("reader did not");
    }

    @Test
    @DisplayName("a bound that only one side carries is caught")
    void divergentBoundIsCaught() {
        Parity parity = new Parity(
                UnaryOperator.identity(),
                m -> withFields(m, m.fields().stream()
                        .map(f -> "totalCents".equals(f.name())
                                ? FieldMetadata.builder(f.name(), f.type()).required(f.required()).max(f.max()).build()
                                : f)
                        .toList()));
        assertThatThrownBy(parity::fieldsAgree)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("fields/totalCents/min");
    }

    @Test
    @DisplayName("an identity divergence is caught")
    void identityDivergenceIsCaught() {
        Parity parity = new Parity(
                UnaryOperator.identity(),
                m -> DomainMetadata.builder(m.entityName() + "Entity", m.packageName())
                        .fields(m.fields()).actions(m.actions()).relationships(m.relationships()).build());
        assertThatThrownBy(parity::identityAgrees)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("entityName");
    }

    @Test
    @DisplayName("an unbuilt facet skips rather than failing")
    void anUnbuiltFacetSkips() {
        Parity notBuiltYet = new Parity(UnaryOperator.identity(), UnaryOperator.identity()) {
            @Override
            protected Set<Facet> unsupportedFacets() {
                return Set.of(Facet.RELATIONSHIPS);
            }
        };
        // A skip, not a pass and not a failure: reading ahead of a producer manufactures exactly
        // the drift this suite exists to catch, so an unbuilt facet must not be asserted on.
        assertThatThrownBy(notBuiltYet::relationshipsAgree)
                .isInstanceOf(TestAbortedException.class);
        assertThatCode(notBuiltYet::fieldsAgree).doesNotThrowAnyException();
    }

    /** A parity binding whose two sides are the reference binding, each put through a defect. */
    static class Parity extends AbstractMetadataParityTck {

        private final UnaryOperator<DomainMetadata> producerDefect;
        private final UnaryOperator<DomainMetadata> readerDefect;

        Parity(UnaryOperator<DomainMetadata> producerDefect, UnaryOperator<DomainMetadata> readerDefect) {
            this.producerDefect = producerDefect;
            this.readerDefect = readerDefect;
        }

        @Override
        protected String produce(String entitySource) {
            return ReferenceBinding.json(producerDefect.apply(ReferenceBinding.readFrom(entitySource)), true);
        }

        @Override
        protected DomainMetadata read(String entitySource) {
            return readerDefect.apply(ReferenceBinding.readFrom(entitySource));
        }
    }

    private static DomainMetadata rebuild(DomainMetadata source, List<RelationshipMetadata> edges) {
        return DomainMetadata.builder(source.entityName(), source.packageName())
                .fields(source.fields()).actions(source.actions()).relationships(edges).build();
    }

    private static DomainMetadata withFields(DomainMetadata source, List<FieldMetadata> fields) {
        return DomainMetadata.builder(source.entityName(), source.packageName())
                .fields(fields).actions(source.actions()).relationships(source.relationships()).build();
    }
}
