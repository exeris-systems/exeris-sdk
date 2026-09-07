---
title: "RFC-2026-06-11: How does bidirectional sync tell user edits from tooling mutations — and what happens when they collide?"
type: rfc
visibility: public
owning-repo: exeris-sdk
status: active
last-verified: 2026-06-14
---

# RFC-2026-06-11: How does bidirectional sync tell user edits from tooling mutations — and what happens when they collide?

| Field             | Value                                                                 |
|:------------------|:----------------------------------------------------------------------|
| **Status**        | **ACCEPTED**                                                          |
| **Author(s)**     | arkstack-dev                                                          |
| **Date Opened**   | 2026-06-11                                                            |
| **Date Closed**   | 2026-06-13                                                            |
| **Target ADR(s)** | [ADR-042](../adr/ADR-042-bidirectional-mutation-surface.md) (locks the detection model + the 0.5.0 mutation surface) |
| **Affected Repos**| `exeris-sdk` (`source-model`, `source-model-io`), `exeris-platform` (lsp, Studio), `exeris-tooling` (codegen baseline) |
| **Reviewers**     | —                                                                    |

## Question

When the LSP/Studio applies a tooling-driven mutation (`exeris/applyMutation`) to a user-authored `@ExerisDomain` source, the file may have drifted since the last codegen run — the user edited it by hand. **What is the unit of comparison for detecting that drift (text, AST facet, or metadata path), what serves as the baseline, and which layer (SDK `-io`, LSP, Studio) decides whether a colliding mutation applies, rejects, or surfaces a conflict?**

## Context

This is the last open 0.3.0 roadmap line ("Conflict resolution: user edits since last codegen vs. tooling-driven mutations"). ADR-037 explicitly deferred it ("may warrant its own RFC") while drawing the `-io` module boundary, and its pre-emptive ruling already fixes *where* the answer will live: `MutationOp` / `MutationResult` records in `source-model` (pure data), application in `-io`. What those records *mean* on a drifted file is undecided — and `MutationResult` cannot be designed without it, because its whole reason to exist is the `success / conflict / validation error` distinction. The 0.5.0 milestone (path-based addressing, optimistic concurrency tokens) is therefore blocked on this question, and `exeris-platform-lsp`'s `exeris/applyMutation` method is blocked on 0.5.0.

The question became concrete (rather than speculative) with the 0.3.0 closing work: the budgetHQ corpus property tests (PR #37) measured what the writer actually guarantees, and it is weaker than a textual-diff consumer would assume — see §Data gathered. Answering now, before the 0.5.0 records are shaped, is cheap; answering after would bake a detection model into a published wire format and then amend it.

## Investigation

### Prior art

- **`unmodeledFacets()` (this repo, ADR-037 Slices A–D)** — the reader already ships a *facet-level* divergence guard: it reports annotations the processor models but the reader would drop, and reattach callers must refuse when it is non-empty. Conflict detection is the same shape of question one level down (attribute-level, between two readable sources) — the guard's "refuse rather than misattribute" posture is the precedent.
- **`exeris-metadata/<entity>.json` (exeris-tooling)** — the processor already emits the canonical AST per entity at every codegen run. A "what did the source look like at last codegen" baseline therefore already exists on disk, in the SDK's own wire format, with no new artifact needed.
- **LSP/IDE ecosystems** — language servers apply workspace edits against a document *version number* (LSP `textDocument/version`): an edit computed against a stale version is rejected by the client, not patched. This is optimistic concurrency at the transport layer, and 0.5.0's "optimistic concurrency tokens" line mirrors it; it handles *racing* edits but not *semantic* collisions (a token says "the file changed," not "your rename collides with the user's rename").
- **git three-way merge** — the obvious textual answer; its failure mode on this exact workload is measured below.

### Constraints

- **ADR-037 pre-emptive ruling** — op/result records in `source-model`, application in `-io`. This RFC does not re-litigate placement, only semantics.
- **Entity-First (ADR-003)** — the user's `.java` file is the single source of truth. Any scheme that demands the user not touch generated regions (marker fences) inverts that premise.
- **No symbol solving in `-io`** — the reader/writer match annotations by simple name and do not resolve types (documented limitation, reaffirmed by ADR-038's written-form service contract). Detection cannot rely on resolved types.
- **Zero runtime coupling** — whatever the records look like, `source-model` stays `jackson-annotations`-only; anything needing JavaParser lives in `-io`.

### Data gathered

From the budgetHQ corpus property tests (PR #37, `BudgetHqCorpusRoundTripTest`, six members ported from cited real budgetHQ entities):

1. **Writer mutations are not byte-exact inverses.** `removeField(addField(src, …))` differs from `src` on every corpus member: `LexicalPreservingPrinter` leaves an indentation-only line where the removed member stood. Content lines and the re-read AST are equal; bytes are not.
2. **In-place mutations are byte-exact.** `renameField` there-and-back restores the source byte-for-byte on every member.
3. **Reader and processor agree on every facet** (`unmodeledFacets()` empty corpus-wide), and a writer mutation is immediately visible to `read()` — so AST-level comparison of two source states is already reliable with shipped code.

Consequence of (1): any *textual* drift detector would classify the writer's own removal artifacts as user edits — false conflicts on the tool's own output, the exact misattribution ADR-037's guard exists to prevent. (Whether the writer should additionally be hardened toward byte-exact removals is a separate, orthogonal improvement; even byte-exact removals would not make textual detection sufficient, since user formatting churn — an IDE reformat — would still read as a total conflict.)

### Spike outcomes

None beyond the corpus measurements above; they were sufficient to rank the options.

## Options Considered

### Option A: Textual three-way merge (git-style)

Baseline = source text snapshot at last codegen; drift = `diff(baseline, current)`; conflict = overlapping hunks between drift and the mutation's edit.

**Pros:**
- No new machinery; mature algorithms; language-agnostic.
- Byte-level fidelity — catches edits in comments and formatting that the AST does not model.

**Cons:**
- **Disproven on our own output** (§Data gathered): the writer's removal artifacts and any IDE reformat read as user edits → false conflicts, eroding trust in the conflict signal.
- Requires storing full source snapshots as baselines (a new artifact; the JSON baseline already exists).
- Conflict reports are line-ranges, not addressable metadata — Studio cannot say "the user changed *this field's type*."

**Cost:** low to build, high to operate (false-conflict triage falls on every Studio user).

### Option B: AST-level three-way comparison at metadata-path granularity

Baseline = the `exeris-metadata/<entity>.json` from the last codegen run (deserialized `DomainMetadata`). Drift = facet-by-facet comparison of `read(currentSource)` against the baseline, addressed by the 0.5.0 metadata paths (`/entities/Order/fields/total`). A `MutationOp` conflicts iff its target path (or a parent/child of it) drifted to a value that differs from *both* the baseline and the op's own intent (so a convergent edit — user and op landing on the same value — is a `success`, not a conflict); `MutationResult.conflict` carries the path, the baseline value, the current value, and the op's intended value. Detection and application live in `-io`; the records live in `source-model`; *policy* (auto-apply non-overlapping ops, prompt on conflicts, three-way auto-merge of attribute sets) stays in the LSP/Studio layer.

**Pros:**
- Immune to both measured failure modes: LPP artifacts and formatting churn are invisible at AST level (property 3 shows AST comparison already works with shipped code).
- Baseline is free — the processor already writes it; no new artifact, no snapshot store.
- Conflicts are addressable: the same path vocabulary 0.5.0 needs anyway, so `MutationOp`, `MutationResult`, and conflict reports share one addressing scheme.
- The `unmodeledFacets()` guard slots in as the safety precondition: non-empty guard → refuse detection entirely (cannot trust the comparison).

**Cons:**
- Blind to drift the AST does not model (comment edits, formatting, non-Exeris annotations) — by design these are *not conflicts*, but it must be documented that tooling mutations may interleave with them silently.
- Facet-by-facet diff of `DomainMetadata` is real implementation work (deep comparison with path attribution across 22 record types — 19 base + 3 capability).
- Baseline freshness depends on codegen having run; a stale or missing JSON has no trustworthy "before" state to diff against, so detection must **refuse** (return the no-baseline outcome named in the recommendation) rather than guess — re-baselining is then an explicit caller action.

**Cost:** moderate engineering in `-io` (the diff walker), small in `source-model` (conflict-shaped records 0.5.0 defines anyway); near-zero operator cost.

### Option C: Marker-fenced generated regions

Generated members carry `// <exeris:generated>` fences; anything outside fences is the user's, anything inside is the tool's; conflicts are edits inside fences.

**Pros:**
- Trivial detection; no baseline at all.

**Cons:**
- Violates Entity-First (ADR-003): the user's file is the source of truth — fencing it partitions ownership *inside* the single source and makes hand-editing fenced members illegal, which is exactly the `.proto`/OpenAPI split the SDK exists to remove.
- Pollutes user sources with tool markup; the writer's preservation guarantee currently means "your file stays yours."

**Cost:** low to build, but architecturally regressive.

### Option D (do nothing): last-write-wins

Apply every mutation unconditionally; the user's VCS is the conflict handler.

**Pros:**
- Zero work; honest about 0.3.0's current state (the writer already behaves this way).

**Cons:**
- Studio silently overwrites hand edits (e.g. a user's `@Validation` tweak lost to a stale Studio panel) — unacceptable for the bidirectional-sync promise, and `MutationResult.conflict` would be a lie (nothing ever conflicts).

**Cost:** deferred, with interest — paid by the first dogfood user.

## Recommendation

**Option B.** It is the only option consistent with the three governing precedents (ADR-003's single source of truth, ADR-037's refuse-rather-than-misattribute guard, the existing JSON hand-off as baseline) and the only one the corpus data does not falsify. Concretely, the eventual ADR should lock:

1. **Unit of comparison:** `DomainMetadata` facets addressed by metadata path — the same paths 0.5.0 uses for `MutationOp` targeting.
2. **Baseline:** the last-codegen `exeris-metadata/<entity>.json`; stale/missing baseline → detection refuses with a distinct `MutationResult` outcome — working name `NO_BASELINE` (a third state alongside `success` and `conflict`, **not** a conflict variant; the ADR must name it precisely since it freezes into the wire format), and re-baselining is an explicit caller action. A convergent edit — user drift and op intent landing on the *same* value at a path — is a `success`, not a conflict (the drift set is "paths whose value differs from both baseline *and* op intent"); this auto-merge is the single most user-friendly case to get right first.
3. **Safety precondition:** non-empty `unmodeledFacets()` on the *current source* → refuse, same posture as reattach. This guards the current side only; the *baseline* JSON may have been written by an older SDK and carry facets the current reader does not model (see Open Questions) — that asymmetry needs its own check or an explicit out-of-scope statement before the ADR.
4. **Layering:** drift detection + conflict-aware application in `-io`; conflict-shaped records in `source-model`; merge/prompt *policy* in LSP/Studio — the SDK reports, it does not decide.
5. **Out of SDK scope:** textual fidelity below the AST (comment/formatting drift) is documented as non-conflicting; optimistic concurrency tokens (0.5.0) handle racing edits at the transport layer and are complementary, not alternative.

Sequencing: accept alongside the 0.5.0 mutation-surface design and fold both into one ADR — the conflict variant is a constructor of `MutationResult`, so the two designs are one wire-format decision.

## Decision Record (ACCEPTED 2026-06-13)

Option B accepted, folded into [ADR-042](../adr/ADR-042-bidirectional-mutation-surface.md) with the 0.5.0 mutation surface. The open questions were resolved as follows:

- **Stale-baseline detection → content hash.** Codegen embeds a `sourceDigest` (hash of the normalized source) in `exeris-metadata/<entity>.json`; the reader recomputes and treats a mismatch as `NO_BASELINE`. The same digest doubles as the 0.5.0 optimistic-concurrency token. (Chosen over mtime-comparison, which is brittle across checkout/clone, and over missing-only, which leaves stale baselines undetected.)
- **Baseline schema-version skew → embed + refuse.** Codegen embeds the `source-model` `schemaVersion`; a reader on a different version refuses with `NO_BASELINE`. This closes the baseline side of the reader/baseline skew; the current-source side stays guarded by `unmodeledFacets()`.
- **Path-overlap semantics → ancestor-or-descendant.** An op conflicts when its path or any ancestor/descendant drifted to a value differing from both baseline and intent; siblings never conflict; convergent edits are `SUCCESS`.

Both digest and version are **wire-format additions to the processor↔codegen hand-off**, so the first 0.5.0 cut carries a hard `exeris-tooling` dependency (ADR-042 cross-repo obligations) — this is the full, safe model, not an MVP. The remaining open questions (digest normalization detail, `MutationOp` record shape, exact `NO_BASELINE`/`CONFLICT` JSON, capability mutation root) are slice-level and tracked in ADR-042's engineering protocol.

## Open Questions

> The three questions struck below were settled in the **Decision Record (ACCEPTED 2026-06-13)** above and folded into ADR-042. They are kept here, struck, for the record.

- ~~Does a *parent/child* path overlap conflict (op targets `/entities/Order/fields/total`, drift at `/entities/Order`) or only exact-path collision? Leaning: ancestor-or-descendant overlap conflicts; siblings never do.~~ → **Resolved:** ancestor-or-descendant overlap conflicts; siblings never do; convergent edits are `SUCCESS` (Decision Record above; ADR-042 obligation 4).
- Should the corpus property "removal inverses are content/AST-equal but not byte-equal" be hardened in the writer (strip the indentation-only residue) regardless of the detection model? It does not change the recommendation, but it reduces VCS noise for users. Candidate 0.5.x nice-to-have.
- Baseline location for non-Maven consumers (Studio scratch projects with no `target/` dir) — does the LSP own a baseline cache?
- ~~**How is a *stale* baseline detected** (distinct from a *missing* one)? A JSON that exists but predates the current source still produces a false "everything is drift" if treated as current. Candidates: source-mtime vs. JSON-mtime, a content hash / source digest embedded in the JSON at codegen time, or deliberately scoping detection to "missing only" for the first cut. Each has a different implementer obligation; the ADR should pick one even if it defers the richer options.~~ → **Resolved:** content-hash `sourceDigest` embedded by codegen; reader recomputes, mismatch ⇒ `NO_BASELINE` (Decision Record above; ADR-042 obligation 5).
- ~~**Baseline schema-version skew.** If the baseline JSON was emitted by an older SDK, it may contain facets the current reader does not model (or omit facets the current reader now reads) — comparing it against a newer reader is a false-safety. Options: embed the source-model schema version in the emitted JSON and refuse on mismatch, treat a lower baseline version as `NO_BASELINE`, or declare cross-version comparison explicitly out of scope. Pairs with recommendation point 3.~~ → **Resolved:** embed `schemaVersion` and refuse on mismatch with `NO_BASELINE` (Decision Record above; ADR-042 obligation 5).
- **Precise name + JSON shape of the `NO_BASELINE` outcome** — it is a wire-format addition to `MutationResult` and freezes at 1.0.0, so the ADR (not this RFC) must fix the constant name and serialized form. (Still open — deferred to ADR-042 slice 1/3.)
