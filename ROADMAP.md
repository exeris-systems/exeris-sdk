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
- [ ] **JSON round-trip tests per AST record** — wire-format guard against accidental breakage
- [ ] **Java 26 baseline rationale** in README (kernel ↔ Panama/VT requirement)
- [ ] **Field/Validation overlap fix** — pick canonical location for `required`, `inCreate`/`inUpdate` vs `Validation.validateOn`
- [ ] **Pre-publish POM metadata** — `<scm>`, `<url>`, `<developers>`, `<distributionManagement>` in root POM
- [ ] **Bump `jackson-annotations` `3.0-rc5` → GA** when stable 3.0.x ships

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
