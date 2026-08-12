# `.claude/` — Claude Code workspace for `exeris-sdk`

This directory is loaded automatically when a Claude Code session opens inside
`~/exeris-systems/exeris-sdk/`. It exists alongside the repo-root [`CLAUDE.md`](../CLAUDE.md)
and works as the operating context for AI assistants on the developer-facing
annotations + source-model + UI-kit surface.

> **Workspace convention.** Agents and skills here use absolute
> `~/exeris-systems/<repo>/…` paths for reference-first lookups (e.g. the
> downstream consumers below). This assumes the Exeris repos are cloned as
> siblings under `~/exeris-systems/`. If your layout differs, adjust those
> paths or symlink the workspace — the convention is intentional, not a
> hardcode bug.

## Layout

- `agents/` — sub-agents Claude can launch via the `Agent` tool (or invoke directly):
  - `exeris-sdk-router.md` — entrypoint triage
  - `exeris-sdk-architect.md` — upstream-zero-deps, AST wire-format contract, stability policy, Entity-First (ADR-003), Field/Validation canonical scoping
  - `exeris-sdk-implementer.md` — annotations / AST records / UI kit code changes
  - `exeris-sdk-verification.md` — `AnnotationContractTest`, `AstJsonRoundTripTest`, JaCoCo gate, Vitest gate
  - `exeris-sdk-docs-adr.md` — `MIGRATION.md`, `ROADMAP.md`, package-info sync, ADR-003
- `commands/` — slash commands (`/<command-name>`):
  - `zero-runtime-coupling-check.md`, `ast-jackson-contract.md`, `field-validation-scoping.md`, `deprecation-pipeline.md`, `maven-central-publish-readiness.md`
- `skills/` — invocable skills (`/<skill-name>`):
  - `exeris-sdk-task-classifier`, `exeris-sdk-routing-planner`
  - `exeris-sdk-zero-runtime-coupling-review`, `exeris-sdk-ast-jackson-contract-review`
  - `exeris-sdk-field-validation-scoping-review`, `exeris-sdk-deprecation-pipeline-review`

## Doctrine — single source

Project doctrine is **not** duplicated under `.claude/`:

- **`/CLAUDE.md`** (repo root) — load-bearing facts (most-upstream repo, zero runtime coupling, AST as wire-format), build/test, coverage gates, JDK baseline follows the kernel's GA LTS (25, ADR-069), ui-kit npm-only, **Maven Central** (not GitHub Packages), stability + version policy, Field/Validation canonical scoping, AST + Jackson 3 wire-format contract, ADR-003 Entity-First.
- **`MIGRATION.md`** — version-to-version deprecation pipeline and concrete diffs.
- **`ROADMAP.md`** — milestone scope (0.1.0 scaffold → 0.2.x quality gates → 0.3.x parser/writer → 0.4.x `@Capability` → 0.5.x mutation surface → 0.6–0.9.x cleanups → 1.0.0 GA = contract surface frozen).
- **`docs/adr/ADR-003 Entity-First Development Strategy.md`** — architectural premise.
- **Package-info files** — canonical-scoping rationale:
  - `exeris-sdk-annotations/src/main/java/eu/exeris/sdk/annotation/package-info.java`
  - `exeris-sdk-source-model/src/main/java/eu/exeris/sdk/sourcemodel/ast/package-info.java`
- **Downstream consumers** (one-way dependency): `~/exeris-systems/exeris-tooling/exeris-processor/`, `~/exeris-systems/exeris-tooling/exeris-codegen-*/`, `~/exeris-systems/exeris-platform/exeris-platform-lsp/`. SDK never imports from them.

When skills/agents need policy context, they reference these — they do not restate them.
