---
# DO NOT EDIT — generated from .agents/skills/exeris-sdk-ast-jackson-contract-review/SKILL.md (agents-md-schema.md rule 7). Edit the source.
name: exeris-sdk-ast-jackson-contract-review
description: AST + Jackson 3 wire-format contract review for exeris-sdk. Use whenever you add/change/remove an AST record under `eu.exeris.sdk.sourcemodel.ast.*`, touch Jackson config (`@JsonInclude`, serializers/deserializers), or bump `jackson-annotations` / `jackson.version` in the BOM — during editing, not only at PR review.
---
<!-- DO NOT EDIT. Generated from .agents/skills/exeris-sdk-ast-jackson-contract-review/SKILL.md by the AGENTS.md adapter step
     (agents-md-schema.md rule 7). Edit the source, not this file. -->
# Exeris SDK AST + Jackson 3 Wire-Format Contract Review

## Purpose
Enforce the wire-format contract that ties together processor (Repo `exeris-tooling`), codegen (same), LSP (Repo `exeris-platform`), and future consumers: AST types are records (Jackson 3 silently drops fields on classes with record-style accessors); `FAIL_ON_NULL_FOR_PRIMITIVES=false` is the consumer contract; `@JsonInclude(NON_DEFAULT)` boxed-zero hazard avoided; `jackson-annotations` on the 2.x line (2.22).

## When to Use
- Any change or PR adding / removing / changing AST records under `eu.exeris.sdk.sourcemodel.ast.*`.
- Any change or PR touching Jackson configuration (`@JsonInclude`, custom serializer / deserializer).
- Any change or PR touching `jackson-annotations` version in BOM.
- Any change or PR adding `@JsonInclude(NON_DEFAULT)` to a new boxed-numeric field.

## Required Inputs
- PR diff scoped to AST + Jackson config.
- Existing `AstJsonRoundTripTest` coverage.
- BOM version state.

## Review Procedure
1. **Records-only** — every AST type MUST be a `record`. A `final class` with record-style accessors is the canonical Jackson 3 silent-failure mode (`ActionParamMetadata` regression). Hard reject any new AST class.
2. **`AstJsonRoundTripTest` coverage** — every public AST record has a case there. New record without test → reject.
3. **Boxed-zero hazard** — flag any new boxed-numeric field with `@JsonInclude(NON_DEFAULT)` where `0` could be a meaningful value. Jackson 3 drops boxed-zero on serialise.
4. **Primitive boolean fields** — primitive boolean defaults to `false`; combined with `@JsonInclude(NON_DEFAULT)` the field disappears when `false`. Document if this is meaningful to the consumer (it usually is). Do **not** explain `FAIL_ON_NULL_FOR_PRIMITIVES=false` as following from that drop — measured, an absent property binds the primitive's own default and raises nothing; the flag is for an explicit `null`, which this SDK's writer never emits and a foreign producer does. That false causality was carried in five files at once and corrected 2026-08-26.
5. **`jackson-annotations` version** — MUST stay on the **2.x** line (currently `2.22`), never unified onto the databind 3.x number. The exact value is dictated by the matching `jackson-bom`'s `jackson.version.annotations`; a change is legitimate only when it tracks that pairing (and keeps `JsonSerializeAs`, floor `2.21`). Moving it onto a 3.x number, or off the jackson-bom-dictated value, → hard reject.
6. **`jackson.version` (databind / datatype)** — bump implies coordinated `jackson-annotations` review: read the new `jackson-bom`'s `jackson.version.annotations` and move `jackson.annotations.version` to match (currently `3.2.0` → `2.22`; `JsonSerializeAs` floor `2.21` preserved).
7. **`@JsonInclude` consistency** — new fields use the same `@JsonInclude` directive as the rest of the AST (verify against `FieldMetadata`, `ViewMetadata`, `DomainMetadata`; note the deliberate exceptions — `ViewMetadata` class-level and the `FieldMetadata` bounds component-level `NON_NULL` dodge the NON_DEFAULT boxed-zero drop).
8. **Cross-repo impact** — AST shape change visibly affects processor / codegen / LSP. Flag for `MIGRATION.md` entry and downstream coordination.
9. **Decision and report** — `APPROVE` / `CONDITIONAL` / `REJECT`.

## Decision Logic
- **APPROVE**: Record-only; round-trip test case present; boxed-zero hazard absent or documented; `jackson-annotations` on its 2.x line (2.22), jackson-bom-paired; `@JsonInclude` consistent.
- **CONDITIONAL**: Missing round-trip test case for an otherwise sound record — propose the test.
- **REJECT**: AST class instead of record; new record without round-trip test; boxed-zero hazard introduced without rationale; `jackson-annotations` bump without coordination.

## Completion Criteria
- Records-only check done.
- Round-trip coverage check done.
- Boxed-zero hazard check done.
- Primitive boolean awareness check done.
- `jackson-annotations` version check done.
- Cross-repo impact flagged when applicable.
- Verdict and remediation recorded.

## Review Output Template
1. **Scope analysed** (AST records / Jackson config touched)
2. **Records-only check** (all records / class found)
3. **`AstJsonRoundTripTest` coverage** (covered / new record without test)
4. **Boxed-zero hazard** (none / present + rationale)
5. **Primitive boolean awareness** (consumer contract honoured)
6. **`jackson-annotations` version** (2.x line, 2.22 — jackson-bom-paired, coordinated on bump)
7. **`@JsonInclude` consistency**
8. **Cross-repo impact** (none / processor + codegen + LSP coordination required)
9. **Verdict** (`APPROVE` / `CONDITIONAL` / `REJECT`)
10. **Required actions** (precise and minimal)

## Non-Negotiable Rules
- Never approve an AST type as `class` (must be `record`).
- Never approve a new AST record without a round-trip test case.
- Never approve `jackson-annotations` version bump without coordinated upstream lift.
- Never approve silent introduction of boxed-zero hazard.
