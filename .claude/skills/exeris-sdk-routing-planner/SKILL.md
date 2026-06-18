---
name: exeris-sdk-routing-planner
description: Execution-planning skill for exeris-sdk — use after exeris-sdk-task-classifier, or whenever an SDK task spans multiple modules/agents. Produces the primary agent, ordered secondary handoffs, a 3–5 step execution plan, validation gates, and the minimal next action.
---

# Exeris SDK Routing Planner

## Purpose
Given a classified task (see `exeris-sdk-task-classifier`), produce a minimal, risk-aware execution order across `exeris-sdk-{router,architect,implementer,verification,docs-adr}`.

## When to Use
- Right after `exeris-sdk-task-classifier`, once the task class is known.
- Whenever a task is `MULTI_DOMAIN` or needs ≥2 agent handoffs.
- Skip when one specialist clearly owns the whole change end-to-end.

## Output Contract
1. `primary_agent`
2. `secondary_handoffs` (ordered list with reason)
3. `execution_plan` (3–5 steps)
4. `validation_gates` (must-pass list)
5. `minimal_next_action`

## Routing Patterns
- `ANNOTATION_CONTRACT` → `exeris-sdk-implementer` primary; `verification` mandatory (`AnnotationContractTest`); `docs-adr` if scoping rationale changed; `architect` if zero-coupling at risk.
- `AST_WIRE_FORMAT` → `exeris-sdk-implementer` primary; `verification` mandatory (`AstJsonRoundTripTest`); `architect` for records-only + Jackson 3 contract; `docs-adr` for downstream-consumer impact in `MIGRATION.md`.
- `UI_KIT` → `exeris-sdk-implementer` primary; `verification` for Vitest 85% per-file gate.
- `STABILITY_DEPRECATION` → `exeris-sdk-architect` primary (deprecation pipeline policy); `docs-adr` mandatory (`MIGRATION.md` entry); `implementer` for code.
- `DOCS_ADR` → `exeris-sdk-docs-adr` primary; `architect` if scoping rationale shifts.
- `PUBLISH_READINESS` → `exeris-sdk-architect` primary (run `exeris-sdk-publish-readiness-review`); `implementer` for pom changes; `verification` for full reactor build green.
- `BUILD_INVARIANTS` → `exeris-sdk-architect` primary (run `exeris-sdk-build-invariants-review`); `implementer` for the pom/plugin change; `docs-adr` if the JDK-26 rationale in `README.md`/`ROADMAP.md` shifts.
- `MULTI_DOMAIN` → start with `architect`, list all dominant handoffs.

## Default Validation Gates
- `AnnotationContractTest` green (annotations module); new `@interface` is `@Retention(SOURCE)` + `@Target` (`exeris-sdk-annotation-contract-review`).
- `AstJsonRoundTripTest` green; new AST records have new test cases (source-model module).
- JaCoCo 85% instruction + line gate on `exeris-sdk-source-model` (BUNDLE-level).
- Vitest 85% per-file gate on `exeris-sdk-ui-kit` (lines / statements / functions / branches).
- Zero runtime coupling: no new compile-scope dep in annotations module; no kernel/tooling/platform imports anywhere.
- Deprecation pipeline applied when public API removed / renamed.
- `MIGRATION.md` entry when user-visible.
- Package-info sync (both locations) when scoping touched.
- Maven Central POM metadata complete when publishable module changed (`exeris-sdk-publish-readiness-review`).
- JDK 26 floor held; `jacoco-maven-plugin` ≥ 0.8.14; ui-kit out of reactor when build/pom touched (`exeris-sdk-build-invariants-review`).
- New tests extend the canonical class (`AstJsonRoundTripTest` / `<Type>MetadataTest` / `MutationWireFormatTest`), not a parallel one (`exeris-sdk-coverage-gates-review`).

## Completion Criteria
All five contract fields present and gates tied to the specific risk surface.
