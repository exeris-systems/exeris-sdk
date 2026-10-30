---
title: Composition runtime
type: module
visibility: public
owning-repo: exeris-sdk
status: active
last-verified: 2026-10-29
---

# Composition runtime

**Module:** `eu.exeris:exeris-sdk-composition-runtime` (0.13.1)
**Dependencies:** [`exeris-sdk-composition-spec`](05-composition-spec.md), [`exeris-sdk-composition-lifecycle`](06-composition-lifecycle.md) and `tools.jackson.core:jackson-databind`. No kernel, tooling or JavaParser dependency.

`exeris-sdk-composition-runtime` is the one SDK module that runs inside a deployed SKU. It asserts the validation stamp in `cap-manifest.json`, discovers each capability's lifecycle hooks, and drives them through `initialize`, `ready`, `drain` and `terminate`. It does not re-resolve the `@Requires` to `@Provides` graph, and it is not a security or licensing gate: it catches honest configuration drift early.

## Contract

### Entry points

| Type | Role |
|---|---|
| `CompositionStampAssertion` | Static `assertConsistent` overloads for a `Path`, a JSON `String`, a `CapManifest`, and a `CapManifest` plus classpath versions. Also `serviceVersions(CapManifest)`. |
| `CompositionConductor` | Boots and shuts down the composition. Built with `from(Path)` or `from(CapManifest)`, configured on its `Builder`, started with `start()`. |
| `CompositionBootException` | Boot failure. Exposes `capName()` and `phase()`. |
| `CompositionStampException` | Stamp failure or unreadable manifest. |
| `DrainRunner` | Package-private seam that runs one drain under a timeout. Not public API. |

### Stamp assertion

`CompositionStampAssertion.assertConsistent` runs these checks in order and throws `CompositionStampException` on the first failure:

1. **Handshake.** `schemaVersion` must lie in `[1, 2]`. The upper bound is the package-private constant `KNOWN_SCHEMA_VERSION`. A missing value reads as `0` and is refused, as is a newer schema.
2. **Presence and form.** The stamp must exist, `validated` must be true, and `contentBinding` must match `^sha256:[0-9a-f]{64}$`. A `compositionVersion` of `"0.0.0"` is tolerated and never asserted.
3. **Module well-formedness.** The module list must be non-null. No module may be null or have a null `qualifiedName`. No provided entry may be null or have a null `service`. A null `version` is legal.
4. **Binding match.** The binding is recomputed with `CompositionBinding.compute` and compared with the stamp.
5. **Version match.** Only when a non-empty `Map<String, String>` of classpath service versions is passed. A service absent from the map is skipped. A mismatch in either direction between versioned and unversioned is drift.

The `Path` overload wraps read and parse failures in `CompositionStampException`, and the `String` overload wraps parse failures. The overloads without a version map skip step 5, because a single bundled manifest is self-consistent.

### Conductor

`CompositionConductor.Builder` has two settings and a start method:

- `drainDeadline(Duration)`: the composition-wide drain budget. It must be positive, otherwise `IllegalArgumentException`. The default is `CompositionConductor.DEFAULT_DRAIN_DEADLINE`, 30 seconds.
- `classLoader(ClassLoader)`: where lifecycle owners load from. Default is the thread context class loader at `start()`, falling back to the conductor's own loader.
- `start()`: boots and returns the running conductor.

`CompositionConductor` implements `AutoCloseable`. `shutdown()` and `close()` are the same operation.

Boot proceeds in a fixed order:

1. Resolve the manifest. A `Path` is read and parsed here, and failures are `CompositionStampException`.
2. Run `CompositionStampAssertion.assertConsistent`.
3. **Discover.** Walk `initOrder` verbatim. Each entry must have a module in the manifest. Each non-blank `lifecycleOwner` is loaded, must implement `CapabilityLifecycleHooks`, and is instantiated through its public no-arg constructor. A module without a `lifecycleOwner` is skipped. All discovery completes before any hook runs.
4. **`initialize` forward** in `initOrder`.
5. **`ready` forward**, the all-capabilities barrier.
6. Log one INFO line with the total capability count and the count with hooks.

A manifest with a `null` `initOrder` is accepted only when no module declares a `lifecycleOwner`. Otherwise discovery fails, because replaying hooks without the tooling's order would mean resolving the graph again.

A call site runs inside the kernel boot, after `KERNEL READY`:

```java
try (var conductor = CompositionConductor.from(Path.of("cap-manifest.json")).start()) {
    // serve until the entrypoint decides to stop
}
```

## Hot path

Boot and shutdown run once per process. Nothing here sits on a request path, but the threading is exact and worth knowing.

**Boot threading.** Every phase runs synchronously on the thread that calls `start()`. There is no parallel initialization, no executor and no timeout on `initialize()` or `ready()`. A hanging hook hangs boot.

**Ordering.** `initialize` and `ready` run in `initOrder`. Shutdown drains and terminates in reverse `initOrder`. The conductor never re-sorts.

**Failure unwind during boot.** The unwind marks the conductor as shut down first, so a later `shutdown()` is a no-op.

- If `initialize` fails at index *k*, the conductor drains capabilities *k-1* down to 0, then terminates *k* down to 0. Capability *k* is not drained because it never finished `initialize`, but it is terminated.
- If `ready` fails, it drains and terminates every capability in reverse.
- Either failure then throws `CompositionBootException` with the original exception as cause.
- Only `Exception` is caught. An `Error` from a hook propagates without an unwind.

**Shutdown.** `shutdown()` uses an atomic compare-and-set, so the second call returns immediately and concurrent callers run the unwind once. It first drains, then terminates. A hook `Exception` is logged and never propagates; an `Error` does.

- The deadline is computed once from `System.nanoTime()` when the drain loop starts. Each capability receives the strictly remaining time.
- When the budget is spent, the remaining drains are skipped with a WARNING.
- A drain that throws is logged and the loop continues.
- `terminate()` then runs for every capability. A `RuntimeException` is logged and does not stop the loop.

**Drain runner.** The production `DrainRunner` runs `hooks.drain(remaining)` on the calling thread, so any scoped values bound on that thread stay visible. It starts one daemon thread named `exeris-cap-drain-watchdog` per drain. That thread sleeps for the remaining time and then interrupts the calling thread, unless the drain finished first. A shared lock orders the two. After the drain, the runner marks it over, cancels the watchdog and clears the interrupt flag, so a late interrupt cannot leak into the next capability or into `terminate()`.

Interruption is cooperative. A drain is reported as an overrun only when it exits by throwing `InterruptedException`. A drain that ignores interruption runs past the budget and delays shutdown. A drain that swallows the interrupt and returns normally is reported as completed.

The runner does not use `StructuredTaskScope`, because it is a preview API on the JDK 25 baseline and the reactor carries no preview flags. See [ADR-069](../adr/ADR-069-jdk-baseline-lts.md).

## Failure modes

| Exception | When | State on throw |
|---|---|---|
| `CompositionStampException` | Unreadable or unparseable manifest, schema outside `[1, 2]`, missing or unvalidated stamp, malformed or mismatched binding, malformed modules, version drift | No hook has run |
| `CompositionBootException`, phase `discover` | Unknown `initOrder` entry, owner not loadable, not a `CapabilityLifecycleHooks`, no public no-arg constructor or a constructor that fails, or hooks declared without `initOrder` | No hook has run |
| `CompositionBootException`, phase `initialize` | A hook threw | Touched prefix drained and terminated |
| `CompositionBootException`, phase `ready` | A hook threw | All capabilities drained and terminated |

The two exception types are unrelated on purpose. A stamp failure means this is not the validated composition and nothing happened. A boot failure means the validated composition failed to start and the effects were unwound. `capName()` is `null` for a failure that belongs to no single capability.

The conductor logs through `System.Logger` named after `CompositionConductor`. It writes WARNING lines for budget exhaustion, overruns, drain failures and terminate failures. It does not emit JFR events.

## Trade-off

Running the drain on the calling thread preserves scoped values and avoids a preview API. The price is that enforcement depends on the hook honoring interruption; a hook that blocks uninterruptibly cannot be cut off. The other limit is that boot has no time bound at all.

## Owning ADRs

- ADR-024, capability composition model. Obligations 8, 8a, 8a prime and 8b place the stamp assertion, the conductor and its call site in the SDK. Stub: [ADR-024](../adr/ADR-024.link.md). Authoritative copy: [exeris-docs](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-024-capability-composition-model.md).
- ADR-053, JSON as the manifest format: [exeris-docs](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-053-sku-composition-manifest-format.md).
- ADR-069, the JDK 25 baseline that rules out the preview structured-concurrency API: [ADR-069](../adr/ADR-069-jdk-baseline-lts.md).

## Where this does not apply

A build with no capabilities has no conductor call. The conductor is not a kernel subsystem, and the open kernel never calls it. It does not resolve the capability graph, and it offers no signature-backed boot gate. A forked SKU runtime can bypass it. Design-time and deploy-time validation belong to `exeris-platform`, which only consumes this library.
