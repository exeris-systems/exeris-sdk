---
name: exeris-sdk-task-classifier
description: Router/Planner triage skill for exeris-sdk. Classifies task type (annotation contract / AST wire-format / UI kit / stability / docs / publish-readiness), scope, severity, and recommends primary agent.
---

# Exeris SDK Task Classifier

## Purpose
Classify incoming work before execution. Triage only — no implementation.

## Output Contract
1. `task_class` (`ANNOTATION_CONTRACT` | `AST_WIRE_FORMAT` | `UI_KIT` | `STABILITY_DEPRECATION` | `DOCS_ADR` | `PUBLISH_READINESS` | `MULTI_DOMAIN`)
2. `scope` (single-module | cross-module | npm-only | cross-repo downstream impact)
3. `severity` (low | medium | high | critical)
4. `primary_risk`
5. `recommended_primary_agent`

## Classification Heuristics
- `ANNOTATION_CONTRACT`: `@interface` add / change / remove in `exeris-sdk-annotations`; `AnnotationContractTest` impact.
- `AST_WIRE_FORMAT`: AST record add / change / remove in `exeris-sdk-source-model`; `AstJsonRoundTripTest` impact; Jackson 3 quirks.
- `UI_KIT`: TS / CSS / Tailwind preset change in `exeris-sdk-ui-kit`.
- `STABILITY_DEPRECATION`: public-API removal / rename; deprecation pipeline application.
- `DOCS_ADR`: `MIGRATION.md`, `ROADMAP.md`, package-info sync, ADR-003.
- `PUBLISH_READINESS`: Sonatype Central Portal, POM metadata, sources/javadoc attachments, version bump.
- `MULTI_DOMAIN`: ≥2 first-order concerns.

## Guardrails
- Preserve zero runtime coupling.
- Preserve AST records-only rule.
- Preserve `jackson-annotations` 2.21 pin.
- Preserve JDK 26 floor.
- Preserve Field/Validation canonical scoping.
- Preserve deprecation pipeline.
- If uncertain, emit `MULTI_DOMAIN` and state both.

## Completion Criteria
All five output fields present and justified in 1-2 bullets.
