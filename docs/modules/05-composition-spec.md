---
title: Composition spec
type: module
visibility: public
owning-repo: exeris-sdk
status: active
last-verified: 2026-10-29
---

# Composition spec

**Module:** `eu.exeris:exeris-sdk-composition-spec` (0.13.0)
**Dependencies:** none at compile or runtime scope. JUnit, AssertJ and Jackson are test scope only.

`exeris-sdk-composition-spec` owns two things: the read schema of `cap-manifest.json` (`CapManifest`) and the one content-binding algorithm (`CompositionBinding`). The build-time tooling that emits the manifest and the SKU-boot runtime that asserts it both depend on this module. It never validates a composition, never resolves the `@Requires` to `@Provides` graph, and never reads or writes JSON itself.

## Contract

### `CapManifest`

`eu.exeris.sdk.composition.CapManifest` is a record with four components: `schemaVersion`, `stamp`, `modules` and `initOrder`. Its nested records are `Stamp`, `Module`, `ModuleBody` and `Provided`.

| Record | Components | Notes |
|---|---|---|
| `CapManifest` | `schemaVersion`, `stamp`, `modules`, `initOrder` | `initOrder` is the module order the boot conductor replays |
| `Stamp` | `validated`, `compositionVersion`, `contentBinding` | `contentBinding` is the output of `CompositionBinding` |
| `Module` | `qualifiedName`, `module` | The component is named `module` because that is the JSON key |
| `ModuleBody` | `provides`, `lifecycleOwner` | A blank `lifecycleOwner` normalizes to `null` in the compact constructor |
| `Provided` | `service`, `version` | A `null` version is a legal unversioned provide |

The records carry no Jackson annotations. A component name is the wire key, so renaming `Module.module` would deserialize every real module body to `null`. The consumer-relevant subset is modeled; producer-only diagnostics are not, and a consumer ignores them.

The consumer brings its own mapper and must disable `FAIL_ON_UNKNOWN_PROPERTIES` and `FAIL_ON_NULL_FOR_PRIMITIVES`. The reasoning is in the `CapManifest` javadoc and the [ast-wire-format policy](../../.agents/policies/ast-wire-format.md). `CompositionStampAssertion` in the [composition runtime](07-composition-runtime.md) builds its mapper with exactly those two settings.

`lifecycleOwner` and `initOrder` are tooling-derived. The content binding does not hash them.

### `CompositionBinding`

`eu.exeris.sdk.composition.CompositionBinding` exposes two static methods, `compute(CapManifest)` and `compute(List<CapManifest.Module>)`. Both return `"sha256:"` followed by 64 lowercase hex characters.

The canonical form is fixed and specified in the `CompositionBinding` javadoc. In short: modules sort by `qualifiedName`, each module contributes its name and a sorted `  provides service@version` line per service, and a `null` version renders as the empty string, so an unversioned provide is `service@`.

The binding covers the capability set only. The composition version is a separate stamp field and is matched independently. `compute(CapManifest)` treats a `null` module list as an empty composition, so a wrong-schema file reaches the asserter's descriptive failure rather than a `NullPointerException`.

Any change to sort order, separators, prefix, trailing newline or null normalization changes the hash and refuses every deployment. `CompositionBindingTest` pins a golden vector for exactly this reason. The ADR-024 stub in this repository records when the consumer schema or the canonical form may change: [ADR-024 stub](../adr/ADR-024.link.md).

## Hot path

None. Nothing in this module runs per request. `CompositionBinding.compute` runs once per boot in the runtime's stamp assertion (see [composition runtime](07-composition-runtime.md)), and once per build in the tooling emitter. It sorts the modules and each module's provides, hashes with `MessageDigest` SHA-256 on the calling thread, and holds no shared state.

## Failure modes

The module throws almost nothing of its own.

- `CompositionBinding` throws `IllegalStateException("SHA-256 unavailable")` only on a JRE without SHA-256.
- A module with a `null` `qualifiedName` makes the sort throw `NullPointerException` once there are two or more modules. A provided entry with a `null` `service` hashes as `null@...` and surfaces only as a binding mismatch. `CompositionStampAssertion` rejects both with a named `CompositionStampException` before it calls `compute`.

Schema and binding mismatches are reported by the runtime, not here.

## Trade-off

The records carry no annotations, which keeps the module dependency-free and lets two independent consumers share it. The cost is that every consumer owns its mapper configuration, and a mapper configured wrongly fails at the consumer, not at compile time.

## Owning ADRs

- ADR-024, capability composition model. Obligation 8b places the schema and binding in the SDK. Stub: [ADR-024](../adr/ADR-024.link.md). Authoritative copy: [exeris-docs](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-024-capability-composition-model.md).
- ADR-053, composition manifest format. The manifest is JSON. Authoritative copy: [exeris-docs](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-053-sku-composition-manifest-format.md).
- ADR-038, the capability annotation surface that the manifest describes: [ADR-038](../adr/ADR-038-capability-annotation-surface.md).

The zero-dependency rule is in [zero-runtime-coupling](../../.agents/policies/zero-runtime-coupling.md).

## Where this does not apply

This module does not validate a composition, resolve the dependency graph, parse JSON or assert a stamp. It does not describe the metadata files under `exeris-metadata/`, which are the AST wire format in [source model](02-source-model.md). It is not a kernel module, and the kernel never reads it.
