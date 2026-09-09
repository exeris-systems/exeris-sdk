---
name: ast-wire-format-review
description: Review AST records and wire-format serialization contracts across source-model and downstream tooling consumers.
disable-model-invocation: true
---

<!-- DO NOT EDIT. Generated from .agents/workflows/ast-wire-format-review.md by agents_render.py
     (exeris-systems/exeris-agents; agents-md-schema.md rule 7). Edit the source. -->
Review AST record changes against Jackson 3 serialization invariants.

Priorities:
1. Record types: verify AST data structures are Java `record` types, never classes.
2. Wire-format safety: check primitive defaults vs explicit nulls (`FAIL_ON_NULL_FOR_PRIMITIVES=false`).
3. Boxed numeric zero: verify bounds fields use `@JsonInclude(NON_NULL)` rather than `NON_DEFAULT`.
4. Round-trip coverage: ensure `AstJsonRoundTripTest` or `MutationWireFormatTest` tests the record.
5. Downstream impact: verify consumer compatibility in `exeris-tooling` and `exeris-platform`.

Changed scope:
$ARGUMENTS

Please produce:
- Affected AST records
- Jackson 3 wire-format verification status
- Test coverage additions
- Downstream coordination notes
- Final verdict: APPROVE / CONDITIONAL / REJECT
