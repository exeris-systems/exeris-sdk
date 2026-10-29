---
title: Technology Compatibility Kit
type: module
visibility: public
owning-repo: exeris-sdk
status: active
last-verified: 2026-10-29
---

# Technology Compatibility Kit

**Module:** `eu.exeris:exeris-sdk-tck` (1.0.0-SNAPSHOT), depended on with `<scope>test</scope>`
**Dependencies:** `exeris-sdk-source-model`, `junit-jupiter` and `assertj-core` (both at compile scope on purpose), and `jackson-databind`. No JavaParser and no `exeris-sdk-source-model-io`.

`exeris-sdk-tck` ships abstract JUnit 5 test classes that a producer of `exeris-metadata/<entity>.json`, a reader of `@ExerisDomain` source, or a consumer of the AST wire format extends to prove it meets the contract the SDK publishes. It turns the ADR-042 rule "the reader reads what the processor writes" into an executable gate. It never supplies an implementation of its own.

## Contract

### Suites

Every suite extends `AbstractExerisTck`. You extend the suite that matches what you built and implement its protected binding methods.

| Suite | You implement | It asks |
|---|---|---|
| `AbstractMetadataProducerTck` | `produce(String entitySource)` returning the JSON | Is the output readable, stamped, and carrying what the source declared? |
| `AbstractMetadataReaderTck` | `read(String entitySource)` returning `DomainMetadata` | The same questions from the reader side. |
| `AbstractMetadataParityTck` | both methods | Do the producer and the reader agree for every corpus entity? |
| `AbstractMapperPostureTck` | `<T> T readValue(String json, Class<T> type)` | Can your mapper read what the SDK emits? It has one case: an explicit `null` on a primitive must be tolerated. |

`AbstractMapperPostureTck` takes no Jackson type in its binding API. Its subject is your own mapper configuration, so you bind the mapper your application ships. The mapper obligations are stated in the [ast-wire-format policy](../../.agents/policies/ast-wire-format.md).

### Facets

`Facet` is an enum of the slices of the hand-off a binding may not have built yet. Override `unsupportedFacets()` to declare them, and `requireSupported(Facet)` skips the affected cases with a reason. The values are `IDENTITY`, `FIELDS`, `VALIDATION_BOUNDS`, `RELATIONSHIPS`, `ACTIONS`, `BASELINE_TRUST`, `SAGA`, `SAGA_COMPENSATION`, `SAGA_TRIGGER`, `ACTION_RESULT_TYPE`, `ROUTE_ACCESS`, `QUERY_PARAMS`, `NAV_MENU`, `TABS`, `GRAPH_PROPERTIES`, `UI_GROUPS` and `BLOBS`. Each constant's Javadoc states what it covers and, for newer ones, the release that added it.

A facet listed as unsupported is a claim that the binding emits nothing for it at all, not something approximate. Reading ahead of a producer manufactures the drift the parity suite exists to catch.

`Facet.MANDATORY` is `IDENTITY` and `FIELDS`. The final method `mandatoryFacetsAreNotDeclaredUnsupported` in `AbstractExerisTck` cannot be skipped and fails if you declare either one unsupported. Without it, a binding that declared every facet unsupported would run only skips and report green.

### Corpus and helpers

- `TckCorpus` ships three entity sources inside the jar: `Order`, `Customer` and `Product`, in package `eu.exeris.sdk.tck.corpus`. Methods are `entityNames()`, `packageName()`, `sourceOf(String)` and `all()`. A binder-supplied corpus would measure the binder's imagination, so the kit supplies it.
- The sources carry a `.java.txt` extension so nothing mistakes them for module code. The entities compile together as a unit, because `Customer` declares a relationship to `Order`. A producer that drives javac must present all of them.
- `TckMappers.canonical()` returns a fresh mapper with `FAIL_ON_NULL_FOR_PRIMITIVES` disabled. The kit uses it to read a producer's output, so the producer is never judged by a misconfigured reader. It is not what `AbstractMapperPostureTck` tests you against.
- `CorpusRouteAccess` is package-private. It holds the route access and permissions the corpus declares per entity and action, and compares them with metadata. The reader and producer suites share it. It is not API.

### Rules the kit holds itself to

- **Implementation-agnostic.** An enforcer rule in the module's `pom.xml`, `ban-javaparser-implementation-agnostic`, fails the build if JavaParser or `exeris-sdk-source-model-io` appears on the dependency tree. The reader implementation that ships in the SDK is bound to the kit from its own side: `exeris-sdk-source-model-io` depends on this module at test scope, never the reverse.
- **Compiled corpus.** `CorpusCompilesTest` compiles the corpus with `-Werror -Xlint:deprecation`. Every mandatory annotation attribute must be supplied, and nothing deprecated for removal may be used.
- **No case that cannot fail.** Each case is driven in this module's own tests against a conforming binding and one broken in exactly the way the case describes. The second must fail.

## Hot path

None at run time. The kit executes inside your test JVM, on the JUnit thread, and holds no state between cases beyond the binder's own fields. Each case calls your `produce` or `read` binding for one or more corpus sources. The cost of a run is the cost of your `produce` and `read` bindings. The kit starts no threads of its own.

## Failure modes

- **A case fails** with a message that explains the divergence and the decision behind it. That is a conformance defect in the binding.
- **A case is skipped** with the text `Skipped: this binding declares <FACET> unsupported via unsupportedFacets()`. A skip is not a pass; count them.
- **`mandatoryFacetsAreNotDeclaredUnsupported` fails** when `IDENTITY` or `FIELDS` is declared unsupported.
- **`TckCorpus.sourceOf` throws `IllegalArgumentException`** for a name outside `entityNames()`, and `IllegalStateException` if a corpus resource is missing from the jar.
- **A mapper failure in `AbstractMapperPostureTck`** surfaces as an `AssertionError` that explains the `FAIL_ON_NULL_FOR_PRIMITIVES` setting.

## Trade-off

The kit ships as a main jar, not a test-jar, unlike the kernel's TCK. That puts its surface under the japicmp semver gate, which compares main artifacts, and it freezes at 1.0.0 like every other publishable module. The cost is that JUnit and AssertJ are compile-scope dependencies here, and that static analysis sees assertions in production source. The rule `java:S5960` is suppressed per class for that reason.

A fixed shipped corpus also means a binder cannot add entities to it. It can only extend a suite class with tests of its own.

## Owning ADRs

- ADR-042, the bidirectional mutation surface and the reader-reads-what-the-processor-writes discipline: [ADR-042](../adr/ADR-042-bidirectional-mutation-surface.md).
- ADR-037, which confines JavaParser to `exeris-sdk-source-model-io`, so the kit can ban it from its own dependency tree: [ADR-037](../adr/ADR-037-source-model-io-module.md).
- ADR-054, validation bounds carried on `FieldMetadata`, which the `VALIDATION_BOUNDS` facet covers: [ADR-054](../adr/ADR-054-field-validation-min-max-pattern-cut.md).

## Where this does not apply

The kit does not test the kernel; the kernel has its own TCK. It does not test generated code, runtime behavior or the composition modules. It cannot judge an implementation that bypasses `DomainMetadata` and the `exeris-metadata/<entity>.json` format. It is not for production classpaths: depend on it at test scope.
