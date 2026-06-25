# RFC-2026-06-24: How should `@ExerisDomain` express a shared-world / `universe` data-scope at design time?

| Field             | Value                                                                 |
|:------------------|:----------------------------------------------------------------------|
| **Status**        | **DRAFT**                                                            |
| **Author(s)**     | arkstack-dev                                                          |
| **Date Opened**   | 2026-06-24                                                            |
| **Date Closed**   | —                                                                    |
| **Target ADR(s)** | TBD — a thin SDK-side "data-scope expression" ADR, coordinated with the kernel-owned **ADR-012 amendment** (the isolation-model decision). Number reserved in `exeris-docs/adr-index.md` only once the kernel-owned RFC selects a shape (cross-repo, same protocol as ADR-037/038). |
| **Affected Repos**| `exeris-sdk` (annotation attribute + AST field), `exeris-tooling` (processor extraction + mapping the author's scope intent onto the kernel `StorageContext` carrier the kernel RFC picks), `exeris-kernel` (**upstream blocker** — the isolation-model decision, ADR-012), `exeris-platform` (LSP/Studio rendering of the scope tier) |
| **Reviewers**     | —                                                                    |

## Question

The SDK's data-scope surface today offers exactly one knob: `@ExerisDomain(tenantScoped = …)` — a boolean whose `false` means "not tenant-partitioned" (a global singleton / reference table) and whose `true` means "tenant-private rows" (row-level isolation, one tenant never sees another's rows). There is **no way to express a third intent**: a dataset that *many tenants co-inhabit and read across* — rows owned by a tenant but visible to others (a shared world, a common reference dataset, a cross-tenant collaboration space). **How should the SDK's design-time surface express that `universe` / shared-world tier — what annotation shape, and with what commitment/timing — given that the kernel cannot honor it yet (no cross-tenant-readable RLS mode exists) and the isolation-model decision is owned upstream?**

## Context

This question is asked now because the **kernel has formally put the matching gap on its own track**. `exeris-kernel/docs/ROADMAP.md` (the *"Multi-Tenancy: Shared-World / `Universe` Scope Tier — RFC Track"* entry) records that the kernel models isolation through a single tenant-scoping primitive (`StorageContext.isolationKey`, ADR-012) and has *no* scope tier for a shared world that multiple tenants observe and mutate together — so `isolationKey` is forced to do double duty (one key meaning both "tenant" and "shared world"), which either over-isolates (no sharing possible) or under-isolates (the tenant boundary leaks). The gap was surfaced by kernel-side downstream dogfooding (a multi-service build; kernel ROADMAP finding **K3, High**). The kernel names the owner as *"Security / Persistence subsystem (isolation model) + **SDK/Tooling (scope expression at design time)**"* — this RFC is that second half.

The SDK side of the same gap is already logged in `ROADMAP.md`: *"No `universe` data-scope tier — the surface offers only global-singleton and tenant-private (`tenantScoped`), so a dataset that many tenants co-inhabit and read across … is unmodellable and `tenantScoped` does double duty; needs a third kernel RLS mode."* It is explicitly paired with the projection item — the "public-projection-vs-private-state split" the read-model surface wants is the same shared-vs-private distinction at a different layer.

The cost of the wrong answer is the SDK's most recently-learned lesson. The 0.6.x roadmap section *"annotation-surface honesty — inert attributes"* removed attributes that advertised behaviour no consumer honoured, and the streaming saga (`realTimeApi` / `@Action(streaming)`) proved the discipline end-to-end: those attributes sat **inert** for releases until the kernel SPI (ADR-043) landed to back them, and only then did they gain meaning. Shipping a `universe` scope value the kernel's RLS cannot enforce would manufacture exactly that anti-pattern again — a published `0.x` attribute that lies about what the platform does. So the timing question (ship-now vs design-now / build-on-kernel) is as load-bearing as the shape question.

This RFC's deliverable is therefore the **agreed SDK-side scope-expression design** plus an **explicit build gate** tied to the kernel-owned decision — not a shipped attribute. When the kernel RFC selects an isolation shape, the SDK side is a transcription, not a re-design.

## Investigation

### Prior art

- **Within the SDK** — the data-management flags (`tenantScoped`, `softDelete`, `audited`, `versioned`) are all booleans on `@ExerisDomain`, each paired with a marker annotation (`@TenantId`, `@SoftDelete`, `@AuditCreatedAt`, …) and an AST-twin boolean on `DomainMetadata`. `tenantScoped` additionally carries `tenantIdField` and a `tenantPolicy` (STRICT/LENIENT) escalation knob. Any scope expression the SDK adds must sit naturally beside that established convention.
- **The inert-attribute rule (this repo, 0.6.x) and the streaming precedent (0.8.0)** — the hard-won discipline that an annotation must be consumed end-to-end or carry an explicit "reserved / not yet honoured" honesty note and a deprecation/realisation path. `realTimeApi` and `@Action(streaming)` are the worked example: declared shape shipped, AST carried it, but the attributes stayed inert (with a javadoc-honesty note) until the kernel affordance (ADR-043) landed. A `universe` scope must follow the same posture — it is meaningless until the kernel RLS mode exists.
- **The additive-surface discipline + reader↔processor parity (ADR-042)** — every AST growth (saga / event-handler / projection / declarative-behaviour) shipped additive and by-name, with `-io` *not* reading what the processor does not yet write. A scope attribute is the same kind of change on `DomainMetadata`.
- **External shape-setters** — multi-tenancy frameworks converge on an *enumerated* discriminator, not a pile of booleans: Hibernate / `@TenantId` partition strategies, and the common "GLOBAL / TENANT / SHARED" data-residency taxonomies, all model the tier as one mutually-exclusive choice. Two independent booleans (`tenantScoped` + a new `universeScoped`) would encode four states, two of which are nonsensical (both-true; the "shared but also tenant-private" contradiction the kernel ROADMAP explicitly warns against).

### Constraints

- **Zero runtime coupling.** The SDK expresses *author intent* only ("this entity is universe-scoped"). It must not encode any kernel isolation mechanism — no `universeKey` carrier, no RLS policy, no `StorageContext` shape. Which `StorageContext` carrier the intent maps to (orthogonal `universeKey` vs reserved `isolationKey` sentinel vs composite scope carrier — the three shapes the kernel RFC enumerates) is a **kernel + tooling** concern, downstream of this surface. This is the same boundary as streaming: the SDK says `streaming = true`; the kernel SPI shape (`StreamEvent`, `HttpStreamHandler`) is downstream.
- **Inert-attribute honesty.** A shipped `universe` value the kernel cannot enforce is a regression the moment it lands. Either it ships **reserved** (honesty note, AST carries it, no enforcement claimed) *after* the kernel RFC is accepted, or it does not ship.
- **Wire-format contract (ADR-037) + parity (ADR-042).** Any AST change is a record growth, additive, by-name, round-trip-tested; `-io` reads it only once the processor extracts it.
- **Fail-closed default.** Whatever the shape, the *absence* of an explicit universe declaration must mean today's behaviour (tenant-private or global as `tenantScoped` already decides). A misconfigured or unmigrated entity must never silently widen visibility — this mirrors the kernel RFC's own fail-closed merge-gate requirement.

### Data gathered

- The scope surface is precisely `@ExerisDomain.tenantScoped` (`ExerisDomain.java:179`) + `tenantIdField` (`:316`) + `tenantPolicy` STRICT/LENIENT (`:402`), with the AST twin `DomainMetadata.tenantScoped` (`DomainMetadata.java:54`). No `universe` / `sharedWorld` / `scope` attribute exists in the annotation or the AST today — greenfield on the SDK side.
- The kernel's `IsolationStrategy` (`exeris-kernel-spi/.../security/StorageContext.java`) is `SHARED` / `SEPARATED_SCHEMA` / `DEDICATED` — all *physical* isolation strategies, all tenant-private at the row-visibility level. There is **no** cross-tenant-readable row-visibility mode, and none is implemented; the kernel ROADMAP entry is a *decision-only* RFC track (its merge gate is explicitly "no kernel SPI commits in this gate"). So the SDK build is upstream-blocked with no committed ETA.
- The kernel RFC has not yet been opened (no `RFC-*Shared-World*` file exists in `exeris-docs/` as of this RFC's date); it is slated as a kernel v0.10 decision-only item. This SDK RFC therefore *precedes or accompanies* it and supplies the "scope expression at design time" input the kernel entry names.

## Options Considered

The non-controversial parts are fixed by precedent: the attribute lives on `@ExerisDomain` in `exeris-sdk-annotations`, its twin on `DomainMetadata` in `exeris-sdk-source-model`, the mapping to a kernel carrier is `exeris-tooling`, and any new AST field is additive/by-name/round-tripped. The genuinely open forks are **(1) the shape of the scope expression** and **(2) commitment / timing**.

### Fork 1 — shape of the scope expression

#### Option A: a second boolean — `universeScoped` beside `tenantScoped`

Add `boolean universeScoped() default false` alongside the existing `tenantScoped`.

**Pros:** smallest diff; mirrors the existing boolean-flag convention literally.
**Cons:** two independent booleans encode four states, two of which are meaningless — `universeScoped && tenantScoped` is the precise "shared but also row-private" contradiction the kernel ROADMAP warns against, and the SDK would have to *validate it away* at build time (a constraint the type system should have made unrepresentable). Perpetuates the double-duty muddle one level up instead of resolving it.
**Cost:** low diff, high modelling debt.

#### Option B: an enumerated tier — `DataScope { GLOBAL, TENANT, UNIVERSE }`

Replace the boolean intent with a single mutually-exclusive discriminator: `@ExerisDomain(dataScope = DataScope.UNIVERSE)`, where `GLOBAL` = today's `tenantScoped=false`, `TENANT` = today's `tenantScoped=true`, and `UNIVERSE` = the new shared-world tier. The AST twin is an **AST-owned enum** field on `DomainMetadata` (no annotation-enum mirror duplicated — the saga-enum precedent). `tenantScoped` runs the deprecation pipeline (`@Deprecated(forRemoval=true)`, processor reads it as a fallback that maps `true→TENANT` / `false→GLOBAL` for at least one minor, per `MIGRATION.md`).

**Pros:** mutually exclusive by construction — the nonsensical states are unrepresentable; one obvious place to add a *fourth* tier later if one ever appears; matches the external "enumerated residency tier" convention; cleanly separates *author intent* (the tier) from *kernel mechanism* (the carrier the kernel RFC picks). Reads well in Studio/LSP as a single dropdown.
**Cons:** deprecates a long-standing public attribute (`tenantScoped`) — a `0.x` migration cost, though exactly the kind the SDK's deprecation policy is built for, and additive on the wire (old `tenantScoped` JSON still reads back via the fallback during the window).
**Cost:** moderate (a deprecation cycle); the cleanest long-run surface.

#### Option C (do-nothing-for-now / design-on-paper): accept the framed shape, gate the build on the kernel decision

Settle the shape (Option B) on paper now as this RFC's deliverable, but ship **nothing** until the kernel-owned isolation RFC selects a carrier shape and the RLS mode is implemented. When that lands, the SDK side is a transcription: introduce the `DataScope` enum + attribute + AST field, ship `UNIVERSE` **reserved** with the streaming-style honesty note until the kernel enforcement is live, then flip the note.

**Pros:** discipline-consistent — exactly the streaming / declarative-behaviour posture (design now, build on a named trigger); avoids manufacturing a fresh inert attribute; no premature `0.x` lock of a value the platform can't honour; the expensive part (the surface debate) is captured now and ready to execute.
**Cons:** nothing ships this milestone; relies on the kernel RFC actually progressing — but the cost of waiting is zero because the shared-world pattern is hand-modelled (or simply absent) today regardless, and the SDK has no enforcement to offer until the kernel does.
**Cost:** lowest; the design *is* the deliverable.

## Recommendation

**Adopt Option B's shape — a single AST-owned `DataScope { GLOBAL, TENANT, UNIVERSE }` discriminator replacing the `tenantScoped` boolean's intent — under Option C's timing: settle the shape now, ship nothing until the kernel-owned isolation RFC (the ADR-012 amendment track) selects a carrier and the cross-tenant-readable RLS mode is implemented. When the kernel backs it, introduce the enum + attribute + AST field additively, ship `UNIVERSE` reserved with the streaming-style "not yet enforced in Open-Core" honesty note, and run `tenantScoped` through the standard deprecation pipeline.**

The shape is decided by the modelling failure of the alternatives: a second boolean (Option A) makes the "shared *and* row-private" contradiction representable and then forces the SDK to validate it away, whereas an enumerated tier makes it unrepresentable — the right level of strictness for a load-bearing isolation intent, and the convention every external multi-tenancy taxonomy already converges on. The enum also draws the SDK/kernel boundary cleanly: the SDK expresses one of three *author intents*, and the kernel RFC's choice between an orthogonal `universeKey`, a reserved `isolationKey` sentinel, or a composite carrier is a pure mapping concern for `exeris-tooling` + the kernel — the SDK never encodes the mechanism, exactly as it never encoded `text/event-stream` framing for streaming.

The timing is decided by the inert-attribute rule the SDK just spent two roadmap sections internalising. `universe` is meaningless without a kernel RLS mode that makes rows cross-tenant-readable, and that mode does not exist and is not yet even an accepted decision (the kernel track is decision-only, "no SPI commits in this gate"). Shipping the value now would re-create precisely the `realTimeApi` / `@Action(streaming)` inert-attribute situation the 0.8.0 work just resolved. So the SDK's correct move is the streaming move: settle the shape, name the trigger, and build when the upstream affordance lands.

### Build trigger — NOT yet met (named for when it is)

The build starts when **both** hold: (1) the kernel-owned Shared-World / Universe RFC is **ACCEPTED** with a carrier shape selected (ADR-012 amended or a companion ADR reserved), and (2) the cross-tenant-readable RLS mode is implemented in kernel persistence (an `IsolationStrategy` extension or equivalent that the processor can target). Until both hold, this RFC's output is the agreed shape, nothing more. The SDK should **not** open its target ADR or reserve a number until (1) — to avoid a reserved-but-unrealised ADR, the same honesty applied to attributes.

### Why not the alternatives?

- **Option A (second boolean)** — makes a contradictory state (`universeScoped && tenantScoped`) representable and pushes the burden onto build-time validation; the enum makes the bad state unrepresentable instead.
- **Ship-now (any shape before the kernel)** — manufactures a fresh inert attribute, the exact anti-pattern the 0.6.x honesty pass and the streaming saga eliminated.
- **Keep `tenantScoped`, never deprecate** — leaves the boolean doing permanent double duty and offers no place for the third tier; the kernel ROADMAP identifies that double-duty collapse as the root problem.

### Risks of the recommendation

- **The kernel RFC may pick a carrier whose author-facing intent isn't a clean three-way tier** (e.g. a `universeKey` that is *orthogonal* to tenant, so an entity could be both tenant-scoped *and* universe-keyed). If so, the three-value enum is too flat and the SDK surface reshapes — bounded by the fact that nothing has shipped, so the cost is editing this RFC, not unwinding a published attribute. Flagged as the primary open question below.
- **Deprecating `tenantScoped` is a real `0.x` migration** for any consumer that pins it — bounded by the standard deprecation policy (fallback-with-warning window, `MIGRATION.md` entry, by-name JSON keeps old baselines readable).
- **The trigger may never fire** (the kernel may defer the isolation change indefinitely) — acceptable: the SDK loses nothing, since it has no enforcement to offer regardless, and the design stays captured.

## Decision Record

<Pending — Status is DRAFT. This RFC settles the SDK-side *shape* recommendation but cannot reach ACCEPTED until the kernel-owned Shared-World / Universe RFC selects a carrier (Fork-1 risk above turns on it). Flip to ACCEPTED once the kernel decision lands and the three-way-tier assumption holds; record the resulting coordinated ADR(s) then.>

| Field                | Value     |
|:---------------------|:----------|
| **Outcome**          | — (DRAFT) |
| **Date**             | —         |
| **Resulting ADR(s)** | TBD — coordinated with the kernel ADR-012 amendment; number reserved in `exeris-docs/adr-index.md` only after the kernel RFC is accepted. |
| **Notes**            | Recommended shape: AST-owned `DataScope { GLOBAL, TENANT, UNIVERSE }` discriminator (replacing `tenantScoped`'s intent, with deprecation fallback `true→TENANT`/`false→GLOBAL`); `UNIVERSE` ships reserved with a streaming-style honesty note until kernel RLS enforcement is live; SDK encodes author intent only, never the `StorageContext` carrier. Build gated on the kernel-owned isolation RFC. Pairs with the `ProjectionMetadata` public-vs-private-state item (`ROADMAP.md`). |

## Open questions / follow-ups

- **Does the kernel's chosen carrier keep `universe` mutually exclusive with `tenant`, or orthogonal?** — the decisive input for whether the SDK surface is a flat three-way enum (this RFC's recommendation) or needs an orthogonal universe-key facet beside `tenantScoped`. Owner: tracked against the kernel-owned Shared-World / Universe RFC; resolve before this RFC flips to ACCEPTED.
- **`UNIVERSE` row-ownership expression** — a universe row is "owned by a tenant but readable across tenants"; does the SDK need to name the owning-tenant field (a `@UniverseOwner`-style marker, paralleling `@TenantId`), or is ownership purely a kernel/runtime concern? Decide with the kernel carrier shape, not now.
- **Interaction with `ProjectionMetadata`** — the public-projection-vs-private-state split (`ROADMAP.md`) is the same shared-vs-private distinction at the read-model layer; relate the two surfaces when the universe tier is built (a projection over a `UNIVERSE` aggregate may be the natural "public read view").
- **`tenantPolicy` (STRICT/LENIENT) under three tiers** — whether the existing policy escalation generalises to a per-tier validation policy or stays tenant-specific. Revisit at build time.
