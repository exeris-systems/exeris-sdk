---
title: Source model
type: module
visibility: public
owning-repo: exeris-sdk
status: active
last-verified: 2026-10-29
---

# Source model

**Module:** `eu.exeris:exeris-sdk-source-model` (0.13.1)

`exeris-sdk-source-model` owns the canonical description of a domain as data: the AST records in `eu.exeris.sdk.sourcemodel.ast`, the mutation vocabulary in `eu.exeris.sdk.sourcemodel.mutation`, and the pre-construction brief records in `eu.exeris.sdk.brief`. Every consumer of a domain, from the annotation processor to Studio, agrees on this one shape.

The module never parses Java source, never applies a mutation, and never reads or writes a file. Parsing and writing live in [`exeris-sdk-source-model-io`](03-source-model-io.md), which keeps JavaParser out of this module's dependency tree. Generating code is `exeris-tooling` work.

## Coordinates and dependencies

| Item | Value |
|---|---|
| Group and artifact | `eu.exeris:exeris-sdk-source-model` |
| Published | Yes, with sources and javadoc jars |
| Compile dependency | `com.fasterxml.jackson.core:jackson-annotations` only |
| Test dependencies | `exeris-sdk-annotations`, JUnit Jupiter, AssertJ, Jackson 3 `jackson-databind` and `jackson-dataformat-yaml`, `com.networknt:json-schema-validator` |

The jar ships no `databind`. The mapper is always the consumer's own, so the Jackson settings in the next section are an obligation on the consumer, which the SDK cannot enforce for you.

The module compiles with two processors from `exeris-sdk-annotation-catalog` on the processor path, `AstSchemaProcessor` and `BriefSchemaProcessor`. They write `META-INF/exeris/ast-schema.json` and `META-INF/exeris/brief-schema.json` into this jar. See [Annotation catalog](04-annotation-catalog.md).

A `maven-enforcer-plugin` rule (`ban-javaparser-zero-coupling`) fails `verify` if `com.github.javaparser` reaches the dependency tree. The invariant is written once, in [zero runtime coupling](../../.agents/policies/zero-runtime-coupling.md).

## Contract

| Package | Holds | Guarantee |
|---|---|---|
| `eu.exeris.sdk.sourcemodel.ast` | `DomainMetadata` (the root), `FieldMetadata`, `ActionMetadata`, `RelationshipMetadata`, `SagaMetadata`, `UIMetadata`, `SystemFieldsMetadata`, `CapabilityModuleMetadata`, `ViewMetadata` and the rest of the record family, plus AST-owned enums such as `DataScope` and `RouteAccess` | Records and enums. They round-trip through JSON by name |
| `eu.exeris.sdk.sourcemodel.mutation` | `MutationOp` (sealed, nine ops), `MutationResult` (sealed, four outcomes), `MutationPath`, `SchemaVersion`, `SourceDigest`, `BaselineTrust` | Pure and JavaParser-free. Polymorphic JSON with an `op` or `outcome` discriminator |
| `eu.exeris.sdk.brief` | `Brief` (the root), `BriefReadiness`, `Actor`, `Workflow`, `EarsCriterion`, `GlossaryTerm`, `OpenQuestion`, `FulfilledBy` | A brief references the source model and never re-encodes it. The dependency runs one way |

### Wire format

The wire-format rules have one home, the `eu.exeris.sdk.sourcemodel.ast` package-info, with the policy in [AST wire format](../../.agents/policies/ast-wire-format.md). Two bind a consumer. A Jackson 3 mapper must set `FAIL_ON_NULL_FOR_PRIMITIVES = false`, and a record may grow at the tail only, with its previous arity kept as a constructor; the growth rule is section 3 of [`MIGRATION-0.x-to-1.0.md`](../../MIGRATION-0.x-to-1.0.md).

### Mutation surface

`MutationOp` has nine members: `AddField`, `RemoveField`, `RenameField`, `ChangeFieldType`, `AddRelationship`, `RemoveRelationship`, `ChangeRelationshipCardinality`, `AddAction`, `RemoveAction`. Each carries a path string, parsed by `MutationPath.parse`; the grammar is in that class's javadoc. `BaselineTrust` is the `(sourceDigest, schemaVersion)` pair that codegen writes beside the domain fields of each `exeris-metadata/<entity>.json`. `SchemaVersion.CURRENT` names the AST shape, not the artifact version.

The SDK reports outcomes. Whether to merge, prompt or auto-apply is the host's decision; ADR-042 records this split.

### Brief

`Brief` is the shape of `docs/briefs/<area>.brief.yaml`. `BriefReadiness.evaluate` returns a `Report` with `valid`, `ready`, `errors`, `assumed`, `unresolved` and `unchecked`. A brief is ready when it is valid and no blocking question is open. Unresolved references do not change readiness. The wire format moves with `briefSchemaFormat`, an integer a consumer pins to. Rules are in ADR-106.

### Guard tests

All live under `exeris-sdk-source-model/src/test/java/eu/exeris/sdk/`.

| Test | What it holds |
|---|---|
| `AstJsonRoundTripTest` (`sourcemodel/ast`) | Every AST record survives a Jackson 3 serialize and deserialize cycle with all components, including the inclusion edge cases |
| `MutationWireFormatTest` (`sourcemodel/mutation`) | Every op and result round-trips through its sealed-interface type, so the discriminator recovers the concrete subtype |
| `BaselineTrustContractTest` (`sourcemodel/mutation`) | `SchemaVersion`, `SourceDigest` and the sibling-field design of `BaselineTrust`; also that `SchemaVersion.CURRENT` is not a compile-time constant |
| `RecordComponentOrderTest` (`sourcemodel`) | Component order against `record-components.txt`: trailing growth only, no reorder, rename, retype or removal |
| `RecordConstructorLedgerTest` (`sourcemodel`) | Each previous arity remains a public constructor, against `record-arities.txt` |
| `JavadocCompletenessTest` (`sourcemodel`) | Every public member carries javadoc, except builder setters |
| `AstSchemaContractTest` (`sourcemodel/ast`) | The generated `ast-schema.json` against the compiled classes |
| `BriefSchemaContractTest`, `BriefDependencyDirectionTest` (`brief`) | The generated brief schema, and the rule that nothing under `eu.exeris.sdk.sourcemodel` uses the brief package |

The build also runs a JaCoCo `check` at `verify` on this module: 0.85 covered ratio for instructions and for lines, over the whole bundle.

## Hot path

None at application runtime. The records are built and read at build time (the annotation processor and codegen) and at design time (LSP, Studio, IDE plugins). They never run inside a deployed service.

The records are immutable. Lists are copied on construction, so a constructed record can be shared across threads. The builders are mutable and meant for single-thread use.

## Failure modes

| Situation | What the caller sees |
|---|---|
| A required component is `null` (for example `FieldMetadata.name` or `type`; `path` on any `MutationOp`) | `NullPointerException` from the compact constructor, with a message naming the component |
| A list component holds a `null` element | `NullPointerException` naming the component. An absent list yields an empty one |
| `MutationPath.parse` receives a malformed string, or a name contains `/` | `IllegalArgumentException` |
| A `MutationResult` outcome | One of `Success`, `Conflict`, `ValidationError`, `NoBaseline`. Only `Success` returns `true` from `successful()`. `NoBaseline` carries a `NoBaselineCause`: `MISSING_BASELINE`, `STALE_DIGEST` or `SCHEMA_VERSION_SKEW` |
| A Jackson 3 consumer leaves `FAIL_ON_NULL_FOR_PRIMITIVES` on | Deserialization throws on an explicit `null` for a primitive |
| A baseline stamped with another `SchemaVersion`, or with none | Reported as `SCHEMA_VERSION_SKEW` by the `-io` layer. The SDK never assumes compatibility |
| A brief with an unknown property, bound with a lenient reader | The property is dropped silently. Validate against `brief-schema.json` first |
| `BriefReadiness.evaluate(null)` | A `Report` that is not valid and not ready, with one problem; it does not throw |

Trade-off: records with many trailing components are awkward to construct by position. That cost is accepted for wire stability. Prefer the builders, and note that `SystemFieldsMetadata` is built through `builder()` so a non-canonical name is set by name.

## Owning ADRs

| ADR | Decision |
|---|---|
| [ADR-003](../adr/ADR-003-entity-first-development-strategy.md) | The annotated class is the only definition of the domain |
| [ADR-037](../adr/ADR-037-source-model-io-module.md) | JavaParser stays out of this module |
| [ADR-038](../adr/ADR-038-capability-annotation-surface.md) | Capability modules are a top-level AST record, parallel to entities |
| [ADR-042](../adr/ADR-042-bidirectional-mutation-surface.md) | `MutationOp`, `MutationResult`, baseline trust and the conflict rule |
| [ADR-054](../adr/ADR-054-field-validation-min-max-pattern-cut.md) | `FieldMetadata` is the sole AST carrier of constraint values |
| [ADR-059](../adr/ADR-059-data-scope-expression.md) | `DataScope` replaces the `tenantScoped` boolean |
| [ADR-072](../adr/ADR-072-kernel-preview-spi-reserved-surface.md) | The blob, route access, schedule and channel facets; the freeze status differs per facet |
| [ADR-106](../adr/ADR-106-pre-construction-brief-contract.md) | The brief contract and its one-way dependency on the AST |

## Where this does not apply

- **Reading or writing `.java` files.** Use [`exeris-sdk-source-model-io`](03-source-model-io.md).
- **Applying a mutation.** This module defines `MutationOp`; the conflict-aware application is in `-io`.
- **Which AST facets are populated.** Presence of a record is not proof of extraction. The status label on the matching annotation says whether a generator reads it.
- **Generated output.** What codegen emits from these records belongs to `exeris-tooling`.
- **Composition.** Capability resolution and lifecycle live in [Composition spec](05-composition-spec.md) and [Composition lifecycle](06-composition-lifecycle.md).
