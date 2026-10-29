---
name: docs-drift-audit
description: Audit whether the published docs still match the code — the guides, module pages, examples index, stability matrix, glossary and architecture page — for a change or for the whole tree. Ends in a page-by-page patch list.
disable-model-invocation: true
---

<!-- DO NOT EDIT. Generated from .agents/workflows/docs-drift-audit.md by agents_render.py
     (exeris-systems/exeris-agents; agents-md-schema.md rule 7). Edit the source. -->
Audit the documentation against the code for the scope below.

Scope:
$ARGUMENTS

If the scope is `all`, treat every page under `docs/guides`, `docs/modules`, `docs/examples`, and
`docs/stability-matrix.md`, `docs/glossary.md` and `docs/architecture.md` as candidates.

Steps:

1. **Triage.** Classify the change with `exeris-sdk-task-classifier`, then run
   `exeris-sdk-doc-impact-triage`. Its path map selects the candidate pages; its status decides
   whether anything below runs.
2. **Mechanical check.** Run `bash tools/docs-citations/test.sh` and then
   `bash tools/docs-citations/check.sh`, and report both results. A `MISSING` or `STALE` line is a
   finding on that page.
3. **Meaning check** (`exeris-sdk-verification`). For each affected page, re-read every claim the
   change could touch against the code: what a type does, which facet the reader carries, a default,
   a tier. For a quoted snippet, confirm it still matches its cited symbol. For a claim backed by an
   `exeris-sdk-examples` test, run `mvn -pl exeris-sdk-examples -am verify`.
4. **Boundary** (`exeris-sdk-architect`, only for `ADR_REVIEW_POSSIBLE`). Decide whether the
   change needs an ADR or a MIGRATION entry before any page moves.

Please produce:
- Triage status, with `why`
- Citation check results (test.sh, check.sh)
- Per affected page: the false or missing claim, the source that shows it, and the edit
- Whether each edited page's `last-verified` and verified-against commit move
- Final verdict: IN SYNC / DRIFT (with the patch list)
