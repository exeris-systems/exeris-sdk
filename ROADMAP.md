# Exeris SDK — Roadmap to 1.0.0 GA

The SDK defines the **contract** between user domain code and Exeris tooling.
1.0.0 GA means: **the contract surface is frozen**. Downstream consumers
(`exeris-tooling`, `exeris-platform-lsp`, IDE plugins, future capabilities)
can pin to it with semver guarantees.

This file tracks scope per milestone. Items marked `[ ]` are open; `[x]` shipped.

---

## 0.1.0 — scaffold (shipped)

- [x] Maven multi-module reactor (`bom`, `parent`, `annotations`, `source-model`, `ui-kit`)
- [x] 36 annotations under `eu.exeris.sdk.annotation.*` (Apache-2.0)
- [x] Canonical AST records under `eu.exeris.sdk.sourcemodel.ast.*` (Jackson-serializable)
- [x] `RetentionPolicy.SOURCE` across the board — annotations are compile-time only
- [x] Round-1 + round-2 review fixes (mojibake, Corelio leaks, `@Action` alias consolidation, records migration, `@author` normalization)

## 0.2.0 — quality gates + pre-publish hygiene

> Goal: CI exists, contract is testable, POMs are publishable.

- [x] **CI** — `.github/workflows/build.yml` runs `mvn -B -ntp verify` (Java tests + JaCoCo 85% instruction/line gate on source-model) and `npm run test:coverage` for the ui-kit (Vitest with v8 coverage, 85% per-file thresholds). JaCoCo + Vitest reports uploaded as artifacts on every run.
- [x] **JaCoCo 85% gate on `exeris-sdk-source-model`** — `jacoco-maven-plugin` 0.8.14 (earlier versions don't support JDK 26 class file v70); current measured coverage is 99.9% instructions / 100% lines / 94% branches across 50 classes. Annotations module is exempt from the gate (no executable code) but has `AnnotationContractTest` enforcing SOURCE retention + @Target across all top-level annotations via reflection — caught a real contract violation on first run (`SoftDeletedBy` was RUNTIME-retained, now fixed).
- [x] **JSON round-trip tests per AST record** — `AstJsonRoundTripTest` exercises every AST record (19 in total, including the three graph leaves directly) through Jackson 3 serialize → deserialize and asserts deep equality. Caught two real wire-format bugs on first run: (a) `ActionParamMetadata` was a class with record-style accessors that Jackson 3 didn't recognize as getters, dropping every field on serialization — fixed by migrating to a record (consistent with all other AST types); (b) Jackson 3 strict-mode rejects `null → primitive` coercion that Jackson 2 tolerated — `FAIL_ON_NULL_FOR_PRIMITIVES=false` requirement documented in `eu.exeris.sdk.sourcemodel.ast` package-info as a consumer contract. Known limitation: `@JsonInclude(NON_DEFAULT)` on `FieldMetadata` drops `Long(0)` because Jackson 3's NON_DEFAULT treats boxed-zero as "empty" — tracked under Field/Validation overlap fix
- [x] **Java 26 baseline rationale** in README (kernel ↔ Panama/VT requirement) — explains why 26 is required (kernel uses Virtual Threads + Panama FFM, processor must match consumer's `javac`), so teams on 21 LTS don't assume the baseline is an oversight
- [x] **Field/Validation overlap fix** — picked canonical locations: `required` lives on `@Field` (it's a field-shape property, not a validation rule); `inCreate`/`inUpdate` stay on `@Field` (form-lifecycle scope). `@Validation.required` and `@Validation.validateOn` are now `@Deprecated(forRemoval = true)` with javadoc pointing at the canonical attributes — to be dropped in 1.0.0. Documented end-to-end in `eu.exeris.sdk.annotation` and `eu.exeris.sdk.sourcemodel.ast` package-info. Wider `min`/`max`/`pattern` overlap between `FieldMetadata` and `ValidationMetadata` is documented and deferred to the 0.6–0.9 cleanup phase, where budgetHQ usage will inform the right cut
- [x] **Pre-publish POM metadata** — root POM now declares `<url>`, `<organization>`, `<licenses>`, `<developers>`, `<scm>`, `<issueManagement>`, `<distributionManagement>` (Sonatype Central Portal). Required by Maven Central; without these the publish step would fail validation
- [x] **Bump `jackson-annotations` `3.0-rc5` → `2.21`** — Jackson 3.x deliberately keeps annotations on the legacy 2.x line (per jackson-bom 3.x: `jackson.version.annotations=2.20+`). The 3.0-rc* annotations track was abandoned. Required for Jackson 3 databind 3.1.2 to load (`JsonSerializeAs` is a 2.21 addition)

## 0.3.0 — source-model parser + writer

> Goal: round-trip Java↔AST via JavaParser. Required by LSP and codegen-maven-plugin.

- [x] **`exeris-sdk-source-model-io`** — a single new sibling module (depends on `source-model` + JavaParser; keeps `source-model` dependency-light to preserve zero runtime coupling). Houses both directions — see [RFC-2026-06-03](docs/rfc/RFC-2026-06-03-source-model-parser-writer.md) and [ADR-037](docs/adr/ADR-037-source-model-io-module.md):
  - [x] parser — JavaParser-based `.java` → `DomainMetadata`, full Slices A–D: `@ExerisDomain` domain attributes, `@DomainEvent`, graph/saga/event-sourcing/internal-API facets, `@Field` + `@Validation`, actions, `@UI`, enums (PRs #28–#31), plus `unmodeledFacets()` round-trip completeness guard (PR #27)
  - [x] writer — idempotent `DomainMetadata` → `.java` on `LexicalPreservingPrinter` (preserves user comments, formatting, non-Exeris annotations); 8 mutations: add/rename/remove/changeType field, add/remove relationship, add/remove action (PRs #23–#25)
- [ ] Round-trip property tests across the budgetHQ corpus (real entities, not synthetic fixtures)
- [ ] Conflict resolution: user edits since last codegen vs. tooling-driven mutations

## 0.4.0 — capability annotation surface (ADR-024)

> Goal: caps (IDP, payments, audit, observability, …) declare what they **provide** and **require** as first-class annotations the processor recognizes. Surface + service-reference model decided in [RFC-2026-06-03](docs/rfc/RFC-2026-06-03-capability-annotation-surface.md); implements [ADR-024](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-024-capability-composition-model.md). The earlier `@Capability` / `@CapabilityRef` / `CapabilityMetadata` wording here predated ADR-024 and is superseded by its `@CapabilityModule` / `@Provides` / `@Requires` vocabulary.

- [x] `@CapabilityModule` / `@Provides` / `@Requires` / `@CapabilityLifecycle` annotations (`@Retention(SOURCE)`, new `eu.exeris.sdk.annotation.capability` package) — services referenced by `Class<?>` (RFC Option B); `@CapabilityLifecycle` is a marker only (the lifecycle interface stays kernel-side). Slice 1 (PR #33)
- [x] Supporting AST records `CapabilityModuleMetadata` / `ProvidesMetadata` / `RequiresMetadata` in `source-model` (service stored as a source-written name string, tooling-normalized to FQN; `version` / `versionRange` as strings). Slice 2 (PR #34)
- [x] `-io` reader reads the capability annotations into the AST (same read-and-guard discipline the entity surface already has) — `readCapabilityModule()` reads `@Provides`/`@Requires` (direct, repeated, hand-written `.List` containers) with written-form services per ADR-038, same-unit `@CapabilityLifecycle` → `lifecycleOwner`, and the `unmodeledFacets()` guard armed for `@CapabilityModule` sources. Slice 3
- [ ] **Out of scope (tooling, not SDK):** `@Requires`→`@Provides` resolution, DAG / version / Wall validation, and the `cap-manifest.json` discovery format are `exeris-tooling` concerns (ADR-024 + ADR-015). The SDK supplies only the annotations + AST records they serialize from.

## 0.5.0 — bidirectional sync surface

> Goal: SDK exposes the metadata mutations that LSP, Studio, and IDE plugins call.

- [ ] `MutationOp` records (add field, rename action, change relationship cardinality, …)
- [ ] `MutationResult` records (success / conflict / validation error)
- [ ] Path-based addressing (`/entities/Order/fields/total`)
- [ ] Optimistic concurrency tokens

## 0.6.0–0.9.0 — feedback-driven cleanups

> Goal: scope adjustments based on real usage from budgetHQ (first dogfood) and the IDP capability (first cap).

- [ ] Drop annotations that didn't earn their keep
- [ ] Add annotations real users repeatedly hand-roll
- [ ] AST records — split / merge based on access patterns observed in tooling
- [ ] Deprecation pipeline (mark `@Deprecated`, document migration in `MIGRATION.md`)

## 1.0.0 GA — frozen contract

> Goal: any 1.x release is binary- and source-compatible with 1.0.0.

- [ ] Public API surface review (everything not in `internal/` is contract)
- [ ] `MIGRATION-0.x-to-1.0.md` written and validated against budgetHQ
- [ ] Annotations module hits Maven Central (Apache-2.0, no Commons Clause)
- [ ] AST records module hits Maven Central
- [ ] UI kit npm package hits npm registry under `@exeris/ui-kit`
- [ ] Contract test suite shippable as `exeris-sdk-tck` for downstream consumers
- [ ] Semver enforcement via `revapi` or `japicmp` in CI

---

## Versioning policy

- **0.x** — breaking changes allowed in any release; downstream consumers pin exact version
- **1.x** — semver: minor bumps additive only, patch bumps bug-fix only
- **2.0** — would only happen for unforced design rethink (not currently planned)

## Tracking

- Per-milestone follow-ups: see open issues with `milestone: 0.X.0` label
- Round-1/round-2 review deferrals: [issue #4](https://github.com/exeris-systems/exeris-sdk/issues/4)
