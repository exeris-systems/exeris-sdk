package eu.exeris.sdk.tck;

import eu.exeris.sdk.sourcemodel.ast.ActionMetadata;
import eu.exeris.sdk.sourcemodel.ast.ActionParamMetadata;
import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.FieldMetadata;
import eu.exeris.sdk.sourcemodel.ast.NavMenuMetadata;
import eu.exeris.sdk.sourcemodel.ast.ParamSource;
import eu.exeris.sdk.sourcemodel.ast.RelationshipMetadata;
import eu.exeris.sdk.sourcemodel.ast.SagaMetadata;
import eu.exeris.sdk.sourcemodel.ast.UIMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

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
            reader.actionResultTypeIsTheDeclaredOne();
            reader.sagaVersionIsTheDeclaredOne();
            reader.sagaCompensationIsTheDeclaredOne();
            reader.routeAccessIsTheDeclaredOne();
            reader.queryParamsAreTheDeclaredOnes();
            reader.navMenuIsTheDeclaredOne();
            reader.tabsAreTheDeclaredOnes();
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
    @DisplayName("a return type carried as written instead of fully qualified is caught")
    void actionResultTypeWrittenFormIsCaught() {
        Reader writtenForm = new Reader(m -> withActions(m, "receipts", "List<Receipt>"));
        assertThatThrownBy(writtenForm::actionResultTypeIsTheDeclaredOne)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("'receipts'")
                .hasMessageContaining("fully qualified");
    }

    @Test
    @DisplayName("a void action read with no return type is caught")
    void actionResultTypeAbsentVoidIsCaught() {
        Reader voidBlind = new Reader(m -> withActions(m, "submit", null));
        assertThatThrownBy(voidBlind::actionResultTypeIsTheDeclaredOne)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("'submit'");
    }

    @Test
    @DisplayName("an action the reader drops is reported rather than passing the result-type case")
    void actionResultTypeMissingActionIsCaught() {
        Reader dropping = new Reader(m -> DomainMetadata.builder(m.entityName(), m.packageName())
                .fields(m.fields())
                .actions(m.actions().stream().filter(a -> !"lineCount".equals(a.name())).toList())
                .relationships(m.relationships())
                .build());
        assertThatThrownBy(dropping::actionResultTypeIsTheDeclaredOne)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("lineCount");
    }

    @Test
    @DisplayName("an unbuilt result-type facet skips rather than failing")
    void anUnbuiltResultTypeFacetSkips() {
        Reader notBuiltYet = new Reader(m -> withActions(m, "receipt", null)) {
            @Override
            protected Set<Facet> unsupportedFacets() {
                return Set.of(Facet.ACTION_RESULT_TYPE);
            }
        };
        assertThatThrownBy(notBuiltYet::actionResultTypeIsTheDeclaredOne)
                .isInstanceOf(TestAbortedException.class);
        assertThatCode(notBuiltYet::actionIsReadUnderTheDeclaredName).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a saga version left at the default an unread attribute leaves is caught")
    void sagaVersionCaseIsNotVacuous() {
        // Every other saga attribute read and version never looked at, so the builder default
        // stands in for what the source declared.
        Reader versionBlind = new Reader(m -> m.sagaMetadata() == null
                ? m
                : withSaga(m, SagaMetadata.builder(m.sagaMetadata().name())
                        .steps(m.sagaMetadata().steps())
                        .build()));
        assertThatThrownBy(versionBlind::sagaVersionIsTheDeclaredOne)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("read as version 1")
                .hasMessageContaining("ADR-064");
    }

    @Test
    @DisplayName("a reader that drops the saga altogether is caught")
    void sagaPresenceCaseIsNotVacuous() {
        Reader sagaBlind = new Reader(m -> withSaga(m, null));
        assertThatThrownBy(sagaBlind::sagaVersionIsTheDeclaredOne)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("no saga metadata");
    }

    @Test
    @DisplayName("a declared compensation 0 or false dropped as if undeclared is caught")
    void sagaCompensationZeroAndFalseCasesAreNotVacuous() {
        Reader zeroBlind = new Reader(m -> withSaga(m, ReferenceBinding.orderSaga()
                .compensationMaxRetries(null).build()));
        assertThatThrownBy(zeroBlind::sagaCompensationIsTheDeclaredOne)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("compensationMaxRetries = 0");

        Reader falseBlind = new Reader(m -> withSaga(m, ReferenceBinding.orderSaga()
                .continueCompensationOnFailure(null).build()));
        assertThatThrownBy(falseBlind::sagaCompensationIsTheDeclaredOne)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("continueCompensationOnFailure = false");

        Reader interventionBlind = new Reader(m -> withSaga(m, ReferenceBinding.orderSaga()
                .manualInterventionOnCompensationFailure(null).build()));
        assertThatThrownBy(interventionBlind::sagaCompensationIsTheDeclaredOne)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("manualInterventionOnCompensationFailure = false");
    }

    @Test
    @DisplayName("a failure handler carried as written instead of fully qualified is caught")
    void sagaCompensationHandlerCaseIsNotVacuous() {
        Reader writtenForm = new Reader(m -> withSaga(m, ReferenceBinding.orderSaga()
                .compensationFailureHandler("Order.CompensationEscalation").build()));
        assertThatThrownBy(writtenForm::sagaCompensationIsTheDeclaredOne)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("fully-qualified");
    }

    @Test
    @DisplayName("an unbuilt compensation facet skips rather than failing")
    void anUnbuiltCompensationFacetSkips() {
        Reader notBuiltYet = new Reader(m -> withSaga(m, ReferenceBinding.orderSaga()
                .compensationMaxRetries(null).build())) {
            @Override
            protected Set<Facet> unsupportedFacets() {
                return Set.of(Facet.SAGA_COMPENSATION);
            }
        };
        assertThatThrownBy(notBuiltYet::sagaCompensationIsTheDeclaredOne)
                .isInstanceOf(TestAbortedException.class);
        assertThatCode(notBuiltYet::sagaVersionIsTheDeclaredOne).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a query parameter read as a body parameter, under its name, or not at all is caught")
    void queryParamCasesAreNotVacuous() {
        Reader bodyBound = new Reader(m -> withSearch(m, p -> ActionParamMetadata.builder(p.name(), p.type())
                .required(p.required()).build()));
        assertThatThrownBy(bodyBound::queryParamsAreTheDeclaredOnes)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("binding source BODY");

        Reader nameKeyed = new Reader(m -> withSearch(m, p -> ActionParamMetadata.builder(p.name(), p.type())
                .required(p.required()).bindingSource(ParamSource.QUERY).build()));
        assertThatThrownBy(nameKeyed::queryParamsAreTheDeclaredOnes)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("query key 'q'");

        Reader requiredBlind = new Reader(m -> withSearch(m, p -> ActionParamMetadata.builder(p.name(), p.type())
                .required(true).bindingSource(p.bindingSource()).queryKey(p.queryKey()).build()));
        assertThatThrownBy(requiredBlind::queryParamsAreTheDeclaredOnes)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("required = false");

        Reader actionParamOnly = new Reader(m -> withActions(m, m.actions().stream()
                .map(a -> "search".equals(a.name())
                        ? ActionMetadata.builder("search").httpMethod("GET").build()
                        : a)
                .toList()));
        assertThatThrownBy(actionParamOnly::queryParamsAreTheDeclaredOnes)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("@QueryParam alone");
    }

    @Test
    @DisplayName("a dropped zero order, or a missing navigation entry, is caught")
    void navMenuCasesAreNotVacuous() {
        Reader zeroBlind = new Reader(m -> withNavMenu(m, m.navMenu() == null
                ? null : ReferenceBinding.orderNavMenu().order(null).build()));
        assertThatThrownBy(zeroBlind::navMenuIsTheDeclaredOne)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("order = 0");

        Reader navBlind = new Reader(m -> withNavMenu(m, null));
        assertThatThrownBy(navBlind::navMenuIsTheDeclaredOne)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("returned no navMenu");
    }

    @Test
    @DisplayName("tabs hung off @UI only, or with a dropped zero order, are caught")
    void tabCasesAreNotVacuous() {
        Reader uiOnly = new Reader(m -> withUi(m, null));
        assertThatThrownBy(uiOnly::tabsAreTheDeclaredOnes)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("no uiMetadata");

        Reader zeroBlind = new Reader(m -> withUi(m, m.uiMetadata() == null ? null : UIMetadata.builder()
                .tabs(List.of(
                        new UIMetadata.UITabMetadata("summary", "Summary", null, null, false, null, null,
                                false, null, null, null, List.of("reference", "totalCents")),
                        ReferenceBinding.orderTabs().get(1)))
                .build()));
        assertThatThrownBy(zeroBlind::tabsAreTheDeclaredOnes)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("@Tab(order = 0)");

        Reader firstMemberOnly = new Reader(m -> withUi(m, m.uiMetadata() == null ? null : UIMetadata.builder()
                .tabs(List.of(
                        new UIMetadata.UITabMetadata("summary", "Summary", 0, null, false, null, null,
                                false, null, null, null, List.of("reference")),
                        ReferenceBinding.orderTabs().get(1)))
                .build()));
        assertThatThrownBy(firstMemberOnly::tabsAreTheDeclaredOnes)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Order.totalCents");
    }

    @Test
    @DisplayName("an unbuilt navigation, tab or query-parameter facet skips rather than failing")
    void unbuiltPresentationFacetsSkip() {
        Reader notBuiltYet = new Reader(m -> withNavMenu(withUi(m, null), null)) {
            @Override
            protected Set<Facet> unsupportedFacets() {
                return Set.of(Facet.NAV_MENU, Facet.TABS, Facet.QUERY_PARAMS);
            }
        };
        assertThatThrownBy(notBuiltYet::navMenuIsTheDeclaredOne).isInstanceOf(TestAbortedException.class);
        assertThatThrownBy(notBuiltYet::tabsAreTheDeclaredOnes).isInstanceOf(TestAbortedException.class);
        assertThatThrownBy(notBuiltYet::queryParamsAreTheDeclaredOnes).isInstanceOf(TestAbortedException.class);
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

    @Test
    @DisplayName("a dropped PUBLIC, a resolved inheritance and a reordered list are each caught")
    void routeAccessCasesAreNotVacuous() {
        assertThatThrownBy(new Reader(ReferenceBinding::publicDropped)::routeAccessIsTheDeclaredOne)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Product/routeAccess — declared PUBLIC, got <absent>")
                .hasMessageContaining("Order/actions/track/routeAccess — declared PUBLIC, got <absent>");
        assertThatThrownBy(new Reader(ReferenceBinding::inheritanceResolved)::routeAccessIsTheDeclaredOne)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Order/actions/submit/routeAccess — declared <absent>, got AUTHENTICATED");
        assertThatThrownBy(new Reader(ReferenceBinding::permissionsReversed)::routeAccessIsTheDeclaredOne)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Order/permissions");
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

    private static DomainMetadata.Builder copyOf(DomainMetadata source) {
        return DomainMetadata.builder(source.entityName(), source.packageName())
                .fields(source.fields())
                .actions(source.actions())
                .relationships(source.relationships())
                .sagaMetadata(source.sagaMetadata())
                .permissions(source.permissions())
                .routeAccess(source.routeAccess())
                .navMenu(source.navMenu())
                .uiMetadata(source.uiMetadata());
    }

    private static DomainMetadata withActions(DomainMetadata source, List<ActionMetadata> actions) {
        return copyOf(source).actions(actions).build();
    }

    private static DomainMetadata withSearch(DomainMetadata source,
                                             UnaryOperator<ActionParamMetadata> perParam) {
        return withActions(source, source.actions().stream()
                .map(a -> {
                    if (!"search".equals(a.name())) {
                        return a;
                    }
                    ActionMetadata.Builder search = ActionMetadata.builder(a.name()).httpMethod(a.httpMethod());
                    a.params().forEach(p -> search.addParam(perParam.apply(p)));
                    return search.build();
                })
                .toList());
    }

    private static DomainMetadata withNavMenu(DomainMetadata source, NavMenuMetadata navMenu) {
        return copyOf(source).navMenu(navMenu).build();
    }

    private static DomainMetadata withUi(DomainMetadata source, UIMetadata ui) {
        return copyOf(source).uiMetadata(ui).build();
    }

    /** The reference metadata with one action's {@code resultType} replaced; other entities unchanged. */
    private static DomainMetadata withActions(DomainMetadata source, String actionName, String resultType) {
        if (source.actions() == null || source.actions().isEmpty()) {
            return source;
        }
        return withActions(source, source.actions().stream()
                .map(a -> actionName.equals(a.name())
                        ? ReferenceBinding.action(a.name(), a.httpMethod(), resultType)
                        : a)
                .toList());
    }

    private static DomainMetadata withSaga(DomainMetadata source, SagaMetadata saga) {
        return DomainMetadata.builder(source.entityName(), source.packageName())
                .fields(source.fields())
                .actions(source.actions())
                .relationships(source.relationships())
                .sagaMetadata(saga)
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
