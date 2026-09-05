---
# DO NOT EDIT — generated from .agents/skills/exeris-sdk-deprecation-pipeline-review/SKILL.md (agents-md-schema.md rule 7). Edit the source.
name: exeris-sdk-deprecation-pipeline-review
description: Deprecation pipeline review for exeris-sdk. Use whenever public API (annotation, AST record, attribute, public method) is removed, renamed, or moved between canonical annotations (e.g. `@Validation.X` → `@Field.X`).
---
<!-- DO NOT EDIT. Generated from .agents/skills/exeris-sdk-deprecation-pipeline-review/SKILL.md by the AGENTS.md adapter step
     (agents-md-schema.md rule 7). Edit the source, not this file. -->
# Exeris SDK Deprecation Pipeline Review

## Purpose
Enforce: removing or renaming public API requires `@Deprecated(forRemoval = true)` + canonical replacement in javadoc + processor fallback-with-warning for ≥ 1 minor release + `MIGRATION.md` entry. Downstream consumers pin exact 0.x versions; the deprecation window is what gives them a safe upgrade path.

## When to Use
- Any change or PR removing a public `@interface` from `exeris-sdk-annotations`.
- Any change or PR removing / renaming an attribute on a public annotation.
- Any change or PR removing / renaming a public AST record or component.
- Any change or PR removing / renaming a public method on an AST record.
- Any change or PR moving an attribute from one canonical location to another (e.g. `@Validation.X` → `@Field.X`).

## Required Inputs
- PR diff scoped to public-API surface.
- Stated motivation (rename / move / removal / collapse).
- ROADMAP milestone context.

## Pipeline Rules (per repo `CLAUDE.md` + `MIGRATION.md`)
- `0.x` is unstable but predictable: breaking changes use the deprecation window; downstream consumers pin exact versions.
- `1.0.0` GA freezes the contract; only additive minors and bug-fix patches after that.
- For removal / rename in `0.x`:
  1. Apply `@Deprecated(forRemoval = true)` to the deprecated surface.
  2. Name the canonical replacement in javadoc.
  3. Give the processor (`~/exeris-systems/exeris-tooling/exeris-processor/`) a fallback-with-warning window of ≥ 1 minor release.
  4. Add a `MIGRATION.md` section with code diff + window + removal milestone (typically 1.0.0).
- Removing an already-deprecated surface: confirm the fallback window of ≥ 1 minor release has elapsed.

## Review Procedure
1. **Detect removal / rename** — does the PR remove or rename a public-API surface? If no, skip.
2. **`@Deprecated(forRemoval = true)`** — applied to the deprecated surface? Missing → reject.
3. **Javadoc canonical replacement** — javadoc on the deprecated surface names the replacement? Missing → reject.
4. **Processor fallback-with-warning** — companion PR in `exeris-tooling/exeris-processor/` provides a fallback path with build warning? Missing → cross-repo coordination required.
5. **Fallback window** — at least one minor release between deprecation and removal? Insufficient window → reject.
6. **`MIGRATION.md`** — section added with: (a) before/after diff, (b) deprecation window, (c) removal milestone, (d) rationale (link to package-info / ADR / scoping)? Missing → reject.
7. **`ROADMAP.md`** — removal milestone reflected as `[x]` when shipped, or as scoped item when planned?
8. **Cross-repo impact** — downstream consumers (`exeris-tooling`, `exeris-platform-lsp`) impacted? Flag.
9. **Decision and report** — `APPROVE` / `CONDITIONAL` / `REJECT`.

## Decision Logic
- **APPROVE**: Full pipeline applied; processor fallback in cross-repo PR; MIGRATION entry; window ≥ 1 minor; ROADMAP aligned.
- **CONDITIONAL**: Mostly correct but one specific element missing (e.g. javadoc pointer phrasing weak) — propose the specific addition.
- **REJECT**: Removal without deprecation; missing `@Deprecated(forRemoval = true)`; missing javadoc pointer; missing processor fallback; insufficient window; missing `MIGRATION.md` entry.

## Completion Criteria
- Removal / rename detection done.
- All 7 pipeline steps audited.
- Cross-repo flag raised when applicable.
- Verdict and remediation recorded.

## Review Output Template
1. **Scope analysed** (public-API surface removed / renamed)
2. **`@Deprecated(forRemoval = true)`** (applied / missing)
3. **Javadoc canonical replacement** (present / missing)
4. **Processor fallback-with-warning** (present / cross-repo PR identifier / missing)
5. **Fallback window** (≥ 1 minor / insufficient)
6. **`MIGRATION.md` entry** (diff + window + milestone + rationale / missing)
7. **`ROADMAP.md` alignment**
8. **Cross-repo impact** (none / processor / LSP / both)
9. **Verdict** (`APPROVE` / `CONDITIONAL` / `REJECT`)
10. **Required actions** (precise and minimal)

## Non-Negotiable Rules
- Never approve removal of a public-API surface without the full deprecation pipeline.
- Never approve removal of an already-deprecated surface before the fallback window elapses.
- Never approve a `MIGRATION.md` entry without an actual code diff.
- Never approve removal that lacks a downstream-processor fallback PR identifier.
