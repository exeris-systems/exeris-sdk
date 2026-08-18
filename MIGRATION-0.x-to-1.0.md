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
- **`@Blob` / `@Schedule` and their AST carriers are the one exception: NOT
  frozen** ([ADR-072](docs/adr/ADR-072-kernel-preview-spi-reserved-surface.md)).
  Every other reserved surface above is frozen as declared, because what it
  waits on is downstream work against a settled premise. These two encode kernel
  SPI packages the kernel itself holds at tier **`preview`** in its
  `docs/stability-matrix.md` — a shape with a scheduled change still in flight.
  Freezing an SDK annotation against that would make the SDK's 1.0 promise
  stronger than the surface it describes, which is the wrong way round.

  So `@Blob`, `@Schedule`, `FieldMetadata.blob` and `ActionMetadata.schedule`
  may be changed, or dropped, in a 1.x minor. They are promoted into the frozen
  surface when the kernel moves each package to `stable` **and** the
  `exeris-tooling` transcription exists — at which point this bullet moves up
  into the list above. Consumers should treat them as preview and pin exactly.

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
    is explicit in the workflow rather than implicit in the plugin. Drop the
    flag when the Central wiring lands.
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
- [ ] **Public API surface review** — everything not in `internal/` is
  contract. Known inputs from the sweep: `ActionMetadata` /
  `InternalApiMetadata` carry components the processor never populates
  (reserved AST surface — confirm each is wanted in the frozen contract);
  `@InternalApi`'s five attributes are inert (documented SDK↔AST drift);
  graph sub-annotations (`@GraphEdge` / `@GraphProperty` / `@GraphQuery`) and
  `@QueryParam` are unextracted with no AST twin.
- [ ] **Tooling lockstep debt at the freeze** — `@Relationship.relationshipType`
  extraction bug (processor reads key `"type"`); the reserved-surface
  extraction flips (`@SagaTransition`, `@Derived`/`@Rule`, `@EventHandler`,
  `@Projection`) each need their coordinated processor + `-io` reader flip
  (ADR-042) as they come live.
- [ ] **`exeris-sdk-tck`** — contract test suite shippable to downstream
  consumers (ROADMAP 1.0.0 GA item; scope TBD).

## 5. TBD at the 1.0.0 release PR

- Final attribute-by-attribute diff 0.9.x → 1.0.0 (expected: the two
  `@Validation` removals only).
- budgetHQ validation pass results.
- npm `@exeris/ui-kit` public-registry publish notes (GA item).
