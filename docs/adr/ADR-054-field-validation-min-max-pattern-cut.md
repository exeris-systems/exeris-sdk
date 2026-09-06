---
title: "ADR-054: Field/Validation min/max/pattern cut — @Validation sole declaration site, FieldMetadata sole AST carrier"
type: adr
visibility: public
owning-repo: exeris-sdk
status: active
last-verified: 2026-07-22
slug: adr/ADR-054
---

# ADR-054: Field/Validation `min`/`max`/`pattern` cut — `@Validation` sole declaration site, `FieldMetadata` sole AST carrier

| Attribute       | Value                                                                                          |
|:----------------|:-----------------------------------------------------------------------------------------------|
| **Status**      | **ACCEPTED**                                                                                   |
| **Deciders**    | Arkadiusz Przychocki                                                                           |
| **Date**        | 2026-07-21                                                                                     |
| **Scope**       | cross-repo                                                                                     |
| **Owning Repo** | `exeris-sdk`                                                                                    |
| **Driven By**   | the 0.2.0 deferral in [`ROADMAP.md`](../../ROADMAP.md) ("budgetHQ usage will inform the right cut") + the 0.9.0 evidence sweep |
| **Compliance**  | Deprecation pipeline ([`MIGRATION.md`](../../MIGRATION.md)); AST wire-format contract ([ADR-042](ADR-042-bidirectional-mutation-surface.md) posture) |

> ADR numbers are a **single ecosystem-wide namespace** registered in [`exeris-docs/adr-index.md`](https://github.com/exeris-systems/exeris-docs/blob/main/adr-index.md). The jump from ADR-042 (this repo's last owned ADR — ADR-043 / ADR-050 exist here only as cross-repo `.link.md` stubs) is expected: 043–050 are owned by other Exeris repos, and 051–053 by `exeris-kernel` / `exeris-docs` (051 = kernel PAQS seam, 052 = kernel JSON mapper seam, 053 = SKU manifest format). The `exeris-docs/adr-index.md` row holds this number.

## Context and Problem Statement

The 0.2.0 Field/Validation scoping fix moved `required` / `inCreate` / `inUpdate` onto `@Field` and deprecated `@Validation.required` / `validateOn`, but deliberately **deferred** the "wider `min`/`max`/`pattern` overlap between `FieldMetadata` and `ValidationMetadata`" to the 0.6–0.9 cleanup phase, "where budgetHQ usage will inform the right cut" (ROADMAP 0.2.0). The 0.9.0 evidence sweep collected that usage and audited every declaration site, extraction path, and consumer. It found four facts, three of which contradict the model the docs had been describing since 0.2.0:

1. **The overlap was fictional at the annotation level.** `@Field` declares **none** of `min` / `max` / `minLength` / `maxLength` / `pattern` — the only pattern-shaped attribute it has ever had is `maskPattern` (log/PII masking, unrelated). `@Validation` is and always was the sole declaration site, and the budgetHQ corpus confirms it: 19/19 constraint usages sit on `@Validation` (`maxLength` ×14, `pattern` ×5, `min`/`minLength` ×0). The long-documented "@Field owns basic shape hints" framing (repo `CLAUDE.md`, both package-infos, the scoping review skill) described attributes that do not exist.
2. **`ValidationMetadata` is a fully orphaned record.** It is never populated by the tooling processor nor the `-io` reader, never referenced by `DomainMetadata` / `FieldMetadata`, and never read by any generator — it has never appeared on the domain wire. Its `notNull` / `notBlank` components were an aspiration ("derived from `required` by the processor") that never happened; the true statement is that DB NOT NULL / not-blank semantics derive from `FieldMetadata.required` at **generator** level. Its `patternMessage` component has no `@Validation` source at all (`@Validation` declares `message`, not `patternMessage`).
3. **A live reader parity break.** The `-io` reader silently dropped `@Validation.minLength` / `maxLength` on read, while the processor has extracted both into `FieldMetadata` all along — spurious ADR-042 drift on any field declaring them (14 `maxLength` occurrences in the corpus).
4. **The boxed-zero hazard was real on the populated carrier.** `FieldMetadata`'s class-level `@JsonInclude(NON_DEFAULT)` drops boxed zero on serialization, so `min = 0` (a non-negativity floor) was inexpressible on the wire.

The deferred question — "which of the two records should carry the constraint values?" — therefore dissolves: only one record was ever populated or consumed. What remains is to make the type surface and the documentation tell the truth, fix the two live defects, and remove the dead record before the 1.0.0 API freeze locks it into the GA contract.

## 🏁 The Decision

**`@Validation` is confirmed as the sole declaration site of `min` / `max` / `minLength` / `maxLength` / `pattern` — nothing moves at the annotation level — and `FieldMetadata` as the sole AST carrier of those values, populated from `@Validation` by the build-time processor and the `-io` reader. `ValidationMetadata` is removed outright in 0.9.0 — no deprecation cycle: the record is a verified orphan (no processor, reader, or generator ever touched it) and no SDK artifact has ever been published to a registry, so no external compile-time dependent can exist and a deprecation window would be vacuous (0.x permits the break; contrast `@Validation.required`/`validateOn`, which had real source usage and got the full window). The four numeric bounds on `FieldMetadata` move to per-component `@JsonInclude(NON_NULL)` so zero-valued bounds survive the wire (`SchemaVersion.CURRENT` → `"0.9.0"`); the `-io` reader now reads `@Validation.minLength` / `maxLength` (parity bugfix). DB NOT NULL / not-blank semantics derive from `FieldMetadata.required` at generator level; `patternMessage` is dropped as unconsumed.**

**Concrete obligations:**

1. **Slice 1 — reader parity (bugfix).** `SourceModelReader` reads `@Validation.minLength` / `maxLength` into `FieldMetadata`, matching the processor's long-standing extraction. Read-side only; sources declaring them now round-trip instead of producing spurious ADR-042 drift.
2. **Slice 2 — boxed-zero fix + schema.** `FieldMetadata.min` / `max` / `minLength` / `maxLength` move to per-component `@JsonInclude(NON_NULL)` (the deliberate `ViewMetadata` `NON_NULL` choice is the precedent); `SchemaVersion.CURRENT` bumps `"0.8.0"` → `"0.9.0"` because the wire can express states it could not before (ADR-042 posture: the schema version names the shape).
3. **Slice 3 — removal + docs retirement.** `ValidationMetadata` is deleted (record + its guard tests). *(Originally landed as `@Deprecated(forRemoval = true)` with a 1.0.0 removal target; upgraded to outright removal in the same release once the zero-possible-consumers argument was accepted — no registry artifact exists, so the window protected nobody.)* The docs retire the fictional framing in lockstep: repo `CLAUDE.md`, both package-infos (`eu.exeris.sdk.annotation`, `eu.exeris.sdk.sourcemodel.ast`), the `exeris-sdk-field-validation-scoping-review` skill, `ROADMAP.md`, the source-model `README.md`, plus `MIGRATION.md` and `CHANGELOG.md` entries.
4. **Tooling lockstep (`exeris-tooling`).** Pin bump to SDK 0.9.0; removal of the dead TS `ValidationMetadataSchema` from codegen-ts; a one-time codegen re-run so `"0.8.0"`-stamped baselines are re-emitted as `"0.9.0"` (the standard `SCHEMA_VERSION_SKEW` recovery).

## Consequences

### ✅ Positive Outcomes

- **[+] Zero user migration.** No `@Field` / `@Validation` source change is required: the sole declaration site is confirmed, not moved. Compile-time references to `ValidationMetadata` (none exist downstream beyond the SDK's own guard tests) migrate to `FieldMetadata`.
- **[+] Dead surface removed before the 1.0.0 freeze.** An orphaned record with an unrealized derivation story and an unsourced `patternMessage` will not be frozen into the GA contract.
- **[+] Zero-floor constraints are now expressible.** `@Validation(min = 0)` reaches the DB `CHECK (col >= 0)`, OpenAPI `minimum: 0`, and client validators instead of vanishing on serialization.

### ⚠️ Trade-offs

- **[-] `"0.8.0"`-stamped baselines skew once.** The schema bump makes existing baselines read as `NO_BASELINE(SCHEMA_VERSION_SKEW)`; the one-time codegen re-run re-stamps them. Accepted — the ADR-042 posture is to refuse a cross-shape baseline rather than assume compatibility.

### 📋 What is NOT in scope

- **`@Validation.message` extraction** — the annotation declares a custom error message that no processor/reader extracts today. Logged as an annotation-honesty candidate (the ROADMAP inert-attribute discipline), not part of this cut.

## Cross-references

- [ADR-042](ADR-042-bidirectional-mutation-surface.md) — the wire-format / baseline-trust posture the schema bump and the reader-parity fix comply with.
- [ADR-038](ADR-038-capability-annotation-surface.md) — the ecosystem-numbering-note precedent (same single ADR namespace discipline).
- [`MIGRATION.md`](../../MIGRATION.md) — the 0.8.x → 0.9.x section: schema bump, zero-bounds semantics, `ValidationMetadata` removal, reader parity.
- `eu.exeris.sdk.annotation` package-info + `eu.exeris.sdk.sourcemodel.ast` package-info — the two canonical scoping-rationale sites, updated in lockstep by this cut.
