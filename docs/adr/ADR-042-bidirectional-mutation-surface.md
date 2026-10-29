---
title: "ADR-042: 0.5.0 bidirectional mutation surface — MutationOp / MutationResult + AST-level three-way conflict resolution"
type: adr
visibility: public
owning-repo: exeris-sdk
status: active
last-verified: 2026-10-29
slug: adr/ADR-042
---

# ADR-042: 0.5.0 bidirectional mutation surface — `MutationOp` / `MutationResult` + AST-level three-way conflict resolution

| Attribute       | Value                                                                                          |
|:----------------|:-----------------------------------------------------------------------------------------------|
| **Status**      | **ACCEPTED**                                                                                   |
| **Deciders**    | Arkadiusz Przychocki                                                                           |
| **Date**        | 2026-06-13                                                                                     |
| **Scope**       | cross-repo                                                                                     |
| **Owning Repo** | `exeris-sdk`                                                                                    |
| **Driven By**   | [RFC-2026-06-11](../rfc/RFC-2026-06-11-source-model-conflict-resolution.md) (ACCEPTED — Option B) |
| **Builds On**   | [ADR-037](ADR-037-source-model-io-module.md) (records in `source-model`, application in `-io`); [ADR-003](ADR-003-entity-first-development-strategy.md) Entity-First |
| **Compliance**  | Zero runtime coupling invariant (`source-model` pulls only `jackson-annotations`; JavaParser stays in `-io`); AST + Jackson 3 wire-format contract |

> ADR numbers are a **single ecosystem-wide namespace** registered in [`exeris-docs/adr-index.md`](https://github.com/exeris-systems/exeris-docs/blob/main/adr-index.md). The gap between ADR-038 and ADR-042 in this repo is expected — 039–041 are owned by other Exeris repos.

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

3. **`MutationResult` — four top-level outcomes.** `SUCCESS`, `CONFLICT`, `VALIDATION_ERROR`, and **`NO_BASELINE`** (the RFC's "no trustworthy baseline" state — a peer of the others, **not** a conflict variant). `CONFLICT` carries the path, the baseline value, the current (drifted) value, and the op's intended value. `VALIDATION_ERROR` is the *op-is-malformed-or-inapplicable* outcome (distinct from a baseline collision): the op is structurally rejected before any drift comparison — e.g. an unresolvable / syntactically invalid target path, an op that would produce a duplicate field/action name, or a `changeFieldType`/`changeRelationshipCardinality` payload the AST grammar rejects. The precise trigger set is fixed in the implementation slice. The exact constant names and serialized JSON shape freeze at 1.0.0; the implementation slice fixes them.

4. **Drift detection — AST-level three-way, in `-io`.** Drift = facet-by-facet comparison of `read(currentSource)` against the deserialized baseline `DomainMetadata`, attributed to paths (obligation 2). A `MutationOp` **conflicts iff** its target path — **or any ancestor or descendant of it** — drifted to a value that differs from *both* the baseline and the op's own intent. A **convergent edit** (user and op landing on the same value) is a `SUCCESS`, not a conflict. Sibling paths never conflict.

5. **Baseline trust — `sourceDigest` + `schemaVersion` (cross-repo, `exeris-tooling`).** The baseline is the last-codegen `exeris-metadata/<entity>.json`. Codegen emits two new fields into it:
   - `sourceDigest` — a hash of the *normalized* source the baseline was derived from (normalization defined in the impl slice — at minimum line-ending and trailing-whitespace neutral, so a reformat that the AST ignores does not spuriously invalidate the baseline). The reader recomputes the digest of the current source; **mismatch ⇒ the baseline is stale ⇒ `NO_BASELINE`**. This also serves as the **optimistic-concurrency token** (obligation 7) — one mechanism, not two.
   - `schemaVersion` — the `source-model` schema version that wrote the JSON. In practice this is the **`exeris-sdk-source-model` artifact version** (e.g. `0.5.0`); codegen stamps it and the `-io` reader reads it from the same source, so the two always agree on the value's meaning. Whether that value is the raw Maven artifact version read at runtime or a dedicated `SchemaVersion` constant in `source-model` (decoupling the wire-format version from the release cadence) is a slice-1 call. **A reader on a different schema version refuses ⇒ `NO_BASELINE`**, because an older baseline may carry or omit facets the current reader models differently (false safety otherwise). Missing baseline file ⇒ `NO_BASELINE`. In all three cases re-baselining (re-running codegen) is an explicit caller action; the SDK never silently guesses.

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
- `schemaVersion` source — raw `exeris-sdk-source-model` artifact version vs. a dedicated `SchemaVersion` constant in `source-model` (obligation 5); a slice-1 call once codegen and the reader bind to it.
- `VALIDATION_ERROR` trigger set — the exact list of structural rejections (path syntax, duplicate names, payload-grammar violations) is fixed in the application slice (slice 4).
- Whether the capability mutation root (`/capabilities/…`) lands in 0.5.0 or waits for a real cap consumer.

## A Live-Source Caller's Baseline Is the Source Its Token Names (2026-10-07 amendment)

Obligation 5 names the last-codegen `exeris-metadata/<entity>.json` as the baseline. That fits a caller that computes ops from codegen output. Studio, the first interactive caller, does not: it computes each op from the domain it read over `exeris/domainDescribe` a moment earlier, together with that source's `SourceDigest`. For that caller the codegen JSON is the wrong baseline. It may belong to another domain of the same simple name, and it predates every write the caller itself made, so the inverse of the caller's own write reads as user drift and returns `CONFLICT`.

### The Decision

1. **A second trusted baseline: the source the concurrency token names.** When a request carries no baseline JSON and its `concurrencyToken` equals `SourceDigest.of` the live source, the baseline is that live source, read with `SourceModelReader` and stamped with `BaselineTrust.current`. The token proves the source has not moved since the op was computed, so this baseline is exact, not guessed.
2. **Where the token does not match, nothing is derived.** The applier's own token check returns `NO_BASELINE` / `STALE_DIGEST`. A request with neither baseline JSON nor token stays `NO_BASELINE` / `MISSING_BASELINE`, as before.
3. **Build output is never searched for a baseline.** A host passes baseline JSON explicitly, or relies on point 1; it does not look for a file by entity name.
4. **The derivation runs on the bytes the applier receives**, inside the host's per-file serialisation, so the baseline and the current source are one snapshot.

### Consequences

- For a token-only caller, baseline equals current, so three-way detection finds no drift: `CONFLICT` cannot occur, and any edit made outside the caller — convergent or not — is reported as `STALE_DIGEST`. That is stricter than obligation 4's convergent-edit merge, and deliberately so: such a caller re-reads and re-applies instead of merging.
- The `SCHEMA_VERSION_SKEW` gate does not apply to a derived baseline, which is read by the current reader from the live source.
- Idempotency is unchanged: the same op re-sent with the digest its first application produced is a convergent no-op, and the same op re-sent with the old token is `STALE_DIGEST` and writes nothing.

### Realisation

`exeris-platform-lsp` realises points 1–4 in `MutationApplyService` for `exeris/applyMutation`; `ApplyMutationTest` pins the derived-baseline apply, the convergent re-apply, the inverse after a write, the stale token, and that build output is not read. Moving the derivation into `SourceModelMutationApplier`, so every host behaves the same, is the follow-up this amendment anticipates.

### What this amendment does NOT change

- Obligation 5 for a caller that passes codegen baseline JSON: trust gating, `sourceDigest` and `schemaVersion` stand as written.
- The `MutationOp` / `MutationResult` vocabulary and wire shape.

## The SDK Fixes the Reader Shape for `@GraphProperty`, `@UIGroup` and `@Blob` (2026-10-29 amendment)

The reader reads what the processor writes, so until now a carrier the processor did not fill stayed at its default in the reader, and `unmodeledFacets()` named the annotation. For `@GraphProperty`, `@UIGroup` and `@Blob` that order cannot hold. SDK 0.13 is the last SDK before 1.0, and exeris-tooling 0.11 and 0.12 both pin it; a shape the processor chose after that pin would be drift against every 0.13 reader until SDK 1.0. exeris-tooling#438 lists the questions the shape has to answer. This amendment answers them; it takes for these three annotations the order exeris-tooling ADR-105 obligation 26 took for `@RouteAccess`: the SDK fixes the shape, the reader ships first, and the processor matches it.

### The Decision

1. **`@GraphProperty` → `GraphMetadata.properties`.**
   - Read under a type-level `@Graph` only, as `@GraphEdge` is. On a type without `@Graph` it is not read and `graphMetadata` stays `null`.
   - One `GraphPropertyMetadata` per annotated field variable, in field declaration order.
   - `name` is a non-blank `@GraphProperty.name`, else the field name. `GraphPropertyMetadata` gains a trailing `field` naming the declaring field, `null` when it equals `name`, the rule `GraphEdgeMetadata.field` already follows. Without it a renamed property names no field to read its value from.
   - `type` is the `graphType` constant name (`"DOUBLE"`, …) when declared, and `null` for `AUTO`. No producer derives a graph type from a Java type: the store type of an `AUTO` property follows the field's Java type, which `FieldMetadata.type` already carries, so two producers cannot derive different ones. The kernel graph SPI types no property (`GraphNodeDescriptor.properties` is a list of names), so nothing downstream needs a derived value.
   - `indexed` is the declared value, else `false`. The other attributes have no component and are not read.
   - `properties` is an empty list when `@Graph` is present and no field declares `@GraphProperty`, the form `edges` takes. `null` keeps its meaning of "not carried": the producer does not extract `@GraphProperty`.
2. **`@UIGroup` → `UIMetadata.groups`.** The rules `@Tab` already follows in the reader, for the same reasons.
   - One `UIGroupMetadata` per distinct `name`, in the order each name is first declared, not sorted by `order`; a consumer sorts by `order`. Each variable of a multi-variable declaration is one member, and a `@Relationship` field is a member.
   - A conflicting declaration is resolved per attribute: the first member field, in declaration order, that declares an attribute gives its value, and a later one is ignored. A blank string counts as undeclared. This is what `@UIGroup.label` documents ("subsequent fields inherit from the first declaration").
   - `UIGroupMetadata`'s numeric and boolean components are primitive, so an attribute no member declares is written as the annotation default: `order` `999`, `collapsible` `true`, `collapsed` `false`. An undeclared string is `null`.
   - `gridSpan` has no `@UIGroup` attribute and is `12`, the full width of the form grid `UIMetadata.columns` defaults to, which is also what `UIGroupMetadata.simple` writes. `@UIGroup.columns` is the column count inside the group, a different quantity, and is not mapped onto it.
   - `@UIGroup.columns`, `cssClass` and `visibleWhen` have no component and are not read.
   - A type with groups and no `@UI` gets a `UIMetadata` whose other components are the builder defaults, as a type with tabs does. A type with no group keeps the builder's empty `groups`.
   - A `@UIGroup` whose `name` is not a non-blank string literal is skipped.
3. **`@Blob` → `FieldMetadata.blob`, as written.**
   - On every field carrying it, with or without `@Field`. A field without `@Field` keeps the `FieldMetadata.simple` values and gains the blob.
   - `container` is a non-blank literal, else `null` (derive from the domain). `contentTypes` holds the declared media types in declaration order, an empty list when none are declared. A `@Blob` declaring nothing is `BlobMetadata.unrestricted()`, not an absent facet.
   - The entity's data scope is not consulted. `@Blob` on a `GLOBAL`-scoped entity stays a build-time rejection in exeris-tooling (ADR-072's open question); the reader reads the declaration the source makes.
4. **`@Channel` stays unread.** `DomainMetadata.channel` encodes the kernel WebSocket SPI, which stays `preview` at kernel 1.0, so its shape is not fixed here. It remains in `UNMODELED_FACET_ANNOTATIONS`, and `unmodeledFacets()` names it.
5. **Parity is held by fixtures and the TCK.** The `-io` reader's processor-parity fixtures (`GraphProperties`, `GraphNoProperties`, `UiGroups`, `Blobs`) pin the shape above. `exeris-sdk-tck` gains `GRAPH_PROPERTIES`, `UI_GROUPS` and `BLOBS`, each with reader, producer and parity cases; the reader's binding runs them on every build. A producer binding declares a facet unsupported until it extracts it.

### Consequences

- `read()` returns these three components for a source that uses them, and `unmodeledFacets()` names only `@Channel` of the four. Against a processor baseline written before tooling extracts them, a source declaring one reads as changed, as it does for `@Tab` and `@QueryParam`.
- `@GraphProperty`, `@UIGroup` and `@Blob` move from RESERVED to PARTIAL: carried by the reader, extracted by no processor yet, read by no generator. `@UIGroup` was the last RESERVED layout annotation.
- The reader clause of ADR-072 obligation 7 no longer holds for `@Blob`. It still holds for `@Schedule` and `@Channel`.

### What this amendment does NOT change

- The freeze status of any of the three. `@Blob` and `BlobMetadata` stay under ADR-072; `GraphPropertyMetadata` grows by one trailing component under the record-growth stance and keeps its 3-component constructor.
- `GraphMetadata.queries` and `@GraphQuery`, which neither side reads.
- The mutation surface, `MutationResult`, and the baseline-trust rules above.
