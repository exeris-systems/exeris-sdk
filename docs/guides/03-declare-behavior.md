---
title: Declare behavior
type: howto
visibility: public
owning-repo: exeris-sdk
status: active
last-verified: 2026-10-29
---

# Declare behavior

**Audience:** you are adding domain events and a saga to an entity and need to know which declarations the reader carries.

**Goal:** declare three events and a three-step saga on one entity, and check what reaches the metadata.

> **Verified against** `0.13.0-SNAPSHOT` at commit `33574011`, 2026-10-29, on `main`. Snippets are quoted or adapted from the cited class, by path and symbol. If a snippet and its source disagree, **the source wins and this guide is the defect**.

## Prerequisites

- [01 Platform and dependencies](01-platform-and-dependencies.md) done, and an entity as in [02 Model a domain](02-model-a-domain.md).
- The `ordering` example: `exeris-sdk-examples/src/main/java/eu/exeris/sdk/examples/ordering/Order.java`.

## Steps

### 1. Declare events with a trigger

`@DomainEvent` is repeatable. Each one names its trigger and its topic. This example uses `CREATE`, `ACTION` (with the action name) and `FIELD_CHANGED` (with the field name). `includeFields` lists the payload, and `excludeFields` removes names from it.

Source: `exeris-sdk-examples/src/main/java/eu/exeris/sdk/examples/ordering/Order.java`, the class-level `@DomainEvent` annotations (quoted).

```java
@DomainEvent(name = "OrderPlaced", trigger = DomainEvent.Trigger.CREATE,
        topic = "ordering.order.placed",
        includeFields = {"number", "status", "totalCents"})
@DomainEvent(name = "OrderCancelled", trigger = DomainEvent.Trigger.ACTION, action = "cancel",
        topic = "ordering.order.cancelled")
@DomainEvent(name = "OrderStatusChanged", trigger = DomainEvent.Trigger.FIELD_CHANGED, field = "status",
        topic = "ordering.order.status-changed",
        excludeFields = {"subtotalCents", "discountCents"})
```

The reader resolves `payloadFields` for you, from the fields that carry `@Field`. `OrderPlaced` carries the three included names. `OrderStatusChanged` carries every `@Field` field except the two excluded ones. `OrderCancelled` lists no filter, so it carries every `@Field` field. `id` carries no `@Field` and is in none of them.

### 2. Declare the saga

`@Saga` names the process, its timeout, its retry budget and its trigger. Here the trigger declares that an event on the topic of `OrderPlaced` starts it.

Source: `exeris-sdk-examples/src/main/java/eu/exeris/sdk/examples/ordering/Order.java`, the class-level `@Saga` annotation (quoted).

```java
@Saga(name = "OrderFulfilment",
        description = "Charges the customer, reserves stock and books the shipment",
        trigger = @Saga.SagaTrigger(type = Saga.SagaTrigger.TriggerType.EVENT, topic = "ordering.order.placed"),
        timeout = "PT30M",
        maxRetries = 2,
        compensationTimeout = "PT15M")
```

`Saga.trigger` and `Saga.compensationTimeout` are PARTIAL: the reader carries them, no build-time processor extracts them, and no generator reads them, so they change no generated artifact. The tier of each annotation is in the [stability matrix](../stability-matrix.md).

### 3. Declare steps and name their compensation

A step is a method marked `@SagaStep`. The reader sorts steps by `order` and carries `service`, `command`, `timeout` and `compensation`. `compensation` names a plain method declared on the same class.

Source: `exeris-sdk-examples/src/main/java/eu/exeris/sdk/examples/ordering/Order.java`, the `capturePayment` and `scheduleShipment` methods (adapted — Javadoc removed).

```java
@SagaStep(order = 1, name = "capturePayment", service = "payments", command = "CapturePayment",
        compensation = "refundPayment", timeout = "PT1M")
public void capturePayment() {
}

@SagaStep(order = 3, name = "scheduleShipment", service = "shipping", command = "ScheduleShipment")
public void scheduleShipment() {
}
```

`refundPayment` is an ordinary method on `Order`. The last step has no compensation, so `hasCompensation()` is `false` for it. A step that cannot be undone says so by leaving the attribute out.

`SagaStep.compensation` is PARTIAL. The Java kernel generator reads only whether it is set, and registers a generated stub for the step, so the method you name is not yet called. Write the method under the name you declare. The `SagaStep` Javadoc has the detail.

### 4. Check what the reader carries

| Declaration | Reaches `DomainMetadata` |
|---|---|
| Three `@DomainEvent` annotations | Yes: trigger, topic, `aggregateType` and resolved `payloadFields` |
| `@Saga` name, description, timeout, retries, compensation timeout, `EVENT` trigger | Yes |
| Three `@SagaStep` annotations | Yes, in `order`, with service, command, timeout and compensation |
| `@SagaTransition` | No: `transitions()` is empty |
| `@Rule` | No: `rules()` is empty |
| `@Derived` | No: `FieldMetadata.derived()` is `null` |

Source: `exeris-sdk-examples/src/test/java/eu/exeris/sdk/examples/ordering/OrderingExampleTest.java`, the `reservedSurfacesAreNotRead` test (quoted).

```java
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
```

The last assertion is the one to read twice. `unmodeledFacets` names `@Channel`, `@Schedule` and a few values it cannot resolve. `@SagaTransition`, `@Rule` and `@Derived` are absent from the model and are not reported, so an empty result does not mean they are modeled.

## Verify

```bash
mvn -pl exeris-sdk-examples -am verify
```

Run it from the repository root. Expect `BUILD SUCCESS`. `OrderingExampleTest` asserts the events, the saga, the step order and the compensation names, and pins the three reserved surfaces as absent.

## Not available today

- **`@SagaTransition`, `@Rule`, `@Derived`.** They compile and document intent. No reader extracts them.
- **`@EventHandler` and `@Projection`.** Both are RESERVED. The example does not use them, and the reader does not read them. Their tier is in the [stability matrix](../stability-matrix.md), and the freeze ruling is in [MIGRATION section 2](../../MIGRATION-0.x-to-1.0.md#2-what-is-deliberately-not-removed-carried-through-1x).
- **Four compensation attributes of `@SagaStep`.** `compensationBuilder`, `compensationClass`, `compensationPayloadExpression` and `compensationService` are removed at 1.0.0. Name the method in `compensation` instead. See [MIGRATION section 1](../../MIGRATION-0.x-to-1.0.md#1-removals-landing-at-100).

## Where this does not apply

- **Executing the saga.** The SDK describes the process and does not run it.
- **Trade-off you accept.** Declaring a reserved annotation records intent in review and in source, and no tool acts on it today. A reader of the source cannot tell the live annotations from the reserved ones without the matrix.
- **Entities without events.** Guide [02](02-model-a-domain.md) is enough for them.
