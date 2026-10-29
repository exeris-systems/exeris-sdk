# Exeris SDK TCK

> Contract, hot path, failure modes and owning ADRs for this module are owned by [`docs/modules/08-tck.md`](../docs/modules/08-tck.md). This README carries the background.

**Module:** `eu.exeris:exeris-sdk-tck`
**Role:** Technology Compatibility Kit for the build-time metadata hand-off

## What this is for

The SDK publishes a format — `exeris-metadata/<entity>.json` — and a discipline: ADR-042's *the
reader reads what the processor writes*. The defect it guards against has one shape: one side reads an attribute under a key the other does not use.

It is hard to catch for one reason: **nothing fails**. Both sides emit well-formed metadata, no
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

The four suites, what each binds and what each asks are listed in [the module page](../docs/modules/08-tck.md#suites).

`exeris-sdk-source-model-io`'s `SourceModelReaderTckTest` is a working reader binding.

The corpus is shipped inside the jar (`TckCorpus`), not left to the binder — a binder-supplied
corpus measures the binder's imagination. Each entity carries a case with a defect behind it.

It is also **compiled** code that happens to travel as a resource: a producer binding drives javac
over it, and nothing in this repository would otherwise. `CorpusCompilesTest` compiles it under
`-Werror -Xlint:deprecation`, which enforces two rules at once — every mandatory attribute is
supplied, and no attribute deprecated for removal is used.

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

The kit ships as a main jar so that japicmp, which compares main artifacts, guards its surface. The trade-off is stated in [the module page](../docs/modules/08-tck.md#trade-off).

## Two rules the kit holds itself to

**Implementation-agnostic.** A binder supplies the implementation; the kit never reaches for one. An
enforcer rule keeps JavaParser and `exeris-sdk-source-model-io` off the dependency tree, because a
kit that could reach the SDK's own reader would quietly be testing that instead of the binding.

**No case that cannot fail.** Every case is driven, in this module's own tests, against a conforming
binding and one broken in exactly the way the case describes; the second must fail. Adding a case
means adding that pair. That is why `AbstractMapperPostureTck` has one case: the other guarantees on
the wire format belong to the AST records, not to the consumer's mapper, and are documented in the
javadoc of that class.

An absent property binds the primitive's own default and raises nothing. An **explicit** null throws
on a default Jackson 3 mapper, so consumers must set `FAIL_ON_NULL_FOR_PRIMITIVES = false`: a
baseline is a file the reader did not necessarily write.
