---
# DO NOT EDIT — generated from .agents/skills/exeris-sdk-coverage-gates-review/SKILL.md (agents-md-schema.md rule 7). Edit the source.
name: exeris-sdk-coverage-gates-review
description: Coverage-gate review for exeris-sdk. Use whenever you add new code/branches to `exeris-sdk-source-model` (`ast.*`, `mutation.*`) or `exeris-sdk-ui-kit`, add or restructure a test, or hit a JaCoCo (85% BUNDLE) / Vitest (85% per-file) failure — enforces where new tests belong.
---
<!-- DO NOT EDIT. Generated from .agents/skills/exeris-sdk-coverage-gates-review/SKILL.md by the AGENTS.md adapter step
     (agents-md-schema.md rule 7). Edit the source, not this file. -->
# Exeris SDK Coverage-Gate Review

## Purpose
Enforce the coverage gates and, more importantly, *where new tests belong* so the suite stays coherent. New uncovered branches in the AST should extend the existing guard/builder tests, not spawn parallel classes.

## When to Use
- Any change adding code or branches to `exeris-sdk-source-model` (`ast.*`, `mutation.*`).
- Any change adding code/branches to `exeris-sdk-ui-kit` (`src/**/*.ts`, `tailwind.preset.js`).
- Any change adding, moving, or restructuring a test.
- Any JaCoCo or Vitest coverage-gate failure in CI or locally.

## Required Inputs
- PR diff (production + test).
- Current gate config (`exeris-sdk-source-model/pom.xml`, ui-kit Vitest config).

## Canonical Rules (per repo `CLAUDE.md`)
- **`exeris-sdk-source-model`** — `jacoco-maven-plugin` ≥ 0.8.14, bound to `verify`, **0.85 BUNDLE-level** on both `INSTRUCTION` and `LINE`. Thresholds are properties (`jacoco.instruction.coverage.minimum`, `jacoco.line.coverage.minimum`) — a one-off debug override (`-Djacoco.instruction.coverage.minimum=0`) is fine but MUST NOT be committed.
- **`exeris-sdk-annotations`** — gate deliberately NOT applied (method-less `@interface` → meaningless metric). Its real gate is `AnnotationContractTest`. See [[exeris-sdk-annotation-contract-review]].
- **`exeris-sdk-ui-kit`** — Vitest v8 provider, **85% per-file** on lines / statements / functions / branches; scope `src/**/*.ts` + `tailwind.preset.js` (the rest is CSS).
- **Where new tests go** (source-model `ast.*`): wire-format concern → extend `AstJsonRoundTripTest`; builder / convenience-method concern → extend the focused `<Type>MetadataTest`. Mutation surface (`mutation.*`) → extend `MutationWireFormatTest`. Prefer extending over a parallel class.

## Review Procedure
1. **Gate still enforced** — JaCoCo plugin ≥ 0.8.14 and 0.85 thresholds intact in `exeris-sdk-source-model/pom.xml`; no committed override lowering them.
2. **No accidental gate on annotations** — `exeris-sdk-annotations` did not gain a JaCoCo gate (it's intentionally exempt).
3. **Vitest thresholds intact** — ui-kit 85% per-file on all four counters; scope unchanged.
4. **New tests in the right home** — new AST branches extend `AstJsonRoundTripTest` (wire-format) or `<Type>MetadataTest` (builder); new mutation variants extend `MutationWireFormatTest`; no parallel class introduced where an existing one fits.
5. **New wire-format type has a round-trip case** — hand off to [[exeris-sdk-ast-jackson-contract-review]] for the records-only + round-trip detail.
6. **Decision and report** — `APPROVE` / `CONDITIONAL` / `REJECT`.

## Decision Logic
- **APPROVE**: Gates intact; annotations still exempt; new tests extend the canonical class; coverage meets thresholds.
- **CONDITIONAL**: Coverage met but a new parallel test class duplicates an existing one — propose the merge target.
- **REJECT**: Committed threshold override; gate added to annotations module; new uncovered AST branch with no test; new mutation variant without `MutationWireFormatTest` case.

## Completion Criteria
- Gate-intact check, annotations-exempt check, Vitest check, test-placement check done.
- Verdict and remediation recorded.

## Review Output Template
1. **Scope analysed** (production + test files touched)
2. **JaCoCo gate** (≥0.8.14, 0.85 intact / lowered)
3. **Annotations exemption** (preserved / gate leaked in)
4. **Vitest gate** (85% per-file intact / weakened)
5. **Test placement** (extends canonical class / parallel class introduced)
6. **Wire-format coverage** (round-trip case present / hand off)
7. **Verdict** (`APPROVE` / `CONDITIONAL` / `REJECT`)
8. **Required actions** (precise and minimal)

## Non-Negotiable Rules
- Never approve a committed lowering of the 0.85 JaCoCo thresholds.
- Never approve a JaCoCo gate added to `exeris-sdk-annotations`.
- Never approve a new AST branch / mutation variant without a case in the canonical test class.
