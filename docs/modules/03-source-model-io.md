---
title: Source model IO
type: module
visibility: public
owning-repo: exeris-sdk
status: active
last-verified: 2026-10-29
---

# Source model IO

**Module:** `eu.exeris:exeris-sdk-source-model-io` (0.13.2)

`exeris-sdk-source-model-io` owns the bridge between Java source text and the canonical model: a JavaParser-based reader, a lexical-preserving writer, a three-way conflict detector and a conflict-aware mutation applier. It exists as its own module so JavaParser stays off the dependency tree of every consumer that only needs the AST records (ADR-037).

The module never runs `javac`, never resolves symbols, and never touches the file system. Callers pass source text in and receive text or metadata back. It is not used by the codegen or processor paths, which need only [`exeris-sdk-source-model`](02-source-model.md). The consumer is the platform LSP.

## Coordinates and dependencies

| Item | Value |
|---|---|
| Group and artifact | `eu.exeris:exeris-sdk-source-model-io` |
| Published | Yes, with sources and javadoc jars |
| Compile dependencies | `exeris-sdk-source-model`, `com.github.javaparser:javaparser-core`, Jackson 3 `jackson-databind` |
| Test dependencies | `exeris-sdk-annotations`, `exeris-sdk-tck`, JUnit Jupiter, AssertJ |

This is the only SDK module that depends on JavaParser. Depending on it pulls JavaParser and Jackson databind into your tree. If you need only records, depend on the source-model module.

## Contract

All public types are in `eu.exeris.sdk.sourcemodel.io`.

| Type | Direction | Guarantee |
|---|---|---|
| `SourceModelReader` | `.java` to model | `read` returns the first `@ExerisDomain` type as `DomainMetadata`, or `Optional.empty()`. `readEnums` and `readCapabilityModule` cover enums and `@CapabilityModule`. `unmodeledFacets` names the annotations a round trip would drop |
| `SourceModelWriter` | model edit to `.java` | Eight idempotent edits: add, rename, retype and remove a field; add and remove a relationship; add and remove an action. A no-op returns the input string unchanged |
| `SourceModelConflictDetector` | three-way drift check | Compares a baseline against the current source for one `MutationOp`. Returns a `MutationResult` |
| `SourceModelMutationApplier` | detect, then write | `apply` returns an `ApplyResult` holding the verdict and the resulting source. The source changes only on `Success` |
| `ApplyResult` | result record | `outcome` and `source`. `applied()` is `true` for a convergent no-op too |

### Reader

The reader matches annotations by simple name, with no import resolution, and reads attribute values from their literals. A constant reference or expression is not folded. `@SagaStep.order` is the one exception: it also folds a same-file `final` field initialized with a literal.

A field's type is the form the processor writes, `TypeMirror.toString()`: `String` reads as `java.lang.String` and `List<Order>` as `java.util.List<com.acme.Order>`. The reader resolves names from the file alone. It looks at member and enclosing types, the file's top-level types, single-type imports and `java.lang`. A name it cannot resolve is taken to be in the file's package, or kept as written when the file has an on-demand import. A `@Relationship` field is listed in `fields` as well as in `relationships`, because the processor takes the owning side's column from it.

The reader mirrors the annotation processor's extraction, with two stated differences. It reads some facets ahead of the processor, and the `SourceModelReader` javadoc lists them with the shape ADR-042 fixes. It leaves `@Channel` and `@Schedule` unread.

`unmodeledFacets` is the safety check for reattach. For a source with an `@ExerisDomain` or `@CapabilityModule` type, its result is non-empty when the source carries `@Channel`, `@Schedule`, a `@SagaStep` whose order cannot be resolved, or a non-literal `permissions` element. A caller doing a round trip should refuse or warn when it is non-empty, because a baseline from the processor may hold what the reader dropped.

### Writer

The writer uses JavaParser's `LexicalPreservingPrinter`, so untouched nodes keep their formatting, comments and non-Exeris annotations byte for byte. Edits act on declarations only. References such as getters or call sites are not updated, because that needs symbol solving.

### Detector

The conflict rule is written once, in ADR-042 obligation 4 and the `SourceModelConflictDetector` javadoc. A user who already made the edit produces `Success`, not a conflict.

A type in an operation is source text, the text the writer puts in the declaration. When the detector checks whether the user already made a `ChangeFieldType` or `AddField` edit, that text matches the qualified type the reader gives if the two are equal once package qualifiers are dropped and whitespace is normalised. So `BigDecimal` matches `java.math.BigDecimal`.

The two-`DomainMetadata` `detect` is pure. The overloads that take baseline JSON and source text add the trust gate first, through `checkBaselineTrust`. The gate returns `NoBaseline` for a null, blank or unparseable baseline (`MISSING_BASELINE`) and for a `schemaVersion` other than `SchemaVersion.CURRENT` (`SCHEMA_VERSION_SKEW`). A baseline without a `sourceDigest` is still trusted. Overloads taking a `List<MutationOp>` check each op independently against the same pair.

### Applier

`apply(op, baselineJson, currentSource)` and its four-argument form with a concurrency token run in this order:

1. If a token is supplied and differs from `SourceDigest.of(currentSource)`, return `NoBaseline` with cause `STALE_DIGEST`.
2. Reject `ChangeRelationshipCardinality` as `ValidationError`. No writer edit sits behind it.
3. Run detection. Any outcome other than `Success` is returned with the input source untouched.
4. Write through `SourceModelWriter`.

Only single-op application is offered. Batch application is deferred, because op *n* would see ops 1 to *n*-1 as user drift.

### Guard tests

All live under `exeris-sdk-source-model-io/src/test/java/eu/exeris/sdk/sourcemodel/io/`.

| Test | What it holds |
|---|---|
| `SourceModelReaderProcessorParityTest` and the `SourceModelReader*ParityTest` family | Reader output against JSON recorded from the `exeris-tooling` processor, and against the ADR-042 shapes for facets read ahead of it |
| `SourceModelReaderTckTest` | The reader against the SDK's reader TCK in [`exeris-sdk-tck`](08-tck.md) |
| `SourceModelConflictDetectorTest`, `SourceModelConflictDetectorBaselineTest` | The conflict rule and the trust gate |
| `SourceModelMutationApplierTest` | The apply flow and its outcomes |
| `BudgetHqCorpusRoundTripTest` | Read completeness and mutate-then-invert over a corpus of ported entities, and the byte-level limits of removal |
| `SourceModelIoTest` | Reader and writer behavior |

The build runs a JaCoCo `check` at `verify` on this module: 0.85 covered ratio for instructions and lines. Under the opt-in `semver` profile (`-Psemver`) the module also runs a japicmp gate, with `ApplyResult` excluded from the first execution and a separate execution covering record growth.

## Hot path

None at application runtime. These classes run at design time, inside an editor or language server, and at build time if a tool chooses to call them.

Threading and allocation, as the javadoc states them:

- `SourceModelReader` and `SourceModelWriter` each hold one `JavaParser` and are **not thread-safe**. Use one instance per call site or guard it externally.
- `SourceModelConflictDetector` is stateless. Its string overloads create a `SourceModelReader` per call, and its `ObjectMapper` is thread-safe.
- `SourceModelMutationApplier` holds a `SourceModelWriter`, so it is not safe to share between threads. Create one applier per call site unless you synchronize.
- Every call re-parses its input. Nothing is cached between calls.
- The parser language level is JavaParser's `CURRENT`.

Trade-off: simple-name matching and literal-only values keep the module light and usable without a compiler. They also mean an aliased annotation or a constant-valued attribute can read differently here than in the processor. `unmodeledFacets` covers some of those cases, not all.

## Failure modes

| Call | What the caller sees |
|---|---|
| `SourceModelReader.read` on source with no `@ExerisDomain` type | `Optional.empty()` |
| `read`, `readEnums`, `readCapabilityModule`, `unmodeledFacets` on invalid Java | `IllegalArgumentException` ("Source is not valid Java") |
| `SourceModelWriter` edit on invalid Java, or on source with no `@ExerisDomain` type | `IllegalArgumentException` |
| Writer edit that is already satisfied (field exists, name absent, type equal) | The input string, unchanged |
| The `DomainMetadata` overload of `detect` with a null op, baseline or current; the string overload with a null op or `currentSource` | `NullPointerException` |
| `detect(op, baselineJson, currentSource)` with an unparseable `currentSource` | `IllegalArgumentException` from the reader |
| `detect` where the source declares no `@ExerisDomain` type | `ValidationError` |
| `detect` with a malformed path, a path for another entity, or a path kind the op does not accept | `ValidationError` |
| `detect` with a missing, unparseable or schema-skewed baseline | `NoBaseline` with `MISSING_BASELINE` or `SCHEMA_VERSION_SKEW` |
| `detect` where the target drifted to a value different from baseline and intent | `Conflict` with `path`, `baselineValue`, `currentValue`, `intendedValue` |
| `apply` | Does not throw for a parse or writer failure. Those become `ValidationError` with the input source returned. A null `op` or `currentSource` still throws `NullPointerException` |
| Removal edits | Content and AST equality hold; byte equality with the pre-add source does not. Compare ASTs, not text |

Handle the four `MutationResult` variants exhaustively: `Success`, `Conflict`, `ValidationError`, `NoBaseline`. The SDK reports; your host decides whether to merge, prompt or auto-apply.

## Owning ADRs

| ADR | Decision |
|---|---|
| [ADR-037](../adr/ADR-037-source-model-io-module.md) | The parser and writer live in a separate module to keep JavaParser off the AST consumers |
| [ADR-038](../adr/ADR-038-capability-annotation-surface.md) | The reader persists capability service names as written in source |
| [ADR-042](../adr/ADR-042-bidirectional-mutation-surface.md) | The mutation vocabulary, baseline trust, three-way conflict rule and apply-time concurrency token |
| [ADR-069](../adr/ADR-069-jdk-baseline-lts.md) | JDK 25 LTS baseline for the module's class files |

The RFC behind the detector is [source model conflict resolution](../rfc/RFC-2026-06-11-source-model-conflict-resolution.md).

## Where this does not apply

- **Symbol-aware refactoring.** The writer does not update references, resolve imports or rename usages.
- **Multi-file or batch edits.** The applier handles one op against one source. Batch application is not available today.
- **Cardinality changes.** `ChangeRelationshipCardinality` can be detected but is rejected at apply time.
- **Annotation processing at build time.** The `exeris-tooling` processor owns that path and uses only the records.
- **Facets the reader leaves unread.** `@Channel` and `@Schedule` are not read; check `unmodeledFacets` before trusting a round trip.
- **Concurrent use of one instance.** See the threading list above.
