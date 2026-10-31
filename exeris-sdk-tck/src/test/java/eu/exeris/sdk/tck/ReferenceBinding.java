package eu.exeris.sdk.tck;

import eu.exeris.sdk.sourcemodel.ast.ActionMetadata;
import eu.exeris.sdk.sourcemodel.ast.ActionParamMetadata;
import eu.exeris.sdk.sourcemodel.ast.BlobMetadata;
import eu.exeris.sdk.sourcemodel.ast.DataScope;
import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.FieldMetadata;
import eu.exeris.sdk.sourcemodel.ast.GraphMetadata;
import eu.exeris.sdk.sourcemodel.ast.GraphPropertyMetadata;
import eu.exeris.sdk.sourcemodel.ast.NavMenuMetadata;
import eu.exeris.sdk.sourcemodel.ast.ParamSource;
import eu.exeris.sdk.sourcemodel.ast.RelationshipMetadata;
import eu.exeris.sdk.sourcemodel.ast.RouteAccess;
import eu.exeris.sdk.sourcemodel.ast.SagaMetadata;
import eu.exeris.sdk.sourcemodel.ast.SagaStepMetadata;
import eu.exeris.sdk.sourcemodel.ast.UIMetadata;
import eu.exeris.sdk.sourcemodel.mutation.BaselineTrust;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.function.Consumer;

/**
 * A conforming binding, hand-built.
 *
 * <p>Exists to make the kit's own cases provably non-vacuous: every self-test drives a contract
 * method against this and against a fake broken in one specific way, asserting the first passes and
 * the second fails. Without the pair, a case asserting a negative would stay green whether or not
 * it enforced anything — a non-conforming implementation would pass the suite and the contract
 * would look enforced while nothing enforced it.
 *
 * <p>It is also the readable statement of what the corpus <em>means</em>: the values here are what
 * a correct reader and a correct producer must both arrive at from the shipped sources.
 */
final class ReferenceBinding {

    /** The saga {@code Order} declares, by the name its {@code @Saga} gives it. */
    static final String ORDER_SAGA = "OrderFulfillmentSaga";

    /** The version {@code Order}'s {@code @Saga} declares — deliberately not the default {@code 1}. */
    static final int ORDER_SAGA_VERSION = 3;

    /** The handler {@code Order}'s {@code @Saga} names, fully qualified as javac reports it. */
    static final String ORDER_SAGA_FAILURE_HANDLER = TckCorpus.packageName() + ".Order.CompensationEscalation";

    /** The event type {@code Order}'s {@code @Saga.trigger} names, fully qualified as javac reports it. */
    static final String ORDER_SAGA_TRIGGER_SOURCE = TckCorpus.packageName() + ".Order.Placed";

    /** The record {@code Order}'s {@code receipt} action returns, fully qualified as javac reports it. */
    static final String ORDER_RECEIPT = TckCorpus.packageName() + ".Order.Receipt";

    private ReferenceBinding() {
    }

    static DomainMetadata metadataFor(String entityName) {
        return switch (entityName) {
            case TckCorpus.ORDER -> order();
            case TckCorpus.CUSTOMER -> customer();
            case TckCorpus.PRODUCT -> product();
            default -> throw new IllegalArgumentException("Not a corpus entity: " + entityName);
        };
    }

    /** @param source a corpus source; the entity is identified by the class it declares */
    static DomainMetadata readFrom(String source) {
        return metadataFor(entityNameOf(source));
    }

    /** @return the JSON a conforming producer writes, trust siblings included */
    static String produceFrom(String source) {
        return json(readFrom(source), true);
    }

    static String entityNameOf(String source) {
        for (String name : TckCorpus.entityNames()) {
            if (source.contains("public class " + name + " ")) {
                return name;
            }
        }
        throw new IllegalArgumentException("No corpus entity declared in the given source");
    }

    /**
     * @param metadata the entity to serialize
     * @param withTrust whether to stamp the two ADR-042 baseline-trust siblings
     * @return the serialized baseline
     */
    static String json(DomainMetadata metadata, boolean withTrust) {
        ObjectNode node = (ObjectNode) TckMappers.canonical().valueToTree(metadata);
        if (withTrust) {
            BaselineTrust trust = BaselineTrust.current("0".repeat(64));
            node.put("schemaVersion", trust.schemaVersion());
            node.put("sourceDigest", trust.sourceDigest());
        }
        return node.toString();
    }

    private static DomainMetadata order() {
        return DomainMetadata.builder(TckCorpus.ORDER, TckCorpus.packageName())
                .module("sales")
                .path("/orders")
                .description("A customer order.")
                .dataScope(DataScope.TENANT)
                .permissions(List.of("order:read", "order:write"))
                .routeAccess(RouteAccess.AUTHENTICATED)
                .fields(List.of(
                        FieldMetadata.simple("id", "java.util.UUID"),
                        FieldMetadata.builder("reference", "java.lang.String")
                                .displayName("Reference")
                                .required(true)
                                .minLength(3)
                                .maxLength(32)
                                .pattern("^ORD-[0-9]+$")
                                .build(),
                        FieldMetadata.builder("totalCents", "java.lang.Long")
                                .displayName("Total (cents)")
                                .required(true)
                                .min(0L)
                                .max(1_000_000L)
                                .build(),
                        FieldMetadata.builder("note", "java.lang.String")
                                .displayName("Note")
                                .build()))
                .actions(orderActions())
                .sagaMetadata(orderSaga().build())
                .navMenu(orderNavMenu().build())
                .uiMetadata(UIMetadata.builder().tabs(orderTabs()).build())
                .build();
    }

    /**
     * The actions {@code Order} declares, with each return type in the form javac reports it.
     *
     * @return the reference actions, in declaration order
     */
    static List<ActionMetadata> orderActions() {
        return List.of(
                action("submit", "Submit", "POST", "void"),
                action("customer", "Customer", "GET", TckCorpus.packageName() + "." + TckCorpus.CUSTOMER),
                action("receipt", "Receipt", "GET", ORDER_RECEIPT),
                action("receipts", "Receipts", "GET", "java.util.List<" + ORDER_RECEIPT + ">"),
                action("lineCount", "Line count", "GET", "int"),
                ActionMetadata.builder("approve")
                        .displayName("Approve")
                        .methodName("approve")
                        .resultType("void")
                        .permissions(List.of("order:approve"))
                        .build(),
                ActionMetadata.builder("track")
                        .displayName("Track")
                        .methodName("track")
                        .httpMethod("GET")
                        .resultType("void")
                        .routeAccess(RouteAccess.PUBLIC)
                        .build(),
                orderSearch());
    }

    /**
     * One reference action, so a self-test can rebuild it with a single component changed.
     *
     * @param name the action and method name
     * @param label the declared label, carried as {@code displayName}
     * @param httpMethod the declared HTTP method
     * @param resultType the return type as javac reports it
     * @return the action
     */
    static ActionMetadata action(String name, String label, String httpMethod, String resultType) {
        return ActionMetadata.builder(name)
                .displayName(label)
                .methodName(name)
                .httpMethod(httpMethod)
                .resultType(resultType)
                .build();
    }

    /**
     * The {@code search} action {@code Order} declares: two parameters bound from the query string.
     *
     * @return the reference action
     */
    static ActionMetadata orderSearch() {
        return ActionMetadata.builder("search")
                .displayName("Search")
                .httpMethod("GET")
                .methodName("search")
                .resultType("void")
                .addParam(ActionParamMetadata.builder("term", "java.lang.String")
                        .required(true)
                        .bindingSource(ParamSource.QUERY)
                        .queryKey("q")
                        .build())
                .addParam(ActionParamMetadata.builder("limit", "int")
                        .required(false)
                        .bindingSource(ParamSource.QUERY)
                        .build())
                .build();
    }

    /**
     * The navigation entry {@code Order} declares, as a builder, so a self-test can change exactly
     * one component.
     *
     * @return a builder holding the reference entry
     */
    static NavMenuMetadata.Builder orderNavMenu() {
        return NavMenuMetadata.builder()
                .group("Sales")
                .label("Orders")
                .order(0)
                .roles(List.of("SALES"))
                .divider(NavMenuMetadata.Divider.AFTER);
    }

    /**
     * The tabs {@code Order}'s fields declare.
     *
     * @return the reference tabs, in first-declared order
     */
    static List<UIMetadata.UITabMetadata> orderTabs() {
        return List.of(
                new UIMetadata.UITabMetadata("summary", "Summary", 0, null, false, null, null, false,
                        null, null, null, List.of("reference", "totalCents")),
                new UIMetadata.UITabMetadata("notes", "Notes", null, null, true, null, null, false,
                        null, null, null, List.of("note")));
    }

    /**
     * The saga {@code Order} declares, as a builder, so a self-test can change exactly one
     * component and leave the rest conforming.
     *
     * @return a builder holding the reference saga
     */
    static SagaMetadata.Builder orderSaga() {
        return SagaMetadata.builder(ORDER_SAGA)
                .version(ORDER_SAGA_VERSION)
                .compensationTimeout("PT2M")
                .compensationMaxRetries(0)
                .compensationRetryDelay("PT30S")
                .continueCompensationOnFailure(false)
                .compensationDlq("sales.order-compensation.dlq")
                .compensationFailureHandler(ORDER_SAGA_FAILURE_HANDLER)
                .manualInterventionOnCompensationFailure(false)
                .trigger(orderSagaTrigger())
                .steps(List.of(SagaStepMetadata.builder("charge", 1)
                        .service("payments")
                        .command("charge")
                        .build()));
    }

    /**
     * The trigger {@code Order}'s {@code @Saga} declares.
     *
     * @return the reference trigger
     */
    static SagaMetadata.SagaTrigger orderSagaTrigger() {
        return new SagaMetadata.SagaTrigger(
                SagaMetadata.TriggerType.EVENT, ORDER_SAGA_TRIGGER_SOURCE, "sales.orders", null, null);
    }

    private static DomainMetadata customer() {
        return DomainMetadata.builder(TckCorpus.CUSTOMER, TckCorpus.packageName())
                .module("sales")
                .path("/customers")
                .description("A person or organisation that places orders.")
                .dataScope(DataScope.TENANT)
                .permissions(List.of("customer:read"))
                .fields(List.of(
                        FieldMetadata.simple("id", "java.util.UUID"),
                        FieldMetadata.builder("email", "java.lang.String")
                                .displayName("E-mail")
                                .required(true)
                                .unique(true)
                                .maxLength(254)
                                .build(),
                        FieldMetadata.builder("displayName", "java.lang.String")
                                .displayName("Display name")
                                .required(true)
                                .minLength(1)
                                .maxLength(120)
                                .build(),
                        FieldMetadata.builder("avatar", "java.lang.String")
                                .displayName("Avatar")
                                .blob(BlobMetadata.ofContentTypes(List.of("image/png", "image/jpeg")))
                                .build(),
                        FieldMetadata.builder("contract", "java.lang.String")
                                .searchable(true)
                                .sortable(true)
                                .filterable(true)
                                .blob(new BlobMetadata("contracts", List.of()))
                                .build(),
                        FieldMetadata.simple("orders", "java.util.List<" + TckCorpus.packageName() + "." + TckCorpus.ORDER + ">"),
                        FieldMetadata.simple("lastOrderId", "java.util.UUID")))
                .relationships(List.of(
                        RelationshipMetadata.builder("orders", TckCorpus.ORDER)
                                .type(RelationshipMetadata.RelationType.ONE_TO_MANY)
                                .mappedBy("customer")
                                .cascade(RelationshipMetadata.CascadeType.REMOVE)
                                .displayField("reference")
                                .build(),
                        RelationshipMetadata.builder("lastOrderId", TckCorpus.ORDER)
                                .displayField("reference")
                                .build()))
                .uiMetadata(UIMetadata.builder().groups(customerGroups()).build())
                .build();
    }

    /**
     * The groups {@code Customer}'s fields declare.
     *
     * @return the reference groups, in first-declared order
     */
    static List<UIMetadata.UIGroupMetadata> customerGroups() {
        return List.of(
                new UIMetadata.UIGroupMetadata("identity", "Identity", null, 2, true, true, null,
                        UIMetadata.UIGroupMetadata.DEFAULT_GRID_SPAN, List.of("email", "displayName")),
                new UIMetadata.UIGroupMetadata("media", "Media", null, 0, false, false, null,
                        UIMetadata.UIGroupMetadata.DEFAULT_GRID_SPAN, List.of("avatar")));
    }

    /**
     * The graph properties {@code Product}'s fields declare.
     *
     * @return the reference properties, in declaration order
     */
    static List<GraphPropertyMetadata> productProperties() {
        return List.of(
                new GraphPropertyMetadata("name", null, true),
                new GraphPropertyMetadata("skuCode", "STRING", false, "sku"));
    }

    /**
     * The metadata put through an edit of its wire form, so a self-test can break one component
     * and keep every other one as the reference has it.
     *
     * @param metadata the metadata to edit
     * @param edit the change to its JSON tree
     * @return the edited metadata
     */
    static DomainMetadata edited(DomainMetadata metadata, Consumer<ObjectNode> edit) {
        ObjectNode node = (ObjectNode) TckMappers.canonical().valueToTree(metadata);
        edit.accept(node);
        return TckMappers.canonical().treeToValue(node, DomainMetadata.class);
    }

    /** A side that skips {@code PUBLIC} as if ordinal 0 were a default, on the entity and its actions. */
    static DomainMetadata publicDropped(DomainMetadata metadata) {
        return edited(metadata, node -> {
            dropPublic(node);
            for (JsonNode action : node.path("actions")) {
                dropPublic((ObjectNode) action);
            }
        });
    }

    /** A side that resolves inheritance into the metadata, copying the entity's declaration onto actions. */
    static DomainMetadata inheritanceResolved(DomainMetadata metadata) {
        return edited(metadata, node -> {
            for (JsonNode action : node.path("actions")) {
                ObjectNode a = (ObjectNode) action;
                if (!a.has("routeAccess") && a.path("permissions").isEmpty()) {
                    if (node.has("routeAccess")) {
                        a.set("routeAccess", node.get("routeAccess"));
                    }
                    if (!node.path("permissions").isEmpty()) {
                        a.set("permissions", node.get("permissions"));
                    }
                }
            }
        });
    }

    /** A side that loses the declaration order of the entity's permissions. */
    static DomainMetadata permissionsReversed(DomainMetadata metadata) {
        return edited(metadata, node -> {
            if (node.path("permissions").size() > 1) {
                ArrayNode reversed = node.arrayNode();
                JsonNode permissions = node.get("permissions");
                for (int i = permissions.size() - 1; i >= 0; i--) {
                    reversed.add(permissions.get(i));
                }
                node.set("permissions", reversed);
            }
        });
    }

    private static void dropPublic(ObjectNode node) {
        if ("PUBLIC".equals(node.path("routeAccess").asString(""))) {
            node.remove("routeAccess");
        }
    }

    private static DomainMetadata product() {
        return DomainMetadata.builder(TckCorpus.PRODUCT, TckCorpus.packageName())
                .module("catalog")
                .path("/products")
                .description("An item offered for sale.")
                .routeAccess(RouteAccess.PUBLIC)
                .fields(List.of(
                        FieldMetadata.simple("id", "java.util.UUID"),
                        FieldMetadata.builder("name", "java.lang.String")
                                .displayName("Name")
                                .required(true)
                                .build(),
                        FieldMetadata.builder("sku", "java.lang.String")
                                .displayName("SKU")
                                .build()))
                .graphMetadata(new GraphMetadata(TckCorpus.PRODUCT, productProperties(), List.of(), null))
                .actions(List.of(ActionMetadata.builder("reprice")
                        .displayName("Reprice")
                        .methodName("reprice")
                        .resultType("void")
                        .permissions(List.of("product:reprice"))
                        .routeAccess(RouteAccess.AUTHENTICATED)
                        .build()))
                .build();
    }
}
