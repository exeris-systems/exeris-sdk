# 02 — Modelling an entity

← [Guide index](README.md) · [01 — Getting started](01-getting-started.md)

Every status label here is backed by the [status matrix](04-status-matrix.md),
which names the `exeris-tooling` site that proves it.

## `@ExerisDomain` — the root

**LIVE.** `@Target(TYPE)`. Declares an aggregate root and starts the generation
chain. 35 declared attributes; 20 reach the AST; 13 change emitted output.

### The attributes that do something

| Attribute | Status | Effect |
|---|---|---|
| `module` | LIVE | Package structure, API path prefix, topic and cache naming. |
| `path` | LIVE | Base REST path. Must start with `/`. |
| `aggregate` | LIVE | Aggregate name; defaults to the class name. |
| `description` | LIVE | Carried into OpenAPI and the DSL output. |
| `apiVersion` | LIVE | Appears in the URL and in the generated clients. |
| `restApi` | LIVE | Gates the REST surface. |
| `realTimeApi` | LIVE | Entity-level SSE stream route + the TypeScript `EventSource` client (ADR-043). |
| `dataScope` | LIVE | Tenant partitioning tier — see below. |
| `softDelete` | LIVE | `deleted_at` column, filtered reads, restore path. |
| `audited` | LIVE | Created/updated audit columns and their repository handling. |
| `versioned` | LIVE | Optimistic-locking version column. |
| `sensitive` | LIVE | Read by table naming and by the event emitters. |
| `tenantScoped` | **DEPRECATED** | Removed at 1.0.0 — see below. |

### The attributes that reach the AST and stop

`graphqlApi`, `internalClient`, `cacheable`, `cacheTtl`, `cacheRegion`,
`fullTextSearch`, `searchConfig` — all **PARTIAL**. They are extracted and they
appear in the wire schema; no generator reads them. `graphqlApi` is the one worth
naming explicitly: **there is no GraphQL emitter in the toolchain at all.**

### The attributes that reach nothing

**RESERVED** (15): `primaryKeyField`, `tenantIdField`, `versionField`,
`softDeleteField`, `softDeleteTimestampField`, `softDeletedByField`,
`createdAtField`, `createdByField`, `updatedAtField`, `updatedByField`,
`permissions`, `roles`, `tags`, `validationMode`, `ui`.

The ten `*Field` attributes are the naming-override surface for the system
columns; the generator derives those names itself today. `ui` is the nested-form
member — see [the trap](README.md#1-the-nested-form-is-a-silent-no-op).

### Data scope

`tenantScoped` was replaced in 0.10.0 by the three-tier `dataScope` (ADR-059):

| Tier | Meaning | Emitted |
|---|---|---|
| `GLOBAL` | One row set shared by every tenant. | No tenant column, no RLS. |
| `TENANT` | Partitioned; no tenant reads another's rows. | Tenant column, RLS policy, query filter. |
| `UNIVERSE` | The cross-tenant shared-world tier. | **The full `TENANT` shape.** |
| `UNSPECIFIED` | Defers to `tenantScoped`. | Whatever that resolves to. |

`UNIVERSE` needs care. The generator's partitioning predicate is phrased as
"scope is not `GLOBAL`", deliberately, so an unrecognised tier can never fall
through to the unpartitioned path. That makes `UNIVERSE` **fail-closed** — it
emits the tenant-partitioned shape, which is strictly narrower than the tier
declares. The cross-tenant widening is the half that does not exist yet. Declare
`UNIVERSE` to record intent; do not expect cross-tenant reads from it.

`UNSPECIFIED` is the default and falls back to `tenantScoped` for the remainder
of the deprecation window, which closes at 1.0.0. Declaring both with
contradicting values is a build-time error, not a silent precedence rule.

## `@Field` — field shape and lifecycle

**LIVE.** `@Target(FIELD)`. 29 declared, 14 extracted, 13 consumed.

`@Field` owns *what the field is*: data shape, persistence, lifecycle. It
declares **no constraint attributes** — those live on `@Validation`.

| Attribute | Status | Effect |
|---|---|---|
| `label` | LIVE | Display name across every target. |
| `description` | LIVE | Carried into OpenAPI and the UI. |
| `required` | LIVE | NOT NULL / not-blank are *derived* from this, not separately declared. |
| `unique` | LIVE | Unique constraint in the migration. |
| `indexed` | LIVE | Index in the migration. |
| `searchable` | LIVE | Search index and store support. |
| `sortable` | LIVE | Sort support in the query builder and list. |
| `filterable` | LIVE | Filter support end to end — repository, service, query builder, list. |
| `readOnly` | LIVE | Excluded from write shapes; reflected in the form and guards. |
| `dataType` | LIVE | Presentation type hint (currency / percent / url / …). |
| `computed` | LIVE | Marks the field as derived. |
| `computedFrom` | LIVE | Source field names; read by the form generator. |
| `inCreate` | LIVE | Field appears in the create shape. |
| **`inUpdate`** | **PARTIAL** | Extracted, in the wire schema, **read by no generator**. |

`inUpdate` deserves a flag of its own. `@Validation.validateOn` is deprecated in
favour of `inCreate` / `inUpdate` — and only half of that replacement has a
consumer. Migrating `validateOn = UPDATE` lands you on an attribute that
currently does nothing. Recorded here rather than left to be discovered.

**RESERVED** (15): `labelKey`, `descriptionKey`, `defaultValue`, `order`,
`group`, `inList`, `inDetail`, `cssClass`, `encrypted`, `maskPattern`,
`sensitive`, `writeOnly`, `compositeUnique`, `ui`, `validation`.

Note that `labelKey` / `descriptionKey` are the i18n message keys. Older javadoc
in this repo presented them as the live 0.6.0 i18n path; they are not extracted
by anything, in either producer.

## `@Validation` — the constraint rules

**LIVE**, narrowly. `@Target(FIELD)`. 24 declared, 5 extracted, 2 more read as a
deprecated fallback.

`@Validation` is the **sole declaration site** of constraint rules. The AST
carrier is `FieldMetadata` — a single record for both annotations. There is no
parallel validation record; ADR-054 removed it outright in 0.9.0.

| Attribute | Status |
|---|---|
| `min`, `max` | LIVE — carried into `FieldMetadata`. |
| `minLength`, `maxLength` | LIVE — same. |
| `pattern` | LIVE — same. |
| `required` | **DEPRECATED** → `@Field.required`. Read as fallback with a build warning until 1.0.0. |
| `validateOn` | **DEPRECATED** → `@Field.inCreate` / `inUpdate`. Same window. See the `inUpdate` caveat above. |

Everything else is **RESERVED** (17): `email`, `url`, `uuid`, `positive`,
`positiveOrZero`, `negative`, `negativeOrZero`, `future`, `futureOrPresent`,
`past`, `pastOrPresent`, `decimalMin`, `decimalMax`, `message`, `groups`,
`customValidator`, `asyncValidator`.

This is the largest single gap between what the annotation reads like and what it
does. `@Validation(email = true)` is a no-op today. Only the five numeric/string
bounds and the pattern survive to the AST.

Zero-valued bounds do survive the wire: `@JsonInclude(NON_DEFAULT)` would
otherwise drop boxed zero, so the four `FieldMetadata` bound components carry a
per-component `NON_NULL` (ADR-054). `minLength = 0` means what it says.

## `@Relationship` — entity references

**LIVE.** `@Target(FIELD)`. 23 declared, 6 extracted.

| Attribute | Status | Effect |
|---|---|---|
| `targetEntity` | LIVE | Required. Preferred over the field's Java type. |
| `relationshipType` | LIVE | `MANY_TO_ONE` (default), `ONE_TO_MANY`, `ONE_TO_ONE`, `MANY_TO_MANY`. |
| `cascadeDelete` / `cascadeUpdate` | LIVE | Folded into the AST cascade enum; drives `ON DELETE CASCADE`. |
| `displayField` | LIVE | Required. Read by the form and detail generators. |
| `mappedBy` | **PARTIAL** | Extracted; no generator reads it. |

`relationshipType` is load-bearing and was, for a while, silently broken: the
processor read the AST's name for the attribute (`type`) rather than the
annotation's (`relationshipType`), so every relationship was recorded as
`MANY_TO_ONE`. Since the migration, repository, service and foreign-key emitters
all gate on `MANY_TO_ONE`, a `ONE_TO_MANY` side was emitting an FK column, its
index, its constraint and a finder that belong on the other side. Fixed in
`exeris-tooling`, with regression tests. If `ROADMAP.md` or `MIGRATION.md` still
says this attribute "is never extracted", those lines are stale — it is
extracted, and it matters.

**RESERVED** (17): `required`, `searchable`, `orderBy`, `filter`, `fetchType`,
`display`, `displayFields`, `displayTemplate`, `labelField`, `searchFields`,
`icon`, `allowCreate`, `allowInlineCreate`, `allowInlineEdit`,
`autocompleteThreshold`, `dependsOn`, `dependsOnField`.

## `@Action` — domain methods as endpoints

**LIVE.** `@Target(METHOD)`. 28 declared, 5 extracted.

| Attribute | Status | Effect |
|---|---|---|
| `description` | LIVE | OpenAPI and DSL output. |
| `httpMethod` | LIVE | Verb for the generated route. |
| `async` | LIVE | Async handling in the DSL and store. |
| `streaming` | LIVE | One `HttpStreamHandler` per streaming action, plus the stream route and the RxJS client (ADR-044 slice 2). |
| `streamEventType` | LIVE | Event type on that stream. |

**RESERVED** (23) — including `name`, `label`, `path`, `permissions`, `roles`,
`confirm`, `visibleWhen`, `successMessage`, `errorMessage`, `variant`,
`location`, `order`, `bulk`, `timeout`, `refreshAfter`, `redirectTo`,
`closeAfter`, `showProgress`, `showResultMessage`, `debounce`, `maxConcurrent`,
`ui`, and `realTimeUpdates`.

`realTimeUpdates` is deliberately not extracted: it is a separate
subscribe-to-progress affordance from `streaming`, and extracting it without a
consumer would only add an inert AST attribute. The extraction is meant to land
in the same change as its generator.

## `@ActionParam` — action parameters

**PARTIAL**, and one known bug. `@Target(PARAMETER)`. 33 declared, 3 read.

| Attribute | Status |
|---|---|
| `description` | LIVE |
| `required` | LIVE (defaults to `true`, mirroring the annotation) |
| `label` | **Declared, and not read.** |

The parameter's own name always reaches the AST — it comes from the method
signature, not the annotation.

The `label` case is a genuine defect rather than a design gap: the processor
reads the key `"displayName"` from `@ActionParam`, and `@ActionParam` declares
`label`. The read always misses, so `ActionParamMetadata.displayName` is
permanently null. It lives in `exeris-tooling`, not here; it is recorded in this
guide so that nobody spends an afternoon on it.

**RESERVED** (30): everything else — `options`, `optionLabels`, `placeholder`,
`helpText`, `group`, `gridSpan`, `visibleWhen`, `enabledWhen`, `requiredWhen`,
`defaultValue`, `defaultValueExpression`, `transformExpression`,
`optionsExpression`, `masked`, `encrypted`, `allowedFileTypes`, `maxFileSize`,
`multipleFiles`, `nullable`, `format`, `icon`, `order`, `dataType`,
`relationship`, `validation`, `ui`, and the option-field trio.

## System columns

Worth restating, because it is the most common wrong assumption about this SDK:

```java
// Does nothing. @TenantId is extracted by no reader.
@Field(label = "Organization")
@TenantId
private UUID organizationId;

// This is what creates the tenant column, the RLS policy and the query filter.
@ExerisDomain(module = "sales", path = "/orders",
              dataScope = ExerisDomain.DataScope.TENANT)
public class Order { }
```

The whole `eu.exeris.sdk.annotation.system` package (`@PrimaryKey`, `@TenantId`,
`@Version`, `@SoftDelete`, `@SoftDeleteTimestamp`, `@SoftDeletedBy`, the four
`@Audit*`) and the whole `security` package (`@Encrypted`, `@RowLevelSecurity`)
are RESERVED. Their javadoc describes generated encryption and RLS policies that
no Open-Core generator implements.

The flags that do the work: `dataScope`, `softDelete`, `audited`, `versioned` —
consumed by the Flyway and repository generators. The `*Field` naming overrides
on `@ExerisDomain` are the intended future way to rename the derived columns, and
are themselves RESERVED.

---

Next: [03 — Behaviour and presentation](03-behaviour-and-presentation.md)
