---
title: Annotation catalog
type: module
visibility: public
owning-repo: exeris-sdk
status: active
last-verified: 2026-10-29
---

# Annotation catalog

**Module:** `eu.exeris:exeris-sdk-annotation-catalog` (0.13.0-SNAPSHOT)

`exeris-sdk-annotation-catalog` owns three javac annotation processors that the SDK build runs on its own sources. They emit machine-readable descriptions of the SDK: the annotation catalog, the AST JSON Schema and the brief JSON Schema. Each lands inside the jar of the module it processes.

The module never ships to consumers and never runs in an application. It is a build tool. The contract that matters is the JSON it emits, not its Java API.

## Coordinates and dependencies

| Item | Value |
|---|---|
| Group and artifact | `eu.exeris:exeris-sdk-annotation-catalog` |
| Published | No. `maven.deploy.skip` is `true` in its `pom.xml` and `japicmp.skip` is set; there is no released baseline |
| Compile dependency | Jackson 3 `jackson-databind`, for serialization only |
| Test dependencies | JUnit Jupiter, AssertJ |

The SDK's own modules reach it through `<annotationProcessorPaths>`, which is a javac classpath. It is not part of the dependency tree of `exeris-sdk-annotations` or `exeris-sdk-source-model`, so their published jars stay free of databind and of the emitters.

The processors need javac's Compiler Tree API to read javadoc. Each processor throws an `IllegalStateException` at `init` if the environment lacks it, rather than emitting output without prose. Build on a JDK.

## Contract

| Processor | Registered | Runs over | Emits |
|---|---|---|---|
| `AnnotationCatalogProcessor` | In `META-INF/services/javax.annotation.processing.Processor` | `exeris-sdk-annotations` | `META-INF/exeris/annotation-catalog.json` |
| `AstSchemaProcessor` | Named explicitly in the `exeris-sdk-source-model` compiler configuration | `exeris-sdk-source-model` | `META-INF/exeris/ast-schema.json` |
| `BriefSchemaProcessor` | Named explicitly in the `exeris-sdk-source-model` compiler configuration | `exeris-sdk-source-model` | `META-INF/exeris/brief-schema.json` |

All three are observers: `process` returns `false`, claiming nothing. Each writes once, when `roundEnv.processingOver()` is true. The AST and brief processors write only if they saw their records.

Guarantees shared by all three:

- **Deterministic output.** No timestamp appears in the output. Two builds of one commit produce identical bytes.
- **Prose included.** Javadoc is carried into the JSON, which a class file cannot supply.
- **Closed inputs.** Each processor refuses to write if a required `-A` option is missing, and reports a compile error instead.

### Annotation catalog

Root keys: `catalogFormat` (an integer, currently 1), `sdkVersion`, `annotationCount`, `annotations` (sorted by qualified name) and `packages` (with package-level prose).

Each annotation entry records its identity (name, qualified name, package, and the enclosing type when nested), `targets`, retention and repeatable container. It also lists attributes with type, required flag, default and enum constants. Deprecations carry `forRemoval`, `since` and the canonical replacement. Purpose and description prose come last. The processor descends into nested annotation types, because an author writes those too.

Option: `-Aexeris.catalog.sdkVersion`, set from `${project.version}`. `catalogFormat` moves when a consumer must change to keep reading the file, independently of the SDK version.

### AST schema

A JSON Schema, draft 2020-12, of the `exeris-metadata/<entity>.json` format. Root keys include `schemaFormat` (currently 1), `sdkVersion`, `astSchemaVersion`, `definitionCount`, `x-exeris-prose-coverage`, `x-exeris-reader-requirements` and a `$ref` to `DomainMetadata`. Definitions are the records and enums in `eu.exeris.sdk.sourcemodel.ast`.

The schema carries what the types alone cannot say. Primitive-typed properties are flagged `x-exeris-primitive`, the set on which an explicit `null` is fatal without `FAIL_ON_NULL_FOR_PRIMITIVES = false`. Each definition records its inclusion posture as `x-exeris-json-include`. The schema has no `required` list on purpose: every property may legitimately be absent from a document the SDK itself wrote.

Options: `-Aexeris.schema.sdkVersion` and `-Aexeris.schema.astVersion`. The AST version is passed in, not read from `SchemaVersion`, because that constant is deliberately not a compile-time constant. `AstSchemaContractTest` in `exeris-sdk-source-model` fails if the two disagree.

### Brief schema

A JSON Schema of the `eu.exeris.sdk.brief.Brief` record, rooted there, with `briefSchemaFormat` an integer a consumer pins to. It differs from the AST schema in three ways:

- **Closed objects.** Every object is `additionalProperties: false`, so a re-encoded source structure fails validation.
- **Constraints.** `required`, `minItems`, patterns and the per-kind EARS and open-question rules come from the package-private `SchemaRules` annotations on the records.
- **Reachable types only.** Definitions are the types reachable from `Brief`, so a helper such as the readiness report never becomes wire format.

The concern-hint vocabulary is the set of top-level annotation names from the annotation sources, minus annotations deprecated for removal and minus repeatable containers. The processor reads those sources directly, so it does not depend on the catalog processor's output or on module build order.

Options: `-Aexeris.schema.sdkVersion`, `-Aexeris.brief.schemaFormat` (an integer) and `-Aexeris.brief.annotationSources` (a directory, set to the `exeris-sdk-annotations` source root).

### Guard tests

| Test | Module | What it holds |
|---|---|---|
| `AnnotationCatalogProcessorTest`, `AstSchemaProcessorTest`, `BriefSchemaProcessorTest` | `exeris-sdk-annotation-catalog` | Each processor's behavior |
| `AnnotationCatalogContractTest` | `exeris-sdk-annotations` | The catalog against reflection over the compiled classes. The two share no mechanism, so a defect shows as disagreement |
| `AstSchemaContractTest` | `exeris-sdk-source-model` | The AST schema against the compiled records |
| `BriefSchemaContractTest` | `exeris-sdk-source-model` | The brief schema against the compiled records, the annotation catalog and a draft 2020-12 validator |

The module carries a JaCoCo `check` at `verify`: 0.85 covered ratio for instructions and lines, over the bundle.

## Hot path

None at application runtime. The processors run once per compilation of the module they process, in the `default-compile` execution. The test-compile executions set `proc` to `none`, so the schema does not describe test fixtures and the output file is not opened twice.

State is held in `TreeMap` and `TreeSet` instances for the lifetime of one compilation, which keeps the output sorted. The classes make no thread-safety promise.

## Failure modes

| Situation | What the builder sees |
|---|---|
| A required `-A` option is missing or blank | A javac `ERROR` diagnostic naming the option. No file is written |
| `-Aexeris.brief.schemaFormat` is not an integer, or `-Aexeris.brief.annotationSources` names no directory | A javac `ERROR` diagnostic |
| The processing environment lacks the Compiler Tree API | `IllegalStateException` at `init`. No degraded catalog |
| The output resource cannot be written | A javac `ERROR` diagnostic naming the resource |
| The catalog disagrees with the compiled annotations | `AnnotationCatalogContractTest` fails the `exeris-sdk-annotations` build |
| The AST version option disagrees with `SchemaVersion.CURRENT` | `AstSchemaContractTest` fails the `exeris-sdk-source-model` build |

A consumer of the emitted files sees none of this. It sees a file, or the build that produced the jar failed.

Trade-off: generating from sources makes the output richer than reflection, with prose and replacement names. It also ties the build to a JDK with the Compiler Tree API, and Maven does not treat a processor path as a reactor edge, so the root `pom.xml` orders this module first by declaration. A partial build of `exeris-sdk-annotations` on a clean checkout fails until this module has been installed once.

## Owning ADRs

| ADR | Decision |
|---|---|
| [ADR-003](../adr/ADR-003-entity-first-development-strategy.md) | The brief schema stays closed so it cannot restate the domain |
| [ADR-042](../adr/ADR-042-bidirectional-mutation-surface.md) | The wire-format version that `astSchemaVersion` pairs with a baseline stamp |
| [ADR-106](../adr/ADR-106-pre-construction-brief-contract.md) | The brief contract that `BriefSchemaProcessor` emits |

No ADR is dedicated to the annotation catalog or to the AST schema. Their rules are in [javadoc and contract emitters](../../.agents/policies/javadoc-and-contract-emitters.md).

## Where this does not apply

- **Consuming the catalog from Java.** Read `META-INF/exeris/annotation-catalog.json` from the `exeris-sdk-annotations` jar. Do not depend on this module.
- **Runtime reflection.** The annotations have `SOURCE` retention; the catalog is the supported way to learn their shape after compilation.
- **Application code generation.** `exeris-tooling` generates code. These processors emit descriptions of the SDK, not of your domain.
- **Validating your own `@ExerisDomain` classes.** The schemas describe the AST and the brief. Your annotated class is checked by the tooling processor.
- **Published API stability.** This module has no released baseline. Only the emitted JSON is a contract, and each file's format number is its version.
