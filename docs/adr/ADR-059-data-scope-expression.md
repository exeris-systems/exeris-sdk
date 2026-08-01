# ADR-059: Data-scope expression — `DataScope { GLOBAL, TENANT, UNIVERSE }` supersedes the `tenantScoped` boolean

| Attribute       | Value                                                                                          |
|:----------------|:-----------------------------------------------------------------------------------------------|
| **Status**      | **ACCEPTED**                                                                                   |
| **Deciders**    | Arkadiusz Przychocki                                                                           |
| **Date**        | 2026-07-31                                                                                     |
| **Scope**       | cross-repo                                                                                     |
| **Owning Repo** | `exeris-sdk`                                                                                    |
| **Driven By**   | [RFC-2026-06-24](../rfc/RFC-2026-06-24-universe-data-scope-expression.md) (ACCEPTED 2026-07-02, Option B shape under Option C timing) |
| **Compliance**  | Deprecation pipeline ([`MIGRATION.md`](../../MIGRATION.md)); AST wire-format contract ([ADR-042](ADR-042-bidirectional-mutation-surface.md) posture); reader↔processor parity |

> This is the thin "data-scope expression" ADR RFC-2026-06-24 reserved for **build start**, deliberately not opened earlier so the registry would not carry a number for an unbuilt surface. The number is held by the [`exeris-docs/adr-index.md`](https://github.com/exeris-systems/exeris-docs/blob/main/adr-index.md) row.

## Context and Problem Statement

`@ExerisDomain` expressed data scope through exactly one knob — `tenantScoped`, a boolean whose `false` means "not tenant-partitioned" (global singleton / reference table) and whose `true` means "tenant-private rows". A third intent was unrepresentable: a dataset that many tenants **co-inhabit and read across** — rows owned by one tenant but visible to others. `tenantScoped` therefore did double duty, and the shared-world pattern had to be hand-modelled or simply went missing.

RFC-2026-06-24 settled the SDK-side shape in July and then deliberately **shipped nothing**, under the inert-attribute rule the repo learned from the streaming surface: an attribute the platform cannot honour is a lie, not a feature. It named two build triggers, both upstream:

1. the kernel-owned shared-world RFC is ACCEPTED with a carrier shape selected — **met** at kernel v0.10.0 ([kernel RFC-2026-07-02](https://github.com/exeris-systems/exeris-kernel/blob/main/docs/rfc/RFC-2026-07-02-shared-scope-isolation-tier.md));
2. the cross-tenant-readable RLS mode is **implemented** in kernel persistence — the ADR-012 in-place amendment: carrier sub-shape, a shared-scope claim, and RLS enforcement with a TCK on the read-widen and write-pin paths.

**Trigger (2) is now met.** The kernel 0.11 line carries the ADR-012 §4b amendment end-to-end: the `sharedScopeKey` carrier plus the `KernelIsolationClaims` shared-scope claim with fail-closed mapping, the persistence side publishing `exeris.shared_scope` so RLS widens reads while writes stay pinned to the owning tenant, a type-check of the claim during token validation, and `AbstractSharedScopeAccessMatrixTck` covering the access matrix. The SDK build gate is open, and this ADR records the transcription.

## 🏁 The Decision

**`@ExerisDomain.dataScope` — a single mutually-exclusive `DataScope { GLOBAL, TENANT, UNIVERSE }` discriminator — becomes the canonical expression of an entity's data-scope tier, and `@ExerisDomain.tenantScoped` is deprecated for removal at 1.0.0 with the standard fallback window (`true → TENANT`, `false → GLOBAL`). `UNIVERSE` ships reserved: the kernel enforces the tier on its 0.11 line, but the `exeris-tooling` transcription that maps the tier onto the kernel carrier is not built, so declaring `UNIVERSE` has no generated effect yet and the annotation says so.**

Two shape rulings, both following existing precedent rather than inventing:

- **The enum is duplicated, not shared.** `@ExerisDomain.DataScope` (annotation module) and `eu.exeris.sdk.sourcemodel.ast.DataScope` (source-model) are two independent types mapped by name at extraction time — the `SagaStep.StepKind` / `UI.ComponentType` precedent. The annotation side carries an extra `UNSPECIFIED` constant because annotation attributes cannot default to `null`; the AST expresses that same state as an absent field and therefore has no `UNSPECIFIED`. This preserves the annotations module's zero-dependency contract in the direction that matters — the annotation never imports the AST.
- **The fallback lives in the AST, not only in the processor.** `DomainMetadata.effectiveDataScope()` returns the explicit tier if set, else `tenantScoped ? TENANT : GLOBAL`, and never returns `null`. This is the `SagaStepMetadata.effectiveKind()` precedent, and it means a baseline or `exeris-metadata/<entity>.json` written before 0.10.0 reads back with exactly the meaning it always had — without every downstream generator re-implementing the three-way decision.

**Concrete obligations:**

1. **Annotation surface** — `@ExerisDomain.dataScope()` defaults to `DataScope.UNSPECIFIED`; `tenantScoped()` is `@Deprecated(since = "0.10.0", forRemoval = true)` with javadoc naming the replacement mapping. `UNIVERSE` carries the streaming-style Open-Core honesty note.
2. **AST surface** — `DomainMetadata` grows a **trailing** `dataScope` component (positional prefixes unchanged, by-name on the wire) plus a builder setter, and `SchemaVersion.CURRENT` moves `"0.9.0"` → `"0.10.0"`. A pre-0.10.0 baseline reads as `SCHEMA_VERSION_SKEW`, per the established posture of refusing cross-shape baselines rather than assuming compatibility.
3. **Wire-format guard** — `AstJsonRoundTripTest` covers every tier explicitly, including `GLOBAL`. `GLOBAL` is the ordinal-0 constant and therefore the one exposed to the class-level `@JsonInclude(NON_DEFAULT)` boxed-zero trap that cost the `FieldMetadata` bounds a fix in 0.9.0; here the failure mode would be worse than a lost hint — a dropped explicit `GLOBAL` falls back through `effectiveDataScope()` and can read the entity back as `TENANT`. A second case pins that an **absent** tier stays absent on the wire rather than being materialised by the reader.
4. **`-io` parity is deliberately NOT taken in this slice.** Per the ADR-042 discipline, the reader reads what the processor writes; the `exeris-tooling` processor does not extract `dataScope` yet, so `-io` reading it would manufacture drift. The reader's `unmodeledFacets()` guard is unaffected — it keys on annotation types, not attributes, so the new attribute does not re-arm it. Reader parity lands lockstep with the processor.
5. **`exeris-tooling` (downstream, not this repo)** — extract `dataScope`; read `tenantScoped` as a fallback **with a build warning** while `dataScope` is `UNSPECIFIED`; reject a declared tier that contradicts a declared `tenantScoped` rather than silently resolving it; map `UNIVERSE` onto presence of the kernel `sharedScopeKey` carrier and `TENANT`/`GLOBAL` onto its absence. Until that lands, `UNIVERSE`'s reserved note stands.

## Consequences

**Positive.** The nonsensical "shared *and* row-private" state is unrepresentable rather than validated away after the fact. The tier reads as one dropdown in Studio/LSP. The SDK↔kernel boundary stays clean: the SDK encodes author intent, never the `StorageContext` carrier — the same boundary it held for streaming, where the SDK said `streaming = true` and the framing stayed kernel-side.

**Negative / accepted cost.** Deprecating a long-standing public attribute is a real 0.x migration for anything pinning it, bounded by the fallback window and by-name JSON. And the window forces sequencing: **deprecate at 0.10.0, remove at 1.0.0** — a deprecation and its removal cannot share a release, and after the freeze there is no additive minor in which to remove anything. Landing `dataScope` inside the 1.0.0 cut instead would have frozen `tenantScoped` into the whole 1.x line with removal deferred to 2.0. That sequencing constraint is the reason this is a pre-1.0 item at all, and it is why `0.10.0` exists as a milestone.

**Honesty debt, tracked.** `UNIVERSE` is a reserved value in a published `0.x` artifact — exactly the shape the 0.6.x honesty pass exists to police. It is acceptable here only because the note is explicit, the enforcing kernel binding demonstrably exists, and the remaining gap is one named downstream slice rather than an open-ended "someday". If the tooling transcription does not land, the honest move is to say so in the note, not to quietly let it read as live.

## Open questions

Carried forward from RFC-2026-06-24, unchanged by this slice:

- **`UNIVERSE` row-ownership expression** — whether the SDK needs to name the owning-tenant field (a `@UniverseOwner` marker paralleling `@TenantId`) or ownership stays purely a kernel/runtime concern. Decide against the landed ADR-012 §4b carrier sub-shape.
- **`tenantPolicy` (STRICT/LENIENT) under three tiers** — per-tier validation policy vs one entity-level knob; `UNIVERSE` may want guards distinct from `TENANT`'s.
- **Interaction with `ProjectionMetadata`** — the public-projection-vs-private-state split is the same shared-vs-private distinction at the read-model layer.
