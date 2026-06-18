# RFC-2026-06-18: Should exeris-sdk introduce a declarative-behaviour layer (`@Derived` / `@Rule`), and with what commitment?

| Field             | Value                                                                 |
|:------------------|:----------------------------------------------------------------------|
| **Status**        | **DRAFT**                                                            |
| **Author(s)**     | arkstack-dev                                                          |
| **Date Opened**   | 2026-06-18                                                            |
| **Date Closed**   | —                                                                    |
| **Target ADR(s)** | TBD — a thin SDK-side "declarative-behaviour surface" ADR (the shape of [ADR-037](../adr/ADR-037-source-model-io-module.md) / [ADR-038](../adr/ADR-038-capability-annotation-surface.md)), authored only if/when this RFC is accepted **and** the usage trigger fires |
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
- **No corpus of hand-rolled derived-fields/rules has yet been captured** from the ready Capabilities or SKU workloads. The very usage signal the discipline says should size the cut is **not in hand**. This is the decisive data point: any surface shipped today is a guess at its own field set.

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

**Option C (commitment) + Option β (expression stance): do not ship `@Derived` / `@Rule` yet; accept the framed design below as the RFC output and gate implementation on a concrete Capabilities/SKU usage corpus. When built, the expression is an opaque `String` plus an optional `language` discriminator defaulting to the SDK's existing SpEL convention.**

Two facts decide it. First, **the usage signal the discipline requires is not in hand** — there is no corpus of real derived-fields/rules to size the cut against, so any surface shipped now guesses at its own field set, and the roadmap is unambiguous that this particular cut waits on ready Capabilities + SKU usage. Second, **the SDK just finished excising inert attributes**, and an unconsumed `@Derived` / `@Rule` would be a brand-new one the moment it lands — directly contradicting the honesty rule the SDK now holds itself to. Designing on paper now captures the expensive part (the surface debate) and lifts it off the future ADR's plate, so when the first concrete examples arrive the build is mechanical. The expression-agnostic `String` + `language` stance keeps the SDK pure regardless of which evaluator Caps/SKU end up wanting, and avoids prematurely blessing SpEL for formula-style derivations that may want a different evaluator than security guards.

### Designed surface (decided on paper — not built)

So the future ADR does not re-litigate it:

- **`@Derived`** — `@Target({FIELD, METHOD})`, `@Retention(SOURCE)`. A read-only field/value computed from others. Attributes: `expression` (`String`), `language` (`String`, default = the SpEL convention), optional `dependsOn` (`String[]` of field names, for the design-time dependency graph). → a field facet `FieldMetadata.derived : DerivedMetadata(expression, language, dependsOn)`, mirroring how `dataType` was added.
- **`@Rule`** — `@Target({TYPE, FIELD})`, `@Repeatable`, `@Retention(SOURCE)`. A named declarative invariant. Attributes: `name`, `expression` (`String`), `message` (`String`, i18n-key-friendly like the 0.6 keys), `severity` (`String`, default `ERROR`), `language` (`String`, default SpEL). → `DomainMetadata.rules : List<RuleMetadata>(name, expression, message, severity, language)`.
- **Escape hatch** — both annotations are strictly **opt-in**: any behaviour not annotated is hand-written *by definition*, and a design-time tool always renders un-annotated logic as "manual / not modeled." Nothing is ever forced into declarative form; genuinely-complex domain logic stays code. Absence **is** the escape hatch — no `manual=true` flag needed.
- **Storage** — all strings; if `severity` is ever enumerated it is an **AST-owned** enum (no annotation-enum mirror to duplicate, like the saga enums); `language` stays a free `String`. Additive, by-name, `@JsonInclude(NON_NULL)`.
- **Parity** — when built, `-io` reads these only once the `exeris-tooling` processor extracts them, in lock-step (ADR-042), as with every prior surface.

### Trigger to start the build

Implement when **≈5 or more distinct hand-rolled derived-fields/rules** are observed across the ready Capabilities + SKU workloads (the informing usage the roadmap names — explicitly **not** budgetHQ), captured as a short corpus in this RFC's follow-up. That corpus sizes the real cut — which attributes earn their place — before a line of annotation ships.

### Why not the alternatives?

- **Option A (full now)** — locks a large `0.x` surface with zero usage input and ships inert; maximal regret risk.
- **Option B (seed now)** — still inert on arrival and still a guess at the field set without a corpus; the foothold buys little the paper design does not already hold.
- **Expression α (name SpEL)** — over-commits the SDK to one evaluator for both formulas and guards; the `language` discriminator keeps SpEL the default without foreclosing alternatives.
- **Expression γ (own grammar)** — hands the SDK a language spec + parser to maintain; out of scope and anti-zero-coupling.

### Risks of the recommendation

- **The usage signal may not arrive on a predictable schedule** — mitigated by naming the trigger; the cost of waiting is zero (behaviour is hand-written today regardless).
- **Designing on paper without a corpus can still misjudge the surface** — accepted and bounded: the design is explicitly provisional, and the trigger corpus is what validates/adjusts the field lists *before* the build; the lists here are a starting hypothesis, not a frozen contract.
- **The two surfaces (`@Derived` as a field facet vs. `@Rule` as a domain-level list) may later want unifying** — flagged as a follow-up, not pre-solved.

## Decision Record

<!-- Filled in when status reaches ACCEPTED / REJECTED / WITHDRAWN. -->

## Open questions / follow-ups

- **Capture the Caps/SKU derived-field/rule corpus** (the build trigger) — owner: SDK roadmap; target: when ready Capabilities + SKU workloads land.
- **`severity` taxonomy** (`ERROR` / `WARN` / `INFO`?) — decide against the corpus, not now.
- **Server-side `@Validation` generation + full `@UI` fidelity** — the roadmap names these as smaller instances of the same "modelled but not generated" gap (their SDK halves already exist; generation is tooling); relate them to this layer when it is built.
- **`@Derived` materialization hint** — computed-on-read vs. materialized is a generation concern (tooling), but the AST may need a hint; revisit with the corpus.
- **`@Derived` vs. `@Rule` unification** — whether a single declarative-expression facet subsumes both; revisit once both have real usage.
