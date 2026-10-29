---
title: Module pages
type: reference
visibility: public
owning-repo: exeris-sdk
status: active
last-verified: 2026-10-29
---

# Module pages

One page per SDK module. Each page states the module's contract, what runs on its hot path, how it fails, and which ADRs own it. Rules live in their own homes: the policies in [`.agents/policies/`](../../.agents/policies), the decisions in [`docs/adr/`](../adr), and the 1.0 surface in [`MIGRATION-0.x-to-1.0.md`](../../MIGRATION-0.x-to-1.0.md).

| Page | Module | In one line |
|---|---|---|
| [01-annotations.md](01-annotations.md) | `exeris-sdk-annotations` | The source-retained annotation surface a developer writes. |
| [02-source-model.md](02-source-model.md) | `exeris-sdk-source-model` | The canonical AST records and mutation types, and their JSON wire format. |
| [03-source-model-io.md](03-source-model-io.md) | `exeris-sdk-source-model-io` | The parser and writer between Java source and the AST. |
| [04-annotation-catalog.md](04-annotation-catalog.md) | `exeris-sdk-annotation-catalog` | The machine-readable catalog of the annotation surface. |
| [05-composition-spec.md](05-composition-spec.md) | `exeris-sdk-composition-spec` | The `cap-manifest.json` read schema and the one content-binding algorithm. |
| [06-composition-lifecycle.md](06-composition-lifecycle.md) | `exeris-sdk-composition-lifecycle` | `CapabilityLifecycleHooks`, the four-phase capability contract. |
| [07-composition-runtime.md](07-composition-runtime.md) | `exeris-sdk-composition-runtime` | Boot-time stamp assertion and the lifecycle conductor. |
| [08-tck.md](08-tck.md) | `exeris-sdk-tck` | Abstract test suites that hold a producer, reader or mapper to the metadata contract. |
| [09-ui-kit.md](09-ui-kit.md) | `@exeris/ui-kit` | Tailwind v4 theme, design tokens and component classes, on its own npm version line. |

`exeris-sdk-bom` and `exeris-sdk-parent` carry build configuration only. They hold dependency management and plugin settings and no code, so they have no module page.

Of the nine modules, only the composition runtime drives anything inside a deployed application. The composition spec and lifecycle jars load alongside it. No other module is part of a deployed application's boot sequence.

## Where this does not apply

This index does not cover the ecosystem outside `exeris-sdk`. The kernel, the tooling and the platform document their own modules in their own repositories.
