---
title: Capabilities and composition
type: howto
visibility: public
owning-repo: exeris-sdk
status: active
last-verified: 2026-10-29
---

# Capabilities and composition

**Audience:** you are declaring a capability, the services it provides and requires, and the
lifecycle hooks the SKU bootstrap drives at boot.

> **Verified against** `0.13.0-SNAPSHOT` at commit `33574011`, 2026-10-29. Every snippet is quoted
> or minimally adapted from the cited file, by path and symbol, never a line range. If a snippet and
> its source disagree, **the source wins and this guide is the bug**.

## Prerequisites

- [01 Platform and dependencies](01-platform-and-dependencies.md): the annotations and
  `exeris-sdk-composition-lifecycle` on the compile classpath, and `exeris-sdk-composition-runtime`
  on the SKU's runtime classpath.
- The `capability` example in `exeris-sdk-examples`, which this page walks.

## Steps

### 1. Declare the capability and its lifecycle owner

A `@CapabilityModule` class states what the capability provides and what it requires. Its
`@CapabilityLifecycle` owner implements `CapabilityLifecycleHooks`. Both sit in one source file,
because the reader resolves the lifecycle owner from the same compilation unit. The owner is the
public type: the conductor instantiates it reflectively through a public no-arg constructor.

Source: `exeris-sdk-examples/src/main/java/eu/exeris/sdk/examples/capability/LedgerCapLifecycle.java`, `LedgerCap` (quoted)

```java
@CapabilityModule
@Provides(service = LedgerService.class, version = "1.0.0")
@Requires(service = AuditTrail.class, versionRange = "[1.0.0,2.0.0)")
@Requires(service = ExchangeRates.class, optional = true)
class LedgerCap {
}
```

Source: `exeris-sdk-examples/src/main/java/eu/exeris/sdk/examples/capability/LedgerCapLifecycle.java`, `LedgerCapLifecycle` (adapted — the phase recorder and two of the four hooks removed)

```java
@CapabilityLifecycle
public class LedgerCapLifecycle implements CapabilityLifecycleHooks {

    @Override
    public void initialize() {
        PHASES.add("initialize");
    }

    @Override
    public void drain(Duration remaining) {
        PHASES.add("drain");
    }
}
```

The four phases and what each may do are the contract of
[composition lifecycle](../modules/06-composition-lifecycle.md).

### 2. Read the declaration back

`SourceModelReader.readCapabilityModule` returns the declaration as AST records. Services are
carried as written in the source, so `LedgerService.class` reads back as `LedgerService`.

Source: `exeris-sdk-examples/src/test/java/eu/exeris/sdk/examples/capability/CapabilityExampleTest.java`, `declaredSurface` (quoted)

```java
CapabilityModuleMetadata cap = new SourceModelReader()
        .readCapabilityModule(Files.readString(SOURCE)).orElseThrow();

assertThat(cap.provides()).containsExactly(ProvidesMetadata.of("LedgerService", "1.0.0"));
assertThat(cap.requires()).containsExactly(
        RequiresMetadata.of("AuditTrail", "[1.0.0,2.0.0)"),
        RequiresMetadata.optional("ExchangeRates"));
assertThat(cap.lifecycleOwner()).isEqualTo("eu.exeris.sdk.examples.capability.LedgerCapLifecycle");
```

### 3. Assert the manifest stamp at boot

The build emits `cap-manifest.json`, with fully qualified service names and a stamp whose
`contentBinding` is computed by `CompositionBinding.compute`. The SKU bootstrap calls
`CompositionStampAssertion.assertConsistent` before any capability enters `initialize`. The
example's manifest is `exeris-sdk-examples/src/test/resources/capability/cap-manifest.json`; its
schema is the contract of [composition spec](../modules/05-composition-spec.md).

Source: `exeris-sdk-examples/src/test/java/eu/exeris/sdk/examples/capability/CapabilityExampleTest.java`, `manifestIsConsistent` (quoted)

```java
assertThatCode(() -> CompositionStampAssertion.assertConsistent(MANIFEST))
        .doesNotThrowAnyException();
```

### 4. Drive the lifecycle with the conductor

`CompositionConductor.from(manifest).start()` asserts the stamp, then runs `initialize` and `ready`.
`shutdown()` runs `drain` and `terminate`. Ordering and the drain deadline are in
[composition runtime](../modules/07-composition-runtime.md).

Source: `exeris-sdk-examples/src/test/java/eu/exeris/sdk/examples/capability/CapabilityExampleTest.java`, `conductorDrivesTheFourPhases` (quoted)

```java
CompositionConductor conductor = CompositionConductor.from(MANIFEST).start();

assertThat(LedgerCapLifecycle.phases()).containsExactly("initialize", "ready");

conductor.shutdown();

assertThat(LedgerCapLifecycle.phases())
        .containsExactly("initialize", "ready", "drain", "terminate");
```

## Verify

```bash
mvn -pl exeris-sdk-examples -am verify -Dtest=CapabilityExampleTest -Dsurefire.failIfNoSpecifiedTests=false
```

Four tests pass. `tamperedManifestIsRefused` changes one provided version, and both
`assertConsistent` and the conductor refuse the manifest with `CompositionStampException` before
any hook runs.

Source: `exeris-sdk-examples/src/test/java/eu/exeris/sdk/examples/capability/CapabilityExampleTest.java`, `tamperedManifestIsRefused`

## Trade-off

The stamp hashes the capability set and not `lifecycleOwner` or `initOrder`, which
`CompositionBindingTest` pins in `lifecycleOwnerIsGoldenInvariant`. Tooling can re-derive either
without invalidating the stamp, and the cost is that the stamp cannot detect a change to them.

Source: `exeris-sdk-composition-spec/src/test/java/eu/exeris/sdk/composition/CompositionBindingTest.java`, `lifecycleOwnerIsGoldenInvariant`

## Where this does not apply

Resolving `@Requires` against `@Provides` is the build-time tooling's job; the SDK runtime asserts
the stamp the tooling wrote and never re-resolves. The stamp is not a security or licensing gate.
Writing `cap-manifest.json` by hand, as the example does, is for tests only.
