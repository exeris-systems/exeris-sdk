/*
 * Copyright (C) 2026 Exeris Systems.
 * SPDX-License-Identifier: Apache-2.0
 */
package eu.exeris.sdk.examples.ordering;

import eu.exeris.sdk.sourcemodel.ast.DomainEventMetadata;
import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.FieldMetadata;
import eu.exeris.sdk.sourcemodel.ast.SagaMetadata;
import eu.exeris.sdk.sourcemodel.ast.SagaStepMetadata;
import eu.exeris.sdk.sourcemodel.io.SourceModelReader;
import eu.exeris.sdk.tck.TckMappers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reads the ordering example source exactly as the documentation quotes it and asserts the
 * metadata it declares. {@code @SagaTransition}, {@code @Rule} and {@code @Derived} are reserved
 * surfaces the reader does not extract, so the tests pin their absence from the read model.
 */
@DisplayName("examples: ordering")
class OrderingExampleTest {

    private static final Path ORDER = Path.of("src/main/java/eu/exeris/sdk/examples/ordering/Order.java");

    private final SourceModelReader reader = new SourceModelReader();

    private String source() throws IOException {
        return Files.readString(ORDER);
    }

    private DomainMetadata order() throws IOException {
        return reader.read(source()).orElseThrow();
    }

    private static DomainEventMetadata event(DomainMetadata domain, String name) {
        return domain.events().stream()
                .filter(e -> name.equals(e.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no event " + name));
    }

    @Test
    @DisplayName("each event carries its trigger, topic and resolved payload")
    void domainEvents() throws IOException {
        DomainMetadata order = order();

        assertThat(order.events()).extracting(DomainEventMetadata::name)
                .containsExactly("OrderPlaced", "OrderCancelled", "OrderStatusChanged");

        DomainEventMetadata placed = event(order, "OrderPlaced");
        assertThat(placed.trigger()).isEqualTo(DomainEventMetadata.Trigger.CREATE);
        assertThat(placed.topic()).isEqualTo("ordering.order.placed");
        assertThat(placed.aggregateType()).isEqualTo("Order");
        assertThat(placed.payloadFields()).containsExactly("number", "status", "totalCents");

        DomainEventMetadata cancelled = event(order, "OrderCancelled");
        assertThat(cancelled.trigger()).isEqualTo(DomainEventMetadata.Trigger.ACTION);
        assertThat(cancelled.actionName()).isEqualTo("cancel");
        assertThat(cancelled.payloadFields())
                .containsExactly("number", "status", "subtotalCents", "discountCents", "totalCents");

        DomainEventMetadata changed = event(order, "OrderStatusChanged");
        assertThat(changed.trigger()).isEqualTo(DomainEventMetadata.Trigger.FIELD_CHANGED);
        assertThat(changed.fieldName()).isEqualTo("status");
        assertThat(changed.payloadFields()).containsExactly("number", "status", "totalCents");
    }

    @Test
    @DisplayName("the saga carries its trigger and its steps in execution order")
    void saga() throws IOException {
        SagaMetadata saga = order().sagaMetadata();

        assertThat(saga.name()).isEqualTo("OrderFulfilment");
        assertThat(saga.description()).isEqualTo("Charges the customer, reserves stock and books the shipment");
        assertThat(saga.timeout()).isEqualTo("PT30M");
        assertThat(saga.maxRetries()).isEqualTo(2);
        assertThat(saga.compensationTimeout()).isEqualTo("PT15M");
        assertThat(saga.trigger()).isEqualTo(new SagaMetadata.SagaTrigger(
                SagaMetadata.TriggerType.EVENT, null, "ordering.order.placed", null, null));

        assertThat(saga.steps()).extracting(SagaStepMetadata::name)
                .containsExactly("capturePayment", "reserveStock", "scheduleShipment");
        assertThat(saga.steps()).extracting(SagaStepMetadata::order).containsExactly(1, 2, 3);
        assertThat(saga.steps()).extracting(SagaStepMetadata::service)
                .containsExactly("payments", "inventory", "shipping");
        assertThat(saga.steps()).extracting(SagaStepMetadata::command)
                .containsExactly("CapturePayment", "ReserveStock", "ScheduleShipment");
        assertThat(saga.steps().getFirst().timeout()).isEqualTo("PT1M");
    }

    @Test
    @DisplayName("a step names the method that undoes it; the last step has none")
    void compensation() throws IOException {
        var steps = order().sagaMetadata().steps();

        assertThat(steps.get(0).compensation()).isEqualTo("refundPayment");
        assertThat(steps.get(0).hasCompensation()).isTrue();
        assertThat(steps.get(1).compensation()).isEqualTo("releaseStock");
        assertThat(steps.get(2).hasCompensation()).isFalse();

        // The names resolve to methods declared on the saga type.
        assertThat(source()).contains("public void refundPayment()", "public void releaseStock()");
    }

    @Test
    @DisplayName("reserved @SagaTransition, @Rule and @Derived are declared but not read")
    void reservedSurfacesAreNotRead() throws IOException {
        DomainMetadata order = order();

        assertThat(order.sagaMetadata().transitions()).isEmpty();
        assertThat(order.rules()).isEmpty();
        FieldMetadata total = order.fields().stream()
                .filter(f -> "totalCents".equals(f.name())).findFirst().orElseThrow();
        assertThat(total.derived()).isNull();
        assertThat(total.hasDerived()).isFalse();

        // The guard names only the annotations it knows it drops; these three are absent
        // from the read model without being reported.
        assertThat(reader.unmodeledFacets(source())).isEmpty();
    }

    @Test
    @DisplayName("the metadata survives a JSON round trip")
    void jsonRoundTrip() throws IOException {
        ObjectMapper mapper = TckMappers.canonical();
        DomainMetadata order = order();

        String json = mapper.writeValueAsString(order);

        assertThat(mapper.readValue(json, DomainMetadata.class)).isEqualTo(order);
    }
}
