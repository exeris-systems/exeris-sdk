---
title: Examples
type: module
visibility: public
owning-repo: exeris-sdk
status: active
last-verified: 2026-10-29
---

# Examples

**Module:** `eu.exeris:exeris-sdk-examples` (0.13.0, not published)

`exeris-sdk-examples` holds small annotated domains and the tests that read them back. Its purpose
is to keep examples of the SDK surface compiling and true: each example is a `.java` file javac
compiles against the annotations, and a test parses that same file and asserts what it declares.

It never ships, never runs in an application, and is not API reference. The contract of each
surface it shows is in that surface's module page and javadoc.

## Coordinates and dependencies

| Item | Value |
|---|---|
| Group and artifact | `eu.exeris:exeris-sdk-examples` |
| Published | No. `maven.deploy.skip` and `japicmp.skip` are set in its `pom.xml`, the root pom lists it in the central-publishing `<excludeArtifacts>`, and it is not in `exeris-sdk-bom` |
| Compile dependencies | `exeris-sdk-annotations`, `exeris-sdk-source-model`, `exeris-sdk-composition-lifecycle` (a main-source class implements `CapabilityLifecycleHooks`) |
| Test dependencies | `exeris-sdk-source-model-io`, `exeris-sdk-composition-spec`, `exeris-sdk-composition-runtime`, `exeris-sdk-tck`, Jackson 3 `jackson-databind`, JUnit Jupiter, AssertJ |

## Contract

| Scenario package (`eu.exeris.sdk.examples.…`) | Main sources | Test |
|---|---|---|
| `invoicing` | `Customer`, `Invoice`, `InvoiceLine` | `InvoicingExampleTest` |
| `ordering` | `Order` | `OrderingExampleTest` |
| `graph` | `Product`, `Category` | `GraphExampleTest` |
| `presentation` | `Employee`, `EmployeeDirectory` | `PresentationExampleTest` |
| `studioedit` | reuses `invoicing.Invoice` | `StudioEditExampleTest` |
| `capability` | `LedgerCapLifecycle` and, in the same file, `LedgerCap` | `CapabilityExampleTest` |
| `tckbinding` | none | `TckBindingExampleTest` |

What the module guarantees on a green build:

- Every example compiles against the current annotations, so every annotation target and
  attribute it uses exists.
- No example uses an element deprecated for removal or the reserved `@Channel` and `@Schedule`.
  A PARTIAL or RESERVED surface an example does use is named in its class javadoc.
- Each test reads its examples from `src/main/java`, resolved against the module base directory,
  through `SourceModelReader`. What it asserts is what the reader carries for that exact file.

## Hot path

None. The examples run only inside this module's tests, at build time. The `capability` test boots
its lifecycle owner through `CompositionConductor` to show the phase order.

## Failure modes

- A removed or renamed annotation, attribute or enum constant fails `javac` on the example that
  uses it.
- A change in what `SourceModelReader` carries fails the scenario test that asserts it.
- A change in the `cap-manifest.json` binding fails `CapabilityExampleTest`, whose manifest carries
  a precomputed `contentBinding`.
- Coverage is not a signal here: the main sources are read as text, not executed, so the module's
  `pom.xml` excludes them from SonarQube Cloud coverage.

## Owning ADRs

- [ADR-003](../adr/ADR-003-entity-first-development-strategy.md) — the annotated class is the single
  source of truth, which is what the examples show.
- [ADR-037](../adr/ADR-037-source-model-io-module.md) — the reader and writer the tests drive.
- [ADR-042](../adr/ADR-042-bidirectional-mutation-surface.md) — the mutation outcomes the
  `studioedit` scenario asserts.
- [ADR-024](../adr/ADR-024.link.md) — the capability composition the `capability` scenario boots.

## Trade-off

An example asserts what the reader carries, not what the build-time processor emits. Where the two
differ, the example is true of the reader only; the presentation scenario asserts a hand-built
view tree for that reason, because the reader does not read `@View`, `@Region`, `@Block` or `@Bind`.

## Where this does not apply

The module is not a test of the processor or of any generator in `exeris-tooling`, and its
manifest is written by hand for a test, not emitted by a build.
