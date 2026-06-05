# RFC-2026-06-03: How does exeris-sdk realize the ADR-024 capability annotation surface under zero runtime coupling?

| Field             | Value                                                                 |
|:------------------|:----------------------------------------------------------------------|
| **Status**        | **ACCEPTED**                                                         |
| **Author(s)**     | arkstack-dev                                                          |
| **Date Opened**   | 2026-06-03                                                            |
| **Date Closed**   | 2026-06-05                                                            |
| **Target ADR(s)** | [ADR-038](../adr/ADR-038-capability-annotation-surface.md) (SDK realization; implements [ADR-024](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-024-capability-composition-model.md)) |
| **Affected Repos**| `exeris-sdk`, `exeris-tooling` (processor/codegen consume the annotations + AST), `exeris-platform` (lsp `exeris/listCapabilities`) |
| **Reviewers**     | —                                                                    |

## Question

The 0.4.0 milestone adds the **`@Capability` surface** to the SDK. But the capability *composition* model is already decided ecosystem-wide: [ADR-024](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-024-capability-composition-model.md) (ACCEPTED 2026-05-13) fixes the annotation vocabulary as **`@CapabilityModule` + `@Provides` + `@Requires` + `@CapabilityLifecycle`**, consumed by the `exeris-tooling` annotation processor (ADR-015) and validated at build time. None of these annotations exist anywhere yet. **How does `exeris-sdk` define this surface — annotation names, where the annotations live, how a "service" is referenced, and what stays out — while preserving the SDK's zero-runtime-coupling invariant?** And how does the stale 0.4.0 ROADMAP wording (`@Capability` / `@CapabilityRef`) reconcile with ADR-024?

## Context

`exeris-sdk` is the home for developer-facing annotations: `@Retention(SOURCE)`, zero compile/runtime dependencies. Capability authors write Java in `exeris-caps-*` repos; the annotations they apply must therefore live in the SDK (or somewhere upstream of every cap), just as `@ExerisDomain` does for entities. ADR-024 already says the contract surface is declared "through annotations consumed by the `exeris-tooling` codegen pipeline" and that `@Provides` "lives on a `@CapabilityModule` class in the cap's API package" — but it does not say *which module defines those annotation types*. They do not exist in `exeris-sdk`, `exeris-tooling`, or the kernel today (verified by classpath grep — the only `@Requires*` hits are unrelated security annotations).

Two things make this a decision rather than a transcription:

1. **Stale ROADMAP wording.** The SDK ROADMAP's 0.4.0 bullets predate ADR-024: `@Capability` annotation, `CapabilityMetadata` / `CapabilityRefMetadata`, `@CapabilityRef("idp")`, manifest under `META-INF/exeris/capabilities/`. ADR-024's accepted vocabulary (`@CapabilityModule`/`@Provides`/`@Requires`) is authoritative and is a different shape (a cap declares the *services* it provides and requires, not a flat `@Capability` + named refs). The ROADMAP must be reconciled, not implemented literally.

2. **Zero-coupling tension.** ADR-024's `@CapabilityLifecycle` marks a class implementing lifecycle hooks "bound to the kernel bootstrap state machine," and `@Requires` can target "kernel SPIs … as well-known service identifiers (`KERNEL_TRANSPORT`, …)." A naive realization would pull a kernel lifecycle interface or kernel SPI types into the SDK — exactly the coupling the SDK forbids. The SDK can define the *markers*; it must not define the *behaviours* they bind to.

This unblocks the original driver: budgetHQ's `PLAN.md` parks an `ExerisObservabilityReporter` swap whose precondition is "the Exeris observability cap ships an SDK contract," and `exeris-platform-lsp` reserves an `exeris/listCapabilities` method — both need this annotation surface to exist.

**Sequencing precondition now met.** This RFC was parked behind the 0.3.0 `-io` reader/writer round-trip work, so that a new annotation surface would not be added on top of a reader that silently dropped existing facets. That work is complete (Slices A–D): `SourceModelReader` is now faithful to the processor across every facet and `unmodeledFacets()` is empty, so the capability annotations can be added with the same read-and-guard discipline the entity surface already has.

## Investigation

### Prior art

- **ADR-024 (Capability Composition Model)** — the authoritative surface: `@CapabilityModule` class carrying `@Provides(service, version)` and `@Requires(service, versionRange, optional)`; one `@CapabilityLifecycle` class; build-time `@Requires`→`@Provides` resolution + DAG + version + Wall checks; lifecycle order derived from the `@Requires` DAG. Emits a `cap-manifest.json` per cap.
- **ADR-023 (Capability Licensing Taxonomy)** — `community`/`commercial`/`enterprise-private` is a **per-cap-repository** property (declared in the repo's `LICENSE` + coordinate), *not* a field on any annotation. So licensing is **out of scope** for the SDK annotation surface.
- **ADR-006 / ADR-024 cap-tier Wall** — caps must not import `org.springframework.*`, `io.netty.*`, etc.; enforced by the tooling pipeline, not by an SDK annotation.
- **`@ExerisDomain` precedent (this repo)** — SOURCE-retained, zero-dep markers in `exeris-sdk-annotations`; canonical AST in `exeris-sdk-source-model`; processor reads them. The capability surface should mirror this layering exactly.

### Constraints

- **Zero runtime coupling.** The new annotations must be `@Retention(SOURCE)` with no compile deps; AST records pull only `jackson-annotations`. The lifecycle *interface* and kernel SPI *types* must NOT be referenced by the SDK.
- **Wire-format contract.** New AST records are a downstream contract (ADR-037 / `AstJsonRoundTripTest`); they must round-trip and be records.
- **ADR-024 is authoritative on names/semantics.** Deviating from `@CapabilityModule`/`@Provides`/`@Requires` would require superseding an accepted ADR — out of scope; this RFC implements it.

### Data gathered

- No capability annotations or `CapabilityMetadata` AST records exist in any repo today — greenfield.
- ADR-024 leaves the **annotation-defining module** unspecified and the **`Service` reference representation** ("Service service" vs "well-known identifiers") ambiguous — the two real open points this RFC closes.

## Options Considered

The naming (`@CapabilityModule`/`@Provides`/`@Requires`/`@CapabilityLifecycle`) and placement (annotations in `exeris-sdk-annotations` under a `capability` subpackage; AST records in `exeris-sdk-source-model`) are determined by ADR-024 + the `@ExerisDomain` precedent. The genuinely open fork is **how a service is referenced** in `@Provides`/`@Requires`:

### Option A: String service identifiers

`@Provides(service = "RouteRegistry", version = "1.0.0")`, `@Requires(service = "KERNEL_TRANSPORT")`.

**Pros:** trivially zero-coupling (just strings); kernel SPIs and cap services look identical; nothing to import.
**Cons:** stringly-typed — typos are build-time-at-best (resolution failure) rather than compile errors; no IDE refactor/navigate; the "well-known identifier" set (`KERNEL_TRANSPORT`, …) becomes an unenforced convention.
**Cost:** lowest; highest long-term fragility.

### Option B: `Class<?>` service-type references

`@Provides(service = RouteRegistry.class, version = "1.0.0")`, `@Requires(service = KernelTransport.class)`.

**Pros:** type-safe, refactor- and navigate-friendly; unifies "cap service" and "kernel SPI" as one mechanism (matching ADR-024's stated goal) because both are just interface types; **the SDK stays pure** — the annotation attribute is `Class<?>`, so the SDK depends on nothing. The *cap* references the service interfaces it legitimately provides/requires (kernel-spi types or sibling-cap API types it already depends on). The AST stores the service as its **fully-qualified name string** (a `Class` literal is read from source as an FQN; never loaded), so the wire format carries no class objects.
**Cons:** a cap must have the service interface type on its compile classpath to name it — but ADR-024 already requires that dependency (you can't `@Requires` a service you can't see). One extra published-coordinate awareness for the cap, not the SDK.
**Cost:** moderate; durable.

### Option C: `Service` marker interface (`Class<? extends Service>`)

Define a `Service` marker interface in the SDK; services are `Class<? extends Service>`.

**Pros:** stronger typing than raw `Class<?>`.
**Cons:** introduces the SDK's **first runtime type** (an interface, not a SOURCE annotation) — every kernel SPI and cap service would have to `implements Service`, retrofitting the entire kernel SPI surface to extend an SDK marker. That inverts the dependency arrow (kernel SPI depending on an SDK type) and expands the SDK beyond pure annotations. Rejected on coupling grounds.

### Option D (do nothing)

Leave 0.4.0 unstarted; the observability cap has no SDK contract, BHQ's reporter swap and the LSP's `listCapabilities` stay blocked, and ADR-024 remains undeliverable.

## Recommendation

**Define ADR-024's surface in `exeris-sdk` — `@CapabilityModule` / `@Provides` / `@Requires` / `@CapabilityLifecycle` as `@Retention(SOURCE)` annotations in a new `eu.exeris.sdk.annotation.capability` package, with services referenced by `Class<?>` (Option B); add `CapabilityModuleMetadata` / `ProvidesMetadata` / `RequiresMetadata` AST records (storing services as FQN strings) in `exeris-sdk-source-model`. Reconcile the ROADMAP to ADR-024 vocabulary.**

Option B gives compile-time-checked, refactor-safe service references and the single "depends on a cap or a kernel SPI" mechanism ADR-024 wants, **without** touching the SDK's zero-coupling: the annotation attribute is a bare `Class<?>`, the SDK imports nothing, and the AST persists service identity as an FQN string (no class loading, consistent with SOURCE retention and the JSON wire format). It mirrors the `@ExerisDomain` → `DomainMetadata` layering the repo already proves.

Three scope lines that keep the SDK pure:

- **`@CapabilityLifecycle` is a marker only.** The four-phase lifecycle *interface* (`initialize`/`ready`/`drain`/`terminate`, bound to kernel bootstrap) stays kernel-side. The SDK annotation merely marks the class; the processor records "this class is the lifecycle owner," and the kernel/tooling bind the behaviour. The SDK never references kernel bootstrap.
- **Licensing is not an annotation.** ADR-023's `community`/`commercial`/`enterprise-private` is a per-cap-repo property; no SDK annotation field encodes it.
- **The manifest is tooling's.** `cap-manifest.json` / discovery format (ADR-024 + ADR-015) is emitted by `exeris-tooling`; the SDK supplies only the AST records it serializes. The ROADMAP's `META-INF/exeris/capabilities/` path is a tooling decision, not an SDK one.

### Annotation & AST shapes (decided)

So the implementation ADR does not re-negotiate these mid-flight:

- **`@CapabilityModule`** — `@Target(TYPE)`, one per cap API class (the `@Provides`/`@Requires` carrier). Mirrors `@ExerisDomain`'s `TYPE` target.
- **`@CapabilityLifecycle`** — `@Target(TYPE)`, marker only, **zero-or-one per cap**. Absence is valid (a cap with no bootstrap-bound lifecycle owner); more than one is a build error the tooling validator rejects. May annotate a class distinct from the `@CapabilityModule` class.
- **`@Provides` / `@Requires`** — `@Target(TYPE)` and **directly `@Repeatable` via a nested `.List` container** (`@Provides.List` / `@Requires.List`), following the Jakarta Bean Validation idiom (`@Pattern.List`, `@Size.List`). The nested form is chosen over the repo's plural-name container styles (`@DomainEvent` → nested `DomainEvents`; `@SagaStep` → top-level `SagaSteps`) because both annotation names already end in `-s`, so a clean plural name is not available here. The AST flattens the container, so consumers never see it.
- **AST field types** — primitive/string, `@JsonInclude(NON_DEFAULT)` per the wire-format contract:
  - `ProvidesMetadata(String service, String version)` — `service` = FQN string; `version` nullable (omitted when absent).
  - `RequiresMetadata(String service, String versionRange, boolean optional)` — `service` = FQN string; `versionRange` nullable; `optional` a **primitive boolean** (default `false`, dropped by `NON_DEFAULT` when false — the same `FAIL_ON_NULL_FOR_PRIMITIVES=false` consumer contract the existing AST relies on).
  - `CapabilityModuleMetadata(List<ProvidesMetadata> provides, List<RequiresMetadata> requires, String lifecycleOwner)` — `lifecycleOwner` = FQN of the `@CapabilityLifecycle` class, nullable when the cap has none.

  `version` / `versionRange` are likely-to-stabilize string fields; the 0.x stability policy covers them, and the implementation ADR flags them as such.

### Why not the alternatives?

- **Option A (strings)** — sacrifices compile-time safety and turns the well-known-identifier set into unenforced convention; the SDK can afford type-safety here for free.
- **Option C (`Service` marker)** — would give the SDK a runtime type and force the kernel SPI surface to implement an SDK interface, inverting the dependency arrow. Violates zero-coupling.
- **Option D** — leaves ADR-024 undeliverable and the original Caps/BHQ driver blocked.

### Risks of the recommendation

- **ADR-024 says "Service service" — singular type, not "services".** Resolved under *Annotation & AST shapes*: `@Provides`/`@Requires` are directly `@Repeatable` so a cap declares several services from one module class; the AST flattens the container. No blocker.
- **`Class<?>` literal of a not-yet-published service.** A cap referencing a kernel SPI not on its classpath won't compile — which is the *correct* failure (you can't depend on what you can't see), and is exactly ADR-024's intent.
- **AST FQN vs. simple-name resolution.** Reading a `Class` literal from source (JavaParser path, per the `-io` reader) yields whatever the source wrote (simple or qualified). The processor (JSR-269) yields the FQN. The AST contract must standardize on FQN; the `-io` reader resolves via imports or records the written form — flag for the AST design.

## Decision Record

| Field                | Value     |
|:---------------------|:----------|
| **Outcome**          | **ACCEPTED** — Option B (`Class<?>` service references, AST stores FQN strings) |
| **Date**             | 2026-06-05 |
| **Resulting ADR(s)** | [ADR-038](../adr/ADR-038-capability-annotation-surface.md) — SDK realization of the capability annotation surface (implements ADR-024) |
| **Notes**            | Annotations in `eu.exeris.sdk.annotation.capability`; `@Provides`/`@Requires` repeatable via nested `.List` containers (Bean Validation idiom — chosen because both names end in `-s`, so the repo's plural-name container convention does not apply); AST records `ProvidesMetadata`/`RequiresMetadata`/`CapabilityModuleMetadata` in `source-model` with collection types `List<…>` and service identity as FQN strings. Lifecycle interface, ADR-023 licensing, and `cap-manifest.json` stay out of the SDK. Delivery: annotations → AST records → `-io` reader slices (per ADR-038 Engineering Protocol). |

## Open questions / follow-ups

- **New ADR vs. implements-ADR-024?** ✅ Resolved — [ADR-038](../adr/ADR-038-capability-annotation-surface.md), a thin SDK-side "implements ADR-024" record (the same shape as ADR-037 for `-io`), reserved number-first in `exeris-docs/adr-index.md`.
- **`Service` type ambiguity in ADR-024.** This RFC reads "Service service" as a `Class<?>` interface reference and "well-known identifiers" as kernel SPI interface classes. Confirm with the ADR-024 owner that no literal `Service` enum/type was intended.
- **AST service identity = FQN.** Standardize the AST on fully-qualified service names. The `-io` reader (now complete) matches annotations by **simple name without symbol solving** — so a written `Class` literal resolves to whatever the source wrote (simple or qualified). The processor (JSR-269) yields the FQN. To keep the two in lock-step, either: (a) the AST stores the **written form** and a normalization step is the tooling's job, or (b) the `-io` reader does a minimal import-table lookup to expand the simple name. Recommend (a) for the first cut (consistent with the reader's documented no-symbol-solving limitation), revisited if the FQN mismatch turns out to matter for capability conflict-detection.
- **Versioning scheme** (`version`, `versionRange`) — string attributes in the SDK; range *syntax* + intersection live in the tooling validator (ADR-024 predicate 3), not the SDK.
- **`@CapabilityLifecycle` ↔ kernel lifecycle interface** — coordinate where the interface lives (kernel SPI) so the marker and the behaviour stay in lock-step without SDK coupling.
