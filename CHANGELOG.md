# Changelog

All notable changes to the Exeris SDK are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

**Versioning policy.** The SDK is `0.x` until 1.0.0 GA: breaking changes may land
in any 0.x release and downstream consumers pin exact versions. See
[`ROADMAP.md`](ROADMAP.md) for the milestone scope and [`MIGRATION.md`](MIGRATION.md)
for per-version upgrade steps.

> **Release history note.** `0.6.0` is the first **tagged** release. The earlier
> entries are ROADMAP milestones that shipped on a single pre-release line — the
> POM version went `0.1.0-SNAPSHOT` → `0.5.0-SNAPSHOT` → `0.6.0`, so `0.2.0` /
> `0.3.0` / `0.4.0` were milestone labels, never cut as artifacts. Dates below
> are when each milestone landed. `0.6.0`–`0.11.0` are tagged releases (with
> links); the earlier entries are milestone labels only.

## [Unreleased]

### Added

- **`META-INF/exeris/annotation-catalog.json` ships inside `exeris-sdk-annotations`, and is
  attached to the GitHub Release.** Every `@interface` the module declares — 72 as this release
  stands, 52 top-level and 20 nested — with `@Target`, `@Retention`, per-attribute type / default /
  required-ness / admissible enum values, deprecations with their canonical replacement, and
  the javadoc prose. Written during the annotations module's own compilation by the new
  build-only `exeris-sdk-annotation-catalog` module, on the javac annotation processor path,
  which is not the annotations module's dependency tree — the published jar stays
  dependency-free. Generated from sources rather than by reflection for two reasons a
  reflection emitter cannot work around: a class file carries no javadoc at any retention,
  and a deprecation's replacement lives in `@deprecated` prose because `@Deprecated` has only
  `since` and `forRemoval`. No timestamp, so two builds of one commit produce identical bytes.
  The count is the catalog's own `annotationCount` field and moves with the surface — it was 71 /
  51 / 20 when this entry was first written, and `@RouteAccess` below has landed since. What is
  gated is coverage, not the number: the guard derives the expected set from the sources rather
  than restating it, so an annotation cannot go missing, only uncounted in this sentence.
  `.github/workflows/release-assets.yml` attaches it on a `v*` tag and refuses when the
  catalog's `sdkVersion` disagrees with the tag. This unblocks `exeris-ai-bridge`'s `sdk:*`
  family, whose whole content had no producer.

- **`@RouteAccess` — the SDK can state that a generated route is public.** `@Target({TYPE,
  METHOD})`, one mandatory `value()` of `Level { PUBLIC, AUTHENTICATED }`, carried by the new
  `DomainMetadata.routeAccess` / `ActionMetadata.routeAccess`. It closes the Entity-First gap
  kernel ADR-061 opened: the kernel gained a declarable per-route policy
  (`HttpRoutePolicy` / `RouteRequirement`), and nothing here could state one. This is **not**
  the `roles` / `permissions` extraction gap — those four attributes all use empty to mean
  "nothing declared", and `@Action.roles` has documented empty as "accessible to all
  authenticated users" since 0.1.0, so no extractor could have distinguished *unspecified*
  from *public*. There is deliberately no `UNSPECIFIED` constant: an element with no
  `@RouteAccess` declared nothing, and the AST component is nullable to match. Two constants
  rather than four — `permissions` keeps the scope half, and `RouteRequirement` declares no
  role kind at all. **Reserved**, on ADR-072's three-part test and therefore outside the 1.0.0
  freeze: no processor extracts it, no generator emits a policy table from it, and the kernel
  holds route authorization at tier `preview`.
- **`SchemaVersion.CURRENT` is `"0.12.0"`** for the two new trailing components. Additive and
  by-name; a `"0.11.0"` baseline reads as `SCHEMA_VERSION_SKEW`, the established posture of
  refusing a cross-shape baseline rather than assuming compatibility.

- **`META-INF/exeris/ast-schema.json` ships inside `exeris-sdk-source-model`, and is attached
  to the GitHub Release.** A JSON Schema (draft 2020-12) for the build-time metadata hand-off
  format — 61 definitions covering every record and enum in `eu.exeris.sdk.sourcemodel.ast`,
  nested ones included, `$ref`-linked and rooted at `DomainMetadata`. The two Jackson 3
  postures a reader must adopt are carried **in the document** rather than as folklore:
  `x-exeris-reader-requirements` states both, every primitive property is flagged
  `x-exeris-primitive`, and each definition — plus any property that overrides it — carries
  its `x-exeris-json-include`, so whether a zero is meaningful is answerable from the schema.
  Stamped with both `sdkVersion` and `astSchemaVersion` (the wire shape, which moves on its
  own schedule); the release workflow refuses a mismatched `sdkVersion` and
  `AstSchemaContractTest` refuses a drift between `astSchemaVersion` and
  `SchemaVersion.CURRENT`. Emitted by the same processor technique as the annotation catalog,
  so no schema library and no second pass over the sources. Deliberately carries no
  `required` array: all three `@JsonInclude` postures in the package omit rather than write,
  so a `required` list would reject documents this SDK itself produces.

- **AST record components are documented with `@param`, and a guard keeps them there.** The
  fifteen doc comments written inside a record header — six on `ActionMetadata`, nine across
  `EnumMetadata` / `EnumValueMetadata` — moved onto their record as `@param` tags, which is
  the canonical Java form and the only one annotation processing can read. They now reach
  `ast-schema.json`: property prose goes from `0/362` to `15/362`, and the record headers are
  a plain component list instead of 116 lines of interleaved comment.
  `AstComponentProseConventionTest` fails if a header comment comes back — the two forms are
  indistinguishable in an IDE, so the trap needs catching where it is written rather than
  where it is missed. `@param` carries no nested `@since`, so the six per-component
  introduction versions on `ActionMetadata` (`methodName` 0.7.0, the three streaming/real-time
  components 0.8.0, `schedule` 0.11.0, `routeAccess` 0.12.0) are stated in the prose instead —
  which also puts them in the schema, where a consumer pinning an older SDK can read them.

- **Every AST record component is documented, and a gate holds it there.** All 362 components
  across 43 records, and all 61 definitions, now carry prose reaching
  `META-INF/exeris/ast-schema.json` — up from 15. `AstSchemaContractTest` fails if coverage
  drops below 100%, so adding a component means adding its `@param`, and
  `AstComponentProseConventionTest` refuses the header-comment form that looks documented and
  reaches nothing. This is the bar for publishing to Maven Central: the schema is what a
  non-JVM consumer reads instead of the sources, and the artifact carrying it is permanent.

- **Every publishable module is javadoc-complete, and each one is gated.** 811 undocumented
  public members across six modules — `source-model` 597, `annotations` 121, `source-model-io`
  49, `composition-runtime` 24, `tck` 11, `composition-spec` 9 (`composition-lifecycle` was
  already clean). All documented except **215 builder setters**, which are deliberately left
  alone: each sets the record component of the same name, that component carries the prose, and
  215 restatements would be filler. Each `Builder` says so in its own class javadoc.

  Gated two ways, because a one-off pass decays. The five clean modules plus `annotations` turn
  on `maven-javadoc-plugin`'s `failOnWarnings`, so a new undocumented member fails the build
  outright. `source-model` cannot — javadoc has no way to exempt the builder setters — so
  `JavadocCompletenessTest` runs the javadoc tool itself and applies the exemption as a rule a
  reader can check. Both verified non-vacuous.

  The bar is Maven Central: nothing in the publish path checks completeness, the javadoc jar
  builds either way, and the artifact is permanent.


### Security

- **`jackson-databind` 3.2.0 → 3.2.2.** 3.2.0 carries the `@JsonView`-bypass advisory for
  `@JsonUnwrapped` container properties on deserialization (Dependabot #12), fixed upstream in
  3.2.1. Taken to 3.2.2 rather than the minimum because `exeris-kernel-bom` is already there and
  this version is coordinated across repos. `jackson.annotations.version` stays at `2.22` —
  checked against `jackson-bom` 3.2.2 rather than assumed, per the rule in `CLAUDE.md`.
  Note `exeris-platform-bom` is still on 3.2.0 and carries the same advisory.
- **`postcss` 8.5.15 → 8.5.26 in `exeris-sdk-ui-kit`'s lockfile.** Clears two advisories,
  both path traversal via an attacker-controlled `sourceMappingURL` (Dependabot #13 high, #14
  moderate; fixed in 8.5.18 and 8.5.23 respectively). Development-only and transitive — Tailwind
  and Vite both pull it under `^8.x`, so the lockfile moves and `package.json` does not. The lock
  diff is postcss and its own `nanoid`, nothing else; `npm ci`, `npm run build` and the Vitest
  suite all pass on it.

### Fixed

- **Two annotation attributes documented an effect they do not have — and one of them documented the
  opposite of what happens.** Both reported by the Stellar Tactics dog-food (T29, T38) and verified
  against the tooling source rather than taken on report, which changed what one of the fixes had to
  say.

  **`@ExerisDomain.dataScope = UNIVERSE` said "no generated effect today". Declaring it fails the
  build.** The tier is reserved pending the `exeris-tooling` transcription onto the kernel's
  `sharedScopeKey` carrier, and "reserved" was written as though it meant "inert" — the posture the
  streaming attributes had before ADR-043. It never did. Without the transcription the tier falls
  through to the `TENANT` emission (owner column, owner-pinned policy, a repository binding
  `getTenantId()`), and a shared-world row is exactly the row with no owner property, so the
  archetypal `UNIVERSE` entity failed with `cannot find symbol` inside generated code its author is
  told not to edit. Tooling replaced that with a refusal at the declaration site — a warning until
  0.8.0, an unconditional error since. So the sentence a reader trusts before trying the tier was
  wrong when written and is further from the truth now: the honest reading of "reserved" here is
  *rejected*, not *ignored*. Corrected on `dataScope()`, on the `UNIVERSE` constant, in
  `MIGRATION.md`, and in the `ROADMAP.md` bullet that still described the warning.

  **`@ExerisDomain.apiVersion` said "used in URL path: `/api/{version}/{path}`". Nothing serves that
  prefix.** The generated router registers routes at the derived domain path and the OpenAPI document
  publishes the same; the generated typed client used to prepend `/api/<version>` and therefore could
  not reach the router it was generated beside — every cross-service call answered 404 until the
  client was aligned. Two streaming TypeScript emitters were still folding the version into their SSE
  routes and were aligned in the same batch, and `-Aexeris.strict` now reports the attribute inert.
  The value does still reach `DomainMetadata.apiVersion()`, unlike `@Action.path` which reaches no AST
  component at all, so the javadoc says that too — carried on the wire, read by no generator, and not
  a statement about where an endpoint is served. Whether the router *should* serve a versioned route
  is a real design question; it is left open rather than answered in a javadoc.

  Same defect class as `@Action.path`, fixed in 0.11.0: an attribute describing a route nobody serves.
  It matters more this release than last, because `annotation-catalog.json` ships for the first time in
  0.12.0 and carries this prose verbatim — a false status note now propagates into the first
  machine-readable copy of the SDK's own contract, and into every agent that reads it.

- **Dark mode ran on two signals; the `.dark` class the README documents now drives both halves.**
  The `--exeris-*` design tokens took their dark values from a `.dark` *class*. The `.exeris-*`
  component classes took theirs from `dark:` variants, which fall back to
  `@media (prefers-color-scheme: dark)` on v3 and v4 alike. So an app that toggled the class — the
  only thing this package ever told you to do — got dark tokens and light component chrome unless
  the operating system happened to agree: dark text on a light input.

  Fixed with `darkMode: 'class'` in `tailwind.preset.js` (v3) and
  `@custom-variant dark (&:where(.dark, .dark *))` in `theme.css` (v4, which has no JS preset to
  read the former from). It covers `dark:` utilities a consumer writes as well as the ones this
  package ships.

  **This is a behaviour change for existing v3 consumers**, and the reason it was taken as its own
  decision rather than as a rider on the v4 component-parity work below. An app that relies on the
  OS signal gets it back with `darkMode: 'media'` — or any custom strategy, such as
  `['selector', '[data-theme="dark"]']` — in its own `tailwind.config.js`, which wins over a
  preset. Both overrides are asserted, so the escape hatch cannot rot.

  `tests/dark-mode-signal.test.js` compiles the documented setup on each major and asserts the
  **absence** of `prefers-color-scheme` alongside the presence of a `.dark`-scoped rule; asserting
  only the latter would pass while a media query sat beside it, which is precisely the split being
  closed. Both halves are mutation-verified, and that is what caught a vacuous assertion in the
  guard itself: Tailwind's escaped class name for a `dark:` utility (`.dark\:bg-exeris-primary`)
  contains the literal `.dark`, so the substring test passed whether or not the fix was in place.

  **Residual, documented rather than fixed:** the `@custom-variant` line sits in `theme.css`, so a
  v4 build importing only `…/styles` keeps the media signal for the component layer. `index.css`
  is v3's entry too, and v3 does not consume `@custom-variant` — it copies the at-rule verbatim
  into every v3 consumer's compiled output. The README's v4 guidance now says both entries are
  required rather than wanted.

- **The `.exeris-*` component classes now reach Tailwind v4 consumers, and the reason they did not
  was one line, not the file's shape.** B3 shipped a styled class for every renderable
  `ComponentType`, but only into `index.css`, which a v4 build refused. The account recorded a
  release earlier — that v4 rejects the `@tailwind base/components/utilities` directives *and*
  the `@apply exeris-btn` composition — was half wrong, and the half that was wrong is the one
  that mattered. Measured against the compiler: **v4 tolerates the `@tailwind` directives** (they
  are no-ops there) and understands `@layer components`, `@apply` of real utilities,
  `duration-[var(--exeris-transition-fast)]`, and the `dark:` / `sm:` / `focus:` variants. The
  single construct it refuses is `@apply` of a *custom* class — four lines, all in the button
  variants.

  Those four are now one selector list (`.exeris-btn, .exeris-btn-primary, … { … }`): no v4-only
  syntax, no second copy of the base declarations, and no change for v3. That last part is
  measured, not asserted — the v3-compiled effective declarations for all five button classes,
  pseudo-class suffixes and `@media` contexts included, are byte-identical before and after.

- **`index.css` is compiled by both majors in CI.** `tests/component-classes-v4-compile.test.js`
  puts the stylesheet through v4 *and* v3 and asserts each emits every `.exeris-*` class the file
  declares, that every renderable `ComponentType` gets **unconditional** declarations rather than
  only a `:checked` / `::before` rule, that an undeclared class emits nothing, and that each
  button variant still carries the shared base. Running it on v3 as well is the point: the change
  was made for v4 and the risk lands on v3. Verified non-vacuous against three mutations —
  restoring `@apply exeris-btn` (the compile fails with the original blocker), dropping a variant
  from the selector list (both majors fail), and renaming a control's own rule while leaving its
  `:checked` rule in place (the looser "a rule mentions it" check would have passed).

  The `ComponentType` → class map moved to `tests/support/component-types.js` so the text guard
  and the compile guard read one copy, and the Tailwind plumbing to `tests/support/tailwind.js`.

- **Tailwind v4 consumers got a frozen theme: `.dark` did nothing, and no test could see it.**
  The kit ships its token namespace twice — `tailwind.preset.js` for v3, `src/styles/theme.css`
  (`@theme`) for v4 — and the v4 half had only ever been *read*, never compiled. Compiling it
  showed the two halves are not equivalent: the v3 preset indirects every entry through a
  `--exeris-*` custom property, so a v3 consumer's `bg-exeris-primary` follows a runtime override,
  while `theme.css` inlined the light-theme literals. So v4 had no working dark mode at all: the kit's `.dark`
  lives in `index.css`, which a v4 build cannot consume, and a consumer writing their own `.dark` could not
  move a literal either.
  The text-level parity guard was blind to it by construction — its assertion, "the v4 literal
  equals the light channels in `index.css`", was the defect written down as an invariant.
  Fixed on three counts, each measured rather than reasoned about:
  - `@theme` values now read `var(--exeris-…)` / `rgb(var(--exeris-…))`, indirecting exactly where
    the v3 preset does and staying literal exactly where it does (the font stack, the animations).
  - `.dark` re-declares the three mapped colours whose channels it changes. A `@theme` value's
    `var()`s are substituted where the property is *declared*, and the substituted value is what
    inherits — so a mapping declared only on `:root` is already resolved by the time a nested
    `.dark` re-points its channels. Confirmed in a headless browser: before, `bg-exeris-primary`
    inside `.dark` computed `rgb(79, 70, 229)`; after, `rgb(99, 102, 241)` at any nesting depth.
  - The `--exeris-*` declarations are mirrored into `theme.css`, because a v4 build could not
    consume `index.css`. `theme.test.js` now guards that copy value-for-value (checked against
    both a drifted value and a dropped token). *(Corrected in the entry above: the reason first
    recorded here — that v4 rejects the `@tailwind` directives as well as the `@apply`
    composition — was half wrong. v4 tolerates the directives. The mirroring is still right, for
    the narrower reason that a v4 consumer may import only the theme entry.)*

  A consumer's own **scoped** override still needs a one-line repeat on v4 (`:root` overrides do
  not); that is a v4 `@theme` property, and it is documented in the README and in `theme.css`
  rather than papered over. Also newly documented: on v4 the `.exeris-*` component classes are
  unreachable, not merely unthemed — the open half of B3.
- **`theme.css` is compiled by a real Tailwind v4 in CI.** The recorded B1 follow-up, and what
  surfaced the above. `@tailwindcss/postcss` (v4) coexists with the v3 devDep by nesting its own
  engine, so no alias or second package was needed. `tests/tailwind-v4-compile.test.js` asserts
  against compiler output that every preset utility is generated, that invented names produce
  nothing, and that each utility resolves to the same `--exeris-*` property v3 uses — all derived
  from `tailwind.preset.js`. `source(none)` keeps the compile hermetic; without it Tailwind
  harvests candidates from this package's own prose and a utility passes because a comment named
  it. Verified non-vacuous against four mutations — restoring the literal `theme.css`, dropping one recomputed colour from `.dark`, renaming a `@theme` variable, and making a deliberately-literal entry indirect — each caught by the assertion it was aimed at.
- **`@exeris/ui-kit` versions independently and gets its own 1.0 — written down, and enforced.**
  It was already true in practice (`0.1.0` against a `0.12.0` Java line, mirroring
  `@exeris/codegen-ts` at `0.2.0` against `exeris-tooling` `0.8.0`) and stated nowhere, which at
  GA would read as "SDK 1.0 covers everything under the SDK umbrella". Now in `CLAUDE.md`,
  `MIGRATION-0.x-to-1.0.md` §2b, `ROADMAP.md`'s GA item and the package README. Its 1.0 freezes
  the **namespace and not the values**: token names, `.exeris-*` classes and Tailwind utility
  keys are the contract; colours, spacing and shadows are the theming surface a CMS overrides.
- **`tests/public-surface.txt` pins the 126 names that contract covers.** The three derived drift
  tests each compare two of this package's artifacts against *each other*, so a **coordinated**
  rename — the same token changed in `index.css`, the preset and the `@theme` entry — kept them
  all green while breaking every consumer. Snapshot-gated the way `annotation-surface.txt` gates
  the Java surface: additions reported so they get recorded, removals and renames fail. Verified
  against exactly that coordinated rename.
  
- **`defaultTheme` gains `primary-hover`, the token it was missing.** `--exeris-primary-hover`
  exists in `index.css` and as a `primary-hover` key in the Tailwind preset; the JS fallback
  had neither, so a generated component taking the fallback had no hover colour while a CSS
  consumer did. Found by the new drift test, which recorded it as an exemption rather than
  silently passing; the exemption is now empty. Kebab-cased to match the other two — three
  artifacts naming one token identically is what lets the drift test map them without a
  translation step. Taken at `0.1.0` deliberately: adding a property to the exported
  `ExerisTheme` is consumer-visible, and doing it before the package reaches public npm costs
  nothing.

- **The ui-kit's real gate was one hard-coded assertion; it is now derived from the CSS.**
  `defaultTheme` is what generated components fall back to when a host overrides nothing, and
  exactly one of its values — `primary` — was checked against `index.css`. Every other colour,
  spacing and radius was pinned only by a shape regex against a key list the test carried
  itself, so a wrong `rgb()` or a `1rem` → `2rem` slip passed. `default-theme-drift.test.js`
  now derives from `index.css` in both directions: values must match, and a CSS colour with no
  JS fallback fails unless it is recorded as one. It also guards `.dark` against `:root` for
  the palette — the four status colours held constant across themes are listed rather than
  asserted to be a defect. Four probes confirm each guard fires.
- **`CLAUDE.md` said the ui-kit's 85% coverage threshold was its quality gate.** Measured, it
  is not: `src/index.ts` is one statement and `tailwind.preset.js` is zero, so the threshold
  cannot fail for either and only reports that the module was imported. The threshold stays —
  it holds the first source file with real logic — but both it and `vitest.config.ts` now say
  what it does and what does the actual work.

- **`@SagaStep.order` / `parallel` and `@Saga`'s compensation pair now say what the platform
  actually does with them.** Raised from `exeris-tooling` as an `INERT_ATTRIBUTES` candidate for
  `order`; measurement says otherwise, and the notes record what was measured.

  `order` is **live**, and the only thing deciding the sequence: `ExerisDomainProcessor` and the
  `-io` reader both read it and sort by it, so the generator's list-order emission follows it.
  Declaration position breaks ties only — the sort is stable. It keeps no default, because giving
  one would let every step share it and hand the sequence to file position, silently turning a
  method move into a drain-before-deploy break (kernel ADR-062).

  `parallel` is the genuine gap: extracted, carried, and read by nothing — the emitted flow is a
  strict linear chain. Two steps with equal `order` and `parallel = true` run one after the other,
  which two javadocs previously implied otherwise. Concurrency has to be expressible in the
  kernel's `FlowDefinition` first.

  `@Saga.compensationStrategy` / `compensationOrder` are the actual declared-but-unextracted pair
  here: both exist on the annotation and an author can set them, but neither producer reads them,
  so `SagaMetadata` holds builder defaults on every build path. Noted on the attributes and on
  their AST components.

- **The javadoc warning count was being read off a truncated log.** `maven-javadoc-plugin`
  caps at 100 warnings per module, so `annotations` and `source-model` both reported exactly
  100 and the survey under-counted by a factor of five (real: 121 and 597). Measured by running
  the javadoc tool directly with the cap lifted.

- **"Most AST components are documented in the record header" — measured, they are not.**
  The claim shipped with the AST schema and overstated the case badly: of 43 records, **three**
  carried component prose at all, totalling **fifteen** comments. The other forty document
  nothing at component level. The corrected statement is in `ROADMAP.md` and in
  `AstSchemaProcessor`'s javadoc: a header comment is *a* form a component can be documented
  in, not the prevailing one, and the reason property prose was empty is mostly that the prose
  does not exist rather than that it was unreachable.

- **The AST schema described the boxed-zero hazard as unmitigated.** Jackson's
  `@JsonInclude` targets do not include `RECORD_COMPONENT`, so an annotation written on a
  record component is propagated to the derived members and
  `RecordComponentElement.getAnnotation` returns `null`. The first emitter asked the
  component alone, so `FieldMetadata`'s four bounds were reported as ordinary `NON_DEFAULT`
  properties — the exact defect their per-component `NON_NULL` exists to prevent. It now
  reads the accessor and backing field too, recovering all four and only those four.
- **The annotation-surface gate rejected a mandatory element on a *new* annotation.**
  `AnnotationSurfaceContractTest` read "new element, no default" as a break for every
  annotation, but the rule protects existing usages and a brand-new annotation has none — the
  baseline's own `@Action#name` and `#label` are required. The gate now treats a declaring
  type absent from the snapshot as new, and still reports the element so it must be recorded
  before the next change to it is gated. Verified non-vacuous against an undefaulted probe
  element added to `@Blob`.
- **Sixteen nested annotations declared neither `@Retention` nor `@Target`**, so they defaulted
  to `CLASS` retention and were applicable anywhere — outside the invariant this module's own
  contract test states, which could not see them because its walk skipped nested types. The
  walk's comment gave the reason as "they inherit SOURCE retention at the use site": true of
  the use sites that existed, and not a property of the declarations. They now declare
  `@Retention(SOURCE)` and `@Target({})` — the empty target set that permits use *only* as
  another annotation's member value, which is what they had always been by convention. Found
  by reading the generated catalog, and the walk now descends into nested types, verified
  against a deliberately un-retained one.
- **Seven nested annotations and 26 of their attributes had no javadoc**, so their catalog
  entries would have carried a name and a type and nothing else. All nested — the surface a
  package walk sees was already documented, which is why it went unnoticed.
  `AnnotationCatalogContractTest` now fails on a missing `purpose`.
- **`SchemaVersion.CURRENT` was a compile-time constant, so consumers inlined it and kept
  comparing against the version they were built against.** `public static final String CURRENT =
  "0.11.0"` is a constant variable (JLS 4.12.4); `javac` bakes it into every downstream compile
  site (JLS 13.1), and swapping the `source-model` jar underneath does not change what that code
  compares against. The two halves of one build then disagree — `isCurrent(stamp)` runs inside the
  new jar and answers correctly, a caller's own `CURRENT.equals(stamp)` answers from the old
  literal — and a baseline the build has just stamped reads back as
  `NO_BASELINE(SCHEMA_VERSION_SKEW)`. Found downstream in `exeris-platform`, where an SDK bump was
  green under `mvn clean test` and dropped three tests without `clean`. `CURRENT` is now
  initialized from a private method, so no `ConstantValue` attribute is emitted; type, name,
  modifiers and value are unchanged. Guarded by a compile probe in `BaselineTrustContractTest`
  that uses `CURRENT` where a constant expression is required and asserts the compile **fails**,
  with a real-constant twin proving the probe is not vacuous — the property is invisible to
  reflection, and japicmp compared 0.11.0 against this change and reported nothing.

## [0.11.0] — 2026-08-26

The kernel-0.11-facets milestone. 0.10.0 called itself the last one before the
1.0.0 freeze; this exists because that framing conflated two things. The ROADMAP
dispositioned the blob and job facets "1.x, not before" on the argument that
freezing an SDK annotation against a kernel package held at tier `preview`
inverts the stability ordering — sound, and unchanged. But it forbids *freezing*,
and read as "not pre-1.0" only because, with 0.10.0 believed to be the last
milestone, the two were the same sentence. A 0.11.0 separates them: 0.x permits
breaking changes in any release, so a surface landing here and explicitly
excluded from the 1.0.0 cut is not frozen against anything
([ADR-072](docs/adr/ADR-072-kernel-preview-spi-reserved-surface.md)).

Schema `"0.10.0"` → `"0.11.0"`. No wire break beyond the schema stamp; both new
components are trailing and by-name, and nothing populates either yet.

### Added
- **`DomainEventMetadata` carries the trigger triple** — `trigger` (an AST-owned
  `Trigger` enum), `actionName`, `fieldName`. Until now the record could name an
  event and frame its payload but not say **when it fires**, which is the input a
  generator needs to place a publish call.

  The gap was easy to miss because `@DomainEvent.trigger` *was* read — and then
  discarded. Both producers used it only to derive the event **name** suffix
  (`CREATE` → `OrderCreatedEvent`) and kept nothing. And because that suffix is
  applied only when the user supplies no explicit `name`,
  `@DomainEvent(name = "OrderPlaced", trigger = CREATE)` left no trace of the
  trigger anywhere at all. `action` and `field` — required by `ACTION` and
  `FIELD_CHANGED` — were read by neither producer.

  `trigger` is **nullable on purpose**. `null` means "this baseline predates the
  growth", which is a different claim from "fires on CREATE"; defaulting it in the
  compact constructor would make the two indistinguishable and silently attach
  create-time publishing to every pre-`0.11.0` event. `hasTrigger()` is the
  intended read. This is deliberately *unlike* `DomainMetadata.effectiveDataScope()`
  (ADR-059), which can default because it has a deprecated predecessor attribute to
  fall through — there is no predecessor here.

  The enum is **duplicated, not shared** — AST-owned, bridged to
  `@DomainEvent.Trigger` by constant-name identity — per the `SagaStepMetadata.StepKind`
  / `DataScope` precedent, keeping the annotations module dependency-free. An
  unrecognised constant leaves the component unset rather than failing the read.

  Unlike `@Blob` / `@Schedule` above, this one **is populated**: the `-io` reader
  extracts all three. The processor half lands in `exeris-tooling` (its EV2 track,
  where the emitted `*EventPublisher` is generated and then invoked by nobody).

  Additive and binary-compatible — the pre-EV2 6-arg constructor is retained, and
  `japicmp` reports additions only.

- **`@Blob` — a field-level binary facet** (kernel ADR-056), with `BlobMetadata`
  carried on `FieldMetadata.blob`. Closes the Entity-First gap the kernel named
  when it shipped `…spi.storage.blob`: there was no way to declare "this entity
  has an attachment", `@Field.dataType` being a free-form presentation hint and
  the ui-kit's `.exeris-file` class styling.

  It declares **no** `maxSizeBytes`, on two independent grounds. A size bound is
  a constraint rule, and constraint rules have had one declaration site
  (`@Validation`) and one carrier (`FieldMetadata`) since ADR-054 — a second one
  here would reopen exactly what that ADR closed. And the kernel states no size
  policy at all (ADR-056 makes transfer buffers caller-owned), so the attribute
  would be inert against a platform with no opinion to be inert against, which
  is worse than inert against an unbuilt consumer.

- **`@Schedule` — an action-level trigger facet** (kernel ADR-057), with
  `ScheduleMetadata` carried on `ActionMetadata.schedule`. Closes the second gap:
  no way to declare "run this action on a schedule". Three mutually exclusive
  attributes mirroring exactly the kinds `JobTrigger` covers — `cron` (standard
  **five-field** syntax, no seconds and no vendor extensions), `every` (ISO-8601
  duration), `at` (ISO-8601 instant). The kernel's fourth, event-driven, is
  excluded there and therefore here; `@EventHandler` already expresses it.

  The AST collapses the three attributes into one `TriggerKind` discriminator, so
  a cron-*and*-interval combination — which the annotation forbids only in prose
  — is unrepresentable downstream. Both new records are class-level `NON_NULL`,
  the posture every small facet record in the package already uses.

  Both surfaces ship **reserved** and **outside the 1.0.0 freeze**: no processor
  extracts them, no generator consumes them, `-io` does not read them, and the
  kernel holds both SPI packages at `preview`. Promotion is conditional on the
  kernel moving each to `stable` *and* the `exeris-tooling` transcription
  existing. Each javadoc also records the one combination its counterpart kernel
  contract refuses — a `@Blob` on a `GLOBAL`-scoped entity has no isolation key
  and is terminally denied, and a declared schedule has no submission event and
  therefore no identity to capture (open, tracked in ADR-072).
- **`exeris-sdk-tck` — a Technology Compatibility Kit for the build-time metadata
  hand-off.** Four abstract JUnit suites a binder extends: producer, reader,
  parity, and consumer mapper posture.

  The parity suite is why the module exists. ADR-042's "the reader reads what the
  processor writes" has been a discipline enforced by whoever remembered it, and
  the record of that is three shipped defects of one shape — `@ExerisDomain(name)`,
  `@Relationship.relationshipType`, `@ActionParam.label` — each one side reading an
  attribute under a key the other did not use, each caught by hand long after. They
  are hard to catch because **nothing fails**: both sides emit well-formed metadata,
  no exception, no diagnostic, and the divergence is visible only by comparing the
  two outputs.

  The corpus ships inside the jar rather than being left to the binder, since a
  binder-supplied corpus measures the binder's imagination. Unbuilt surface is
  declared through `Facet` so its cases skip — reading ahead of a producer
  manufactures exactly the drift the gate exists to catch — with identity and
  fields non-optional, closing the trapdoor where a binding declaring everything
  unsupported reports a suite of skips as green. An enforcer rule keeps JavaParser
  and `-io` off the dependency tree: a kit that could reach the SDK's own reader
  would quietly be testing that instead of the binding.

  Every case is proven non-vacuous against a conforming binding and one broken in
  exactly the way the case describes, committed as self-tests rather than done once
  and described — the kernel TCK's rule, adopted whole. It paid for itself before
  the module shipped: three of the four cases first written for the mapper-posture
  suite could not be made to fail and were deleted (unknown properties are handled
  by `@JsonIgnoreProperties` on the record; a per-component `NON_NULL` beats a
  mapper-wide inclusion; `@JsonTypeInfo` resolves the polymorphic subtype on a
  stock mapper). Each asserts something true that no binding could get wrong, and
  as TCK cases they would have shipped as coverage that covered nothing.

  The corpus is compiled code that travels as a resource — a producer binding
  drives javac over it, and nothing in this repository otherwise would.
  `CorpusCompilesTest` closes that, under `-Werror -Xlint:deprecation`, which
  enforces both that every mandatory attribute is supplied and that nothing
  deprecated for removal is used. It failed on both counts the moment it was
  written: the corpus omitted `@Action.label`, `@Relationship.targetEntity` and
  `@Relationship.displayField`, so no binder could have compiled it, and it
  declared its tenancy tier through `@ExerisDomain.tenantScoped` — removed at
  1.0.0, which would have left the kit failing to compile at exactly the release
  it exists to guard. Reviewing the annotations for attributes that *exist* does
  not catch either; compiling does.


### Changed
- **`SourceModelConflictDetector` and `SourceModelMutationApplier` now declare
  their constructors.** Both previously declared none and therefore carried an
  implicit public no-arg constructor — a member nobody wrote that 1.0.0 would
  freeze regardless, while their siblings `SourceModelReader` and
  `SourceModelWriter` declare theirs. Behaviour-neutral; found by the pre-freeze
  public API surface review.
- **`@Action.path` is now optional** — `String path()` gained a `default ""`. The
  attribute was mandatory and read by nobody: `ActionMetadata` carries no path
  component, so the value never reached the build-time JSON, and the served route
  is derived (`{domainPath}/{id}/actions/{kebab-case-action-name}`). It obliged
  every author to write a plausible, adjacent, wrong URL beside each action, and
  every reader to believe it. Found by dog-fooding (finding T44), whose first test
  to call a served action asserted both paths and measured the gap. Adding a
  default widens what compiles, so no existing code is affected. Whether the
  attribute becomes an honoured override or is removed stays open; the derived
  convention is the contract meanwhile.
- **`SchemaVersion.CURRENT` `"0.10.0"` → `"0.11.0"`.** A `"0.10.0"` baseline reads
  as `NO_BASELINE(SCHEMA_VERSION_SKEW)` until codegen re-stamps — the same
  one-milestone degradation the 0.10.0 bump caused. Taken even though nothing
  populates the new components yet: the schema names the shape, not its
  population.

### Fixed
- **35 annotations claimed `@since 1.0.0` while shipping in 0.1.0.** Scaffold
  boilerplate from the very first commit, harmless while the repo is pre-1.0 and
  false the day 1.0.0 ships, when it starts reading as "added in 1.0.0" for
  surface that predates every release.

  Each file was rewritten to the version its own history gives, not to a blanket
  value. 34 came from `1fef6c5`, whose subject is *"init: exeris-sdk
  v0.1.0-SNAPSHOT skeleton (#1)"* → `0.1.0`; one,
  `annotation/system/package-info.java`, came from `ac4bc63` dated 2026-07-22,
  the 0.9.0 release → `0.9.0`. The batch was already self-contradicting:
  `ExerisDomain` came out of that same scaffold commit and has carried an
  accurate `@since 0.1.0` the whole time.

  `@version 1.0.0` moved with it, kept rather than dropped because this module
  already uses `@version` as "version at introduction" — its 0.4.0 files still
  read `0.4.0` at 0.11.0 — and inventing a second convention mid-cleanup is
  worse than following the one in place. The other six modules were checked and
  carry zero such stamps, so the defect was confined to where the pre-freeze
  surface review found it.

- **The `-io` reader read an `@ExerisDomain` attribute that does not exist.**
  `SourceModelReader` treated `@ExerisDomain(name = "...")` as the entity
  identity, and its javadoc called it "the canonical entity name *the processor
  uses*, which may differ from the Java class name". `@ExerisDomain` declares 35
  attributes and `name` is not among them, and the processor derives the name
  from `element.getSimpleName()` with no override anywhere. JavaParser reads
  source text unvalidated, so nothing ever failed.

  The consequence was not cosmetic: for the same source the reader could return
  a different entity identity than the processor, which is exactly the
  reader↔processor disagreement ADR-042 parity exists to prevent. The attribute
  is dropped rather than added — adding it would be new public API immediately
  before the freeze, inert until `exeris-tooling` honoured it, and it argues
  against Entity-First, where the class *is* the identity.

  52 usages removed, including all five budgetHQ corpus files — documented as
  ported from real entities, and annotated with source that would not have
  compiled against the real annotation. Reads are unaffected: every corpus name
  already equalled its class name, and the reader had a class-name fallback.
- **A wire-format hazard that does not exist, asserted since 0.10.0.** ADR-059
  obligation 3 states that under `@JsonInclude(NON_DEFAULT)` it is
  `DataScope.GLOBAL` (ordinal 0) that drops, and that a dropped explicit `GLOBAL`
  falls through `effectiveDataScope()` and reads back as `TENANT` — "a silent
  tenancy flip rather than a lost hint". `ROADMAP.md` repeats it, and this
  release's own drafting reasoned from it for `TriggerKind.CRON`.

  Measured: it is false. `NON_DEFAULT` drops a boxed numeric zero — the caveat
  `CLAUDE.md` states correctly, and the defect that cost the `FieldMetadata`
  bounds a fix in 0.9.0 — but Jackson does not treat an ordinal-0 enum constant
  as empty, and it survives untouched. The claim was never exercised because
  `DomainMetadata` is `NON_NULL`, so nothing ever tested the premise.

  `AstJsonRoundTripTest` now pins the actual semantics (boxed zero dropped,
  ordinal-0 enum kept), and ADR-059 carries a dated correction. Nothing changes
  in behaviour: the `NON_NULL` postures were the right choice anyway and the
  tier-by-tier round-trip cases are worth keeping — only the stated reason was
  wrong, in three documents at once.
- **The annotation surface gate could not see an element losing its default.**
  `AnnotationSurfaceContractTest` consulted the snapshot's `:default` /
  `:required` flag only for elements it had never seen; for everything already
  recorded it compared the type and nothing else. An existing element having its
  default taken away therefore passed silently, though it breaks every usage that
  omitted it exactly as an undefaulted addition does — so the rule the class
  states as its whole growth story was enforceable in one direction only. A stale
  flag is what made that possible, and there was already one instance (the
  `@Action.path` widening above), so both halves land together: the missing rule,
  and a report on a widening telling the author to refresh the line.
- **`DomainMetadata.isInternal()` was `false` by construction.** It read
  `InternalApiMetadata.hidden()`, a component neither extraction path ever
  populates: the processor (`extractInternalApiMetadata`) and the `-io` reader
  (`SourceModelReader.internalApi`) both map the *presence* of `@InternalApi` to
  `internal = true` and leave the other six components at their defaults, because
  the SDK annotation (a service-to-service call policy — `consumers`,
  `rateLimit`, `requireMtls`, `timeout`, `documented`) and the AST record (entity
  visibility and access control) share a name and nothing else. So the predicate
  returned `false` for every `@InternalApi` entity on the build-time path, and
  `false` even for `InternalApiMetadata.internal(…)`, the factory named after it —
  while the test pinning it read as intent (`isInternalRequiresHiddenInternalApi`).

  Now reads `internal()`. Found by the pre-freeze surface review, and fixed there
  rather than frozen: `exeris-tooling` references neither `isInternal()` nor
  `internalApi()`, so nothing downstream moves.

- **"Absent fields arrive as `null`" — measured, they do not.** The consumer
  contract has said since the wire format was written that consumers must set
  `FAIL_ON_NULL_FOR_PRIMITIVES = false` *because* `@JsonInclude(NON_DEFAULT)`
  makes absent fields arrive as `null`, and that deserialization "throws on any
  record that has a default-valued boolean". On a stock Jackson 3 mapper an
  **absent** property binds the primitive's own default and raises nothing. What
  throws is an **explicit** `null` on the wire.

  The obligation itself is real and unchanged — a baseline is a file the reader
  did not necessarily write, and a third-party producer, a hand edit, or a
  re-serialization under `ALWAYS` inclusion each put explicit nulls in one — but
  it does not follow from our own writer's inclusion posture, which is what the
  text claimed.

  **Corrected in five files, found in three passes.** The first pass took the
  `ast` package-info and `CLAUDE.md`; review caught a third copy in the new
  `TckMappers`, written fresh in the same change that disproved it; review caught
  a fourth in `MIGRATION.md` — the one a downstream consumer actually reads to
  satisfy the obligation. Only then was the repo swept rather than the flagged
  line fixed, which turned up `AstJsonRoundTripTest` (the file `CLAUDE.md` names
  as the canonical reference), `RequiresMetadata`, `CapManifest` in the
  composition spec, and the `.claude` review guardrail that would have
  regenerated the claim on the next contract review. Each is dated; `CapManifest`
  was measured against its own record rather than assumed to match.

  Pinned by `AstJsonRoundTripTest.absentPrimitiveDefaultsWhileExplicitNullNeedsTheFlag`,
  which asserts all three outcomes on a stock mapper: absent binds the default,
  explicit null throws, and with the flag set that same null reads back as the
  default. Prose corrections drift; this one now has a test under it.

  Found by writing the TCK's non-vacuity proof: the case built on the stated
  premise could not be made to fail against a deliberately misconfigured mapper,
  which is the same way the ordinal-zero claim fell earlier this release. The
  premise had never been exercised, because the SDK's own writer never emits an
  explicit null.


## [0.10.0] — 2026-08-12

The kernel-catch-up milestone, and the last one before the 1.0.0 freeze. Three
things land together. The **data-scope tier** goes live (ADR-059):
`@ExerisDomain.dataScope` + an AST-owned `DataScope { GLOBAL, TENANT, UNIVERSE }`
supersede the `tenantScoped` boolean, which is deprecated for removal at 1.0.0 —
taken now because a deprecation and its removal cannot share a release and 1.x is
additive-only. The **record-growth stance** stops being prose and becomes three
enforcing pieces (japicmp with a records-aware override, plus two snapshot guards
that need no baseline), because every post-1.0 facet arrives as a trailing
component and would otherwise be blocked until 2.0. And the **JDK baseline moves
26 → 25 LTS** (ADR-069), following the kernel's own measurement that the 26 was
never load-bearing: these jars sit on a consumer's compile classpath, so the
LTS-only consumer the kernel had just admitted was being stopped here, at the most
upstream repo in the ecosystem.

Alongside them, an evidence-first survey of the annotation surface that traced
attributes to the **emitted artifact** rather than to the AST — which is how it
found what the 0.9.0 sweep structurally could not — and closed the
`@Repeatable`-container defect as a class rather than as a second instance.

Schema `"0.9.0"` → `"0.10.0"`. No wire break beyond the schema stamp; the
`DomainMetadata` growth is trailing and by-name. Releases still ship as git tag +
GitHub Release — Central publishing stays deferred until the kernel moves first.

### Added
- **Data-scope tier — `@ExerisDomain.dataScope` + `DataScope { GLOBAL, TENANT,
  UNIVERSE }`** ([ADR-059](docs/adr/ADR-059-data-scope-expression.md),
  implementing [RFC-2026-06-24](docs/rfc/RFC-2026-06-24-universe-data-scope-expression.md)).
  A single mutually-exclusive discriminator replaces the `tenantScoped`
  boolean's intent, adding the shared-world tier the boolean could not express.
  The RFC's build gate opened when its trigger (2) — the kernel ADR-012 §4b
  in-place amendment — landed on the kernel 0.11 line: a `sharedScopeKey`
  carrier, a `KernelIsolationClaims` shared-scope claim with fail-closed
  mapping (type-checked during token validation), persistence publishing
  `exeris.shared_scope` so RLS widens reads while writes stay pinned to the
  owning tenant, and `AbstractSharedScopeAccessMatrixTck`.

  The annotation-side enum and the AST-owned
  `eu.exeris.sdk.sourcemodel.ast.DataScope` are two independent types mapped by
  name at extraction (the `SagaStep.StepKind` precedent) — the annotations
  module keeps its zero-dependency contract. The annotation side carries an
  extra `UNSPECIFIED` constant because annotation attributes cannot default to
  `null`; the AST expresses that state as an absent field.

  `DataScope.UNIVERSE` ships **reserved**: the kernel enforces the tier, but
  the `exeris-tooling` transcription onto the kernel carrier is not built, so
  declaring it has no generated effect yet and the javadoc says so.
- **`DomainMetadata.effectiveDataScope()`** — the explicit tier if set, else
  `tenantScoped ? TENANT : GLOBAL`; never `null`. The AST half of the
  deprecation window, so a baseline written before 0.10.0 reads back with the
  meaning it always had without every downstream generator re-implementing the
  three-way decision (the `SagaStepMetadata.effectiveKind()` precedent).
- **Semver enforcement, in three pieces that only work together** — `japicmp`
  bound to `verify` in the five non-annotation publishable modules, strict by
  default, with `CONSTRUCTOR_REMOVED` downgraded to a compatible change only
  where records actually live (that is the signal a trailing component
  produces; on plugin defaults it fails the build, which would have blocked
  every post-1.0 facet). `RecordComponentOrderTest` pins component order for
  every public record in `ast` + `mutation` against a snapshot, closing the one
  hole the override opens — a same-arity, same-type reorder is invisible to
  japicmp by construction and survives by-name Jackson binding.
  `AnnotationSurfaceContractTest` replaces japicmp in the annotations module,
  which is `@Retention(SOURCE)` and has no runtime presence to compare: no
  element removed, none retyped, every new element defaulted. Each was verified
  non-vacuous against a deliberately introduced break. CI still runs japicmp
  with `-Djapicmp.skip=true` — there is no published baseline artifact to
  resolve, and an absent baseline is configured to fail rather than pass
  quietly.
- **`AnnotationContractTest.repeatableContainersArePublic`** — every
  `@Repeatable` annotation's container type must be `public`. Reads the
  container off the `@Repeatable` meta-annotation rather than off the package
  walk, which skips nested types. Catches the `@GraphEdges` class of defect
  below at the source instead of one symptom at a time.

### Changed
- **`-io` reader extracts `@ExerisDomain.dataScope`** (ADR-059 parity) — with
  `UNSPECIFIED` and any unrecognised constant reading as *absent*, which is the
  processor's exact behaviour, so both paths fall through the `tenantScoped`
  fallback in `effectiveDataScope()`. Taken only after `exeris-tooling` landed
  the processor side: the reader reads what the processor writes.
- **Annotation honesty notes against kernel v0.11** — `@ExerisDomain.roles` /
  `permissions` (ADR-061: route authorization is the first contract they could
  compile into; ADR-063: only `permissions` has a destination, since
  `RouteRequirement` decides on named scopes and declares no role kind),
  `@SagaStep.name` (ADR-062: step identity is enforced, so renaming a step
  breaks in-flight sagas — the javadoc carries the kernel's drain-before-reorder
  procedure), and `@Saga.version` (ADR-064: the kernel keys its plan catalog by
  `(name, version)` and fails closed on an unregistered version — while the
  attribute reaches no AST on either producer, so `version = 3` yields a
  `SagaMetadata.version` of `1`, silently).
- **`@Field`, `@Action.path`, `@ExerisDomain.graphqlApi` and the
  `@SagaSteps` container carry accurate status notes** — from the 2026-08
  evidence survey, which traced attributes to the *emitted artifact* rather
  than to the AST and so found what the 0.9.0 sweep structurally could not:
  attributes that are extracted and then dropped at the far end of the chain.
  `@SagaSteps` in particular claimed hand-written containers were "read
  identically"; repeating `@SagaStep` compiles and then yields no step from the
  processor and only the first from the `-io` reader.
- **`DomainMetadata` gained a trailing `dataScope` component** — positional
  prefixes unchanged in order, by-name on the wire; positional callers add one
  trailing `null`, builder callers are unaffected. Same posture as the
  `ActionMetadata` growth in 0.8.0 and `CapManifest.ModuleBody` in 0.9.0.
- **`SchemaVersion.CURRENT` `"0.9.0"` → `"0.10.0"`** — a `"0.9.0"` baseline
  reads as `SCHEMA_VERSION_SKEW`; re-run codegen once after upgrading.
- **`MIGRATION-0.x-to-1.0.md` §3 now states the record-growth stance as
  policy** — the canonical record constructor is not part of the frozen 1.0
  surface; a trailing component may be added in a 1.x minor. Previously this
  was only implied by the "builders are the stable path" bullet, and it gates
  every post-1.0 facet (blob, job, flow, graph): if trailing growth counted as
  a break, all of it would be blocked until 2.0. The 1.0.0 japicmp/revapi gate
  must be configured to match.

- **JDK baseline `26` → `25` LTS** ([ADR-069](docs/adr/ADR-069-jdk-baseline-lts.md),
  following [kernel ADR-066](https://github.com/exeris-systems/exeris-kernel/blob/main/docs/adr/ADR-066-preview-clean-ga-baseline.md)).
  `maven.compiler.release=25` across the reactor; published jars carry class-file
  **major 69** and require no `--enable-preview`. For consumers this is a
  **widening** — anything that built on JDK 26 still does, and JDK 25 LTS becomes
  reachable, where a major-70 class was previously refused by `javac` outright.
  No source, wire-format, AST or annotation-surface change of any kind. The
  kernel measured the old premise false (nothing in the tree used a JDK-26-only
  API); the same probe here is green at `--release 25` across all six modules
  with every gate. New `ClassFileBaselineTest` asserts the emitted major rather
  than the build property. CI now runs a two-row matrix: JDK 25 is the
  release-bearing row, JDK 26 the forward-compatibility row. Note
  `exeris-tooling`'s processor has its own baseline — until it follows, an LTS
  build can compile against the annotations but not run the processor.

### Deprecated
- **`@ExerisDomain.tenantScoped`** — `@Deprecated(since = "0.10.0",
  forRemoval = true)`, removal at 1.0.0. Replaced by `dataScope`
  (`true → TENANT`, `false → GLOBAL`). The processor reads it as a fallback
  with a build warning while `dataScope` is `UNSPECIFIED`. Deprecating it now
  rather than at the freeze is deliberate: a deprecation and its removal cannot
  share a release and 1.x is additive-only, so a boolean still live at 1.0.0
  would have been frozen through the whole 1.x line with removal deferred to
  2.0.

### Fixed
- **`@GraphEdges` is `public`, so `@GraphEdge` can be repeated outside the
  SDK's own package** — the container was a top-level type declared without
  `public` inside `GraphEdge.java`, so every external repeated use failed with
  "`GraphEdges.value()` is defined in an inaccessible class or interface"; the
  compiler requires the container to be at least as accessible as the
  repeatable annotation at every use site. Confirmed and re-confirmed by
  compiling from an external package, not inferred. Same FQN (the type moved to
  its own `GraphEdges.java`, per the `SagaSteps` precedent), no wire or AST
  change — containers are flattened on extraction. This is the identical defect
  fixed for `@SagaSteps` in 0.9.0; the guard added above is what makes it the
  last one of its class. Note the fix buys compilation only: `@GraphEdge` is
  extracted by neither producer, so a repeated *or* single edge declaration
  still reaches no AST.

## [0.9.0] — 2026-07-22

The composition-conductor + contract-truth milestone: the cap lifecycle goes
live SDK-side — a new zero-dependency `exeris-sdk-composition-lifecycle`
module (`CapabilityLifecycleHooks`) plus the boot conductor in
`composition-runtime` (ADR-024 obligations 8a/8a′: invoked by the SKU
bootstrap after `KERNEL READY`, never a kernel `Subsystem`) — and the
0.2.0-deferred Field/Validation overlap is cut for good (ADR-054):
`@Validation` confirmed the sole declaration site, `FieldMetadata` the sole
AST carrier, `ValidationMetadata` removed outright, zero-valued bounds fixed
via per-component `NON_NULL`, and `-io` reader `minLength`/`maxLength` parity
restored. The saga state-machine annotation half ships reserved
(`@SagaTransition` + `@SagaStep.kind`), the final pre-1.0 deprecation sweep
closes with zero additions (dispositions + honesty refresh recorded), and the
1.0.0-freeze crossing guide is seeded. Schema `"0.8.0"` → `"0.9.0"`. Releases
ship as git tag + GitHub Release — Central publishing is deliberately
deferred until the kernel moves first (wiring parked).

### Added
- **`exeris-sdk-composition-lifecycle` module** — a new zero-dependency
  publishable jar (the enforcer bans every compile/runtime-scope dependency)
  carrying `CapabilityLifecycleHooks`, the cap-facing four-phase lifecycle
  contract with four default no-op methods (`initialize` / `ready` /
  `drain(remaining)` / `terminate`) — the runtime twin of the
  `@CapabilityLifecycle` marker (ADR-024 obligation 8a, see
  [`docs/adr/ADR-024.link.md`](docs/adr/ADR-024.link.md)). Cap authors compile
  against `exeris-sdk-annotations` + this jar only; implementations need a
  public no-arg constructor and implement only the subset they use.
- **Boot conductor** — `CompositionConductor` + `CompositionBootException` in
  `exeris-sdk-composition-runtime` (ADR-024 obligation 8a′, amendment
  2026-07-21): stamp assertion first → manifest-driven reflective hook
  discovery (all-or-nothing, zero side effects on failure) →
  `initialize*`/`ready*` in **verbatim** manifest `initOrder` (no DAG
  re-resolution) → reverse `drain*`/`terminate*` on `shutdown()`/`close()`
  under one composition-wide drain budget (default 30s, configurable;
  overrunning drains are interrupted and abandoned, an exhausted budget skips
  the remaining drains, terminate always runs). A failing `initialize`/`ready`
  fail-fast-unwinds the touched prefix (drain, then unconditional terminate)
  before throwing. Invoked by the SKU bootstrap inside `kernelMain` after
  `KERNEL READY` — never a kernel `Subsystem`.
- **`CapManifest` module-body `lifecycleOwner` consumer-modeled** — the
  composition-spec `ModuleBody` grew a trailing `lifecycleOwner` component
  (blank normalizes to null; absent/null/blank = the cap has no hooks, matching
  `@CapabilityLifecycle`'s zero-or-one cardinality). It is **not** covered by
  the content binding (like `initOrder` — trusted post-assertion), so
  cap-manifest `schemaVersion` stays **2**: no wire break, pre-0.9.0 manifests
  boot as hook-less. See [`MIGRATION.md`](MIGRATION.md#08x--09x).
- **`@SagaTransition` / `@SagaTransitions` + `@SagaStep.kind`** — the
  annotation half of the 0.7.0 saga state-machine AST
  (`SagaMetadata.transitions` / `SagaStepMetadata.kind`): a repeatable
  saga-class-level outcome-edged transition (`from` / `to` / `on` / SpEL
  `guard`; blank `to` = terminal edge, loops by pointing `to` at an earlier
  step) plus a step-kind attribute, with two annotation-side enum mirrors of
  the AST-owned types (`TransitionOutcome`
  SUCCESS/FAILURE/TIMEOUT/COMPENSATED; `StepKind` = the four AST kinds plus
  `UNSPECIFIED`, which exists only annotation-side and maps to an absent AST
  `kind`, deferring to `SagaStepMetadata.effectiveKind()` structural
  inference — the await kinds always need the explicit attribute). Shipped **reserved** (the
  `@Derived`/`@Rule` honesty pattern): no processor extraction nor `-io`
  reader consumes them yet — the coordinated `exeris-tooling` flip lands
  later (ADR-042 lock-step parity), so declaring them today has no generated
  effect.
- **`MIGRATION-0.x-to-1.0.md` skeleton** — the 1.0.0-freeze crossing guide,
  seeded ahead of time: removals landing at 1.0.0 (the two 0.2.0 `@Validation`
  deprecations — the 0.9.0 sweep added zero), what is deliberately carried
  through 1.x (`@UI`, `tenantScoped`, the reserved surfaces), the consumer
  contract recap, and the pre-freeze review backlog. To be validated against
  budgetHQ before GA.

### Changed
- **`SchemaVersion.CURRENT`** bumped `"0.8.0"` → `"0.9.0"` — `FieldMetadata`
  bounds (`min`/`max`/`minLength`/`maxLength`) moved to per-component
  `@JsonInclude(NON_NULL)`, so zero-valued bounds survive the wire (the
  NON_DEFAULT boxed-zero drop is fixed; the `ViewMetadata` NON_NULL precedent).
  Baselines stamped `"0.8.0"` read as `SCHEMA_VERSION_SKEW` — re-run codegen
  once. See [`MIGRATION.md`](MIGRATION.md#08x--09x).
- **Field/Validation canonical scoping finalized (the 0.6–0.9 deferred cut)** —
  `@Validation` is the sole declaration site for
  `min`/`max`/`minLength`/`maxLength`/`pattern`; `FieldMetadata` the sole AST
  carrier. Corpus signal: 19/19 budgetHQ usages on `@Validation` (`maxLength` ×14,
  `pattern` ×5, `min`/`minLength` ×0). Docs (CLAUDE.md, both package-infos, the
  scoping review skill) retire the "@Field owns shape hints" framing. ADR-054.
- **Capability-surface javadoc honesty refresh** — the `@CapabilityLifecycle`
  example now points at `exeris-sdk-composition-lifecycle` (the interface's
  real home), and the capability package-info's stale "reserved, not yet
  consumed" Open-Core note is rewritten to the live status: the tooling
  processor extracts the capability annotations and emits the stamped
  `cap-manifest.json`, the boot conductor (0.9.0) drives the four-phase
  lifecycle from it, and the remaining not-yet piece is the tooling-generated
  SKU bootstrap call site.
- **`composition.runtime` package-info call-site model** — the "follow-up (not
  this slice)" conductor paragraph is now the landed description: hooks-module
  split, generated-call-site status, and boundaries updated to "spec + the
  composition-lifecycle interface + a JSON mapper".
- **0.9.0 final deprecation sweep — closed with zero new deprecations** (only
  the 0.2.0 `@Validation.required`/`validateOn` remain `forRemoval`). Honesty
  refresh: `@View`/`@Region`/`@Block`/`@Bind` reclassified **consumed**
  (processor + codegen-ts view-gen; the ADR-047 field facet is the remaining
  piece, so `@UI` stays the field-level path and carries into 1.x);
  system/security field markers + `@ExerisDomain.validationMode` gain reserved
  notes (live path = override attributes / canonical names). Dispositions in
  [`ROADMAP.md`](ROADMAP.md).

### Removed
- **`ValidationMetadata`** — removed outright (no deprecation cycle): never
  populated by any processor/reader nor consumed by any generator, and with
  zero published artifacts no external dependent can exist — the window would
  have protected nobody (0.x permits the break). `FieldMetadata` is the
  canonical carrier; `notNull`/`notBlank` derive from `FieldMetadata.required`;
  `patternMessage` dropped as unconsumed. See
  [`MIGRATION.md`](MIGRATION.md#08x--09x).

### Fixed
- **Repeating `@SagaStep` compiles from outside the SDK package** — the
  `@SagaSteps` container was package-private (a top-level type inside
  `SagaStep.java`), so repeated `@SagaStep` was a compile error at every
  external use site ("container … is defined in an inaccessible class or
  interface"). The container is now `public` in its own file; same FQN, no
  wire or AST change (the container is flattened on extraction).
- **`-io` reader reads `@Validation.minLength`/`maxLength`** — restores
  reader↔processor parity (ADR-042); previously both were dropped on read
  despite processor extraction, causing spurious drift on any field declaring
  them (14 occurrences in the budgetHQ corpus).

## [0.8.0] — 2026-07-02

The events + composition milestone, cut in lockstep with kernel v0.10.0: the
AST grows the per-action streaming twin (ADR-043) and the resolved
event-payload framing (EV1, pairing with the kernel Event-Payload Codec SPI,
ADR-046); the `-io` reader closes both coordinated parity flips
(`@Field.dataType`, the streaming driver); two new composition modules ship
the ADR-024 SKU manifest spec + boot-stamp asserter; and the reserved `@View`
presentation IR seed lands. Schema `"0.7.0"` → `"0.8.0"`.

### Changed
- **`SchemaVersion.CURRENT`** bumped `"0.7.0"` → `"0.8.0"` to name the
  `ActionMetadata` streaming growth below (a `"0.7.0"` baseline now reads as
  `SCHEMA_VERSION_SKEW`). The other 0.8.0 additions ride the same schema.
- **UI kit published to GitHub Packages** — `@exeris-systems/ui-kit` on the org
  registry. The public npm-registry publish under `@exeris/ui-kit` stays a
  1.0.0 GA item.
- **Kernel v0.10.0 lockstep (docs + javadoc)** — `@DomainEvent.topic` carries
  the ADR-050 stance (binding-agnostic kernel sink on `EventTypeSpec`; the
  Community Kafka binding honours it on publish + subscribe; the in-memory bus
  treats it as advisory), anchored by a new `docs/adr/ADR-050.link.md` stub;
  the streaming attribute javadocs reflect the shipped kernel SSE SPI (ADR-043)
  + tooling emitters (`realTimeApi` live at the entity level, per-action
  pending the tooling EV1-stream producer pass, `realTimeUpdates` still inert);
  `RFC-2026-06-24` (universe data-scope) moved DRAFT → ACCEPTED on the kernel
  shared-scope decision (RFC-2026-07-02) — the build stays gated on the
  ADR-012 amendment.

### Added
- **`ActionMetadata` per-action streaming fields** — `streaming` /
  `streamEventType` / `realTimeUpdates`, the faithful AST twin of the
  `@Action` streaming attributes (the per-action SSE driver, ADR-043 /
  RFC-2026-06-22 Slice 2). Trailing components; blank `streamEventType` → null.
  See [`MIGRATION.md`](MIGRATION.md#07x--08x).
- **`DomainEventMetadata` resolved payload framing (EV1)** — `payloadFields`
  (resolved payload field *names*: `includeFields` if non-empty, else all
  `@Field` names, minus `excludeFields`) and `sensitiveFields` (verbatim), the
  AST side of the generated event-payload story (kernel ADR-046 / tooling EV1).
  Field names, not `FieldMetadata` copies; the `-io` reader shares the same
  resolution semantics (ADR-042 lock-step). Trailing components, null-list →
  empty. See [`MIGRATION.md`](MIGRATION.md#07x--08x).
- **Composition spec + runtime modules** — `exeris-sdk-composition-spec` (the
  shared SKU manifest schema + binding, ADR-024 obligation 8b) and
  `exeris-sdk-composition-runtime` (the SKU-boot composition-stamp asserter on
  that shared spec, ADR-024 obligation 8a): two new publishable jars, wired
  into the reactor and the BOM with sources/javadoc attached.
- **`-io` reader parity growth** — the reader now reads `@Field.dataType`
  (closing UI-kit gap B5, coordinated with the tooling processor flip per
  ADR-042) and the per-action streaming driver (`@Action.streaming` /
  `streamEventType`; `realTimeUpdates` stays unextracted on both sides until
  it has a generator consumer). See [`MIGRATION.md`](MIGRATION.md#07x--08x).
- **Unified presentation / front model (`@View` / `ViewMetadata`)** —
  RFC-2026-06-25 (ACCEPTED). Annotations `@View` / `@Region` / `@Block` / `@Bind`
  in `exeris-sdk-annotations`, and AST records `ViewMetadata` / `RegionMetadata` /
  `ComponentNodeMetadata` / `BindingMetadata` (+ AST-owned enums `ViewKind` /
  `BlockType` / `BindSource`) in `exeris-sdk-source-model`. A framework-neutral,
  entity-optional composition IR (view → region → component-node → binding;
  `ComponentNodeMetadata` is a recursive tree). Authored class-structure-derived;
  `@JsonInclude(NON_NULL)` with blank→null / null-list→empty normalization and
  `effective*()` defaults. **One presentation model, not a split:** `@UI` is
  being subsumed — its field-level render detail is reused as the leaf field
  facet of `ComponentNodeMetadata` (`UIMetadata.UIFieldMetadata`), its entity-level
  view selection becomes a `@View`. **Shipped reserved** — no processor/codegen/
  reader consumes it yet (the `-io` reader stays in parity, ADR-042); generation
  is the `exeris-tooling` Angular 22 emitter, gated on the emitter + a Headless
  CMS corpus. `@UI` carries a successor note but is **not** deprecated yet (it
  stays the functional path until the emitter lands). Net-new standalone records,
  not referenced by `DomainMetadata` — `SchemaVersion` unchanged (`"0.8.0"`).

### Security
- **Jackson bumped to pick up upstream security fixes** — `jackson-databind` /
  `jackson-datatype-jsr310` `3.1.2` → `3.2.0` and `jackson-annotations`
  `2.21` → `2.22` in the BOM. Adopted ecosystem-wide (`exeris-platform-bom` was
  already pinned to `3.2.0` pending this SDK BOM bump). The two versions stay on
  their separate 3.x / 2.x tracks — the pairing is dictated by `jackson-bom
  3.2.0` (`jackson.version.annotations=2.22`), and `2.22` still ships
  `JsonSerializeAs` (the AST floor). No wire-format, schema, or public-API change.

## [0.7.0] — 2026-06-18

The AST-expressiveness milestone — the SDK side of behaviour / choreography /
topology modeling is complete: projection source / read-model framing, the JVM
method behind `@Action`, the saga state machine, and the declarative
`@Derived` / `@Rule` layer. All additions are wire-additive on the `"0.7.0"`
schema; downstream generation of the new surfaces is `exeris-tooling` work.

### Changed
- **`SchemaVersion.CURRENT`** bumped `"0.6.0"` → `"0.7.0"` to name the
  `ProjectionMetadata` shape growth (a `"0.6.0"` baseline now reads as
  `SCHEMA_VERSION_SKEW`). The saga state-machine growth below rides on the same
  `"0.7.0"` schema (same release) — no second bump.

### Added
- **`ProjectionMetadata` source + read-model framing** — the record grew
  `aggregateTypes` (source aggregate link), `events` / `eventClassNames` /
  `topicPattern` (event subscription), and `model` / `schema` (read-model
  identity) alongside the existing `fields` subset, plus a `builder`, an
  `of(name, aggregateType, fields)` factory, and `hasSourceAggregate` /
  `hasFields`. The operational surface of `@Projection` (partitioning / rebuild /
  consistency / monitoring / query API) is deferred. Canonical constructor
  changed arity + order — prefer the builder / factories. See
  [`MIGRATION.md`](MIGRATION.md#06x--07x).
- **`ActionMetadata.methodName`** — the JVM method behind an `@Action`, distinct
  from the `@Action(name)` identity, with `effectiveMethodName()` falling back to
  the action name. Unlike the other 0.7.0 additions it **is** populated by the
  `-io` reader (the writer keys on the effective action name, so no write-path
  divergence). Appended at the end of the record.
- **Saga step `kind` + typed transitions** — `SagaStepMetadata` grew `kind`
  (`StepKind` = INVOKE / COMPENSATE / AWAIT_EVENT / AWAIT_TIMER) with an
  inferring `effectiveKind()`, and `SagaMetadata` grew `transitions`
  (`SagaTransition` edges = `from` → `to` `on` a `TransitionOutcome` of
  SUCCESS / FAILURE / TIMEOUT / COMPENSATED, optional SpEL `guard`, terminal on
  null `to`), with `hasTransitions`, `success`/`failure`/`timeout`/`ofOutcome`
  factories, and normalization. Promotes the step list + `dependsOn` DAG into an
  outcome-edged state-machine graph; AST-owned enums (zero-coupling). Both
  components appended at the end (constructor arity grew). Shares the `"0.7.0"`
  schema (same release as the projection growth). See
  [`MIGRATION.md`](MIGRATION.md#06x--07x).
- **Declarative-behaviour layer (`@Derived` / `@Rule`)** — RFC-2026-06-18
  (ACCEPTED). Annotations `@Derived` / `@Rule` / `@Rules` in
  `exeris-sdk-annotations`, and AST records `DerivedMetadata` (a `FieldMetadata`
  facet via `derived`) + `RuleMetadata` (a `DomainMetadata.rules` list).
  Expression stored verbatim + `language` tag (default `"spel"`); `@Rule`
  `severity` default `"ERROR"`; blank → null normalization; `dependsOn` allows
  related-entity paths. Both facets appended at the end (constructor arity grew);
  shares the `"0.7.0"` schema. Shipped reserved — no processor extraction /
  codegen consumes them yet (the `-io` reader stays in parity); generation is the
  `exeris-tooling` follow-up. See [`MIGRATION.md`](MIGRATION.md#06x--07x).

## [0.6.0] — 2026-06-17

Feedback-driven cleanups (the first slices of the 0.6.0–0.9.0 band): annotation
honesty, UI-kit gaps, and the start of the AST-expressiveness growth.

### Added
- **`EventHandlerMetadata`** AST record — the reaction-side companion to
  `DomainEventMetadata`, giving the long-shipped `@EventHandler` annotation an
  AST record to be extracted into; wired into `DomainMetadata.eventHandlers`.
- **`FieldMetadata.dataType`** (B5) — carries `@Field.dataType` semantic kind
  (`currency` / `percent` / `url`), distinct from the presentation `format`.
- **i18n message keys** (B4a) — `FieldMetadata.displayNameKey` / `descriptionKey`
  and `UIMetadata.UIFieldMetadata.placeholderKey` / `helpTextKey`, with the
  literal as fallback.
- **Custom-component escape hatch** (B4b) — `ComponentType.CUSTOM` +
  `@UI.customComponent` → `UIFieldMetadata.customComponent` (+ a `custom(...)`
  factory).
- **UI-kit** — a Tailwind v4 `@theme` entry alongside the v3 preset (B1), the
  preset/theme exposed via package `exports` (B2), and styled component classes
  for every renderable `ComponentType` (B3).

### Changed
- **`SchemaVersion.CURRENT`** bumped `"0.5.0"` → `"0.6.0"` to name the grown AST
  shape (a `"0.5.0"` baseline now reads as `SCHEMA_VERSION_SKEW`).
- **Annotation honesty pass** — `@ExerisDomain` system-field overrides,
  `@Relationship.targetEntity`, and `@Action(name=…)` are honoured by tooling;
  `@DomainEvent.topic`, the streaming/real-time attributes, and the capability
  surface carry Open-Core-status javadoc notes where they are not yet consumed.

### Wire format
- Canonical (all-args) constructors of `FieldMetadata`, `UIFieldMetadata`, and
  `DomainMetadata` gained components — prefer the builders / factories, which are
  stable. All additions are by-name JSON and back-compatible to read. See
  [`MIGRATION.md`](MIGRATION.md#05x--06x).

## 0.5.0 — 2026-06-15 — bidirectional mutation surface

### Added
- New package `eu.exeris.sdk.sourcemodel.mutation` — `MutationOp` /
  `MutationResult` / `MutationPath` / `SchemaVersion` / `SourceDigest` /
  `BaselineTrust` (ADR-042).
- `exeris-sdk-source-model-io` — AST-level three-way conflict detection
  (`SourceModelConflictDetector`) and conflict-aware application
  (`SourceModelMutationApplier`).

## 0.4.0 — 2026-06-13 — capability annotation surface

### Added
- New package `eu.exeris.sdk.annotation.capability` — `@CapabilityModule` /
  `@Provides` / `@Requires` / `@CapabilityLifecycle` (ADR-024 / ADR-038).
- AST records `CapabilityModuleMetadata` / `ProvidesMetadata` /
  `RequiresMetadata`, plus `-io` reader support.

## 0.3.0 — 2026-06-05 — source-model parser + writer

### Added
- New sibling module `exeris-sdk-source-model-io` (ADR-037): JavaParser-based
  parser (`.java` → `DomainMetadata`) and idempotent writer
  (`DomainMetadata` → `.java`). JavaParser is confined to this module so
  `source-model` stays dependency-light.

## 0.2.0 — 2026-06-03 — quality gates + pre-publish hygiene

### Added
- CI (`mvn verify` on JDK 26 + ui-kit Vitest), JaCoCo 85% gate on `source-model`,
  `AnnotationContractTest`, and `AstJsonRoundTripTest` (the wire-format guard).
- Pre-publish POM metadata for Sonatype Central Portal.

### Changed
- **Field/Validation canonical scoping** — `required` / `inCreate` / `inUpdate`
  are owned by `@Field`; `@Validation.required` and `@Validation.validateOn` are
  `@Deprecated(forRemoval = true)` (removal in 1.0.0). See
  [`MIGRATION.md`](MIGRATION.md#01x--02x).
- `jackson-annotations` pinned to `2.21` (Jackson 3 keeps annotations on the 2.x
  line).

## 0.1.0 — 2026-05-03 — scaffold

### Added
- Maven multi-module reactor (`bom`, `parent`, `annotations`, `source-model`)
  plus the `ui-kit` npm package.
- The annotation surface under `eu.exeris.sdk.annotation.*`
  (`@Retention(SOURCE)`, Apache-2.0) and the canonical Jackson-serializable AST
  under `eu.exeris.sdk.sourcemodel.ast.*`.

[0.11.0]: https://github.com/exeris-systems/exeris-sdk/releases/tag/v0.11.0
[0.10.0]: https://github.com/exeris-systems/exeris-sdk/releases/tag/v0.10.0
[0.9.0]: https://github.com/exeris-systems/exeris-sdk/releases/tag/v0.9.0
[0.8.0]: https://github.com/exeris-systems/exeris-sdk/releases/tag/v0.8.0
[0.7.0]: https://github.com/exeris-systems/exeris-sdk/releases/tag/v0.7.0
[0.6.0]: https://github.com/exeris-systems/exeris-sdk/releases/tag/v0.6.0
