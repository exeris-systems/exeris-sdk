---
title: Migration guide
type: migration-guide
visibility: public
owning-repo: exeris-sdk
last-verified: 2026-10-01
---

# Migration guide

This document tracks user-visible changes between Exeris SDK versions and
the upgrade steps required.

> **Versioning policy.** The SDK is `0.x` until 1.0.0 GA. Breaking changes
> may land in any 0.x release; downstream consumers pin exact versions.
> Once 1.0.0 ships, semver applies (minor bumps additive only, patch bumps
> bug-fix only). See [`ROADMAP.md`](ROADMAP.md).

---

## 0.12.x → 0.13.x

### `SagaMetadata` carries the `@Saga` compensation section + `SchemaVersion.CURRENT` is `"0.13.0"`

**Impact on code: none required.** `SagaMetadata` gained six trailing, nullable
components and keeps its 0.12.0 constructor:

| Record | Components 0.12.0 → 0.13.0 | Added | Kept constructor | Named construction |
|---|---|---|---|---|
| `SagaMetadata` | 16 → 22 | `compensationMaxRetries`, `compensationRetryDelay`, `continueCompensationOnFailure`, `compensationDlq`, `compensationFailureHandler`, `manualInterventionOnCompensationFailure` | 16 arguments | builder: one setter per component |

Each component is named after its `@Saga` attribute. They are boxed (`Integer`,
`Boolean`, `String`) and `null` when the author declared nothing, in which case the
annotation default is the value. A declared `0` or `false` is written, because the
record is `NON_NULL`. `compensationFailureHandler` holds a fully-qualified class
name, with `void.class` read as `null`. No producer populates them yet. The
`exeris-tooling` processor and the `-io` reader extract them together, as ADR-042
requires, and until then a producer's JSON is the same as it was on 0.12.0.

**Baselines.** `SchemaVersion.CURRENT` moves `"0.12.0"` → `"0.13.0"`, so a
baseline stamped `"0.12.0"` reads as `NO_BASELINE(SCHEMA_VERSION_SKEW)` until codegen
re-stamps it. This is the deliberate posture, the same one the 0.11.0 and 0.12.0
bumps took: refuse a cross-shape baseline rather than assume compatibility.

---

## 0.11.x → 0.12.x

### Three AST records grew a trailing component + `SchemaVersion.CURRENT` is `"0.12.0"`

**Impact on code: none required.** Three records in `eu.exeris.sdk.sourcemodel.ast`
gained trailing, nullable components, and each keeps its 0.11.0 constructor:

| Record | Components 0.11.0 → 0.12.0 | Added | Kept constructor | Named construction |
|---|---|---|---|---|
| `DomainMetadata` | 39 → 41 | `routeAccess` (`@RouteAccess`), `channel` (`@Channel`) | 39 arguments | builder: `.routeAccess(…)`, `.channel(…)` |
| `ActionMetadata` | 17 → 18 | `routeAccess` (`@RouteAccess`) | 17 arguments | builder: `.routeAccess(…)` |
| `SystemFieldsMetadata` | 10 → 11 | `sharedScopeField` (`@SharedScope`) | 10 arguments | builder: **new**, `SystemFieldsMetadata.builder()` |

The kept constructor delegates to the new canonical one with `null` for what was
added, which is the 0.11.0 meaning. So builder callers change nothing, positional
callers compile unchanged, and a class compiled against 0.11.0 still links rather
than failing with `NoSuchMethodError` on a constructor that is no longer there.

This is the record-growth stance in
[`MIGRATION-0.x-to-1.0.md` §3](MIGRATION-0.x-to-1.0.md), **rewritten for 0.12.0**
(Stellar finding S6). Until now growth replaced the canonical constructor,
positional callers added one trailing argument per new component, and
`source-model`'s semver gate was told to accept the removed constructor as a
minor change. That broke the one caller with no alternative. `SystemFieldsMetadata`
had no builder, and its only factory, `defaults()`, fixes the canonical names, so
`exeris-tooling`'s processor, which sets non-canonical names from `@TenantId` and
`tenantIdField`, could only build it positionally, and it stopped compiling against
0.12.0. It compiles unchanged now. The semver gate no longer treats a removed
constructor as compatible anywhere in `source-model`.

**Optional: switch to the builder.** A caller that fills `SystemFieldsMetadata`
with non-canonical names can name them instead of ordering them:

```java
// 0.11.0, and still compiles on 0.12.0
new SystemFieldsMetadata("id", "createdAt", "createdBy", "modifiedAt", "updatedBy",
        "orgId", "rev", "deleted", null, null);

// 0.12.0: starts from defaults() and sets only what differs — the same value
SystemFieldsMetadata.builder()
        .updatedAtField("modifiedAt")
        .tenantIdField("orgId")
        .versionField("rev")
        .softDeleteField("deleted")
        .build();
```

All eleven components are `String`s, so a positional call that swaps two of them
compiles and silently points a generated column at the wrong field. The builder
cannot make that mistake. `exeris-tooling`'s processor is the natural first user:
it is the only producer that fills this record with anything but the defaults.

**On the wire nothing breaks.** Every new component is by-name and omitted when
absent, so a 0.11.0 document reads back with them `null`. The AST shape still has
a version of its own, and the stamp moves with it: a baseline stamped by 0.11.0
now reads as `NO_BASELINE(SCHEMA_VERSION_SKEW)` until codegen re-stamps it. That
is the deliberate posture — refuse a cross-shape baseline rather than assume
compatibility — and it is the same one-milestone degradation the 0.10.0 and
0.11.0 bumps caused. Note this is *not* the inlining bug below: that one made
the two halves of a single build disagree; this one is the mechanism working.

`@RouteAccess` and `@Channel` are reserved, and no processor populates them;
`@SharedScope` is read by the `exeris-tooling` processor on a `UNIVERSE` entity.
Each has its own entry below.

### `@RouteAccess` is new, reserved, and outside the 1.0.0 freeze

Nothing to migrate — it is additive and nothing extracts it yet. Three things to
know before you write it:

- Declaring it has **no generated effect on routes**. The `exeris-tooling`
  processor does not extract it and no generator emits a route policy from it,
  and the `-io` reader does not read it. It records author intent for the
  `exeris-tooling` slice that will.
- The processor **does validate it**: `@RouteAccess(PUBLIC)` beside a non-empty
  `permissions` fails the build — on the entity, on an action, and on an action
  that declares `permissions` while inheriting the entity's `PUBLIC`.
- It is **not frozen at 1.0.0** ([ADR-072](docs/adr/ADR-072-kernel-preview-spi-reserved-surface.md),
  as amended), because the kernel holds route authorization at tier `preview`.
  A 1.x minor may change or drop it. Pin exactly if you adopt it early.

Do not reach for `roles = {}` or `permissions = {}` to mean "public" — empty
already means "nothing declared" on all four attributes, and `@Action.roles` has
documented empty as "accessible to all authenticated users" since 0.1.0. That
collision is the reason this annotation exists.

### `@Channel` is new, reserved, and outside the 1.0.0 freeze

Nothing to migrate — it is additive and nothing extracts it yet. Code that
constructs `DomainMetadata` positionally keeps compiling through the kept 0.11.0
constructor, which leaves `channel` absent (first entry above).

- Declaring it has **no generated effect today**. No processor reads it, no
  generator opens an endpoint from it, and the `-io` reader does not read it.
- It is **not frozen at 1.0.0** ([ADR-072](docs/adr/ADR-072-kernel-preview-spi-reserved-surface.md),
  amended 2026-09-03), because the kernel holds its WebSocket SPI (kernel
  ADR-084) at tier `preview`. That tier is gated on evidence under load rather
  than on the contract's shape, but it still bars freezing: a 1.x minor may
  change or drop the annotation. Pin exactly if you adopt it early.

It is not another spelling of `@ExerisDomain(realTimeApi = true)` or
`@Action(streaming = true)`. Those are server push over SSE, one-directional by
construction; `@Channel` declares a connection clients also write to. It
deliberately declares no message-size limit, origin allowlist, frame format or
reconnection behaviour — ADR-072 obligation 20 gives the reason for each.

### `@SharedScope` is new — frozen as declared

Nothing to migrate. Code that constructs `SystemFieldsMetadata` positionally
keeps compiling through the kept 10-argument constructor, which leaves
`sharedScopeField` `null`. To set it, use the 11-argument canonical constructor or
the new builder's `.sharedScopeField(…)` (first entry above).

- It marks the field holding the **shared-scope key** of a `DataScope.UNIVERSE`
  entity — the column a generated policy compares against the kernel's
  `ConnectionInterceptor.SESSION_KEY_SHARED_SCOPE` to widen reads across
  tenants. It **accompanies** `@TenantId`, which keeps writes pinned to the owning
  tenant; it does not replace it.
- The `exeris-tooling` processor reads it on a `dataScope = UNIVERSE` entity
  into `SystemFieldsMetadata.sharedScopeField`, and transcribes the tier: the
  `TENANT` emission pins writes to the owner, and an additive `FOR SELECT`
  policy widens reads on this column. A `UNIVERSE` entity without a
  `@SharedScope` field, or without an owner field, is refused at the
  declaration site. On an entity of any other tier the marker has no effect:
  the processor warns and does not record it.
- Unlike `@RouteAccess` and `@Channel`, it is **not** on ADR-072's exception
  list. Like the other system markers it is frozen at 1.0.0 as declared
  ([`MIGRATION-0.x-to-1.0.md` §2](MIGRATION-0.x-to-1.0.md)).

### The UI kit is `@exeris/ui-kit` on the public npm registry

**Impact on code: a dependency name, and an `.npmrc` line removed.** The UI kit was published to
GitHub Packages as `@exeris-systems/ui-kit`, which needs a token with `read:packages` even to
install. It is now `@exeris/ui-kit` on registry.npmjs.org and installs with no configuration. The
version stays the package's own (`0.1.0`); nothing in it changed besides the name.

```diff
-"@exeris-systems/ui-kit": "^0.1.0"
+"@exeris/ui-kit": "^0.1.0"
```

Rewrite every import specifier the same way (`@exeris/ui-kit/theme`, `@exeris/ui-kit/styles`,
`@exeris/ui-kit/tailwind.preset.js`, and any `node_modules/@exeris-systems/ui-kit/…` path), and
drop `@exeris-systems:registry=https://npm.pkg.github.com` from `.npmrc` if nothing else uses it.
Applications generated by `exeris-tooling` take the new name from the tooling release that pins
SDK 0.12.0. The GitHub Packages copy is not updated further.

### `@ExerisDomain.apiVersion` is deprecated — removal in 1.0.0

**Why:** the attribute reaches no emitted artifact. The generated router registers
each entity at its `path`, the OpenAPI document publishes the same, and every
generated client requests the same; none of them has an `/api/<version>` segment.
The client used to prefix one, which is how the mismatch was found: the first
request from a generated client to a generated server answered `404` (Stellar
finding T38), and the client was aligned on `path`. Since then the attribute has
had no destination at all.

It cannot be given one later either. Its default is `"v1"`, so a generator that
started honouring it would move every route of every application that never wrote
it. And 1.0.0 freezes everything public, with 1.x additive-only, so an attribute
still live at the freeze could not be removed before 2.0. Deprecating it now is
the only window in which removing it costs nothing.

**Impact:** a source that sets it now compiles with a `[removal]` warning from
javac. Neither build path carries it: the `-io` reader and the `exeris-tooling`
processor both leave it unread, and `DomainMetadata.Builder` has no default for
it. So `DomainMetadata.apiVersion` is `null` and the metadata JSON has no
`"apiVersion"` key. No generator read it, so the generated output does not
change, and a baseline that carries `"apiVersion"` still reads back. Code that
reads `DomainMetadata.apiVersion()` gets `null` where it got `"v1"`, with the
same `[removal]` warning as code that calls the builder's `.apiVersion(…)`; both
go at 1.0.0 with the attribute.

```java
// before — declares a version nothing serves
@ExerisDomain(module = "sales", path = "/orders", apiVersion = "v2")
public class Order { … }

// after — say nothing; the route was always /orders
@ExerisDomain(module = "sales", path = "/orders")
public class Order { … }

// if you need a versioned route today, it is part of the path
@ExerisDomain(module = "sales", path = "/v2/orders")
public class Order { … }
```

**There is no replacement attribute.** If generated versioned routes come back,
they will come as a new opt-in attribute with no default, so that an entity that
declares nothing keeps its route.

- **Deprecated:** 0.12.0 (`@Deprecated(since = "0.12.0", forRemoval = true)`).
- **Removed:** 1.0.0 — the attribute, and the `DomainMetadata.apiVersion` component
  with its accessor and builder setter
  ([`MIGRATION-0.x-to-1.0.md` §1](MIGRATION-0.x-to-1.0.md)).
- **Processor:** `exeris-tooling` does not read it, and its `-Aexeris.strict`
  inert-attribute check reports it.

### `effectivePath()` and `effectiveTableName()` take the English plural; `@ExerisDomain.tableName` is new

**Why:** three naming helpers on `DomainMetadata` disagreed about the same entity.
`pluralName()` applied English endings (`Colony` → `Colonies`). `effectivePath()`'s
fallback appended a bare `s` (`/colonys`). `effectiveTableName()` returned the
*singular* (`colony`), a table no generator has ever emitted: `exeris-tooling`
computes its own `snake_case(name) + "s"` and says in its source that it avoids this
method for that reason. What a helper returns freezes at 1.0.0, and changing it in a
1.x minor would be a silent break that no signature check can see, so the three are
aligned now. The naive plural reached real output: `colonys`, `technologys` and
`reassemblys` as table names, and `colonys` as an Angular route (Stellar finding T6).

**What changed** — both helpers now derive from `pluralName()`, whose rule is
unchanged (`+es` after `s`, `x`, `z`, `ch`, `sh`; consonant + `y` → `ies`; otherwise
`+s`), and both lower-case under `Locale.ROOT`, so a Turkish default locale no longer
turns `Item` into `ıtem` — or serves an entity with no declared path at `/ınvoices`
while the generated client calls `/invoices`. `FieldMetadata.effectiveColumnName()`
got the same fix (`invoiceId` was `invoice_ıd` under `tr-TR`). On a JVM whose default
locale is not Turkish-like nothing changes; on one that is, derived names now match
every other machine; tests run all three under `Locale.of("tr", "TR")`. What the
plural changes:

| Entity | `effectiveTableName()` 0.11 → 0.12 | `effectivePath()` fallback 0.11 → 0.12 |
|---|---|---|
| `Order` | `order` → `orders` | `/orders` (unchanged) |
| `OrderLineItem` | `order_line_item` → `order_line_items` | `/order-line-items` (unchanged) |
| `Colony` | `colony` → `colonies` | `/colonys` → `/colonies` |
| `Status` | `status` → `statuses` | `/statuss` → `/statuses` |
| `Box` | `box` → `boxes` | `/boxs` → `/boxes` |

`effectiveTableName()` changed for every entity. `effectivePath()` changed only where
English does not add a bare `s`, and only when `path` is blank, which metadata read
from annotated source never is, since `@ExerisDomain.path` is required.

**Who has to act:** code that calls either helper and relied on the old output. Set
the value you relied on explicitly:

```java
// hand-built metadata that expected the old results
DomainMetadata.builder("Colony", "com.acme.empire")
        .tableName("colony")   // effectiveTableName() used to return this
        .path("/colonys")      // effectivePath()'s old fallback
        .build();
```

The rule does not know irregular or already-plural nouns (`Person` → `persons`,
`Settings` → `settingses`); the explicit values are the remedy there too.

**`@ExerisDomain.tableName`** (new, default `""` = derive) is the author's side of
the same override, and until now there was none: `DomainMetadata.tableName` existed,
and `exeris-tooling`'s table naming honoured it, but nothing could fill it. The `-io`
reader reads it now. The `exeris-tooling` processor does not yet, so on the build path
that generates code it has no effect until the processor release that extracts it.
It is also how an existing table keeps its name once the default changes:

```java
// keeps the table exeris-tooling has created for Colony so far
@ExerisDomain(module = "empire", path = "/colonies", tableName = "colonys")
public class Colony { … }
```

**What follows in `exeris-tooling`, not in this release:** its own default table name
and its Angular route segments switch from the bare `s` to this rule, and the
processor warns once for each entity whose table name that changes, naming the
`tableName` value that keeps the old one. On an existing database, an entity without
that override gets a new table name and a new migration file name.

The TCK has no case for `tableName`. Its only identity cases cover the class name and
package, which come from the class and not from an attribute, and the parity suite
that would catch a reader/processor disagreement is bound nowhere today.

### `SchemaVersion.CURRENT` is no longer a compile-time constant (bugfix)

**Recompile once against 0.12.0, then this stops being your problem.**

The field was `public static final String CURRENT = "0.11.0";` — a *constant
variable* (JLS 4.12.4), which `javac` inlines into every compile site that
mentions it (JLS 13.1). So a consumer that compiled against `source-model`
0.10.0 and later resolved 0.11.0 kept `"0.10.0"` baked into its own class
files. Swapping the jar did not change what that code compares against.

The failure is confusing rather than loud, because the two halves of one build
disagree: `SchemaVersion.isCurrent(stamp)` executes inside the *new* jar and
answers correctly, while a caller's own `SchemaVersion.CURRENT.equals(stamp)`
answers from the *old* inlined literal. A baseline the build has just stamped
therefore reads back as `NO_BASELINE(SCHEMA_VERSION_SKEW)`. It reproduces
perfectly and disappears under a clean build, which is what makes it costly:
found in `exeris-platform`, where an SDK bump was green under `mvn clean test`
and dropped three tests to `NO_BASELINE` without `clean`.

`CURRENT` is now initialized from a private method, so no `ConstantValue`
attribute is emitted and nothing inlines it. The type, name, modifiers and
value are unchanged, and `isCurrent` behaves as before — nothing to change in
your code. Stale *pre-0.12.0* compilation output still carries whatever literal
it baked in; one recompile clears it for good.

**Do not confuse this with the deliberate cross-shape refusal.** A genuine
`"0.10.0"` stamp read by a 0.11.0 build *is* `SCHEMA_VERSION_SKEW`, and is
meant to be reported — the posture is to refuse a cross-shape baseline rather
than assume compatibility. The bug above is the case where the two versions
were never actually different, only the class files were.

**Guarded, not just fixed.** `BaselineTrustContractTest` compiles a probe that
uses `SchemaVersion.CURRENT` where a constant expression is required (an
annotation element value) and asserts it **fails**, plus a twin with a real
constant that must succeed so the first assertion cannot pass on an unrelated
compile error. The property is invisible to reflection and to japicmp — the
semver gate compared 0.11.0 against this change and reported nothing at all —
so measuring the compiler is the only way to hold it.

### `SourceModelReader` reads `@Saga.version` (bugfix)

The `-io` reader now carries `@Saga.version` into `SagaMetadata.version`, as the `exeris-tooling`
processor has since tooling 0.8.0. Before, it left the builder default `1` for every saga, so for
`@Saga(version = 3)` the reader and the processor's baseline disagreed. There is no AST shape
change and no `SchemaVersion` move.

What a caller sees: `read()` of a source that declares a version now returns it. A value below `1`
is carried as written, as the processor carries it, and the kernel refuses it at
`FlowDefinitionBuilder.version(int)`. One difference remains. A version given through a constant
reference or expression (`version = Versions.CURRENT`) reads as `1`, because the reader is
syntactic and only the processor sees javac's folded value. Declare saga versions as literals.

TCK binders: `Facet.SAGA` is new, and the corpus `Order` now declares `@Saga(version = 3)`. The
reader, producer and parity suites each gain a case under it. A binding whose side does not extract
sagas yet declares `Facet.SAGA` in `unsupportedFacets()`. A binding that switches exhaustively over
`Facet` needs a new arm.

---

## 0.10.x → 0.11.x

### `@Action.path` is now optional (and has never been consumed)

**Why:** the attribute was mandatory — `String path();`, no default — so every
author had to supply one, and no generator has ever read it. `ActionMetadata`
carries no `path` component, so the value does not even reach the build-time
JSON. The served route is derived:

```
{domainPath}/{id}/actions/{kebab-case-action-name}
```

An `@Action(name = "commandFormation", path = "/fleets/{id}/formation")` on a
domain at `/fleets` is served at `POST /fleets/{id}/actions/command-formation`.
The declared path is not a route and never was — so the annotation obliged
every author to write a plausible, adjacent, wrong URL, and every reader to
believe it. Found by dog-fooding (`Stellar-Tactics`, finding T44), whose first
test to call a served action asserted both paths and measured the gap.

**Impact:** none on existing code — adding a `default` widens what compiles.
New actions may omit `path` entirely:

```java
// still compiles, still ignored
@Action(name = "cancel", path = "/{id}/cancel", httpMethod = "POST")

// preferred: say nothing rather than something untrue
@Action(name = "cancel", httpMethod = "POST")
```

`exeris-tooling` registers `Action.path` in its inert-attribute registry, so
`-Aexeris.strict` warns on builds that still set it.

**Still open:** whether the attribute becomes an honoured override or is
removed. Honouring it would mean an AST wire-format change and would break
every existing route; removal runs the deprecation pipeline above. Until then
the derived convention is the contract.

---

### `SchemaVersion` moves `"0.10.0"` → `"0.11.0"`

**Why:** the AST grew two trailing components — `FieldMetadata.blob` and
`ActionMetadata.schedule` (see below).

**Impact:** every `exeris-metadata/<entity>.json` baseline stamped `"0.10.0"`
now reads as `NO_BASELINE(SCHEMA_VERSION_SKEW)`, so `applyMutation` in the LSP
and any other baseline-trust consumer degrades until codegen re-stamps. Re-run
codegen once after upgrading. This is the same one-milestone degradation the
0.10.0 bump caused and is expected, not a defect.

Note the bump is taken even though **nothing populates either new component
yet**. The schema names the shape, not its population, and the standing posture
is to refuse a cross-shape baseline rather than assume compatibility.

### New reserved surface: `@Blob` and `@Schedule`

**Why:** kernel v0.11 shipped `…spi.storage.blob` (kernel ADR-056) and
`…spi.scheduling` (kernel ADR-057), each naming an Entity-First gap — no way to
declare "this entity has an attachment" or "run this action on a schedule". See
[ADR-072](docs/adr/ADR-072-kernel-preview-spi-reserved-surface.md).

```java
@Field(displayName = "Statement PDF")
@Blob(contentTypes = {"application/pdf"})
private BlobRef statement;

@Action(name = "reconcile")
@Schedule(cron = "0 3 * * *")          // standard five-field cron
public void reconcile() { ... }
```

**Impact:** none on existing code — both are new, additive, `@Retention(SOURCE)`
annotations, and their AST carriers are trailing and nullable.

**Read this before using either.** Both ship **reserved**: no `exeris-tooling`
processor extracts them, no generator consumes them, and the
`exeris-sdk-source-model-io` reader does not read them (a source using either
will correctly report an unmodeled facet). Declaring them today has no generated
effect. Because the kernel holds both SPI packages at tier `preview`, **neither
surface enters the 1.0.0 freeze** — a 1.x minor may still change or drop them.
They are promoted when the kernel package leaves `preview` *and* the tooling
transcription exists.

Two combinations the platform will refuse once the transcription lands, worth
knowing before writing them:

- **`@Blob` on a `dataScope = GLOBAL` entity is unstorable.** Global scope leaves
  `StorageContext.isolationKey` empty, and a blob store must terminally deny in
  that state rather than fall back to an unscoped location (kernel ADR-056
  obligation 5). Use `TENANT` scope, or do not model the attachment as a blob.
- **A `@Schedule`d action has no identity.** The kernel captures
  `PrincipalContext` at job *submission* and fails a job closed if none was
  captured (kernel ADR-057 obligation 5); a declared trigger has no submission
  event. How a scheduled action authenticates is open — tracked in ADR-072.

Also note what `@Blob` deliberately does **not** declare: a `maxSizeBytes`. A
size bound is a constraint rule, and constraint rules have one declaration site
(`@Validation`) and one AST carrier (`FieldMetadata`) — ADR-054. If you need a
bound, it belongs there.

### `DomainMetadata.isInternal()` reads `internal`, not `hidden` (bugfix)

The predicate keyed off `InternalApiMetadata.hidden()`, a component **neither**
extraction path populates. Both the `exeris-tooling` processor
(`extractInternalApiMetadata`) and the `-io` reader (`SourceModelReader`) map
the mere presence of `@InternalApi` to `internal = true` and leave every other
component at its default — the annotation and the AST record describe different
concepts, so presence is the only signal that crosses. The result was that
`isInternal()` returned `false` for every `@InternalApi` entity on the
build-time path, and `false` even for `InternalApiMetadata.internal(…)`, the
factory named after it.

It now reads `internal()`. If you were reading it, you were reading a constant
`false` unless you hand-built the metadata; if you hand-built it with
`hidden(true)` and relied on the old reading, switch to `internal(true)` — or
set both, since they are independent facets (`hidden` is "absent from generated
docs", `internal` is "service-to-service only"). `hidden()` / `readOnly()` are
unchanged and still readable directly.

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

### `DataScope.UNIVERSE` is reserved — declaring it fails the build

The kernel enforces the shared tier, but the `exeris-tooling` transcription
that maps `UNIVERSE` onto the kernel's `sharedScopeKey` carrier is not built.
Until it is, the processor **refuses** a `UNIVERSE` declaration at the
declaration site, naming the tier and the reason.

The tier is not inert. Without the transcription it falls through to the
`TENANT` emission — owner column, owner-pinned policy, a repository binding
`getTenantId()` — and a shared-world row is exactly the row with no owner
property, so the build fails inside generated code you are told not to edit.
Refusing the declaration is what you meet instead.

**If you declared it:** there is no way to obtain cross-tenant read-widening
from this build. Declare `TENANT` if the entity really is partitioned by an
owner (and give it a tenant property); leave the tier undeclared otherwise.
`GLOBAL` and `TENANT` carry exactly the semantics `tenantScoped` already
carried and are live through the same path.

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

**`-io` reader parity landed in the same release, after the processor.** The
reader reads what the processor writes (ADR-042), so extraction could not be
correct before the `exeris-tooling` slice existed; once it did, the reader
gained `dataScope` with the processor's exact behaviour — `UNSPECIFIED` and any
unrecognised constant read as *absent*, so both paths fall through
`effectiveDataScope()`'s `tenantScoped` fallback rather than inventing a fourth
tier. The reader's `unmodeledFacets()` guard is unaffected — it keys on
annotation types, not attributes.

### `@GraphEdge` can now be repeated outside the SDK's package

**Impact:** none on existing sources — this only removes a compile error. The
`@GraphEdges` container was package-private, so repeating `@GraphEdge` from any
other package failed with "`GraphEdges.value()` is defined in an inaccessible
class or interface" (the compiler requires a container to be at least as
accessible as its repeatable annotation). The container is `public` as of
0.10.0, keeping the same FQN — it moved to its own `GraphEdges.java`, which is
a source-file reorganisation, not an API change.

**What it does not change:** repeating `@GraphEdge` on one field is still not a
way to declare two edges. The processor unwraps the container and then refuses
two edges at the declaration, because `GraphEdgeMetadata` cannot express the
shape — a build error rather than a silent loss. A single `@GraphEdge` is
extracted by the processor and by the `-io` reader alike, and consumed by the
graph-sync generator. If you worked around the old accessibility defect by
hand-writing a container, that workaround can go.

This is the same defect fixed for `@SagaSteps` in 0.9.0. `AnnotationContractTest`
now asserts the rule for every `@Repeatable` in the SDK, so the class is closed
rather than the instance.

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

Jackson 3 defaults this to `true`, and AST records use primitive booleans
heavily. Without the flag, deserialization throws on any explicit `null`
standing where one of them is declared.

> **Corrected 2026-08-26.** This paragraph used to add that
> `@JsonInclude(NON_DEFAULT)` / `NON_NULL` makes "absent fields arrive as
> `null` on the wire", and that the throw follows from "a record that has a
> default-valued boolean". Measured, neither holds: an **absent** property
> binds the primitive's own default and raises nothing. What throws is an
> **explicit** `null`. The requirement is unchanged and still applies to you —
> a baseline is a file you did not necessarily write, and a third-party
> producer, a hand edit, or a re-serialization under `ALWAYS` inclusion each
> put explicit nulls in one — but it does not follow from the SDK writer's
> inclusion posture, which never emits a null at all. That is why the premise
> went unexercised for so long.

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
