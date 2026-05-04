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

- [ ] **CI** — `.github/workflows/build.yml` (`mvn -B verify` on push/PR, JDK 26)
- [x] **JSON round-trip tests per AST record** — `AstJsonRoundTripTest` exercises every AST record (19 in total, including the three graph leaves directly) through Jackson 3 serialize → deserialize and asserts deep equality. Caught two real wire-format bugs on first run: (a) `ActionParamMetadata` was a class with record-style accessors that Jackson 3 didn't recognize as getters, dropping every field on serialization — fixed by migrating to a record (consistent with all other AST types); (b) Jackson 3 strict-mode rejects `null → primitive` coercion that Jackson 2 tolerated — `FAIL_ON_NULL_FOR_PRIMITIVES=false` requirement documented in `eu.exeris.sdk.sourcemodel.ast` package-info as a consumer contract. Known limitation: `@JsonInclude(NON_DEFAULT)` on `FieldMetadata` drops `Long(0)` because Jackson 3's NON_DEFAULT treats boxed-zero as "empty" — tracked under Field/Validation overlap fix
- [ ] **Java 26 baseline rationale** in README (kernel ↔ Panama/VT requirement)
- [x] **Field/Validation overlap fix** — picked canonical locations: `required` lives on `@Field` (it's a field-shape property, not a validation rule); `inCreate`/`inUpdate` stay on `@Field` (form-lifecycle scope). `@Validation.required` and `@Validation.validateOn` are now `@Deprecated(forRemoval = true)` with javadoc pointing at the canonical attributes — to be dropped in 1.0.0. Documented end-to-end in `eu.exeris.sdk.annotation` and `eu.exeris.sdk.sourcemodel.ast` package-info. Wider `min`/`max`/`pattern` overlap between `FieldMetadata` and `ValidationMetadata` is documented and deferred to the 0.6–0.9 cleanup phase, where budgetHQ usage will inform the right cut
- [x] **Pre-publish POM metadata** — root POM now declares `<url>`, `<organization>`, `<licenses>`, `<developers>`, `<scm>`, `<issueManagement>`, `<distributionManagement>` (Sonatype Central Portal). Required by Maven Central; without these the publish step would fail validation
- [x] **Bump `jackson-annotations` `3.0-rc5` → `2.21`** — Jackson 3.x deliberately keeps annotations on the legacy 2.x line (per jackson-bom 3.x: `jackson.version.annotations=2.20+`). The 3.0-rc* annotations track was abandoned. Required for Jackson 3 databind 3.1.2 to load (`JsonSerializeAs` is a 2.21 addition)

## 0.3.0 — source-model parser + writer

> Goal: round-trip Java↔AST via JavaParser. Required by LSP and codegen-maven-plugin.

- [ ] `exeris-sdk-source-model/parser` module — JavaParser-based `.java` → `DomainMetadata`
- [ ] `exeris-sdk-source-model/writer` module — idempotent `DomainMetadata` → `.java` (preserves user comments, formatting, non-Exeris annotations)
- [ ] Round-trip property tests across the budgetHQ corpus (real entities, not synthetic fixtures)
- [ ] Conflict resolution: user edits since last codegen vs. tooling-driven mutations

## 0.4.0 — `@Capability` surface

> Goal: capabilities (IDP, payments, audit, …) are first-class annotations the processor recognizes.

- [ ] `@Capability` annotation + supporting AST records (`CapabilityMetadata`, `CapabilityRefMetadata`)
- [ ] Reference resolution semantics (`@CapabilityRef("idp")` → linked `CapabilityMetadata`)
- [ ] Versioning scheme for capability contracts (independent of SDK version)
- [ ] Capability discovery format (manifest under `META-INF/exeris/capabilities/`)

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
