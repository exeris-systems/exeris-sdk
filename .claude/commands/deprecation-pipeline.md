---
description: Audit a public-API removal / rename against the deprecation pipeline (@Deprecated(forRemoval) + javadoc replacement + processor fallback ≥1 minor + MIGRATION.md entry).
argument-hint: PR diff or public-API removal / rename to audit
---

Invoke the `exeris-sdk-deprecation-pipeline-review` skill and apply its full review procedure (removal/rename detection, `@Deprecated(forRemoval = true)`, javadoc canonical replacement, processor fallback-with-warning, fallback window ≥1 minor, `MIGRATION.md` entry, `ROADMAP.md` alignment, cross-repo flag) to the change below. Produce the skill's review-output template and verdict.

Change:
$ARGUMENTS
