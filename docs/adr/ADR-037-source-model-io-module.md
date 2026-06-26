# ADR-037: Isolate the source-model parser/writer in a dedicated `exeris-sdk-source-model-io` module

| Attribute       | Value                                                                                          |
|:----------------|:-----------------------------------------------------------------------------------------------|
| **Status**      | **ACCEPTED**                                                                                   |
| **Deciders**    | Arkadiusz Przychocki                                                                           |
| **Date**        | 2026-06-03                                                                                     |
| **Scope**       | cross-repo                                                                                     |
| **Owning Repo** | `exeris-sdk`                                                                                    |
| **Driven By**   | [RFC-2026-06-03](../rfc/RFC-2026-06-03-source-model-parser-writer.md) (ACCEPTED)               |
| **Compliance**  | Zero runtime coupling invariant (the SDK's defining discipline); [ADR-003](ADR-003%20Entity-First%20Development%20Strategy.md) Entity-First |

> ADR numbers are a **single ecosystem-wide namespace** registered in [`exeris-docs/adr-index.md`](https://github.com/exeris-systems/exeris-docs/blob/main/adr-index.md). The gap between ADR-003 and ADR-037 in this repo is expected — 004–036 are owned by other Exeris repos.

## Context and Problem Statement

The 0.3.0 milestone adds a standalone `.java` → `DomainMetadata` parser and an idempotent `DomainMetadata` → `.java` writer (preserving user comments, formatting, and non-Exeris annotations). Both need [JavaParser](https://javaparser.org/) — the first heavy third-party dependency the SDK would carry. The SDK's defining invariant is *zero runtime coupling*: `exeris-sdk-annotations` has no dependencies, and `exeris-sdk-source-model` pulls only `jackson-annotations` at compile scope (`jackson-databind` is `test`-scope on purpose).

`exeris-sdk-source-model` is the module **every** downstream consumer depends on for the AST records: `exeris-tooling/exeris-codegen-*`, `exeris-processor` (for the JSON shape), `exeris-platform-lsp`, and the future capability registry. Adding JavaParser as a compile-scope dependency of `source-model` would silently push a multi-MB parser onto consumers whose entire need is "deserialize `DomainMetadata` from JSON" — exactly the coupling regression the SDK's layering exists to prevent. `exeris-platform-lsp` is a live consumer waiting on this: its README states implementation lands "once `exeris-sdk-source-model` ships its JavaParser-based parser/writer."

Two sub-questions were settled in the RFC and are not re-opened here: the **library** is JavaParser (the existing `ExerisDomainProcessor` uses JSR-269 inside `javac`, unavailable to an in-editor LSP — so this is new capability, not a refactor), and the **repo** is `exeris-sdk` (placing it in `exeris-tooling` would invert the SDK→tooling dependency arrow and drag `exeris-codegen-*` into the LSP). The open question this ADR answers: **how is the parser/writer packaged inside `exeris-sdk` so that pure AST-record consumers do not transitively inherit JavaParser?**

## 🏁 The Decision

**The 0.3.0 parser and writer ship in a new, separately-published sibling module `exeris-sdk-source-model-io` that depends on `exeris-sdk-source-model` + JavaParser; `exeris-sdk-source-model` stays dependency-light and never gains a compile-scope JavaParser dependency.**

The module is named `-io` (not `-parser`) because it owns both directions of source-text ↔ model round-tripping, and the name deliberately avoids the engine (`-javaparser`) so the published Maven coordinate survives an engine swap. Packages live under `eu.exeris.sdk.sourcemodel.io`.

**Concrete obligations:**

1. **`exeris-sdk-source-model` carries no JavaParser.** Its `pom.xml` MUST NOT declare a compile- or runtime-scope dependency on `com.github.javaparser:*`. (Reviewable: `grep javaparser exeris-sdk-source-model/pom.xml` returns nothing.)
2. **JavaParser is scoped to `-io` only.** `com.github.javaparser:javaparser-core` is a compile-scope dependency of `exeris-sdk-source-model-io` and of no other published module. (Reviewable: `mvn -pl exeris-sdk-source-model,exeris-sdk-annotations dependency:tree` shows no `javaparser` node.)
3. **The dependency arrow points one way.** `-io` depends on `source-model`; `source-model` and `annotations` never depend on `-io`.
4. **AST records stay in `source-model`.** The Jackson-serializable AST records — the surface frozen at 1.0 — remain in `exeris-sdk-source-model`. The parser/writer (free to evolve) lives in `-io`, so the two version independently.
5. **Consumers depend on the narrowest coordinate.** Codegen and the processor's JSON layer depend on `source-model` (no JavaParser); only consumers that parse or rewrite source (LSP, future tooling) add `-io`.
6. **License election is recorded.** The BOM `dependencyManagement` entry for `javaparser-core` carries a comment electing **Apache-2.0** (JavaParser is dual-licensed Apache-2.0 OR LGPL-3.0; the Apache-2.0 election keeps the SDK's Apache-2.0 + Maven Central distribution clean). A note lands in `CONTRIBUTING.md` (creating the file if absent); optionally a `maven-enforcer` banned-dependency rule fails the build if an LGPL-classified variant resolves.
7. **`-io` is publishable.** It mirrors the `attach-sources` + `attach-javadoc` executions of the existing jar modules, gets a BOM `dependencyManagement` entry, and a `<module>` line in the root reactor.

**Pre-emptive ruling (not a testable obligation — design intent for 0.5.0):** When `MutationOp`/`MutationResult` (0.5.0) land, the **records** should live in `source-model` (pure data, importable by LSP and codegen without JavaParser) and the **application** of mutations to source in `-io` — the same isolation rationale as obligations 1–5. RFC-2026-06-03 flagged this for 0.5.0 scoping; this ADR rules on it pre-emptively (no separate RFC required) so the `-io` boundary is drawn with it in mind. It binds only if the 0.5.0 design does not surface a contradicting force; revisit there.

## Consequences

### ✅ Positive Outcomes

- **[+] Zero runtime coupling is preserved.** Pure AST-record consumers never see JavaParser; the invariant that justifies the whole repo layering holds.
- **[+] Independent versioning.** The 1.0-frozen AST records and the still-maturing writer-idempotency rules evolve on separate clocks.
- **[+] Clean LSP coordinate.** `exeris-platform-lsp` depends on one narrow module instead of dragging tooling.
- **[+] Reversible / engine-agnostic.** The `-io` name and package boundary let JavaParser be swapped without a breaking coordinate rename.

### ⚠️ Trade-offs

- **[-] One more publishable module** — extra `attach-sources`/`attach-javadoc` wiring, a BOM entry, and a reactor line.
- **[-] Two coordinates for parsing consumers** — anyone needing the parser adds `-io` on top of `source-model`.
- **[-] Module-proliferation precedent** — must be reserved for genuine dependency-isolation boundaries (this is one), not used for mere organization.

### 📋 What is NOT in scope

- The writer's idempotency / lexical-preservation **algorithm** (implementation concern, spiked against the budgetHQ corpus).
- **Conflict-resolution** semantics (user edits since last codegen vs. tooling-driven mutations) — roadmap 0.3.0 line; may warrant its own RFC.
- **JDK-26 source-level parse verification** — an implementation gate on the spike, not a topology decision.
- `@Capability` (0.4.0) and the mutation **surface design** (0.5.0) — only the module-placement rule for their data vs. logic is fixed here.

## Cross-references

- [RFC-2026-06-03](../rfc/RFC-2026-06-03-source-model-parser-writer.md) — the accepted RFC this ADR locks; full options analysis (A: JavaParser into `source-model`; C: in tooling; D: do nothing) and §ADR prerequisites.
- [ADR-003](ADR-003%20Entity-First%20Development%20Strategy.md) — Entity-First: the annotated class is the single source of truth the parser/writer round-trips.
- `exeris-platform/exeris-platform-lsp` — the consuming repo; its custom LSP methods (`exeris/entityModel`, `exeris/applyMutation`) depend on `-io`.
- `exeris-tooling/exeris-processor` — uses JSR-269 (not JavaParser); confirms the parser/writer is new capability, not a refactor.

## Engineering Protocol

This ADR is **forward-looking** — `exeris-sdk-source-model-io` does not exist yet. Migration owner: repo author; target window: 0.3.0.

1. **Zero-coupling guard.** The existing `zero-runtime-coupling-check` review skill is the gate; once `-io` lands, extend it (or add a CI `dependency:tree` assertion) to fail if `javaparser` appears in the dependency tree of `annotations` or `source-model`.
2. **Scaffold prerequisites (from RFC §ADR prerequisites).** BOM `<javaparser.version>` property + `dependencyManagement` entry (compile scope, Apache-2.0 comment); root reactor `<module>` entry; `attach-sources`/`attach-javadoc` wiring.
3. **Spike before bulk build.** First implementation step parses one real budgetHQ entity → `DomainMetadata` → writes it back, asserting comments + non-Exeris annotations survive; gated on a JDK-26-syntax fixture.
4. **ROADMAP** already reflects the single `-io` module (0.3.0 section) — keep aligned.
