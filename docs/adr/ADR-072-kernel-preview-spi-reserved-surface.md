---
title: "ADR-072: The kernel 0.11 preview SPI gets a design-time surface in 0.11.0 — reserved, and outside the 1.0.0 freeze"
type: adr
visibility: public
owning-repo: exeris-sdk
status: active
last-verified: 2026-09-04
slug: adr/ADR-072
---

# ADR-072: The kernel 0.11 preview SPI gets a design-time surface in 0.11.0 — reserved, and outside the 1.0.0 freeze

| Attribute       | Value                                                                                                     |
|:----------------|:----------------------------------------------------------------------------------------------------------|
| **Status**      | **ACCEPTED**                                                                                              |
| **Deciders**    | Arkadiusz Przychocki                                                                                      |
| **Date**        | 2026-08-18                                                                                                |
| **Scope**       | cross-repo                                                                                                |
| **Owning Repo** | `exeris-sdk`                                                                                              |
| **Driven By**   | [`ROADMAP.md`](../../ROADMAP.md) §"Kernel 0.11 SPI — dispositioned, deliberately not pre-1.0" — this ADR amends that disposition |
| **Compliance**  | AST wire-format contract; record-growth stance ([`MIGRATION-0.x-to-1.0.md`](../../MIGRATION-0.x-to-1.0.md) §3); Field/Validation canonical scoping ([ADR-054](ADR-054-field-validation-min-max-pattern-cut.md)); inert-attribute honesty rule |

> **Registry note.** Drafted as `ADR-070` against a stale local checkout of [`exeris-docs`](https://github.com/exeris-systems/exeris-docs); upstream, `070` belongs to `exeris-tooling` (generated composition root) and `071` to the kernel (operational-limits configuration path), so this is `072`. The house rule is to reserve the number in `exeris-docs/adr-index.md` **before** writing the content, and that ordering was inverted here — the index row is owed. Fetch before taking a number; the ecosystem shares one namespace and a local index goes stale silently.

## Context and Problem Statement

Kernel v0.11 shipped two net-new SPI packages on the distributed line: `eu.exeris.kernel.spi.storage.blob` ([kernel ADR-056](https://github.com/exeris-systems/exeris-kernel/blob/main/docs/adr/ADR-056-blob-storage-provider-spi.md)) and `eu.exeris.kernel.spi.scheduling` ([kernel ADR-057](https://github.com/exeris-systems/exeris-kernel/blob/main/docs/adr/ADR-057-job-scheduler-spi.md)). Both name an Entity-First gap explicitly: there is no way for a domain author to declare *"this entity has an attachment"* or *"run this action on a schedule"*, and kernel ADR-056 names the consumer by name — "`exeris-tooling` gains something to generate against".

This repo's ROADMAP dispositioned both as **"1.x, not before"**, on one argument: the kernel's own `docs/stability-matrix.md` holds each package at tier **`preview`**, and freezing an SDK annotation against a preview SPI would invert the stability ordering — the SDK's 1.0 promise would be stronger than the surface it encodes.

**That argument is still sound, and it is not what this ADR overturns.** What it actually forbids is *freezing*, and it was written when 0.10.0 was believed to be the last milestone before the freeze, so "not pre-1.0" and "not frozen" were the same sentence. Opening a 0.11.0 milestone separates them: 0.x permits breaking changes in any release, so a surface that lands in 0.11.0 and is explicitly excluded from the 1.0.0 cut is not frozen against anything.

**This ADR answers: does the design-time surface for the kernel 0.11 preview SPI wait for 1.x, or land now under a reservation?**

## 🏁 The Decision

**Both surfaces land in 0.11.0 as `@Retention(SOURCE)` annotations plus AST carriers, shipped *reserved* — no processor extracts them, no generator consumes them, and each says so — and are excluded from the 1.0.0 freeze until the kernel promotes its package out of `preview` and the `exeris-tooling` transcription exists.**

The reservation is not a new device. `UNIVERSE` shipped exactly this way in 0.10.0 ([ADR-059](ADR-059-data-scope-expression.md)), and `ActionMetadata.realTimeUpdates` carries the same "reserved, extraction pending tooling" note. What makes it legitimate in all three cases is the same three-part test, which this ADR adopts as the standing rule for reserved surface:

1. the enforcing platform binding **demonstrably exists** (here: both kernel packages shipped with their TCKs, `AbstractBlobStorageTck` and `AbstractJobSchedulerTck`);
2. the remaining gap is **one named downstream slice**, not an open-ended "someday";
3. the annotation and the AST component **both say so**, in the text the author reads.

If a reservation cannot meet all three, the honest move is to ship nothing.

**Concrete obligations:**

1. **`@Blob` — a field-level binary facet.** `@Target(FIELD)`, in the root `eu.exeris.sdk.annotation` package. Two attributes: `container()` (the tenant-relative container name; empty means the tooling derives it from the domain) and `contentTypes()` (declared media types for the generated upload surface). It supersedes nothing — `@Field.dataType` stays the free-form presentation hint it always was.

2. **`@Blob` declares no size or format constraint.** A `maxSizeBytes` would read as the obvious attribute and is deliberately absent, on two independent grounds. It would be a **second constraint declaration site**, which ADR-054 closed: `@Validation` is the sole declaration site of constraint rules and `FieldMetadata` their single AST carrier. And the kernel promises nothing about size — ADR-056 obligation 3 makes buffers caller-owned and states no size policy — so the attribute would be inert against a platform that has no opinion to be inert *against*, which is worse than inert against an unbuilt consumer.

3. **`@Schedule` — an action-level trigger facet.** `@Target(METHOD)`, root package, alongside an `@Action`. Three mutually exclusive attributes mirroring the three kinds `JobTrigger` covers and no more: `cron()` (the standard **five-field** syntax — kernel ADR-057 obligation 7 excludes seconds, `@reboot`, and vendor extensions, and the SDK must not widen what the kernel narrowed), `every()` (ISO-8601 duration, fixed interval), and `at()` (ISO-8601 instant, one-shot). The kernel's fourth, event-driven, is excluded there and therefore here.

4. **The AST collapses the three attributes into a discriminator.** `ScheduleMetadata(TriggerKind kind, String expression)` with a nested `TriggerKind { CRON, INTERVAL, ONE_SHOT }` — the `SagaStepMetadata.StepKind` precedent. Three parallel nullable strings on the wire would let a consumer read a nonsense combination (a cron *and* an interval) that the annotation only forbids by prose; one discriminator makes it unrepresentable. Deriving the kind from which attribute is set is the extractor's job, and validating that exactly one is set is tooling's, not the SDK's — this is a design-time carrier.

5. **Both new records are class-level `@JsonInclude(NON_NULL)`, not `NON_DEFAULT`** — the posture every small facet record in the package already uses (`DerivedMetadata`, `ActionParamMetadata`, the `SagaStepMetadata` nested records); the `NON_DEFAULT` on the larger records is the exception. It is *not* chosen to dodge an ordinal-zero hazard, and stating that plainly matters because the drafting of this ADR assumed one and measurement refuted it.

   **Measured, and it corrects a standing claim.** `NON_DEFAULT` drops a boxed numeric zero — the caveat `CLAUDE.md` states, and the defect that cost the `FieldMetadata` bounds a fix in 0.9.0 — but it does **not** drop an ordinal-0 enum constant, which Jackson does not treat as empty. ADR-059 obligation 3 asserted the opposite for `DataScope.GLOBAL` ("under `NON_DEFAULT` it is `GLOBAL` (ordinal 0) that drops … a dropped explicit `GLOBAL` … reads the entity back as `TENANT`, a silent tenancy flip"), and `ROADMAP.md` repeats it. That hazard does not exist. It was never measured because `DomainMetadata` is `NON_NULL`, so nothing ever exercised the premise. `AstJsonRoundTripTest` now pins the actual semantics — boxed zero dropped, ordinal-0 enum kept — and ADR-059 carries a dated correction. The tests written on the false premise stay: pinning every tier is still worth having, and the `NON_NULL` choices are still right; only the reason was wrong.

6. **Both carriers arrive as trailing components** — `FieldMetadata.blob` and `ActionMetadata.schedule` — under the record-growth stance: positional prefixes unchanged, binding by name on the wire, and the japicmp `CONSTRUCTOR_REMOVED` downgrade that stance already configured in `exeris-sdk-source-model`. `SchemaVersion.CURRENT` moves `"0.10.0"` → `"0.11.0"`, so a 0.10.0 baseline reads as `SCHEMA_VERSION_SKEW` — the established posture of refusing a cross-shape baseline rather than assuming compatibility.

7. **`-io` reader parity is deliberately NOT taken in this slice**, on the ADR-042 discipline ADR-059 obligation 4 already applied: the reader reads what the processor writes, and no processor writes these yet, so reading them would manufacture drift. The `unmodeledFacets()` completeness guard keys on annotation types, so a source using `@Blob` or `@Schedule` will correctly report an unmodeled facet — which is the honest reading of a reserved surface, not a regression.

8. **Neither surface enters the 1.0.0 freeze.** `MIGRATION-0.x-to-1.0.md` and the 1.0.0 GA section of `ROADMAP.md` list both as excluded, with the promotion condition stated: the kernel package leaves `preview` in `docs/stability-matrix.md`, **and** the `exeris-tooling` transcription exists. Until then a 1.x minor may still break them, and the annotations say that too.

## Amendment — 2026-08-28 (0.12.0): a third surface, `@RouteAccess`

**Route authorization joins the reservation, on the same three-part test.** Kernel ADR-061 replaced a hardcoded `/secure` convention with a declarable `HttpRoutePolicy` returning a `RouteRequirement`; the surface shipped on the kernel 0.11 line with `AbstractHttpRoutePolicyTck` and is held at tier `preview` in the kernel's `docs/stability-matrix.md`. That is the same premise as blob and scheduling, so this is the same decision applied to a third facet rather than a new one — recorded here rather than as a new ADR for that reason.

What is *not* the same is what the SDK was missing. `@Blob` and `@Schedule` closed gaps where the SDK had no annotation. This one closes a gap where the SDK had four attributes and still could not make the statement:

9. **`@RouteAccess` — a route-level admission facet.** `@Target({TYPE, METHOD})`, root package, one mandatory element `value()` of a nested `Level { PUBLIC, AUTHENTICATED }`. On a type it covers the entity's generated routes; on a method beside an `@Action` it overrides for that action. Nearest declaration wins.

10. **The empty case was already taken, which is why extraction could not have fixed this.** `ROADMAP.md` recorded the SDK half of ADR-061 as "extraction + AST carrier, not a new annotation". That was wrong. `@ExerisDomain.roles` / `permissions` and `@Action.roles` / `permissions` all use empty to mean "nothing declared", and `@Action.roles` has documented empty as "accessible to all authenticated users" since 0.1.0 — so no extractor could distinguish *unspecified* from *public*, because the declaration site never encoded the difference. The correction is dated in the ROADMAP in place rather than rewritten, per this repo's editing discipline.

11. **No `UNSPECIFIED` constant, on either side — and the reasons differ per side.** The AST omits it for the reason `DataScope` already states: an absent field expresses "not declared". The annotation omits it because the *annotation itself* can be absent, which the `DataScope` case could not do (an attribute cannot default to absent). This is the one facet where both sides carry the third state structurally. A sentinel would rebuild the very ambiguity obligation 10 describes, one level down. `value()` is mandatory for the same reason: an annotation that can be written without saying anything is not worth writing.

12. **Two constants, not four — `permissions` keeps the scope half.** `RouteRequirement` also offers `ANY_SCOPE` / `ALL_SCOPES`, and `permissions` is the existing attribute that maps onto a named scope. Mirroring all four here would be a second declaration site for one fact, the shape ADR-054 closed. `@RouteAccess` states only what `permissions` cannot: whether a verified identity is required at all. Note that roles are *not* the missing half either — `RouteRequirement` declares no role kind, because roles resolve at the method level against a build-time `methodId` the kernel cannot derive from a URL.

13. **One combination the platform refuses,** recorded on the pattern obligations 2 and 8 established: `@RouteAccess(PUBLIC)` beside a non-empty `permissions` on the same element. A permit-all route runs its handler with **no `PrincipalContext` bound** — the kernel binds identity only on the path that demands it — so a scope check on a public route can never be satisfied rather than passing vacuously. Contradictory, not redundant; a build-time rejection for `exeris-tooling`.

14. **Carriers and schema.** `DomainMetadata.routeAccess` and `ActionMetadata.routeAccess`, both trailing and nullable, under the record-growth stance of obligation 6. `SchemaVersion.CURRENT` moves `"0.11.0"` → `"0.12.0"`, so a 0.11.0 baseline reads as `SCHEMA_VERSION_SKEW` — again the posture of refusing a cross-shape baseline rather than assuming compatibility. `-io` reader parity is again deliberately not taken (obligation 7). The wire behaviour is measured, not assumed: `ActionMetadata` is class-level `NON_DEFAULT` and `PUBLIC` is ordinal 0, so `AstJsonRoundTripTest` asserts the key is present — the concrete instance of what obligation 5 established in general, and it matters here because a dropped `PUBLIC` would read back as "never declared", turning a route the author opened into one nobody spoke about.

15. **Not frozen at 1.0.0.** Same promotion condition as the other two: the kernel moves route authorization out of `preview` **and** the `exeris-tooling` transcription exists. The reservation count is now three surfaces, and the accounting in *Consequences* below applies unchanged — it is going up, not down.

## Amendment — 2026-09-03 (0.12.0): a fourth surface, `@Channel`

**A duplex channel joins the reservation, on the same three-part test.** Kernel v0.12.0 shipped `eu.exeris.kernel.spi.websocket` ([kernel ADR-084](https://github.com/exeris-systems/exeris-kernel/blob/main/docs/adr/ADR-084-websocket-provider-spi.md)) — provider, engine, exchange, session and config in SPI, an RFC 6455 codec in Core, a Community transport binding, and `AbstractWebSocketExchangeTck` as the merge gate — held at tier `preview` in the kernel's `docs/stability-matrix.md`. Same premise as blob, scheduling and route access, so this is the same decision applied to a fourth facet.

16. **`@Channel` — an entity-level duplex facet.** `@Target(TYPE)`, root package, alongside `@ExerisDomain`. Two optional attributes: `messageType()` (the name of what crosses the wire) and `subprotocol()` (the negotiated WebSocket subprotocol).

17. **This is not a fourth spelling of the streaming attributes, and the difference is structural.** `@ExerisDomain.realTimeApi` and `@Action.streaming` describe server push over SSE, which is one-directional *by construction*: a subscribed client has no channel back. That is why the kernel added a separate SPI rather than widening `HttpStreamExchange` — ADR-084 §2 makes the same argument ADR-043 made, that "a mode flag on an existing exchange conditions an invariant that is currently unconditional" — and it is why this is a separate annotation rather than a flag on those. An entity whose clients edit it concurrently cannot be described by any combination of the existing four attributes.

18. **Entity-level, deliberately not per action.** A per-action duplex channel would describe a command that talks back while it runs, and that shape already has a declaration site in `@Action.realTimeUpdates` — reserved since 0.8.0 and still awaiting a generator. Adding a second spelling of one idea before the first has a consumer is how a surface acquires two ways to say the same thing, which is the shape ADR-054 closed for constraints.

19. **The AST carrier is a record, not a boolean — because three states must stay distinguishable.** `DomainMetadata.channel` is a nullable `ChannelMetadata(String messageType, String subprotocol)`. Absent means no channel; present with neither component set means a channel that declares nothing further; present with components means a channel that does. A boolean beside `realTimeApi` collapses the middle state into the first. Measured rather than assumed: class-level `NON_NULL` omits the unset components but keeps the object, so a bare channel reaches the wire as `"channel":{}` — `AstJsonRoundTripTest` asserts exactly that, because `NON_EMPTY` would drop it and turn a declared channel into one nobody mentioned. Blank normalises to `null` in the compact constructor, the `streamEventType` treatment, so `""` and absent are not two spellings of one state.

20. **Four things `@Channel` deliberately does not declare**, each for a reason already established here:
    - **No message-size limit.** `WebSocketConfig.maxMessageBytes` is an operational limit, and kernel ADR-071 gives every operational limit a configuration path with a chosen default. This is obligation 2's argument about `@Blob.maxSizeBytes`, unchanged.
    - **No origin allowlist.** A WebSocket handshake is not subject to CORS, so the kernel filters origins *before* the handler runs and refuses by default (ADR-084 §6). A per-entity attribute could only widen that, quietly, in a place nobody re-reads — and admission is `@RouteAccess`'s question.
    - **No frame format.** The kernel's application surface is text only; a binary frame is a protocol error, not a payload. There is nothing to choose.
    - **Nothing about reconnection.** Connection identity does not survive a reconnect by kernel decision, because resumption without buffering resumes identity rather than the stream and buffering is the unbounded on-heap queue ADR-043 forbids.

21. **Schema.** `SchemaVersion.CURRENT` stays `"0.12.0"`: `@RouteAccess` already moved it there in this same unreleased milestone, and a schema version marks a released wire shape rather than each commit that grows one. Trailing component under the record-growth stance of obligation 6, `-io` reader parity again deliberately not taken (obligation 7).

22. **The named consumer is `exeris-platform`, and naming it is what makes obligation 2 pass.** ADR-084's own consumers are Platform LSP and Studio; the Studio and CMS wiring is the part that reaches a *domain* declaration rather than a transport. The CMS content types are `@ExerisDomain` declarations today (`studio/docs/cms-strategy.md` §4, currently "documentation as code" that "doesn't compile against any current Exeris SDK"), and §7's migration lists real-time editing as what it brings.

    **A drafting error is recorded here rather than silently fixed**, because it would recur. This amendment was first argued the other way — that obligation 2 fails because `exeris-tooling` mentions WebSocket nowhere. That test is wrong, and measurement shows it was never the test this ADR applied: tooling's only references to `@Blob` and `@Schedule` are `INERT_ANNOTATIONS` entries recording that it deliberately does *not* process them, and `@RouteAccess` has zero tooling references at all. Requiring a built downstream slice would have failed all three surfaces this ADR approved — and it inverts the dependency direction, since a processor cannot be written against an annotation that does not exist. Obligation 2 asks whether the remaining gap is *nameable and bounded*, which is why the original text cites kernel ADR-056 naming the consumer prospectively.

23. **The `preview` tier here is benchmark-gated, not shape-gated, and that distinction matters to this decision.** ADR-084 §10 states the contract is settled and that promotion to `stable` waits on evidence a TCK structurally cannot supply — concurrent connection count, frame throughput, backpressure under a slow reader, teardown of a dead peer. What is unproven is durability under load, not what the surface looks like. The stability-ordering argument this ADR was written against — that freezing an SDK annotation over a preview SPI inverts the ordering — still holds and still forbids *freezing*, which is why this surface is excluded from 1.0.0. It does not forbid shaping a carrier against a settled contract.

24. **Not frozen at 1.0.0.** Promotion condition as before: the kernel moves `…spi.websocket` out of `preview` **and** the `exeris-tooling` transcription exists. The reservation count is now four surfaces, and the accounting in *Consequences* applies unchanged — it is going up, not down.


## Consequences

**Positive.** The Entity-First premise stops having two holes the kernel has already filled: an author can express an attachment and a schedule in the one place ADR-003 says the truth lives. `exeris-tooling` gets a target to generate against instead of a design task, which is the sequencing kernel ADR-056 asked for by name. And the reservation costs nothing that a 1.x-deferred surface would have saved — the design pressure is paid now either way, and paying it while breaking changes are still legal is strictly cheaper than paying it after the freeze, when the shape would arrive as an irrevocable additive minor.

**Negative / accepted cost.** Two more reserved surfaces in a published `0.x` artifact, which is the shape the 0.6.x honesty pass exists to police, and there are now three of them (`UNIVERSE`, `realTimeUpdates`, and this pair). The three-part test above is the guard, but the honest accounting is that reserved surface has a carrying cost and the count is going up, not down. If the tooling transcription slips past 1.0.0, the right move is to say so in the notes — or to drop the surface in the freeze cut, which 0.x still permits.

**Neutral.** The `SchemaVersion` bump degrades `exeris/applyMutation` to `NO_BASELINE` in `exeris-platform`'s LSP until codegen re-stamps its baselines. This is the same one-milestone degradation ADR-059 caused and is expected, not a defect.

## Open questions

Both are genuine gaps between what the SDK can declare and what the kernel will honour. Neither blocks this slice — a reserved surface is exactly the right place for them to sit visibly — but both must be answered before either surface is promoted out of reservation, and neither is the SDK's to answer alone.

- **A declaratively-scheduled action has no submitter.** Kernel ADR-057 obligation 5 captures `PrincipalContext` and `StorageContext` **at submission** and makes a job with no captured context fail closed rather than run under an ambient identity — deliberately, and correctly. But `@Schedule` declares a trigger with no submission event and therefore no principal to capture, so a naive transcription produces a job that fails closed on every fire. The SDK cannot resolve this: the answer is either a kernel-side notion of a system/service principal for declared jobs, or an SDK attribute naming the identity to run as (which would put an authorization decision in a design-time annotation — the worse option on its face). Owner: kernel, with `exeris-tooling` as the consumer.

- **`@Blob` on a `GLOBAL`-scoped entity is unstorable by construction.** Kernel ADR-056 obligation 5 makes an absent `isolationKey` a **terminal deny** rather than a global namespace, and `DataScope.GLOBAL` is precisely the tier that leaves it empty (`ImmutableStorageContext.GLOBAL`). So `@ExerisDomain(dataScope = GLOBAL)` plus a `@Blob` field is a declaration the platform will refuse at runtime, every time. System-scope blob storage is named out of scope by that ADR, so this is not an oversight to fix but a combination to reject — at build time, with a diagnostic, in `exeris-tooling`. Recorded here so the transcription slice does not have to rediscover it.
