---
name: exeris-sdk-field-validation-scoping-review
description: Field/Validation canonical scoping review for exeris-sdk. Use whenever `@Field` or `@Validation` attributes are touched, or when processor-derived properties (`notNull`, `notBlank`) are in scope.
---

# Exeris SDK Field/Validation Canonical Scoping Review

## Purpose
Enforce: `@Field` owns field-shape + lifecycle (`required`, `inCreate`, `inUpdate`, basic shape hints); `@Validation` owns constraint rules (`email`, `url`, `pattern`, `future`, `past`); `@Validation.required` / `@Validation.validateOn` deprecated; deferred wider `min` / `max` / `pattern` overlap NOT collapsed unilaterally; rationale stays synced across BOTH package-info files.

This is the single most likely regression surface in the repo.

## When to Use
- Any PR adding / removing / moving attributes on `@Field`.
- Any PR adding / removing / moving attributes on `@Validation`.
- Any PR touching `FieldMetadata` or `ValidationMetadata` AST records.
- Any PR touching processor-derived properties (`notNull`, `notBlank` ← `required`).
- Any PR touching either of the two package-info files.

## Required Inputs
- PR diff.
- Both package-info file states.
- Deferred-overlap items list (per ROADMAP / repo CLAUDE.md).

## Canonical Scoping (per repo `CLAUDE.md` + package-info)
- **`@Field`** owns: `required`, `inCreate`, `inUpdate`, `minLength`, `maxLength`, `min`, `max`, `pattern` (basic shape hints).
- **`@Validation`** owns: `email`, `url`, `pattern`, `future`, `past`, etc. (constraint rules).
- **Deprecated (forRemoval) in 0.2.x; removed in 1.0.0**:
  - `@Validation.required` → moved to `@Field.required`.
  - `@Validation.validateOn` → moved to `@Field.inCreate` / `@Field.inUpdate`.
- **Derived in processor (NOT separately configurable)**:
  - `ValidationMetadata.notNull` / `notBlank` ← `FieldMetadata.required`.
- **Deferred to 0.6–0.9 (do NOT unilaterally collapse)**:
  - wider `min` / `max` / `pattern` overlap between `FieldMetadata` and `ValidationMetadata` — waits on budgetHQ usage signal.

## Review Procedure
1. **Scoping audit** — confirm every changed attribute is on the canonical annotation:
   - field-shape + lifecycle on `@Field`,
   - constraint rules on `@Validation`.
2. **Deprecation alignment** — if the PR touches `@Validation.required` / `@Validation.validateOn`, confirm they stay `@Deprecated(forRemoval = true)` with javadoc pointer to canonical replacement.
3. **Processor-derived property check** — `notNull` / `notBlank` derived from `required`. Don't make them separately configurable.
4. **Deferred-overlap protection** — wider `min` / `max` / `pattern` overlap is intentionally deferred to 0.6–0.9. Collapsing unilaterally → hard reject.
5. **Package-info sync** — BOTH files must reflect the scoping rationale:
   - `exeris-sdk-annotations/src/main/java/eu/exeris/sdk/annotation/package-info.java`
   - `exeris-sdk-source-model/src/main/java/eu/exeris/sdk/sourcemodel/ast/package-info.java`
6. **MIGRATION.md** — user-visible scoping change requires a `MIGRATION.md` entry with diff.
7. **Cross-repo coordination** — flag the processor (`~/exeris-systems/exeris-tooling/exeris-processor/`) when the scoping change visibly affects extraction logic.
8. **Decision and report** — `APPROVE` / `CONDITIONAL` / `REJECT`.

## Decision Logic
- **APPROVE**: Canonical scoping respected; deprecations intact with javadoc pointers; derived properties not duplicated; deferred overlap untouched; package-infos synced; MIGRATION entry present.
- **CONDITIONAL**: One specific gap (e.g. package-info sync missing on one of the two files) — propose the correction.
- **REJECT**: Field-shape attribute on `@Validation` (or vice versa); deprecated attribute revived; `notNull` / `notBlank` made separately configurable; deferred overlap collapsed without budgetHQ signal; package-infos out of sync.

## Completion Criteria
- Scoping audit done.
- Deprecation alignment checked.
- Derived properties checked.
- Deferred overlap protected.
- Package-info sync confirmed.
- MIGRATION entry confirmed.
- Cross-repo flag raised when applicable.
- Verdict and remediation recorded.

## Review Output Template
1. **Scope analysed** (`@Field` / `@Validation` / metadata records touched)
2. **Scoping audit** (canonical / regression found)
3. **Deprecation alignment**
4. **Derived properties** (preserved / duplicated)
5. **Deferred overlap** (intact / collapsed)
6. **Package-info sync** (both files / one out of sync)
7. **MIGRATION entry** (present / missing for user-visible change)
8. **Cross-repo flag** (none / processor coordination required)
9. **Verdict** (`APPROVE` / `CONDITIONAL` / `REJECT`)
10. **Required actions** (precise and minimal)

## Non-Negotiable Rules
- Never approve field-shape on `@Validation` or constraint rules on `@Field`.
- Never approve revival of `@Validation.required` / `@Validation.validateOn` as non-deprecated.
- Never approve `notNull` / `notBlank` as separately configurable (they are derived).
- Never approve unilateral collapse of the deferred `min` / `max` / `pattern` overlap.
- Never approve out-of-sync package-info files.
