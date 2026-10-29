---
name: exeris-sdk-doc-impact-triage
description: Documentation-impact triage for exeris-sdk. Use whenever a change touches a public type, annotation, AST record, `-io` entry point, composition or TCK type, ui-kit export, a module pom, `exeris-sdk-examples`, or a page under `docs/` — during editing, not only at PR review. Decides which pages under `docs/guides`, `docs/modules`, `docs/examples`, `docs/stability-matrix.md`, `docs/glossary.md` and `docs/architecture.md` must change, and at what level.
---

# Exeris SDK Doc Impact Triage

## Purpose
Decide, before merge, whether a change leaves a published page saying something the code no longer
does. Triage only: name the pages and the action, do not write them.

`tools/docs-citations/check.sh` already fails CI when a cited path disappears or a cited symbol no
longer occurs in its file. It cannot see a change in **meaning** — a default that moved, a facet the
reader started carrying, a tier that changed. That is what this skill is for.

## Output Contract
Return exactly one status:
- `NO_DOC_IMPACT` — nothing a page states changed.
- `MINOR_DOC_IMPACT` — a cited name moved, an example was added, or wording drifted; no claim is
  now false.
- `DOCS_REVIEW_REQUIRED` — a claim on at least one page is now false or incomplete.
- `ADR_REVIEW_POSSIBLE` — a boundary moved: the freeze scope, a dependency direction, what the SDK
  never does. Hand to `exeris-sdk-architect` before any page changes.

And return:
1. `why` — the changed behaviour, in one sentence, naming the type or attribute.
2. `affected_docs` — page paths, each with the section that changes, or `None`.
3. `suggested_doc_action` — the edit per page, and whether `last-verified` and a guide's
   verified-against commit move (they move only after the page is re-checked against the code).

## Path map
This table is the one place the path-to-page mapping lives. A changed path selects its rows; the
pages in them are candidates, and a candidate is affected only if it states something the change
made untrue. Always also grep `docs/` for the changed symbol's simple name: a guide citing it is a
candidate whatever this table says.

| Changed path | Candidate pages |
|:--|:--|
| `exeris-sdk-annotations/src/main/**` | `docs/modules/01-annotations.md`; `docs/stability-matrix.md` when a javadoc `Status:` line or a `forRemoval` changes; the guide that walks the annotation (02–05, 08) |
| `exeris-sdk-source-model/src/main/**` (`ast`, `mutation`, `brief`) | `docs/modules/02-source-model.md`; `docs/stability-matrix.md` AST, mutation and brief sections; guides 06 and 07 |
| `exeris-sdk-source-model-io/src/main/**` | `docs/modules/03-source-model-io.md`; every guide that states what the reader carries or reports unmodeled (02–06, 08); the matching `exeris-sdk-examples` test |
| `exeris-sdk-annotation-catalog/**` | `docs/modules/04-annotation-catalog.md`; guide 07 |
| `exeris-sdk-composition-spec/**`, `-lifecycle/**`, `-runtime/**` | `docs/modules/05`–`07`; guide 08 |
| `exeris-sdk-tck/**` | `docs/modules/08-tck.md`; guide 07 |
| `exeris-sdk-ui-kit/**` | `docs/modules/09-ui-kit.md`; guide 09 |
| `exeris-sdk-examples/**` | `docs/examples/README.md`; `docs/modules/10-examples.md`; every guide quoting the changed file |
| root `pom.xml` `<modules>`, a module `pom.xml` dependency | `docs/architecture.md` module graph; `docs/modules/README.md`; guide 01 |
| `MIGRATION-0.x-to-1.0.md` §1–§3, an ADR-072 amendment | `docs/stability-matrix.md` |
| a new term in javadoc or a policy | `docs/glossary.md` |

## Triage Rules
- Tests outside `exeris-sdk-examples`, CI configuration, and a refactor with no observable change →
  `NO_DOC_IMPACT`.
- A renamed cited symbol, a new example scenario, a new annotation attribute nothing documents yet →
  `MINOR_DOC_IMPACT`.
- A changed default, a facet the reader now carries or stops carrying, a javadoc `Status:` change, a
  `forRemoval` added, a new public type or module → `DOCS_REVIEW_REQUIRED`.
- A change to what the 1.0.0 freeze covers, to which module may depend on which, or to zero runtime
  coupling → `ADR_REVIEW_POSSIBLE`.

## Guardrails
- One home per fact: the fix for a page that restates a rule is a link to the rule's owner, not a
  second updated copy (`exeris-docs/standards/docs-style-guide.md` rule 12).
- A page describes current behaviour; it never narrates what changed. History goes to the commit
  and `CHANGELOG.md`.
- Never move `last-verified` or a verified-against commit for a page that was not re-read against
  the code.

## Completion Criteria
Complete only when status, `why`, `affected_docs` and `suggested_doc_action` are all explicit.
