# Exeris SDK TCK

**Module:** `eu.exeris:exeris-sdk-tck`
**Role:** Technology Compatibility Kit for the build-time metadata hand-off

## What this is for

The SDK publishes a format — `exeris-metadata/<entity>.json` — and a discipline: ADR-042's *the
reader reads what the processor writes*. The discipline has been enforced by whoever remembered it,
and the record of that is not good. Three parity defects shipped, all the same shape:

| defect | what it did |
|---|---|
| `@ExerisDomain(name = …)` | the reader read an attribute the annotation never declared, so it could return a different entity identity than the producer for the same file |
| `@Relationship.relationshipType` | the processor read the cardinality under the AST's name for it, so every edge silently came back as the builder default and the wrong side generated the foreign key |
| `@ActionParam.label` | the processor reads it under `displayName`, a key the annotation does not declare, so the value is null on every run and a fallback hides it completely |

They are hard to catch for one reason: **nothing fails**. Both sides emit well-formed metadata, no
exception is raised, no diagnostic appears. The defect is only visible if you compare the two
outputs — which is what this kit does.

## Using it

```xml
<dependency>
    <groupId>eu.exeris</groupId>
    <artifactId>exeris-sdk-tck</artifactId>
    <version>${exeris.sdk.version}</version>
    <scope>test</scope>
</dependency>
```

Extend the suite that matches what you built, implement its binding method(s), and it runs:

```java
class MyParityTckTest extends AbstractMetadataParityTck {
    protected String produce(String src) { return MyProcessor.emitJson(src); }
    protected DomainMetadata read(String src) { return new MyReader().read(src); }
}
```

| suite | binds | asks |
|---|---|---|
| `AbstractMetadataProducerTck` | `produce(source) → json` | is the output readable, stamped, and carrying what the source declared? |
| `AbstractMetadataReaderTck` | `read(source) → DomainMetadata` | the same, from the other side |
| `AbstractMetadataParityTck` | both | do they agree? |
| `AbstractMapperPostureTck` | `readValue(json, type)` | can your mapper read what we emit? |

The corpus is shipped inside the jar (`TckCorpus`), not left to the binder — a binder-supplied
corpus measures the binder's imagination. Each entity carries a case with a defect behind it.

It is also **compiled** code that happens to travel as a resource: a producer binding drives javac
over it, and nothing in this repository would otherwise. `CorpusCompilesTest` compiles it under
`-Werror -Xlint:deprecation`, which enforces two rules at once — every mandatory attribute is
supplied, and no attribute deprecated for removal is used. Both had already been broken when the
guard was written: the corpus was missing `@Action.label`, `@Relationship.targetEntity` and
`@Relationship.displayField`, so it could not have compiled for any binder, and it declared its
tenancy tier through `@ExerisDomain.tenantScoped` — removed at 1.0.0, so the kit would have stopped
compiling at exactly the release it exists to guard.

## Facets

A facet you have not built yet is declared, and its cases skip:

```java
protected Set<Facet> unsupportedFacets() { return Set.of(Facet.ACTIONS); }
```

This is not politeness. Under ADR-042 a reader that reads ahead of its producer *manufactures* the
drift the parity gate exists to catch, so an unbuilt facet must not be asserted on.

`Facet.MANDATORY` — identity and fields — cannot be declared unsupported, and a case that cannot be
skipped enforces it. Without that, a binding declaring everything unsupported would run a suite of
skips and report green, which reads as conformance and is worse than a failure.

## Why this is a main jar, not a test-jar

`exeris-kernel-tck` ships its abstract bases from `src/test/java` and publishes a `test-jar`. This
one does not, and the difference is deliberate: the kit's surface freezes at 1.0.0 like every other
publishable module here, and japicmp — which guards a frozen surface — compares main artifacts.

The cost is that static analysis reads `src/main/java` as production code and flags the assertions
as residue (`java:S5960`). In a TCK the assertions *are* the artifact: a binder depends on this jar
to run them, and a TCK with its assertions stripped is an empty interface. The rule is suppressed
per class with that rationale rather than satisfied by moving the code, which would trade the
semver guard for a clean report on a rule that does not apply.

## Two rules the kit holds itself to

**Implementation-agnostic.** A binder supplies the implementation; the kit never reaches for one. An
enforcer rule keeps JavaParser and `exeris-sdk-source-model-io` off the dependency tree, because a
kit that could reach the SDK's own reader would quietly be testing that instead of the binding.

**No case that cannot fail.** Every case is driven, in this module's own tests, against a conforming
binding and one broken in exactly the way the case describes; the second must fail. Adding a case
means adding that pair.

The rule is not ceremony. It removed three of the four cases originally written for
`AbstractMapperPostureTck`, each asserting something true that no binding could get wrong:

- an unknown property cannot break a consumer — the AST records carry `@JsonIgnoreProperties(ignoreUnknown = true)` themselves;
- a zero-valued bound cannot be lost on write — a per-component `@JsonInclude(NON_NULL)` beats a mapper-wide default inclusion;
- a polymorphic `MutationOp` resolves its subtype on a stock mapper — `@JsonTypeInfo` drives it.

Each is a real guarantee, and each is the *record's* to keep rather than the consumer's. As TCK
cases they would have passed for every binding ever written.

The same measurement corrected a claim this repo had carried since the format was written: that
`@JsonInclude(NON_DEFAULT)` makes absent fields "arrive as `null`", which is why consumers must set
`FAIL_ON_NULL_FOR_PRIMITIVES = false`. An absent property binds the primitive's own default and
raises nothing. An **explicit** null throws. The obligation is real — a baseline is a file the
reader did not necessarily write — but it does not follow from our own writer's inclusion posture.
