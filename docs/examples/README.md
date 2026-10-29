---
title: Examples
type: reference
visibility: public
owning-repo: exeris-sdk
status: active
last-verified: 2026-10-29
---

# Examples

The `exeris-sdk-examples` module holds annotated sources and tests that the guides in [`docs/guides`](../guides) quote. Each scenario is a package `eu.exeris.sdk.examples.<scenario>`, in `src/main/java`, `src/test/java` or both. Its test reads the scenario's `.java` files through `SourceModelReader` and asserts the metadata they declare, except `tckbinding`, which binds the reader to the kit's own corpus. A removed annotation attribute in a scenario that has sources fails this build before it fails a guide.

> **Verified against** `0.13.0-SNAPSHOT` at commit `0753b7d0`, 2026-10-29. If this table and the module disagree, **the module wins and this page is the bug**. The module is not published and is not in `exeris-sdk-bom`.

## Scenarios

Paths are relative to `exeris-sdk-examples/src`. Each scenario lists what the SDK reads from it and what it does not, because a declared annotation is not always a modeled one. Tiers are in the [stability matrix](../stability-matrix.md).

| Scenario | What it shows | Main sources | Tests | Guide |
|---|---|---|---|---|
| `invoicing` | `@ExerisDomain`, system markers, `@Field` flags, `@Validation` bounds, relationships in both directions, `@Action`, `@ActionParam`, `@QueryParam`, `@RouteAccess`. Several of these are PARTIAL or RESERVED, so the reader carries them and a generator may not use them yet. | `main/java/eu/exeris/sdk/examples/invoicing/Invoice.java`, `Customer.java`, `InvoiceLine.java` | `test/java/eu/exeris/sdk/examples/invoicing/InvoicingExampleTest.java` | [Model a domain](../guides/02-model-a-domain.md); `Invoice` is also the input of [read and write source](../guides/06-read-and-write-source.md). |
| `ordering` | `@DomainEvent` with three triggers, `@Saga` and three `@SagaStep`s with compensation. `@SagaTransition`, `@Rule` and `@Derived` are declared and not read. | `main/java/eu/exeris/sdk/examples/ordering/Order.java` | `test/java/eu/exeris/sdk/examples/ordering/OrderingExampleTest.java` | [Declare behavior](../guides/03-declare-behavior.md) |
| `graph` | `@Graph`, `@GraphProperty` and `@GraphEdge` as node label, properties and edge. `@GraphQuery` is RESERVED and not read. | `main/java/eu/exeris/sdk/examples/graph/Product.java`, `Category.java` | `test/java/eu/exeris/sdk/examples/graph/GraphExampleTest.java` | [Model a graph](../guides/04-model-a-graph.md) |
| `presentation` | Entity-level `@UI`, `@UIGroup`, `@Tab`, `@NavMenu`, and a page declared with `@View`, `@Region`, `@Block` and `@Bind`. The page side is not read. | `main/java/eu/exeris/sdk/examples/presentation/Employee.java`, `EmployeeDirectory.java` | `test/java/eu/exeris/sdk/examples/presentation/PresentationExampleTest.java` | [Declare presentation](../guides/05-declare-presentation.md) |
| `studioedit` | The edit loop over the `invoicing` `Invoice` source: writer, mutation applier, `Success`, `Conflict` and both `NoBaseline` causes. | Reuses `main/java/eu/exeris/sdk/examples/invoicing/Invoice.java` | `test/java/eu/exeris/sdk/examples/studioedit/StudioEditExampleTest.java` | [Read and write source](../guides/06-read-and-write-source.md) |
| `tckbinding` | A reader bound to `AbstractMetadataReaderTck`, with `unsupportedFacets()` empty. | None. The binding is the test. | `test/java/eu/exeris/sdk/examples/tckbinding/TckBindingExampleTest.java` | [Consume the AST](../guides/07-consume-the-ast.md) |
| `capability` | `@CapabilityModule` with `@Provides` and `@Requires`, its `@CapabilityLifecycle` owner implementing `CapabilityLifecycleHooks`, a stamped `cap-manifest.json`, and the boot conductor driving the four phases. | `main/java/eu/exeris/sdk/examples/capability/LedgerCapLifecycle.java` | `test/java/eu/exeris/sdk/examples/capability/CapabilityExampleTest.java`, `test/resources/capability/cap-manifest.json` | [Capabilities and composition](../guides/08-capabilities-and-composition.md) |

## Run the examples

From the repository root:

```bash
mvn -pl exeris-sdk-examples -am verify
```

The `-am` flag builds the modules the examples depend on. The tests resolve source files relative to the module base directory, which is surefire's working directory.

To run one scenario, pass its test class:

```bash
mvn -pl exeris-sdk-examples -am verify -Dtest=PresentationExampleTest -Dsurefire.failIfNoSpecifiedTests=false
```

The `ui-kit` guide has no scenario here. [Style generated UI](../guides/09-style-generated-ui.md) is verified by the package's own `npm test`.

## Citation format

Every snippet in a guide is preceded by one line in this form, with a repository-relative path and no line numbers:

```text
Source: `<path>`, `<Symbol>` (quoted)
```

The last word is `quoted` when the snippet is verbatim and `adapted` when anything was trimmed. A line may name several symbols, or only a path. `tools/docs-citations/check.sh` fails on a path that does not exist and on a symbol that no longer occurs in its file.

## Trade-off

The tests prove that the SDK reads each source the way the guides say. They do not prove what the `exeris-tooling` processor emits for the same source. Where the two can differ, the scenario names the gap.

## Where this does not apply

- **Published artifacts.** The module is not released and is not a template to copy as a project layout.
- **Runtime behavior.** The annotations are `@Retention(SOURCE)`. Nothing here runs an application.
- **Capability composition.** No scenario covers it.
