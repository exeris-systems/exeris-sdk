---
title: "RFC-2026-06-18: Should exeris-sdk introduce a declarative-behaviour layer (@Derived / @Rule), and with what commitment?"
type: rfc
visibility: public
owning-repo: exeris-sdk
status: active
last-verified: 2026-06-18
---

# RFC-2026-06-18: Should exeris-sdk introduce a declarative-behaviour layer (`@Derived` / `@Rule`), and with what commitment?

| Field             | Value                                                                 |
|:------------------|:----------------------------------------------------------------------|
| **Status**        | **ACCEPTED**                                                        |
| **Author(s)**     | arkstack-dev                                                          |
| **Date Opened**   | 2026-06-18                                                            |
| **Date Closed**   | 2026-06-18                                                           |
| **Target ADR(s)** | TBD — a thin SDK-side "declarative-behaviour surface" ADR (the shape of [ADR-037](../adr/ADR-037-source-model-io-module.md) / [ADR-038](../adr/ADR-038-capability-annotation-surface.md)); number to be reserved in `exeris-docs/adr-index.md` before authoring (cross-repo), same as ADR-037/038 |
| **Affected Repos**| `exeris-sdk` (annotations + AST), `exeris-tooling` (processor extraction + codegen generation), `exeris-platform` (LSP/Studio rendering of modeled behaviour) |
| **Reviewers**     | —                                                                    |

## Question

The AST models domain **structure** richly (entities, fields, actions, events, sagas) but **behaviour** thinly — a generated app's scaffolds are real, their bodies hand-written. A mechanical slice of that behaviour (derived fields, roll-ups, simple guards/formulas) could be *declared* rather than written by hand. **Should `exeris-sdk` introduce `@Derived` / `@Rule` to model it — and if so, with what expression-language commitment, what AST shape, and what timing (ship now vs. design-now / build-on-usage)** — given the roadmap's explicit "let real usage inform the cut" discipline?

## Context

The 0.7.0 AST-expressiveness pass closed the saga, event-handler, and projection items. **Declarative-behaviour is the last and deepest item in that section, and the only one flagged "RFC-worthy (large new surface)."** With the structural-expressiveness work done, this is the natural next strategic question — but it is also explicitly gated: the roadmap says the cut should be *"informed by ready Capabilities + SKU workloads — the same 'let real usage inform the cut' discipline as the Field/Validation overlap, not budgetHQ."*

The cost of the wrong answer is unusually concrete here. This is a *large* new `0.x` surface, and the SDK has just spent an entire roadmap section — **"annotation-surface honesty — inert attributes"** — removing attributes that advertise behaviour no consumer honours, on the rule that an annotation must "tell the truth" (be wired end-to-end, or be deprecated). Shipping `@Derived` / `@Rule` with no generation behind them would manufacture a *fresh* inert surface — the exact anti-pattern the 0.6.x pass just eliminated.

Who's affected: capability authors and SKU workloads first (the named informing usage), budgetHQ later; `exeris-tooling` (which would generate the behaviour) and `exeris-platform` (which would render it in Studio/LSP) downstream. None of this surface exists in any repo today.

## Investigation

### Prior art

- **Within the SDK** — SpEL is already the de-facto embedded expression language: `@SagaStep.condition`, `@UI`/field `visibleWhen="hasRole(...)"`, saga input/output mappings. Any expression attribute the SDK adds lands next to that established convention.
- **The inert-attributes rule (this repo, 0.6.x)** — the hard-won discipline that an annotation must be consumed end-to-end or run the deprecation pipeline. A behaviour annotation with no consumer violates it *on arrival*.
- **The additive-surface discipline (capability / event-handler / projection / saga)** — the SDK ships the *declared shape* as records/annotations storing source-written **strings**; semantics and generation are tooling; reader↔processor parity (ADR-042) is preserved by **not** populating the field in the `-io` reader until both sides extract it together.
- **External shape-setters** — Hibernate `@Formula` (a read-only column derived from a fragment) bounds `@Derived`; Bean Validation class-level constraints / `@AssertTrue` (named invariant + message + severity) bound `@Rule`; Spring `@Value("#{…}")` shows the embedded-SpEL precedent. Together they say: *derived = a read-projection of other fields; rule = a named invariant carrying a message.*

### Constraints

- **Zero runtime coupling.** Whatever the expression syntax, it crosses the wire as a `String`; the SDK must not depend on an expression-language runtime (no SpEL evaluator, no parser). The SDK may *name* a language as a contract; it may not *embed* one.
- **Wire-format contract (ADR-037).** New AST records must be records, round-trip, and be additive / by-name.
- **Inert-attribute honesty (this repo's rule).** A shipped annotation must be consumed end-to-end, or it is a regression the moment it lands.
- **Reader↔processor parity (ADR-042).** `-io` must not read what the processor does not yet write.

### Data gathered

- No `@Derived` / `@Rule` annotations and no `DerivedMetadata` / `RuleMetadata` AST records exist in any repo today — greenfield.
- **The build trigger named below is now met.** When this RFC was opened (status DRAFT) no usage corpus existed. It now does: an internal adopter exercising the SDK against a non-trivial generated domain has a concrete, recurring need for exactly this surface (see *Adopter corpus*). The "no signal in hand" caveat that originally argued for deferral no longer holds.

### Adopter corpus (the build trigger)

An internal adopter, building a non-trivial generated domain on the SDK, hand-writes a class of mechanical behaviour the AST cannot yet describe. Sanitized to neutral shapes (no domain specifics), the recurring patterns are:

1. **Roll-up / derived field** — a value aggregated from related entities or other fields, today "maintained by event handlers" that are in fact hand-written services. Some are **cross-aggregate** (the derived value reads across a relationship), not just sibling-field arithmetic. → `@Derived` + `dependsOn`.
2. **Discriminant → value mapping** — a value selected piecewise from a state/enum discriminant. → a `@Derived` formula.
3. **Ratio / threshold guard** — a boolean derived from a value's relation to a threshold. → `@Rule` (named invariant / guard).
4. **Decay / erosion formula** — a value that decrements by a formula over time or quantity. → `@Derived` (time-dependent — touches the materialization-hint open question).

That is ≥4 distinct, recurring shapes from one real adopter — meeting the trigger's intent (a real adopter with a genuine, repeated need, enough to size the cut), even if it sits just under the rounded "≈5" the RFC first wrote down. Two refinements the corpus surfaced are folded into the design below: `dependsOn` must be able to name **related-entity paths**, not only sibling fields; and the adopter also envisions a third sibling, a reaction/choreography annotation (`@Reaction`-shaped), which is **out of scope here** — it overlaps the already-shipped `EventHandlerMetadata` and the still-open `@DomainEvent(trigger = STATE_TRANSITION)` "declarative-only" gap, and belongs to that choreography track, not this derived/rule one.

## Options Considered

The non-controversial parts are fixed by precedent: annotation names live in `exeris-sdk-annotations`, AST records in `exeris-sdk-source-model`, the expression is stored as a `String`, and generation is `exeris-tooling`. The genuinely open forks are **(1) commitment / timing** and **(2) expression-language stance**.

### Fork 1 — commitment / timing

#### Option A: Full surface now

Define `@Derived` / `@Rule` with committed semantics up front — derived-field trigger timing, rule severity/message/grouping, dependency ordering, the lot.

**Pros:** one decisive surface; nothing half-built.
**Cons:** locks a large `0.x` surface with zero usage input — exactly what the discipline forbids; high odds of reshaping under the first real examples (`0.x` churn for any consumer that pins it); and it ships **inert** (no generation behind it) on day one, tripping the honesty rule immediately.
**Cost:** high; high regret risk.

#### Option B: Minimal expression-agnostic seed now

Ship the two annotations + thin AST records (opaque `String` expression + the escape hatch) now; defer severity / timing / dependency semantics to additive growth.

**Pros:** a small foothold; additive growth is the SDK's proven mode.
**Cons:** still ships **inert** (no tooling consumer) — same honesty violation, just smaller; even a "thin" annotation is a published `0.x` surface a consumer may pin; and without a corpus we cannot validate even the seed's field set is the right one.
**Cost:** moderate; still premature.

#### Option C (do-nothing-for-now / design-on-paper): Accept the framed design; gate the build on a concrete usage signal

Do not ship annotations this milestone. The RFC's **deliverable is the agreed design** (names, layering, expression stance, AST shape, escape hatch) plus an **explicit trigger**: implement when the ready Capabilities + SKU workloads produce a concrete corpus of hand-rolled derived-fields/rules (threshold below). When that lands, implementation is a transcription, not a re-design.

**Pros:** discipline-consistent — matches the `roles[]` and Field/Validation-overlap deferrals exactly; avoids a fresh inert surface; no premature `0.x` lock; the expensive part (the surface debate) is still done *now*, captured, and ready to execute.
**Cons:** nothing ships this milestone; relies on the usage signal actually arriving — mitigated by naming the trigger, and the cost of waiting is zero because the behaviour is hand-written today regardless.
**Cost:** lowest; the design work *is* the deliverable.

### Fork 2 — expression-language stance (applies whenever the build happens)

- **Option α: Name SpEL as the contract** — consistent with `condition` / `visibleWhen`; familiar; but over-commits the SDK to one evaluator for both formula-style derivations and guard-style rules.
- **Option β: Opaque `String` + optional `language` discriminator (default = the SDK's existing SpEL convention)** — the SDK interprets nothing and stores `(expression, language?)`; SpEL stays the *default* without foreclosing a different evaluator for `@Derived` formulas vs. `@Rule` guards; zero-coupling by construction.
- **Option γ: A neutral restricted grammar the SDK defines** — portable, but the SDK would then own a language spec **and a parser** — scope explosion, and a runtime artifact the SDK has no business holding.

## Recommendation

**Build the framed design now (Option β expression stance). The RFC opened recommending Option C — defer until a usage corpus exists — but that corpus now exists (an internal adopter, see *Adopter corpus*), so the gate the RFC itself set is open and we proceed. Ship the `@Derived` / `@Rule` annotations + `DerivedMetadata` / `RuleMetadata` AST records, with a "reserved — generation pending tooling" javadoc-honesty note (the capability-annotation precedent). The expression is an opaque `String` plus an optional `language` discriminator defaulting to the SDK's existing SpEL convention.**

The two facts that originally argued for deferral have flipped or fallen away. The **usage signal is now in hand** — a real adopter has ≥4 distinct, recurring hand-rolled shapes (roll-ups incl. cross-aggregate, discriminant→value maps, ratio/threshold guards, decay formulas) — meeting the trigger's intent, so the surface is sized against real usage rather than guessed. And the **inert-attribute objection is resolved the same way capability resolved it**: the surface ships with an explicit "reserved, not yet consumed in Open-Core" javadoc note (`@Provides`/`@Requires` already live this way), with a real adopter authoring against it and the AST carrying it; the *generation* is the coordinated `exeris-tooling` follow-up the user routes (processor extraction + `-io` reader in parity, ADR-042), not an SDK gap. The expression-agnostic `String` + `language` stance keeps the SDK pure regardless of which evaluator the adopter's formulas vs. guards end up wanting, and avoids prematurely blessing SpEL for both.

### Designed surface (decided on paper — not built)

So the future ADR does not re-litigate it:

- **`@Derived`** — `@Target({FIELD, METHOD})`, `@Retention(SOURCE)`. A read-only field/value computed from others. Attributes: `expression` (`String`), `language` (`String`, default `""` ⇒ the SpEL convention, see *expression-tag* below), optional `dependsOn` (`String[]` — dependency hints for the design-time graph; an entry is a sibling field name **or a related-entity path** like `customer.tier`, since the adopter corpus shows cross-aggregate derivations). → a field facet `FieldMetadata.derived : DerivedMetadata(expression, language, dependsOn)`, mirroring how `dataType` was added. Stored as plain strings — path *resolution* against the relationship graph is build-time tooling's job, not the SDK's (the same source-written-string discipline the capability service refs use).
- **`@Rule`** — `@Target({TYPE, FIELD})`, `@Retention(SOURCE)`, `@Repeatable(Rules.class)` — the container is a top-level **`@Rules`** annotation (the plural-name convention the repo uses for `@SagaStep`→`SagaSteps`; the nested-`.List` idiom was reserved for capability annotations only because `@Provides`/`@Requires` already end in `-s`, which does not apply here). The AST flattens the container, so consumers never see it. A named declarative invariant. Attributes: `name`, `expression` (`String`), `message` (`String`, i18n-key-friendly like the 0.6 keys), `severity` (`String`, default `""` ⇒ `ERROR`, see *severity* below), `language` (`String`, default `""` ⇒ SpEL). → `DomainMetadata.rules : List<RuleMetadata>(name, expression, message, severity, language)`.
- **Escape hatch** — both annotations are strictly **opt-in**: any behaviour not annotated is hand-written *by definition*, and a design-time tool always renders un-annotated logic as "manual / not modeled." Nothing is ever forced into declarative form; genuinely-complex domain logic stays code. Absence **is** the escape hatch — no `manual=true` flag needed.
- **Expression-tag (`language`)** — the canonical default tag is **`"spel"`** (the SDK's existing embedded expression language, used by `condition`/`visibleWhen`). The annotation default is `""` and the compact constructor normalizes blank → `null`; a consumer reading `null` applies the `"spel"` default. So the common case carries no `language` on the wire, and an alternative evaluator is named explicitly (`language = "jexl"`, …) only when it differs. The exact tag vocabulary is corpus-adjustable (see Open questions).
- **Storage & defaults on the wire** — all strings; `@JsonInclude(NON_NULL)`. To keep defaulted attributes off the wire under `NON_NULL` (which, unlike `NON_DEFAULT`, would otherwise serialize a non-null `"ERROR"`/`"spel"` every time), the annotation defaults are the **empty string** and the compact constructor normalizes blank → `null` (the established `EventHandlerMetadata`/`ProjectionMetadata`/saga pattern); the *semantic* default (`severity` ⇒ `ERROR`, `language` ⇒ `spel`) is applied by the consumer, not stored. If `severity` is ever promoted to an enumerated type it is an **AST-owned** enum (no annotation-enum mirror to duplicate, like the saga enums).
- **Parity** — when built, `-io` reads these only once the `exeris-tooling` processor extracts them, in lock-step (ADR-042), as with every prior surface.

### Trigger to start the build — MET

The trigger was a usage signal of **several distinct hand-rolled derived-fields/rules** (the RFC's working estimate was ≈5) observed in real usage (the informing usage the roadmap names — explicitly **not** budgetHQ). The *Adopter corpus* above records ≥4 distinct, recurring shapes from one internal adopter; that is just under the rounded ≈5 but squarely at the trigger's intent — a real adopter with a genuine, repeated need, enough to size the cut — so the build proceeds. The corpus stays the reference for which attributes earn their place; further adopter patterns refine the surface additively, not by reshaping it.

### Why not the alternatives?

- **Option A (full now)** — locks a large `0.x` surface with zero usage input and ships inert; maximal regret risk.
- **Option B (seed now)** — still inert on arrival and still a guess at the field set without a corpus; the foothold buys little the paper design does not already hold.
- **Expression α (name SpEL)** — over-commits the SDK to one evaluator for both formulas and guards; the `language` discriminator keeps SpEL the default without foreclosing alternatives.
- **Expression γ (own grammar)** — hands the SDK a language spec + parser to maintain; out of scope and anti-zero-coupling.

### Risks of the recommendation

- **The corpus comes from one adopter and may not represent all adoption patterns** — bounded by the additive-surface discipline: further patterns *extend* the surface (new attributes / a sibling annotation) rather than reshape what ships, and the surface ships reserved (generation pending) so a wrong early cut costs no generated behaviour to unwind.
- **Designing on paper without a corpus can still misjudge the surface** — accepted and bounded: the design is explicitly provisional, and the trigger corpus is what validates/adjusts the field lists *before* the build; the lists here are a starting hypothesis, not a frozen contract.
- **The two surfaces (`@Derived` as a field facet vs. `@Rule` as a domain-level list) may later want unifying** — flagged as a follow-up, not pre-solved.

## Decision Record

| Field                | Value     |
|:---------------------|:----------|
| **Outcome**          | **ACCEPTED** — build the framed `@Derived` / `@Rule` surface now (Option β expression stance). The Option-C deferral gate is open: the *Adopter corpus* meets the build trigger. |
| **Date**             | 2026-06-18 |
| **Resulting ADR(s)** | TBD — a thin SDK-side "declarative-behaviour surface" ADR (the ADR-037/038 shape); number reserved in `exeris-docs/adr-index.md` first (cross-repo). |
| **Notes**            | Ship with the capability precedent's "reserved — generation pending tooling" javadoc-honesty note: a real adopter authors against the surface and the AST carries it; processor extraction + `-io` reader-in-parity + codegen generation are the coordinated `exeris-tooling` follow-up the user routes (ADR-042 parity). Pinned shapes: `@Derived` (field facet `FieldMetadata.derived`), `@Rule` (+ top-level `@Rules` container; `DomainMetadata.rules` list); expression as opaque `String` + `language` tag (default `""` ⇒ `"spel"`); `severity` default `""` ⇒ `ERROR`; blank → null normalization under `@JsonInclude(NON_NULL)`; `dependsOn` allows related-entity paths. `@Reaction` / choreography sibling is out of scope (overlaps `EventHandlerMetadata` + the `STATE_TRANSITION` gap). Delivery: annotations slice → AST-records slice (mirrors the ADR-038 protocol). |

## Open questions / follow-ups

- **Capture the Caps/SKU derived-field/rule corpus** (the build trigger) — owner: SDK roadmap; target: when ready Capabilities + SKU workloads land.
- **`severity` taxonomy** (`ERROR` / `WARN` / `INFO`?) and whether to promote it to an AST-owned enum — decide against the corpus, not now (the default-`ERROR`-on-blank wire treatment is already pinned above).
- **`language` tag vocabulary** — `"spel"` is the pinned default; the full set of accepted tags (e.g. `"jexl"`, `"mvel"`) is corpus-adjustable. The wire treatment (blank ⇒ `null` ⇒ consumer applies `"spel"`) is fixed; only the recognized non-default tags are open.
- **Server-side `@Validation` generation + full `@UI` fidelity** — the roadmap names these as smaller instances of the same "modelled but not generated" gap (their SDK halves already exist; generation is tooling); relate them to this layer when it is built.
- **`@Derived` materialization hint** — computed-on-read vs. materialized is a generation concern (tooling), but the AST may need a hint; revisit with the corpus.
- **`@Derived` vs. `@Rule` unification** — whether a single declarative-expression facet subsumes both; revisit once both have real usage.
