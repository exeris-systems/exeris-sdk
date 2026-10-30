---
title: "ADR-106: A pre-construction brief is a contract in the SDK that references the source model and never re-encodes it"
type: adr
visibility: public
owning-repo: exeris-sdk
status: active
last-verified: 2026-10-29
slug: adr/ADR-106
---

# ADR-106: A pre-construction brief is a contract in the SDK that references the source model and never re-encodes it

| Attribute       | Value                                                                                          |
|:----------------|:-----------------------------------------------------------------------------------------------|
| **Status**      | **ACCEPTED**                                                                                   |
| **Deciders**    | Arkadiusz Przychocki                                                                           |
| **Date**        | 2026-10-27                                                                                     |
| **Scope**       | cross-repo (`sdk/source-model`; consumed by `exeris-ai-bridge`)                                |
| **Owning Repo** | `exeris-sdk`                                                                                    |
| **Driven By**   | [RFC-2026-10-24](https://github.com/exeris-systems/exeris-docs/blob/main/rfc/RFC-2026-10-24-pre-construction-discovery.md) (ACCEPTED 2026-10-27: Q1-C, Q2-B1, Q3-B, Q4-C) |
| **Compliance**  | [ADR-003](ADR-003-entity-first-development-strategy.md) Entity-First; zero runtime coupling ([policy](../../.agents/policies/zero-runtime-coupling.md)); emitted contracts ([policy](../../.agents/policies/javadoc-and-contract-emitters.md)) |

> The number is held by the [`exeris-docs/adr-index.md`](https://github.com/exeris-systems/exeris-docs/blob/main/adr-index.md) row. The contract ships in SDK 0.13.0 as `briefSchemaFormat` 1, in `exeris-sdk-source-model`'s jar.

## Context and Problem Statement

An application developer's agent can author an `@ExerisDomain` entity and preview what it generates, but nothing in the ecosystem covers the step before: finding out what is being built, for whom, what it must never do, and how anyone will know it is done. An agent asked for "an invoicing system" goes straight to entities, and the requirements it never asked about surface later as rework.

Discovery that produces no artefact cannot be checked, resumed or reviewed. Discovery that produces prose cannot be checked by machine, and a section called "data model" grows field lists until it is a second description of the domain beside the annotated class, which ADR-003 declares to be the only one. The artefact therefore has to be structured, and its structure has to leave no room for what the source model carries.

RFC-2026-10-24 settled where the contract lives (Q2-B1: records in `exeris-sdk` with a processor-emitted schema) and how agents reach it (Q3-B: a stateless prompt and a validation tool in `exeris-ai-bridge`). This ADR records the contract itself: **what a brief carries, what it may not carry, and when it is ready.**

## 🏁 The Decision

**A pre-construction brief is a set of Java records in `exeris-sdk-source-model` under `eu.exeris.sdk.brief`, from which `BriefSchemaProcessor` emits `META-INF/exeris/brief-schema.json`, a closed JSON Schema in which no structure `DomainMetadata` carries has a slot.**

A brief is `docs/briefs/<area>.brief.yaml` in an application project, one per bounded area, read as YAML 1.2 and validated as the JSON document it denotes. Its first key is `briefSchema`, the SDK version it was written against.

**Concrete obligations:**

1. **Content.** `Brief` carries `briefSchema`, `purpose`, `goals`, `nonGoals`, `actors` (`Actor`), `glossary` (`GlossaryTerm`: term, meaning, a `DataScope` intent, concern hints), `workflows` (`Workflow` with `EarsCriterion` criteria), `nonFunctional` (`NonFunctionalRequirement`), `integrations` (`Integration`), `candidateCapabilities` (names) and `openQuestions` (`OpenQuestion`). Nothing else.
2. **No re-encoding.** Every object in the schema is `additionalProperties: false`, and the schema declares no property named after a `DomainMetadata` component that carries a record. `BriefSchemaContractTest` derives that set from `DomainMetadata`'s record components at test time and holds one rejection case per structure, at every object position of a brief; a structure added to the source model fails the suite until it has its case.
3. **Vocabulary by reference.** The data-scope intent is `eu.exeris.sdk.sourcemodel.ast.DataScope`, reused by type. A concern hint is a `String` whose admitted values are the simple names of the top-level annotation types of `exeris-sdk-annotations`, less repeatable containers and less those deprecated for removal. The processor reads them from the annotation sources, not from `annotation-catalog.json`, so neither processor depends on the other's output; the contract test asserts every hint is in the catalog of the same build.
4. **One-way dependency.** `eu.exeris.sdk.brief` may use enums from `eu.exeris.sdk.sourcemodel.ast`; nothing under `eu.exeris.sdk.sourcemodel` may use `eu.exeris.sdk.brief`. `BriefDependencyDirectionTest` checks both halves on the compiled classes. The module's compile classpath stays `jackson-annotations` alone.
5. **EARS by structure.** A criterion is `{ kind: ubiquitous | when | if | while, trigger?, condition?, response }`. `ubiquitous` has neither trigger nor condition; `when` and `if` require a trigger and admit a condition; `while` requires a condition and admits no trigger; every kind requires a response. No text is inspected.
6. **Typed references.** `fulfilledBy: [{ kind: entity | action | rule | saga, ref }]`. For `entity`, `ref` is the qualified class name; for the others it is `<entity qualified name>#<member name>`. A glossary term admits only `entity`; a workflow admits `action`, `rule` and `saga`. The source wins any disagreement.
7. **Minimum content and readiness.** Validity requires a non-blank `purpose`, at least one actor, at least one workflow with at least one criterion, at least one non-goal, and an `assumption` and `owner` on every `assumed` question; these are `required` / `minItems` / conditional constraints in the schema. A brief is **ready** when it is valid and no blocking question is `open`. An absent or empty brief is never ready. `BriefReadiness.evaluate` is that function, and every fixture runs through both it and a draft 2020-12 validator, which must agree.
8. **Versioning.** The schema carries `briefSchemaFormat`, an integer (`brief.schema.format` in the module's POM), and `sdkVersion`. A consumer pins to `briefSchemaFormat`; it moves only when a brief valid under the previous number would stop validating.
9. **Freeze.** Before SDK 1.0, a change to the brief is recorded in the SDK changelog and ships with the brief fixtures updated in the same change. From 1.0 it is additive only; removing or renaming a brief property is a breaking change under the rule for an annotation attribute. The brief records are covered by `RecordComponentOrderTest` and `RecordConstructorLedgerTest` like the AST.

## Consequences

### ✅ Positive Outcomes

- **[+] "Discovery is done" is a check.** Readiness is a function of the document, the same in the SDK and in any consumer validating against the schema.
- **[+] ADR-003 is enforced by shape.** A brief cannot carry a field list, type, relationship, validation bound, action signature, saga step or view layout, because the schema has nowhere to put one.
- **[+] Vocabulary drift fails the build.** `DataScope` changes do not compile against the brief; a renamed annotation changes the hint `enum` in the same build, and the contract test compares it with the catalog.
- **[+] Two audiences, one contract.** JVM consumers bind the records; others read the schema, as with the AST.

### ⚠️ Trade-offs

- **[-] A pre-source package in a module named for the source model.** Held in check by the one-way dependency test.
- **[-] The constraints live twice.** The schema's constraints are declared on the records and emitted; `BriefReadiness` restates them in Java. The fixtures run through both, so a disagreement fails, but a rule added to one side only is caught only if a fixture exercises it.
- **[-] The SDK's release cadence gates every brief change.**
- **[-] Prose is not guarded.** A field list written into a `response` or a `meaning` passes the schema. Left to review; no text heuristic is added.

### 📋 What is NOT in scope

- `build-validate_brief`, the `sdk:*` schema resource and the `discover-requirements` prompt: `exeris-ai-bridge`, under its ADR-025 amendment of 2026-10-27.
- Resolving `fulfilledBy` against a project's emitted metadata. `BriefReadiness` accepts the set of known references from its caller, who builds it from `DomainMetadata`; the brief package itself does not read source-model records.
- Checking candidate capabilities against anything wider than a project's own composition.

### 🚫 Non-Goals

- A brief is not an entity draft and is never required: authoring a single entity without one stays a valid path.
- No interview procedure is defined here; the SDK owns the artefact, not the conversation.

### ⚠️ Risks and Assumptions

- **Assumes:** a closed schema is acceptable to brief authors; additions arrive through SDK releases.
- **Reversed by:** evidence that briefs routinely need a property the contract cannot admit without re-encoding the source, which would mean the brief/source boundary is drawn in the wrong place.
- **Risk:** the brief and the AST start sharing types beyond enums, eroding the separation from inside the module. `BriefDependencyDirectionTest` fails first.

## Cross-references

- ADR-003 (Entity-First Development Strategy) — the annotated class is the only definition of the domain; the brief references it.
- ADR-059 (Data-scope expression) — `DataScope`, reused by type for a glossary term's intent.
- ADR-025 (`exeris-ai-bridge`, 2026-10-27 amendment) — the consumer: schema resource, `build-validate_brief`, `discover-requirements`.
- ADR-086 (`exeris-ai-execution`) — no relationship now (RFC-2026-10-24 Q4-C).
- `exeris-sdk-source-model/src/main/java/eu/exeris/sdk/brief/` — the records and `BriefReadiness`.
- `exeris-sdk-annotation-catalog/src/main/java/eu/exeris/sdk/catalog/BriefSchemaProcessor.java` — the emitter.

## Engineering Protocol

1. `BriefSchemaContractTest` — closed objects, prose coverage, the derived ADR-003 rejection set, EARS per kind, positional reference kinds, the catalog-bound hint vocabulary, and the planted-gap fixtures under `src/test/resources/briefs/` against a validator and `BriefReadiness`.
2. `BriefDependencyDirectionTest` — the one-way dependency.
3. `BriefSchemaProcessorTest` — the emitter, including byte-identical output across two runs.
4. `RecordComponentOrderTest` and `RecordConstructorLedgerTest` — growth at the tail only, via `WireRecords`, which names `eu.exeris.sdk.brief`.

## Amendments

- **2026-10-30 — two rules outside the schema, and the hint vocabulary shipped as text.**
  Obligation 7 defined validity as the schema's constraints. Two rules were stated in the schema's
  prose and enforced nowhere: an `OpenQuestion.id` is unique within the brief, and each entry of
  `Workflow.actors` is the name of a declared `Actor`. JSON Schema cannot compare values across
  array items or resolve one array against another, so a brief breaking either was ready for a
  validator and for `BriefReadiness` alike. Both are now validity rules. `BriefReadiness` reports
  a repeated id at the later question's `id`, and an undeclared workflow actor at that actor. The
  schema lists both in `x-exeris-reader-requirements` (`QUESTION_IDS_ARE_UNIQUE`,
  `WORKFLOW_ACTORS_ARE_DECLARED`) beside `UNKNOWN_PROPERTIES_ARE_ERRORS`, so a consumer validating
  with a JSON Schema validator applies them from the document. Validity is therefore the schema's
  constraints plus its reader requirements. On a fixture, `BriefReadiness` may reject what the
  schema accepts only under one of these rules, and never accepts what the schema rejects.
  `briefSchemaFormat` stays `1`: the two rules were part of format 1's text, and a brief that
  breaks one was never valid under it.
  `BriefSchemaProcessor` also writes the concern-hint vocabulary as
  `META-INF/exeris/concern-hints.txt`, one name per line, from the same sources and in the same
  build as the `ConcernHint` enum. `BriefReadiness.evaluate(brief)` checks hints against it, so the
  one-argument form agrees with the schema on hints; before this, it left them unchecked. Each
  rule has a planted-gap fixture, and `BriefSchemaContractTest` holds the text list equal to the
  enum. Shipped in 0.13.2 (#257). Rejected: dropping the two prose claims, which would leave a
  brief whose question ids or actor names do not resolve indistinguishable from a sound one.
