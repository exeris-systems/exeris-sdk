# Migration guide: 0.x → 1.0.0 (skeleton)

> **Status: skeleton, seeded during the 0.9.0 cycle.** 1.0.0 GA freezes the
> public API surface (1.x minors are additive-only, patches bug-fix only —
> see [`ROADMAP.md`](ROADMAP.md) "Versioning policy"). This document collects,
> ahead of time, everything a 0.x consumer must do to cross the freeze. It is
> to be **validated against budgetHQ** before 1.0.0 ships (ROADMAP 1.0.0 GA
> item) and finalized in the 1.0.0 release PR. Per-0.x-step upgrade notes stay
> in [`MIGRATION.md`](MIGRATION.md).

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
- **`@Blob` / `@Schedule` / `@RouteAccess` and their AST carriers are the one
  exception: NOT frozen**
  ([ADR-072](docs/adr/ADR-072-kernel-preview-spi-reserved-surface.md), as
  amended 2026-08-28). Every other reserved surface above is frozen as declared,
  because what it waits on is downstream work against a settled premise. These
  three encode kernel SPI surfaces the kernel itself holds at tier **`preview`**
  in its `docs/stability-matrix.md` — a shape with a scheduled change still in
  flight. Freezing an SDK annotation against that would make the SDK's 1.0
  promise stronger than the surface it describes, which is the wrong way round.
  Route authorization is the clearest case: the kernel moved it twice inside one
  minor.

  So `@Blob`, `@Schedule`, `@RouteAccess`, `FieldMetadata.blob`,
  `ActionMetadata.schedule`, `DomainMetadata.routeAccess` and
  `ActionMetadata.routeAccess` may be changed, or dropped, in a 1.x minor. They
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
public registry is the GA item; the number on it is that package's own business.

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
- **Builders/factories over positional constructors** — canonical (all-args)
  record constructors grow across 0.x minors; builders and `simple(...)` /
  `of(...)` factories are the stable path and remain so in 1.x.
- **Record growth stays legal after the freeze — the canonical constructor is
  not the frozen surface.** Stated explicitly because the bullet above only
  implies it, and because it is load-bearing: an AST record may gain a
  **trailing** component in a 1.x minor (by-name on the wire, absent reads back
  `null`, existing positional prefixes unchanged in order), accompanied by a
  `SchemaVersion` bump. Positional callers recompile with one added trailing
  argument; that recompile is the accepted cost, and it is why builders are
  documented as the stable path. Consumers that must survive a minor without
  recompiling should construct through builders and factories exclusively.

  **Why this has to be policy and not a footnote:** every surface the SDK
  expects to grow after 1.0 — the blob and job facets kernel 0.11 opened, flow
  await, graph multi-hop — arrives as a new component on an existing record.
  If a trailing component counts as a break, all of it is blocked until 2.0.
  The 1.0.0 GA japicmp/revapi gate must be configured to encode this
  distinction (trailing-additive = allowed; reorder, rename, retype, or remove
  = break). A gate configured on defaults will reject exactly the growth this
  paragraph permits — see the ROADMAP 1.0.0 GA item.
- **There is no `internal/` package, and everything public is contract.** Said
  plainly because the phrase "everything not in `internal/` is contract" invites
  the assumption that some escape hatch exists. None does, and none is planned:
  every module here is a deliberate API surface, so there is no implementation
  detail for one to hold. Practical consequence for a consumer: if it is
  `public` in a publishable module, 1.0.0 freezes it — with the single stated
  exception in §2 (`@Blob` / `@Schedule` and their AST carriers).
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
- [~] **japicmp/revapi semver gate** — **configured (0.10.0)**, encoding the
  record-growth stance in §3: `CONSTRUCTOR_REMOVED` is downgraded to a
  MINOR-level compatible change, because that is the signal a trailing record
  component produces, while removals, renames and retypes still break the build
  through their accessors. Bound to `verify` in all five non-annotation
  publishable modules — **strict by default**, with the relaxation declared
  only where records actually live:
  - `exeris-sdk-source-model` — module-wide, because `ast` + `mutation` *is*
    the record surface end to end.
  - `exeris-sdk-source-model-io` (`ApplyResult`) and
    `exeris-sdk-composition-spec` (`CapManifest`) — two executions each, the
    relaxed one naming the single record and the strict one excluding it.
  - `exeris-sdk-composition-lifecycle` / `-runtime` — strict, no relaxation.
    They have no public records at all, and do have exception classes with
    meaningful constructor overloads, so a blanket relaxation would have
    retired a real safety net in exchange for nothing.

  japicmp 0.23.1's `overrideCompatibilityChangeParameter` carries no class
  pattern, which is why the scoping is expressed as execution-level
  `includes`/`excludes` rather than inside the override itself.

  Two follow-ons, both deliberate:
  - **CI runs it skipped** (`-Djapicmp.skip=true`) because the baseline is the
    last released jar and no `eu.exeris` artifact is published anywhere yet.
    An absent baseline is configured to fail, not to pass quietly, so the skip
    is explicit in the workflow rather than implicit in the plugin.
    **Corrected 2026-09-03:** this used to say the flag comes off "when the
    Central wiring lands". It does not, and the difference is one milestone.
    The wiring landed in 0.12.0, and 0.12.0 is the *first* version to reach
    Central — so the baseline japicmp names, `0.11.0`, predates the move and is
    not resolvable there either. The flag comes off when the 0.13.0 line opens
    against a Central-resolvable `0.12.0` baseline. Turning Central on and
    giving the gate something to resolve are two changes, not one.
  - **The annotations module runs no japicmp at all.** `@Retention(SOURCE)`
    means no runtime presence in a consumer image, and japicmp reports a new
    annotation element as `METHOD_ABSTRACT_ADDED_TO_CLASS` whether or not it
    declares a `default` — which is the entire compatibility question there.
    `AnnotationSurfaceContractTest` gates it instead.

  What the gate cannot see is a same-arity, same-type component **reorder** —
  invisible by construction, since the override stops reading exactly the
  constructor signal a reorder would show up in. `RecordComponentOrderTest`
  pins component order against a snapshot and closes it. The two are a pair;
  removing either leaves the stance unenforced.
- [x] **Public API surface review** — **done**; 103 public top-level types, the
  full result is in `ROADMAP.md` under 1.0.0 GA. The premise as seeded
  ("everything not in `internal/` is contract") describes a partition that does
  not exist — there is no `internal/` package anywhere in the repo, and there
  should not be. Each of the three seeded inputs, checked against the sources on
  both sides of the build-time hand-off rather than assumed:
  - **Never-populated AST components — three, each with a different
    disposition.** `ActionMetadata.realTimeUpdates` is **reserved and frozen as
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
    carried by `GraphMetadata`). Neither the processor nor the `-io` reader reads
    the member annotations, so `GraphMetadata` arrives with `properties = null`
    and empty `edges` / `queries` — but symmetrically, on both sides, which makes
    it an ADR-042 parity pair rather than a divergence, and the reader says so
    (`SourceModelReader.java:704`). `@QueryParam` is the one with no AST twin:
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
  other publishable module. `japicmp` is skipped there until a baseline artifact
  exists, since none was released before the module did.

## 5. TBD at the 1.0.0 release PR

- Final attribute-by-attribute diff 0.9.x → 1.0.0 (expected: the two
  `@Validation` removals only).
- budgetHQ validation pass results.
- npm `@exeris/ui-kit` public-registry publish notes (GA item).
