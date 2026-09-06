---
title: "ADR-069: The SDK baselines on JDK 25 LTS, and ships one artifact line"
type: adr
visibility: public
owning-repo: exeris-sdk
status: active
last-verified: 2026-08-12
slug: adr/ADR-069
---

# ADR-069: The SDK baselines on JDK 25 LTS, and ships one artifact line

| Attribute       | Value                                                                                          |
|:----------------|:-----------------------------------------------------------------------------------------------|
| **Status**      | **ACCEPTED**                                                                                   |
| **Deciders**    | Arkadiusz Przychocki                                                                           |
| **Date**        | 2026-08-12                                                                                     |
| **Scope**       | `exeris-sdk` (build baseline)                                                                  |
| **Owning Repo** | `exeris-sdk`                                                                                    |
| **Driven By**   | [kernel ADR-066](https://github.com/exeris-systems/exeris-kernel/blob/main/docs/adr/ADR-066-preview-clean-ga-baseline.md) (ACCEPTED 2026-08-08, kernel v0.11.0) |
| **Relates To**  | [spring-runtime ADR-068](https://github.com/exeris-systems/exeris-spring-runtime/blob/main/docs/adr/ADR-068-two-track-jdk-artefact-model.md) — the two-track artefact model this ADR declines, using its own reasoning |

> The number is held by the [`exeris-docs/adr-index.md`](https://github.com/exeris-systems/exeris-docs/blob/main/adr-index.md) row.

## Context and Problem Statement

`maven.compiler.release=26` has been a documented, deliberately non-negotiable invariant of this repo. `README.md` states the reason plainly: "The SDK is pinned to JDK 26 because the **Exeris platform kernel** is, and the annotation processor … runs in the same `javac` invocation as your project — it cannot lag behind the kernel it generates code for."

**Kernel v0.11.0 falsified the premise.** ADR-066 moved the kernel's distributable line to JDK 25 LTS with no `--enable-preview`, and measured the JDK-26 target as costing nothing: the full kernel reactor and 747 tests are green at `--release 25`, "no JDK-26-only API is used anywhere in the tree; the `26` in the build was never load-bearing." The SDK's floor is now *above* the runtime it exists to describe.

That is not a cosmetic inconsistency, because of what this repo is. `exeris-sdk-annotations` and `exeris-sdk-source-model` are **compile-classpath** artifacts for a consumer's own build. A jar compiled at `--release 26` carries class-file major 70, and `javac` on JDK 25 refuses to read it:

```
class file has wrong version 70.0, should be 69.0
```

So the LTS-only, no-preview consumer that ADR-066 exists to unblock — named in that ADR as a real downstream constraint, not a hypothetical — is unblocked at the kernel and then stopped at the most upstream repo in the ecosystem. Every Exeris annotation is unreachable to them. The SDK is where the widening either completes or does not happen.

The second forcing item is the freeze. 1.0.0 promises a frozen public API. Freezing a surface that an LTS consumer cannot put on a classpath makes the guarantee address an audience that cannot accept delivery. Lowering the floor after 1.0.0 remains legal — it widens rather than narrows — but "the GA that LTS users could not consume" is not a state worth shipping deliberately.

### What was measured, rather than assumed

The same probe ADR-066 ran, run here:

- `mvn clean verify -Dmaven.compiler.release=25` is **green across all six modules**, every test and every gate (JaCoCo 85%, the two snapshot guards, javadoc).
- All six produced jars carry **class-file major 69**, verified by reading the class-file header bytes rather than trusting the flag.
- The SDK's main sources use no API above the 21-era record / sealed-type set; a grep for `Thread.ofVirtual`, `ScopedValue`, `StableValue` and `Gatherers` across `*/src/main/java` returns nothing.

**Stated limitation, and its closure.** The local probe cross-compiled with the JDK 26 `javac` at `--release 25` — only a JRE 25 is installed on the authoring machine, so "the reactor is green *on* a JDK 25 toolchain" was **not** verified locally. That is why the CI matrix change is part of this decision rather than a follow-up. It has since been closed by the matrix itself: on `exeris-sdk#93` the `mvn verify (JDK 25)` row compiled the reactor at `release 25` on Temurin 25 and passed every gate, and the JDK 26 row passed alongside it.

The one preview-adjacent site in the tree confirms the floor was never load-bearing from the other direction too: `DrainRunner` documents choosing a calling-thread + watchdog design *because* `StructuredTaskScope` is preview on the pinned toolchain and "this reactor carries no preview flags". The SDK has been preview-clean all along; it was simply targeting a higher major than anything required.

## 🏁 The Decision

**`maven.compiler.release` moves 26 → 25 across the reactor, matching the kernel's GA baseline. The SDK ships one artifact line — there is no `-preview` counterpart — and CI builds on JDK 25 LTS, with a JDK 26 row kept as a forward-compatibility check.**

Four rulings carry it.

### 1. The floor follows the runtime it describes, and no higher

The SDK is the most upstream repo in the ecosystem: nothing here depends on the kernel, but everything downstream of an author's source passes through these jars. A floor above the kernel's cannot buy anything — there is no JDK-26 API in the tree to preserve — and costs exactly the consumer population the kernel just admitted. Where the kernel's baseline moves at a future LTS, this one follows it; it does not lead it.

### 2. One line, no `-preview` artifact — by ADR-068's own argument

`exeris-spring-runtime` publishes two artefacts (ADR-068), and the reason it gives is specific: JEP 401 value classes are *different source* (`value record` vs `record`), so the preview line carries content that cannot exist on the GA line, and that content must be exercised before the preview line becomes the GA line.

Neither half applies here. The SDK declares annotations and records; there is no preview construct it would adopt and nothing to exercise ahead of an LTS. ADR-068's own ruling (2) then decides the rest: a jar at `--release 25` (major 69) loads unchanged on a JDK 28 JVM, so a content-free `-preview` coordinate "would be a second distribution consumers must reason about in exchange for nothing." The SDK's `-preview` artifact would be exactly that content-free rebuild. It is not published.

This is worth recording rather than leaving to inference, because the ecosystem now has a two-track pattern in two repos and the default reading of "SDK follows the kernel" would be that it tracks both lines. It follows the *baseline*, not the *bifurcation*.

### 3. A guard reads the class-file major, because the flag is not the artifact

`ClassFileBaselineTest` reads the header bytes of a compiled class and fails if the major exceeds 69. This is the SDK-scale analogue of the kernel's `tools/preview-bytecode-scan/`, and it exists for the same reason that ADR-066 gives for preferring bytecode over a source grep: the stamp is what a consumer actually trips over. A property override, a stray module-level `<release>`, or a plugin default that re-raises the target all fail the guard; a comment claiming compliance does not satisfy it. Verified non-vacuous by building at 26 and watching it fail.

### 4. CI baselines on 25 and keeps a 26 row

The release-bearing row is **JDK 25 LTS** — that is the toolchain a published artifact must come from. A second matrix row on JDK 26 stays, cheaply, to catch the opposite regression: source that stops compiling on a newer JDK, which a single-row matrix would surface only when a consumer on 26 hit it. The rows are not interchangeable and the matrix names which is which.

## Consequences

- **Downstream widens, nothing narrows.** Anything that consumed the SDK on JDK 26 continues to; JDK 25 LTS becomes reachable. There is no source change for any consumer and no wire-format, AST or annotation-surface change of any kind — this ADR touches the build target and the documents that justified it.
- **`exeris-tooling` is now the binding constraint, and this ADR does not move it.** The processor runs inside a consumer's `javac`, so an LTS consumer needs the *processor* jar at major 69 too. That repo has its own floor and its own decision to make; the SDK half is a prerequisite for it, not a substitute. Until tooling follows, an LTS consumer can compile against the annotations but cannot run the processor.
- **The "JDK 26 is not negotiable" invariant is retired**, along with its statement in `CLAUDE.md`, `README.md`, `ROADMAP.md`, the annotations `package-info`, and the seven `.claude/` agent and skill files that restated it as a guardrail. What replaces it is narrower and true: *do not raise the floor above the kernel's GA baseline, and do not lower it below what the SDK's own sources need.* The JaCoCo ≥ 0.8.14 requirement stays — it was justified by class-file v70 support, and while v69 no longer needs it, downgrading the plugin buys nothing.
- **The README's kernel-requirements section becomes a runtime note, not a floor argument.** Virtual Threads and FFM are real kernel requirements and are GA at 21 and 22 — they never implied 26. The section said so in passing already ("records and pattern matching … landed in 16 and 21 — they're not what fixes the floor at 26"); with the kernel's own measurement in hand, nothing fixes the floor at 26.
- **The 1.0.0 freeze now covers an artifact an LTS consumer can accept**, which was the point of doing this before the cut rather than after.

## Alternatives considered

**Stay at 26 and revisit after 1.0.0.** Legal — lowering a floor is a widening and permitted in a 1.x minor. Rejected on what it costs in the interim: the freeze would ship a GA surface the LTS audience cannot put on a classpath, and the ecosystem's most upstream repo would be the one blocking a widening every other repo had already made.

**Follow `exeris-spring-runtime` into a two-track model (GA + `-preview`).** Rejected by ADR-068's own ruling (2) — with no preview construct in the SDK's source, the second artifact is a JDK-target rebuild that loads nowhere the first one does not, in exchange for a second coordinate every consumer must reason about.

**Drop to 21 LTS instead**, the version the README names as the most common consumer baseline. Rejected: it would put the SDK *below* the kernel's baseline and re-introduce the same class of mismatch pointing the other way, and 21 buys nothing 25 does not — the constraint that produced this decision is the kernel's GA line, not a search for the lowest reachable floor.

## Not in scope

`exeris-tooling`'s own baseline (its decision, prerequisite met by this one). The `exeris-sdk-ui-kit` npm package, which has no JDK. Maven Central publishing, still sequenced behind the kernel. Whether a future LTS moves the baseline again — that follows the kernel and needs no new decision here.
