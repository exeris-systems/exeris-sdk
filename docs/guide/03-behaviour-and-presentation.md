# 03 — Behaviour and presentation

← [Guide index](README.md) · [02 — Modelling an entity](02-modelling-an-entity.md)

This is where the gap between the declared surface and the working surface is
widest. `@Saga` alone declares 81 attributes and four of them are read. Nothing
below is broken; it is a model designed ahead of its generators. Read the status
labels and you will not be surprised.

## Events

### `@DomainEvent` — LIVE

`@Target(TYPE)`, repeatable via the nested `@DomainEvent.DomainEvents`
container. 58 declared attributes; **4 extracted**.

| Attribute | Status |
|---|---|
| `name` | LIVE |
| `trigger` | LIVE — defaults to `CREATE` |
| `topic` | LIVE |
| `description` | LIVE |

The other 54 are **RESERVED**. That includes everything a reader would assume is
load-bearing: `persistent`, `useOutbox`, `outboxTable`, `partitionKey`,
`orderingRequired`, `processingGuarantee`, `requiresIdempotency`,
`includeTenantContext`, `includeTraceContext`, `encryptFields`,
`sensitiveFields`, `excludeFields`, `retentionDays`, `schemaSubject`,
`registerSchema`, `compatibility`, and the rest. Delivery semantics are the
kernel's; the annotation declares intent that no emitter reads yet.

Repeating `@DomainEvent` works — the container is extracted.

### `@EventHandler` — RESERVED

21 attributes, no extraction. Note that a `KernelEventHandlerGenerator` does
exist and does emit handlers — it is driven by `@DomainEvent`, not by this
annotation.

### `@EventSourced` — RESERVED

57 attributes, no extraction. It is the one annotation the processor names in its
inert-annotation registry, so a build run with `-Aexeris.strict` warns about it
by name. That registry can only flag annotations that are *extracted and then
unread*, which is why it lists exactly one entry and not the twenty-eight other
reserved annotations — a structural limit of the check, not a clean bill of
health.

## Sagas

### `@Saga` — LIVE, narrowly

`@Target(TYPE)`. 81 declared attributes; **4 extracted**: `name`, `description`,
`timeout`, `maxRetries`. Those drive `KernelSagaGenerator` and its test scaffold.

The other 77 are **RESERVED** — the entire compensation policy surface
(`compensationStrategy`, `compensationOrder`, `compensationTimeout`,
`compensationMaxRetries`, `compensationDlq`, …), the retry surface
(`retryPolicy`, `retryBackoffMultiplier`, `retryJitter`, `retryOn`, `noRetryOn`,
…), persistence, idempotency, concurrency limits, alerting, and the lifecycle
hooks.

`version` needs its own note. It stopped being a free-form marker with kernel
0.11 (ADR-064): the kernel keys its plan catalog by `(name, version)`, resumes a
parked saga on the exact version it parked under, fails closed on an unregistered
one, and crosses versions only through a registered migration transform. The
attribute expressing that reaches no AST — neither producer extracts it. The
kernel-side contract is real; the annotation half is not wired to it.

### `@SagaStep` — LIVE, narrowly

`@Target(METHOD)`. 75 declared attributes; **8 read**: `name` (defaults to the
method name), `order` (defaults to 1), `description`, `service`, `command`,
`compensation`, `timeout`, `parallel`. Steps are sorted by `order`.

`name` is step identity as of ADR-062, not a label — a duplicate is a
registration failure, and renaming a step breaks sagas already in flight. Drain
before reordering.

The other 67 are RESERVED: circuit breaker, retry, idempotency, input/output
mappings, response handling, security propagation, and the rest.

### `@SagaSteps` — the repeatable container, and a real hazard

Repeating `@SagaStep` on one method compiles cleanly and **loses steps**, and the
two readers lose them differently:

- the `exeris-tooling` processor matches the exact `@SagaStep` type against the
  method's mirrors, sees only the container, and extracts **no step at all**;
- the SDK's `-io` reader keeps **the first** and drops the rest.

A clean compile that silently loses steps is worse than an attribute that does
nothing. Until a reader handles the container, declare one `@SagaStep` per
method.

### `@SagaTransition` / `@SagaTransitions` — RESERVED

The saga state-machine half. 4 attributes, no extraction.

## Presentation

### `@View`, `@Region`, `@Block`, `@Bind` — LIVE

This is the presentation path that actually works. `@View` is `@Target(TYPE)` on
a carrier class; the processor writes a separate `view_<Name>.json` document, and
the codegen-ts view generator emits an Angular page component plus its route
(RFC-2026-06-28).

Every declared attribute is extracted:

| Annotation | Attributes | Status |
|---|---|---|
| `@View` | `name`, `kind`, `route`, `title`, `titleKey`, `layout` | all LIVE |
| `@Region` | `slot` | LIVE |
| `@Block` | `type`, `customType`, `props` | all LIVE |
| `@Bind` | `source`, `ref`, `path`, `expression`, `language` | all LIVE |

If you want generated presentation today, this is the surface to use.

### `@UI` — PARTIAL at entity level, RESERVED at field level

38 declared attributes. The two levels behave differently and neither produces
output.

**Entity level** (sibling of the annotated type): the seven view flags —
`listView`, `detailView`, `createForm`, `editForm`, `searchable`, `filterable`,
`exportable` — are extracted into `UIMetadata` and reach the AST. No emitter
gates on them: which Angular artifacts are generated is a **codegen CLI setting**
(`generateForms`, `generateLists`, `generateServices`), not an annotation. The
only `UIMetadata` member a generator reads is `listColumns`, and no `@UI`
attribute populates it.

There is a reader convention worth knowing: when a standalone `@UI` is present,
the four view flags default to `true` and `exportable` to `false` — "you added
`@UI`, so the views are on" — which is stronger than the per-attribute defaults
declared on the annotation itself.

**Field level**: RESERVED. `extractUIMetadata` is called from the entity path
only, so no field-level `@UI` is extracted in either the sibling or the nested
form. `componentType`, `displayInList`, `displayInDetail`, `displayOrder`,
`placeholder`, `helpText`, `width`, `format`, `customComponent`, the `*Key` i18n
attributes — none of them reach anything.

`@UI` is not deprecated, and it is worth being precise about why. `@View` and the
unified presentation IR are meant to absorb it (RFC-2026-06-25), but the ADR-047
leaf-field facet that would replace the field-level surface does not exist. The
rule is that `@Deprecated(forRemoval)` runs only once the replacement can
actually replace it. That is a reason to keep the annotation — not evidence that
it works.

Because 1.x minors are additive-only, `@UI` is frozen through the 1.x line, with
deprecation targeted at the minor where the facet completes and removal at 2.0.

### `@Tab`, `@UIGroup`, `@NavMenu` — RESERVED

The layout family. 39 attributes between them, no extraction.

## Graph

### `@Graph` — LIVE as a marker

57 declared attributes; **`nodeClass` is extracted**, and drives
`KernelGraphSyncGenerator`. The other 56 are RESERVED — sync policy, similarity,
vector embeddings, full-text, traversal, caching, and the `graphqlExtensions` /
`graphqlQueries` / `graphqlTypeName` trio.

### `@GraphEdge`, `@GraphProperty`, `@GraphQuery` — RESERVED

142 attributes between them, extracted by neither producer.

`@GraphEdge` is worth one note. Its repeatable container `@GraphEdges` was a
top-level type declared without `public`, so repeating `@GraphEdge` from any
other package failed to compile — the compiler requires a container to be at
least as accessible as its repeatable annotation. Fixed in 0.10.0, and
`AnnotationContractTest` now asserts the rule for every `@Repeatable` rather than
for that one instance. The fix buys **compilation only**: a repeated or single
edge declaration still reaches no AST.

### Not GraphQL

The `@Graph*` family projects into a graph *database*. It is unrelated to
`@ExerisDomain.graphqlApi`, and neither produces a GraphQL schema, because no
GraphQL emitter exists in the toolchain.

## Capabilities

The capability surface is **LIVE** — it drives `cap-manifest.json`, which the
composition tooling and the SKU-boot asserter consume (ADR-024).

| Annotation | Attributes | Status |
|---|---|---|
| `@CapabilityModule` | none — a marker | LIVE |
| `@Provides` | `service` (required), `version` | LIVE |
| `@Requires` | `service` (required), `versionRange`, `optional` | LIVE |
| `@CapabilityLifecycle` | none — a marker | LIVE |

Three published SDK modules back this:

- `exeris-sdk-composition-spec` — the `cap-manifest.json` schema and the one
  canonical content binding. Zero runtime deps.
- `exeris-sdk-composition-lifecycle` — the cap-facing `CapabilityLifecycleHooks`
  interface: the four-phase `initialize` / `ready` / `drain` / `terminate`
  contract. Zero dependencies by design, enforcer-proven, so a cap author
  compiles against annotations plus this jar and nothing else.
- `exeris-sdk-composition-runtime` — the boot-time validation-stamp asserter and
  the conductor that drives the cap lifecycle. Ships into the SKU artifact.

## The reserved behaviour layer

`@Derived` and `@Rule` (repeatable via `@Rules`) are **RESERVED** — 8 attributes,
no extraction, no generator.

They exist to let a domain declare the *mechanical* slice of behaviour the AST
cannot otherwise describe: derived fields and roll-ups (`@Derived`), named
invariants and guards (`@Rule`). The expression is stored verbatim and tagged by
a `language` attribute (empty means `"spel"`, following the existing `condition` /
`visibleWhen` convention). The SDK interprets nothing — that is what preserves
zero runtime coupling.

Direction in RFC-2026-06-18 (ACCEPTED). Declaring them today records design
intent and produces nothing.

### `@Projection` — RESERVED

97 attributes, the largest single reserved annotation in the SDK. No extraction.

### `@QueryParam` — RESERVED

10 attributes, no extraction.

### `@InternalApi` — LIVE as a marker, with a known drift

The annotation's **presence** is extracted and is genuinely load-bearing: the
codegen-ts orchestrator, app-structure generator and guard generator all skip
domains marked internal.

All five declared attributes — `consumers`, `rateLimit`, `requireMtls`,
`timeout`, `documented` — are RESERVED, and the reason is a documented drift: the
annotation and the AST record `InternalApiMetadata` (`hidden`, `readOnly`,
`internal`, `reason`, `since`, `disabledActions`, `allowedRoles`) describe two
different concepts, so there is nothing to map attribute-to-attribute. Only
presence survives. Reconciling the two is a tracked 1.0.0 item.

---

Next: [04 — Status matrix](04-status-matrix.md)
