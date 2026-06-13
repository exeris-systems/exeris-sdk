# ADR-042: 0.5.0 bidirectional mutation surface — `MutationOp` / `MutationResult` + AST-level three-way conflict resolution

| Attribute       | Value                                                                                          |
|:----------------|:-----------------------------------------------------------------------------------------------|
| **Status**      | **ACCEPTED**                                                                                   |
| **Deciders**    | Arkadiusz Przychocki                                                                           |
| **Date**        | 2026-06-13                                                                                     |
| **Scope**       | cross-repo                                                                                     |
| **Owning Repo** | `exeris-sdk`                                                                                    |
| **Driven By**   | [RFC-2026-06-11](../rfc/RFC-2026-06-11-source-model-conflict-resolution.md) (ACCEPTED — Option B) |
| **Builds On**   | [ADR-037](ADR-037-source-model-io-module.md) (records in `source-model`, application in `-io`); [ADR-003](ADR-003%20Entity-First%20Development%20Strategy.md) Entity-First |
| **Compliance**  | Zero runtime coupling invariant (`source-model` pulls only `jackson-annotations`; JavaParser stays in `-io`); AST + Jackson 3 wire-format contract |

> ADR numbers are a **single ecosystem-wide namespace** registered in [`exeris-docs/adr-index.md`](../../../exeris-docs/adr-index.md). The gap between ADR-038 and ADR-042 in this repo is expected — 039–041 are owned by other Exeris repos.

## Context and Problem Statement

The 0.5.0 milestone exposes the metadata mutations that LSP, Studio, and IDE plugins call (`MutationOp`, `MutationResult`, path-based addressing, optimistic concurrency tokens). The `-io` writer already *applies* eight field/relationship/action mutations idempotently (ADR-037, PRs #23–#25); what is missing is (a) a serializable **operation** and **result** vocabulary the transport layer can carry, and (b) a **conflict-resolution** model for the case the writer cannot see: the user hand-edited the source since the last codegen run, so a tooling-driven mutation may collide with a user edit.

[RFC-2026-06-11](../rfc/RFC-2026-06-11-source-model-conflict-resolution.md) settled the conflict-detection model (Option B — **AST-level three-way comparison** against the last-codegen `exeris-metadata/<entity>.json` baseline) and ranked it by measurement: the budgetHQ corpus tests (PR #37) proved a *textual* three-way merge is falsified by the writer's own output (`LexicalPreservingPrinter` leaves an indentation-only line after a removal, so removal inverses are content/AST-equal but not byte-equal), while AST comparison is reliable with shipped code. This ADR locks that model together with the surrounding 0.5.0 surface, because the conflict and no-baseline states are constructors of `MutationResult` — they are one wire-format decision, not two.

ADR-037 already issued the placement ruling pre-emptively: the **op/result records live in `source-model`** (pure data, importable by LSP and codegen without JavaParser), the **application lives in `-io`**. This ADR does not re-litigate placement; it fixes the record shapes, the detection algorithm, the baseline-trust mechanism, and the cross-repo obligation that follows from it.

This unblocks `exeris-platform-lsp`'s reserved `exeris/applyMutation` method (its `exeris/entityModel` counterpart is already served by the `-io` reader).

## 🏁 The Decision

**Add `MutationOp` and `MutationResult` records to `exeris-sdk-source-model` (Jackson-serializable, JavaParser-free), addressed by a path grammar shared with conflict reports; implement AST-level three-way drift detection and conflict-aware application in `exeris-sdk-source-model-io`, comparing `read(currentSource)` against the last-codegen baseline JSON. To make the baseline trustworthy, `exeris-tooling` codegen emits two baseline-trust fields into each `exeris-metadata/<entity>.json` — a `sourceDigest` (a content hash of the normalized source) and a `schemaVersion` (the `source-model` schema version). The SDK detects and reports; merge/prompt policy stays in LSP/Studio.**

This mirrors the layering the repo already proves: pure data in `-source-model`, JavaParser-bound behaviour in `-io`, the processor/codegen as the baseline producer.

**Concrete obligations:**

1. **`MutationOp` — `source-model`, path-addressed.** A sealed record family (or a single record with an `OpKind` discriminator — finalized in the implementation slice) covering the eight shipped writer operations plus relationship-cardinality change: `addField`, `removeField`, `renameField`, `changeFieldType`, `addRelationship`, `removeRelationship`, `addAction`, `removeAction`, `changeRelationshipCardinality`. Each carries a **target path** and the operation payload. No JavaParser types; serializes under the AST-wide Jackson 3 contract (`FAIL_ON_NULL_FOR_PRIMITIVES=false`, `@JsonInclude(NON_DEFAULT)` quirks honoured).

2. **Path grammar.** `/entities/<Entity>/fields/<name>`, `/entities/<Entity>/relationships/<fieldName>`, `/entities/<Entity>/actions/<name>` — the same vocabulary used for `MutationOp` targeting and for conflict-report addressing, so ops and conflicts speak one language. Capability modules get a parallel root (`/capabilities/<Module>/…`) when the mutation surface extends to caps; out of scope for the first cut.

3. **`MutationResult` — four top-level outcomes.** `SUCCESS`, `CONFLICT`, `VALIDATION_ERROR`, and **`NO_BASELINE`** (the RFC's "no trustworthy baseline" state — a peer of the others, **not** a conflict variant). `CONFLICT` carries the path, the baseline value, the current (drifted) value, and the op's intended value. The exact constant names and serialized JSON shape freeze at 1.0.0; the implementation slice fixes them.

4. **Drift detection — AST-level three-way, in `-io`.** Drift = facet-by-facet comparison of `read(currentSource)` against the deserialized baseline `DomainMetadata`, attributed to paths (obligation 2). A `MutationOp` **conflicts iff** its target path — **or any ancestor or descendant of it** — drifted to a value that differs from *both* the baseline and the op's own intent. A **convergent edit** (user and op landing on the same value) is a `SUCCESS`, not a conflict. Sibling paths never conflict.

5. **Baseline trust — `sourceDigest` + `schemaVersion` (cross-repo, `exeris-tooling`).** The baseline is the last-codegen `exeris-metadata/<entity>.json`. Codegen emits two new fields into it:
   - `sourceDigest` — a hash of the *normalized* source the baseline was derived from (normalization defined in the impl slice — at minimum line-ending and trailing-whitespace neutral, so a reformat that the AST ignores does not spuriously invalidate the baseline). The reader recomputes the digest of the current source; **mismatch ⇒ the baseline is stale ⇒ `NO_BASELINE`**. This also serves as the **optimistic-concurrency token** (obligation 7) — one mechanism, not two.
   - `schemaVersion` — the `source-model` schema version that wrote the JSON. **A reader on a different schema version refuses ⇒ `NO_BASELINE`**, because an older baseline may carry or omit facets the current reader models differently (false safety otherwise). Missing baseline file ⇒ `NO_BASELINE`. In all three cases re-baselining (re-running codegen) is an explicit caller action; the SDK never silently guesses.

6. **Safety precondition.** Non-empty `unmodeledFacets()` on the *current source* ⇒ refuse, same posture as reattach (ADR-037). The baseline side is guarded by `schemaVersion` (obligation 5), so the current-source guard and the baseline-version guard together close both directions of the reader/baseline skew the RFC flagged.

7. **Optimistic concurrency tokens.** The 0.5.0 "optimistic concurrency token" is the `sourceDigest` of the source a mutation batch was computed against; a batch computed against a stale digest is rejected at submission. This handles *racing* edits at the transport layer and is complementary to — not a substitute for — the semantic conflict detection of obligation 4.

8. **Layering — the SDK reports, it does not decide.** Records in `source-model`; drift detection + conflict-aware application in `-io`; merge/auto-apply/prompt **policy** in LSP/Studio. The SDK returns `MutationResult`s; it does not choose how a conflict is resolved.

9. **Out of SDK scope.** Textual fidelity below the AST (comment/formatting drift) is **non-conflicting by design** — documented, not detected. The `cap-manifest` and `@Requires`→`@Provides` resolution remain tooling concerns (ADR-024). Marker-fenced generated regions are rejected (they violate ADR-003 Entity-First — see RFC Option C).

## Cross-repo obligations

| Repo | Obligation |
|:-----|:-----------|
| `exeris-tooling` | Codegen emits `sourceDigest` + `schemaVersion` into every `exeris-metadata/<entity>.json` (wire-format addition the processor/codegen own). Without it the reader has no trustworthy baseline and every `applyMutation` returns `NO_BASELINE`. `.link.md` stub + the emit change. |
| `exeris-platform` | LSP `exeris/applyMutation` consumes `MutationOp`/`MutationResult` and the `-io` conflict detector; `exeris/entityModel` already served by the reader. Merge/prompt policy lives here. `.link.md` stub. |

## Consequences

**Positive.** The conflict signal is immune to the two measured failure modes (LPP residue, IDE reformat). The baseline is the artifact codegen already writes — no new store, only two fields. Ops, results, and conflicts share one path vocabulary. `sourceDigest` does double duty as the concurrency token.

**Negative / accepted.** The deep facet diff across 22 AST record types is real implementation work in `-io`. The baseline-trust fields are a **wire-format change to the processor↔codegen hand-off** that `exeris-tooling` must ship before `applyMutation` is usable end-to-end — a hard cross-repo sequencing dependency, called out so it is not discovered late. Comment/formatting drift is silently non-conflicting; documented as a known boundary.

## Engineering protocol (slices)

1. **Records slice (`source-model`):** `MutationOp` family + `MutationResult` (four outcomes) + path grammar, with `AstJsonRoundTripTest` coverage per record (wire-format contract). No `-io` dependency.
2. **Detection slice (`-io`):** three-way drift walker (baseline JSON vs `read(current)`), path attribution, ancestor-or-descendant conflict rule, convergent-edit = success. Corpus-backed tests reusing `corpus/budgethq/`.
3. **Baseline-trust slice (cross-repo):** `exeris-tooling` codegen emits `sourceDigest` + `schemaVersion`; `-io` reads them and maps missing/stale/version-skew → `NO_BASELINE`. `.link.md` stubs land with this slice.
4. **Application slice (`-io`):** apply a `MutationOp` (or batch) with conflict-aware results, reusing the existing idempotent writer mutations underneath.

## Open questions deferred to the slices (not blocking acceptance)

- Exact normalization for `sourceDigest` (line-ending/whitespace neutrality is the floor; whether to canonicalize via a parse-and-reprint is a slice decision).
- `MutationOp` as a sealed record family vs. a discriminator record — a `source-model` shape call made in slice 1.
- Precise constant names + JSON shape of `NO_BASELINE` and `CONFLICT` (frozen at 1.0.0, fixed in slice 1/3).
- Whether the capability mutation root (`/capabilities/…`) lands in 0.5.0 or waits for a real cap consumer.
