---
title: SDK stability matrix
type: reference
visibility: public
owning-repo: exeris-sdk
status: active
last-verified: 2026-10-29
---

# SDK stability matrix

This page is the lookup index for what the 1.0.0 freeze covers, per public surface of `exeris-sdk`. It states a tier for each surface and links the section that rules it. It does not restate the removal list or the reasoning: those live in [`MIGRATION-0.x-to-1.0.md`](../MIGRATION-0.x-to-1.0.md), [ADR-072](adr/ADR-072-kernel-preview-spi-reserved-surface.md) and the Javadoc named in each row.

If this page and a ruling disagree, the ruling wins and this page is the defect.

## How to read the tiers

Every annotation row carries two independent values. Do not read one from the other.

- **Extraction status** is the word in the annotation's own Javadoc `Status:` or `Open-Core status` line: `LIVE`, `PARTIAL`, `RESERVED` or `DEPRECATED`. It says how far the toolchain consumes the annotation today. The vocabulary is defined in the `eu.exeris.sdk.annotation` `package-info.java`.
- **1.0 tier** says what the freeze promises. It is one of the four values below.

| 1.0 tier | Meaning for a 1.x consumer | Ruling |
|---|---|---|
| Frozen as declared | Binary- and source-compatible for the whole 1.x line. Extraction or generation may arrive additively. | [MIGRATION §2](../MIGRATION-0.x-to-1.0.md#2-what-is-deliberately-not-removed-carried-through-1x) for the surfaces it names; for every other surface, the [MIGRATION §1](../MIGRATION-0.x-to-1.0.md#1-removals-landing-at-100) rule that anything not on the removal list survives unchanged |
| Preview | Outside the freeze. A 1.x minor may change or drop it. Pin the exact SDK version. | MIGRATION §2; ADR-072 obligations 31, 32 and 35 |
| Removed at 1.0 | The whole annotation is deleted at 1.0.0. | MIGRATION §1 |
| Members removed at 1.0 | The annotation survives. The listed attributes or constants carry `forRemoval = true` and are deleted at 1.0.0. | MIGRATION §1 |

A `RESERVED` annotation is not automatically outside the freeze. `@Derived`, `@Rule`, `@RouteAccess` and others are `RESERVED` and frozen as declared. The kernel tier of the platform contract an annotation encodes decides the freeze, not the extraction status ([ADR-072](adr/ADR-072-kernel-preview-spi-reserved-surface.md) obligation 29).

## Annotations

Module `exeris-sdk-annotations`. All annotations are `@Retention(SOURCE)`. Members listed as removed are the ones marked `forRemoval = true` in each file.

### Package `eu.exeris.sdk.annotation`: entity and actions

| Annotation | Extraction status | 1.0 tier | Where it is ruled |
|---|---|---|---|
| `@ExerisDomain` | LIVE | Members removed at 1.0: `tenantScoped`, `apiVersion` | MIGRATION §1; [ADR-059](adr/ADR-059-data-scope-expression.md) |
| `@Field` | LIVE (per attribute) | Members removed at 1.0: `encrypted` | MIGRATION §1 |
| `@Validation` | LIVE | Members removed at 1.0: `required`, `validateOn` | MIGRATION §1; [ADR-054](adr/ADR-054-field-validation-min-max-pattern-cut.md) |
| `@Relationship` | LIVE | Frozen as declared | MIGRATION §1 (unlisted) |
| `@Action` | LIVE | Frozen as declared | MIGRATION §1 (unlisted); MIGRATION §2 for `realTimeUpdates` |
| `@ActionParam` | LIVE | Members removed at 1.0: `encrypted` | MIGRATION §1 |
| `@QueryParam` | PARTIAL | Members removed at 1.0: `transform`, `validation`, `validationMessage`, `type`, `elementType`, `sanitize` | MIGRATION §1 |
| `@InternalApi` | PARTIAL | Frozen as declared | MIGRATION §1 (unlisted) |
| `@RouteAccess` | RESERVED | Frozen as declared since 0.13.0 | MIGRATION §2; ADR-072 obligation 29 |
| `@Blob` | PARTIAL | Frozen as declared since 0.13.0 | MIGRATION §2; ADR-072 obligation 29 |
| `@Schedule` | RESERVED | Preview | MIGRATION §2; ADR-072 obligation 31 |
| `@Channel` | RESERVED | Preview | MIGRATION §2; ADR-072 obligation 32 |

### Package `eu.exeris.sdk.annotation`: rules, events, sagas, graph

| Annotation | Extraction status | 1.0 tier | Where it is ruled |
|---|---|---|---|
| `@Derived` | RESERVED | Frozen as declared | MIGRATION §2 |
| `@Rule`, `@Rules` | RESERVED | Frozen as declared | MIGRATION §2 |
| `@DomainEvent` | LIVE | Members removed at 1.0: `encryptFields` | MIGRATION §1 |
| `@EventHandler` | RESERVED | Frozen as declared | MIGRATION §2 |
| `@EventSourced` | PARTIAL | Members removed at 1.0: `encryptSnapshots`, `snapshotEncryptionKeyAlias` | MIGRATION §1 |
| `@Saga` | LIVE | Members removed at 1.0: `encryptState`, `stateEncryptionKeyAlias`, and the `COMMAND`, `SCHEDULE` and `HTTP` constants of the nested `SagaTrigger.TriggerType` | MIGRATION §1 |
| `@SagaStep` | LIVE | Members removed at 1.0: `compensationBuilder`, `compensationClass`, `compensationPayloadExpression`, `compensationService` | MIGRATION §1 |
| `@SagaSteps` | PARTIAL | Frozen as declared | MIGRATION §1 (unlisted) |
| `@SagaTransition`, `@SagaTransitions` | RESERVED | Frozen as declared | MIGRATION §2 |
| `@Graph` | LIVE | Frozen as declared | MIGRATION §1 (unlisted) |
| `@GraphEdge` | LIVE | Split three ways: kernel-model attributes are Preview; 29 attributes, `PropertyMapping.transform` and `ComputedProperty` are removed at 1.0; the rest is frozen as declared | MIGRATION §1 and §2; ADR-072 obligation 35 |
| `@GraphEdges` | PARTIAL | Frozen as declared | MIGRATION §1 (unlisted) |
| `@GraphProperty` | PARTIAL | Members removed at 1.0: `encrypted` | MIGRATION §1 |
| `@GraphQuery` | RESERVED | Frozen as declared | MIGRATION §1 (unlisted) |
| `@Projection` | RESERVED | Frozen as declared (operational attributes) | MIGRATION §2 |

The `@GraphEdge` attributes `type`, `target`, `targetName`, `targetLabel` and `description` are frozen as declared. The Preview and removed sets are listed in MIGRATION §1 and §2 and in the `GraphEdge` Javadoc.

### Package `eu.exeris.sdk.annotation`: presentation

| Annotation | Extraction status | 1.0 tier | Where it is ruled |
|---|---|---|---|
| `@UI` | PARTIAL (entity level live, field level RESERVED) | Frozen as declared. Stays through 1.x. | MIGRATION §2 |
| `@UIGroup` | PARTIAL | Frozen as declared | MIGRATION §1 (unlisted) |
| `@View` | LIVE (structurally) | Frozen as declared | MIGRATION §1 (unlisted) |
| `@Region`, `@Block`, `@Bind` | LIVE (structurally) | Frozen as declared | MIGRATION §1 (unlisted) |
| `@Tab` | PARTIAL | Frozen as declared | MIGRATION §1 (unlisted) |
| `@NavMenu` | PARTIAL | Frozen as declared | MIGRATION §1 (unlisted) |

### Package `eu.exeris.sdk.annotation.capability`

| Annotation | Extraction status | 1.0 tier | Where it is ruled |
|---|---|---|---|
| `@CapabilityModule` | LIVE | Frozen as declared | [ADR-038](adr/ADR-038-capability-annotation-surface.md) |
| `@Provides` | LIVE | Frozen as declared | ADR-038 |
| `@Requires` | LIVE | Frozen as declared | ADR-038 |
| `@CapabilityLifecycle` | LIVE | Frozen as declared | ADR-038 |

### Package `eu.exeris.sdk.annotation.system`

| Annotation | Extraction status | 1.0 tier | Where it is ruled |
|---|---|---|---|
| `@TenantId`, `@Version`, `@SharedScope` | LIVE | Frozen as declared | MIGRATION §2 |
| `@AuditCreatedAt`, `@AuditCreatedBy`, `@AuditUpdatedAt`, `@AuditUpdatedBy` | LIVE | Frozen as declared | MIGRATION §2 |
| `@SoftDelete`, `@SoftDeletedBy`, `@SoftDeleteTimestamp` | LIVE | Frozen as declared | MIGRATION §2 |
| `@PrimaryKey` | PARTIAL | Frozen as declared | MIGRATION §2 |

### Package `eu.exeris.sdk.annotation.security`

| Annotation | Extraction status | 1.0 tier | Where it is ruled |
|---|---|---|---|
| `@RowLevelSecurity` (with `Policy`, `AuditLevel`) | DEPRECATED | Removed at 1.0 | MIGRATION §1 |
| `@Encrypted` (with `Algorithm`) | DEPRECATED | Removed at 1.0 | MIGRATION §1 |

The security annotations are not a reserved surface. The `security` `package-info.java` explains why the ADR-072 reservation does not apply to them.

## AST records

Package `eu.exeris.sdk.sourcemodel.ast` in `exeris-sdk-source-model`: 32 files declare a `public record`, alongside the enums `DataScope` and `ViewKind`, the annotation-twin enums `BindSource`, `BlockType` and `ParamSource`, the `RouteAccess` twin and the helper `AstLists`.

**Tier: frozen, under the record-growth rule** in [MIGRATION §3](../MIGRATION-0.x-to-1.0.md#3-consumer-contract-recap-unchanged-at-100--restated-for-the-freeze). The wire format is in the `ast` `package-info.java`.

Exceptions:

| Surface | Tier | Where it is ruled |
|---|---|---|
| `ScheduleMetadata`, `ActionMetadata.schedule` | Preview | ADR-072 obligation 31 |
| `ChannelMetadata`, `DomainMetadata.channel` | Preview | ADR-072 obligation 32 |
| `GraphEdgeMetadata` kernel-model components and the `Direction` enum | Preview | MIGRATION §2; `GraphEdgeMetadata` Javadoc |
| `DomainMetadata.tenantScoped`, `DomainMetadata.apiVersion` | Removed at 1.0. This changes every `DomainMetadata` constructor; its constructor history restarts at 1.0.0. | MIGRATION §1 and §3 |

`BlobMetadata`, `FieldMetadata.blob`, `DomainMetadata.routeAccess`, `ActionMetadata.routeAccess` and `RouteAccess` are frozen as declared (ADR-072 obligation 29).

The AST has no `internal` package. Every public type in a publishable module is contract, apart from the exceptions on this page (MIGRATION §3).

## Mutation package

Package `eu.exeris.sdk.sourcemodel.mutation` in `exeris-sdk-source-model`: `MutationOp`, `MutationResult`, `MutationPath`, `SchemaVersion`, `SourceDigest`, `BaselineTrust`, `MutationMessages`.

**Tier: frozen**, under the same record-growth rule as the AST. `SchemaVersion.CURRENT` names the wire shape separately from the artifact version; an older stamp reads as `NO_BASELINE` with cause `SCHEMA_VERSION_SKEW` (MIGRATION §3). Design: [ADR-042](adr/ADR-042-bidirectional-mutation-surface.md).

## Brief package

Package `eu.exeris.sdk.brief` in `exeris-sdk-source-model`, since 0.13: the pre-construction brief records and `BriefReadiness`. Design: [ADR-106](adr/ADR-106-pre-construction-brief-contract.md).

**Tier: additive-only from 1.0.** The discipline, including the `briefSchemaFormat` consumers pin to, is written in the `brief` `package-info.java` under "Freeze discipline". `BriefSchemaContractTest` holds the generated `brief-schema.json` to the contract. The package may use enums from `ast`; nothing under `sourcemodel` may use it, and `BriefDependencyDirectionTest` holds that direction. The record-growth guards in "How the matrix is enforced" cover `brief` records as well.

## Source-model-io entry points

Module `exeris-sdk-source-model-io`, package `eu.exeris.sdk.sourcemodel.io`: `SourceModelReader`, `SourceModelWriter`, `SourceModelConflictDetector`, `SourceModelMutationApplier`, `ApplyResult`.

**Tier: frozen.** Every public method and constructor of these classes is contract (MIGRATION §3). `ApplyResult` is a record and follows the record-growth rule. Design: [ADR-037](adr/ADR-037-source-model-io-module.md) and ADR-042.

What the reader extracts is a separate question from the tier. `SourceModelReader.unmodeledFacets()` reports source facets the reader does not carry, so a caller can detect a lossy round trip. `@Schedule` is among them (ADR-072 obligation 33). Extraction status does not change the tier.

## Composition modules

| Module | Surface | Tier | Where it is ruled |
|---|---|---|---|
| `exeris-sdk-composition-spec` | `CapManifest`, `CompositionBinding` | Frozen. `CapManifest` follows the record-growth rule. | MIGRATION §3 |
| `exeris-sdk-composition-lifecycle` | `CapabilityLifecycleHooks` | Frozen | ADR-038 |
| `exeris-sdk-composition-runtime` | `CompositionConductor`, `CompositionStampAssertion`, `CompositionStampException`, `CompositionBootException`, `DrainRunner` | Frozen | MIGRATION §3 |

The `cap-manifest.json` `schemaVersion` is 2. A manifest without `lifecycleOwner` boots hook-less (MIGRATION §3). `CompositionStampAssertion` accepts versions 1 through 2 and fails fast outside that range. The manifest itself is emitted by `exeris-tooling`; the SDK owns the record and the binding.

## Technology compatibility kit

Module `exeris-sdk-tck`, package `eu.exeris.sdk.tck`: the base `AbstractExerisTck`, the four suites `AbstractMetadataProducerTck`, `AbstractMetadataReaderTck`, `AbstractMetadataParityTck` and `AbstractMapperPostureTck`, plus `TckCorpus`, `Facet` and `TckMappers`.

**Tier: frozen**, like any other publishable module (MIGRATION §4, the `exeris-sdk-tck` item). japicmp covers it on strict defaults. A binder subclasses the abstract suites, so an added abstract method would break every binder at compile time.

## Annotation catalog

Module `exeris-sdk-annotation-catalog`: `AnnotationCatalogProcessor`, `AstSchemaProcessor` and `BriefSchemaProcessor`.

**Tier: not published, so not part of the freeze.** The module sets `maven.deploy.skip` and `japicmp.skip` in its own `pom.xml`. It is a build tool that emits resources the published jars carry, such as the annotation catalog and `brief-schema.json`.

## @exeris/ui-kit

The npm package `@exeris/ui-kit`, directory `exeris-sdk-ui-kit`, is outside the Java freeze and runs on its own version line. Its 1.0, when it comes, freezes names and not values: `--exeris-*` custom properties, `.exeris-*` classes and Tailwind keys. Colors, spacing, radii and shadows stay free to change. The classes behind `@exeris/ui-kit/preview` are outside that freeze.

Rulings: [MIGRATION §2b](../MIGRATION-0.x-to-1.0.md#2b-exerisui-kit-is-outside-this-freeze-and-has-its-own) and [ADR-094](adr/ADR-094-ui-kit-public-npm-own-version-line-tailwind-v4-only.md). The recorded name list is `exeris-sdk-ui-kit/tests/public-surface.txt`.

## How the matrix is enforced

| Surface | Gate | What it catches |
|---|---|---|
| Annotations | `AnnotationSurfaceContractTest` against `annotation-surface.txt` | An element removed or retyped, or a new element without a default |
| AST, mutation and brief records | `RecordComponentOrderTest` | A reorder of components, which no signature shows |
| AST, mutation and brief records | `RecordConstructorLedgerTest` | A published constructor arity that lost its constructor, or growth that went unrecorded |
| Published Java modules except `annotations` | japicmp, `mvn verify -Psemver` | A removed, renamed or retyped member against the baseline release |
| `@exeris/ui-kit` | `tests/public-surface.txt` | A removed or renamed public name |

The annotations module is `@Retention(SOURCE)` and has no runtime presence for japicmp to compare, which is why it sets `japicmp.skip` and uses the surface test instead. The `-Psemver` profile resolves a released baseline, so the default build does not run it; CI does.

Trade-off: the tier labels on this page are prose. Nothing checks that a row here matches the Javadoc `Status:` line. A review that changes a status or tier updates the Javadoc, the ruling and this page together.

## How a tier changes

- **Deprecation.** Removing or renaming public API follows [`.agents/policies/stability-and-deprecation.md`](../.agents/policies/stability-and-deprecation.md).
- **Promoting a Preview surface.** The kernel tier is the condition, and each surface's promotion trigger is in [MIGRATION §2](../MIGRATION-0.x-to-1.0.md#2-what-is-deliberately-not-removed-carried-through-1x) and ADR-072 obligations 29, 31 and 32.
- **Adding to a frozen surface.** The growth rules are in MIGRATION §3 for records and in `AnnotationSurfaceContractTest` for annotation attributes.

## Owning ADRs

- [ADR-072](adr/ADR-072-kernel-preview-spi-reserved-surface.md): which reserved surfaces enter the freeze, obligations 29, 31, 32, 33 and 35.
- [ADR-042](adr/ADR-042-bidirectional-mutation-surface.md): the mutation surface and baseline trust.
- [ADR-037](adr/ADR-037-source-model-io-module.md): why parsing lives in its own module.
- [ADR-038](adr/ADR-038-capability-annotation-surface.md): the capability annotations.
- [ADR-059](adr/ADR-059-data-scope-expression.md): `dataScope`, successor of `tenantScoped`.
- [ADR-094](adr/ADR-094-ui-kit-public-npm-own-version-line-tailwind-v4-only.md): `@exeris/ui-kit` versioning.
- [ADR-106](adr/ADR-106-pre-construction-brief-contract.md): the brief package.

## Where this does not apply

This page covers the SDK's own public surface. It does not describe the kernel's SPI tiers, which the kernel publishes in [its stability matrix](https://github.com/exeris-systems/exeris-kernel/blob/main/docs/stability-matrix.md). It does not say what `exeris-tooling` extracts or generates today: that is each annotation's extraction status and the tooling repository's own documentation. It makes no promise about 0.x releases, which may break between minors.
