---
name: exeris-sdk-ast-jackson-contract-review
description: AST + Jackson 3 wire-format contract review for exeris-sdk. Use whenever you add/change/remove an AST record under `eu.exeris.sdk.sourcemodel.ast.*` or mutation record under `eu.exeris.sdk.sourcemodel.mutation.*`, touch Jackson config (`@JsonInclude`, serializers/deserializers, `@JsonSubTypes`), or bump `jackson-annotations` / `jackson.version` in the BOM — during editing, not only at PR review.
---

# Exeris SDK AST + Jackson 3 Wire-Format Contract Review

## Purpose
Enforce the wire-format contract that ties together processor (Repo `exeris-tooling`), codegen (same), LSP (Repo `exeris-platform`), and future consumers: AST and mutation types are records (Jackson 3 silently drops fields on classes with record-style accessors); `FAIL_ON_NULL_FOR_PRIMITIVES=false` is the consumer contract; `@JsonInclude(NON_DEFAULT)` boxed-zero hazard avoided; `jackson-annotations` on the 2.x line (2.22); polymorphic mutation family (`MutationOp` / `MutationResult`, ADR-042) registered in `@JsonSubTypes` and verified through sealed interfaces.

## When to Use
- Any change or PR adding / removing / changing AST records under `eu.exeris.sdk.sourcemodel.ast.*`.
- Any change or PR adding / removing / changing mutation records or sealed subtypes under `eu.exeris.sdk.sourcemodel.mutation.*` (`MutationOp`, `MutationResult`, ADR-042).
- Any change or PR touching Jackson configuration (`@JsonInclude`, `@JsonSubTypes`, `@JsonTypeInfo`, custom serializer / deserializer).
- Any change or PR touching `jackson-annotations` version in BOM.
- Any change or PR adding `@JsonInclude(NON_DEFAULT)` to a new boxed-numeric field.

## Required Inputs
- PR diff scoped to AST / mutation records + Jackson config.
- Existing `AstJsonRoundTripTest` and `MutationWireFormatTest` coverage.
- BOM version state.

## Review Procedure
1. **Records-only** — every AST and mutation type MUST be a `record`. A `final class` with record-style accessors is the canonical Jackson 3 silent-failure mode (`ActionParamMetadata` regression). Hard reject any new AST or mutation class.
2. **`AstJsonRoundTripTest` coverage** — every public AST record has a case there. New record without test → reject.
3. **`MutationWireFormatTest` coverage & sealed-interface deserialization** — every `MutationOp` and `MutationResult` subtype must be round-tripped through its sealed-interface type (`MutationOp.class` / `MutationResult.class`), asserting that payload keys are present in the JSON body and concrete subtypes recover intact.
4. **Polymorphic discriminator registration** — any new `MutationOp` subtype MUST be registered under `@JsonSubTypes` on `MutationOp` with its `"op"` name; any new `MutationResult` subtype MUST be registered under `@JsonSubTypes` on `MutationResult` with its `"outcome"` name. Missing registration → hard reject.
5. **Boxed-zero hazard** — flag any new boxed-numeric field with `@JsonInclude(NON_DEFAULT)` where `0` could be a meaningful value. Jackson 3 drops boxed-zero on serialise.
6. **Primitive boolean fields** — primitive boolean defaults to `false`; combined with `@JsonInclude(NON_DEFAULT)` the field disappears when `false`. Document if this is meaningful to the consumer (it usually is). Do **not** explain `FAIL_ON_NULL_FOR_PRIMITIVES=false` as following from that drop — measured, an absent property binds the primitive's own default and raises nothing; the flag is for an explicit `null`, which this SDK's writer never emits and a foreign producer does. That false causality was carried in five files at once and corrected 2026-08-26.
7. **`jackson-annotations` version** — MUST stay on the **2.x** line (currently `2.22`), never unified onto the databind 3.x number. The exact value is dictated by the matching `jackson-bom`'s `jackson.version.annotations`; a change is legitimate only when it tracks that pairing (and keeps `JsonSerializeAs`, floor `2.21`). Moving it onto a 3.x number, or off the jackson-bom-dictated value, → hard reject.
8. **`jackson.version` (databind / datatype)** — bump implies coordinated `jackson-annotations` review: read the new `jackson-bom`'s `jackson.version.annotations` and move `jackson.annotations.version` to match (currently `3.2.0` → `2.22`; `JsonSerializeAs` floor `2.21` preserved).
9. **`@JsonInclude` consistency** — new fields use the same `@JsonInclude` directive as the rest of the AST (verify against `FieldMetadata`, `ViewMetadata`, `DomainMetadata`; note the deliberate exceptions — `ViewMetadata` class-level and the `FieldMetadata` bounds component-level `NON_NULL` dodge the NON_DEFAULT boxed-zero drop).
10. **Cross-repo impact** — AST shape change visibly affects processor / codegen / LSP. Flag for `MIGRATION.md` entry and downstream coordination.
11. **Decision and report** — `APPROVE` / `CONDITIONAL` / `REJECT`.

## Decision Logic
- **APPROVE**: Record-only; round-trip test cases present in `AstJsonRoundTripTest` and/or `MutationWireFormatTest`; polymorphic subtype registered in `@JsonSubTypes`; boxed-zero hazard absent or documented; `jackson-annotations` on its 2.x line (2.22), jackson-bom-paired; `@JsonInclude` consistent.
- **CONDITIONAL**: Missing round-trip test case or discriminator registration for an otherwise sound record — propose the test or registration.
- **REJECT**: AST or mutation class instead of record; new record without round-trip test; unregistered subtype in `@JsonSubTypes`; boxed-zero hazard introduced without rationale; `jackson-annotations` bump without coordination.

## Completion Criteria
- Records-only check done.
- Round-trip coverage check done (`AstJsonRoundTripTest` for AST, `MutationWireFormatTest` for mutation).
- Polymorphic discriminator check done (registered in `@JsonSubTypes`).
- Boxed-zero hazard check done.
- Primitive boolean awareness check done.
- `jackson-annotations` version check done.
- Cross-repo impact flagged when applicable.
- Verdict and remediation recorded.

## Review Output Template
1. **Scope analysed** (AST records / mutation records / Jackson config touched)
2. **Records-only check** (all records / class found)
3. **`AstJsonRoundTripTest` coverage** (covered / new record without test)
4. **`MutationWireFormatTest` coverage** (covered through sealed interface / missing)
5. **Polymorphic discriminator registration** (registered under `@JsonSubTypes` / missing)
6. **Boxed-zero hazard** (none / present + rationale)
7. **Primitive boolean awareness** (consumer contract honoured)
8. **`jackson-annotations` version** (2.x line, 2.22 — jackson-bom-paired, coordinated on bump)
9. **`@JsonInclude` consistency**
10. **Cross-repo impact** (none / processor + codegen + LSP coordination required)
11. **Verdict** (`APPROVE` / `CONDITIONAL` / `REJECT`)
12. **Required actions** (precise and minimal)

## Non-Negotiable Rules
- Never approve an AST or mutation type as `class` (must be `record`).
- Never approve a new AST record without a round-trip test case in `AstJsonRoundTripTest`.
- Never approve a new `MutationOp` or `MutationResult` variant without `@JsonSubTypes` discriminator registration and a round-trip test in `MutationWireFormatTest` through the sealed-interface type.
- Never approve `jackson-annotations` version bump without coordinated upstream lift.
- Never approve silent introduction of boxed-zero hazard.
