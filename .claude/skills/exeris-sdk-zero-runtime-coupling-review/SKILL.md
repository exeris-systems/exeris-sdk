---
# DO NOT EDIT — generated from .agents/skills/exeris-sdk-zero-runtime-coupling-review/SKILL.md (agents-md-schema.md rule 7). Edit the source.
name: exeris-sdk-zero-runtime-coupling-review
description: Zero runtime coupling review for exeris-sdk. Use whenever you add a dependency to any module pom (annotations / source-model / bom / parent) or add a new import in any Java/TS source file — catches kernel / tooling / platform / framework leakage into the upstream-most repo.
---
<!-- DO NOT EDIT. Generated from .agents/skills/exeris-sdk-zero-runtime-coupling-review/SKILL.md by the AGENTS.md adapter step
     (agents-md-schema.md rule 7). Edit the source, not this file. -->
# Exeris SDK Zero Runtime Coupling Review

## Purpose
Enforce: this is the most upstream Exeris repo. Nothing here depends on kernel, tooling, or platform — even transitively. Annotations are `@Retention(SOURCE)` and the annotations module has no compile-time deps; the source-model module only pulls `jackson-annotations`.

## When to Use
- Any change or PR adding / removing / changing deps in `exeris-sdk-annotations/pom.xml`.
- Any change or PR adding / removing / changing deps in `exeris-sdk-source-model/pom.xml`.
- Any change or PR touching `exeris-sdk-bom/pom.xml` or `exeris-sdk-parent/pom.xml`.
- Any change or PR adding new imports in any source file (Java or TS).

## Required Inputs
- PR diff.
- Stated change scope.

## Review Procedure
1. **Annotations module classpath** — `exeris-sdk-annotations/pom.xml` has NO compile-scope deps. New compile dep → hard reject.
2. **Source-model module classpath** — `exeris-sdk-source-model/pom.xml` only pulls `jackson-annotations`. New compile dep beyond that → justify or reject.
3. **Import audit** — no new import statement references:
   - `eu.exeris.kernel.*` (downstream Tier 1 substrate) → hard reject.
   - `eu.exeris.tooling.*` / `eu.exeris.processor.*` / `eu.exeris.codegen.*` (downstream tooling) → hard reject.
   - `eu.exeris.platform.*` (downstream platform) → hard reject.
   - `org.springframework.*` / `jakarta.servlet.*` / `io.netty.*` / `io.projectreactor.*` (framework / runtime) → hard reject.
4. **BOM elevation audit** — `exeris-sdk-bom` MUST NOT manage kernel / framework versions; if such a row appears, hard reject (would imply downstream consumers see this dep as managed by us).
5. **Transitive audit** — new third-party deps need transitive-deps inspection; flag pulls of kernel / framework / runtime classes.
6. **UI kit** — `package.json` deps don't pull React / Angular framework runtime into the published `@exeris/ui-kit` (Tailwind preset + CSS + minimal TS).
7. **Decision and report** — `APPROVE` / `CONDITIONAL` / `REJECT`.

## Decision Logic
- **APPROVE**: No new compile-scope dep on annotations module; source-model deps remain bounded; no kernel/tooling/platform/framework imports; BOM clean; transitive clean.
- **CONDITIONAL**: New third-party dep needs transitive-deps justification — propose the specific exclusion rules.
- **REJECT**: Compile dep added to annotations module; kernel / tooling / platform import; framework runtime import; BOM elevating downstream version.

## Completion Criteria
- Annotations classpath audited.
- Source-model classpath audited.
- Import audit done.
- BOM audit done.
- Transitive audit done.
- Verdict and remediation recorded.

## Review Output Template
1. **Scope analysed** (poms / source files touched)
2. **Annotations classpath** (clean / new dep — reject)
3. **Source-model classpath** (only `jackson-annotations` / extended — justification)
4. **Import audit** (clean / forbidden imports found)
5. **BOM audit** (clean / elevated downstream version)
6. **Transitive audit** (clean / suspicious pull)
7. **UI kit** (clean / framework runtime added)
8. **Verdict** (`APPROVE` / `CONDITIONAL` / `REJECT`)
9. **Required actions** (precise and minimal)

## Non-Negotiable Rules
- Never approve compile-scope dep on `exeris-sdk-annotations`.
- Never approve import from `eu.exeris.kernel.*` / `eu.exeris.tooling.*` / `eu.exeris.platform.*`.
- Never approve framework runtime import (`org.springframework.*`, `jakarta.servlet.*`, etc.).
- Never approve BOM elevating a kernel / framework version.
