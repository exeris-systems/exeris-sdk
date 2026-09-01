# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

`exeris-sdk` is the **developer-facing API surface** of the Exeris platform: pure annotations + a canonical AST + an Angular/Tailwind UI kit. It is the most upstream Exeris repo — nothing in here depends on the kernel, tooling, or platform. Downstream consumers depend on it:

```
exeris-sdk-annotations  (compile-time markers, RetentionPolicy.SOURCE)
        │
        ▼
exeris-sdk-source-model (Jackson-serializable AST records)
        │
        ▼  (one-way; SDK never imports from below)
        ▼
exeris-tooling/exeris-processor      ← extracts AST from annotated source
exeris-tooling/exeris-codegen-*      ← emits Java + TS from AST
exeris-platform/exeris-platform-lsp  ← bidirectional Studio/IDE sync
```

Two practical consequences:

1. **Zero runtime coupling** — annotations are `@Retention(SOURCE)`. They must not introduce kernel or framework dependencies, even transitively. The annotations module has no compile-time deps; the source-model module only pulls `jackson-annotations`.
2. **AST lives here, not in tooling** — every consumer (processor, codegen, LSP, future capability registry) must agree on one shape of "what a domain is." Adding a field to `DomainMetadata` is a wire-format change that downstream tooling has to absorb; treat it as a contract change, not an internal refactor.

## Build & test

### Coverage gates

- **`exeris-sdk-source-model`** — `jacoco-maven-plugin` ≥ 0.8.14 (earlier versions reject Java 26 class file v70; the baseline is v69 as of ADR-069, so this is no longer load-bearing — but downgrading buys nothing). Bound to `verify` with a **0.85 BUNDLE-level** threshold on both `INSTRUCTION` and `LINE` counters. Configured in `exeris-sdk-source-model/pom.xml`; thresholds are properties (`jacoco.instruction.coverage.minimum`, `jacoco.line.coverage.minimum`) so a per-build override is possible without editing the pom (e.g. for a one-off debug `-Djacoco.instruction.coverage.minimum=0`). The pluginManagement entry + agent/report executions live in the root `pom.xml` and `exeris-sdk-parent/pom.xml`.
- **`exeris-sdk-annotations`** — the gate is **deliberately not applied** to this module. The annotation `@interface` declarations have no method bodies, so JaCoCo reports 0 covered instructions (with thousands of synthetic accessor instructions in the denominator) and the metric is meaningless. Instead the module has `AnnotationContractTest`, which discovers every annotation by classpath reflection and asserts (a) `@Retention(SOURCE)` and (b) presence of `@Target` across both the root package and the `system` / `security` subpackages. This is the actual invariant that downstream consumers depend on; if you add a new annotation, the test picks it up automatically.
- **`exeris-sdk-ui-kit`** — Vitest with the v8 coverage provider, **85% per-file** thresholds on lines / statements / functions / branches. Coverage scope is `src/**/*.ts` + `tailwind.preset.js`; the rest of the package is CSS, which Vitest cannot meaningfully measure.

When a test for `eu.exeris.sdk.sourcemodel.ast` adds new uncovered branches, prefer expanding `AstJsonRoundTripTest` (for wire-format concerns) or the focused `<Type>MetadataTest` class (for builder / convenience-method concerns) over introducing a parallel test class.

### The JDK baseline follows the kernel's GA line — JDK 25 LTS

`maven.compiler.release=25` across the reactor (ADR-069, following kernel ADR-066). These jars sit on a consumer's **compile** classpath, so the rule runs in both directions:

- **Do not raise it above the kernel's GA baseline.** A major-70 class is refused outright by `javac` on JDK 25 ("class file has wrong version 70.0, should be 69.0"), which locks out the LTS-only deployments the kernel deliberately admitted. The floor follows the kernel; it does not lead it.
- **Do not lower it below what the SDK's own sources need** (records / sealed types, 21-era). 21 is not a target: it would put the SDK below the kernel's baseline, the same mismatch pointing the other way.

`ClassFileBaselineTest` guards the **emitted class-file major** (≤ 69) rather than the property — the stamp is what a consumer trips over, and a module-level `<release>` or a plugin default that re-raises the target would not be caught by asserting on the property. Verified non-vacuous by building at 26.

This was `26` until 0.10.0, on the stated rationale that the kernel required it. Kernel ADR-066 measured that premise false ("the `26` in the build was never load-bearing") and moved to 25 LTS; the SDK followed before the 1.0.0 freeze so that GA covers an artifact an LTS consumer can accept. `exeris-tooling` has its own baseline and its own decision — the SDK half is a prerequisite, not a substitute. If the baseline changes again, keep `README.md`, `ROADMAP.md` and ADR-069 aligned.

### ui-kit is npm-only

`exeris-sdk-ui-kit/` is **not in the Maven reactor** (despite the README listing it as a module). It's a standalone npm package (Tailwind preset + CSS), published interim to **GitHub Packages as `@exeris-systems/ui-kit`** (`.github/workflows/publish-ui-kit.yml`, tag `ui-kit-v*`); publishing to public npm as `@exeris/ui-kit` is a 1.0.0 GA item. Build it separately:

```bash
cd exeris-sdk-ui-kit && npm install && npm run build
npm run test:coverage   # Vitest + the 85% per-file gate
```

## Maven module layout — what's publishable

| Module | Publishable? | Notes |
|---|---|---|
| `exeris-sdk-bom` | yes (pom) | Single source of truth for module + 3rd-party versions |
| `exeris-sdk-parent` | yes (pom) | Common build plugins; imports the BOM |
| `exeris-sdk-annotations` | **yes** (jar) | Attaches `-sources` and `-javadoc` jars (Maven Central requirement) |
| `exeris-sdk-source-model` | **yes** (jar) | Same — attaches sources + javadoc |
| `exeris-sdk-source-model-io` | **yes** (jar) | JavaParser-based parser/writer (ADR-037); JavaParser is confined to this module |
| `exeris-sdk-composition-spec` | **yes** (jar) | `cap-manifest.json` schema + the one canonical content binding (ADR-024 obligation 8b); zero runtime deps |
| `exeris-sdk-composition-lifecycle` | **yes** (jar) | Cap-facing `CapabilityLifecycleHooks` interface — the four-phase lifecycle contract (ADR-024 obligation 8a); zero deps (enforcer-proven), so cap authors compile against annotations + this jar only |
| `exeris-sdk-composition-runtime` | **yes** (jar) | SKU-boot stamp asserter + boot conductor (ADR-024 obligations 8a/8a′); ships into the SKU jar; deps = spec + lifecycle + `jackson-databind` |
| `exeris-sdk-annotation-catalog` | **no** (build-time tool) | The two contract emitters: `AnnotationCatalogProcessor` (emits `annotation-catalog.json`, on `exeris-sdk-annotations`' processor path) and `AstSchemaProcessor` (emits `ast-schema.json`, on `exeris-sdk-source-model`'s). Both ship their output, not themselves; the artifactId predates the second emitter. `maven.deploy.skip` |
| `exeris-sdk-ui-kit` | npm, not Maven | Excluded from the reactor in `pom.xml` |

When adding a new Maven module that's intended for publish, mirror the `attach-sources` + `attach-javadocs` executions from `exeris-sdk-annotations/pom.xml` — without them, Maven Central rejects the artifact.

## Distribution: Maven Central, not GitHub Packages

> **Status (2026-07-21):** Central publishing is deliberately **not wired yet** — the SDK does not move to Central before the kernel does (ecosystem-wide sequencing decision). Releases ship as git tag + GitHub Release; there are no `eu.exeris` artifacts on Maven Central, and downstream repos resolve SDK artifacts from a local `mvn install`. The wiring (Central Portal plugin + GPG release profile + publish workflow) is prepared on the parked branch `feat/0.9.0-central-publish-wiring`; the Central flow below is the target state once the ecosystem switches, kernel first.

The parent `exeris-systems/CLAUDE.md` describes a `GITHUB_TOKEN` / `PACKAGES_READ_TOKEN` flow for cross-repo `eu.exeris:*` resolution. **That does not apply here.** The SDK publishes to Sonatype Central Portal (see `<distributionManagement>` in root `pom.xml`):

- Snapshots → `https://central.sonatype.com/repository/maven-snapshots`
- Releases → `https://central.sonatype.com`

Downstream Exeris repos resolve SDK artifacts from Central, not from GitHub Packages. Maven Central metadata requirements (`<url>`, `<organization>`, `<licenses>`, `<developers>`, `<scm>`, `<issueManagement>`) are already wired in the root POM — keep them populated when bumping the version.

## Stability contract & version policy

`0.x` is unstable: breaking changes can land in any 0.x release; downstream consumers pin exact versions. `1.0.0` freezes the public API — only additive minors and bug-fix patches after that. The current scope is tracked in `ROADMAP.md` (`0.2.x` quality gates → `0.3.x` parser/writer → `0.4.x` `@Capability` → `0.5.x` mutation surface → `0.6–0.9.x` feedback cleanups → `1.0.0` GA).

Practical implication while in 0.x: when removing/renaming public API, run the deprecation pipeline in `MIGRATION.md` (mark `@Deprecated(forRemoval = true)`, document the canonical replacement, give the processor a fallback-with-warning window of at least one minor release).

## Field vs Validation canonical scoping

This is the single most likely place to introduce a subtle regression. Fixed in 0.2.0; finalized in 0.9.0 (ADR-054); deprecations removed in 1.0.0:

- **`@Field`** owns field-shape and lifecycle: `required`, `inCreate`, `inUpdate`. It declares **no constraint attributes**.
- **`@Validation`** is the **sole declaration site** of the constraint rules: `min`, `max`, `minLength`, `maxLength`, `pattern`, `email`, `url`, `future`, `past`, etc.
- **`FieldMetadata`** is the **single AST carrier** of the constraint values (`minLength`/`maxLength`/`min`/`max`/`pattern`), populated from `@Validation` by the processor and the `-io` reader.
- `@Validation.required` and `@Validation.validateOn` are `@Deprecated(forRemoval = true)`. The downstream processor still reads them as a fallback during 0.2.x with a build warning.
- DB NOT NULL / not-blank semantics are **derived** from `FieldMetadata.required` at generator level — they are not separately declared.

The full rationale is in two package-info files; keep them in sync when changing scoping:

- `exeris-sdk-annotations/src/main/java/eu/exeris/sdk/annotation/package-info.java`
- `exeris-sdk-source-model/src/main/java/eu/exeris/sdk/sourcemodel/ast/package-info.java`

The long-deferred "wider `min` / `max` / `pattern` overlap" cut **landed in 0.9.0 (ADR-054)**, informed by the budgetHQ corpus (19/19 constraint usages on `@Validation`): the overlap was fictional at the annotation level (`@Field` never declared those attributes), and `ValidationMetadata` — never populated by any processor/reader nor consumed by any generator, with no published artifact for anyone to depend on — was **removed outright in 0.9.0** (0.x permits the break; a deprecation window with zero possible consumers is vacuous). Do not reintroduce constraint attributes on `@Field` or a parallel AST validation carrier.

## Javadoc completeness is a publish gate, not a preference

Every public member of every publishable module carries javadoc, and the build enforces it.
These jars go to Maven Central, where they are permanent and where their javadoc is what a
consumer reads instead of the sources; nothing in the publish path checks completeness, so the
build does.

- **Six modules use `maven-javadoc-plugin`'s `failOnWarnings`** — `annotations`,
  `source-model-io`, `composition-spec`, `composition-lifecycle`, `composition-runtime`, `tck`.
  Any new undocumented public member fails the build there.
- **`source-model` uses `JavadocCompletenessTest` instead**, because it has one deliberate
  exemption javadoc cannot express: **builder setters are not documented**. Each sets the record
  component of the same name, and that component's `@param` carries the meaning — a per-setter
  restatement is filler. The test runs the javadoc tool and drops warnings whose declaration
  matches `public Builder <name>(`. Do not "fix" a setter by documenting it, and do not widen
  the exemption to anything else.

Adding a public method, factory, enum constant or record component means adding its javadoc.

## Route access: "unspecified" and "public" are different, and only one is declarable

`@RouteAccess(PUBLIC | AUTHENTICATED)` (0.12.0, reserved) is the **sole** way to say a
generated route admits unauthenticated callers. Two rules follow, and both are easy to
break by reflex:

- **Never overload `roles = {}` / `permissions = {}` to mean "public."** Empty means
  "nothing declared" on all four attributes (`@ExerisDomain` and `@Action`), and
  `@Action.roles` has documented empty as "accessible to all authenticated users" since
  0.1.0. The collision is exactly why `@RouteAccess` exists; re-creating it silently
  reopens routes.
- **Never add an `UNSPECIFIED` constant**, to `@RouteAccess.Level` or to
  `sourcemodel.ast.RouteAccess`. The third state is structural: no annotation ⇒ not
  declared, and the AST components (`DomainMetadata.routeAccess`,
  `ActionMetadata.routeAccess`) are nullable to match. `value()` is mandatory for the same
  reason. This is the `DataScope` rule one level up — see that enum's javadoc.

Two constants, not four: `permissions` keeps the scope half (the kernel's edge decides on
scopes and `RouteRequirement` declares no role kind at all), so `@RouteAccess` states only
whether identity is required. And `PUBLIC` beside a non-empty `permissions` is a
contradiction the platform refuses — a permit-all route binds no principal, so the scope
check can never pass. Rationale: ADR-072 (amended), obligations 9–15.

## AST records & the Jackson 3 wire-format contract

The `eu.exeris.sdk.sourcemodel.ast.*` records are the build-time hand-off format (`exeris-metadata/<entity>.json`) between processor and codegen. Three non-obvious constraints govern them:

1. **They must be records, not classes** — `ActionParamMetadata` was originally a `final class` with record-style accessors, and Jackson 3 silently dropped every field because it didn't recognize them as getters. Every AST type is now a record; keep it that way.
2. **Downstream Jackson consumers must set `FAIL_ON_NULL_FOR_PRIMITIVES=false`** — Jackson 3 defaults it to `true`, and the AST is primitive-boolean heavy, so an explicit `null` standing where one of them is declared throws. **Corrected 2026-08-26:** this used to read "so absent fields arrive as `null`" — measured, an *absent* property binds the primitive's own default and raises nothing; only an *explicit* null throws. The obligation stands (a baseline is a file the reader did not write — third-party producers, hand edits and `ALWAYS`-inclusion re-serialization all emit explicit nulls), but it does not follow from our own writer's inclusion posture. `AstJsonRoundTripTest` configures the mapper this way; `exeris-sdk-tck`'s `AbstractMapperPostureTck` is what a consumer runs against their own.
3. **`@JsonInclude(NON_DEFAULT)` drops boxed-zero** — `Long(0)` is treated as "empty" by Jackson 3 and dropped on serialization. Fixed for the `FieldMetadata` bounds (`min`/`max`/`minLength`/`maxLength`) in 0.9.0 via per-component `@JsonInclude(NON_NULL)`, so zero-valued bounds survive the wire (ADR-054). The general caveat still holds for any other boxed-numeric field under class-level `NON_DEFAULT`: avoid `0` as a meaningful value, or give the component the same per-component `NON_NULL` treatment.

Every public AST record is exercised by `AstJsonRoundTripTest` (serialize → deserialize → deep equality). When adding a new AST record or component, add a test case there; this is the wire-format guard that already caught two real bugs.

The mutation surface in `eu.exeris.sdk.sourcemodel.mutation` (`MutationOp` / `MutationResult`, 0.5.0, ADR-042) has its own analogous guard, `MutationWireFormatTest` — it round-trips every op and result *through the sealed-interface type* so the polymorphic `"op"` / `"outcome"` discriminator is exercised and the concrete subtype is recovered. When adding a new `MutationOp` / `MutationResult` variant, add its case there (and register the `@JsonSubTypes` entry — these are sealed, polymorphic types, not flat records).

### `jackson-annotations` stays on the 2.x line on purpose (databind 3.x ≠ annotations version)

The BOM has `jackson.version = 3.2.2` (databind / datatype) but `jackson.annotations.version = 2.22`. Jackson 3.x deliberately keeps annotations on the legacy 2.x line — the `3.0-rc*` annotations track was abandoned upstream. The exact pairing is **dictated by `jackson-bom`**: `jackson-bom 3.2.2` declares `jackson.version.annotations=2.22`, so databind `3.2.2` pairs with annotations `2.22` (and `2.22` still ships `JsonSerializeAs`, the floor the AST relies on — added back at `2.21`). When bumping `jackson.version`, read the matching `jackson-bom`'s `jackson.version.annotations` and move `jackson.annotations.version` to match — but **never "unify" the two onto one number**; they are intentionally on different (3.x / 2.x) tracks. (History: `3.1.2/2.21` → `3.2.0/2.22` in 2026-06 for upstream security fixes; `3.2.0` → `3.2.2` in 2026-09 because `3.2.0` carries the `@JsonView`-bypass advisory, fixed in `3.2.1` — `3.2.2` rather than the minimum because `exeris-kernel-bom` is already there and this version is coordinated across repos. Both bumps kept annotations at `2.22`, checked against each `jackson-bom` rather than assumed.)

## The annotation catalog

`exeris-sdk-annotations` ships `META-INF/exeris/annotation-catalog.json` — every `@interface`
the module declares, with its `@Target`, `@Retention`, per-attribute type / default /
required-ness / admissible enum values, deprecations with their canonical replacement, and
the javadoc prose. It is written during that module's own compilation by
`AnnotationCatalogProcessor` in `exeris-sdk-annotation-catalog`, on the javac
**annotation processor path** — which is not the annotations module's dependency tree, so
the published jar stays dependency-free.

Three things about it are load-bearing:

- **It is generated from sources, not reflection.** Reflection recovers names, types,
  defaults, `@Target`, `@Retention` and `@Deprecated`, and then stops. It cannot recover the
  prose (a class file carries no javadoc, at any retention — the `SOURCE` retention is a
  true fact standing next to the reason rather than being it), and it cannot recover a
  deprecation's canonical replacement, which lives in `@deprecated` prose because
  `@Deprecated` has only `since` and `forRemoval`.
- **It covers nested declaration sites.** 20 of the 71 `@interface` declarations are nested
  inside another annotation, and a classpath package walk does not see them. A catalog that
  is 51/71 complete is worse than none, because its consumer cannot tell.
- **No timestamp.** The output is a pure function of the sources and `${project.version}`,
  so two builds of one commit produce identical bytes.

It costs jar size: 453 KB of JSON, 89 KB deflated, taking the annotations jar from
128 KB to 216 KB. Acceptable here specifically because these annotations are
`@Retention(SOURCE)` and belong on a consumer's **compile** classpath at `provided`
scope — the jar never reaches a runtime image, so the bytes are paid once at build time.

Two guards, deliberately using different mechanisms so they cannot share a bug:
`AnnotationCatalogProcessorTest` (catalog module) drives the processor over a synthetic
annotation and reads back what it wrote; `AnnotationCatalogContractTest` (annotations
module) compares the catalog against **reflection over the compiled classes** and fails on
any disagreement, on a missing `purpose`, or on a deprecation with no replacement. The
expected set is derived, never written down — a hard-coded count is wrong one release later.

**Build wrinkle:** `annotationProcessorPaths` is not a dependency edge, so Maven neither
orders the reactor by it nor resolves it from the reactor in a partial build. The full
`mvn verify` works (declaration order in `<modules>` puts the generator first); a
`mvn -pl exeris-sdk-annotations -am ...` fails to resolve the processor until the catalog
module has been installed once.

**Distribution:** `.github/workflows/release-assets.yml` attaches the catalog to the GitHub
Release on a `v*` tag, and refuses if the catalog's `sdkVersion` disagrees with the tag.
That channel needs no publishing infrastructure, so it does not wait for the Central move.
It uploads to an *existing* release and never creates one — which is how releases are cut
here (`gh release create` publishes tag and release together). A tag pushed alone with
`git push --tags` fails the job; re-run it via `workflow_dispatch` once the release exists.

## The AST JSON Schema

`META-INF/exeris/ast-schema.json` ships inside `exeris-sdk-source-model` and is attached to
each GitHub Release. Same technique as the annotation catalog: `AstSchemaProcessor` in
`exeris-sdk-annotation-catalog`, on **`exeris-sdk-source-model`**'s javac processor path
(again not a dependency edge — the published jar stays free of the emitter and of databind,
and the module's enforcer rule still proves it).

Four things to know before touching it:

- **The reader requirements are quoted contract, not prose.** The
  `FAIL_ON_NULL_FOR_PRIMITIVES` entry states the *corrected* posture (an explicit null is
  the hazard; an absent property binds the type default) — the claim this file's own
  2026-08-26 correction above supersedes. `AstSchemaContractTest` fails if the superseded
  wording comes back, because a schema is where a dead reason must not be resurrected.
- **Two versions, and they are not the same number.** `sdkVersion` comes from
  `${project.version}`; `astSchemaVersion` is the AST wire shape and comes from the
  `ast.schema.version` property in `exeris-sdk-source-model/pom.xml`. That property duplicates
  `SchemaVersion.CURRENT`, which cannot be read from the POM or the declaration model because
  it is deliberately no longer a compile-time constant. **Bump both together** —
  `AstSchemaContractTest` fails when they drift.
- **Jackson's annotations do not stay on a record component.** `@JsonInclude` and
  `@JsonProperty` target `{ANNOTATION_TYPE, METHOD, FIELD, TYPE, PARAMETER}` — no
  `RECORD_COMPONENT` — so `RecordComponentElement.getAnnotation` returns `null` and javac
  propagates them to the accessor and backing field instead. Read all three (`onComponent`
  does). Getting this wrong is silent: the schema still emits, and simply understates the
  wire contract.
- **Property prose comes from `@param` on the record, and nothing else works.** A comment
  written above a component inside the record header renders in javadoc but is unreachable
  from annotation processing — four routes were measured and all return `null`. Adding a
  record component therefore means adding its `@param`: coverage is held at **100%** by
  `AstSchemaContractTest`, and `AstComponentProseConventionTest` refuses the header form
  outright. Both fail loudly rather than letting the schema quietly thin out.
- **No `required` array, deliberately.** All three postures the package uses (`NON_NULL`,
  `NON_DEFAULT`, `NON_EMPTY`) omit rather than write, so any `required` list would reject
  documents this SDK produces. Which components a compact constructor rejects as `null` is a
  construction-time invariant, not a property of the wire format.

Adding an AST record or component needs no schema edit — the emitter picks it up, and
`AstSchemaContractTest` fails if a new Java type reaches the document unmapped (add it to the
type switch rather than shipping `x-exeris-unmapped`).

## ADR-003 — Entity-First

The architectural premise this whole repo serves: the `@ExerisDomain`-annotated Java class is the **single source of truth**; SQL, REST/GraphQL endpoints, DTOs, and Angular UI are generated artifacts. No hand-written `.proto` or OpenAPI files. See `docs/adr/ADR-003 Entity-First Development Strategy.md` for context; this is what justifies the annotation-heavy surface.

## When in doubt — reference-first

The parent `exeris-systems/CLAUDE.md` reference-first rule applies here too: when a downstream repo (`exeris-tooling`, `exeris-platform`) has already solved a pattern that touches this SDK's surface, grep that working reference before designing from scratch. Common cases:

- How an AST record is consumed by the processor → `exeris-tooling/exeris-processor/`.
- How an annotation is read at compile time → same repo, `ExerisDomainProcessor` and its helpers.
- Real-world `@ExerisDomain` usage (when scoping a new annotation attribute) → `budgetHQ/backend/` entities and `pbm/` legacy entities.
