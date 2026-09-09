---
name: sdk-pr-review
description: Full pull-request review for exeris-sdk — scope, the contract invariants, public-API stability, test reach, publish readiness, ending in a verdict. This is the routine the CI review action runs; run it locally before opening a pull request to get the same answer earlier.
disable-model-invocation: true
---

<!-- DO NOT EDIT. Generated from .agents/workflows/sdk-pr-review.md by agents_render.py
     (exeris-systems/exeris-agents; agents-md-schema.md rule 7). Edit the source. -->
Review the change below and return one verdict.

Change: $ARGUMENTS

If that is empty — the routine was invoked without one — review the working branch's diff against
`main`, and say which you reviewed.

Read `AGENTS.md` first — it is the entry point, and the rules live under `.agents/`. Read the
policies your diff actually touches rather than working from memory; each is the single owner of
its list, and a summary repeated here would go stale the next time an entry is added.

What makes this repository's review different from a normal one is the blast radius: it ships
`@Retention(SOURCE)` annotations and a canonical AST that `exeris-tooling` and `exeris-platform`
compile against, so a contract broken here breaks someone else's build rather than this one's
runtime. Nothing fails locally.

Steps:

1. **Triage.** Classify the change with the `exeris-sdk-task-classifier` skill. The class decides
   which specialist passes run at all; a docs-only change does not need the wire-format pass.

2. **Record what the mechanical checks said**, before reading the diff for meaning. Read the
   pull request's own check runs and report each by the name and conclusion it reports — all of
   them, including the ones this repository did not write (CodeQL, Snyk, SonarCloud). Do not work
   from a list kept here: a hand-maintained copy of the job set is a second source of truth for
   which gates exist, and it is wrong the first time a job is added. A check that has not finished,
   or that you could not read, is `not-run`; it is never silence, and a verdict resting on one must
   say so.

3. **Specialist passes**, as the triage class requires. Each owns its own list and none restates
   another's:

   - `exeris-sdk-architect` — the contract invariants. Its policies are the authority:
     zero-runtime-coupling, ast-wire-format, field-validation-scoping, route-access,
     stability-and-deprecation, jdk-baseline. Read the ones the diff touches.
   - `exeris-sdk-verification` — whether a new branch, record component or annotation is reachable
     by a test that would fail without it, and whether it landed in the right class rather than a
     parallel one.

4. **Correctness.** Read the diff for defects, not only for policy breaches: wrong logic, an edge
   case the change introduces, a broken contract, a merge artefact, a reference to a file the change
   deletes. This step exists because the rest of the routine is organised by policy, and a bug that
   violates no written clause would otherwise have nowhere to go — the verdict schema's `findings[].why`
   takes a `<file>#<rule>` coordinate, so a defect with no clause degrades to a suggestion and stops
   blocking. It does not stop blocking. **A defect found here is a blocking finding whose `why` is
   `.agents/workflows/sdk-pr-review.md#correctness`** — this rule is the clause. Code that is wrong
   does not merge because nothing forbade being wrong in particular.

5. **Public API and stability.** 1.0.0 freezes the contract. Anything removed, renamed or moved
   between canonical annotations goes through the deprecation pipeline —
   `@Deprecated(forRemoval = true)`, a documented replacement, a fallback window, a `MIGRATION.md`
   entry and a `### Breaking` line in `CHANGELOG.md`. A public element that changed shape without
   one is `BLOCKED`, however small the diff.

6. **Scope.** Does the diff match the pull-request title and description? Name what is in scope and
   missing, and what is out of scope and should be split.

7. **Report.** Lead with blockers, then in-scope improvements, then non-blocking suggestions. Cite
   `file:line`, and say what breaks rather than that something could be improved. End with the
   verdict and, after it, the same content as a fenced `json` block conforming to
   `.agents/schemas/verdict.schema.json`.

Two things are absolute regardless of size. A published-surface change with no entry in the
deprecation pipeline is `BLOCKED`; so is a claim about a gate that was not run being reported as
though it had been.
