package eu.exeris.sdk.tck;

import eu.exeris.sdk.sourcemodel.ast.ActionMetadata;
import eu.exeris.sdk.sourcemodel.ast.ActionParamMetadata;
import eu.exeris.sdk.sourcemodel.ast.BlobMetadata;
import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.FieldMetadata;
import eu.exeris.sdk.sourcemodel.ast.GraphMetadata;
import eu.exeris.sdk.sourcemodel.ast.GraphPropertyMetadata;
import eu.exeris.sdk.sourcemodel.ast.NavMenuMetadata;
import eu.exeris.sdk.sourcemodel.ast.ParamSource;
import eu.exeris.sdk.sourcemodel.ast.RelationshipMetadata;
import eu.exeris.sdk.sourcemodel.ast.SagaMetadata;
import eu.exeris.sdk.sourcemodel.ast.UIMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract every reader of {@code @ExerisDomain} source must satisfy.
 *
 * <p>Extend it, implement {@link #read(String)} against your reader, and the suite runs. A binder
 * that reads from compiled elements rather than source text (an annotation processor, say) can
 * implement the method by driving javac over the given text — the kit never assumes how.
 *
 * {@snippet lang="java" :
 * class MyReaderTckTest extends AbstractMetadataReaderTck {
 *     protected DomainMetadata read(String entitySource) {
 *         return new MyReader().read(entitySource);
 *     }
 * }
 * }
 *
 * <p>The cases are not a wish list. Each one is a defect this ecosystem actually shipped: a reader
 * inventing an entity name from an attribute the annotation never declared, constraint values
 * landing on a carrier no generator reads, a zero bound dropped as if it were absent, a
 * relationship cardinality read under the wrong key so every edge came back as the builder default,
 * a saga version never read at all so every saga came back as version 1.
 */
/*
 * S5960 suppressed: in a TCK the assertions are the shipped artifact, not residue. Full rationale
 * on {@code AbstractExerisTck}.
 */
@SuppressWarnings("java:S5960")
public abstract class AbstractMetadataReaderTck extends AbstractExerisTck {

    /**
     * For subclasses; this type is extended, never instantiated directly.
     *
     * <p>Declared so the metadata-reader suite carries a documented constructor rather than an implicit
     * one. It stays {@code public} rather than becoming {@code protected}: the implicit
     * constructor of a public class is public, so narrowing it here would be a binary
     * break on a published artifact — which the semver gate reports as
     * {@code CONSTRUCTOR_LESS_ACCESSIBLE}.
     */
    // java:S5993 asks for protected here, and is right in general — an abstract class's
    // constructor exists only for subclasses. It is wrong for this one: the constructor it
    // replaces was implicit and therefore public, so narrowing it breaks binary
    // compatibility on a published artifact, which japicmp reports as
    // CONSTRUCTOR_LESS_ACCESSIBLE.
    @SuppressWarnings("java:S5993")
    public AbstractMetadataReaderTck() {
        // Nothing to initialize; declared so it carries a comment rather than being implicit.
    }

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
    @DisplayName("the relationship carries the display field the source declares")
    void relationshipDisplayFieldIsTheDeclaredOne() {
        requireSupported(Facet.RELATIONSHIPS);
        RelationshipMetadata orders = relationship(TckCorpus.CUSTOMER, "orders");
        assertThat(orders.displayField())
                .withFailMessage(
                        "Customer.orders declares displayField = \"reference\" and was read as '%s'. "
                                + "An unread displayField does not fail: the record reports 'name', "
                                + "and every picker and label for the association shows a field "
                                + "the target may not have.",
                        orders.displayField())
                .isEqualTo("reference");
    }

    private RelationshipMetadata relationship(String entity, String field) {
        DomainMetadata domain = read(TckCorpus.sourceOf(entity));
        // fieldName() falls back to name(), so one comparison covers both ways of naming the edge.
        return domain.relationships().stream()
                .filter(r -> field.equals(r.fieldName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No relationship read for " + entity + "." + field + "; got " + domain.relationships()));
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

    @Test
    @DisplayName("each action carries its return type, fully qualified, and void as \"void\"")
    void actionResultTypeIsTheDeclaredOne() {
        requireSupported(Facet.ACTIONS);
        requireSupported(Facet.ACTION_RESULT_TYPE);
        DomainMetadata order = read(TckCorpus.sourceOf(TckCorpus.ORDER));
        for (Map.Entry<String, String> expected : TckCorpus.orderResultTypes().entrySet()) {
            ActionMetadata action = order.actions().stream()
                    .filter(a -> expected.getKey().equals(a.name()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Order declares @Action(name = \"" + expected.getKey() + "\"); the reader "
                                    + "did not return it."));
            assertThat(action.resultType())
                    .withFailMessage(
                            "Order's action '%s' was read with resultType '%s'. The component carries "
                                    + "the return type as javac reports it, %s: fully qualified, type "
                                    + "arguments kept, and \"void\" for an action that returns nothing. "
                                    + "The streaming generators type the action's result frame from it, "
                                    + "and emit none for \"void\".",
                            expected.getKey(), action.resultType(), expected.getValue())
                    .isEqualTo(expected.getValue());
        }
    }

    @Test
    @DisplayName("the saga carries the version the source declares")
    void sagaVersionIsTheDeclaredOne() {
        requireSupported(Facet.SAGA);
        SagaMetadata saga = read(TckCorpus.sourceOf(TckCorpus.ORDER)).sagaMetadata();
        assertThat(saga)
                .withFailMessage("Order declares @Saga; the reader returned no saga metadata.")
                .isNotNull();
        assertThat(saga.version())
                .withFailMessage(
                        "Order declares @Saga(version = 3) and was read as version %d. The kernel "
                                + "addresses a saga plan by name and version (ADR-064), so this is a "
                                + "different plan identity, not an approximation of the right one. A "
                                + "reader that skips the attribute does not fail: it yields the "
                                + "default 1, and disagrees with every producer that reads it. The "
                                + "SDK's own reader shipped exactly this defect.",
                        saga.version())
                .isEqualTo(3);
    }

    @Test
    @DisplayName("the saga carries the compensation section the source declares")
    void sagaCompensationIsTheDeclaredOne() {
        requireSupported(Facet.SAGA_COMPENSATION);
        SagaMetadata saga = read(TckCorpus.sourceOf(TckCorpus.ORDER)).sagaMetadata();
        assertThat(saga)
                .withFailMessage("Order declares @Saga; the reader returned no saga metadata.")
                .isNotNull();
        assertThat(saga.compensationMaxRetries())
                .withFailMessage(
                        "Order declares @Saga(compensationMaxRetries = 0) and it was read as %s. The "
                                + "component is boxed so that a declared 0 survives: null means "
                                + "'not declared', and the annotation default, 5, then applies.",
                        saga.compensationMaxRetries())
                .isEqualTo(0);
        assertThat(saga.continueCompensationOnFailure())
                .withFailMessage(
                        "Order declares @Saga(continueCompensationOnFailure = false) and it was read "
                                + "as %s. A dropped false reads as 'not declared', and the annotation "
                                + "default, true, is the opposite of what the source says.",
                        saga.continueCompensationOnFailure())
                .isFalse();
        assertThat(saga.manualInterventionOnCompensationFailure())
                .withFailMessage(
                        "Order declares @Saga(manualInterventionOnCompensationFailure = false) and it "
                                + "was read as %s. A dropped false reads as 'not declared', and the "
                                + "annotation default, true, is the opposite of what the source says.",
                        saga.manualInterventionOnCompensationFailure())
                .isFalse();
        assertThat(saga.compensationFailureHandler())
                .withFailMessage(
                        "Order declares compensationFailureHandler = Order.CompensationEscalation.class "
                                + "and it was read as '%s'. The component carries the fully-qualified "
                                + "name javac reports, %s.Order.CompensationEscalation; the written "
                                + "form names a different type to every consumer outside this file.",
                        saga.compensationFailureHandler(), TckCorpus.packageName())
                .isEqualTo(TckCorpus.packageName() + ".Order.CompensationEscalation");
        assertThat(saga.compensationTimeout()).isEqualTo("PT2M");
        assertThat(saga.compensationRetryDelay()).isEqualTo("PT30S");
        assertThat(saga.compensationDlq()).isEqualTo("sales.order-compensation.dlq");
    }

    @Test
    @DisplayName("@QueryParam binds an action parameter from the query string, under its declared key")
    void queryParamsAreTheDeclaredOnes() {
        requireSupported(Facet.QUERY_PARAMS);
        DomainMetadata order = read(TckCorpus.sourceOf(TckCorpus.ORDER));
        ActionMetadata search = order.actions().stream()
                .filter(a -> "search".equals(a.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Order declares @Action(name = \"search\"); the reader returned "
                                + order.actions().stream().map(ActionMetadata::name).toList()));
        ActionParamMetadata term = param(search, "term");
        assertThat(term.effectiveBindingSource())
                .withFailMessage(
                        "Order.search(term) carries @QueryParam(\"q\") and was read with binding "
                                + "source %s. A query parameter read as a body parameter is generated "
                                + "into the request document, where no client sends it.",
                        term.effectiveBindingSource())
                .isEqualTo(ParamSource.QUERY);
        assertThat(term.effectiveQueryKey())
                .withFailMessage(
                        "Order.search(term) is read under the query key 'q' and was read under '%s'. "
                                + "The key is @QueryParam.value, not the parameter name.",
                        term.effectiveQueryKey())
                .isEqualTo("q");
        ActionParamMetadata limit = param(search, "limit");
        assertThat(limit.effectiveBindingSource()).isEqualTo(ParamSource.QUERY);
        assertThat(limit.effectiveQueryKey()).isEqualTo("limit");
        assertThat(limit.required())
                .withFailMessage(
                        "Order.search(limit) declares @QueryParam(required = false) and was read "
                                + "as required. A dropped false reads as the annotation default, "
                                + "true, and a request without the key is refused.")
                .isFalse();
    }

    @Test
    @DisplayName("@NavMenu is carried as the navigation entry the source declares, a zero order included")
    void navMenuIsTheDeclaredOne() {
        requireSupported(Facet.NAV_MENU);
        NavMenuMetadata nav = read(TckCorpus.sourceOf(TckCorpus.ORDER)).navMenu();
        assertThat(nav)
                .withFailMessage("Order declares @NavMenu; the reader returned no navMenu.")
                .isNotNull();
        assertThat(nav.order())
                .withFailMessage(
                        "Order declares @NavMenu(order = 0) and it was read as %s. Zero is a "
                                + "position; read as absent it becomes the default 999 and the "
                                + "entry moves to the end of its group.",
                        nav.order())
                .isEqualTo(0);
        assertThat(nav.group()).isEqualTo("Sales");
        assertThat(nav.label()).isEqualTo("Orders");
        assertThat(nav.roles()).containsExactly("SALES");
        assertThat(nav.divider()).isEqualTo(NavMenuMetadata.Divider.AFTER);
        assertThat(read(TckCorpus.sourceOf(TckCorpus.CUSTOMER)).navMenu())
                .withFailMessage("Customer declares no @NavMenu; the reader returned one.")
                .isNull();
    }

    @Test
    @DisplayName("@Tab is carried as the tabs the fields declare, on an entity with no @UI")
    void tabsAreTheDeclaredOnes() {
        requireSupported(Facet.TABS);
        UIMetadata ui = read(TckCorpus.sourceOf(TckCorpus.ORDER)).uiMetadata();
        assertThat(ui)
                .withFailMessage(
                        "Order declares @Tab on three fields and no @UI; the reader returned no "
                                + "uiMetadata. Tabs are carried whether or not the type declares @UI.")
                .isNotNull();
        List<UIMetadata.UITabMetadata> tabs = ui.tabs() == null ? List.of() : ui.tabs();
        assertThat(tabs.stream().map(UIMetadata.UITabMetadata::name).toList())
                .withFailMessage(
                        "Order's fields declare the tabs 'summary' and 'notes', in that order; the "
                                + "reader returned %s. One tab per name, in the order each name is "
                                + "first declared.",
                        tabs.stream().map(UIMetadata.UITabMetadata::name).toList())
                .containsExactly("summary", "notes");
        UIMetadata.UITabMetadata summary = tabs.get(0);
        assertThat(summary.fields())
                .withFailMessage(
                        "The 'summary' tab is named on Order.reference and Order.totalCents; the "
                                + "reader returned members %s.",
                        summary.fields())
                .containsExactly("reference", "totalCents");
        assertThat(summary.order())
                .withFailMessage(
                        "Order.reference declares @Tab(order = 0) and the tab was read with order "
                                + "%s. Zero is a position; read as absent it becomes the default 999.",
                        summary.order())
                .isEqualTo(0);
        assertThat(summary.label()).isEqualTo("Summary");
        assertThat(tabs.get(1).lazy()).isTrue();
        assertThat(tabs.get(1).fields()).containsExactly("note");
    }

    @Test
    @DisplayName("@GraphProperty is carried as the properties the fields declare, AUTO left untyped")
    void graphPropertiesAreTheDeclaredOnes() {
        requireSupported(Facet.GRAPH_PROPERTIES);
        GraphMetadata graph = read(TckCorpus.sourceOf(TckCorpus.PRODUCT)).graphMetadata();
        assertThat(graph)
                .withFailMessage("Product declares @Graph; the reader returned no graphMetadata.")
                .isNotNull();
        List<GraphPropertyMetadata> properties = graph.properties();
        assertThat(properties)
                .withFailMessage(
                        "Product's fields declare two @GraphProperty and the reader returned %s. "
                                + "Null means the reader does not extract @GraphProperty at all.",
                        properties)
                .isNotNull()
                .hasSize(2);
        GraphPropertyMetadata name = properties.get(0);
        assertThat(name.name()).isEqualTo("name");
        assertThat(name.indexed()).isTrue();
        assertThat(name.type())
                .withFailMessage(
                        "Product.name declares no graphType and was read with type '%s'. AUTO is "
                                + "absent: the store type follows the field's Java type, and a type "
                                + "derived here is one no other side derives.",
                        name.type())
                .isNull();
        GraphPropertyMetadata sku = properties.get(1);
        assertThat(sku.name()).isEqualTo("skuCode");
        assertThat(sku.type()).isEqualTo("STRING");
        assertThat(sku.effectiveField())
                .withFailMessage(
                        "Product.sku is the graph property 'skuCode' and was read with field '%s'. "
                                + "Without the field a graph-sync writer has nothing to read the "
                                + "property from.",
                        sku.effectiveField())
                .isEqualTo("sku");
    }

    @Test
    @DisplayName("@UIGroup is carried as the groups the fields declare, on an entity with no @UI")
    void uiGroupsAreTheDeclaredOnes() {
        requireSupported(Facet.UI_GROUPS);
        UIMetadata ui = read(TckCorpus.sourceOf(TckCorpus.CUSTOMER)).uiMetadata();
        assertThat(ui)
                .withFailMessage(
                        "Customer declares @UIGroup on three fields and no @UI; the reader returned no "
                                + "uiMetadata. Groups are carried whether or not the type declares @UI.")
                .isNotNull();
        List<UIMetadata.UIGroupMetadata> groups = ui.groups() == null ? List.of() : ui.groups();
        assertThat(groups.stream().map(UIMetadata.UIGroupMetadata::name).toList())
                .withFailMessage(
                        "Customer's fields declare the groups 'identity' and 'media', in that order; "
                                + "the reader returned %s. One group per name, in the order each name "
                                + "is first declared, whatever its order attribute says.",
                        groups.stream().map(UIMetadata.UIGroupMetadata::name).toList())
                .containsExactly("identity", "media");
        UIMetadata.UIGroupMetadata identity = groups.get(0);
        assertThat(identity.fields()).containsExactly("email", "displayName");
        assertThat(identity.label())
                .withFailMessage(
                        "Customer.email declares the 'identity' label 'Identity' and a later member "
                                + "restates it; the group was read with label '%s'. The first "
                                + "declaration of an attribute is the one kept.",
                        identity.label())
                .isEqualTo("Identity");
        assertThat(identity.order()).isEqualTo(2);
        assertThat(identity.collapsed()).isTrue();
        assertThat(identity.collapsible())
                .withFailMessage(
                        "No 'identity' member declares collapsible and the group was read as not "
                                + "collapsible. The annotation default is true.")
                .isTrue();
        assertThat(identity.gridSpan()).isEqualTo(UIMetadata.UIGroupMetadata.DEFAULT_GRID_SPAN);
        UIMetadata.UIGroupMetadata media = groups.get(1);
        assertThat(media.fields()).containsExactly("avatar");
        assertThat(media.order()).isZero();
        assertThat(media.collapsible())
                .withFailMessage("Customer.avatar declares @UIGroup(collapsible = false); it was read as true.")
                .isFalse();
    }

    @Test
    @DisplayName("@Blob is carried on the field as written, a field without @Field included")
    void blobsAreTheDeclaredOnes() {
        requireSupported(Facet.BLOBS);
        DomainMetadata customer = read(TckCorpus.sourceOf(TckCorpus.CUSTOMER));
        assertThat(field(customer, "avatar").blob())
                .withFailMessage(
                        "Customer.avatar declares @Blob(contentTypes = {\"image/png\", \"image/jpeg\"}) "
                                + "and was read with blob %s.",
                        field(customer, "avatar").blob())
                .isEqualTo(BlobMetadata.ofContentTypes(List.of("image/png", "image/jpeg")));
        assertThat(field(customer, "contract").blob())
                .withFailMessage(
                        "Customer.contract declares @Blob(container = \"contracts\") and no @Field, and "
                                + "was read with blob %s. A @Blob is read whether or not the field "
                                + "carries @Field.",
                        field(customer, "contract").blob())
                .isEqualTo(new BlobMetadata("contracts", List.of()));
        assertThat(field(customer, "email").blob())
                .withFailMessage("Customer.email declares no @Blob and was read with one.")
                .isNull();
    }

    /**
     * Returns the named parameter of an action, failing the test when it was not read.
     *
     * @param action the action to look in
     * @param name the parameter to find
     * @return that parameter's metadata
     * @throws AssertionError when the reader returned no such parameter
     */
    private static ActionParamMetadata param(ActionMetadata action, String name) {
        List<ActionParamMetadata> params = action.params() == null ? List.of() : action.params();
        return params.stream()
                .filter(p -> name.equals(p.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No parameter '" + name + "' was read for action '" + action.name() + "'; got "
                                + params.stream().map(ActionParamMetadata::name).toList()
                                + ". A parameter carrying @QueryParam alone is an action parameter."));
    }

    /**
     * Every route-access declaration in the corpus, read as declared. A {@code PUBLIC} skipped as
     * if ordinal 0 were a default reads as "declared nothing"; an entity's declaration copied onto
     * an inheriting action reads as a declaration the author never wrote.
     */
    @Test
    @DisplayName("route access and permissions are carried as each element declares them")
    void routeAccessIsTheDeclaredOne() {
        requireSupported(Facet.ROUTE_ACCESS);
        List<String> gaps = new ArrayList<>();
        for (String name : TckCorpus.entityNames()) {
            gaps.addAll(CorpusRouteAccess.gaps(read(TckCorpus.sourceOf(name))));
        }
        assertThat(gaps)
                .withFailMessage(
                        "The reader's route access disagrees with the corpus on %d path(s):%n  %s%n%n"
                                + "Each element carries only what it declares: an action without a "
                                + "declaration of its own carries no routeAccess and no permissions, "
                                + "and PUBLIC is a declaration, not a default.",
                        gaps.size(), String.join("\n  ", gaps))
                .isEmpty();
    }

    @Test
    @DisplayName("the saga carries the trigger the source declares")
    void sagaTriggerIsTheDeclaredOne() {
        requireSupported(Facet.SAGA_TRIGGER);
        SagaMetadata saga = read(TckCorpus.sourceOf(TckCorpus.ORDER)).sagaMetadata();
        assertThat(saga)
                .withFailMessage("Order declares @Saga; the reader returned no saga metadata.")
                .isNotNull();
        SagaMetadata.SagaTrigger trigger = saga.trigger();
        assertThat(trigger)
                .withFailMessage(
                        "Order declares @Saga(trigger = ...) and the reader returned none. Null means "
                                + "'not declared', and the saga then has no recorded start.")
                .isNotNull();
        assertThat(trigger.type())
                .withFailMessage(
                        "Order declares a trigger of type EVENT and it was read as %s. EVENT is the first "
                                + "constant of its enum, so a dropped value reads as the default, which "
                                + "is not what the source says.",
                        trigger.type())
                .isEqualTo(SagaMetadata.TriggerType.EVENT);
        String source = TckCorpus.packageName() + ".Order.Placed";
        assertThat(trigger.source())
                .withFailMessage(
                        "Order declares source = Order.Placed.class and it was read as '%s'. The component "
                                + "carries the fully-qualified name javac reports, %s.",
                        trigger.source(), source)
                .isEqualTo(source);
        assertThat(trigger.topic()).isEqualTo("sales.orders");
        assertThat(trigger.cronExpression()).isNull();
    }

    /**
     * Returns the named field, failing the test when it was not read.
     *
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
