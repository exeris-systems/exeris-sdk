package eu.exeris.sdk.tck;

import eu.exeris.sdk.sourcemodel.ast.ActionMetadata;
import eu.exeris.sdk.sourcemodel.ast.ActionParamMetadata;
import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.FieldMetadata;
import eu.exeris.sdk.sourcemodel.ast.NavMenuMetadata;
import eu.exeris.sdk.sourcemodel.ast.RelationshipMetadata;
import eu.exeris.sdk.sourcemodel.ast.SagaMetadata;
import eu.exeris.sdk.sourcemodel.ast.UIMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

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
            parity.actionResultTypesAgree();
            parity.sagaIdentityAgrees();
            parity.sagaCompensationAgrees();
            parity.sagaTriggerAgrees();
            parity.routeAccessAgrees();
            parity.queryParamsAgree();
            parity.navMenuAgrees();
            parity.tabsAgree();
            parity.graphPropertiesAgree();
            parity.uiGroupsAgree();
            parity.blobsAgree();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an action label one side does not write, or a parameter type in another form, is caught")
    void actionLabelAndParameterTypeDivergenceIsCaught() {
        // The producer as it stands: no @Action.label written.
        Parity labelBlind = new Parity(m -> copyOf(m).actions(m.actions().stream()
                .map(a -> ReferenceBinding.action(a.name(), null, a.httpMethod(), a.resultType()))
                .toList()).build(), UnaryOperator.identity());
        assertThatThrownBy(labelBlind::actionsAgree)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("actions/submit/displayName");

        ActionMetadata search = ReferenceBinding.orderSearch();
        ActionMetadata.Builder asWritten = ActionMetadata.builder(search.name()).displayName(search.displayName())
                .httpMethod(search.httpMethod()).methodName(search.methodName()).resultType(search.resultType());
        search.params().forEach(p -> asWritten.addParam(ActionParamMetadata
                .builder(p.name(), p.type().replace("java.lang.", ""))
                .required(p.required()).bindingSource(p.bindingSource()).queryKey(p.queryKey()).build()));
        Parity typeSplit = new Parity(UnaryOperator.identity(), m -> withSearch(m, asWritten.build()));
        assertThatThrownBy(typeSplit::actionsAgree)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("actions/search/params/term/type");
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
    @DisplayName("a display field one side leaves at the record default is caught")
    void displayFieldDivergenceIsCaught() {
        Parity parity = new Parity(
                UnaryOperator.identity(),
                m -> m.relationships() == null || m.relationships().isEmpty()
                        ? m
                        : rebuild(m, List.of(
                                RelationshipMetadata.oneToMany("orders", TckCorpus.ORDER, "customer"))));
        assertThatThrownBy(parity::relationshipsAgree)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("relationships/orders/displayField");
    }

    @Test
    @DisplayName("a cascade one side leaves unread is caught")
    void cascadeDivergenceIsCaught() {
        Parity parity = new Parity(
                UnaryOperator.identity(),
                m -> m.relationships() == null || m.relationships().isEmpty()
                        ? m
                        : rebuild(m, m.relationships().stream()
                                .map(r -> RelationshipMetadata.builder(r.name(), r.targetEntity())
                                        .type(r.type()).mappedBy(r.mappedBy()).displayField(r.displayField())
                                        .build())
                                .toList()));
        assertThatThrownBy(parity::relationshipsAgree)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("relationships/orders/cascade");
    }

    @Test
    @DisplayName("a target one side takes from the field type is caught")
    void targetEntityDivergenceIsCaught() {
        Parity parity = new Parity(
                UnaryOperator.identity(),
                m -> m.relationships() == null || m.relationships().isEmpty()
                        ? m
                        : rebuild(m, m.relationships().stream()
                                .map(r -> "lastOrderId".equals(r.name())
                                        ? RelationshipMetadata.builder(r.name(), "UUID")
                                                .displayField(r.displayField()).build()
                                        : r)
                                .toList()));
        assertThatThrownBy(parity::relationshipsAgree)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("relationships/lastOrderId/targetEntity");
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
    @DisplayName("a return type one side carries as written is caught")
    void actionResultTypeDivergenceIsCaught() {
        Parity parity = new Parity(
                UnaryOperator.identity(),
                m -> withActionResultType(m, "receipts", "List<Receipt>"));
        assertThatThrownBy(parity::actionResultTypesAgree)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("actions/receipts/resultType — producer: 'java.util.List<"
                        + ReferenceBinding.ORDER_RECEIPT + ">', reader: 'List<Receipt>'");
    }

    @Test
    @DisplayName("a void action only one side writes as \"void\" is caught")
    void actionResultTypeVoidDivergenceIsCaught() {
        Parity parity = new Parity(m -> withActionResultType(m, "submit", null), UnaryOperator.identity());
        assertThatThrownBy(parity::actionResultTypesAgree)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("actions/submit/resultType — producer: <absent>, reader: 'void'");
    }

    @Test
    @DisplayName("a producer that does not extract the return type yet skips the case")
    void anUnbuiltResultTypeFacetSkips() {
        Parity notBuiltYet = new Parity(m -> withActionResultType(m, "submit", null), UnaryOperator.identity()) {
            @Override
            protected Set<Facet> unsupportedFacets() {
                return Set.of(Facet.ACTION_RESULT_TYPE);
            }
        };
        assertThatThrownBy(notBuiltYet::actionResultTypesAgree)
                .isInstanceOf(TestAbortedException.class);
        assertThatCode(notBuiltYet::actionsAgree).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a saga version one side read and the other did not is caught")
    void sagaVersionDivergenceIsCaught() {
        // The divergence the case exists for: the producer carries the declared version, the
        // reader never reads the attribute and reports the builder default. Both documents are
        // well-formed.
        Parity parity = new Parity(
                UnaryOperator.identity(),
                m -> m.sagaMetadata() == null
                        ? m
                        : withSaga(m, SagaMetadata.builder(m.sagaMetadata().name())
                                .steps(m.sagaMetadata().steps())
                                .build()));
        assertThatThrownBy(parity::sagaIdentityAgrees)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("saga/version — producer: '3', reader: '1'");
    }

    @Test
    @DisplayName("a saga name one side reads differently is caught")
    void sagaNameDivergenceIsCaught() {
        Parity parity = new Parity(
                m -> m.sagaMetadata() == null
                        ? m
                        : withSaga(m, SagaMetadata.builder(m.entityName())
                                .version(m.sagaMetadata().version())
                                .steps(m.sagaMetadata().steps())
                                .build()),
                UnaryOperator.identity());
        assertThatThrownBy(parity::sagaIdentityAgrees)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("saga/name");
    }

    @Test
    @DisplayName("a saga only one side emits is caught")
    void oneSidedSagaIsCaught() {
        Parity readerBlind = new Parity(UnaryOperator.identity(), m -> withSaga(m, null));
        assertThatThrownBy(readerBlind::sagaIdentityAgrees)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("producer emitted one, reader did not");

        Parity producerBlind = new Parity(m -> withSaga(m, null), UnaryOperator.identity());
        assertThatThrownBy(producerBlind::sagaIdentityAgrees)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("reader emitted one, producer did not");
    }

    @Test
    @DisplayName("a declared false one side drops is caught")
    void sagaCompensationFalseDivergenceIsCaught() {
        Parity parity = new Parity(
                UnaryOperator.identity(),
                m -> m.sagaMetadata() == null
                        ? m
                        : withSaga(m, ReferenceBinding.orderSaga().continueCompensationOnFailure(null).build()));
        assertThatThrownBy(parity::sagaCompensationAgrees)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("saga/continueCompensationOnFailure — producer: 'false', reader: <absent>");
    }

    @Test
    @DisplayName("a failure handler one side carries as written is caught")
    void sagaCompensationHandlerDivergenceIsCaught() {
        Parity parity = new Parity(
                UnaryOperator.identity(),
                m -> m.sagaMetadata() == null
                        ? m
                        : withSaga(m, ReferenceBinding.orderSaga()
                                .compensationFailureHandler("Order.CompensationEscalation").build()));
        assertThatThrownBy(parity::sagaCompensationAgrees)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("saga/compensationFailureHandler");
    }

    @Test
    @DisplayName("a PUBLIC one side drops is caught, on the entity and on an action")
    void droppedPublicIsCaught() {
        Parity parity = new Parity(UnaryOperator.identity(), ReferenceBinding::publicDropped);
        assertThatThrownBy(parity::routeAccessAgrees)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Product: routeAccess — producer: 'PUBLIC', reader: <absent>")
                .hasMessageContaining("Order: actions/track/routeAccess — producer: 'PUBLIC', reader: <absent>");
    }

    @Test
    @DisplayName("an entity declaration one side copies onto an inheriting action is caught")
    void resolvedInheritanceIsCaught() {
        Parity parity = new Parity(ReferenceBinding::inheritanceResolved, UnaryOperator.identity());
        assertThatThrownBy(parity::routeAccessAgrees)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Order: actions/submit/routeAccess — producer: 'AUTHENTICATED', reader: <absent>")
                .hasMessageContaining("Order: actions/submit/permissions");
    }

    @Test
    @DisplayName("permissions one side reorders are caught")
    void reorderedPermissionsAreCaught() {
        Parity parity = new Parity(UnaryOperator.identity(), ReferenceBinding::permissionsReversed);
        assertThatThrownBy(parity::routeAccessAgrees)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Order: permissions — producer: '[order:read, order:write]'");
    }

    @Test
    @DisplayName("a trigger one side does not read is caught")
    void sagaTriggerAbsenceIsCaught() {
        Parity parity = new Parity(
                UnaryOperator.identity(),
                m -> m.sagaMetadata() == null
                        ? m
                        : withSaga(m, ReferenceBinding.orderSaga().trigger(null).build()));
        assertThatThrownBy(parity::sagaTriggerAgrees)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("saga/trigger");
    }

    @Test
    @DisplayName("a trigger source one side carries as written is caught")
    void sagaTriggerSourceDivergenceIsCaught() {
        Parity parity = new Parity(
                UnaryOperator.identity(),
                m -> m.sagaMetadata() == null
                        ? m
                        : withSaga(m, ReferenceBinding.orderSaga().trigger(new SagaMetadata.SagaTrigger(
                                SagaMetadata.TriggerType.EVENT, "Order.Placed", "sales.orders", null, null))
                                .build()));
        assertThatThrownBy(parity::sagaTriggerAgrees)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("saga/trigger/source");
    }

    @Test
    @DisplayName("a trigger kind one side reads as the default is caught")
    void sagaTriggerTypeDivergenceIsCaught() {
        Parity parity = new Parity(
                UnaryOperator.identity(),
                m -> m.sagaMetadata() == null
                        ? m
                        : withSaga(m, ReferenceBinding.orderSaga().trigger(new SagaMetadata.SagaTrigger(
                                SagaMetadata.TriggerType.MANUAL, ReferenceBinding.ORDER_SAGA_TRIGGER_SOURCE,
                                "sales.orders", null, null)).build()));
        assertThatThrownBy(parity::sagaTriggerAgrees)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("saga/trigger/type — producer: 'EVENT', reader: 'MANUAL'");
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

    @Test
    @DisplayName("a query parameter one side reads as a body parameter, or does not admit, is caught")
    void queryParamDivergenceIsCaught() {
        Parity bodyBound = new Parity(
                m -> withSearch(m, ActionMetadata.builder("search").httpMethod("GET")
                        .addParam(ActionParamMetadata.builder("term", "String").required(true).build())
                        .addParam(ActionParamMetadata.builder("limit", "int").required(false).build())
                        .build()),
                UnaryOperator.identity());
        assertThatThrownBy(bodyBound::queryParamsAgree)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("actions/search/params/term/bindingSource — producer: 'BODY', reader: 'QUERY'")
                .hasMessageContaining("actions/search/params/term/queryKey — producer: 'term', reader: 'q'");

        Parity actionParamOnly = new Parity(
                m -> withSearch(m, ActionMetadata.builder("search").httpMethod("GET").build()),
                UnaryOperator.identity());
        assertThatThrownBy(actionParamOnly::queryParamsAgree)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("reader emitted 'term', producer did not");
    }

    @Test
    @DisplayName("a navigation entry the sides disagree on is caught")
    void navMenuDivergenceIsCaught() {
        Parity zeroBlind = new Parity(
                m -> m.navMenu() == null ? m : withNavAndUi(m, ReferenceBinding.orderNavMenu().order(null).build(),
                        m.uiMetadata()),
                UnaryOperator.identity());
        assertThatThrownBy(zeroBlind::navMenuAgrees)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("navMenu — producer:");

        Parity readerBlind = new Parity(UnaryOperator.identity(), m -> withNavAndUi(m, null, m.uiMetadata()));
        assertThatThrownBy(readerBlind::navMenuAgrees)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("reader: <absent>");
    }

    @Test
    @DisplayName("tabs the sides disagree on, or order differently, are caught")
    void tabDivergenceIsCaught() {
        Parity uiOnly = new Parity(UnaryOperator.identity(), m -> withNavAndUi(m, m.navMenu(), null));
        assertThatThrownBy(uiOnly::tabsAgree)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("tabs — producer emitted 'summary', reader did not");

        List<UIMetadata.UITabMetadata> reference = ReferenceBinding.orderTabs();
        Parity reordered = new Parity(
                UnaryOperator.identity(),
                m -> m.uiMetadata() == null ? m : withNavAndUi(m, m.navMenu(),
                        UIMetadata.builder().tabs(List.of(reference.get(1), reference.get(0))).build()));
        assertThatThrownBy(reordered::tabsAgree)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("tabs (order)");

        Parity memberBlind = new Parity(
                UnaryOperator.identity(),
                m -> m.uiMetadata() == null ? m : withNavAndUi(m, m.navMenu(), UIMetadata.builder()
                        .tabs(List.of(
                                new UIMetadata.UITabMetadata("summary", "Summary", 0, null, false, null, null,
                                        false, null, null, null, List.of("reference")),
                                reference.get(1)))
                        .build()));
        assertThatThrownBy(memberBlind::tabsAgree)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("tabs/summary");
    }

    @Test
    @DisplayName("graph properties one side does not extract, or carries without the field, are caught")
    void graphPropertyDivergenceIsCaught() {
        Parity notExtracted = new Parity(m -> m.graphMetadata() == null ? m : ReferenceBinding.edited(m,
                node -> ((ObjectNode) node.get("graphMetadata")).remove("properties")), UnaryOperator.identity());
        assertThatThrownBy(notExtracted::graphPropertiesAgree)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("graphMetadata/properties — producer: <absent>");

        Parity fieldBlind = new Parity(UnaryOperator.identity(), m -> m.graphMetadata() == null ? m
                : ReferenceBinding.edited(m, node ->
                        ((ObjectNode) node.get("graphMetadata").get("properties").get(1)).remove("field")));
        assertThatThrownBy(fieldBlind::graphPropertiesAgree)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("graphMetadata/properties");
    }

    @Test
    @DisplayName("groups one side does not carry, or orders differently, are caught")
    void uiGroupDivergenceIsCaught() {
        Parity uiOnly = new Parity(m -> m.uiMetadata() == null || m.uiMetadata().groups().isEmpty()
                ? m : withNavAndUi(m, m.navMenu(), null), UnaryOperator.identity());
        assertThatThrownBy(uiOnly::uiGroupsAgree)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("groups — reader emitted 'identity', producer did not");

        List<UIMetadata.UIGroupMetadata> reference = ReferenceBinding.customerGroups();
        Parity sorted = new Parity(UnaryOperator.identity(), m -> m.uiMetadata() == null
                || m.uiMetadata().groups().isEmpty() ? m : withNavAndUi(m, m.navMenu(),
                UIMetadata.builder().groups(List.of(reference.get(1), reference.get(0))).build()));
        assertThatThrownBy(sorted::uiGroupsAgree)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("groups (order)");
    }

    @Test
    @DisplayName("a blob one side drops is caught")
    void blobDivergenceIsCaught() {
        Parity fieldOnly = new Parity(UnaryOperator.identity(), m -> ReferenceBinding.edited(m, node -> {
            for (JsonNode field : node.path("fields")) {
                if ("contract".equals(field.path("name").asString(""))) {
                    ((ObjectNode) field).remove("blob");
                }
            }
        }));
        assertThatThrownBy(fieldOnly::blobsAgree)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("fields/contract/blob — producer:");
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

    private static DomainMetadata.Builder copyOf(DomainMetadata source) {
        return DomainMetadata.builder(source.entityName(), source.packageName())
                .fields(source.fields()).actions(source.actions()).relationships(source.relationships())
                .sagaMetadata(source.sagaMetadata()).navMenu(source.navMenu()).uiMetadata(source.uiMetadata())
                .graphMetadata(source.graphMetadata());
    }

    private static DomainMetadata withSearch(DomainMetadata source, ActionMetadata search) {
        return copyOf(source).actions(source.actions().stream()
                .map(a -> "search".equals(a.name()) ? search : a)
                .toList()).build();
    }

    private static DomainMetadata withNavAndUi(DomainMetadata source, NavMenuMetadata navMenu, UIMetadata ui) {
        return copyOf(source).navMenu(navMenu).uiMetadata(ui).build();
    }

    private static DomainMetadata rebuild(DomainMetadata source, List<RelationshipMetadata> edges) {
        return DomainMetadata.builder(source.entityName(), source.packageName())
                .fields(source.fields()).actions(source.actions()).relationships(edges).build();
    }

    private static DomainMetadata withActionResultType(DomainMetadata source, String actionName, String resultType) {
        return DomainMetadata.builder(source.entityName(), source.packageName())
                .fields(source.fields())
                .actions(source.actions().stream()
                        .map(a -> actionName.equals(a.name())
                                ? ReferenceBinding.action(a.name(), a.displayName(), a.httpMethod(), resultType)
                                : a)
                        .toList())
                .relationships(source.relationships())
                .sagaMetadata(source.sagaMetadata())
                .build();
    }

    private static DomainMetadata withSaga(DomainMetadata source, SagaMetadata saga) {
        return DomainMetadata.builder(source.entityName(), source.packageName())
                .fields(source.fields()).actions(source.actions()).relationships(source.relationships())
                .sagaMetadata(saga).build();
    }

    private static DomainMetadata withFields(DomainMetadata source, List<FieldMetadata> fields) {
        return DomainMetadata.builder(source.entityName(), source.packageName())
                .fields(fields).actions(source.actions()).relationships(source.relationships()).build();
    }
}
