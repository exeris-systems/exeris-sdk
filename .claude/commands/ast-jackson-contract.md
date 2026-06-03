---
description: Enforce AST + Jackson 3 wire-format contract — AST types are records (not classes), every public record has `AstJsonRoundTripTest` coverage, Jackson 3 quirks honoured.
argument-hint: AST record change / new record / Jackson-affecting change to audit
---

Audit this change against the AST + Jackson 3 wire-format contract.

Contract rules (per repo `CLAUDE.md`):
1. **AST types MUST be records, not classes.** `ActionParamMetadata` was originally a `final class` with record-style accessors; Jackson 3 silently dropped every field because it didn't recognise them as getters. Every AST type is now a record — keep it that way.
2. **Downstream Jackson consumers must set `FAIL_ON_NULL_FOR_PRIMITIVES=false`.** Jackson 3 defaults it to `true`, but the AST uses primitive booleans with `@JsonInclude(NON_DEFAULT)` so absent fields arrive as `null`. The guard test `AstJsonRoundTripTest` configures the mapper this way — canonical reference.
3. **`@JsonInclude(NON_DEFAULT)` drops boxed-zero.** `Long(0)` is treated as "empty" by Jackson 3 and dropped on serialization. Avoid `0` as a meaningful value for `min` / `max` on boxed-numeric fields until the Field/Validation overlap fix lands.
4. **Every public AST record is exercised by `AstJsonRoundTripTest`** — serialise → deserialise → deep equality. When adding a new AST record or component, add a test case there.
5. **`jackson-annotations` pinned to 2.21** — Jackson 3 keeps annotations on the legacy 2.x line; the `3.0-rc*` track was abandoned. `2.21` specifically is required for databind `3.1.2` to load (`JsonSerializeAs` is a 2.21 addition). Do NOT "unify" annotations and databind versions.

Change:
$ARGUMENTS

Please review:
1. Are all new / changed AST types declared as `record`, not `class` (even with record-style accessors)?
2. If a new AST record is added, is there a corresponding case in `AstJsonRoundTripTest`?
3. Do any boxed-numeric fields use `0` as a meaningful value with `@JsonInclude(NON_DEFAULT)`? Investigate — boxed-zero will be dropped silently.
4. Are primitive booleans aware of the `FAIL_ON_NULL_FOR_PRIMITIVES=false` consumer contract?
5. Does the change touch `jackson-annotations` version? If yes, MUST stay at 2.21 unless a coordinated Jackson 3 release lifts the abandoned `3.0-rc*` track.
6. Does the change include `@JsonInclude` directives consistent with the rest of the AST?
7. Minimal correction if Jackson 3 contract is at risk.

This is the single most common silent-regression surface in the repo — when Jackson drops a field on serialize, no exception fires; the round-trip test is the only catch.
