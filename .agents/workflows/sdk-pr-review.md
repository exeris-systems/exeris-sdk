---
name: sdk-pr-review
description: Full pull-request review for exeris-sdk — scope, the contract invariants, public-API stability, test reach, publish readiness, ending in a verdict. This is the routine the CI review action runs; run it locally before opening a pull request to get the same answer earlier.
argument-hint: PR number, diff, or the list of changed files
steps:
  - {agent: exeris-sdk-router, skill: exeris-sdk-task-classifier}
  - {agent: exeris-sdk-architect, when: "the diff touches an annotation, an AST record, a pom or a published surface", gate: verdict}
  - {agent: exeris-sdk-verification, when: "the diff adds a branch, a record component or an annotation", gate: verdict}
gates: [ci:docs, ci:commits, ci:pr-body, ci:javadoc, ci:tsdoc, mvn:verify, npm:test]
output: schemas/verdict.schema.json
---

Review the change below and return one verdict.

Change: $ARGUMENTS

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

2. **Record what the mechanical checks said**, before reading the diff for meaning. The gates are
   CI's, not this routine's: `docs-lint`, `commit-lint`, `pr-body-check`, `javadoc-gate`,
   `tsdoc-gate`, `mvn verify` (JaCoCo 85% BUNDLE), and `npm test` in `exeris-sdk-ui-kit` (Vitest
   85% per file). Report each as pass, fail or not-run with its exit code. A check you did not run
   is `not-run`; it is never silence, and a verdict resting on one must say so.

3. **Specialist passes**, as the triage class requires. Each owns its own list and none restates
   another's:

   - `exeris-sdk-architect` — the contract invariants. Its policies are the authority:
     zero-runtime-coupling, ast-wire-format, field-validation-scoping, route-access,
     stability-and-deprecation, jdk-baseline. Read the ones the diff touches.
   - `exeris-sdk-verification` — whether a new branch, record component or annotation is reachable
     by a test that would fail without it, and whether it landed in the right class rather than a
     parallel one.

4. **Public API and stability.** 1.0.0 freezes the contract. Anything removed, renamed or moved
   between canonical annotations goes through the deprecation pipeline —
   `@Deprecated(forRemoval = true)`, a documented replacement, a fallback window, a `MIGRATION.md`
   entry and a `### Breaking` line in `CHANGELOG.md`. A public element that changed shape without
   one is `BLOCKED`, however small the diff.

5. **Scope.** Does the diff match the pull-request title and description? Name what is in scope and
   missing, and what is out of scope and should be split.

6. **Report.** Lead with blockers, then in-scope improvements, then non-blocking suggestions. Cite
   `file:line`, and say what breaks rather than that something could be improved. End with the
   verdict and, after it, the same content as a fenced `json` block conforming to
   `.agents/schemas/verdict.schema.json`.

Two things are absolute regardless of size. A published-surface change with no entry in the
deprecation pipeline is `BLOCKED`; so is a claim about a gate that was not run being reported as
though it had been.
