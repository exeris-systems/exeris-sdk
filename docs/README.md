---
title: Documentation index
type: reference
visibility: public
owning-repo: exeris-sdk
status: active
last-verified: 2026-10-29
---

# Documentation index

Find the page that owns an answer. Each fact has one home; the other pages link to it.

| Question | Page |
|---|---|
| How do the modules fit together, and what does the SDK never do? | [architecture.md](architecture.md) |
| What does a term such as baseline, facet or SKU mean? | [glossary.md](glossary.md) |
| Which annotation or record is frozen for 1.0, reserved, or deprecated? | [stability-matrix.md](stability-matrix.md) |
| Why was a decision made? | The records in `docs/adr/`, for example [ADR-003](adr/ADR-003-entity-first-development-strategy.md) |
| Which options were weighed before a decision? | The records in `docs/rfc/`, for example [RFC-2026-06-03-source-model-parser-writer.md](rfc/RFC-2026-06-03-source-model-parser-writer.md) |
| What does review check in this repository? | [repo-review-rules.md](repo-review-rules.md) |
| How do I move between releases? | [MIGRATION.md](../MIGRATION.md) |
| What is removed at 1.0.0, and what is deliberately kept? | [MIGRATION-0.x-to-1.0.md](../MIGRATION-0.x-to-1.0.md) |
| What is planned and in what order? | [ROADMAP.md](../ROADMAP.md) |
| What changed in each release? | [CHANGELOG.md](../CHANGELOG.md) |
| How do I build, test and contribute? | [CONTRIBUTING.md](../CONTRIBUTING.md) |
| What contract do agents working here follow? | `AGENTS.md` at the repository root |

## Maintaining these docs

The rules for these pages, including frontmatter, link form, one home per fact and when to bump `last-verified`, are owned by the [documentation style guide](https://github.com/exeris-systems/exeris-docs/blob/main/standards/docs-style-guide.md).

## Where this does not apply

This index covers documentation in this repository. Ecosystem-wide ADRs and the other repositories' pages live in [exeris-docs](https://github.com/exeris-systems/exeris-docs/blob/main/README.md) and in each repository.
