---
# DO NOT EDIT — generated from .agents/skills/exeris-sdk-field-validation-scoping-review/SKILL.md (agents-md-schema.md rule 7). Edit the source.
name: exeris-sdk-field-validation-scoping-review
description: Field/Validation canonical scoping review for exeris-sdk. Use whenever `@Field` or `@Validation` attributes (or the `FieldMetadata` AST record) are touched, or when generator-derived NOT NULL / not-blank semantics (from `FieldMetadata.required`) are in scope.
---
<!-- DO NOT EDIT. Generated from .agents/skills/exeris-sdk-field-validation-scoping-review/SKILL.md by the AGENTS.md adapter step
     (agents-md-schema.md rule 7). Edit the source, not this file. -->
# Exeris SDK Field/Validation Canonical Scoping Review

## Purpose
Enforce: `@Field` owns field-shape + lifecycle (`required`, `inCreate`, `inUpdate` + the presentation facets) and declares NO constraint attributes; `@Validation` owns ALL constraint declarations (`min`, `max`, `minLength`, `maxLength`, `pattern`, `email`, `url`, `future`, `past`, …); `FieldMetadata` is the single AST carrier of the constraint values, populated from `@Validation` by the processor and the `-io` reader; `ValidationMetadata` is deprecated (removal 1.0.0); `@Validation.required` / `@Validation.validateOn` deprecated; the 0.9.0 cut (ADR-054) is never reversed; rationale stays synced across BOTH package-info files.

This is the single most likely regression surface in the repo.

## When to Use
- Any change or PR adding / removing / moving attributes on `@Field`.
- Any change or PR adding / removing / moving attributes on `@Validation`.
- Any change or PR touching the `FieldMetadata` AST record (or attempting to resurrect the removed `ValidationMetadata`).
- Any change or PR touching the generator-derived NOT NULL / not-blank semantics (← `FieldMetadata.required`).
- Any change or PR touching either of the two package-info files.

## Required Inputs
- PR diff.
- Both package-info file states.
- ADR-054 (`docs/adr/ADR-054-field-validation-min-max-pattern-cut.md`) + the repo `CLAUDE.md` scoping section.

## Canonical Scoping (per repo `CLAUDE.md` + package-info + ADR-054)
- **`@Field`** owns: `required`, `inCreate`, `inUpdate` + presentation / persistence facets. It declares **no constraint attributes** (the only pattern-shaped attribute it has ever had is `maskPattern` — log/PII masking, unrelated).
- **`@Validation`** owns: ALL constraint declarations — `min`, `max`, `minLength`, `maxLength`, `pattern`, `email`, `url`, `future`, `past`, etc.
- **`FieldMetadata`** is the **single AST carrier** of the constraint values (`minLength` / `maxLength` / `min` / `max` / `pattern`), populated from `@Validation` by the build-time processor and the `-io` reader. The four numeric bounds are per-component `@JsonInclude(NON_NULL)` (0.9.0) so zero-valued bounds survive the wire.
- **`ValidationMetadata`** was **removed outright in 0.9.0** — never populated by any processor/reader, never referenced by `DomainMetadata`, never consumed by any generator, and no published artifact existed for anyone to depend on (the deprecation window would have been vacuous; 0.x permits the break).
- **Deprecated (forRemoval) in 0.2.x; removed in 1.0.0**:
  - `@Validation.required` → moved to `@Field.required`.
  - `@Validation.validateOn` → moved to `@Field.inCreate` / `@Field.inUpdate`.
- **Derived at generator level (NOT separately declared)**:
  - DB NOT NULL / not-blank semantics ← `FieldMetadata.required`.
- **The cut landed in 0.9.0 (ADR-054)** — budgetHQ corpus evidence (19/19 constraint usages on `@Validation`) informed it. REJECT reintroducing constraint attributes on `@Field`, resurrecting `ValidationMetadata` (growing or un-deprecating it), or adding any parallel AST validation carrier.

## Review Procedure
1. **Scoping audit** — confirm every changed attribute is on the canonical annotation:
   - field-shape + lifecycle on `@Field`,
   - constraint declarations on `@Validation`.
2. **Deprecation alignment** — if the PR touches `@Validation.required` / `@Validation.validateOn`, confirm they stay `@Deprecated(forRemoval = true)` with a javadoc pointer to the canonical replacement (`@Field.required` / `@Field.inCreate`+`inUpdate`).
3. **Derived-semantics check** — NOT NULL / not-blank derive from `FieldMetadata.required` at generator level. Don't make them separately declarable.
4. **Post-cut protection (ADR-054)** — a constraint attribute proposed on `@Field`, `ValidationMetadata` resurrected / grown / un-deprecated, or a new parallel AST validation carrier → hard reject.
5. **Package-info sync** — BOTH files must reflect the scoping rationale:
   - `exeris-sdk-annotations/src/main/java/eu/exeris/sdk/annotation/package-info.java`
   - `exeris-sdk-source-model/src/main/java/eu/exeris/sdk/sourcemodel/ast/package-info.java`
6. **MIGRATION.md** — user-visible scoping change requires a `MIGRATION.md` entry with diff.
7. **Cross-repo coordination** — flag the processor (`~/exeris-systems/exeris-tooling/exeris-processor/`) when the scoping change visibly affects extraction logic (constraint-value extraction targets `FieldMetadata`; the `-io` reader must stay in parity per ADR-042).
8. **Decision and report** — `APPROVE` / `CONDITIONAL` / `REJECT`.

## Decision Logic
- **APPROVE**: Canonical scoping respected; deprecations intact with javadoc pointers; derived semantics not duplicated; ADR-054 cut not reversed; package-infos synced; MIGRATION entry present.
- **CONDITIONAL**: One specific gap (e.g. package-info sync missing on one of the two files) — propose the correction.
- **REJECT**: Constraint attribute on `@Field` (or field-shape on `@Validation`); deprecated attribute or `ValidationMetadata` revived; NOT NULL / not-blank made separately declarable; a parallel AST validation carrier introduced; package-infos out of sync.

## Completion Criteria
- Scoping audit done.
- Deprecation alignment checked.
- Derived semantics checked.
- ADR-054 cut protected.
- Package-info sync confirmed.
- MIGRATION entry confirmed.
- Cross-repo flag raised when applicable.
- Verdict and remediation recorded.

## Review Output Template
1. **Scope analysed** (`@Field` / `@Validation` / metadata records touched)
2. **Scoping audit** (canonical / regression found)
3. **Deprecation alignment**
4. **Derived semantics** (preserved / duplicated)
5. **ADR-054 cut** (intact / reversed)
6. **Package-info sync** (both files / one out of sync)
7. **MIGRATION entry** (present / missing for user-visible change)
8. **Cross-repo flag** (none / processor coordination required)
9. **Verdict** (`APPROVE` / `CONDITIONAL` / `REJECT`)
10. **Required actions** (precise and minimal)

## Non-Negotiable Rules
- Never approve a constraint attribute on `@Field` or field-shape on `@Validation`.
- Never approve revival of `@Validation.required` / `@Validation.validateOn` as non-deprecated.
- Never approve `ValidationMetadata` resurrected (removed in 0.9.0) or any parallel AST validation carrier.
- Never approve NOT NULL / not-blank as separately declarable (they derive from `FieldMetadata.required` at generator level).
- Never approve a parallel AST validation carrier alongside `FieldMetadata`.
- Never approve out-of-sync package-info files.
