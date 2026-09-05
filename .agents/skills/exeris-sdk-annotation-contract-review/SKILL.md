---
name: exeris-sdk-annotation-contract-review
description: Annotation contract review for exeris-sdk. Use whenever you add, change, or remove a public `@interface` in `exeris-sdk-annotations` (any package — root, `system`, `security`, or a newly introduced subpackage) — enforces `@Retention(SOURCE)` + `@Target`, the invariant every downstream consumer depends on.
---

# Exeris SDK Annotation Contract Review

## Purpose
Enforce the invariant the whole annotation surface rests on: every annotation in `exeris-sdk-annotations` is `@Retention(SOURCE)` (zero runtime footprint) and carries `@Target` (so misuse is a compile error downstream). `AnnotationContractTest` discovers every annotation by classpath reflection and asserts both properties across the root package plus the `system` / `security` subpackages — a new annotation is picked up automatically, but the contract must actually hold.

The annotations module deliberately has **no JaCoCo gate** (`@interface` declarations have no method bodies, so coverage is meaningless); `AnnotationContractTest` IS the gate. See [[exeris-sdk-coverage-gates-review]].

## When to Use
- Any change adding a new public `@interface` to `exeris-sdk-annotations`.
- Any change altering `@Retention` / `@Target` on an existing annotation.
- Any change adding a new subpackage of annotations (the test asserts across root + `system` + `security`).
- Any change removing / renaming a public annotation (also triggers [[exeris-sdk-deprecation-pipeline-review]]).

## Required Inputs
- PR diff scoped to `exeris-sdk-annotations`.
- `AnnotationContractTest` state (package coverage list).

## Review Procedure
1. **`@Retention(SOURCE)`** — every new / changed annotation is `@Retention(RetentionPolicy.SOURCE)`. `CLASS` or `RUNTIME` → reject (it would leak a runtime footprint into the zero-coupling repo). See [[exeris-sdk-zero-runtime-coupling-review]].
2. **`@Target` present** — every annotation declares an explicit `@Target`. Missing → reject (downstream misuse should fail at compile time, not silently).
3. **`@Target` correctness** — the declared targets match how the processor consumes the annotation (`TYPE` for `@ExerisDomain`, `FIELD` for `@Field` / `@Validation`, etc.).
4. **Subpackage coverage** — if the change introduces a new annotation subpackage, confirm `AnnotationContractTest`'s package list (or its reflective scan root) reaches it.
5. **No compile-time deps introduced** — a new annotation must not drag a dependency into the module. Hand off to [[exeris-sdk-zero-runtime-coupling-review]] if any import is added.
6. **Decision and report** — `APPROVE` / `CONDITIONAL` / `REJECT`.

## Decision Logic
- **APPROVE**: `@Retention(SOURCE)` + correct `@Target` on every touched annotation; subpackage reachable by the contract test; no new deps.
- **CONDITIONAL**: Sound annotation but a new subpackage may not be in the test's scan root — propose the test update.
- **REJECT**: `@Retention(CLASS|RUNTIME)`; missing `@Target`; new annotation subpackage unreachable by `AnnotationContractTest`; compile-time dep introduced.

## Completion Criteria
- Retention check, target presence + correctness check, subpackage-reach check, dep check done.
- Verdict and remediation recorded.

## Review Output Template
1. **Scope analysed** (annotations added / changed / removed)
2. **`@Retention(SOURCE)`** (all / violation found)
3. **`@Target` present** (all / missing)
4. **`@Target` correctness** (matches processor usage / mismatch)
5. **Subpackage coverage** (reachable by `AnnotationContractTest` / new package unreached)
6. **Compile-time deps** (none / introduced — hand off)
7. **Verdict** (`APPROVE` / `CONDITIONAL` / `REJECT`)
8. **Required actions** (precise and minimal)

## Non-Negotiable Rules
- Never approve an annotation that is not `@Retention(SOURCE)`.
- Never approve a public annotation without an explicit `@Target`.
- Never approve a new annotation subpackage that `AnnotationContractTest` cannot reach.
