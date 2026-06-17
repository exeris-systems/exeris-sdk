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
> are when each milestone landed. Only `0.6.0` has a release tag / link.

## [Unreleased]

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

[0.6.0]: https://github.com/exeris-systems/exeris-sdk/releases/tag/v0.6.0
