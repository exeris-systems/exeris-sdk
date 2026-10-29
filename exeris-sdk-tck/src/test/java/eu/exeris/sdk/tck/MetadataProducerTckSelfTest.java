package eu.exeris.sdk.tck;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.util.function.Consumer;
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
            producer.sagaVersionReachesTheProducedMetadata();
            producer.sagaCompensationReachesTheProducedJson();
            producer.actionResultTypeReachesTheProducedJson();
            producer.routeAccessReachesTheProducedJson();
            producer.queryParamsReachTheProducedJson();
            producer.navMenuReachesTheProducedJson();
            producer.tabsReachTheProducedJson();
            producer.graphPropertiesReachTheProducedJson();
            producer.uiGroupsReachTheProducedJson();
            producer.blobsReachTheProducedJson();
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

    @Test
    @DisplayName("a saga version the producer never read is caught")
    void sagaVersionCaseIsNotVacuous() {
        // What an extraction that skips the attribute writes: the builder default, well-formed.
        Producer versionBlind = new Producer(json -> json.replace(
                "\"version\":" + ReferenceBinding.ORDER_SAGA_VERSION, "\"version\":1"));
        assertThatThrownBy(versionBlind::sagaVersionReachesTheProducedMetadata)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("baseline says 1")
                .hasMessageContaining("ADR-064");
    }

    @Test
    @DisplayName("a baseline with no saga at all is caught")
    void sagaPresenceCaseIsNotVacuous() {
        Producer sagaBlind = new Producer(json -> {
            ObjectNode root = (ObjectNode) TckMappers.canonical().readTree(json);
            root.remove("sagaMetadata");
            return root.toString();
        });
        assertThatThrownBy(sagaBlind::sagaVersionReachesTheProducedMetadata)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("carries no sagaMetadata");
    }

    @Test
    @DisplayName("a declared compensation 0 or false missing from the JSON is caught")
    void sagaCompensationKeyCasesAreNotVacuous() {
        Producer zeroBlind = new Producer(json -> withoutSagaKey(json, "compensationMaxRetries"));
        assertThatThrownBy(zeroBlind::sagaCompensationReachesTheProducedJson)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("compensationMaxRetries = 0");

        Producer falseBlind = new Producer(json -> withoutSagaKey(json, "manualInterventionOnCompensationFailure"));
        assertThatThrownBy(falseBlind::sagaCompensationReachesTheProducedJson)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("manualInterventionOnCompensationFailure = false");
    }

    @Test
    @DisplayName("a failure handler written as declared instead of fully qualified is caught")
    void sagaCompensationHandlerCaseIsNotVacuous() {
        Producer writtenForm = new Producer(json -> json.replace(
                "\"" + ReferenceBinding.ORDER_SAGA_FAILURE_HANDLER + "\"", "\"Order.CompensationEscalation\""));
        assertThatThrownBy(writtenForm::sagaCompensationReachesTheProducedJson)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("fully-qualified");
    }

    @Test
    @DisplayName("a producer that writes no resultType is caught")
    void actionResultTypeKeyCaseIsNotVacuous() {
        Producer typeBlind = new Producer(json -> {
            ObjectNode root = (ObjectNode) TckMappers.canonical().readTree(json);
            root.path("actions").forEach(action -> ((ObjectNode) action).remove("resultType"));
            return root.toString();
        });
        assertThatThrownBy(typeBlind::actionResultTypeReachesTheProducedJson)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("did not extract it");
    }

    @Test
    @DisplayName("a return type written as declared instead of fully qualified is caught")
    void actionResultTypeWrittenFormIsCaught() {
        Producer writtenForm = new Producer(json -> json.replace(
                "\"" + ReferenceBinding.ORDER_RECEIPT + "\"", "\"Receipt\""));
        assertThatThrownBy(writtenForm::actionResultTypeReachesTheProducedJson)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("'receipt'");
    }

    @Test
    @DisplayName("an action missing from the produced JSON is caught")
    void actionResultTypeMissingActionIsCaught() {
        Producer dropping = new Producer(json -> json.replace("\"name\":\"lineCount\"", "\"name\":\"count\""));
        assertThatThrownBy(dropping::actionResultTypeReachesTheProducedJson)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("no such action");
    }

    @Test
    @DisplayName("a query binding, key or declared false missing from the JSON is caught")
    void queryParamCasesAreNotVacuous() {
        Producer bodyBound = new Producer(json -> json.replace("\"bindingSource\":\"QUERY\"", "\"x\":0"));
        assertThatThrownBy(bodyBound::queryParamsReachTheProducedJson)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Absent means BODY");

        Producer keyBlind = new Producer(json -> json.replace("\"queryKey\":\"q\"", "\"x\":0"));
        assertThatThrownBy(keyBlind::queryParamsReachTheProducedJson)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("query key 'q'");

        Producer falseBlind = new Producer(json -> json.replace(
                "\"name\":\"limit\",\"type\":\"int\",\"required\":false", "\"name\":\"limit\",\"type\":\"int\""));
        assertThatThrownBy(falseBlind::queryParamsReachTheProducedJson)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("required = false");

        Producer actionParamOnly = new Producer(json -> json.replace("\"name\":\"term\"", "\"name\":\"other\""));
        assertThatThrownBy(actionParamOnly::queryParamsReachTheProducedJson)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("@QueryParam alone");
    }

    @Test
    @DisplayName("a dropped navigation entry or zero order is caught")
    void navMenuCasesAreNotVacuous() {
        Producer zeroBlind = new Producer(json -> {
            ObjectNode root = (ObjectNode) TckMappers.canonical().readTree(json);
            ((ObjectNode) root.get("navMenu")).remove("order");
            return root.toString();
        });
        assertThatThrownBy(zeroBlind::navMenuReachesTheProducedJson)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("order = 0");

        Producer navBlind = new Producer(json -> {
            ObjectNode root = (ObjectNode) TckMappers.canonical().readTree(json);
            root.remove("navMenu");
            return root.toString();
        });
        assertThatThrownBy(navBlind::navMenuReachesTheProducedJson)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("carries no navMenu");
    }

    @Test
    @DisplayName("tabs missing from the JSON, or a dropped zero order, are caught")
    void tabCasesAreNotVacuous() {
        Producer tabBlind = new Producer(json -> {
            ObjectNode root = (ObjectNode) TckMappers.canonical().readTree(json);
            root.remove("uiMetadata");
            return root.toString();
        });
        assertThatThrownBy(tabBlind::tabsReachTheProducedJson)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("uiMetadata.tabs");

        Producer zeroBlind = new Producer(json -> {
            ObjectNode root = (ObjectNode) TckMappers.canonical().readTree(json);
            ((ObjectNode) root.get("uiMetadata").get("tabs").get(0)).remove("order");
            return root.toString();
        });
        assertThatThrownBy(zeroBlind::tabsReachTheProducedJson)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("@Tab(order = 0)");
    }

    private static String withoutSagaKey(String json, String key) {
        ObjectNode root = (ObjectNode) TckMappers.canonical().readTree(json);
        ((ObjectNode) root.get("sagaMetadata")).remove(key);
        return root.toString();
    }

    /** A producer binding whose output is the reference baseline put through one transformation. */
    @Test
    @DisplayName("a PUBLIC missing from the produced JSON is caught")
    void routeAccessPublicCaseIsNotVacuous() {
        Producer publicBlind = new Producer(json -> json.replace("\"routeAccess\":\"PUBLIC\"", "\"x\":0"));
        assertThatThrownBy(publicBlind::routeAccessReachesTheProducedJson)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Product/routeAccess — no PUBLIC key in the produced JSON")
                .hasMessageContaining("Order/actions/track/routeAccess — no PUBLIC key in the produced JSON");
    }

    @Test
    @DisplayName("an action permission the producer never wrote is caught")
    void routeAccessPermissionCaseIsNotVacuous() {
        Producer permissionBlind = new Producer(json -> json.replace("\"order:approve\"", ""));
        assertThatThrownBy(permissionBlind::routeAccessReachesTheProducedJson)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Order/actions/approve/permissions — declared [order:approve], got []");
    }

    @Test
    @DisplayName("graph properties missing, typed from AUTO, or without their field are caught")
    void graphPropertyCasesAreNotVacuous() {
        Producer notExtracted = new Producer(json -> treeEdited(json, root -> {
            if (root.has("graphMetadata")) {
                ((ObjectNode) root.get("graphMetadata")).remove("properties");
            }
        }));
        assertThatThrownBy(notExtracted::graphPropertiesReachTheProducedJson)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("does not extract @GraphProperty");

        Producer autoTyped = new Producer(json -> treeEdited(json, root -> {
            if (root.has("graphMetadata")) {
                ((ObjectNode) root.get("graphMetadata").get("properties").get(0)).put("type", "STRING");
            }
        }));
        assertThatThrownBy(autoTyped::graphPropertiesReachTheProducedJson)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("AUTO is written as no type");

        Producer fieldBlind = new Producer(json -> json.replace(",\"field\":\"sku\"", ""));
        assertThatThrownBy(fieldBlind::graphPropertiesReachTheProducedJson)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("'skuCode'");
    }

    @Test
    @DisplayName("groups missing, or a collapsible default not written out, are caught")
    void uiGroupCasesAreNotVacuous() {
        Producer groupBlind = new Producer(json -> treeEdited(json, root -> {
            if (root.path("uiMetadata").path("groups").size() == 2) {
                root.remove("uiMetadata");
            }
        }));
        assertThatThrownBy(groupBlind::uiGroupsReachTheProducedJson)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("uiMetadata.groups");

        Producer recordDefault = new Producer(json -> treeEdited(json, root -> {
            if (root.path("uiMetadata").path("groups").size() == 2) {
                ((ObjectNode) root.get("uiMetadata").get("groups").get(0)).put("collapsible", false);
            }
        }));
        assertThatThrownBy(recordDefault::uiGroupsReachTheProducedJson)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("written out");

        Producer falseBlind = new Producer(json -> treeEdited(json, root -> {
            if (root.path("uiMetadata").path("groups").size() == 2) {
                ((ObjectNode) root.get("uiMetadata").get("groups").get(1)).remove("collapsible");
            }
        }));
        assertThatThrownBy(falseBlind::uiGroupsReachTheProducedJson)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("collapsible = false");
    }

    @Test
    @DisplayName("a blob dropped from a field without @Field, or reordered, is caught")
    void blobCasesAreNotVacuous() {
        Producer fieldOnly = new Producer(json -> json.replace(",\"blob\":{\"container\":\"contracts\",\"contentTypes\":[]}", ""));
        assertThatThrownBy(fieldOnly::blobsReachTheProducedJson)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("no @Field");

        Producer reordered = new Producer(json -> json.replace("[\"image/png\",\"image/jpeg\"]", "[\"image/jpeg\",\"image/png\"]"));
        assertThatThrownBy(reordered::blobsReachTheProducedJson)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Customer.avatar");
    }

    /** The JSON with its tree edited; the edit decides which entity it applies to. */
    private static String treeEdited(String json, Consumer<ObjectNode> edit) {
        ObjectNode root = (ObjectNode) TckMappers.canonical().readTree(json);
        edit.accept(root);
        return root.toString();
    }

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
