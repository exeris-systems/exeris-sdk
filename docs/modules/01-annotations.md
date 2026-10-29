---
title: Annotations
type: module
visibility: public
owning-repo: exeris-sdk
status: active
last-verified: 2026-10-29
---

# Annotations

**Module:** `eu.exeris:exeris-sdk-annotations` (0.13.0-SNAPSHOT)

`exeris-sdk-annotations` owns the annotation vocabulary an application author writes on a domain class: `@ExerisDomain`, `@Field`, `@Action`, `@Relationship`, `@Saga`, `@UI` and their relatives. The annotations describe shape and intent. They carry no behavior.

The module never runs code in your application, never reads a source file, and never holds the AST. Turning annotations into metadata is the job of `exeris-tooling` (build time) or of [`exeris-sdk-source-model-io`](03-source-model-io.md) (design time). The records those readers produce live in [`exeris-sdk-source-model`](02-source-model.md).

## Coordinates and dependencies

| Item | Value |
|---|---|
| Group and artifact | `eu.exeris:exeris-sdk-annotations` |
| Packaging | `jar`, with a sources jar and a javadoc jar attached |
| Compile and runtime dependencies | None |
| Test dependencies | JUnit Jupiter, AssertJ, Jackson 3 `jackson-databind` (reads the generated catalog back) |
| Published | Yes. Maven Central from the 0.12.0 line; see the quick start in the `eu.exeris.sdk.annotation` package-info for the scope to use |

The dependency-free claim is enforced, not stated. The module's `pom.xml` binds a `maven-enforcer-plugin` rule (`ban-javaparser-zero-coupling`) that fails the `verify` phase if `com.github.javaparser` reaches the dependency tree. The invariant itself is written once, in [zero runtime coupling](../../.agents/policies/zero-runtime-coupling.md).

The annotation catalog is generated into this module's jar during `compile`. `exeris-sdk-annotation-catalog` sits on the compiler's processor path, not in the dependency tree. See [Annotation catalog](04-annotation-catalog.md) for what it emits.

## Contract

Every annotation here is `@Retention(RetentionPolicy.SOURCE)` and declares an explicit `@Target`. Nothing survives into a class file, so nothing is on your runtime classpath. Use the `provided` scope.

The surface is split across four packages:

| Package | Holds | Notes |
|---|---|---|
| `eu.exeris.sdk.annotation` | Top-level annotation types, including `@ExerisDomain`, `@Field`, `@Validation`, `@Action`, `@Relationship`, `@Saga`, `@DomainEvent`, `@UI`, `@View`, `@RouteAccess`, `@Blob` | The entity-first core. Repeatable annotations ship a public container; four are top-level (`GraphEdges`, `Rules`, `SagaSteps`, `SagaTransitions`) |
| `eu.exeris.sdk.annotation.system` | System-field markers, including `@PrimaryKey`, `@TenantId`, `@SharedScope`, `@Version`, `@SoftDelete`, `@AuditCreatedAt` | Name the field that plays a system role |
| `eu.exeris.sdk.annotation.security` | `@Encrypted`, `@RowLevelSecurity` | Row-level security and field encryption declarations |
| `eu.exeris.sdk.annotation.capability` | `@CapabilityModule`, `@Provides`, `@Requires`, `@CapabilityLifecycle` | Capability composition; see ADR-038 |

The guarantees a caller can rely on:

- **Status is stated per annotation.** The `eu.exeris.sdk.annotation` package-info defines four labels: LIVE, PARTIAL, RESERVED and DEPRECATED. Each annotation's javadoc states its status with one of them. Existence of an attribute does not mean a generator reads it; read the label.
- **Compatibility has three rules.** No element is removed, no element is retyped, and a new element on a shipped annotation declares a `default`. A removal window opens with `@Deprecated(forRemoval = true)` and a named replacement. The removal list for 1.0.0 lives in section 1 of [`MIGRATION-0.x-to-1.0.md`](../../MIGRATION-0.x-to-1.0.md); what stays is section 2.
- **Annotations are siblings, not nested.** A `@Validation` sits beside its `@Field`, never inside it. The package-info explains the nested-form trap in full.
- **`@Field` and `@Validation` have split scopes.** The split is owned by [field and validation scoping](../../.agents/policies/field-validation-scoping.md) and ADR-054.
- **The annotation catalog describes this surface.** `META-INF/exeris/annotation-catalog.json` ships in the jar and lists names, targets, retention, attributes with defaults, and deprecations with their replacements.

Trade-off: a `SOURCE` retention keeps the annotations out of every runtime image, but it also means a library cannot read them by reflection. Anything that needs the declarations after compilation must read the emitted `exeris-metadata/<entity>.json`.

### Guard tests

Four tests and one helper live in `exeris-sdk-annotations/src/test/java/eu/exeris/sdk/annotation/`.

| Test | What it holds |
|---|---|
| `AnnotationContractTest` | Walks every `@interface` under `eu.exeris.sdk.annotation.*`, including new subpackages, and asserts `SOURCE` retention, a declared `@Target`, and public `@Repeatable` containers |
| `AnnotationSurfaceContractTest` | Compares the compiled surface to `src/test/resources/annotation-surface.txt`: no element removed, none retyped, every new element on a shipped annotation has a default |
| `AnnotationCatalogContractTest` | Checks the generated catalog against reflection over the compiled classes, through a mechanism independent of the processor |
| `ClassFileBaselineTest` | Reads the class-file header and fails if the emitted major version exceeds 69 (Java 25); see ADR-069 |
| `DeclaredAnnotations` | Shared helper that enumerates declared annotations for the tests above; not a test itself |

The module runs no japicmp execution (`japicmp.skip` is set in its `pom.xml`). Binary compatibility is meaningless for `SOURCE` annotations, and japicmp reports a defaulted new element as an abstract-method addition. `AnnotationSurfaceContractTest` replaces it.

The module carries no JaCoCo gate. Annotations have no executable code to measure. The 0.85 coverage gate applies to the modules that do; see [Source model](02-source-model.md).

## Hot path

There is none at application runtime. The module is on your compile classpath and absent from your runtime image.

Annotations are read in two places, both outside your running service:

1. At build time, `exeris-tooling`'s annotation processor reads them through `javax.lang.model` and writes `exeris-metadata/<entity>.json`.
2. At design time, `SourceModelReader` in [`exeris-sdk-source-model-io`](03-source-model-io.md) parses source text with JavaParser. It matches annotations by simple name, with no import resolution.

The two readers must agree on what they extract. Where one reads an attribute ahead of the other, the javadoc of that attribute says so.

## Failure modes

Because the annotations are inert, most mistakes fail silently rather than loudly.

| Mistake | What you see |
|---|---|
| Annotation compiles but no generator reads it (RESERVED, or PARTIAL with no consumer) | Nothing. The build passes and the output is unchanged |
| Nested form instead of sibling form | Nothing. The metadata is dropped |
| Removed or retyped element in an SDK change | `AnnotationSurfaceContractTest` fails the SDK build; in your code, a compile error |
| A runtime-retained or untargeted annotation added to the SDK | `AnnotationContractTest` fails the SDK build |
| Java 26 class files emitted by the SDK build | `ClassFileBaselineTest` fails; a Java 25 consumer's `javac` would reject the jar |
| A missing `-Aexeris.catalog.sdkVersion` option | The catalog processor reports a compile error rather than stamping an unknown version |

The status labels exist to catch the first two rows before you hit them. Consult the label before relying on an attribute.

## Owning ADRs

| ADR | Decision |
|---|---|
| [ADR-003](../adr/ADR-003-entity-first-development-strategy.md) | The annotated class is the only definition of the domain |
| [ADR-038](../adr/ADR-038-capability-annotation-surface.md) | The capability annotation surface: `@CapabilityModule`, `@Provides`, `@Requires`, `@CapabilityLifecycle` |
| [ADR-054](../adr/ADR-054-field-validation-min-max-pattern-cut.md) | `@Validation` is the sole declaration site for bounds and pattern |
| [ADR-059](../adr/ADR-059-data-scope-expression.md) | `DataScope { GLOBAL, TENANT, UNIVERSE }` supersedes the `tenantScoped` boolean |
| [ADR-069](../adr/ADR-069-jdk-baseline-lts.md) | The SDK baselines on JDK 25 LTS and ships one artifact line |
| [ADR-072](../adr/ADR-072-kernel-preview-spi-reserved-surface.md) | The design-time surface for the kernel preview SPI: `@RouteAccess` and `@Blob` are inside the 1.0.0 freeze, `@Schedule` and `@Channel` stay reserved and outside it |

## Where this does not apply

- **Runtime behavior.** These annotations configure nothing in a running process. Kernel and Spring behavior is documented in the kernel and spring-runtime repositories.
- **Code generation rules.** What a generator emits for an annotation belongs to `exeris-tooling`. This page covers the vocabulary only.
- **The AST.** The record shapes, their JSON form and their growth rules are on the [Source model](02-source-model.md) page.
- **The UI kit.** `@exeris/ui-kit` is an npm package on its own version line and is not part of this Maven module.
- **Stability of every attribute.** `@Schedule`, `@Channel` and the kernel-model graph-edge attributes are outside the 1.0.0 freeze. Section 2 of [`MIGRATION-0.x-to-1.0.md`](../../MIGRATION-0.x-to-1.0.md) lists what is frozen and what is not.
