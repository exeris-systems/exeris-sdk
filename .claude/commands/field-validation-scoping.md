---
description: Enforce Field/Validation canonical scoping — `@Field` owns shape + lifecycle; `@Validation` owns constraint rules; deferred wider overlap NOT collapsed unilaterally.
argument-hint: PR diff or `@Field` / `@Validation` attribute change to audit
---

Audit this change against Field/Validation canonical scoping.

Scoping rules (per repo `CLAUDE.md` + the two package-info files):

- **`@Field`** owns field-shape and lifecycle:
  - `required`
  - `inCreate`, `inUpdate`
  - basic shape hints: `minLength`, `maxLength`, `min`, `max`, `pattern`
- **`@Validation`** owns constraint rules:
  - `email`, `url`, `pattern`, `future`, `past`, etc.
- **Deprecated (forRemoval) in 0.2.x; removed in 1.0.0:**
  - `@Validation.required` — moved to `@Field.required`
  - `@Validation.validateOn` — moved to `@Field.inCreate` / `@Field.inUpdate`
- **Derived in the processor (NOT separately configurable):**
  - `ValidationMetadata.notNull` / `notBlank` ← `FieldMetadata.required`
- **Deferred to 0.6–0.9 (do NOT unilaterally collapse):**
  - wider `min` / `max` / `pattern` overlap between `FieldMetadata` and `ValidationMetadata`

The full rationale lives in TWO package-info files — keep them in sync:
- `exeris-sdk-annotations/src/main/java/eu/exeris/sdk/annotation/package-info.java`
- `exeris-sdk-source-model/src/main/java/eu/exeris/sdk/sourcemodel/ast/package-info.java`

Change:
$ARGUMENTS

Please review:
1. Does the change put a field-shape / lifecycle attribute (`required`, `inCreate`, `inUpdate`) on `@Validation` instead of `@Field`? Reject.
2. Does the change put a constraint rule (`email`, `url`, `future`, `past`) on `@Field` instead of `@Validation`? Reject.
3. Does the change collapse the deferred `min` / `max` / `pattern` overlap unilaterally? Reject — it waits on budgetHQ usage signal.
4. If the change deprecates an old attribute, is the canonical replacement named in javadoc + the deprecation pipeline applied?
5. If the change introduces new derived processor logic, is it consistent with the existing `notNull` / `notBlank` derivation pattern (derived in processor, not separately configurable)?
6. Are BOTH package-info files updated to reflect the scoping change?
7. Is `MIGRATION.md` updated when the change is user-visible?
8. Minimal correction if scoping discipline is at risk.

Subtle regression hotspot — single most likely place to introduce a downstream-visible bug. Treat scoping changes as contract changes, not refactors.
