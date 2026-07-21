# Exeris SDK

Annotations, source model, and UI kit for Entity-First development on the Exeris platform.

This repository contains the **developer-facing API surface** of Exeris. It has zero runtime
coupling to the Exeris kernel — annotations are descriptive metadata consumed at build time
by the tooling (`exeris-tooling`) and the platform (`exeris-platform`).

## Modules

| Module | Purpose |
|---|---|
| [`exeris-sdk-annotations`](exeris-sdk-annotations) | Pure annotation library: `@ExerisDomain`, `@Action`, `@Field`, `@Relationship`, `@Saga`, `@DomainEvent`, security & system field annotations. |
| [`exeris-sdk-source-model`](exeris-sdk-source-model) | Canonical AST/metadata types describing an Exeris domain. Single source of truth shared by the annotation processor, codegen, and Studio. |
| [`exeris-sdk-source-model-io`](exeris-sdk-source-model-io) | JavaParser-based parser and writer: round-trips `.java` ↔ `DomainMetadata` for codegen, LSP, and Studio mutations. |
| [`exeris-sdk-composition-spec`](exeris-sdk-composition-spec) | Canonical `cap-manifest.json` schema and content-binding algorithm, shared by the tooling emitter and the SKU-boot asserter (ADR-024). |
| [`exeris-sdk-composition-runtime`](exeris-sdk-composition-runtime) | Boot-time composition validation-stamp assertion, shipped into each SKU artifact and invoked by the SKU bootstrap (ADR-024). |
| [`exeris-sdk-ui-kit`](exeris-sdk-ui-kit) | Tailwind preset and base styles for generated Angular components (TypeScript / npm). |
| `exeris-sdk-bom` | Bill of materials for module versions and third-party deps. |
| `exeris-sdk-parent` | Common Maven build configuration. |

## Architecture

```
@ExerisDomain → exeris-sdk-annotations  (compile-time markers)
              ↓
              exeris-sdk-source-model   (DomainMetadata, FieldMetadata, ActionMetadata, …)
              ↓
              consumed by:
                - exeris-tooling/exeris-processor    (build-time extraction)
                - exeris-tooling/exeris-codegen-*    (Java + TS code generation)
                - exeris-platform/exeris-platform-lsp (Studio + IDE bidirectional sync)
```

The model lives here, not in tooling, because every consumer must agree on the same
shape of a domain. Tooling and platform depend on `exeris-sdk-source-model`; the SDK
never depends on them.

## Requirements

- JDK 26
- Maven 3.9+
- Node 18+ (only for `exeris-sdk-ui-kit`)

### Why JDK 26?

Most consumer teams run JDK 21 LTS, so this baseline is the most common surprise on
first build. The SDK is pinned to JDK 26 because the **Exeris platform kernel** is,
and the annotation processor (`exeris-tooling/exeris-processor`) that consumes
`@ExerisDomain` runs in the same `javac` invocation as your project — it cannot lag
behind the kernel it generates code for.

The kernel's hard requirements:

- **Virtual Threads** (stable since 21, refined through JDK 24's `synchronized`
  pinning fix in JEP 491) — request handling, saga execution, and IO are
  virtual-thread-first. Generated controllers and saga runners assume virtual-thread
  semantics, and the pinning fix is load-bearing under contention.
- **Foreign Function & Memory API** (stable since 22, JEP 454) — used for native
  interop without JNI in kernel capability code paths.

The AST (`exeris-sdk-source-model`) and the Java emitted by `exeris-tooling` also
use records and pattern matching, but those landed in 16 and 21 — they're not what
fixes the floor at 26.

This is not an oversight, and we will not be backporting to 21 LTS. If you need to
call into Exeris from a JDK 21 service, keep the Exeris-annotated module on JDK 26
(built and packaged separately) and consume it over the platform's wire protocol.

## Build

```bash
mvn clean install
```

## License

Apache-2.0. See [LICENSE](LICENSE).
