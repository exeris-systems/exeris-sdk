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

## Build

```bash
mvn clean install
```

## License

Apache-2.0. See [LICENSE](LICENSE).
