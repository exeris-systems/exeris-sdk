package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonInclude;
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
                .dataScope(DataScope.TENANT)
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
                .rules(List.of(
                        RuleMetadata.of("hasLineItems", "lineItems.size() > 0"),
                        new RuleMetadata("discountNeedsApproval",
                                "discount <= 0 or approvedBy != null",
                                "order.rule.discountNeedsApproval", "WARN", "spel")))
                .build();

        assertRoundTrip(original, DomainMetadata.class);
    }

    @Test
    @DisplayName("DomainMetadata.dataScope survives the wire for every tier, including GLOBAL")
    void dataScopeRoundTripsForEveryTier() {
        // DomainMetadata is class-level @JsonInclude(NON_NULL) — the deliberate
        // ViewMetadata posture — so a non-null tier is not at risk today, and the
        // ordinal-0 / boxed-zero trap that cost the FieldMetadata bounds a fix in
        // 0.9.0 is NOT armed here. Every tier is still pinned because the cost of
        // that changing is asymmetric: under NON_DEFAULT it is GLOBAL (ordinal 0)
        // that drops, and a dropped explicit GLOBAL is not a lost hint — it falls
        // back through effectiveDataScope() and reads the entity back as TENANT.
        // A silent tenancy flip is exactly what the tier that looks least
        // interesting would cause, so it is the one worth asserting.
        for (DataScope scope : DataScope.values()) {
            DomainMetadata original = DomainMetadata.builder("Order", "com.acme.domain")
                    .tenantScoped(true)
                    .dataScope(scope)
                    .build();

            assertRoundTrip(original, DomainMetadata.class);
        }
    }

    @Test
    @DisplayName("DomainMetadata with no dataScope reads back absent, not defaulted")
    void absentDataScopeStaysAbsentOnTheWire() {
        // A pre-0.10.0 baseline carries only tenantScoped. The absent tier must
        // stay absent so effectiveDataScope() applies the documented fallback,
        // rather than being materialised into a concrete tier by the reader.
        DomainMetadata original = DomainMetadata.builder("Order", "com.acme.domain")
                .tenantScoped(true)
                .build();

        assertRoundTrip(original, DomainMetadata.class);

        String json;
        DomainMetadata parsed;
        try {
            json = mapper.writeValueAsString(original);
            parsed = mapper.readValue(json, DomainMetadata.class);
        } catch (Exception e) {
            throw new AssertionError("round-trip failed: " + e.getMessage(), e);
        }

        assertThat(json).doesNotContain("dataScope");
        assertThat(parsed.dataScope()).isNull();
        assertThat(parsed.effectiveDataScope()).isEqualTo(DataScope.TENANT);
    }

    @Test
    @DisplayName("FieldMetadata round-trips (NON_DEFAULT inclusion)")
    void fieldMetadataRoundTrips() {
        // Bounds may be any value incl. 0 since 0.9.0 (per-component NON_NULL);
        // the zero-bound wire contract is pinned in fieldMetadataZeroBoundsRoundTrip.
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
                .derived(DerivedMetadata.of("unitPrice * quantity", List.of("unitPrice", "quantity")))
                .build();

        assertRoundTrip(original, FieldMetadata.class);
    }

    @Test
    @DisplayName("FieldMetadata zero-valued bounds survive the wire (0.9.0, per-component NON_NULL)")
    void fieldMetadataZeroBoundsRoundTrip() {
        // min = 0 is a legitimate non-negativity floor. Under the pre-0.9.0
        // class-level NON_DEFAULT, Jackson 3 treated boxed zero as "empty" and
        // silently dropped all four bound keys; per-component NON_NULL keeps
        // them. The deep-equality round trip alone would catch the drop
        // (null != 0), but the JSON-key assertions document the wire contract.
        FieldMetadata original = FieldMetadata.builder("amount", "Long")
                .min(0L)
                .max(0L)
                .minLength(0)
                .maxLength(0)
                .build();

        String json = mapper.writeValueAsString(original);
        assertThat(json)
                .contains("\"min\":0")
                .contains("\"max\":0")
                .contains("\"minLength\":0")
                .contains("\"maxLength\":0");

        assertRoundTrip(original, FieldMetadata.class);
    }

    @Test
    @DisplayName("FieldMetadata absent bounds stay absent (NON_NULL drops null, not zero)")
    void fieldMetadataAbsentBoundsStayAbsent() {
        // NON_NULL only rescues zero — it must not start emitting null-valued
        // keys for unset bounds ("no bound" stays absent on the wire).
        String json = mapper.writeValueAsString(FieldMetadata.simple("notes", "String"));
        assertThat(json)
                .doesNotContain("\"min\"")
                .doesNotContain("\"max\"")
                .doesNotContain("\"minLength\"")
                .doesNotContain("\"maxLength\"");
    }

    @Test
    @DisplayName("DerivedMetadata round-trips (full + minimal)")
    void derivedMetadataRoundTrips() {
        // Full: explicit language + cross-aggregate dependency path.
        assertRoundTrip(new DerivedMetadata("customer.tier == 'GOLD' ? 0.1 : 0", "jexl",
                List.of("customer.tier")), DerivedMetadata.class);
        // Minimal: default language (null on the wire), no dependencies.
        assertRoundTrip(DerivedMetadata.of("unitPrice * quantity"), DerivedMetadata.class);
    }

    @Test
    @DisplayName("RuleMetadata round-trips (full + minimal)")
    void ruleMetadataRoundTrips() {
        // Full: message + non-default severity + explicit language.
        assertRoundTrip(new RuleMetadata("discountNeedsApproval",
                "discount <= 0 or approvedBy != null", "order.rule.discountNeedsApproval",
                "WARN", "spel"), RuleMetadata.class);
        // Minimal: default severity / language (null on the wire).
        assertRoundTrip(RuleMetadata.of("hasLineItems", "lineItems.size() > 0"), RuleMetadata.class);
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
                .methodName("doShip")
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
    @DisplayName("ActionMetadata round-trips streaming fields (0.8.0, NON_DEFAULT inclusion)")
    void streamingActionMetadataRoundTrips() {
        ActionMetadata original = ActionMetadata.builder("generate-report")
                .description("Stream report progress")
                .streaming(true)
                .streamEventType("ReportProgress")
                .realTimeUpdates(true)
                .build();

        assertRoundTrip(original, ActionMetadata.class);
    }

    @Test
    @DisplayName("NON_DEFAULT drops boxed zero, but NOT an ordinal-0 enum — measured, not assumed")
    void nonDefaultDropsBoxedZeroNotOrdinalZeroEnums() {
        // This pins the actual Jackson 3 semantics that several inclusion choices
        // in this package are justified by, because one of those justifications
        // was wrong and nothing checked it. The repo-wide caveat is correct as
        // CLAUDE.md states it — NON_DEFAULT treats a boxed numeric zero as
        // "empty" and drops it, which is what cost the FieldMetadata bounds a fix
        // in 0.9.0. The extension of that to enums is not: an ordinal-0 constant
        // is not "empty" to Jackson and survives NON_DEFAULT untouched. Measured
        // here so the next inclusion decision reasons from the behaviour rather
        // than from the folklore.
        String json;
        try {
            json = mapper.writeValueAsString(new InclusionProbe(Probe.FIRST, 0L, "", false));
        } catch (Exception e) {
            throw new AssertionError("serialization failed: " + e.getMessage(), e);
        }

        assertThat(json).as("ordinal-0 enum survives NON_DEFAULT").contains("FIRST");
        assertThat(json).as("boxed zero is dropped by NON_DEFAULT").doesNotContain("bound");
        assertThat(json).as("empty string is dropped by NON_DEFAULT").doesNotContain("text");
        assertThat(json).as("false is dropped by NON_DEFAULT").doesNotContain("flag");
    }

    private enum Probe { FIRST, SECOND }

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private record InclusionProbe(Probe kind, Long bound, String text, boolean flag) {}

    @Test
    @DisplayName("ScheduleMetadata round-trips every trigger kind, CRON included")
    void scheduleMetadataRoundTripsForEveryKind() {
        for (ScheduleMetadata.TriggerKind kind : ScheduleMetadata.TriggerKind.values()) {
            assertRoundTrip(new ScheduleMetadata(kind, "0 3 * * *"), ScheduleMetadata.class);
        }
    }

    @Test
    @DisplayName("an explicit CRON survives the wire (ordinal-0 enum)")
    void cronTriggerKindSurvivesTheWire() {
        // CRON is ordinal 0. See nonDefaultDropsBoxedZeroNotOrdinalZeroEnums below
        // for why that is *not* the hazard it is sometimes assumed to be — this
        // case pins the outcome that matters here regardless of the inclusion
        // posture: a schedule that round-trips must come back as the same trigger.
        ScheduleMetadata original = ScheduleMetadata.cron("0 3 * * *");

        String json;
        ScheduleMetadata parsed;
        try {
            json = mapper.writeValueAsString(original);
            parsed = mapper.readValue(json, ScheduleMetadata.class);
        } catch (Exception e) {
            throw new AssertionError("round-trip failed: " + e.getMessage(), e);
        }

        assertThat(json).contains("CRON");
        assertThat(parsed.kind()).isEqualTo(ScheduleMetadata.TriggerKind.CRON);
        assertThat(parsed.isRecurring()).isTrue();
    }

    @Test
    @DisplayName("ActionMetadata round-trips a schedule facet (0.11.0)")
    void scheduledActionMetadataRoundTrips() {
        assertRoundTrip(ActionMetadata.builder("reconcile")
                .description("Nightly reconciliation")
                .methodName("reconcile")
                .schedule(ScheduleMetadata.cron("0 3 * * *"))
                .build(), ActionMetadata.class);

        assertRoundTrip(ActionMetadata.builder("refreshQuotes")
                .schedule(ScheduleMetadata.every("PT15M"))
                .build(), ActionMetadata.class);
    }

    @Test
    @DisplayName("BlobMetadata round-trips (full + minimal)")
    void blobMetadataRoundTrips() {
        // Full: explicit tenant-relative container + a narrowed media-type set.
        assertRoundTrip(new BlobMetadata("statements", List.of("application/pdf", "image/png")),
                BlobMetadata.class);
        // Minimal: derived container (null on the wire), unrestricted types.
        assertRoundTrip(BlobMetadata.unrestricted(), BlobMetadata.class);
    }

    @Test
    @DisplayName("FieldMetadata round-trips a blob facet (0.11.0)")
    void blobFieldMetadataRoundTrips() {
        assertRoundTrip(FieldMetadata.builder("statement", "BlobRef")
                .displayName("Statement PDF")
                .blob(BlobMetadata.ofContentTypes(List.of("application/pdf")))
                .build(), FieldMetadata.class);
    }

    @Test
    @DisplayName("an absent schedule / blob facet stays absent on the wire")
    void absentReservedFacetsStayAbsent() {
        // The common case: neither facet declared. Both carriers are nullable and
        // must not be materialised into empty objects by the reader — an empty
        // ScheduleMetadata is not even constructible (kind and expression are
        // required), so a reader that invented one would fail loudly rather than
        // quietly, but the wire must not carry the key in the first place.
        String actionJson;
        String fieldJson;
        try {
            actionJson = mapper.writeValueAsString(ActionMetadata.simple("ship"));
            fieldJson = mapper.writeValueAsString(FieldMetadata.simple("total", "BigDecimal"));
        } catch (Exception e) {
            throw new AssertionError("serialization failed: " + e.getMessage(), e);
        }

        assertThat(actionJson).doesNotContain("schedule");
        assertThat(fieldJson).doesNotContain("blob");
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
    @DisplayName("DomainEventMetadata round-trips (full payload + empty/minimal)")
    void domainEventMetadataRoundTrips() {
        // Full: EV1 resolved payload field list + sensitive-field subset.
        DomainEventMetadata full = DomainEventMetadata.builder("OrderShipped")
                .topic("orders.shipped")
                .description("Order has shipped")
                .aggregateType("Order")
                .payloadFields(List.of("orderNumber", "amount", "customerEmail"))
                .sensitiveFields(List.of("customerEmail"))
                .build();
        assertRoundTrip(full, DomainEventMetadata.class);

        // Empty/minimal: the pre-EV1 4-tuple shape still round-trips. Both EV1
        // lists default to empty (the compact constructor normalizes null ->
        // List.of()); empty lists serialize as [] and read back as empty, so the
        // deep-equality holds.
        DomainEventMetadata minimal = new DomainEventMetadata(
                "OrderCreated", "orders.created", "Order created", "Order");
        assertThat(minimal.payloadFields()).isEmpty();
        assertThat(minimal.sensitiveFields()).isEmpty();
        // Pin the wire shape: under class-level @JsonInclude(NON_NULL) the
        // normalized-empty lists emit [] (NOT absent — NON_NULL only drops null),
        // matching DomainMetadata's builder-built list members. Machine-checked so
        // the prose above can't silently drift to "keys are absent".
        String minimalJson = mapper.writeValueAsString(minimal);
        assertThat(minimalJson).contains("\"payloadFields\":[]");
        assertThat(minimalJson).contains("\"sensitiveFields\":[]");
        assertRoundTrip(minimal, DomainEventMetadata.class);
    }

    @Test
    @DisplayName("EV2: the trigger triple round-trips, and an absent trigger stays absent "
            + "rather than defaulting to CREATE")
    void domainEventTriggerRoundTrips() throws Exception {
        DomainEventMetadata triggered = DomainEventMetadata.builder("OrderPlaced")
                .topic("orders.created")
                .aggregateType("Order")
                .trigger(DomainEventMetadata.Trigger.CREATE)
                .build();
        assertThat(triggered.hasTrigger()).isTrue();
        assertRoundTrip(triggered, DomainEventMetadata.class);

        DomainEventMetadata onAction = DomainEventMetadata.builder("OrderCancelledEvent")
                .trigger(DomainEventMetadata.Trigger.ACTION)
                .actionName("cancel")
                .build();
        assertRoundTrip(onAction, DomainEventMetadata.class);

        DomainEventMetadata onFieldChange = DomainEventMetadata.builder("OrderStatusChangedEvent")
                .trigger(DomainEventMetadata.Trigger.FIELD_CHANGED)
                .fieldName("status")
                .build();
        assertRoundTrip(onFieldChange, DomainEventMetadata.class);

        // The load-bearing negative. A pre-EV2 baseline carries no trigger, and it must read
        // back as "unknown" rather than as CREATE — a generator keys a publish call off this,
        // so a silent default would attach create-time publishing to every legacy event.
        DomainEventMetadata preEv2 = new DomainEventMetadata(
                "OrderCreated", "orders.created", "Order created", "Order");
        assertThat(preEv2.trigger()).isNull();
        assertThat(preEv2.hasTrigger()).isFalse();
        assertThat(preEv2.actionName()).isNull();
        assertThat(preEv2.fieldName()).isNull();
        assertRoundTrip(preEv2, DomainEventMetadata.class);

        // ...and the wire form omits the keys entirely (class-level @JsonInclude(NON_NULL)),
        // so an older reader sees no new fields at all.
        String preEv2Json = mapper.writeValueAsString(preEv2);
        assertThat(preEv2Json).doesNotContain("trigger");
        assertThat(preEv2Json).doesNotContain("actionName");
        assertThat(preEv2Json).doesNotContain("fieldName");

        // The EV1 6-arg constructor keeps working and also leaves the triple unset.
        DomainEventMetadata ev1 = new DomainEventMetadata(
                "OrderShipped", null, null, "Order", List.of("amount"), List.of());
        assertThat(ev1.hasTrigger()).isFalse();
        assertThat(ev1.payloadFields()).containsExactly("amount");
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
    @DisplayName("ProjectionMetadata round-trips (source + subscription + read model)")
    void projectionMetadataRoundTrips() {
        ProjectionMetadata original = ProjectionMetadata.builder("OrderSummary")
                .description("List view projection")
                .aggregateTypes(List.of("Order"))
                .events(List.of("OrderCreated", "OrderShipped"))
                .eventClassNames(List.of("com.acme.events.OrderCreatedEvent"))
                .topicPattern("orders\\..*")
                .model("OrderSummaryView")
                .schema("read_models")
                .fields(List.of("id", "orderNumber", "amount"))
                .cacheable(true)
                .build();
        assertRoundTrip(original, ProjectionMetadata.class);

        // The pre-0.7.0 minimal shape still round-trips (defaults: empty lists,
        // null strings dropped by NON_NULL).
        assertRoundTrip(ProjectionMetadata.simple("Bare", List.of("id")), ProjectionMetadata.class);
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
                .transitions(List.of(
                        SagaMetadata.SagaTransition.success("reserve", "charge"),
                        new SagaMetadata.SagaTransition("charge", "reserve",
                                SagaMetadata.TransitionOutcome.FAILURE, "state.retryable"),
                        SagaMetadata.SagaTransition.ofOutcome("charge", null,
                                SagaMetadata.TransitionOutcome.COMPENSATED)))
                .build();

        assertRoundTrip(original, SagaMetadata.class);
    }

    @Test
    @DisplayName("SagaTransition omits a normalized-away blank target on the wire")
    void sagaTransitionBlankTargetOmittedFromJson() {
        // Regression (PR #61): a blank `to` must normalize to null so the key is
        // absent on the wire (NON_NULL), not serialized as the whitespace string
        // "to": "   " that a downstream `to == null` check would miss.
        String json;
        try {
            json = mapper.writeValueAsString(SagaMetadata.SagaTransition.ofOutcome(
                    "charge", "   ", SagaMetadata.TransitionOutcome.COMPENSATED));
        } catch (Exception e) {
            throw new AssertionError("serialize failed: " + e.getMessage(), e);
        }
        assertThat(json).contains("\"from\"").doesNotContain("\"to\"");
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
                .kind(SagaStepMetadata.StepKind.AWAIT_EVENT)
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

    @Test
    @DisplayName("BindingMetadata round-trips each source flavour")
    void bindingMetadataRoundTrips() {
        assertRoundTrip(BindingMetadata.none(), BindingMetadata.class);
        assertRoundTrip(BindingMetadata.entity("Order", "amount"), BindingMetadata.class);
        assertRoundTrip(BindingMetadata.projection("OrderSummary", "total"), BindingMetadata.class);
        assertRoundTrip(BindingMetadata.action("placeOrder"), BindingMetadata.class);
        assertRoundTrip(
                new BindingMetadata(BindSource.STATIC, null, null, "#now()", "spel"),
                BindingMetadata.class);
    }

    @Test
    @DisplayName("ComponentNodeMetadata leaf round-trips with binding")
    void componentNodeLeafRoundTrips() {
        assertRoundTrip(
                ComponentNodeMetadata.leaf(BlockType.RICH_TEXT, BindingMetadata.staticBinding()),
                ComponentNodeMetadata.class);
    }

    @Test
    @DisplayName("ComponentNodeMetadata round-trips a NESTED tree (>=2 levels of non-empty children)")
    void componentNodeNestedRoundTrips() {
        // grid > card > [list(entity), nav-leaf] — proves the recursive children
        // serialize/deserialize beyond a flat leaf.
        ComponentNodeMetadata deepChild =
                ComponentNodeMetadata.leaf(BlockType.LIST, BindingMetadata.entity("Order", "lines"));
        ComponentNodeMetadata navLeaf =
                ComponentNodeMetadata.leaf(BlockType.NAV, BindingMetadata.none());
        ComponentNodeMetadata card =
                ComponentNodeMetadata.container(BlockType.CARD, List.of(deepChild, navLeaf));
        ComponentNodeMetadata grid =
                ComponentNodeMetadata.container(BlockType.GRID, List.of(card));

        assertThat(grid.hasChildren()).isTrue();
        assertThat(grid.children().getFirst().hasChildren()).isTrue();
        assertRoundTrip(grid, ComponentNodeMetadata.class);
    }

    @Test
    @DisplayName("ComponentNodeMetadata round-trips a leaf field-render facet (the @UI successor)")
    void componentNodeFieldFacetRoundTrips() {
        ComponentNodeMetadata fieldLeaf = ComponentNodeMetadata.fieldLeaf(
                BlockType.FORM,
                BindingMetadata.entity("Product", "price"),
                UIMetadata.UIFieldMetadata.simple("price", UIMetadata.ComponentType.NUMBER_INPUT));
        assertThat(fieldLeaf.hasField()).isTrue();
        assertRoundTrip(fieldLeaf, ComponentNodeMetadata.class);
    }

    @Test
    @DisplayName("RegionMetadata round-trips with components")
    void regionMetadataRoundTrips() {
        assertRoundTrip(
                new RegionMetadata("main",
                        List.of(ComponentNodeMetadata.leaf(BlockType.HERO, BindingMetadata.none()))),
                RegionMetadata.class);
        // empty region: slot null, components []
        assertRoundTrip(new RegionMetadata(null, null), RegionMetadata.class);
    }

    @Test
    @DisplayName("ViewMetadata round-trips full view (>=2 regions, >=1 component each, a non-NONE binding)")
    void viewMetadataRoundTrips() {
        RegionMetadata header = new RegionMetadata("header",
                List.of(ComponentNodeMetadata.leaf(BlockType.HERO, BindingMetadata.staticBinding())));
        RegionMetadata body = new RegionMetadata("body",
                List.of(ComponentNodeMetadata.container(BlockType.GRID,
                        List.of(ComponentNodeMetadata.leaf(BlockType.LIST,
                                BindingMetadata.projection("ProductSummary", "items"))))));

        ViewMetadata original = ViewMetadata.builder("ProductLanding")
                .kind(ViewKind.PAGE)
                .route("/products")
                .title("Products")
                .titleKey("products.title")
                .layout("default-shell")
                .regions(List.of(header, body))
                .build();

        assertRoundTrip(original, ViewMetadata.class);
        // minimal: kind null/dropped, regions [], read back via effectiveKind default
        assertRoundTrip(ViewMetadata.builder("Bare").build(), ViewMetadata.class);
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
