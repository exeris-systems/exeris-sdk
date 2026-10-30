---
title: Composition lifecycle
type: module
visibility: public
owning-repo: exeris-sdk
status: active
last-verified: 2026-10-29
---

# Composition lifecycle

**Module:** `eu.exeris:exeris-sdk-composition-lifecycle` (0.13.1)
**Dependencies:** none at compile or runtime scope. JUnit and AssertJ are test scope only.

`exeris-sdk-composition-lifecycle` holds one interface, `eu.exeris.sdk.composition.lifecycle.CapabilityLifecycleHooks`. A capability marks the class that implements it with `@CapabilityLifecycle`. The module exists so a capability author compiles against the annotations jar and this jar only. It never drives the hooks; that is the job of [the composition runtime](07-composition-runtime.md).

## Contract

`CapabilityLifecycleHooks` declares four default methods, all no-ops. Implement the subset you need.

| Phase | Method | Guarantee |
|---|---|---|
| 1 | `initialize()` | Runs after `KERNEL READY` and before the capability serves a request. Must be idempotent. A throw aborts boot. |
| 2 | `ready()` | Runs only after every capability has completed `initialize()`. Calling a service another capability provides is safe here, not earlier. |
| 3 | `drain(Duration remaining)` | Stops intake and finishes in-flight work within the remaining slice of the composition-wide drain budget. |
| 4 | `terminate()` | Releases resources unconditionally for every capability that entered `initialize()`. Declares no checked exception. |

Three obligations fall on the implementation: a public no-arg constructor, because the conductor instantiates the class reflectively from the manifest's `lifecycleOwner`; a `drain` that watches `remaining` and returns in time, because an overrunning drain is interrupted and abandoned; and a defensive, re-entry-safe `terminate()`, because the abandoned drain may still run on another thread. The full wording is in the `CapabilityLifecycleHooks` javadoc.

Order is not decided here. The conductor runs `initialize()` and `ready()` forward through the manifest `initOrder`, and `drain` and `terminate` in reverse.

## Hot path

None in this module. The interface has no state and no logic. The hooks run once per boot and once per shutdown, on the thread that calls `start()` and `shutdown()`. Whether a hook allocates or blocks is the capability author's choice. `initialize()` and `ready()` are not time-bounded by the conductor.

## Failure modes

The interface raises nothing itself. What the caller sees depends on which phase throws.

- `initialize()` or `ready()` throws: the conductor unwinds and startup fails with `CompositionBootException`, with your exception as its cause.
- `drain` throws: the conductor logs it and continues the unwind.
- `terminate()` throws a runtime exception despite the contract: the conductor logs it and still terminates the remaining capabilities.

The exact unwind sequence is in [composition runtime](07-composition-runtime.md).

## Trade-off

Splitting the interface out of the runtime keeps Jackson off every capability author's classpath. The price is a third composition artifact to version and publish, and a reflective instantiation contract the compiler cannot check: a missing no-arg constructor shows up at boot, not at build.

## Owning ADRs

- ADR-024, capability composition model. Its lifecycle table defines the four phases, and obligation 8a places the conductor that drives them in the SDK. Stub: [ADR-024](../adr/ADR-024.link.md).
- ADR-038, the capability annotation surface, including `@CapabilityLifecycle`: [ADR-038](../adr/ADR-038-capability-annotation-surface.md).

The zero-dependency rule is stated in [zero-runtime-coupling](../../.agents/policies/zero-runtime-coupling.md) and enforced by the module's build.

## Where this does not apply

A build that declares no `@CapabilityModule` has no composition, so the generated application bootstrap contains no conductor call and no hook runs. This module does not discover hooks, read the manifest or enforce deadlines. It is unrelated to the kernel's own subsystem lifecycle, and the conductor is never registered as a kernel subsystem.
