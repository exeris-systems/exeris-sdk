# Migration guide

This document tracks user-visible changes between Exeris SDK versions and
the upgrade steps required.

> **Versioning policy.** The SDK is `0.x` until 1.0.0 GA. Breaking changes
> may land in any 0.x release; downstream consumers pin exact versions.
> Once 1.0.0 ships, semver applies (minor bumps additive only, patch bumps
> bug-fix only). See [`ROADMAP.md`](ROADMAP.md).

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
