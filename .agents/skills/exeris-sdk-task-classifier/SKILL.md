---
name: exeris-sdk-task-classifier
description: Triage skill for exeris-sdk — use FIRST on any non-trivial SDK task, before editing annotations, AST records, UI kit, poms, or docs. Classifies task type (annotation contract / AST wire-format / UI kit / stability / docs / publish-readiness), scope, and severity, and routes to the right specialist agent.
---

# Exeris SDK Task Classifier

## Purpose
Classify incoming work before execution. Triage only — no implementation.

## When to Use
- At the START of any non-trivial exeris-sdk task, before any edit.
- Whenever a request could plausibly touch more than one module or concern.
- Skip for a single-file, already-scoped, one-line change.

## Output Contract

The field names are [`.agents/schemas/triage-result.schema.json`](../../schemas/triage-result.schema.json)'s,
because `exeris-sdk-router` emits that schema and this skill is how it decides what to put in it. A
name that differs here is a name the router cannot use.

1. `task_class` (`ANNOTATION_CONTRACT` | `AST_WIRE_FORMAT` | `UI_KIT` | `STABILITY_DEPRECATION` | `DOCS_ADR` | `PUBLISH_READINESS` | `BUILD_INVARIANTS` | `MULTI_DOMAIN`)
2. `primary_risk` — one sentence: what goes wrong if this is routed badly, not a restatement of the task
3. `primary_agent` — the role that runs
4. `validation_gates` — objects, not strings: no gate is named without the risk it retires
5. `next_action` — the single best immediate move

`secondary_handoffs` and `execution_plan` are optional and belong to the routing planner. Scope and
severity are judgement this skill applies rather than fields it emits: they decide `task_class` and
`primary_agent`, and a consumer that needs them reads the risk sentence.

## Classification Heuristics
- `ANNOTATION_CONTRACT`: `@interface` add / change / remove in `exeris-sdk-annotations`; `AnnotationContractTest` impact.
- `AST_WIRE_FORMAT`: AST record add / change / remove in `exeris-sdk-source-model`; `AstJsonRoundTripTest` impact; Jackson 3 quirks.
- `UI_KIT`: TS / CSS / Tailwind preset change in `exeris-sdk-ui-kit`.
- `STABILITY_DEPRECATION`: public-API removal / rename; deprecation pipeline application.
- `DOCS_ADR`: `MIGRATION.md`, `ROADMAP.md`, package-info sync, ADR-003.
- `PUBLISH_READINESS`: Sonatype Central Portal, POM metadata, sources/javadoc attachments, version bump.
- `BUILD_INVARIANTS`: `maven.compiler.release` (JDK baseline, ADR-069), `jacoco-maven-plugin` version, reactor `<modules>`, root/parent build-plugin config.
- `MULTI_DOMAIN`: ≥2 first-order concerns.

## Review Skill per Class
After classifying, fire the matching enforcement skill (these are the canonical guardrails):
- `ANNOTATION_CONTRACT` → `exeris-sdk-annotation-contract-review` (+ `exeris-sdk-zero-runtime-coupling-review` if imports/deps change).
- `AST_WIRE_FORMAT` → `exeris-sdk-ast-jackson-contract-review` (+ `exeris-sdk-coverage-gates-review` for test placement).
- `STABILITY_DEPRECATION` → `exeris-sdk-deprecation-pipeline-review`; if `@Field`/`@Validation` scoped → also `exeris-sdk-field-validation-scoping-review`.
- `UI_KIT` → `exeris-sdk-coverage-gates-review`.
- `PUBLISH_READINESS` → `exeris-sdk-publish-readiness-review`.
- `BUILD_INVARIANTS` → `exeris-sdk-build-invariants-review`.
- `DOCS_ADR` → `exeris-sdk-docs-adr` agent (no enforcement skill — pure doc/ADR work).
- Any new dep / import (any class) → `exeris-sdk-zero-runtime-coupling-review`.

## Guardrails
- Preserve zero runtime coupling.
- Preserve AST records-only rule.
- Preserve `jackson-annotations` 2.22 pin.
- Preserve the JDK baseline (`maven.compiler.release=25`, ADR-069); never move it in either direction to fix a build failure — it tracks the kernel's GA line.
- Preserve `jacoco-maven-plugin` ≥ 0.8.14 + coverage gates (85% source-model BUNDLE / ui-kit per-file).
- Preserve ui-kit npm-only (excluded from the Maven reactor).
- Preserve Field/Validation canonical scoping.
- Preserve deprecation pipeline.
- If uncertain, emit `MULTI_DOMAIN` and state both.

## Completion Criteria
All five output fields present and justified in 1-2 bullets.
