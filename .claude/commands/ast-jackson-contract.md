---
description: Audit a change against the AST + Jackson 3 wire-format contract (records-only, round-trip coverage, Jackson 3 quirks, jackson-annotations 2.21).
argument-hint: AST record change / new record / Jackson-affecting change to audit
---

Invoke the `exeris-sdk-ast-jackson-contract-review` skill and apply its full review procedure (records-only check, `AstJsonRoundTripTest` coverage, boxed-zero hazard, primitive-boolean consumer contract, `jackson-annotations` 2.21 pin, cross-repo impact) to the change below. Produce the skill's review-output template and verdict.

Change:
$ARGUMENTS
