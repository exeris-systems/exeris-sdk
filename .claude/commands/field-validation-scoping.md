---
description: Audit a change against Field/Validation canonical scoping (@Field owns shape+lifecycle; @Validation owns constraint rules; deferred overlap not collapsed; package-infos synced).
argument-hint: PR diff or `@Field` / `@Validation` attribute change to audit
---

Invoke the `exeris-sdk-field-validation-scoping-review` skill and apply its full review procedure (scoping audit, deprecation alignment, processor-derived `notNull`/`notBlank` check, deferred `min`/`max`/`pattern` overlap protection, both-package-info sync, `MIGRATION.md` entry, cross-repo flag) to the change below. Produce the skill's review-output template and verdict.

Change:
$ARGUMENTS
