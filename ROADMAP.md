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
- [x] Round-trip property tests across the budgetHQ corpus — `BudgetHqCorpusRoundTripTest` over `corpus/budgethq/` (BankConnection, BankAccount, BankTransaction, User + IdentityProvider enum, Investment), each ported from the cited real budgetHQ entity (budgetHQ does not author `@ExerisDomain` sources yet; reading raw BHQ sources directly lands with the 0.6.x dogfood). Properties per member: `unmodeledFacets()` empty, mutate→invert restores content+AST, idempotence, reader-sees-writer, plus relationship and field-type round-trips. Caught a real artifact on first run: removal mutations leave an indentation-only line (LexicalPreservingPrinter), so removal inverses are content/AST-equal but not byte-equal — documented as a writer limitation; conflict detection must compare ASTs, not text (input to the conflict-resolution RFC below)
- [ ] Conflict resolution: user edits since last codegen vs. tooling-driven mutations — direction set in [RFC-2026-06-11](docs/rfc/RFC-2026-06-11-source-model-conflict-resolution.md) (ACCEPTED, Option B) and **locked in [ADR-042](docs/adr/ADR-042-bidirectional-mutation-surface.md)** (AST-level three-way comparison). Implementation tracked under 0.5.0 below

## 0.4.0 — capability annotation surface (ADR-024)

> Goal: caps (IDP, payments, audit, observability, …) declare what they **provide** and **require** as first-class annotations the processor recognizes. Surface + service-reference model decided in [RFC-2026-06-03](docs/rfc/RFC-2026-06-03-capability-annotation-surface.md); implements [ADR-024](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-024-capability-composition-model.md). The earlier `@Capability` / `@CapabilityRef` / `CapabilityMetadata` wording here predated ADR-024 and is superseded by its `@CapabilityModule` / `@Provides` / `@Requires` vocabulary.

- [x] `@CapabilityModule` / `@Provides` / `@Requires` / `@CapabilityLifecycle` annotations (`@Retention(SOURCE)`, new `eu.exeris.sdk.annotation.capability` package) — services referenced by `Class<?>` (RFC Option B); `@CapabilityLifecycle` is a marker only (the lifecycle interface stays kernel-side). Slice 1 (PR #33)
- [x] Supporting AST records `CapabilityModuleMetadata` / `ProvidesMetadata` / `RequiresMetadata` in `source-model` (service stored as a source-written name string, tooling-normalized to FQN; `version` / `versionRange` as strings). Slice 2 (PR #34)
- [x] `-io` reader reads the capability annotations into the AST (same read-and-guard discipline the entity surface already has) — `readCapabilityModule()` reads `@Provides`/`@Requires` (direct, repeated, hand-written `.List` containers) with written-form services per ADR-038, same-unit `@CapabilityLifecycle` → `lifecycleOwner`, and the `unmodeledFacets()` guard armed for `@CapabilityModule` sources. Slice 3
- [ ] **Out of scope (tooling, not SDK):** `@Requires`→`@Provides` resolution, DAG / version / Wall validation, and the `cap-manifest.json` discovery format are `exeris-tooling` concerns (ADR-024 + ADR-015). The SDK supplies only the annotations + AST records they serialize from.

## 0.5.0 — bidirectional sync surface

> Goal: SDK exposes the metadata mutations that LSP, Studio, and IDE plugins call. Design locked in [ADR-042](docs/adr/ADR-042-bidirectional-mutation-surface.md) (implements [RFC-2026-06-11](docs/rfc/RFC-2026-06-11-source-model-conflict-resolution.md)); records in `source-model`, detection + application in `-io`, baseline-trust fields emitted by `exeris-tooling` codegen.

- [x] `MutationOp` records (add/rename/change-type field, change relationship cardinality, …) — `source-model`, path-addressed (ADR-042 slice 1). Sealed interface with nine nested op records (`AddField`/`RemoveField`/`RenameField`/`ChangeFieldType`/`AddRelationship`/`RemoveRelationship`/`ChangeRelationshipCardinality`/`AddAction`/`RemoveAction`); polymorphic JSON via an `"op"` discriminator. New package `eu.exeris.sdk.sourcemodel.mutation` (operations over the AST, not AST nodes); JavaParser-free, Jackson-only — zero-coupling enforcer green
- [x] `MutationResult` records — four outcomes: `SUCCESS` / `CONFLICT` / `VALIDATION_ERROR` / `NO_BASELINE` (ADR-042 slice 1). Sealed interface; `Conflict` carries baseline/current/intended values; `NoBaseline` shape frozen with a `NoBaselineCause` enum (`MISSING_BASELINE`/`STALE_DIGEST`/`SCHEMA_VERSION_SKEW`); `"outcome"` discriminator
- [x] Path-based addressing (`/entities/Order/fields/total`) — shared by ops and conflict reports. `MutationPath` (build/parse/validate + `isSameOrAncestorOf` for the slice-2 ancestor-or-descendant rule); ops carry the path as a plain wire string
- [x] AST-level three-way conflict detection in `-io` (ancestor-or-descendant overlap; convergent edits are `SUCCESS`) — ADR-042 slice 2. `SourceModelConflictDetector.detect(op, baseline, current)` compares `read(currentSource)` against the deserialized baseline at member granularity (field/relationship/action paths): drift = current ≠ baseline; conflict = drift ∧ non-convergent (the user hasn't already landed on the op's intent); sibling edits never conflict. Returns `SUCCESS`/`CONFLICT`, plus `VALIDATION_ERROR` for the structural rejections visible without a baseline (unparseable path, path-kind/op mismatch, wrong entity). Pure over two `DomainMetadata` values — no source reading, no digest, no `NO_BASELINE` (slice 3) and no mutation (slice 4). Hand-built SUCCESS/CONFLICT/convergent/sibling matrix + a budgetHQ corpus-backed case
- [~] Baseline-trust fields (ADR-042 slice 3, **cross-repo**). **SDK side done:** `source-model` gains the shared contract — `SchemaVersion.CURRENT` (a dedicated wire-format-schema constant, decoupled from the Maven artifact version so a no-AST-change release doesn't read as a skew), `SourceDigest` (pure, JavaParser-free, line-ending/trailing-whitespace-neutral SHA-256 so both codegen and `-io` compute it identically), and `BaselineTrust` (the two trust fields as JSON siblings of the domain, each read blind to the other via `ignoreUnknown`). `-io` `SourceModelConflictDetector.checkBaselineTrust` + JSON/source `detect` overloads map missing/unparseable/schema-skew → `NO_BASELINE` before detection. **Decision:** per ADR obligation 4–5 tension, `STALE_DIGEST` is **not** a detection-time block — three-way detection runs on an edited source (its whole point); the digest is the apply-time optimistic-concurrency token (slice 4). **Remaining (cross-repo):** `exeris-tooling` codegen emits `sourceDigest` + `schemaVersion` into each `exeris-metadata/<entity>.json` + the `.link.md` stub
- [x] Conflict-aware application + optimistic concurrency token (ADR-042 slice 4). `SourceModelMutationApplier.apply(op, baselineJson, currentSource[, token])` composes detection (slices 2–3) with the idempotent writer: applies only on a `SUCCESS` verdict — never over a non-convergent user edit — and returns `ApplyResult{outcome, source}` (source returned unchanged on `CONFLICT`/`NO_BASELINE`/`VALIDATION_ERROR`; idempotent no-op when the op is convergent). The `sourceDigest` is the apply-time optimistic-concurrency token → `STALE_DIGEST` when the live source moved since the op was computed. `VALIDATION_ERROR` covers `changeRelationshipCardinality` (structural — no writer mutation behind it) and writer/parse rejections. Single-op; conflict-aware **batch** apply is deferred (it must thread the accumulating tooling edits through detection — own semantics)

## 0.6.0–0.9.0 — feedback-driven cleanups

> Goal: scope adjustments based on real usage from budgetHQ (first dogfood) and the IDP capability (first cap).

- [ ] Drop annotations that didn't earn their keep
- [ ] Add annotations real users repeatedly hand-roll
- [ ] AST records — split / merge based on access patterns observed in tooling
- [ ] Deprecation pipeline (mark `@Deprecated`, document migration in `MIGRATION.md`)

### Annotation-surface honesty — inert attributes (issues to address)

> An internal surface review found attributes the SDK **advertises** but no consumer honours — they read as live configuration while silently doing nothing. Logged here as roadmap issues (not yet opened on GitHub). The SDK-owned obligation is the same in every case — **make the annotation tell the truth**: wire it end-to-end, or run the deprecation pipeline so it stops reading as configuration. Some pair with a downstream gap (a tooling consumer or a kernel SPI) tracked in `exeris-tooling` / `exeris-kernel`.

- [ ] **`@ExerisDomain` system-field overrides are inert** — all nine override attributes (`tenantIdField`, `versionField`, `softDeleteField`, `softDeleteTimestampField`, `softDeletedByField`, `createdAtField`, `createdByField`, `updatedAtField`, `updatedByField`) are advertised, but the repository generator hard-codes the canonical accessor names. Decide: honour them in tooling, or deprecate + document the canonical names (`tenantId`/`version`/`createdAt`/`createdBy`/`updatedAt`/`updatedBy`/…) as mandatory in `MIGRATION.md`.
- [ ] **`@Relationship.targetEntity` is inert** — the processor derives the target from the field's Java type, so the attribute does nothing on the explicit-UUID-FK style. Decide: document the entity-typed requirement, or push tooling to prefer `targetEntity` when set (fall back to the field type only when `void.class`).
- [ ] **`@Action(name=…)` is inert** — action identity is the method name, so a `name` chosen to dodge a bean-setter collision has no effect, even though `name` is a *required* attribute. Decide: clarify in the javadoc that the method name is the identity today, or make tooling honour `name` as the stable action id.
- [ ] **`@DomainEvent.topic` is inert against the Open-Core event bus** — topic routing is an enterprise/Kafka-tier concern. Javadoc note that `topic` is enterprise-tier routing metadata, ignored by the Open-Core event engine.
- [ ] **Capability surface is inert end-to-end** — `@Provides` / `@Requires` / `@CapabilityModule` (+ their AST records) are read into the AST (0.4.0 Slice 3) but no processor extraction nor codegen consumer uses them. Decide: wire the graph end-to-end with a tooling registry consumer, or a javadoc + `MIGRATION.md` note that the capability annotations are reserved and not yet consumed in Open-Core.
- [ ] **Streaming / real-time attributes are inert** — `@ExerisDomain(realTimeApi=true)` and `@Action(streaming=true, streamEventType=…, realTimeUpdates=true)` advertise server-push, but there is no Open-Core streaming HTTP SPI to back them (kernel-owned root cause). Javadoc note that they are not wired in Open-Core until the kernel streaming affordance lands; the annotations gain meaning once it does.
- [ ] **No `universe` data-scope tier** — the surface offers only global-singleton and tenant-private (`tenantScoped`), so a dataset that many tenants co-inhabit and read across (rather than each tenant's private rows) is unmodellable and `tenantScoped` does double duty; needs a third kernel RLS mode. Add a first-class universe scope to `@ExerisDomain` (cross-tenant-readable, row-owned but not row-hidden) once kernel persistence supports it.

### UI kit — gaps to address

> The same surface review exercised `exeris-sdk-ui-kit` against a generated app and found gaps between what the kit ships and what generated UI needs. These connect to the broader ui-kit scope question (the kit is today a Tailwind preset + a handful of component classes). Tooling-side items (codegen-ts emitter parity) are tracked in `exeris-tooling`. Suggested order: **B1+B2 → B5 → B3 → B4**.

- [ ] **B1 — Tailwind major mismatch (headline).** The kit is Tailwind **v3**: a JS preset (`exeris-sdk-ui-kit/tailwind.preset.js`, documented as `presets: [exerisPreset]`) with `peerDependencies.tailwindcss: ">=3.4.0"`. Tailwind **v4** is CSS-first (`@import` / `@theme`) and removed JS presets, so the documented `presets: [exerisPreset]` wiring does not work for a v4 consumer. Decide: ship a v4 entry point (a `@theme` CSS export) alongside the JS preset, or synchronize the kit's Tailwind major with the generated-app toolchain.
- [ ] **B2 — preset not in package `exports`.** `tailwind.preset.js` exists at the package root but is absent from `package.json` `exports` (only `.`, `./styles`, `./styles/*`). Under `exports` enforcement the README's preset import resolves to nothing. Add the preset to `exports` and pin the import path the README shows.
- [ ] **B5 — `@Field.dataType` lost at the annotation→AST boundary.** `@Field.dataType` (`Field.java`, e.g. `currency` / `percent` / `url`) is declared, but `FieldMetadata` carries only `format` — no `dataType` component — so the semantic type never reaches a consumer and display formatting silently degrades. Decide: carry `dataType` into `FieldMetadata`, and clarify the `format` vs `dataType` split (which one owns display semantics). This is a wire-format change to the AST — treat as a contract change.
- [ ] **B3 — component styles cover a fraction of `ComponentType`.** `ComponentType` declares ~20 control kinds (`SELECT`, `TEXT_AREA`, `TOGGLE`, `SLIDER`, `RADIO_GROUP`, `FILE_UPLOAD`, `COLOR_PICKER`, `RICH_TEXT_EDITOR`, date/datetime pickers, …), but the kit ships styled classes for essentially one form control (`exeris-input`) plus chrome (`exeris-btn` / `badge` / `card` / `table` / `alert`). Fill in the missing control styles so generated forms are not unstyled for most field types.
- [ ] **B4 — extensibility / localization gaps.** Four sub-gaps in the design-time surface: (a) **no i18n** — no locale / message-key surface anywhere in the SDK; (b) **no custom-component registration** — `ComponentType` is a closed enum, so a consumer cannot register a control the kit does not enumerate; (c) **no icon abstraction** — `icon()` is a free-form `String` on every annotation that has one (`@UI`, `@NavMenu`, `@Tab`, …), with no icon-set contract; (d) **field-level role visibility only partial** — `visibleWhen` (expression) exists on `@UI` / `@Field` and `roles[]` exists on `@ExerisDomain` / `@Action` / `@NavMenu`, but there is no declarative field-level `roles[]`.

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
