---
description: Audit a change for zero runtime coupling (no compile dep on annotations module; source-model only pulls jackson-annotations; no kernel/tooling/platform/framework imports).
argument-hint: PR diff or pom / source change to audit
---

Invoke the `exeris-sdk-zero-runtime-coupling-review` skill and apply its full review procedure (annotations-module classpath, source-model classpath, import audit, BOM elevation audit, transitive audit, ui-kit framework-runtime check) to the change below. Produce the skill's review-output template and verdict.

Change:
$ARGUMENTS
