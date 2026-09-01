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
| [`exeris-sdk-annotation-catalog`](exeris-sdk-annotation-catalog) | Build-time processors that emit the SDK's own contract in machine-readable form: `annotation-catalog.json` into the annotations jar, and `ast-schema.json` (JSON Schema for the AST records) into the source-model jar. Both are also attached to each GitHub Release. Not published. |
| [`exeris-sdk-composition-spec`](exeris-sdk-composition-spec) | Canonical `cap-manifest.json` schema and content-binding algorithm, shared by the tooling emitter and the SKU-boot asserter (ADR-024). |
| [`exeris-sdk-composition-lifecycle`](exeris-sdk-composition-lifecycle) | Cap-facing `CapabilityLifecycleHooks` interface — the four-phase `initialize`/`ready`/`drain`/`terminate` contract, zero dependencies by design (ADR-024). |
| [`exeris-sdk-composition-runtime`](exeris-sdk-composition-runtime) | Boot-time composition validation-stamp assertion + the boot conductor driving the cap lifecycle, shipped into each SKU artifact and invoked by the SKU bootstrap (ADR-024). |
| [`exeris-sdk-tck`](exeris-sdk-tck) | Technology Compatibility Kit for the build-time metadata hand-off: abstract JUnit bases a producer, a reader and a consumer of the AST wire format extend to prove they satisfy the contract, making the ADR-042 parity discipline an executable gate. Test scope. |
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

- JDK 25 LTS
- Maven 3.9+
- Node 18+ (only for `exeris-sdk-ui-kit`)

### Why JDK 25 LTS?

The baseline follows the **Exeris platform kernel**, which moved its distributable
line to JDK 25 LTS with no preview flags in v0.11.0. The SDK's jars sit on your
*compile* classpath, so they cannot target a higher class-file major than the
runtime they describe: `javac` on JDK 25 refuses a major-70 class outright, which
would put the SDK above a floor the kernel had just lowered.

The published artifacts carry class-file **major 69** and require no
`--enable-preview` on your side. A guard test reads the emitted header rather than
trusting the build flag, because the stamp is what you would trip over.

**On JDK 21 LTS.** 21 is still below the baseline, and the kernel's own hard
requirements are why: Virtual Threads with JDK 24's `synchronized`-pinning fix
(JEP 491), which is load-bearing under contention, and the Foreign Function &
Memory API (JEP 454) for native interop in kernel capability paths. Both are GA
below 25, so they are not what sets the floor — the kernel's GA line is. If you
need to call into Exeris from a JDK 21 service, keep the Exeris-annotated module
on the baseline (built and packaged separately) and consume it over the platform's
wire protocol.

The SDK's own sources use only records and sealed types (16- and 21-era), so
nothing here pushes the floor upward on its own. See
[ADR-069](docs/adr/ADR-069-jdk-baseline-lts.md) for the decision and the
measurement behind it.

**Note on the annotation processor.** `exeris-tooling/exeris-processor` runs in the
same `javac` invocation as your project and has its own baseline; the SDK moving is
a prerequisite for it following, not a substitute. Until it does, an LTS build can
compile against the annotations but not yet run the processor.

## Build

```bash
mvn clean install
```

## License

Apache-2.0. See [LICENSE](LICENSE).
