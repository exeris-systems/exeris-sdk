package eu.exeris.sdk.tck;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves {@link AbstractMetadataProducerTck}'s cases are not vacuous.
 *
 * <p>The defects are injected on the produced JSON text rather than on the metadata, because that
 * is where a producer's mistakes actually live: the baseline is a file, and a field lost during
 * serialization is indistinguishable from one never extracted — until someone reads it back.
 */
@DisplayName("the producer TCK rejects a non-conforming producer")
class MetadataProducerTckSelfTest {

    @Test
    @DisplayName("a conforming producer passes every case")
    void conformingProducerPassesEveryCase() {
        Producer producer = new Producer(UnaryOperator.identity());
        assertThatCode(() -> {
            producer.outputIsReadableByACorrectlyConfiguredMapper();
            producer.schemaVersionIsStamped();
            producer.baselineTrustFieldsAreSiblingsNotAWrapper();
            producer.validationConstraintsReachTheProducedField();
            producer.aZeroValuedBoundSurvivesSerialization();
            producer.mandatoryFacetsAreNotDeclaredUnsupported();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an unstamped baseline is caught")
    void schemaVersionCaseIsNotVacuous() {
        Producer unstamped = new Producer(json -> json.replaceAll("\"schemaVersion\":\"[^\"]*\",?", ""));
        assertThatThrownBy(unstamped::schemaVersionIsStamped)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("NO_BASELINE");
    }

    @Test
    @DisplayName("a baseline stamped with the wrong schema version is caught")
    void schemaVersionValueCaseIsNotVacuous() {
        Producer wrong = new Producer(json -> json.replaceAll(
                "\"schemaVersion\":\"[^\"]*\"", "\"schemaVersion\":\"0.0.1\""));
        assertThatThrownBy(wrong::schemaVersionIsStamped)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("decoupled from the Maven");
    }

    @Test
    @DisplayName("trust fields wrapped around the metadata rather than beside it are caught")
    void wrapperShapeCaseIsNotVacuous() {
        Producer wrapping = new Producer(json ->
                "{\"schemaVersion\":\"" + eu.exeris.sdk.sourcemodel.mutation.SchemaVersion.CURRENT
                        + "\",\"metadata\":" + json + "}");
        assertThatThrownBy(wrapping::baselineTrustFieldsAreSiblingsNotAWrapper)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("wrapped around the metadata");
    }

    @Test
    @DisplayName("a blank digest, which absent and empty do not mean the same thing as, is caught")
    void blankDigestCaseIsNotVacuous() {
        Producer blank = new Producer(json -> json.replaceAll(
                "\"sourceDigest\":\"[^\"]*\"", "\"sourceDigest\":\"\""));
        assertThatThrownBy(blank::baselineTrustFieldsAreSiblingsNotAWrapper)
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("a zero bound dropped on serialization is caught")
    void zeroBoundCaseIsNotVacuous() {
        Producer dropping = new Producer(json -> json.replaceAll("\"min\":0,?", ""));
        assertThatThrownBy(dropping::aZeroValuedBoundSurvivesSerialization)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("boxed-zero drop");
    }

    @Test
    @DisplayName("a constraint that never reaches the produced field is caught")
    void validationCaseIsNotVacuous() {
        Producer dropping = new Producer(json -> json.replaceAll("\"minLength\":3,?", ""));
        assertThatThrownBy(dropping::validationConstraintsReachTheProducedField)
                .isInstanceOf(AssertionError.class);
    }

    /** A producer binding whose output is the reference baseline put through one transformation. */
    static class Producer extends AbstractMetadataProducerTck {

        private final UnaryOperator<String> defect;

        Producer(UnaryOperator<String> defect) {
            this.defect = defect;
        }

        @Override
        protected String produce(String entitySource) {
            return defect.apply(ReferenceBinding.produceFrom(entitySource));
        }
    }
}
