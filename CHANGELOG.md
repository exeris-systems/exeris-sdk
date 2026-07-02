# Changelog

All notable changes to the Exeris SDK are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

**Versioning policy.** The SDK is `0.x` until 1.0.0 GA: breaking changes may land
in any 0.x release and downstream consumers pin exact versions. See
[`ROADMAP.md`](ROADMAP.md) for the milestone scope and [`MIGRATION.md`](MIGRATION.md)
for per-version upgrade steps.

> **Release history note.** `0.6.0` is the first **tagged** release. The earlier
> entries are ROADMAP milestones that shipped on a single pre-release line — the
> POM version went `0.1.0-SNAPSHOT` → `0.5.0-SNAPSHOT` → `0.6.0`, so `0.2.0` /
> `0.3.0` / `0.4.0` were milestone labels, never cut as artifacts. Dates below
> are when each milestone landed. `0.6.0` and `0.7.0` are tagged releases (with
> links); the earlier entries are milestone labels only.

## [Unreleased]

### Changed
- **`SchemaVersion.CURRENT`** bumped `"0.7.0"` → `"0.8.0"` to name the
  `ActionMetadata` streaming growth below (a `"0.7.0"` baseline now reads as
  `SCHEMA_VERSION_SKEW`). The other 0.8.0 additions ride the same schema.
- **UI kit published to GitHub Packages** — `@exeris-systems/ui-kit` on the org
  registry. The public npm-registry publish under `@exeris/ui-kit` stays a
  1.0.0 GA item.
- **Kernel v0.10.0 lockstep (docs + javadoc)** — `@DomainEvent.topic` carries
  the ADR-050 stance (binding-agnostic kernel sink on `EventTypeSpec`; the
  Community Kafka binding honours it on publish + subscribe; the in-memory bus
  treats it as advisory), anchored by a new `docs/adr/ADR-050.link.md` stub;
  the streaming attribute javadocs reflect the shipped kernel SSE SPI (ADR-043)
  + tooling emitters (`realTimeApi` live at the entity level, per-action
  pending the tooling EV1-stream producer pass, `realTimeUpdates` still inert);
  `RFC-2026-06-24` (universe data-scope) moved DRAFT → ACCEPTED on the kernel
  shared-scope decision (RFC-2026-07-02) — the build stays gated on the
  ADR-012 amendment.

### Added
- **`ActionMetadata` per-action streaming fields** — `streaming` /
  `streamEventType` / `realTimeUpdates`, the faithful AST twin of the
  `@Action` streaming attributes (the per-action SSE driver, ADR-043 /
  RFC-2026-06-22 Slice 2). Trailing components; blank `streamEventType` → null.
  See [`MIGRATION.md`](MIGRATION.md#07x--08x).
- **`DomainEventMetadata` resolved payload framing (EV1)** — `payloadFields`
  (resolved payload field *names*: `includeFields` if non-empty, else all
  `@Field` names, minus `excludeFields`) and `sensitiveFields` (verbatim), the
  AST side of the generated event-payload story (kernel ADR-046 / tooling EV1).
  Field names, not `FieldMetadata` copies; the `-io` reader shares the same
  resolution semantics (ADR-042 lock-step). Trailing components, null-list →
  empty. See [`MIGRATION.md`](MIGRATION.md#07x--08x).
- **Composition spec + runtime modules** — `exeris-sdk-composition-spec` (the
  shared SKU manifest schema + binding, ADR-024 obligation 8b) and
  `exeris-sdk-composition-runtime` (the SKU-boot composition-stamp asserter on
  that shared spec, ADR-024 obligation 8a): two new publishable jars, wired
  into the reactor and the BOM with sources/javadoc attached.
- **`-io` reader parity growth** — the reader now reads `@Field.dataType`
  (closing UI-kit gap B5, coordinated with the tooling processor flip per
  ADR-042) and the per-action streaming driver (`@Action.streaming` /
  `streamEventType`; `realTimeUpdates` stays unextracted on both sides until
  it has a generator consumer).
- **Unified presentation / front model (`@View` / `ViewMetadata`)** —
  RFC-2026-06-25 (ACCEPTED). Annotations `@View` / `@Region` / `@Block` / `@Bind`
  in `exeris-sdk-annotations`, and AST records `ViewMetadata` / `RegionMetadata` /
  `ComponentNodeMetadata` / `BindingMetadata` (+ AST-owned enums `ViewKind` /
  `BlockType` / `BindSource`) in `exeris-sdk-source-model`. A framework-neutral,
  entity-optional composition IR (view → region → component-node → binding;
  `ComponentNodeMetadata` is a recursive tree). Authored class-structure-derived;
  `@JsonInclude(NON_NULL)` with blank→null / null-list→empty normalization and
  `effective*()` defaults. **One presentation model, not a split:** `@UI` is
  being subsumed — its field-level render detail is reused as the leaf field
  facet of `ComponentNodeMetadata` (`UIMetadata.UIFieldMetadata`), its entity-level
  view selection becomes a `@View`. **Shipped reserved** — no processor/codegen/
  reader consumes it yet (the `-io` reader stays in parity, ADR-042); generation
  is the `exeris-tooling` Angular 22 emitter, gated on the emitter + a Headless
  CMS corpus. `@UI` carries a successor note but is **not** deprecated yet (it
  stays the functional path until the emitter lands). Net-new standalone records,
  not referenced by `DomainMetadata` — `SchemaVersion` unchanged (`"0.8.0"`).

### Security
- **Jackson bumped to pick up upstream security fixes** — `jackson-databind` /
  `jackson-datatype-jsr310` `3.1.2` → `3.2.0` and `jackson-annotations`
  `2.21` → `2.22` in the BOM. Adopted ecosystem-wide (`exeris-platform-bom` was
  already pinned to `3.2.0` pending this SDK BOM bump). The two versions stay on
  their separate 3.x / 2.x tracks — the pairing is dictated by `jackson-bom
  3.2.0` (`jackson.version.annotations=2.22`), and `2.22` still ships
  `JsonSerializeAs` (the AST floor). No wire-format, schema, or public-API change.

## [0.7.0] — 2026-06-18

The AST-expressiveness milestone — the SDK side of behaviour / choreography /
topology modeling is complete: projection source / read-model framing, the JVM
method behind `@Action`, the saga state machine, and the declarative
`@Derived` / `@Rule` layer. All additions are wire-additive on the `"0.7.0"`
schema; downstream generation of the new surfaces is `exeris-tooling` work.

### Changed
- **`SchemaVersion.CURRENT`** bumped `"0.6.0"` → `"0.7.0"` to name the
  `ProjectionMetadata` shape growth (a `"0.6.0"` baseline now reads as
  `SCHEMA_VERSION_SKEW`). The saga state-machine growth below rides on the same
  `"0.7.0"` schema (same release) — no second bump.

### Added
- **`ProjectionMetadata` source + read-model framing** — the record grew
  `aggregateTypes` (source aggregate link), `events` / `eventClassNames` /
  `topicPattern` (event subscription), and `model` / `schema` (read-model
  identity) alongside the existing `fields` subset, plus a `builder`, an
  `of(name, aggregateType, fields)` factory, and `hasSourceAggregate` /
  `hasFields`. The operational surface of `@Projection` (partitioning / rebuild /
  consistency / monitoring / query API) is deferred. Canonical constructor
  changed arity + order — prefer the builder / factories. See
  [`MIGRATION.md`](MIGRATION.md#06x--07x).
- **`ActionMetadata.methodName`** — the JVM method behind an `@Action`, distinct
  from the `@Action(name)` identity, with `effectiveMethodName()` falling back to
  the action name. Unlike the other 0.7.0 additions it **is** populated by the
  `-io` reader (the writer keys on the effective action name, so no write-path
  divergence). Appended at the end of the record.
- **Saga step `kind` + typed transitions** — `SagaStepMetadata` grew `kind`
  (`StepKind` = INVOKE / COMPENSATE / AWAIT_EVENT / AWAIT_TIMER) with an
  inferring `effectiveKind()`, and `SagaMetadata` grew `transitions`
  (`SagaTransition` edges = `from` → `to` `on` a `TransitionOutcome` of
  SUCCESS / FAILURE / TIMEOUT / COMPENSATED, optional SpEL `guard`, terminal on
  null `to`), with `hasTransitions`, `success`/`failure`/`timeout`/`ofOutcome`
  factories, and normalization. Promotes the step list + `dependsOn` DAG into an
  outcome-edged state-machine graph; AST-owned enums (zero-coupling). Both
  components appended at the end (constructor arity grew). Shares the `"0.7.0"`
  schema (same release as the projection growth). See
  [`MIGRATION.md`](MIGRATION.md#06x--07x).
- **Declarative-behaviour layer (`@Derived` / `@Rule`)** — RFC-2026-06-18
  (ACCEPTED). Annotations `@Derived` / `@Rule` / `@Rules` in
  `exeris-sdk-annotations`, and AST records `DerivedMetadata` (a `FieldMetadata`
  facet via `derived`) + `RuleMetadata` (a `DomainMetadata.rules` list).
  Expression stored verbatim + `language` tag (default `"spel"`); `@Rule`
  `severity` default `"ERROR"`; blank → null normalization; `dependsOn` allows
  related-entity paths. Both facets appended at the end (constructor arity grew);
  shares the `"0.7.0"` schema. Shipped reserved — no processor extraction /
  codegen consumes them yet (the `-io` reader stays in parity); generation is the
  `exeris-tooling` follow-up. See [`MIGRATION.md`](MIGRATION.md#06x--07x).

## [0.6.0] — 2026-06-17

Feedback-driven cleanups (the first slices of the 0.6.0–0.9.0 band): annotation
honesty, UI-kit gaps, and the start of the AST-expressiveness growth.

### Added
- **`EventHandlerMetadata`** AST record — the reaction-side companion to
  `DomainEventMetadata`, giving the long-shipped `@EventHandler` annotation an
  AST record to be extracted into; wired into `DomainMetadata.eventHandlers`.
- **`FieldMetadata.dataType`** (B5) — carries `@Field.dataType` semantic kind
  (`currency` / `percent` / `url`), distinct from the presentation `format`.
- **i18n message keys** (B4a) — `FieldMetadata.displayNameKey` / `descriptionKey`
  and `UIMetadata.UIFieldMetadata.placeholderKey` / `helpTextKey`, with the
  literal as fallback.
- **Custom-component escape hatch** (B4b) — `ComponentType.CUSTOM` +
  `@UI.customComponent` → `UIFieldMetadata.customComponent` (+ a `custom(...)`
  factory).
- **UI-kit** — a Tailwind v4 `@theme` entry alongside the v3 preset (B1), the
  preset/theme exposed via package `exports` (B2), and styled component classes
  for every renderable `ComponentType` (B3).

### Changed
- **`SchemaVersion.CURRENT`** bumped `"0.5.0"` → `"0.6.0"` to name the grown AST
  shape (a `"0.5.0"` baseline now reads as `SCHEMA_VERSION_SKEW`).
- **Annotation honesty pass** — `@ExerisDomain` system-field overrides,
  `@Relationship.targetEntity`, and `@Action(name=…)` are honoured by tooling;
  `@DomainEvent.topic`, the streaming/real-time attributes, and the capability
  surface carry Open-Core-status javadoc notes where they are not yet consumed.

### Wire format
- Canonical (all-args) constructors of `FieldMetadata`, `UIFieldMetadata`, and
  `DomainMetadata` gained components — prefer the builders / factories, which are
  stable. All additions are by-name JSON and back-compatible to read. See
  [`MIGRATION.md`](MIGRATION.md#05x--06x).

## 0.5.0 — 2026-06-15 — bidirectional mutation surface

### Added
- New package `eu.exeris.sdk.sourcemodel.mutation` — `MutationOp` /
  `MutationResult` / `MutationPath` / `SchemaVersion` / `SourceDigest` /
  `BaselineTrust` (ADR-042).
- `exeris-sdk-source-model-io` — AST-level three-way conflict detection
  (`SourceModelConflictDetector`) and conflict-aware application
  (`SourceModelMutationApplier`).

## 0.4.0 — 2026-06-13 — capability annotation surface

### Added
- New package `eu.exeris.sdk.annotation.capability` — `@CapabilityModule` /
  `@Provides` / `@Requires` / `@CapabilityLifecycle` (ADR-024 / ADR-038).
- AST records `CapabilityModuleMetadata` / `ProvidesMetadata` /
  `RequiresMetadata`, plus `-io` reader support.

## 0.3.0 — 2026-06-05 — source-model parser + writer

### Added
- New sibling module `exeris-sdk-source-model-io` (ADR-037): JavaParser-based
  parser (`.java` → `DomainMetadata`) and idempotent writer
  (`DomainMetadata` → `.java`). JavaParser is confined to this module so
  `source-model` stays dependency-light.

## 0.2.0 — 2026-06-03 — quality gates + pre-publish hygiene

### Added
- CI (`mvn verify` on JDK 26 + ui-kit Vitest), JaCoCo 85% gate on `source-model`,
  `AnnotationContractTest`, and `AstJsonRoundTripTest` (the wire-format guard).
- Pre-publish POM metadata for Sonatype Central Portal.

### Changed
- **Field/Validation canonical scoping** — `required` / `inCreate` / `inUpdate`
  are owned by `@Field`; `@Validation.required` and `@Validation.validateOn` are
  `@Deprecated(forRemoval = true)` (removal in 1.0.0). See
  [`MIGRATION.md`](MIGRATION.md#01x--02x).
- `jackson-annotations` pinned to `2.21` (Jackson 3 keeps annotations on the 2.x
  line).

## 0.1.0 — 2026-05-03 — scaffold

### Added
- Maven multi-module reactor (`bom`, `parent`, `annotations`, `source-model`)
  plus the `ui-kit` npm package.
- The annotation surface under `eu.exeris.sdk.annotation.*`
  (`@Retention(SOURCE)`, Apache-2.0) and the canonical Jackson-serializable AST
  under `eu.exeris.sdk.sourcemodel.ast.*`.

[0.7.0]: https://github.com/exeris-systems/exeris-sdk/releases/tag/v0.7.0
[0.6.0]: https://github.com/exeris-systems/exeris-sdk/releases/tag/v0.6.0
