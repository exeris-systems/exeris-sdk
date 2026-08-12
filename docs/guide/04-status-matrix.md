# 04 — Status matrix

← [Guide index](README.md) · [03 — Behaviour and presentation](03-behaviour-and-presentation.md)

The reference table. Every annotation in the SDK, what reaches the AST, and what
a generator actually reads.

## Method

Two independent facts had to be established per attribute, because they fail
independently:

1. **Extraction** — does `exeris-tooling`'s `ExerisDomainProcessor` (or the SDK's
   own `SourceModelReader`) read this attribute name into the AST? Established
   by reading the extraction sites, not by reading annotation javadoc.
2. **Consumption** — does any emitter in `exeris-codegen-java` or
   `exeris-codegen-ts` read the resulting AST member? Established by tracing the
   member to a generator that writes output, not to the Zod wire schema — the
   wire schema declares the shape of everything and consumes nothing.

Attribute inventory is the surface snapshot,
`exeris-sdk-annotations/src/test/resources/annotation-surface.txt` (938
attributes), which `AnnotationSurfaceContractTest` keeps honest.

Evidence base: `exeris-tooling` at `9c72a8f`, SDK 0.10.0.

## Totals

| | Count | Share |
|---|---|---|
| Declared attributes | 938 | 100% |
| Extracted into the AST | 98 | 10% |
| — of those, LIVE (a generator reads them) | 79 | 8% |
| — of those, PARTIAL (AST only, no reader) | 16 | 2% |
| — of those, DEPRECATED (live with a warning, removed at 1.0.0) | 3 | — |
| RESERVED (extracted by nobody) | 840 | 90% |

Annotation types: 49. Referenced by the processor at all: **21**.

## Annotations that produce output

### `@ExerisDomain` — 35 declared

| Status | Attributes |
|---|---|
| **LIVE** (12) | `module` `path` `aggregate` `description` `apiVersion` `restApi` `realTimeApi` `dataScope` `softDelete` `audited` `versioned` `sensitive` |
| **DEPRECATED** (1) | `tenantScoped` → `dataScope` (ADR-059), removed 1.0.0 |
| **PARTIAL** (7) | `graphqlApi` `internalClient` `cacheable` `cacheTtl` `cacheRegion` `fullTextSearch` `searchConfig` |
| **RESERVED** (15) | `primaryKeyField` `tenantIdField` `versionField` `softDeleteField` `softDeleteTimestampField` `softDeletedByField` `createdAtField` `createdByField` `updatedAtField` `updatedByField` `permissions` `roles` `tags` `validationMode` `ui` |

Evidence: `ExerisDomainProcessor` extraction block; `KernelFlywayGenerator` and
`KernelRepositoryGenerator` for the system flags; `KernelTableNaming` and
`KernelEventGenerator` for `sensitive`; `KernelStreamHandlerGenerator` and
`stream-client-gen.ts` for `realTimeApi`; `KernelClientGenerator` and
`service-gen.ts` for `apiVersion`. The seven PARTIAL attributes appear in
`exeris-codegen-ts/src/models/domain-model.ts` — the Zod wire schema — and
nowhere else.

### `@Field` — 29 declared

| Status | Attributes |
|---|---|
| **LIVE** (13) | `label` `description` `required` `unique` `indexed` `searchable` `sortable` `filterable` `readOnly` `dataType` `computed` `computedFrom` `inCreate` |
| **PARTIAL** (1) | `inUpdate` |
| **RESERVED** (15) | `labelKey` `descriptionKey` `defaultValue` `order` `group` `inList` `inDetail` `cssClass` `encrypted` `maskPattern` `sensitive` `writeOnly` `compositeUnique` `ui` `validation` |

`inUpdate` is the half of `@Validation.validateOn`'s replacement that has no
consumer — it appears only in the Zod schema. Worth knowing before migrating off
the deprecated attribute.

### `@Validation` — 24 declared

| Status | Attributes |
|---|---|
| **LIVE** (5) | `min` `max` `minLength` `maxLength` `pattern` |
| **DEPRECATED** (2) | `required` → `@Field.required`; `validateOn` → `@Field.inCreate`/`inUpdate` |
| **RESERVED** (17) | `email` `url` `uuid` `positive` `positiveOrZero` `negative` `negativeOrZero` `future` `futureOrPresent` `past` `pastOrPresent` `decimalMin` `decimalMax` `message` `groups` `customValidator` `asyncValidator` |

The single largest gap between how an annotation reads and what it does.
`@Validation(email = true)` is a no-op.

All five LIVE values land on `FieldMetadata`, which is the only AST carrier for
constraints — `ValidationMetadata` was removed outright in 0.9.0 (ADR-054),
never having been populated or consumed. Zero-valued bounds survive the wire via
per-component `@JsonInclude(NON_NULL)`.

### `@Relationship` — 23 declared

| Status | Attributes |
|---|---|
| **LIVE** (5) | `targetEntity` `relationshipType` `cascadeDelete` `cascadeUpdate` `displayField` |
| **PARTIAL** (1) | `mappedBy` |
| **RESERVED** (17) | `required` `searchable` `orderBy` `filter` `fetchType` `display` `displayFields` `displayTemplate` `labelField` `searchFields` `icon` `allowCreate` `allowInlineCreate` `allowInlineEdit` `autocompleteThreshold` `dependsOn` `dependsOnField` |

`targetEntity` and `displayField` are `required` on the annotation.
`relationshipType` gates the FK column, its index, its constraint and the
generated finder — if a doc in this repo still says it is never extracted, that
doc is stale.

### `@Action` — 28 declared

| Status | Attributes |
|---|---|
| **LIVE** (5) | `description` `httpMethod` `async` `streaming` `streamEventType` |
| **RESERVED** (23) | `name` `label` `path` `permissions` `roles` `confirm` `visibleWhen` `successMessage` `errorMessage` `variant` `location` `order` `bulk` `timeout` `refreshAfter` `redirectTo` `closeAfter` `showProgress` `showResultMessage` `debounce` `maxConcurrent` `realTimeUpdates` `ui` |

`realTimeUpdates` is deliberately unextracted — a separate affordance from
`streaming`, to be extracted in the same change as its generator.

### `@ActionParam` — 33 declared

| Status | Attributes |
|---|---|
| **LIVE** (2) | `description` `required` |
| **RESERVED** (31) | everything else, including `label` |

The parameter name comes from the method signature, not from the annotation.

**Known defect (`exeris-tooling`, not the SDK):** the processor reads the key
`"displayName"` from `@ActionParam`, and `@ActionParam` declares `label`. The
read always misses, so `ActionParamMetadata.displayName` is permanently null.

### `@DomainEvent` — 58 declared

| Status | Attributes |
|---|---|
| **LIVE** (4) | `name` `trigger` `topic` `description` |
| **RESERVED** (54) | all delivery, outbox, schema-registry, tracing, retention and payload-shaping attributes |

Repeatable; the `@DomainEvent.DomainEvents` container is extracted.

### `@Saga` — 81 declared

| Status | Attributes |
|---|---|
| **LIVE** (4) | `name` `description` `timeout` `maxRetries` |
| **RESERVED** (77) | the entire compensation, retry, persistence, idempotency, concurrency, alerting and hook surface — including `version`, which kernel 0.11 made load-bearing (ADR-064) on the kernel side only |

### `@SagaStep` — 75 declared

| Status | Attributes |
|---|---|
| **LIVE** (8) | `name` `order` `description` `service` `command` `compensation` `timeout` `parallel` |
| **RESERVED** (67) | circuit breaker, retry, idempotency, mappings, response handling, security propagation, … |

`name` defaults to the method name and is step **identity** (ADR-062), not a
label. `order` defaults to 1; steps are sorted by it.

**Hazard:** repeating `@SagaStep` compiles and loses steps — the processor
extracts none, the `-io` reader keeps the first. One step per method.

### `@Graph` — 57 declared

| Status | Attributes |
|---|---|
| **LIVE** (1) | `nodeClass` |
| **RESERVED** (56) | sync policy, similarity, vector embeddings, full-text, traversal, caching, and the `graphql*` trio |

### `@View` / `@Region` / `@Block` / `@Bind` — 15 declared

**All LIVE.** `@View`: `name` `kind` `route` `title` `titleKey` `layout`.
`@Region`: `slot`. `@Block`: `type` `customType` `props`. `@Bind`: `source`
`ref` `path` `expression` `language`.

The only presentation path with full extraction and a real emitter
(`view-gen.ts`, RFC-2026-06-28).

### `@UI` — 38 declared

| Status | Attributes |
|---|---|
| **PARTIAL** (7) | `listView` `detailView` `createForm` `editForm` `searchable` `filterable` `exportable` — entity level only |
| **RESERVED** (31) | the whole field-level surface, plus `icon` `color` `label` at entity level |

Extracted only from a **standalone `@UI` on the type**. Field-level `@UI` is not
extracted in either form. Which Angular artifacts get generated is a codegen CLI
setting, not an annotation.

### `@InternalApi` — 5 declared

**Presence is LIVE** — the orchestrator, app-structure generator and guard
generator skip domains marked internal. **All 5 attributes are RESERVED**
(`consumers` `rateLimit` `requireMtls` `timeout` `documented`), because the
annotation and the AST record `InternalApiMetadata` describe two different
concepts. Reconciliation is a tracked 1.0.0 item.

### `capability` — 5 declared

**All LIVE.** `@CapabilityModule` and `@CapabilityLifecycle` are attribute-less
markers. `@Provides`: `service` (required), `version`. `@Requires`: `service`
(required), `versionRange`, `optional`. They drive `cap-manifest.json` (ADR-024).

## Annotations extracted by nobody

Everything below is **RESERVED in full**: declared here, read by neither
producer, consumed by no generator. Writing any of it compiles and produces
nothing.

| Annotation | Attributes | Note |
|---|---|---|
| `@Projection` | 97 | Largest reserved annotation in the SDK |
| `@GraphQuery` | 65 | |
| `@EventSourced` | 57 | The one entry in the processor's inert-annotation registry |
| `@GraphEdge` | 44 | Container `@GraphEdges` made `public` in 0.10.0 — compilation fix only |
| `@GraphProperty` | 33 | |
| `@EventHandler` | 21 | Handlers *are* generated — from `@DomainEvent`, not from this |
| `@NavMenu` | 18 | |
| `@RowLevelSecurity` | 16 | Live tenancy path is `@ExerisDomain(dataScope = TENANT)` |
| `@Encrypted` | 11 | |
| `@Tab` | 11 | |
| `@UIGroup` | 10 | |
| `@QueryParam` | 10 | |
| `@Rule` / `@Rules` | 6 | RFC-2026-06-18 (ACCEPTED) |
| `@SagaTransition` / `@SagaTransitions` | 5 | Saga state-machine half |
| `@Derived` | 3 | RFC-2026-06-18 (ACCEPTED) |
| `system.*` (11 annotations) | 23 | `@PrimaryKey` `@TenantId` `@Version` `@SoftDelete` `@SoftDeleteTimestamp` `@SoftDeletedBy` `@AuditCreatedAt` `@AuditCreatedBy` `@AuditUpdatedAt` `@AuditUpdatedBy` — columns come from the `@ExerisDomain` flags |

## Why `-Aexeris.strict` will not tell you this

The processor has a strict mode that warns about inert surface. It cannot
substitute for this table, structurally: by the registry's own doctrine an entry
requires the attribute to be **extracted and then unread**. An attribute that is
never extracted can never be flagged. That is why the registry holds two
attributes and one annotation while 840 attributes reach nothing — the check is
sound and its scope is narrow.

## Reporting a discrepancy

`exeris-tooling` is authoritative. If it disagrees with this table, this table is
wrong — please open an issue against `exeris-sdk` with the generator site, and it
gets corrected here rather than argued.
