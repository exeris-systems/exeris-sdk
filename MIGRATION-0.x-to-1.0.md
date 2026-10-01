---
title: "Migration guide: 0.x → 1.0.0 (skeleton)"
type: migration-guide
visibility: public
owning-repo: exeris-sdk
status: draft
last-verified: 2026-10-01
---

# Migration guide: 0.x → 1.0.0 (skeleton)

> **Status: skeleton, seeded during the 0.9.0 cycle.** 1.0.0 GA freezes the
> public API surface (1.x minors are additive-only, patches bug-fix only —
> see [`ROADMAP.md`](ROADMAP.md) "Versioning policy"). This document collects,
> ahead of time, everything a 0.x consumer must do to cross the freeze. It is
> to be **validated against the consumers that cross the freeze** — the
> `exeris-tooling` processor and codegen, `exeris-platform-lsp`, and, for the
> author-facing parts only, Stellar Tactics (ROADMAP 1.0.0 GA item) — and
> finalized in the 1.0.0 release PR. Per-0.x-step upgrade notes stay
> in [`MIGRATION.md`](MIGRATION.md).
>
> **Stellar Tactics validates the author side** (founder decision, 2026-09-26):
> §1/§2 as they apply to annotated sources, and what §3 promises an author. It
> is the one consumer that authors `@ExerisDomain` at scale — 36 classes across
> two generated services — and where the SDK, `exeris-tooling` and the kernel
> meet, which is how it found Stellar finding S6. It validates through tooling,
> not through the Java API, so it does not replace the two consumers above.
> "Validated" means, on the SDK 1.0.0-RC plus an `exeris-tooling` build and a
> kernel build on that RC:
> 1. `./build.sh` is green, with zero platform patches;
> 2. the hand-written source diff contains only what this guide lists;
> 3. every hunk in the generated trees is traced to this guide or to
>    `exeris-tooling`'s changelog;
> 4. any change in `-Aexeris.strict` warnings is explained;
> 5. the pass is recorded as a dated entry in the Stellar findings log and a
>    line in §5 of this guide.

---

## 1. Removals landing at 1.0.0

The complete list of API removed at the freeze. Anything not listed here
survives into 1.x unchanged.

- **`@Validation.required`** — deprecated since 0.2.0 (`forRemoval = true`).
  Replacement: `@Field.required`. The processor's read-with-warning fallback
  ends at 1.0.0 — sources still setting it silently lose required-ness.
- **`@Validation.validateOn`** — deprecated since 0.2.0 (`forRemoval = true`).
  Replacement: `@Field.inCreate` / `@Field.inUpdate`. Same fallback window.
- **`@ExerisDomain.tenantScoped`** — deprecated since 0.10.0
  (`forRemoval = true`, [ADR-059](docs/adr/ADR-059-data-scope-expression.md)).
  Replacement: `@ExerisDomain.dataScope` — `true → DataScope.TENANT`,
  `false → DataScope.GLOBAL`. The processor's read-with-warning fallback (and
  the AST's `DomainMetadata.effectiveDataScope()` fallback) end at 1.0.0;
  sources still setting only the boolean silently lose their tier. The AST
  component `DomainMetadata.tenantScoped` goes with it — `dataScope` becomes
  the sole carrier and `effectiveDataScope()` collapses to returning it.
- **`@ExerisDomain.apiVersion`** — deprecated since 0.12.0 (`forRemoval = true`,
  Stellar finding T38). **No replacement:** the attribute reaches no emitted
  artifact (router, OpenAPI document and generated clients all serve the entity
  at its `path`), and its `"v1"` default means it could never be switched on
  without moving every route. A versioned route, if wanted, is spelled in `path`.
  Two things go together:
  - the attribute itself;
  - the AST component `DomainMetadata.apiVersion`, with its accessor and its
    builder setter `DomainMetadata.Builder.apiVersion(String)` — which changes
    every `DomainMetadata` constructor, as the `tenantScoped` removal does.
  No producer reads the attribute in 0.12.x: neither the `-io` reader nor the
  `exeris-tooling` processor. A 0.x baseline that carries `"apiVersion"` still reads, because the records
  ignore unknown properties.

*(The 0.9.0 final deprecation sweep closed with zero additions to this list —
see the sweep disposition in [`ROADMAP.md`](ROADMAP.md). `ValidationMetadata`
was removed outright in 0.9.0, ADR-054, and is not a 1.0.0 item.)*

## 2. What is deliberately NOT removed (carried through 1.x)

- **`@UI`** — stays the functional field-level presentation path. The `@View`
  structural generation is live, but the ADR-047 leaf-field facet is not
  implemented yet, so the deprecation gate never opened in 0.x. Plan of
  record: facet lands in a 1.x minor → `@UI` gains `@Deprecated(forRemoval)`
  there → removal at 2.0.
- ~~**`@ExerisDomain.tenantScoped`**~~ — **moved to §1.** This entry used to
  read "frozen as a boolean through 1.x; the `DataScope` successor is gated on
  the kernel ADR-012 amendment and arrives additively in 1.x". That amendment
  landed on the kernel 0.11 line, so `DataScope` shipped in 0.10.0 and the
  boolean is a 1.0.0 removal instead. The old plan would have left removal to
  **2.0**: a deprecation and its removal cannot share a release, and 1.x is
  additive-only, so a boolean still live at the freeze stays live for the whole
  1.x line. That sequencing constraint is why 0.10.0 exists as a milestone.
- **Reserved surfaces** (`@Derived` / `@Rule` / `@SagaTransition` /
  `@EventHandler` / `@Projection` operational attrs, `@Action.realTimeUpdates`,
  system/security field markers, `@ExerisDomain.validationMode`) — frozen as
  declared; extraction/generation lands additively in 1.x.
- **`@Blob` / `@Schedule` / `@RouteAccess` / `@Channel` and their AST carriers are
  the one exception: NOT frozen**
  ([ADR-072](docs/adr/ADR-072-kernel-preview-spi-reserved-surface.md), as
  amended 2026-08-28 and 2026-09-03). Every other reserved surface above is frozen
  as declared, because what it waits on is downstream work against a settled
  premise. These four encode kernel SPI surfaces the kernel itself holds at tier
  **`preview`** in its `docs/stability-matrix.md`. Freezing an SDK annotation
  against that would make the SDK's 1.0 promise stronger than the surface it
  describes, which is the wrong way round. Route authorization is the clearest
  case: the kernel moved it twice inside one minor.

  The four are not `preview` for the same reason, and the difference is worth
  carrying because it decides what may still be built against them. Blob,
  scheduling and route authorization are `preview` with their shape still open.
  WebSocket is not: kernel ADR-084 §10 gates promotion on benchmark evidence a TCK
  structurally cannot supply — concurrent connections, throughput, a slow reader, a
  dead peer — so what is unproven there is durability under load. That still bars
  freezing (the kernel may yet change the contract, and a 1.0 promise over it would
  invert the ordering), which is why `@Channel` is on this list; it does not bar
  shaping a design-time carrier against a settled contract, which is why the
  surface exists at all.

  So `@Blob`, `@Schedule`, `@RouteAccess`, `@Channel`, `FieldMetadata.blob`,
  `ActionMetadata.schedule`, `DomainMetadata.routeAccess`,
  `ActionMetadata.routeAccess` and `DomainMetadata.channel` may be changed, or
  dropped, in a 1.x minor. They
  are promoted into the frozen surface when the kernel moves each surface to
  `stable` **and** the `exeris-tooling` transcription exists — at which point
  this bullet moves up into the list above. Consumers should treat them as
  preview and pin exactly.

## 2b. `@exeris/ui-kit` is outside this freeze, and has its own

The 1.0.0 contract in this document is the **Java** surface: annotations, AST records, the
parser/writer, the composition modules. The npm package `exeris-sdk-ui-kit` is not part of it and
does not freeze with it.

It is at `0.1.0` today against a `0.12.0` Java line, and the precedent is already in the
ecosystem: `@exeris/codegen-ts` runs at `0.2.0` against `exeris-tooling`'s `0.8.0`. The two kinds
of contract fail differently — a Java break is a compile error in a consumer's build, a CSS break
is a visual regression — and binding a design system's release cadence to a compilation
contract's would hold back the half Studio and a headless CMS drive hardest.

**What the GA list requires of it is publication, not a version.** `@exeris/ui-kit` reaching the
public registry is the GA item; the number on it is that package's own business. It is published
there from 0.12.0 on, at its own `0.1.0`.

**Its own 1.0, when it comes, freezes names and not values:**

- **Frozen:** every `--exeris-*` custom property, every `.exeris-*` class in the
  `ComponentType` map, and every Tailwind key that produces a utility. These appear in generated
  components and in hand-written application markup; renaming one is a break for both.
- **Free:** the values behind those names. Colours, spacing, radii and shadows are the theming
  surface a CMS is meant to override — freezing them would freeze the wrong thing, and the drift
  tests already hold them *consistent* across the package's three artifacts without holding them
  *constant*.

Enforced by `tests/public-surface.txt`, a recorded list of the 126 names, gated the way
`annotation-surface.txt` gates the Java surface: additions are reported so they get recorded,
removals and renames fail.

## 3. Consumer contract recap (unchanged at 1.0.0 — restated for the freeze)

- **Jackson mapper posture** for reading SDK-emitted JSON:
  `FAIL_ON_NULL_FOR_PRIMITIVES = false` (Jackson 3 defaults it to `true`).
  Canonical reference: `AstJsonRoundTripTest` + the
  `eu.exeris.sdk.sourcemodel.ast` package-info.
- **Record growth stays legal after the freeze, and breaks no caller.** An AST
  record may gain a **trailing** component in a 1.x minor (by-name on the wire,
  absent reads back `null`, existing components unchanged in order), accompanied
  by a `SchemaVersion` bump. The record **keeps its previous arity as a public
  constructor** that delegates to the new canonical one with `null` for what was
  added. `DomainEventMetadata` has done this since EV1; 0.12.0 made it the rule
  (Stellar finding S6). So neither kind of caller breaks: a builder or factory
  caller never saw the constructor, and a positional caller, in source or in a
  compiled class, finds the shape it was built against. Every constructor a
  record has published is part of the frozen surface. They accumulate across
  1.x, roughly one per growth, and can only be pruned at 2.0.

  Builders and `simple(...)` / `of(...)` factories remain the recommended path,
  for readability rather than survival: they name what a positional call leaves
  to argument order. That matters most on a record with a run of same-typed
  components, which is why `SystemFieldsMetadata`, eleven `String`s, gained a
  builder in 0.12.0. Not every record has one — 26 `ast` records have no builder
  or factory that reaches every component — and under this rule none needs one
  to be safe.

  **Why this has to be policy and not a footnote:** every surface the SDK
  expects to grow after 1.0 — the blob and job facets kernel 0.11 opened, flow
  await, graph multi-hop — arrives as a new component on an existing record.
  If a trailing component counted as a break, all of it would wait for 2.0.

  **How it is enforced.** japicmp on its plugin defaults (`-Psemver`, see §4),
  which is what the rule makes possible: growth emits only `CONSTRUCTOR_ADDED` and
  `METHOD_ADDED`, while removing a constructor, or removing, renaming or retyping a
  component, fails the build. In every build, with or without that profile,
  `RecordConstructorLedgerTest` fails if a published arity loses its constructor
  or a growth goes unrecorded, and `RecordComponentOrderTest` pins the one change
  no signature shows, a reorder of same-typed components.

  **What this replaced, and why.** Through 0.11 the rule was that positional
  callers "recompile with one added trailing argument; that recompile is the
  accepted cost", with builders and factories named as the stable path, and
  `source-model`'s japicmp configuration labelled `CONSTRUCTOR_REMOVED` binary-
  and source-compatible so that growth could pass. The label was false: removing
  a public constructor is a `NoSuchMethodError` for every class compiled against
  it (JLS 13.4.12). The stable path was missing too — eight `ast` records had no
  builder or factory that takes any value at all. `exeris-tooling`'s processor
  builds `SystemFieldsMetadata` positionally because nothing else could set its
  names, and it stopped compiling against 0.12.0 (Stellar finding S6). It runs on
  each consumer's processor path, linked against whatever SDK version Maven
  resolves there, so under the old rule a newer SDK minor meant a
  `NoSuchMethodError` inside javac.

  **The one exception is a major.** Removing a component from the middle of a
  record changes every constructor it has. 1.0.0 does that to `DomainMetadata`
  (§1), and its constructor history restarts from the 1.0.0 shape.
- **There is no `internal/` package, and everything public is contract.** Said
  plainly because the phrase "everything not in `internal/` is contract" invites
  the assumption that some escape hatch exists. None does, and none is planned:
  every module here is a deliberate API surface, so there is no implementation
  detail for one to hold. Practical consequence for a consumer: if it is
  `public` in a publishable module, 1.0.0 freezes it — with the single stated
  exception in §2 (`@Blob` / `@Schedule` / `@RouteAccess` / `@Channel` and
  their AST carriers).
- **`SchemaVersion` names the wire shape**, decoupled from the artifact
  version; a baseline stamped with an older schema reads as
  `NO_BASELINE(SCHEMA_VERSION_SKEW)` — re-run codegen once after upgrading.
- **`cap-manifest.json` `schemaVersion` = 2** (composition surface) — stable
  across the 0.9.0 conductor addition; manifests without `lifecycleOwner`
  boot hook-less.

## 4. Pre-freeze review backlog (must be resolved before the 1.0.0 cut)

Seeded from the 0.9.0 deprecation sweep; each item either lands before the
freeze or is explicitly re-dispositioned here.

- [x] **`@ExerisDomain(name = …)` reconciliation** — **dropped, not adopted.**
  The `-io` reader read a `name` attribute `@ExerisDomain` does not declare
  (JavaParser reads source text unvalidated, so nothing ever failed), and its
  javadoc called it "the canonical entity name *the processor uses*, which may
  differ from the Java class name". The processor does no such thing: it takes
  `element.getSimpleName()` and has no override at all. So the reader could
  return a different identity than the processor for the same source — the one
  disagreement ADR-042 reader↔processor parity cannot tolerate.

  Adding the attribute was the other option and was rejected: it is new public
  API immediately before the freeze, inert until `exeris-tooling` honours it,
  and it argues against Entity-First, where the class *is* the identity.

  Scope was wider than the item implied — 52 usages, including all five
  budgetHQ corpus files, which are documented as ported from real entities and
  would not have compiled against the actual annotation. Four usages declared a
  name differing from the class; one (`entityNameComesFromAnnotationNotClassName`)
  existed only to pin the fiction and is gone, while three used the divergence
  as a discriminator to prove some *other* fallback resolves to the class name.
  Those keep their assertions but lose the discriminator, because after this
  change there is no second candidate to distinguish from — their comments say
  so rather than claiming a distinction that no longer exists.
- [~] **japicmp/revapi semver gate** — **configured (0.10.0)**, covering all
  six non-annotation publishable modules (opt-in through `-Psemver` since
  0.12.0, see below) and **strict by default**. As configured in 0.10.0 it encoded the record-growth stance of the
  time by downgrading `CONSTRUCTOR_REMOVED` to a MINOR-level compatible change
  where records live, since that is the signal a trailing component produced.
  **Amended 2026-09-26 (Stellar finding S6):** the stance changed (§3), and with
  it the configuration:
  - `exeris-sdk-source-model` — **strict, no relaxation.** A record that grows
    keeps its previous arity as a delegating constructor, so growth removes
    nothing and the plugin defaults accept it. Until 0.12.0 this module carried
    the downgrade module-wide.
  - `exeris-sdk-source-model-io` (`ApplyResult`) and
    `exeris-sdk-composition-spec` (`CapManifest`) — still two executions each,
    the relaxed one naming the single record and the strict one excluding it.
    Neither record has grown since; whether they move to the §3 rule is open
    (`ROADMAP.md`, 0.12.0).
  - `exeris-sdk-composition-lifecycle` / `-runtime` — strict, no relaxation.
    They have no public records at all, and do have exception classes with
    meaningful constructor overloads, so a blanket relaxation would have
    retired a real safety net in exchange for nothing.

  japicmp 0.23.1's `overrideCompatibilityChangeParameter` carries no class
  pattern, which is why the scoping is expressed as execution-level
  `includes`/`excludes` rather than inside the override itself.

  Two follow-ons, both deliberate:
  - **The gate is opt-in: `mvn -Psemver verify`** (amended 2026-09-26). All of
    its configuration and both bindings live in one `semver` profile in the
    root pom; a maintainer runs it before a release, and the default build
    runs only the baseline-free guards (`AnnotationSurfaceContractTest`,
    `RecordComponentOrderTest`, `RecordConstructorLedgerTest`). Until then it
    was bound to `verify`, so every workflow and every fresh clone had to pass
    `-Djapicmp.skip=true`: the baseline is the last released jar, `0.11.0`,
    and no release at or below it was ever published to Central. Inside the
    profile an absent baseline still fails the build rather than passing
    quietly. CI adds `-Psemver` to `build.yml` and `release.yml` when the
    0.13.0 line opens against a Central-resolvable `0.12.0` baseline — the
    first version that can be one — and that edit is what starts 1.x binary
    enforcement. Turning Central on and giving the gate something to resolve
    were two changes, not one (corrected 2026-09-03).
  - **The annotations module runs no japicmp at all.** `@Retention(SOURCE)`
    means no runtime presence in a consumer image, and japicmp reports a new
    annotation element as `METHOD_ABSTRACT_ADDED_TO_CLASS` whether or not it
    declares a `default` — which is the entire compatibility question there.
    `AnnotationSurfaceContractTest` gates it instead.

  What the gate cannot see is a same-arity, same-type component **reorder** —
  invisible by construction, because swapping two `String` components leaves
  every accessor and every constructor descriptor exactly as it was.
  `RecordComponentOrderTest` pins component order against a snapshot and closes
  it; `RecordConstructorLedgerTest` holds the constructor half of §3 until the
  gate has a baseline. Removing any of the three leaves the stance unenforced.
  *(Corrected 2026-09-26: this said the reorder was invisible "since the override
  stops reading exactly the constructor signal a reorder would show up in". That
  held only for a reorder across different types; the same-type case is invisible
  with or without the override, which is gone.)*
- [x] **Public API surface review** — **done**; 103 public top-level types, the
  full result is in `ROADMAP.md` under 1.0.0 GA. The premise as seeded
  ("everything not in `internal/` is contract") describes a partition that does
  not exist — there is no `internal/` package anywhere in the repo, and there
  should not be. Each of the three seeded inputs, checked against the sources on
  both sides of the build-time hand-off rather than assumed:
  - **Never-populated AST components — three seeded, each with a different
    disposition; the full recount follows them.** `ActionMetadata.realTimeUpdates` is **reserved and frozen as
    declared**: the processor declines it by name ("deliberately NOT extracted
    here … extracting it would only create an inert `ActionMetadata` attribute")
    and the extraction lands with its consumer. `ActionMetadata.schedule` is
    **excluded from the freeze** per ADR-072, alongside `FieldMetadata.blob`.
    `InternalApiMetadata` is the one that needed a decision: **six of its seven
    components are never populated by either path.** The processor
    (`extractInternalApiMetadata`) and the `-io` reader
    (`SourceModelReader.java:794`) both map the *presence* of `@InternalApi` to
    `internal = true` and leave `hidden` / `readOnly` / `reason` / `since` /
    `disabledActions` / `allowedRoles` at their defaults — deliberately, and
    documented on both sides. Frozen as declared: the record is written on the
    wire today, so unlike `ValidationMetadata` (removed outright in 0.9.0, ADR-054)
    there is a live producer, and narrowing it to the one populated component
    would be a wire-format break bought for nothing.

    **Recounted 2026-09-26** against `exeris-tooling` `main` and the `-io`
    reader. "Three" was the seeded list, never a census, and 0.12.0 added more.
    Components that *neither* producer ever sets, on the four records at the top
    of the wire:
    - `DomainMetadata` — 8 of 41: `tags`, `roles`, `permissions`,
      `projections`, `eventHandlers`, `rules`, `routeAccess`, `channel`.
    - `ActionMetadata` — 9 of 18: `resultType`, `idempotent`, `dangerous`,
      `requiresConfirmation`, `permissions`, `producesEvents`,
      `realTimeUpdates`, `schedule`, `routeAccess`.
    - `FieldMetadata` — 10 of 31: `columnName`, `audited`, `hidden`,
      `defaultValue`, `format`, `enumType`, `displayNameKey`, `descriptionKey`,
      `derived`, `blob`.
    - `SystemFieldsMetadata` — 0 of 11. (`primaryKeyField` is populated and
      read by no generator, which is a different gap.)

    That is 27. Five are the ADR-072 exception §2 already excludes from the
    freeze (`FieldMetadata.blob`, `ActionMetadata.schedule`, both `routeAccess`,
    `DomainMetadata.channel`); the other 22 are frozen as declared under §3's
    rule that everything public is contract. Whole records no producer
    populates — `EventHandlerMetadata`, `ProjectionMetadata`, `DerivedMetadata`,
    `RuleMetadata`, `SagaMetadata.SagaTransition`, `GraphPropertyMetadata`,
    `GraphQueryMetadata`, `BlobMetadata`, `ScheduleMetadata`, `ChannelMetadata`
    — are counted as records rather than components and follow the same two
    rules. Not counted: components one producer sets and the other does not
    (`ActionMetadata.displayName`). That is an ADR-042 parity question, not a
    population one.
  - **`@InternalApi`'s five attributes are inert — and the name is a collision,
    not a drift.** `@InternalApi` declares a service-to-service *call policy*
    (`consumers`, `rateLimit`, `requireMtls`, `timeout`, `documented`);
    `InternalApiMetadata` carries entity *visibility and access control*
    (`hidden`, `readOnly`, `internal`, `reason`, `since`, `disabledActions`,
    `allowedRoles`). The overlap is **zero** — two unrelated features that
    collided on a name during the 0.1.0 scaffolding, which is why only presence
    crosses the hand-off. Both frozen as declared; the collision is a naming
    wart, and renaming either side at the freeze would break the `exeris-tooling`
    import for a cosmetic gain. `@InternalApi` is also `@Target({METHOD, TYPE})`
    while only the type-level case has an AST home — a method-level
    `@InternalApi`, which is what the annotation's own usage example shows, has
    no representation at all, since `ActionMetadata` carries no `internalApi`
    component. That gap is real and stays open by choice: closing it means
    designing the action-level shape, which is a feature, not a freeze chore.
  - **What the review *found* here, and fixed:** `DomainMetadata.isInternal()`
    read `internalApi.hidden()` — a component neither extraction path ever sets.
    It therefore returned `false` for every `@InternalApi` entity on the
    build-time path, and `false` even for `InternalApiMetadata.internal(…)`, the
    factory named after it. It now reads `internal()`. A predicate that is
    `false` by construction is not a contract worth freezing. Consumer note in
    `MIGRATION.md` under 0.10.x → 0.11.x.
  - **Graph sub-annotations — the seeded premise was stale.** `@GraphEdge` /
    `@GraphProperty` / `@GraphQuery` **do** have AST twins now
    (`GraphEdgeMetadata` / `GraphPropertyMetadata` / `GraphQueryMetadata`, all
    carried by `GraphMetadata`). Both the processor and the `-io` reader read
    `@GraphEdge` into `edges`; neither reads `@GraphProperty` or `@GraphQuery`,
    so `GraphMetadata` arrives with `properties = null` and empty `queries` —
    symmetrically, on both sides, which makes it an ADR-042 parity pair rather
    than a divergence. `@QueryParam` is the one with no AST twin:
    it binds parameters of a `@GraphQuery` method, and its twin belongs to the
    change that starts extracting `@GraphQuery` — an `exeris-tooling` slice, same
    lockstep shape as the entry below, not SDK work now.
- [x] **`@since 1.0.0` on annotations that shipped in 0.1.0** — **fixed.** All
  35 files rewritten, each to the version its own history says, not to a
  blanket value:
  - **34 → `0.1.0`.** Every one was added by `1fef6c5`, whose subject is
    literally *"init: exeris-sdk v0.1.0-SNAPSHOT skeleton (#1)"*. The strongest
    check is a sibling: `ExerisDomain` came from that same commit and has
    carried an accurate `@since 0.1.0` all along, so the batch was already
    self-contradicting.
  - **1 → `0.9.0`.** `annotation/system/package-info.java` was added by
    `ac4bc63` on 2026-07-22 — the 0.9.0 release date, in the 0.9.0
    deprecation-sweep commit.

  `@version 1.0.0` moved with it on the same 35 files. It is kept rather than
  dropped because this module already uses `@version` as "version at
  introduction" (its 0.4.0 files still read `0.4.0` at 0.11.0), and inventing a
  second convention mid-cleanup would be worse than following the one that is
  there. Nothing else in the reactor was affected: the other six modules were
  checked and carry **zero** files stamped `@since 1.0.0`.

  **No guard added, deliberately.** The failure mode was one-time scaffold
  boilerplate claiming a version that did not exist yet. Once 1.0.0 ships,
  `@since 1.0.0` becomes a legitimate stamp for anything new, so a check for
  that string decays into noise on the exact release it would first matter.
- [~] **Tooling lockstep debt at the freeze** — re-dispositioned: **nothing here
  is SDK work, now or at the freeze.** Both halves were checked against
  `exeris-tooling` `main` rather than assumed.
  - `@Relationship.relationshipType` extraction bug — **fixed downstream.** The
    processor read annotation key `"type"` (the AST's name for it) while the
    annotation declares `relationshipType`, so every relationship carried the
    builder default `MANY_TO_ONE` and a `ONE_TO_MANY`/`MANY_TO_MANY` side was
    emitting an FK column, its index, its constraint and a finder that belong on
    the other side. `ExerisDomainProcessor` now reads the declared key. The SDK
    surface was correct throughout — annotation, `-io` reader and `-io` writer
    all used `relationshipType`.
  - The reserved-surface flips (`@SagaTransition`, `@Derived`/`@Rule`,
    `@EventHandler`, `@Projection`) — **still pending, correctly.** The
    processor extracts none of them today, so no `-io` reader flip is owed: per
    ADR-042 the reader reads what the processor writes, and reading ahead of it
    would manufacture drift. Each pairs with its processor slice as it comes
    live, which is the lockstep, not a debt.

  One item of this shape *is* still live and is deliberately not an SDK issue:
  `@ActionParam.label` is read by the processor under `"displayName"`, a key the
  annotation does not declare, so `ActionParamMetadata.displayName` is `null` on
  every processor run and `effectiveDisplayName()`'s fallback to `name` masks it
  completely. Same shape as `relationshipType`, same file, and the `-io` reader
  maps `label` correctly — so it is a live processor/reader divergence owned by
  `exeris-tooling`. Tracked in `ROADMAP.md`; the SDK surface is correct as
  declared and there is nothing to change here.
- [x] **`exeris-sdk-tck`** — **landed in 0.11.0.** The "scope TBD" resolved to
  the build-time metadata hand-off rather than to the capability lifecycle: four
  abstract suites (producer, reader, parity, consumer mapper posture) that a
  binder extends. The parity suite turns ADR-042's reader↔processor discipline
  into an executable gate, which is the half of the SDK's contract that has
  actually been broken — three times, each time silently, because both sides
  kept emitting well-formed metadata.

  For consumers the module is additive and optional: test scope, nothing on a
  runtime classpath, and the surface it publishes freezes at 1.0.0 like any
  other publishable module. The semver gate covers it from 0.12.0, against the
  0.11.0 release it first shipped in.

## 5. TBD at the 1.0.0 release PR

- Final attribute-by-attribute diff 0.9.x → 1.0.0 (expected: the four §1
  removals only — `@Validation.required`, `@Validation.validateOn`,
  `@ExerisDomain.tenantScoped` and `@ExerisDomain.apiVersion`, with the
  `DomainMetadata.tenantScoped` and `DomainMetadata.apiVersion` components that
  go with the last two).
- Consumer validation pass results (`exeris-tooling`, `exeris-platform-lsp`),
  and one dated line for the Stellar Tactics author-side pass, recorded to the
  definition in the status note at the top.
- npm `@exeris/ui-kit` public-registry publish notes (GA item).
