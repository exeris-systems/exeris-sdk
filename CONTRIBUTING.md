---
title: "Contributing to Exeris SDK"
type: reference
visibility: public
owning-repo: exeris-sdk
status: active
last-verified: 2026-09-05
---

# Contributing to Exeris SDK

This document describes how to build, test, and contribute to `exeris-sdk`, the upstream developer-facing
API surface of the Exeris platform.

## Build & test

```bash
mvn clean install                                # full reactor build + JaCoCo 85% gate
mvn -pl exeris-sdk-source-model-io -am verify    # one module + dependencies

cd exeris-sdk-ui-kit && npm ci && npm run test:coverage   # ui-kit (npm-only, 85% per-file gate)
```

**JDK 25 LTS is the baseline** across the reactor ([ADR-069](docs/adr/ADR-069-jdk-baseline-lts.md)).
Never lower or raise `maven.compiler.release` without an ecosystem-wide decision. Emitted class-file
bytecode is verified by `ClassFileBaselineTest` (major ≤ 69).

## Dependencies & licensing

`exeris-sdk` is published under **Apache-2.0** to Maven Central. Two non-negotiable rules maintain
architectural purity:

1. **Zero runtime coupling:** `exeris-sdk-annotations` has no compile/runtime dependencies;
   `exeris-sdk-source-model` pulls only `jackson-annotations`. Heavy dependencies live in leaf modules
   only — in particular, JavaParser is strictly confined to `exeris-sdk-source-model-io` ([ADR-037](docs/adr/ADR-037-source-model-io-module.md)).
2. **JavaParser license election:** `com.github.javaparser:javaparser-core` is dual-licensed
   (Apache-2.0 OR LGPL-3.0). The SDK **elects Apache-2.0**. Keep the election comment in `exeris-sdk-bom`.

## Contributor terms & DCO sign-off

External contributions require a Developer Certificate of Origin sign-off via the `Signed-off-by:`
trailer (`git commit -s`), per [ADR-085 §K](docs/adr/ADR-085.link.md).

The sign-off certifies that you have the right to submit the contribution under the Apache-2.0 licence
published in `LICENSE`. Organisation members are exempt from the trailer, but remain accountable for
every merged change.

## AI provenance

Exeris is developed with AI assistance and states the terms openly per
[`ai-provenance.md`](https://github.com/exeris-systems/exeris-docs/blob/main/standards/ai-provenance.md):

- **Provenance is kept:** An AI-assisted commit carries `Co-authored-by: <model name> <noreply@anthropic.com>`
  (or equivalent). Stripping it is a defect; adding it where no AI was involved is prohibited.
- **A named human is accountable for every line:** The PR author must be able to explain and defend any part
  of the change in review. "The agent produced it" is never an acceptable explanation.
- **Agents do not open pull requests, file issues or post comments unattended:** Automated review comments
  are allowed; automated contributions without human review are not.
- **Verification is stated, not assumed:** PR descriptions must name the exact test commands executed.
- **Reject hollow tests:** AI-generated tests that assert nothing observable or only restate mocks are rejected.

## Conventions

Development standards are binding per [ADR-085](docs/adr/ADR-085.link.md) and hosted in
[`exeris-docs/standards/`](https://github.com/exeris-systems/exeris-docs/tree/main/standards):

- [`commit-conventions.md`](https://github.com/exeris-systems/exeris-docs/blob/main/standards/commit-conventions.md) — Conventional Commits with Netty-form body (`Motivation:`, `Modification:`, `Result:`).
- [`pr-conventions.md`](https://github.com/exeris-systems/exeris-docs/blob/main/standards/pr-conventions.md) — structured PR bodies and scope classification.
- [`javadoc-conventions.md`](https://github.com/exeris-systems/exeris-docs/blob/main/standards/javadoc-conventions.md) — Oracle doc-comment standards; `failOnWarnings=true` across 6 modules.
- [`docs-style-guide.md`](https://github.com/exeris-systems/exeris-docs/blob/main/standards/docs-style-guide.md) — validated frontmatter and naming conventions.
- [`agents-md-schema.md`](https://github.com/exeris-systems/exeris-docs/blob/main/standards/agents-md-schema.md) — [`AGENTS.md`](AGENTS.md) as the canonical entry point and [`.agents/`](.agents) as the semantic source.
- Language: English everywhere (code, identifiers, comments, commit messages, PR titles/bodies, documentation).
- AST records are wire-format contracts; changes must be verified with `AstJsonRoundTripTest`.
- Public-API removals/renames must follow the deprecation pipeline in [`MIGRATION.md`](MIGRATION.md).
