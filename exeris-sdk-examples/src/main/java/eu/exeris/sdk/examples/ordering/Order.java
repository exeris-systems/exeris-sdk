/*
 * Copyright (C) 2026 Exeris Systems.
 * SPDX-License-Identifier: Apache-2.0
 */
package eu.exeris.sdk.examples.ordering;

import eu.exeris.sdk.annotation.Action;
import eu.exeris.sdk.annotation.Derived;
import eu.exeris.sdk.annotation.DomainEvent;
import eu.exeris.sdk.annotation.ExerisDomain;
import eu.exeris.sdk.annotation.Field;
import eu.exeris.sdk.annotation.Rule;
import eu.exeris.sdk.annotation.Saga;
import eu.exeris.sdk.annotation.SagaStep;
import eu.exeris.sdk.annotation.SagaTransition;
import eu.exeris.sdk.annotation.system.PrimaryKey;

import java.util.UUID;

/**
 * A customer purchase that publishes domain events and is fulfilled by a saga.
 *
 * <p>{@code @DomainEvent}, {@code @Saga} and {@code @SagaStep} are LIVE: the source model reader
 * carries them. {@code @SagaTransition}, {@code @Rule} and {@code @Derived} are RESERVED: they
 * compile and document intent, and no reader extracts them yet. {@code Saga.trigger},
 * {@code Saga.compensationTimeout}, {@code SagaStep.compensation} and {@code @PrimaryKey} are
 * PARTIAL: the reader carries them and the build-time processor reads them in part or not yet.
 */
@ExerisDomain(module = "ordering", path = "/orders", description = "A customer purchase")
@DomainEvent(name = "OrderPlaced", trigger = DomainEvent.Trigger.CREATE,
        topic = "ordering.order.placed",
        includeFields = {"number", "status", "totalCents"})
@DomainEvent(name = "OrderCancelled", trigger = DomainEvent.Trigger.ACTION, action = "cancel",
        topic = "ordering.order.cancelled")
@DomainEvent(name = "OrderStatusChanged", trigger = DomainEvent.Trigger.FIELD_CHANGED, field = "status",
        topic = "ordering.order.status-changed",
        excludeFields = {"subtotalCents", "discountCents"})
@Saga(name = "OrderFulfilment",
        description = "Charges the customer, reserves stock and books the shipment",
        trigger = @Saga.SagaTrigger(type = Saga.SagaTrigger.TriggerType.EVENT, topic = "ordering.order.placed"),
        timeout = "PT30M",
        maxRetries = 2,
        compensationTimeout = "PT15M")
@SagaTransition(from = "capturePayment", to = "reserveStock")
@SagaTransition(from = "reserveStock", to = "scheduleShipment")
@Rule(name = "discountWithinSubtotal",
        expression = "discountCents <= subtotalCents",
        message = "A discount cannot exceed the subtotal")
public class Order {

    @PrimaryKey
    private UUID id;

    @Field(label = "Number", required = true, inCreate = false, unique = true, readOnly = true)
    private String number;

    @Field(label = "Status", readOnly = true)
    private String status;

    @Field(label = "Subtotal (cents)", required = true)
    private long subtotalCents;

    @Field(label = "Discount (cents)")
    private long discountCents;

    @Field(label = "Total (cents)", readOnly = true)
    @Derived(expression = "subtotalCents - discountCents", dependsOn = {"subtotalCents", "discountCents"})
    private long totalCents;

    /** Cancels an order that has not shipped; publishes {@code OrderCancelled}. */
    @Action(name = "cancel", label = "Cancel")
    public void cancel() {
    }

    /** Takes payment for the order. */
    @SagaStep(order = 1, name = "capturePayment", service = "payments", command = "CapturePayment",
            compensation = "refundPayment", timeout = "PT1M")
    public void capturePayment() {
    }

    /** Holds the ordered items in the warehouse. */
    @SagaStep(order = 2, name = "reserveStock", service = "inventory", command = "ReserveStock",
            compensation = "releaseStock")
    public void reserveStock() {
    }

    /** Books the carrier. It has no compensation: a booked shipment is cancelled by the carrier. */
    @SagaStep(order = 3, name = "scheduleShipment", service = "shipping", command = "ScheduleShipment")
    public void scheduleShipment() {
    }

    /** Undoes {@code capturePayment} when a later step fails. */
    public void refundPayment() {
    }

    /** Undoes {@code reserveStock} when a later step fails. */
    public void releaseStock() {
    }
}
