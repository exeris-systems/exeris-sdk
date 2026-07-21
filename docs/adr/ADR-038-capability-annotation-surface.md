# ADR-038: SDK realization of the capability annotation surface — `@CapabilityModule` / `@Provides` / `@Requires` / `@CapabilityLifecycle`

| Attribute       | Value                                                                                          |
|:----------------|:-----------------------------------------------------------------------------------------------|
| **Status**      | **ACCEPTED**                                                                                   |
| **Deciders**    | Arkadiusz Przychocki                                                                           |
| **Date**        | 2026-06-05                                                                                     |
| **Scope**       | cross-repo                                                                                     |
| **Owning Repo** | `exeris-sdk`                                                                                    |
| **Driven By**   | [RFC-2026-06-03](../rfc/RFC-2026-06-03-capability-annotation-surface.md) (ACCEPTED)            |
| **Implements**  | [ADR-024](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-024-capability-composition-model.md) Capability Composition Model |
| **Compliance**  | Zero runtime coupling invariant (the SDK's defining discipline); [ADR-003](ADR-003%20Entity-First%20Development%20Strategy.md) Entity-First; [ADR-023](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-023-capability-licensing-taxonomy.md) (licensing is a per-cap-repo property, not an annotation field) |

> ADR numbers are a **single ecosystem-wide namespace** registered in [`exeris-docs/adr-index.md`](https://github.com/exeris-systems/exeris-docs/blob/main/adr-index.md). The gap between ADR-003 / ADR-037 and ADR-038 in this repo is expected — 004–036 are owned by other Exeris repos.

## Context and Problem Statement

[ADR-024](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-024-capability-composition-model.md) (ACCEPTED 2026-05-13) fixes the ecosystem-wide capability **composition model**: a `@CapabilityModule` class carrying `@Provides(service, version)` and `@Requires(service, versionRange, optional)`, one `@CapabilityLifecycle` class, build-time `@Requires`→`@Provides` resolution + DAG + version + Wall checks, and a `cap-manifest.json` emitted per cap. It is authoritative on **names and semantics** — but it does not say **which module defines these annotation types**, nor how a "service" is concretely **referenced** (`"Service service"` versus well-known string identifiers). None of the four annotations or any `Capability*Metadata` AST record exists in any repo today (verified by classpath grep — the only `@Requires*` hits are unrelated security annotations).

`exeris-sdk` is the upstream home for developer-facing annotations: `@Retention(SOURCE)`, zero compile/runtime dependencies, mirrored as Jackson-serializable AST records in `exeris-sdk-source-model`. Capability authors write Java in `exeris-caps-*` repos, so the annotations they apply must live in the SDK (or upstream of every cap), exactly as `@ExerisDomain` does for entities. Two forces make this a decision rather than a transcription:

1. **Zero-coupling tension.** ADR-024's `@CapabilityLifecycle` marks a class implementing hooks "bound to the kernel bootstrap state machine," and `@Requires` can target "kernel SPIs … as well-known service identifiers." A naive realization would pull a kernel lifecycle interface or kernel SPI types into the SDK — precisely the coupling the SDK forbids. The SDK can define the *markers*; it must not define the *behaviours* they bind to.
2. **Service-reference representation is open.** Strings are trivially decoupled but stringly-typed; `Class<?>` is type-safe but raises the question of how the AST persists it without loading classes.

This unblocks the original driver: budgetHQ's `PLAN.md` parks an `ExerisObservabilityReporter` swap whose precondition is "the Exeris observability cap ships an SDK contract," and `exeris-platform-lsp` reserves an `exeris/listCapabilities` method — both need this annotation surface to exist.

**Sequencing precondition met.** This work was parked behind the 0.3.0 `-io` reader/writer round-trip (ADR-037) so a new annotation surface would not be added on top of a reader that silently dropped existing facets. That work is complete: `SourceModelReader` is faithful to the processor across every facet and `unmodeledFacets()` is empty, so the capability annotations can be added with the same read-and-guard discipline the entity surface already has.

## 🏁 The Decision

**Define ADR-024's surface in `exeris-sdk` as `@Retention(SOURCE)` annotations in a new `eu.exeris.sdk.annotation.capability` package, with services referenced by `Class<?>`; add `CapabilityModuleMetadata` / `ProvidesMetadata` / `RequiresMetadata` AST records in `exeris-sdk-source-model` that persist a service as a **string** — the source-written name, normalized to a canonical fully-qualified name by tooling (see obligation #6). The lifecycle interface, the licensing taxonomy, and the discovery manifest stay out of the SDK.**

This mirrors the `@ExerisDomain` → `DomainMetadata` layering the repo already proves: SOURCE-retained markers in `-annotations`, canonical AST in `-source-model`, read by the processor (JSR-269) and the `-io` reader (JavaParser).

**Concrete obligations:**

1. **Annotations live in `exeris-sdk-annotations` under `eu.exeris.sdk.annotation.capability`.** `@CapabilityModule`, `@Provides`, `@Requires`, `@CapabilityLifecycle`, all `@Retention(SOURCE)` with **no compile dependencies**. `AnnotationContractTest` discovers them by reflection and asserts SOURCE retention + `@Target` automatically — no test edit required. (Reviewable: the new subpackage carries no `import` outside `java.lang.annotation`.)
2. **Service reference is `Class<?>`.** `@Provides(service = RouteRegistry.class, version = "1.0.0")`, `@Requires(service = KernelTransport.class, optional = false)`. The annotation attribute is a bare `Class<?>`, so the **SDK imports nothing**; the *cap* references the service interfaces it legitimately provides/requires (kernel-spi types or sibling-cap API types it already depends on per ADR-024). The AST never holds a `Class` object — it stores the service as a **string** read from the source `Class` literal (never loaded), consistent with SOURCE retention and the JSON wire format. That string is the source-written name; canonical FQN normalization is the tooling's job (obligation #6).
3. **`@CapabilityModule` and `@CapabilityLifecycle` target `TYPE`.** `@CapabilityModule` is one-per-cap (the `@Provides`/`@Requires` carrier), mirroring `@ExerisDomain`'s `TYPE` target. `@CapabilityLifecycle` is a **marker only**, `@Target(TYPE)`, **zero-or-one per cap**: absence is valid (a cap with no bootstrap-bound lifecycle owner); more than one is a build error the tooling validator rejects (it cannot be expressed as a Java type constraint). It may annotate a class distinct from the `@CapabilityModule` class.
4. **`@Provides` / `@Requires` are `@Target(TYPE)` and directly `@Repeatable` via a nested `.List` container** (`@Provides.List` / `@Requires.List`), following the Jakarta Bean Validation idiom for repeatable constraints (`@Pattern.List`, `@Size.List`). The nested form is chosen over the repo's other container styles because both annotation names already end in `-s`, so the plural-name convention used elsewhere (`@DomainEvent` → `DomainEvents`, `@SagaStep` → `SagaSteps`) produces no clean name here. The AST **flattens** the container — consumers see a list of `ProvidesMetadata` / `RequiresMetadata`, never the container annotation.
5. **AST records live in `exeris-sdk-source-model`, are records, and round-trip.** Each is exercised by `AstJsonRoundTripTest` and obeys the Jackson 3 wire-format contract (`@JsonInclude(NON_DEFAULT)`; consumers set `FAIL_ON_NULL_FOR_PRIMITIVES=false`). Shapes:
   - `ProvidesMetadata(String service, String version)` — `service` = service-identity string (source-written, tooling-normalized — see #6); `version` nullable (omitted when absent).
   - `RequiresMetadata(String service, String versionRange, boolean optional)` — `service` = service-identity string (source-written, tooling-normalized — see #6); `versionRange` nullable; `optional` a **primitive boolean** (default `false`, dropped by `NON_DEFAULT`).
   - `CapabilityModuleMetadata(List<ProvidesMetadata> provides, List<RequiresMetadata> requires, String lifecycleOwner)` — `lifecycleOwner` = fully-qualified name of the `@CapabilityLifecycle` class, nullable when the cap has none. (Unlike a referenced `service` type, the *declaring* class is FQN-resolvable on both paths — the `-io` reader has the compilation unit's package declaration + type name — so no normalization gap applies here.)
6. **The AST stores the written service form; FQN normalization is the tooling's job.** The `-io` reader matches annotations by **simple name without symbol solving** (its documented limitation), so a `Class` literal resolves to whatever the source wrote (simple or qualified); the processor (JSR-269) yields the FQN. For the first cut the AST persists the written form and any normalization to FQN happens in tooling — revisited only if the FQN mismatch turns out to matter for capability conflict-detection.
7. **Three scope lines keep the SDK pure.**
   - **The lifecycle *interface* stays kernel-side.** The four-phase lifecycle (`initialize`/`ready`/`drain`/`terminate`, bound to kernel bootstrap) is a kernel SPI type. `@CapabilityLifecycle` only *marks* the owning class; the processor records "this class is the lifecycle owner," and the kernel/tooling bind the behaviour. The SDK never references kernel bootstrap. *(Superseded by the ADR-024 amendments of 2026-06-25 "Composition Runtime Placement" and 2026-07-21 "Boot Conductor Call Site": `CapabilityLifecycleHooks` lives SDK-side — planned for the zero-dependency `exeris-sdk-composition-lifecycle` module in 0.9.0, with the boot conductor in `exeris-sdk-composition-runtime` — and the conductor is invoked by the generated SKU bootstrap after `KERNEL READY`, never bound to kernel bootstrap. The marker-only stance of `@CapabilityLifecycle` is unchanged.)*
   - **Licensing is not an annotation.** [ADR-023](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-023-capability-licensing-taxonomy.md)'s `community`/`commercial`/`enterprise-private` is a per-cap-repository property (declared in the repo's `LICENSE` + coordinate); no SDK annotation field encodes it.
   - **The manifest is tooling's.** `cap-manifest.json` / discovery format (ADR-024 + ADR-015) is emitted by `exeris-tooling`; the SDK supplies only the AST records it serializes from.

## Consequences

### ✅ Positive Outcomes

- **[+] Zero runtime coupling is preserved.** The annotation attribute is a bare `Class<?>`, the SDK imports nothing, the AST persists service names as strings (no class loading); the invariant that justifies the whole repo layering holds.
- **[+] Type-safe, refactor-safe service references.** `Class<?>` gives compile-time-checked navigation/rename and the single "depends on a cap or a kernel SPI" mechanism ADR-024 wants — both are just interface types.
- **[+] Mirrors a proven layering.** `@CapabilityModule` → `CapabilityModuleMetadata` is structurally identical to `@ExerisDomain` → `DomainMetadata`; `AnnotationContractTest` and `AstJsonRoundTripTest` extend to it for free.
- **[+] Unblocks downstream.** budgetHQ's `ExerisObservabilityReporter` swap and the LSP's `exeris/listCapabilities` get a concrete SDK contract.

### ⚠️ Trade-offs

- **[-] A cap must have the service interface type on its compile classpath** to name it in `Class<?>` form — but ADR-024 already requires that dependency (you cannot `@Requires` a service you cannot see), so the failure-to-compile is the *correct* failure.
- **[-] FQN-vs-simple-name normalization is deferred to tooling.** The `-io` reader's no-symbol-solving limitation means the AST may carry a simple name; tooling normalizes. Acceptable for the first cut; flagged for revisit if conflict-detection needs FQN equality.
- **[-] `version` / `versionRange` are likely-to-stabilize string fields.** The 0.x stability policy covers them; the range *syntax* lives in the tooling validator, not the SDK.

### 📋 What is NOT in scope

- **`@Requires`→`@Provides` resolution, the dependency DAG, version-range intersection, and the cap-tier Wall checks** — all `exeris-tooling` build-time concerns (ADR-024 predicates 1–4 + ADR-006).
- **`cap-manifest.json` / discovery format** — emitted by tooling (ADR-024 + ADR-015).
- **The lifecycle interface** (`initialize`/`ready`/`drain`/`terminate`) — a kernel SPI type; the SDK ships only the marker. *(Superseded — see the note in decision #7: SDK-side per the ADR-024 2026-06-25 amendment; this annotations module still ships only the marker.)*
- **Licensing taxonomy** — a per-cap-repo property (ADR-023), never an SDK annotation field.
- **The mutation surface (0.5.0)** — unrelated; only mentioned to disambiguate scope.

## Cross-references

- [ADR-024](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-024-capability-composition-model.md) — the ecosystem composition model this ADR realizes in the SDK; authoritative on names + semantics.
- [ADR-023](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-023-capability-licensing-taxonomy.md) — licensing is a per-cap-repo property, kept off the annotation surface.
- [ADR-015](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-015-codegen-emission-strategy.md) — Codegen Emission Strategy: the tooling pipeline that owns `cap-manifest.json` emission and the cap discovery format (both kept out of the SDK per "What is NOT in scope").
- [ADR-037](ADR-037-source-model-io-module.md) — the `-io` module whose round-trip completion is this ADR's sequencing precondition; the same "thin implements" shape.
- [RFC-2026-06-03](../rfc/RFC-2026-06-03-capability-annotation-surface.md) — the accepted RFC this ADR locks; full options analysis (A: strings; B: `Class<?>` — chosen; C: `Service` marker interface; D: do nothing) and the annotation/AST shapes.
- [ADR-003](ADR-003%20Entity-First%20Development%20Strategy.md) — Entity-First: the annotated class is the single source of truth, capabilities included.

## Engineering Protocol

This ADR is **forward-looking** — none of the four annotations or AST records exist yet. Migration owner: repo author; target window: 0.4.0. Delivered as small, independently reviewable slices in dependency order:

1. **Annotations slice.** `@CapabilityModule` / `@Provides` (+ `Provides.List`) / `@Requires` (+ `Requires.List`) / `@CapabilityLifecycle` in `eu.exeris.sdk.annotation.capability`, `@Retention(SOURCE)`, `@Target(TYPE)`. `AnnotationContractTest` picks them up automatically (the SOURCE-retention + `@Target` gate the downstream consumers depend on).
2. **AST records slice.** `ProvidesMetadata` / `RequiresMetadata` / `CapabilityModuleMetadata` in `eu.exeris.sdk.sourcemodel.ast`, with `AstJsonRoundTripTest` cases (serialize → deserialize → deep equality), honouring the boxed-zero / `NON_DEFAULT` quirks.
3. **`-io` reader slice.** `SourceModelReader` reads the capability annotations into the AST, mirroring the processor, with the `unmodeledFacets()` guard extended so a future processor facet the reader misses is reported — the read-and-guard discipline established by ADR-037 Slices A–D.
4. **ROADMAP** 0.4.0 was already reconciled to ADR-024 vocabulary (the RFC PR) — keep aligned as slices land.
