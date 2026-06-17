package eu.exeris.sdk.sourcemodel.ast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wire-format guard: every AST record this SDK exposes must survive a JSON
 * serialize → deserialize round trip via Jackson 3 with all components
 * preserved. The processor writes these records to {@code *.json} files at
 * build time and downstream tooling reads them back; a silent loss of a
 * component (missing {@code @JsonInclude}, missing {@code @JsonCreator},
 * accidental getter override) would break the codegen contract.
 */
@DisplayName("AST records survive JSON round-trip")
class AstJsonRoundTripTest {

    /**
     * Jackson 3 defaults {@code FAIL_ON_NULL_FOR_PRIMITIVES=true}; Jackson 2
     * defaulted it to {@code false}. AST records use primitive booleans heavily
     * with {@code @JsonInclude(NON_DEFAULT)} / {@code NON_NULL}, so absent
     * fields land as {@code null} on the wire. Downstream consumers MUST
     * configure their mapper the same way or read paths break.
     */
    private final ObjectMapper mapper = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
            .build();

    @Test
    @DisplayName("DomainMetadata round-trips with nested fields, actions, events")
    void domainMetadataRoundTrips() {
        DomainMetadata original = DomainMetadata.builder("Order", "com.acme.domain")
                .module("sales")
                .path("/orders")
                .description("Customer order")
                .apiVersion("v1")
                .tags(List.of("retail", "billing"))
                .restApi(true)
                .tenantScoped(true)
                .audited(true)
                .softDelete(true)
                .versioned(true)
                .roles(List.of("ROLE_ORDER_VIEW"))
                .permissions(List.of("orders:read", "orders:write"))
                .cacheable(true)
                .cacheTtl("PT5M")
                .cacheRegion("orders")
                .tableName("orders")
                .fields(List.of(
                        FieldMetadata.builder("orderNumber", "String").required(true).unique(true).build(),
                        FieldMetadata.builder("amount", "BigDecimal").required(true).build()))
                .events(List.of(new DomainEventMetadata("OrderCreated", "orders.created", "Order created", "Order")))
                .eventHandlers(List.of(
                        EventHandlerMetadata.builder("onOrderCreated")
                                .event("OrderCreated")
                                .condition("event.amount > 1000")
                                .order(1)
                                .build()))
                .relationships(List.of(
                        RelationshipMetadata.builder("customer", "Customer")
                                .type(RelationshipMetadata.RelationType.MANY_TO_ONE)
                                .build()))
                .build();

        assertRoundTrip(original, DomainMetadata.class);
    }

    @Test
    @DisplayName("FieldMetadata round-trips (NON_DEFAULT inclusion)")
    void fieldMetadataRoundTrips() {
        // Note: with @JsonInclude(NON_DEFAULT), Jackson 3 treats Long(0) as
        // empty for boxed wrappers and drops it on serialization. Avoid 0 as a
        // meaningful min/max value until tracked under the Field/Validation
        // overlap fix (SDK 0.2 follow-up).
        FieldMetadata original = FieldMetadata.builder("amount", "BigDecimal")
                .required(true)
                .unique(true)
                .indexed(true)
                .searchable(true)
                .sortable(true)
                .filterable(true)
                .audited(true)
                .columnName("amount_total")
                .displayName("Total Amount")
                .description("Sum across line items")
                .defaultValue("0")
                .min(1L)
                .max(1_000_000L)
                .pattern("\\d+(\\.\\d{2})?")
                .format("#,##0.00")
                .dataType("currency")
                .displayNameKey("order.field.amount.label")
                .descriptionKey("order.field.amount.help")
                .build();

        assertRoundTrip(original, FieldMetadata.class);
    }

    @Test
    @DisplayName("ActionMetadata round-trips with params (NON_DEFAULT inclusion)")
    void actionMetadataRoundTrips() {
        ActionMetadata original = ActionMetadata.builder("ship")
                .description("Ship the order")
                .httpMethod("POST")
                .idempotent(true)
                .dangerous(true)
                .requiresConfirmation(true)
                .permissions(List.of("orders:ship"))
                .producesEvents(List.of("OrderShipped"))
                .params(List.of(
                        ActionParamMetadata.builder("trackingNumber", "String")
                                .required(true)
                                .pattern("[A-Z0-9]+")
                                .build()))
                .build();

        assertRoundTrip(original, ActionMetadata.class);
    }

    @Test
    @DisplayName("ActionParamMetadata round-trips (record with NON_NULL inclusion)")
    void actionParamMetadataRoundTrips() {
        ActionParamMetadata original = ActionParamMetadata.builder("amount", "BigDecimal")
                .required(true)
                .description("Refund amount")
                .min(0L)
                .max(99_999L)
                .build();

        assertRoundTrip(original, ActionParamMetadata.class);
    }

    @Test
    @DisplayName("DomainEventMetadata round-trips")
    void domainEventMetadataRoundTrips() {
        DomainEventMetadata original = new DomainEventMetadata(
                "OrderShipped", "orders.shipped", "Order has shipped", "Order");
        assertRoundTrip(original, DomainEventMetadata.class);
    }

    @Test
    @DisplayName("EventHandlerMetadata round-trips (projection + saga shapes)")
    void eventHandlerMetadataRoundTrips() {
        // Projection-style handler: event selection + condition + ordering.
        EventHandlerMetadata projectionHandler = EventHandlerMetadata.builder("onOrderCompleted")
                .description("Update the order-summary read model")
                .event("OrderCompleted")
                .events(List.of("OrderCompleted", "OrderAmended"))
                .eventClassNames(List.of("com.acme.events.OrderCompletedEvent"))
                .topic("orders.completed")
                .condition("event.amount > 1000")
                .order(1)
                .priority("HIGH")
                .idempotent(true)
                .async(true)
                .timeout("PT10S")
                .transactionMode("REQUIRES_NEW")
                .build();
        assertRoundTrip(projectionHandler, EventHandlerMetadata.class);

        // Saga-style handler: trigger step + expected / failure events.
        EventHandlerMetadata sagaHandler = EventHandlerMetadata.builder("onPaymentCompleted")
                .triggerStep("processPayment")
                .expectedEvents(List.of("PaymentCompletedEvent"))
                .failureEvents(List.of("PaymentDeclinedEvent"))
                .build();
        assertRoundTrip(sagaHandler, EventHandlerMetadata.class);
    }

    @Test
    @DisplayName("RelationshipMetadata round-trips (Builder populates derived defaults)")
    void relationshipMetadataRoundTrips() {
        RelationshipMetadata original = RelationshipMetadata.builder("customer", "Customer")
                .type(RelationshipMetadata.RelationType.MANY_TO_ONE)
                .cascade(RelationshipMetadata.CascadeType.PERSIST)
                .fetch(RelationshipMetadata.FetchType.EAGER)
                .build();

        assertRoundTrip(original, RelationshipMetadata.class);
    }

    @Test
    @DisplayName("ProjectionMetadata round-trips")
    void projectionMetadataRoundTrips() {
        ProjectionMetadata original = new ProjectionMetadata(
                "OrderSummary", "List view projection", List.of("id", "orderNumber", "amount"), true);
        assertRoundTrip(original, ProjectionMetadata.class);
    }

    @Test
    @DisplayName("ValidationMetadata round-trips")
    void validationMetadataRoundTrips() {
        ValidationMetadata original = new ValidationMetadata(
                true, true, 1, 255, 0L, 1000L, "[A-Z]+", "must be uppercase",
                false, false, false, false);
        assertRoundTrip(original, ValidationMetadata.class);
    }

    @Test
    @DisplayName("UIMetadata round-trips with nested groups + field overrides")
    void uiMetadataRoundTrips() {
        // A field override carrying both the custom-component escape hatch and
        // the i18n placeholder/helpText keys, so all three survive the round-trip.
        UIMetadata.UIFieldMetadata withKeys = new UIMetadata.UIFieldMetadata(
                "location", UIMetadata.ComponentType.CUSTOM, 6, 0, true, true, true,
                "Pick a point", "Drag the marker", null, null, null, null, null, null,
                "app-geo-point-picker", "order.field.location.placeholder", "order.field.location.help");

        UIMetadata original = UIMetadata.builder()
                .icon("shopping-cart")
                .color("#ff8800")
                .columns(12)
                .groups(List.of(UIMetadata.UIGroupMetadata.simple("main", "Main", List.of("orderNumber", "amount"))))
                .fieldOverrides(List.of(
                        UIMetadata.UIFieldMetadata.simple("orderNumber", UIMetadata.ComponentType.TEXT_INPUT),
                        withKeys))
                .build();

        assertRoundTrip(original, UIMetadata.class);
    }

    @Test
    @DisplayName("GraphMetadata round-trips with edges + properties")
    void graphMetadataRoundTrips() {
        GraphMetadata original = new GraphMetadata(
                "Order",
                List.of(new GraphPropertyMetadata("orderNumber", "String", true)),
                List.of(new GraphEdgeMetadata("placedBy", "Customer", "PLACED_BY")),
                List.of(new GraphQueryMetadata("recentOrders", "MATCH (o:Order) ...", "Last 24h")));

        assertRoundTrip(original, GraphMetadata.class);
    }

    // The three graph leaves below are tested directly in addition to their
    // transitive coverage via GraphMetadata, so a future regression on a leaf's
    // own annotations surfaces as a focused failure rather than a confusing
    // GraphMetadata trace.

    @Test
    @DisplayName("GraphPropertyMetadata round-trips (graph leaf)")
    void graphPropertyMetadataRoundTrips() {
        assertRoundTrip(
                new GraphPropertyMetadata("amount", "BigDecimal", true),
                GraphPropertyMetadata.class);
    }

    @Test
    @DisplayName("GraphEdgeMetadata round-trips (graph leaf)")
    void graphEdgeMetadataRoundTrips() {
        assertRoundTrip(
                new GraphEdgeMetadata("placedBy", "Customer", "PLACED_BY"),
                GraphEdgeMetadata.class);
    }

    @Test
    @DisplayName("GraphQueryMetadata round-trips (graph leaf)")
    void graphQueryMetadataRoundTrips() {
        assertRoundTrip(
                new GraphQueryMetadata("recentOrders", "MATCH (o:Order) RETURN o LIMIT 10", "Last N orders"),
                GraphQueryMetadata.class);
    }

    @Test
    @DisplayName("SagaMetadata round-trips with steps + trigger")
    void sagaMetadataRoundTrips() {
        SagaMetadata original = SagaMetadata.builder("OrderFulfillmentSaga")
                .description("Reserve, charge, ship")
                .steps(List.of(
                        SagaStepMetadata.builder("reserve", 0)
                                .command("ReserveInventory")
                                .compensation("ReleaseInventory")
                                .build(),
                        SagaStepMetadata.builder("charge", 1)
                                .command("ChargeCard")
                                .compensation("RefundCard")
                                .dependsOn(List.of("reserve"))
                                .build()))
                .trigger(SagaMetadata.SagaTrigger.onEvent("OrderPlaced", "orders.placed"))
                .monitoring(SagaMetadata.MonitoringConfig.enabled())
                .build();

        assertRoundTrip(original, SagaMetadata.class);
    }

    @Test
    @DisplayName("SagaStepMetadata round-trips with mappings")
    void sagaStepMetadataRoundTrips() {
        SagaStepMetadata original = SagaStepMetadata.builder("ship", 2)
                .command("ShipOrder")
                .compensation("CancelShipment")
                .producesEvents(List.of("OrderShipped"))
                .inputMapping(SagaStepMetadata.InputMapping.fields(List.of(
                        SagaStepMetadata.FieldMapping.direct("state.address", "command.address"))))
                .outputMapping(SagaStepMetadata.OutputMapping.expression("$.trackingNumber"))
                .build();

        assertRoundTrip(original, SagaStepMetadata.class);
    }

    @Test
    @DisplayName("EventSourcedMetadata round-trips with projection configs")
    void eventSourcedMetadataRoundTrips() {
        EventSourcedMetadata original = EventSourcedMetadata.builder("Order")
                .snapshotEvery(50)
                .snapshotStrategy(EventSourcedMetadata.SnapshotStrategy.TIME_BASED)
                .archiveAfterDays(365)
                .compactEvents(true)
                .eventHandlers(List.of("OrderProjector"))
                .projections(List.of(
                        EventSourcedMetadata.ProjectionConfig.async(
                                "OrderListView", "OrderListItem",
                                List.of("OrderCreated", "OrderShipped"), "projections.orders")))
                .build();

        assertRoundTrip(original, EventSourcedMetadata.class);
    }

    @Test
    @DisplayName("InternalApiMetadata round-trips")
    void internalApiMetadataRoundTrips() {
        InternalApiMetadata original = InternalApiMetadata.builder()
                .readOnly(true)
                .reason("Frozen for migration")
                .since("2026-04-01")
                .disabledActions(List.of("create", "update"))
                .allowedRoles(List.of("admin"))
                .build();

        assertRoundTrip(original, InternalApiMetadata.class);
    }

    @Test
    @DisplayName("SystemFieldsMetadata round-trips (defaults factory)")
    void systemFieldsMetadataRoundTrips() {
        assertRoundTrip(SystemFieldsMetadata.defaults(), SystemFieldsMetadata.class);
    }

    @Test
    @DisplayName("EnumMetadata round-trips with values")
    void enumMetadataRoundTrips() {
        EnumMetadata original = new EnumMetadata(
                "OrderStatus", "com.acme.domain.OrderStatus", "com.acme.domain", "Order lifecycle states",
                List.of(
                        new EnumMetadata.EnumValueMetadata("PENDING", "Pending", "Awaiting confirmation", 0),
                        new EnumMetadata.EnumValueMetadata("SHIPPED", "Shipped", null, 1)));

        assertRoundTrip(original, EnumMetadata.class);
    }

    @Test
    @DisplayName("ProvidesMetadata round-trips versioned and unversioned")
    void providesMetadataRoundTrips() {
        assertRoundTrip(ProvidesMetadata.of("com.acme.gw.RouteRegistry", "1.0.0"), ProvidesMetadata.class);
        // unversioned: version is null and dropped by NON_NULL, read back as null
        assertRoundTrip(ProvidesMetadata.of("com.acme.gw.RouteRegistry"), ProvidesMetadata.class);
        // NON_NULL does not drop blank strings — the AST preserves what it is given
        // (the caller maps blank -> null before constructing; see Slice 3 reader)
        assertRoundTrip(ProvidesMetadata.of("com.acme.gw.RouteRegistry", "  "), ProvidesMetadata.class);
    }

    @Test
    @DisplayName("RequiresMetadata round-trips (NON_DEFAULT drops optional=false)")
    void requiresMetadataRoundTrips() {
        assertRoundTrip(RequiresMetadata.optional("com.acme.gw.UpstreamPool", "[1.0.0,2.0.0)"), RequiresMetadata.class);
        // minimal: versionRange null and optional false both drop, read back as (service, null, false)
        assertRoundTrip(RequiresMetadata.of("KERNEL_TRANSPORT"), RequiresMetadata.class);
    }

    @Test
    @DisplayName("CapabilityModuleMetadata round-trips with provides, requires, lifecycleOwner")
    void capabilityModuleMetadataRoundTrips() {
        CapabilityModuleMetadata original = CapabilityModuleMetadata.builder()
                .provides(List.of(
                        ProvidesMetadata.of("com.acme.gw.RouteRegistry", "1.0.0"),
                        ProvidesMetadata.of("com.acme.gw.BackendHealthMonitor")))
                .requires(List.of(
                        RequiresMetadata.of("com.acme.kernel.KernelTransport"),
                        new RequiresMetadata("com.acme.obs.MetricsSink", null, true)))
                .lifecycleOwner("com.acme.gw.GatewayLifecycle")
                .build();

        assertRoundTrip(original, CapabilityModuleMetadata.class);
        // empty module: lists serialize as [], lifecycleOwner null/dropped
        assertRoundTrip(CapabilityModuleMetadata.empty(), CapabilityModuleMetadata.class);
    }

    private <T> void assertRoundTrip(T original, Class<T> type) {
        String json;
        T parsed;
        try {
            json = mapper.writeValueAsString(original);
            parsed = mapper.readValue(json, type);
        } catch (Exception e) {
            throw new AssertionError("round-trip failed for " + type.getSimpleName() + ": " + e.getMessage(), e);
        }

        assertThat(parsed)
                .as("round-tripped %s should match original (json=%s)", type.getSimpleName(), json)
                .usingRecursiveComparison()
                .isEqualTo(original);
    }
}
