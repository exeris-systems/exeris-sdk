# Migration guide

This document tracks user-visible changes between Exeris SDK versions and
the upgrade steps required.

> **Versioning policy.** The SDK is `0.x` until 1.0.0 GA. Breaking changes
> may land in any 0.x release; downstream consumers pin exact versions.
> Once 1.0.0 ships, semver applies (minor bumps additive only, patch bumps
> bug-fix only). See [`ROADMAP.md`](ROADMAP.md).

---

## 0.9.x → 0.10.x

### `@ExerisDomain.tenantScoped` is deprecated — use `dataScope`

**Why:** a boolean could express only two of the three data-scope tiers, so
`tenantScoped` did double duty for "not partitioned" and "tenant-private" and
had no room for the shared-world tier. The kernel now enforces that third tier
(ADR-012 §4b amendment on the kernel 0.11 line: a `sharedScopeKey` carrier,
a shared-scope claim with fail-closed mapping, and RLS that widens reads while
pinning writes to the owning tenant), which opened the build gate
RFC-2026-06-24 had been waiting on. See [ADR-059](docs/adr/ADR-059-data-scope-expression.md).

**Impact:** `@ExerisDomain.tenantScoped` is `@Deprecated(since = "0.10.0",
forRemoval = true)`; removal lands at **1.0.0**. The replacement is a single
mutually-exclusive discriminator:

```java
// before
@ExerisDomain(module = "sales", path = "/orders", tenantScoped = true)

// after
@ExerisDomain(module = "sales", path = "/orders", dataScope = DataScope.TENANT)
```

The mapping is `true → DataScope.TENANT`, `false → DataScope.GLOBAL`. The
third tier, `DataScope.UNIVERSE`, is new: rows owned by a tenant but readable
across tenants.

**Nothing breaks on upgrade.** `dataScope` defaults to `UNSPECIFIED`, and while
it is unspecified the tier falls back to `tenantScoped` — in the annotation
(the processor reads it as a fallback with a build warning) and in the AST
(`DomainMetadata.effectiveDataScope()` returns `tenantScoped ? TENANT :
GLOBAL`). Sources and baselines written before 0.10.0 keep the meaning they
always had. That fallback window closes at 1.0.0: after the freeze, a source
still setting only `tenantScoped` silently loses its tier.

**Do not set both** a `dataScope` tier and a contradicting `tenantScoped` —
the processor reports that as a build error rather than resolving it silently.

### JDK baseline moves to 25 LTS — a widening, no action required

**Why:** the kernel moved its distributable line to JDK 25 LTS with no preview
flags (kernel ADR-066), which left the SDK targeting a *higher* class-file major
than the runtime it describes. These jars are on your compile classpath, so that
gap was load-bearing: `javac` on JDK 25 rejects a major-70 class with "class file
has wrong version 70.0, should be 69.0". See
[ADR-069](docs/adr/ADR-069-jdk-baseline-lts.md).

**Impact:** none, unless you were blocked. Published jars now carry class-file
major **69** and impose no `--enable-preview`. A build on JDK 26 (or newer) keeps
working unchanged — this only widens what can consume the SDK.

**If you are on JDK 25 LTS:** you can now compile against `exeris-sdk-annotations`
and `exeris-sdk-source-model`. Note that `exeris-tooling`'s annotation processor
runs inside your own `javac` invocation and carries its own baseline; until it
follows, the annotations resolve but the code generator does not run on 25.

**If you are on JDK 21 LTS:** still below the baseline, unchanged from before.

### `DataScope.UNIVERSE` is reserved — declaring it has no generated effect yet

The kernel enforces the shared tier, but the `exeris-tooling` transcription
that maps `UNIVERSE` onto the kernel's `sharedScopeKey` carrier is not built.
`UNIVERSE` therefore ships with the same honesty note the streaming attributes
carried before ADR-043: the AST carries the author's intent, no generator acts
on it. `GLOBAL` and `TENANT` carry exactly the semantics `tenantScoped`
already carried and are live through the same path.

### `DomainMetadata` arity grew (trailing `dataScope`) + schema `"0.9.0"` → `"0.10.0"`

**Impact:** `DomainMetadata`'s positional constructor gained a **trailing**
`DataScope dataScope` component — same posture as the `ActionMetadata` growth
in 0.8.0 and `CapManifest.ModuleBody` in 0.9.0: existing positional prefixes
are unchanged in order, and positional callers add one trailing `null`. The
builder (`.dataScope(…)`) is the stable path and needs no change. By-name on
the wire — an old baseline reads the component back `null`, which is exactly
the state `effectiveDataScope()`'s fallback is written for.

`SchemaVersion.CURRENT` moves to `"0.10.0"`, so a baseline stamped `"0.9.0"`
reads as `NO_BASELINE(SCHEMA_VERSION_SKEW)` — re-run codegen once after
upgrading. This is the standard posture (refuse a cross-shape baseline rather
than assume compatibility), not a signal that anything is wrong.

**`-io` reader parity is deliberately deferred.** The reader reads what the
processor writes (ADR-042); the `exeris-tooling` processor does not extract
`dataScope` yet, so the reader would manufacture drift. Parity lands lockstep
with the processor. The reader's `unmodeledFacets()` guard is unaffected — it
keys on annotation types, not attributes.

---

## 0.8.x → 0.9.x

### Composition: no wire break

The 0.9.0 composition-lifecycle slice (`CapabilityLifecycleHooks` + the boot
conductor, ADR-024 obligations 8a/8a′) changes **no wire format**: the
cap-manifest `schemaVersion` stays **2** and the content binding is untouched
(`lifecycleOwner` is deliberately not binding-covered, like `initOrder` —
golden vectors unchanged). A manifest emitted before 0.9.0 (no
`lifecycleOwner` fields) boots as a hook-less composition: the conductor
asserts the stamp, finds no lifecycle owners, and reports ready. Nothing to
re-emit, nothing to re-deploy.

### `CapManifest.ModuleBody` arity grew (trailing `lifecycleOwner`)

**Why:** the boot conductor discovers each cap's hooks from the manifest, so
the consumer schema now binds the per-module-body `lifecycleOwner` the tooling
processor already emits (`NON_NULL`) instead of ignoring it.

**Impact:** `ModuleBody`'s positional constructor arity grew 1 → 2 with a
**trailing** `String lifecycleOwner` component — same posture as the
`ActionMetadata` trailing-component growth in 0.8.0: existing positional
prefixes are unchanged in order, and positional callers add one trailing
`null` (`new CapManifest.ModuleBody(provides)` →
`new CapManifest.ModuleBody(provides, null)`). Blank normalizes to `null` in
the compact constructor; absent/`null`/blank all mean "this cap has no
lifecycle hooks" (matching `@CapabilityLifecycle`'s zero-or-one cardinality).
By-name on the wire — an old manifest reads the component back `null`.

### Cap authors: new dependency coordinates for the lifecycle interface

Implement `eu.exeris.sdk.composition.lifecycle.CapabilityLifecycleHooks` from
the new **`exeris-sdk-composition-lifecycle`** jar (zero dependencies,
enforcer-proven). Do **not** depend on `exeris-sdk-composition-runtime` from
cap code — that jar is the SKU-boot side (stamp asserter + conductor) and
would drag `jackson-databind` onto your classpath; the conductor depends on
the lifecycle module, never the other way around. The pre-ADR-024 javadoc
claim that the lifecycle interface would be kernel-side was voided by the
2026-06-25 re-amendment and the annotation javadoc is corrected accordingly.
There is **no code migration**: the interface never existed before 0.9.0 —
`@CapabilityLifecycle`-annotated classes simply gain a real contract to
implement (public no-arg constructor required; default no-ops mean you
implement only the subset you need).

### `SchemaVersion.CURRENT` bumped `"0.8.0"` → `"0.9.0"`

**Why:** `FieldMetadata.min/max/minLength/maxLength` moved to per-component
`@JsonInclude(NON_NULL)`, so zero-valued bounds (e.g. `min = 0` non-negativity)
now survive serialization — previously the class-level
`@JsonInclude(NON_DEFAULT)` silently dropped boxed zero. The wire can express
states it could not before; the schema version names the shape (ADR-042
posture).

**Impact:** a baseline stamped `"schemaVersion": "0.8.0"` reads as
`NO_BASELINE(SCHEMA_VERSION_SKEW)`. Unlike prior bumps, the tooling processor
NOW stamps `schemaVersion` into `exeris-metadata/<entity>.json`, so 0.8.0
baselines exist in the wild — **re-run codegen once** after upgrading to emit
fresh `"0.9.0"` baselines. Consumer mapper posture unchanged:
`FAIL_ON_NULL_FOR_PRIMITIVES=false` still required.

### `min = 0` / `max = 0` / `minLength = 0` / `maxLength = 0` are now meaningful

The "avoid 0 as a meaningful bound" caveat is retired. Generators that
null-check bounds now see them: `@Validation(min = 0)` yields the DB
`CHECK (col >= 0)`, OpenAPI `minimum: 0`, and client validators. If you wrote
`0` expecting it to be ignored, remove the attribute.

### `ValidationMetadata` is removed — `FieldMetadata` is the canonical carrier

**Why:** the record was never populated by the processor or the `-io` reader,
never referenced by `DomainMetadata`, and never consumed by any generator; the
constraint values it mirrors live on `FieldMetadata`
(`minLength`/`maxLength`/`min`/`max`/`pattern`), populated from `@Validation`.
`notNull`/`notBlank` semantics derive from `FieldMetadata.required`;
`patternMessage` is dropped (no `@Validation` source, no consumer).

**Window:** removed outright in 0.9.0 — no deprecation cycle. The pipeline's
window exists for consumers that need to migrate, and none can exist here: no
processor ever wrote the record, no generator ever read it, and no SDK
artifact has ever been published to a registry, so there is no external
compile-time dependent (0.x permits the break). Any in-org compile-time
reference migrates to `FieldMetadata`. No `@Field` / `@Validation` source
change is required.

### `-io` reader parity: `@Validation.minLength` / `maxLength` (bugfix)

The reader now reads both into `FieldMetadata`, matching what the processor
has extracted all along; previously they were silently dropped on read,
causing spurious ADR-042 drift on any field declaring them. Read-side only;
nothing to migrate — sources using them now round-trip.

---

## 0.7.x → 0.8.x

### `SchemaVersion.CURRENT` bumped `"0.7.0"` → `"0.8.0"`

**Why:** the `ActionMetadata` streaming growth below is a JSON-affecting AST
shape change, and the baseline-trust schema version names the AST shape (see
`eu.exeris.sdk.sourcemodel.mutation.SchemaVersion`).

**Impact:** a baseline JSON stamped `"schemaVersion": "0.7.0"` now reads as
`NO_BASELINE(SCHEMA_VERSION_SKEW)` — same posture as the prior bumps. The
additions are by-name and back-compatible to *read*, but conflict detection
will not trust a stale-schema baseline; the impact is confined to the
baseline-trust check. In practice there is nothing to migrate yet: codegen does
not emit the trust fields until the tooling writer lands, so no `"0.7.0"`
baselines exist in the wild. **Re-run codegen** to emit a fresh `"0.8.0"`
baseline once that writer exists.

### `ActionMetadata` grew the per-action streaming fields

**Why:** the `@Action` annotation has declared `streaming` / `streamEventType`
/ `realTimeUpdates` since the early surface, but `ActionMetadata` carried none
of them, so the tooling per-action SSE stream emitter (RFC-2026-06-22, Slice 2)
had no metadata to extract. 0.8.0 adds the faithful AST twin, unblocking that
driver. (The entity-level `@ExerisDomain(realTimeApi)` driver was already
plumbed via `DomainMetadata.realTimeApi`.) See `docs/adr/ADR-043.link.md`.

- **New components** — three **trailing** components `streaming` (`boolean`),
  `streamEventType` (`String`), `realTimeUpdates` (`boolean`), appended after
  `methodName`. New builder setters (`.streaming(...)` / `.streamEventType(...)`
  / `.realTimeUpdates(...)`) and a `hasStreamEventType()` convenience. The
  compact constructor normalizes blank `streamEventType` → `null`.

**Impact:** both booleans and the string are **appended at the end** of the
record, so the all-args constructor *arity* grew from 13 → 16 while existing
positional prefixes are unchanged in order. Code calling `new ActionMetadata(…)`
**positionally** must add the three trailing defaults (`false, null, false` is
fine — they normalize). The `simple(name)` factory and `ActionMetadata.builder`
are the unaffected path. All additions are by-name on the wire (an old baseline
reads `streaming` / `realTimeUpdates` back `false` and `streamEventType` back
`null`). The processor extraction of `@Action(streaming)` + codegen consumption
landed in `exeris-tooling` (Slice 2, #106), and the `-io` reader reads the same
two attributes since 0.8.0 (see the parity section below); `realTimeUpdates` is
deliberately unextracted on both sides until it has a generator consumer.

### `DomainEventMetadata` grew resolved payload framing (EV1)

**Why:** the generated event story (kernel Event-Payload Codec SPI, ADR-046 /
the tooling EV1 payload pass) needs the *resolved* payload shape — which fields
an event actually carries — on the event's AST record, not recomputed by every
consumer.

- **New components** — two **trailing** `List<String>` components:
  `payloadFields` (resolved payload field *names*, in `includeFields` order when
  set: `@DomainEvent.includeFields` if non-empty, else all of the entity's
  `@Field` names, minus `excludeFields`) and `sensitiveFields` (the
  `@DomainEvent.sensitiveFields` names, verbatim). Field names, not
  `FieldMetadata` copies — the field definitions live once on
  `DomainMetadata.fields()`. Null lists normalize to empty (defensive copies);
  the resolution semantics are shared by the processor and the `-io` reader
  (ADR-042 lock-step).

**Impact:** the all-args constructor arity grew 4 → 6; positional callers add
two trailing `List.of()` (`null` also normalizes). The `simple(name)` /
`withTopic(name, topic)` factories and `builder(name)` are the unaffected path.
By-name on the wire — an old baseline reads both lists back empty.
`@DomainEvent.includeComputed` / `includePreviousValues` do not contribute yet
(no computed-field source in the persisted field list).

### `-io` reader parity: `@Field.dataType` + the per-action streaming driver

**Why:** ADR-042 conflict detection compares `read(currentSource)` against the
processor-emitted baseline, so the reader must read exactly what the processor
writes. 0.8.0 closes two coordinated flips: the `exeris-tooling` processor began
extracting `@Field.dataType` (UI-kit gap B5) and the per-action streaming driver
(`@Action.streaming` / `streamEventType`, tooling Slice 2), and the reader now
mirrors both. `@Action.realTimeUpdates` is deliberately unextracted on **both**
sides (no generator consumer yet) — parity holds by omission.

**Impact:** nothing to migrate — read-side only, additive. Sources using these
attributes now round-trip them; blank `streamEventType` / unset `dataType` stay
off the wire (null).

### Presentation / front model (`@View` / `ViewMetadata`) added — reserved

**Why:** RFC-2026-06-25 — a unified, framework-neutral presentation IR for views
composed beyond a single entity (the Headless CMS gap). New annotations
`@View` / `@Region` / `@Block` / `@Bind` and AST records `ViewMetadata` /
`RegionMetadata` / `ComponentNodeMetadata` / `BindingMetadata` (+ `ViewKind` /
`BlockType` / `BindSource`).

**Impact:** none on existing types. The records are net-new and standalone (not
referenced by `DomainMetadata`), so no existing wire shape changes and
`SchemaVersion` stays `"0.8.0"`. Nothing to migrate: the surface is **reserved**
— no processor/codegen/`-io` consumes it yet (parity, ADR-042); generation is the
`exeris-tooling` Angular 22 emitter, gated on that emitter + a Headless CMS corpus.

**`@UI` convergence (no action yet):** `@View`/`ViewMetadata` is the single
presentation model `@UI` is being absorbed into — entity-level view selection
becomes a `@View`; field-level render detail is reused as the leaf field facet of
`ComponentNodeMetadata` (the existing `UIMetadata.UIFieldMetadata` record).
`@UI` is **not** deprecated yet and keeps working unchanged; the formal
`@Deprecated(forRemoval)` migration (with `@View` as the replacement and a
processor fallback window) runs only once the emitter lands and `@View` can
actually replace it. No code change is required of `@UI` users today.

---

## 0.6.x → 0.7.x

### `SchemaVersion.CURRENT` bumped `"0.6.0"` → `"0.7.0"`

**Why:** the `ProjectionMetadata` and saga-state-machine growth below are
JSON-affecting AST shape changes, and the baseline-trust schema version names
the AST shape (see `eu.exeris.sdk.sourcemodel.mutation.SchemaVersion`). Both
land within the 0.7.0 release, so they share the single `"0.7.0"` schema (the
same batching as `DomainMetadata.eventHandlers` under 0.6.0) — no second bump.

**Impact:** a baseline JSON stamped `"schemaVersion": "0.6.0"` now reads as
`NO_BASELINE(SCHEMA_VERSION_SKEW)` — same posture as the 0.6.0 bump. The
additions are by-name and back-compatible to *read*, but conflict detection
will not trust a stale-schema baseline. In practice there is nothing to migrate
yet: codegen does not emit the trust fields until the tooling writer lands, so
no `"0.6.0"` baselines exist in the wild. **Re-run codegen** to emit a fresh
`"0.7.0"` baseline once that writer exists.

### `ProjectionMetadata` grew the source + read-model framing

**Why:** the record could say *what* a projection exposes (`fields`) but not
*what it is a view of*. 0.7.0 adds the source-aggregate link and the
event-subscription / read-model framing so "expose this subset of *this*
aggregate as a read-only view" is expressible.

- **New components** — `aggregateTypes`, `events`, `eventClassNames`,
  `topicPattern`, `model`, `schema` (alongside the existing `name`,
  `description`, `fields`, `cacheable`).
- **Reordered** — the components are grouped logically (identity → source →
  subscription → read model → exposed fields → caching), so the **canonical
  (all-args) constructor signature changed** in both arity and order.

**Impact:** code calling `new ProjectionMetadata(...)` **positionally** will no
longer compile. The `simple(name, fields)` factory is unchanged (still
non-cacheable, no source); a new `of(name, aggregateType, fields)` factory and a
`ProjectionMetadata.builder(name)` cover the common cases:

```diff
-ProjectionMetadata p = new ProjectionMetadata("OrderSummary", "desc", List.of("id"), true);
+ProjectionMetadata p = ProjectionMetadata.builder("OrderSummary")
+        .description("desc").aggregateType("Order").fields(List.of("id")).cacheable(true).build();
+// or .simple("OrderSummary", List.of("id")) / .of("OrderSummary", "Order", List.of("id"))
```

All additions are by-name on the wire (an old baseline reads back with the new
lists empty and the new strings `null`). The compact constructor normalizes
blank → `null` and null list → empty.

### Saga step `kind` + typed transitions

**Why:** `SagaStepMetadata` and `SagaMetadata` modelled steps as an ordered,
`dependsOn`-linked list but couldn't express a step's *kind* or the *outcome* a
branch fires on. 0.7.0 grows both into an outcome-edged state-machine graph.

- **`SagaStepMetadata`** — new trailing component `kind` (`StepKind` = `INVOKE` /
  `COMPENSATE` / `AWAIT_EVENT` / `AWAIT_TIMER`); new `effectiveKind()` infers
  `INVOKE`/`COMPENSATE` from the command/compensation structure (await kinds
  require the explicit field). New builder setter `.kind(...)`.
- **`SagaMetadata`** — new trailing component `transitions`
  (`List<SagaTransition>`); `SagaTransition(from, to, on, guard)` carries a
  `TransitionOutcome` (`SUCCESS`/`FAILURE`/`TIMEOUT`/`COMPENSATED`), a null/blank
  `to` marks a terminal edge, and an optional SpEL `guard` narrows the edge. New
  `hasTransitions()`, factories (`success`/`failure`/`timeout`/`ofOutcome`), and a
  compact constructor normalizing `transitions` null → empty (defensive copy),
  `SagaTransition` blank `to`/`guard` → null and rejecting a null/blank `from`.

**Impact:** both components are **appended at the end** of their records, so the
all-args constructor *arity* grew but existing positional prefixes are unchanged
in order. Code calling `new SagaStepMetadata(...)` / `new SagaMetadata(...)`
**positionally** must add the trailing argument (`null` / `null` is fine — they
normalize). The `simple(...)` factories and the builders are the unaffected
path. All additions are by-name on the wire (an old baseline reads `kind` back
`null` and `transitions` back empty); both enums are AST-owned (no annotation
dependency).

### Declarative-behaviour AST records (`DerivedMetadata` / `RuleMetadata`)

**Why:** the declarative-behaviour layer (RFC-2026-06-18) needs the AST to carry
`@Derived` / `@Rule` so a domain can author the declarative form. Two new records
plus two trailing facets:

- **`DerivedMetadata(expression, language, dependsOn)`** — a new `FieldMetadata`
  facet: `FieldMetadata` gains a trailing `derived` component (+ `.derived(...)`
  builder setter, `hasDerived()`). `language` blank → null (`effectiveLanguage()`
  ⇒ `"spel"`); `dependsOn` null → empty (defensive copy); entries may be sibling
  field names or related-entity paths.
- **`RuleMetadata(name, expression, message, severity, language)`** — a new
  `DomainMetadata.rules` list (+ `.rules(...)` builder setter, `hasRules()`).
  Blank `message` / `severity` / `language` → null; the consumer applies the
  semantic default (`effectiveSeverity()` ⇒ `"ERROR"`, `effectiveLanguage()` ⇒
  `"spel"`).

**Impact:** both facets are **appended at the end** of `FieldMetadata` /
`DomainMetadata`, so the all-args constructor *arity* grew (positional callers add
one trailing argument — `null` is fine) while existing prefixes are unchanged.
Builders and factories are the unaffected path. Additive / by-name on the wire
(an old baseline reads `derived` back `null`, `rules` back empty). Shares the
`"0.7.0"` schema (same release). The `-io` reader does not populate these yet
(reader↔processor parity); processor extraction + codegen are the `exeris-tooling`
follow-up.

---

## 0.5.x → 0.6.x

The 0.6.0 line grew the AST record shapes (B4 / B5). Two consequences follow
from that growth.

### `SchemaVersion.CURRENT` bumped `"0.5.0"` → `"0.6.0"`

**Why:** 0.6.0 added JSON-affecting components to the AST — `FieldMetadata.dataType`
(B5), the i18n message keys `FieldMetadata.displayNameKey` / `descriptionKey`
and `UIFieldMetadata.placeholderKey` / `helpTextKey`, the custom-component
escape hatch `UIFieldMetadata.customComponent`, and `ComponentType.CUSTOM`
(B4). The baseline-trust schema version names the AST shape, so it bumps on a
shape change (see `eu.exeris.sdk.sourcemodel.mutation.SchemaVersion`).

**Impact:** a baseline JSON stamped `"schemaVersion": "0.5.0"` now reads as
`NO_BASELINE(SCHEMA_VERSION_SKEW)` — the ADR-042 posture is to refuse a
cross-shape baseline rather than assume compatibility. The additions are
by-name and back-compatible to *read*, but conflict detection will not trust
a stale-schema baseline. **Re-run codegen** to emit a fresh `"0.6.0"` baseline.
In practice there is nothing to migrate yet: codegen does not emit the trust
fields until the tooling writer lands, so no `"0.5.0"` baselines exist in the
wild.

### Positional `FieldMetadata` / `UIFieldMetadata` constructors changed arity

**Why:** the B4 / B5 additions are new record components, so the canonical
(all-args) record constructors gained parameters.

- `FieldMetadata` — two new trailing components (`displayNameKey`,
  `descriptionKey`) after the B5 `dataType`.
- `UIMetadata.UIFieldMetadata` — three new trailing components
  (`customComponent`, `placeholderKey`, `helpTextKey`).
- `DomainMetadata` — one new component `eventHandlers`
  (`List<EventHandlerMetadata>`) inserted in the nested-metadata block after
  `projections`. The annotation `@EventHandler` has shipped since 0.1.0 but had
  no AST record; `EventHandlerMetadata` (new in 0.6.0) is the reaction-side
  companion to `DomainEventMetadata`. Additive and by-name on the wire (an old
  baseline without it reads back as an empty list); the change is to the
  canonical constructor / `DomainMetadata.builder()` shape, both of which gained
  the field.

**Impact:** code calling `new FieldMetadata(...)` / `new UIFieldMetadata(...)`
**positionally** will no longer compile. Prefer the builder / factories, which
are stable across these additions:

```diff
-FieldMetadata f = new FieldMetadata("amount", "Long", /* …all 29 args… */);
+FieldMetadata f = FieldMetadata.builder("amount", "Long")./* …setters… */.build();

-UIMetadata.UIFieldMetadata u = new UIMetadata.UIFieldMetadata(/* …positional… */);
+UIMetadata.UIFieldMetadata u = UIMetadata.UIFieldMetadata.simple("amount", ComponentType.NUMBER_INPUT);
+// or .fullWidth(...) / .custom(fieldName, customComponent)
```

`UIFieldMetadata` also normalizes blank → `null` for the three new fields in
its compact constructor, so an emitter passing `""` (the `@UI` attribute
default) gets an omitted field under `@JsonInclude(NON_NULL)` rather than a
`""`-valued one. `FieldMetadata.Builder` does the same for `displayNameKey` /
`descriptionKey` / `dataType`.

---

## 0.4.x → 0.5.x

**Additive — no migration steps for existing consumers.** 0.5.0 introduced the
bidirectional mutation surface: a new package
`eu.exeris.sdk.sourcemodel.mutation` (`MutationOp` / `MutationResult` /
`MutationPath` / `SchemaVersion` / `SourceDigest` / `BaselineTrust`) in
`source-model`, and conflict detection + conflict-aware application in
`exeris-sdk-source-model-io` (`SourceModelConflictDetector` /
`SourceModelMutationApplier`). No existing annotation or AST record changed.

- **New:** `SchemaVersion.CURRENT` shipped as `"0.5.0"` — the wire-format schema
  version stamped into baseline JSON, decoupled from the Maven artifact version.
- **Consumers:** only code that drives LSP/Studio mutations needs the new
  package; plain annotation / AST / codegen consumers are unaffected.

---

## 0.3.x → 0.4.x

**Additive — no migration steps for existing consumers.** 0.4.0 added the
capability composition surface (ADR-024 / ADR-038): the annotations
`@CapabilityModule` / `@Provides` / `@Requires` / `@CapabilityLifecycle` in the
new `eu.exeris.sdk.annotation.capability` package, the AST records
`CapabilityModuleMetadata` / `ProvidesMetadata` / `RequiresMetadata`, and
`-io` reader support. No existing surface changed.

- **Consumers:** only code declaring or reading capabilities needs the new
  package. The downstream build-time consumer (`@Requires`→`@Provides`
  resolution, the cap manifest) is `exeris-tooling` work, not part of this SDK.

---

## 0.2.x → 0.3.x

**Additive — no migration steps for existing consumers.** 0.3.0 added a single
new sibling module, `exeris-sdk-source-model-io` (ADR-037), housing the
JavaParser-based parser (`.java` → `DomainMetadata`) and idempotent writer
(`DomainMetadata` → `.java`). The `annotations` and `source-model` modules were
unchanged, and `source-model` stayed dependency-light (JavaParser is confined to
`-io`) to preserve zero runtime coupling.

- **Consumers:** add the `exeris-sdk-source-model-io` dependency only if you need
  round-trip Java↔AST (LSP, codegen-maven-plugin). Annotation / AST consumers
  need no change.

---

## 0.1.x → 0.2.x

### `@Validation.required` is deprecated — move to `@Field.required`

**Why:** `required` is a field-shape property, not a validation rule. It now
lives on `@Field` (see `eu.exeris.sdk.annotation` package-info for the
canonical-scoping rationale).

**Window:** during `0.2.x` the processor still reads `@Validation.required`
as a fallback and emits a build warning pointing at the canonical attribute.
**Removed in 1.0.0** — fix the warnings before then or you will silently
lose required-ness.

```diff
 @Field(
     label = "Email",
+    required = true,
     validation = @Validation(
-        required = true,
         email = true
     )
 )
 private String email;
```

### `@Validation.validateOn` is deprecated — move to `@Field.inCreate` / `@Field.inUpdate`

**Why:** form-lifecycle scope is a field property; a field that isn't on the
create form shouldn't have create-scoped validation rules to begin with.
`validateOn = "CREATE"` was a workaround for putting two concerns on the
wrong annotation.

**Window:** same as above — read with build warning during `0.2.x`,
**removed in 1.0.0**.

```diff
 @Field(
     label = "Password",
+    inUpdate = false,
     validation = @Validation(
         minLength = 8
-        validateOn = "CREATE"
     )
 )
 private String password;
```

### `@SoftDeletedBy` retention corrected `RUNTIME` → `SOURCE`

**Why:** every SDK annotation is compile-time only (`@Retention(SOURCE)`) so
nothing leaks into end-user runtime images. `@SoftDeletedBy` was mistakenly
`RUNTIME`-retained in the published `0.1.x` artifacts; it is now `SOURCE`
like the rest, and `AnnotationContractTest` guards the whole surface against
regressions.

**Impact:** none for normal use (the processor reads it at compile time). The
only affected case is code that reflected over `@SoftDeletedBy` **at runtime**
— it will no longer find the annotation. This is intentional; the SDK never
promised runtime presence. No source change required.

### `jackson-annotations` bumped from `3.0-rc5` → `2.21`

No user code change. Jackson 3.x deliberately keeps annotations on the
legacy 2.x line (per `jackson-bom` 3.x: `jackson.version.annotations=2.20+`)
— the 3.0-rc* annotations track was abandoned. This was required for
Jackson 3 databind 3.1.2 to load (`JsonSerializeAs` is a 2.21 addition).

If your downstream code imports `com.fasterxml.jackson.core.*` annotations
directly (rather than transitively through the SDK BOM), no change needed —
the package coordinates and class names are stable; only the version bumps.

### Wire-format contract for downstream Jackson consumers

If you read SDK-emitted `*.json` files into AST records via your own Jackson
mapper, you **must** configure:

```java
ObjectMapper mapper = JsonMapper.builder()
        .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
        .build();
```

Jackson 3 defaults this to `true`; AST records use primitive booleans with
`@JsonInclude(NON_DEFAULT)` / `NON_NULL`, so absent fields arrive as `null`
on the wire. Without the flag, deserialization throws on any record that has
a default-valued boolean.

See `eu.exeris.sdk.sourcemodel.ast` package-info and the
`AstJsonRoundTripTest` wire-format guard for the canonical reference.

### `ActionParamMetadata` is now a record

Previously a `final class` with record-style accessors; Jackson 3 didn't
recognize the accessors as getters and silently dropped every field on
serialization. The migration to a record fixed the bug.

**API impact:** Builder API and static factories (`required`, `optional`)
are unchanged. Accessor names (`name()`, `type()`, …) are unchanged.

**Behavioural note:** `equals` / `hashCode` semantics changed from "by
`name` only" to the synthesized record default (all components). If you
stored params in `Set<ActionParamMetadata>` or relied on `List.contains`
for dedup-by-name, behaviour is now stricter. Tooling consumers
(`ExerisDomainProcessor.extractActionParamMetadata`) only construct via
Builder and don't rely on by-name equality, so the practical impact is
contained.
