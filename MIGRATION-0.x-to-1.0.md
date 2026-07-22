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

*(The 0.9.0 final deprecation sweep closed with zero additions to this list —
see the sweep disposition in [`ROADMAP.md`](ROADMAP.md). `ValidationMetadata`
was removed outright in 0.9.0, ADR-054, and is not a 1.0.0 item.)*

## 2. What is deliberately NOT removed (carried through 1.x)

- **`@UI`** — stays the functional field-level presentation path. The `@View`
  structural generation is live, but the ADR-047 leaf-field facet is not
  implemented yet, so the deprecation gate never opened in 0.x. Plan of
  record: facet lands in a 1.x minor → `@UI` gains `@Deprecated(forRemoval)`
  there → removal at 2.0.
- **`@ExerisDomain.tenantScoped`** — frozen as a boolean through 1.x; the
  `DataScope { GLOBAL, TENANT, UNIVERSE }` successor is gated on the kernel
  ADR-012 amendment and arrives additively in 1.x.
- **Reserved surfaces** (`@Derived` / `@Rule` / `@SagaTransition` /
  `@EventHandler` / `@Projection` operational attrs, `@Action.realTimeUpdates`,
  system/security field markers, `@ExerisDomain.validationMode`) — frozen as
  declared; extraction/generation lands additively in 1.x.

## 3. Consumer contract recap (unchanged at 1.0.0 — restated for the freeze)

- **Jackson mapper posture** for reading SDK-emitted JSON:
  `FAIL_ON_NULL_FOR_PRIMITIVES = false` (Jackson 3 defaults it to `true`).
  Canonical reference: `AstJsonRoundTripTest` + the
  `eu.exeris.sdk.sourcemodel.ast` package-info.
- **Builders/factories over positional constructors** — canonical (all-args)
  record constructors grow across 0.x minors; builders and `simple(...)` /
  `of(...)` factories are the stable path and remain so in 1.x.
- **`SchemaVersion` names the wire shape**, decoupled from the artifact
  version; a baseline stamped with an older schema reads as
  `NO_BASELINE(SCHEMA_VERSION_SKEW)` — re-run codegen once after upgrading.
- **`cap-manifest.json` `schemaVersion` = 2** (composition surface) — stable
  across the 0.9.0 conductor addition; manifests without `lifecycleOwner`
  boot hook-less.

## 4. Pre-freeze review backlog (must be resolved before the 1.0.0 cut)

Seeded from the 0.9.0 deprecation sweep; each item either lands before the
freeze or is explicitly re-dispositioned here.

- [ ] **`@ExerisDomain(name = …)` reconciliation** — the `-io` reader and its
  tests read a `name` attribute the annotation does not declare (JavaParser
  reads source text unvalidated). Drop it from the reader/tests or add the
  attribute deliberately.
- [ ] **japicmp/revapi semver gate** — needs a resolvable baseline artifact;
  gated on the Central publish wiring (parked branch). Wire it before 1.0.0 so
  1.x enforcement starts at GA.
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
