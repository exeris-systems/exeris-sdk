---
title: "exeris-sdk: developer-facing API surface of the Exeris platform"
type: reference
visibility: public
owning-repo: exeris-sdk
status: active
last-verified: 2026-09-07
---

# exeris-sdk

Guardrails for AI assistants working inside this repository — the contract a session must respect,
and an index to where each rule lives. Human onboarding is [`README.md`](README.md) and
[`CONTRIBUTING.md`](CONTRIBUTING.md).

## Mission and scope

`exeris-sdk` is the **developer-facing API surface** of the Exeris platform: pure annotations + a
canonical AST + an Angular/Tailwind UI kit. It is the most upstream Exeris repository: nothing here
depends on the kernel, tooling, or platform. Downstream consumers (`exeris-tooling`,
`exeris-platform`, `budgetHQ`) depend on it.

The repository serves the **Entity-First** paradigm ([ADR-003](docs/adr/ADR-003-entity-first-development-strategy.md)):
the Java domain class annotated with `@ExerisDomain` is the **single source of truth**; SQL,
endpoints, DTOs, and UI views are generated artifacts.

Coordinates: groupId `eu.exeris`, packages `eu.exeris.sdk.*`.

## Operating contract

**Non-negotiable invariants, whatever the task:**

- **Zero runtime coupling:** Annotations are `@Retention(SOURCE)` with `@Target`. The
  `annotations` module has no compile-time dependencies. `source-model` pulls only
  `jackson-annotations` ([policy](.agents/policies/zero-runtime-coupling.md)).
- **AST records are wire format:** AST types MUST be Java records, never classes. Jackson 3
  consumers set `FAIL_ON_NULL_FOR_PRIMITIVES=false`. Boxed numeric zeroes require per-component
  `@JsonInclude(NON_NULL)`. `jackson-annotations` stays on 2.22 ([policy](.agents/policies/ast-wire-format.md)).
- **Field vs Validation scoping:** `@Field` owns lifecycle only (`required`, `inCreate`,
  `inUpdate`). `@Validation` is the sole declaration site for constraints (`min`, `max`, etc.).
  `FieldMetadata` is the single AST carrier ([policy](.agents/policies/field-validation-scoping.md)).
- **Route access:** `@RouteAccess(PUBLIC | AUTHENTICATED)` is the sole declaration for unauthenticated
  routes. Never overload empty roles/permissions. Never add `UNSPECIFIED` ([policy](.agents/policies/route-access.md)).
- **JDK baseline:** `maven.compiler.release=25` across reactor (ADR-069, tracking kernel GA LTS).
  Class-file major ≤ 69 guarded by `ClassFileBaselineTest` ([policy](.agents/policies/jdk-baseline.md)).
- **UI kit is npm-only:** Standalone npm package (`@exeris-systems/ui-kit`), excluded from Maven reactor.
  Dark mode declared in both `tailwind.preset.js` and `theme.css`. Versions independently ([policy](.agents/policies/ui-kit.md)).
- **Distribution:** Maven Central via Sonatype Central Portal (NOT GitHub Packages). Build-time
  tools excluded via `maven.deploy.skip` and `<excludeArtifacts>` ([reference](.agents/references/maven-central-distribution.md)).
- **Stability & deprecation:** 0.x line is unstable; 1.0.0 freezes public API. Deprecations require
  `@Deprecated(forRemoval = true)`, javadoc replacement pointer, fallback window ≥ 1 minor,
  and `MIGRATION.md` entry ([policy](.agents/policies/stability-and-deprecation.md)).
- **Javadoc completeness:** Publish gate to Maven Central. `failOnWarnings=true` on 6 modules;
  `source-model` uses `JavadocCompletenessTest`, which exempts a builder setter only when its body
  is the bare assignment — one that copies, normalises, appends or renames is documented
  ([policy](.agents/policies/javadoc-and-contract-emitters.md)).
- **TSDoc + API golden:** `@exeris-systems/ui-kit` is a gated TypeScript surface under ADR-085
  §F.21a–c. Every export carries a release tag and a doc comment, and `api/ui-kit.api.md` is
  committed — CI fails on drift, so a removed name is a major for that package's own version
  ([policy](.agents/policies/ui-kit.md)).

## Architecture and documentation entry points

1. [`docs/adr/`](docs/adr/) for architectural decisions ([ADR-003](docs/adr/ADR-003-entity-first-development-strategy.md),
   [ADR-024](docs/adr/ADR-024.link.md), [ADR-037](docs/adr/ADR-037-source-model-io-module.md),
   [ADR-054](docs/adr/ADR-054-field-validation-min-max-pattern-cut.md),
   [ADR-069](docs/adr/ADR-069-jdk-baseline-lts.md), [ADR-072](docs/adr/ADR-072-kernel-preview-spi-reserved-surface.md),
   [ADR-085](docs/adr/ADR-085.link.md)).
2. [`MIGRATION.md`](MIGRATION.md) and [`ROADMAP.md`](ROADMAP.md) for deprecation diffs and milestone scope.
3. Package-info files for scoping rationale: `annotations/.../package-info.java`
   and `source-model/.../package-info.java`.
4. Downstream references: `exeris-tooling/exeris-processor/`, `exeris-platform/exeris-platform-lsp/`,
   `budgetHQ/backend/` ([reference](.agents/references/downstream-consumers.md)).

## `.agents/` — the canonical semantic source

Detailed rules are authored once under [`.agents/`](.agents) and nowhere else.

| Path | What it holds |
|:--|:--|
| [`.agents/policies/`](.agents/policies) | Non-negotiable boundaries — zero runtime coupling, AST wire format, field/validation scoping, route access, JDK baseline, UI kit, stability & deprecation, javadoc & emitters. Each is linked from the invariant it governs above. |
| [`.agents/references/`](.agents/references) | Authoritative summaries — [build & testing](.agents/references/build-and-testing.md), downstream consumers, Maven Central distribution. |
| [`.agents/skills/`](.agents/skills) | Bounded review capabilities, one per invariant this repository defends, plus triage and routing. |
| [`.agents/agents/`](.agents/agents) | Role profiles: router, architect, implementer, verification, docs-adr. |
| [`.agents/workflows/`](.agents/workflows) | Repeatable sequences. **`sdk-pr-review`** is the routine the CI review runs; the other four are audits. |
| [`.agents/schemas/`](.agents/schemas), [`evals/`](.agents/evals) | The decision contract: `verdict` and `triage-result` narrow the bundle's bases, and ten cases pin the two roles that emit them. |
| [`.agents/manifest.yaml`](.agents/manifest.yaml) | Composition metadata, schema v2. Pins the `exeris-agents` bundle vendored under `.agents/vendor/`. |

Instruction sources resolve broad to narrow: organisation bundle → repository → subtree → workflow.
A narrower file may restrict behaviour; it may never relax a higher-order rule.

## Verification and reporting

- `mvn clean install` runs the full reactor and JaCoCo 0.85 BUNDLE instruction + line gate on `source-model`.
- `AnnotationContractTest` verifies `@Retention(SOURCE)` and `@Target` across all annotations.
- `AstJsonRoundTripTest` and `MutationWireFormatTest` enforce Jackson 3 serialization.
- `ClassFileBaselineTest` enforces class-file major ≤ 69 (JDK 25 LTS).
- UI kit: `npm run test:coverage` (85% per-file gate) and derived drift tests.
- Javadoc gates: `failOnWarnings=true` across 6 modules, `JavadocCompletenessTest` on `source-model`.

Report outcomes first. A claim names the command that proves it, verified against the effective source.

## Conventions and contribution terms

Binding standards live in [`exeris-docs/standards/`](https://github.com/exeris-systems/exeris-docs/tree/main/standards):
commit conventions, PR conventions, javadoc conventions, docs style guide, ADR conventions, the
[agent-file schema](https://github.com/exeris-systems/exeris-docs/blob/main/standards/agents-md-schema.md),
and [AI provenance](https://github.com/exeris-systems/exeris-docs/blob/main/standards/ai-provenance.md).

An AI-assisted commit keeps its `Co-authored-by:` trailer, a named human is accountable for every
line, and an agent does not open pull requests or file issues unattended. Contribution terms:
[`CONTRIBUTING.md`](CONTRIBUTING.md).

## Provider adapters

[`.claude/`](.claude) holds Claude Code adapters generated from `.agents/` — profiles and
workflows carry a do-not-edit marker naming their source, skills are symlinks to it. One shared
renderer produces them (`exeris-systems/exeris-agents`, pinned in `manifest.yaml`); this
repository carries none of its own. Never edit an adapter directly. [`CLAUDE.md`](CLAUDE.md)
points here.
