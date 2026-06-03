# RFC-2026-06-03: Where do the 0.3.0 source-model parser & writer live without breaking zero runtime coupling?

| Field             | Value                                                                 |
|:------------------|:----------------------------------------------------------------------|
| **Status**        | **ACCEPTED**                                                        |
| **Author(s)**     | arkstack-dev                                                          |
| **Date Opened**   | 2026-06-03                                                            |
| **Date Closed**   | 2026-06-03                                                           |
| **Target ADR(s)** | [ADR-037](../adr/ADR-037-source-model-io-module.md) (locks the module topology) |
| **Affected Repos**| `exeris-sdk`, `exeris-platform` (lsp), `exeris-tooling` (codegen consumes AST) |
| **Reviewers**     | —                                                                    |

## Question

The 0.3.0 milestone adds a **standalone `.java` → `DomainMetadata` parser** and an **idempotent `DomainMetadata` → `.java` writer** (preserving user comments, formatting, and non-Exeris annotations) to the SDK. Both require [JavaParser](https://javaparser.org/) — the first heavy third-party dependency in a repo whose defining invariant is *zero runtime coupling* (`annotations` has no deps; `source-model` pulls only `jackson-annotations` at compile scope). **Where does the parser/writer code live, so that consumers who only need the AST records do not transitively inherit JavaParser?**

## Context

`exeris-platform-lsp` already depends on `exeris-sdk-source-model` and its README states plainly: *"Real implementation lands once `exeris-sdk-source-model` ships its JavaParser-based parser/writer."* So two sub-questions are **already informally decided** and are not re-litigated here:

1. **Library** — JavaParser (lightweight, no `javac` invocation; the existing `ExerisDomainProcessor` uses JSR-269 `javax.lang.model` *inside* `javac`, which is unavailable to an in-editor LSP). The parser/writer is genuinely new capability, **not** a duplicate of the processor's source-reading.
2. **Repo** — `exeris-sdk`, not `exeris-tooling`. Putting it in tooling would invert the dependency arrow (tooling depends on SDK, never the reverse) and force the LSP to drag `exeris-codegen-*`.

What is **not** decided is the module topology *inside* `exeris-sdk`. The naive reading of the roadmap ("add parser/writer to `exeris-sdk-source-model`") would make JavaParser a compile-scope dependency of `source-model`. Because `source-model` is the module every downstream consumer depends on for the AST records (`exeris-tooling/exeris-codegen-*`, `exeris-processor` for the JSON shape, the LSP, the future capability registry), that single edit silently pushes a multi-MB parser onto consumers whose entire need is "deserialize `DomainMetadata` from JSON." That is precisely the coupling regression the repo's whole layering exists to prevent. Leaving it unanswered blocks `exeris-platform` (LSP custom methods `exeris/entityModel`, `exeris/applyMutation` cannot ship) and, downstream, the 0.4.0 `@Capability` work the LSP surfaces.

## Investigation

### Prior art

- **`ExerisDomainProcessor` (exeris-tooling)** — reads source via JSR-269 `Element`/`TypeElement`/`TypeMirror` during compilation and emits `DomainMetadata` as JSON via Jackson. One-directional (source → AST → JSON); no round-trip, no JavaParser. Confirms the parser/writer is a separate concern, not a refactor of existing code.
- **Round-trip test precedent (this repo, PR #12 / `AstJsonRoundTripTest`)** — `source-model` already keeps a heavy dep (`jackson-databind`) at **`test` scope** rather than compile scope, precisely so consumers pulling the AST records don't inherit a full databind stack. The same discipline applies to JavaParser, only more so (JavaParser cannot be test-scoped — it's the parser's runtime engine).
- **Codegen (exeris-tooling)** consumes the AST to emit *fresh* Java/TS/OpenAPI artifacts. It never modifies user-authored files; the idempotent writer (LexicalPreservingPrinter territory) is a distinct capability the codegen path does not provide.

### Constraints

- **Zero runtime coupling (repo invariant).** `annotations` = 0 deps; `source-model` = `jackson-annotations` only at compile scope — observable in `exeris-sdk-source-model/pom.xml` (`jackson-databind` is `test`-scope) and the root `pom.xml` description. (The prose statement of this invariant lands in `CLAUDE.md` via the in-flight repo-tooling PR #13; it is not yet on `main`, so this RFC cites the POMs as the authoritative evidence.) This is the hard constraint the whole RFC turns on.
- **Maven Central / licensing.** JavaParser is dual-licensed **Apache-2.0 OR LGPL-3.0** — the consumer elects. *Verified against upstream:* `javaparser/LICENSE.LGPL` is "GNU LESSER GENERAL PUBLIC LICENSE Version 3, 29 June 2007" and Maven Central lists the artifact as LGPL-3.0 + Apache-2.0 (not 2.1). The **Apache-2.0** election is compatible with the SDK's Apache-2.0 + Maven Central distribution; we must record that election explicitly (see §ADR prerequisites for the mechanism).
- **Publishable-module checklist.** Any new publishable module must mirror the `attach-sources` + `attach-javadoc` executions — pattern in `exeris-sdk-annotations/pom.xml` / `exeris-sdk-source-model/pom.xml` — or Central rejects it.
- **JDK 26 floor.** New module inherits `maven.compiler.release=26`; confirm the chosen JavaParser version supports parsing 26-level sources (records, sealed types, pattern matching) the SDK's own entities use.

### Data gathered

- `exeris-sdk-source-model/pom.xml` compile-scope deps today: `exeris-sdk-annotations`, `jackson-annotations`. (`jackson-databind` is **test**-scope.)
- `exeris-platform-lsp/pom.xml` already declares a dependency on `exeris-sdk-source-model` — it is a live consumer waiting on this decision.
- No `javaparser` reference exists anywhere in `exeris-tooling` or `exeris-sdk` today — greenfield introduction.

### Spike outcomes

None yet. A thin spike (parse one budgetHQ entity → `DomainMetadata`, write it back, assert non-Exeris annotations + comments survive) is proposed as the first implementation step once topology is locked.

## Options Considered

### Option A: Parser/writer directly in `exeris-sdk-source-model`

Add JavaParser as a compile-scope dependency of the existing module; parser/writer classes live alongside the AST records.

**Pros:**
- Fewest modules; one import for consumers who want everything.
- Matches the most literal reading of the roadmap line.

**Cons:**
- **Breaks zero runtime coupling.** Every AST-record consumer (codegen, processor JSON layer, future capability registry) transitively inherits JavaParser, whether or not they parse source.
- Pollutes the published `source-model` artifact's dependency tree on Maven Central.
- No clean way to walk this back post-1.0 without a breaking module split.

**Cost:** Low upfront engineering, high architectural debt.

### Option B: New sibling module `exeris-sdk-source-model-io`

A new publishable module that depends on `exeris-sdk-source-model` (for the AST records) + JavaParser. Houses both the parser (`.java` → `DomainMetadata`) and the idempotent writer. `source-model` stays dependency-light.

**Name: `exeris-sdk-source-model-io`** (packages `eu.exeris.sdk.sourcemodel.io`). The module does source-text ↔ model round-tripping, so it names the *abstraction* (read + write), not just the parse half — `-parser` would mislead, since the writer is the harder, more contract-relevant side. It also deliberately avoids naming the engine (`-javaparser`): a published Maven coordinate is expensive to rename, and the engine could be swapped behind a stable `io` package boundary without a breaking coordinate change.

**Pros:**
- **Preserves zero runtime coupling** — pure-AST consumers depend on `source-model` and never see JavaParser; only the LSP / writer-needing consumers add the parser module.
- Mirrors the test-scope discipline already used for `jackson-databind`.
- Clean publishable boundary; reversible (the parser module can evolve or be deprecated independently of the frozen AST contract).
- Lets the writer's idempotency/lexical-preservation concerns evolve without touching the wire-format records.

**Cons:**
- One more module to publish (extra `attach-sources`/`attach-javadoc` wiring, BOM entry).
- Consumers needing parsing add a second coordinate.

**Cost:** Moderate upfront (module scaffold + BOM/parent wiring); low ongoing.

### Option C: Parser/writer in `exeris-tooling`

Place the capability next to codegen in tooling.

**Pros:**
- Co-located with the other source/AST machinery (`ExerisDomainProcessor`, codegen).

**Cons:**
- **Inverts the dependency arrow** — `exeris-platform-lsp` would depend on `exeris-tooling`, dragging `exeris-codegen-*` into the LSP runtime; contradicts the SDK-is-most-upstream layering and the platform README.
- The parser/writer is contract-shaped (the canonical "read/write a domain from source"); splitting it from the AST records it produces invites metamodel drift.

**Cost:** Low engineering, high coupling/architecture cost.

### Option D (do nothing)

Leave 0.3.0 unstarted; `exeris-platform-lsp` stays a stub, 0.4.0 `@Capability` (which the LSP surfaces via `exeris/listCapabilities`) stays blocked downstream.

## Recommendation

**Option B — introduce a new `exeris-sdk-source-model-io` module that depends on `source-model` + JavaParser; keep `source-model` dependency-light.**

This is the only option that delivers the parser/writer *and* honours the repo's one non-negotiable invariant. The cost is a single extra module — and the SDK already pays exactly this kind of split (the `annotations` / `source-model` boundary exists for the same reason: keep the most-depended-on artifact lean). The test-scope treatment of `jackson-databind` is the precedent in miniature: heavy machinery stays off the compile classpath of pure consumers. The LSP gets a clean, narrow coordinate to depend on; codegen and the processor's JSON layer never learn JavaParser exists. Because the AST records (the frozen-at-1.0 wire contract) stay in `source-model` and the parser/writer (implementation, free to evolve) stay in the new module, the contract surface and the tooling surface can version independently — which matters when 1.0 freezes the records but the writer's idempotency rules are still maturing against the budgetHQ corpus.

### Why not the alternatives?

- **Option A** — silently makes JavaParser a transitive dep of every AST consumer; a direct violation of zero runtime coupling that's expensive to reverse after 1.0.
- **Option C** — inverts the SDK→tooling dependency arrow and drags codegen into the LSP; contradicts the platform README and invites metamodel drift.
- **Option D** — indefinitely blocks `exeris-platform` and, transitively, the `@Capability` work.

### Risks of the recommendation

- **Module proliferation** — if every future capability spawns a module, the reactor sprawls. Mitigation: reserve new modules for genuine dependency-isolation boundaries (this is one); don't split for organization alone.
- **Writer idempotency is hard** — preserving user formatting/comments/non-Exeris annotations on rewrite is the genuinely difficult part and is independent of topology. Mitigation: spike against real budgetHQ entities early (see §Spike outcomes); property-test round-trip stability.
- **JavaParser JDK-26 source-level support** — must verify the pinned version parses 26-level constructs. Mitigation: gate the spike on a 26-syntax fixture.

## ADR prerequisites

The ADR that accepts this RFC should land the module half-scaffolded-proof — these are the concrete wiring tasks, flagged here so the scaffold doesn't ship in a half-done state:

1. **BOM** (`exeris-sdk-bom/pom.xml`) — add a `<javaparser.version>` property and a `<dependencyManagement>` entry for `com.github.javaparser:javaparser-core` with explicit `<scope>compile</scope>`. Record the **Apache-2.0 license election** in a comment on that entry.
2. **Root reactor** (`pom.xml`) — add `<module>exeris-sdk-source-model-io</module>` (currently only `bom`, `parent`, `annotations`, `source-model`).
3. **Publishable wiring** — mirror `attach-sources` + `attach-javadoc` from the existing jar modules.
4. **License-election enforcement** — at minimum the BOM comment above plus a note in `CONTRIBUTING.md`; *optionally* a `maven-enforcer` banned-dependency rule that fails the build if the LGPL-classified variant is ever resolved, so the Apache-2.0 election can't silently regress.

## Decision Record

<Filled in when status reaches ACCEPTED / REJECTED / WITHDRAWN.>

| Field                | Value     |
|:---------------------|:----------|
| **Outcome**          | ACCEPTED  |
| **Date**             | 2026-06-03 |
| **Resulting ADR(s)** | [ADR-037](../adr/ADR-037-source-model-io-module.md) — Isolate the source-model parser/writer in a dedicated `exeris-sdk-source-model-io` module |
| **Notes**            | Option B adopted. Module named `-io` (holds parser + writer; engine-agnostic coordinate). LGPL version verified = 3.0; elect Apache-2.0. |

## Open questions / follow-ups

- ~~Which LGPL version?~~ **Resolved: LGPL-3.0** (verified against upstream `LICENSE.LGPL` "Version 3, 29 June 2007" + Maven Central). Remaining action: pin the **Apache-2.0** election + enforcement mechanism (see §ADR prerequisites).
- Verify pinned JavaParser version parses **JDK 26** source constructs (records, sealed, pattern matching) — owner: author, gates the spike.
- Writer conflict-resolution semantics (user edits since last codegen vs. tooling-driven mutations) — roadmap 0.3.0 line; may warrant its own RFC if non-trivial.
- **0.5.0 `MutationOp`/`MutationResult` home.** Recommended split: the **records stay in `source-model`** (pure data, no JavaParser) so the LSP and codegen can import the mutation vocabulary without dragging the parser; the **application of mutations** (rewriting source) lives in `exeris-sdk-source-model-io`. Confirm at 0.5.0 scoping — flagged now so the `-io` boundary is drawn with it in mind.
