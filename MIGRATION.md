# Migration guide

This document tracks user-visible changes between Exeris SDK versions and
the upgrade steps required.

> **Versioning policy.** The SDK is `0.x` until 1.0.0 GA. Breaking changes
> may land in any 0.x release; downstream consumers pin exact versions.
> Once 1.0.0 ships, semver applies (minor bumps additive only, patch bumps
> bug-fix only). See [`ROADMAP.md`](ROADMAP.md).

---

## 0.6.x → 0.7.x

### `SchemaVersion.CURRENT` bumped `"0.6.0"` → `"0.7.0"`

**Why:** the `ProjectionMetadata` growth below is a JSON-affecting AST shape
change, and the baseline-trust schema version names the AST shape (see
`eu.exeris.sdk.sourcemodel.mutation.SchemaVersion`).

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
