---
description: Enforce deprecation pipeline — public API removal / rename requires `@Deprecated(forRemoval = true)` + canonical replacement in javadoc + processor fallback-with-warning for ≥ 1 minor release + `MIGRATION.md` entry.
argument-hint: PR diff or public-API removal / rename to audit
---

Audit this public-API change against the deprecation pipeline.

Pipeline rules (per repo `CLAUDE.md` + `MIGRATION.md`):
- `0.x` is unstable: breaking changes can land in any 0.x release; downstream consumers pin exact versions.
- `1.0.0` freezes the public API — only additive minors and bug-fix patches after that.
- For removing / renaming public API in 0.x:
  1. Mark `@Deprecated(forRemoval = true)`.
  2. Document the canonical replacement in javadoc.
  3. Give the processor a fallback-with-warning window of at least ONE minor release.
  4. Add a `MIGRATION.md` entry with the diff and removal milestone.
- Example: `@Validation.required` deprecated in 0.2.0, processor reads with build warning in 0.2.x, removed in 1.0.0.

Change:
$ARGUMENTS

Please review:
1. Does the PR remove or rename a public API surface (annotation, AST record, attribute, public method)?
2. If yes:
   - Is `@Deprecated(forRemoval = true)` applied to the deprecated surface?
   - Is the canonical replacement named in the deprecated surface's javadoc?
   - Does the processor (cross-repo in `~/exeris-systems/exeris-tooling/exeris-processor/`) have a fallback-with-warning path for ≥ 1 minor release?
   - Is there a `MIGRATION.md` section with a code diff showing the migration?
   - Is the removal milestone named (typically `1.0.0`)?
3. If removing an already-deprecated surface: has the fallback window of ≥ 1 minor release elapsed?
4. Is the change consistent with the stability policy in `ROADMAP.md`?
5. Does this require cross-repo coordination (processor update in `exeris-tooling`)? Flag.
6. Minimal correction if deprecation pipeline is at risk.

Don't ship public-API removal without the full pipeline — downstream consumers pin exact versions and need the deprecation window.
