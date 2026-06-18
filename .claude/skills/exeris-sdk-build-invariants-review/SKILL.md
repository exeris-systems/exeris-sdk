---
name: exeris-sdk-build-invariants-review
description: Build-invariants review for exeris-sdk. Use whenever you touch `maven.compiler.release`, the `jacoco-maven-plugin` version, the reactor `<modules>` list, or root/parent build-plugin config — guards the JDK 26 floor, JaCoCo ≥0.8.14, and ui-kit-is-npm-only.
---

# Exeris SDK Build-Invariants Review

## Purpose
Protect the non-negotiable build floor. The most tempting "fix" for a build failure here is the wrong one — lowering `maven.compiler.release` to make a JDK error disappear breaks the whole downstream contract.

## When to Use
- Any change to `maven.compiler.release` anywhere in the reactor.
- Any change to the `jacoco-maven-plugin` version (root / parent `pluginManagement`).
- Any change to the reactor `<modules>` list.
- Any change to root or `exeris-sdk-parent` build-plugin configuration.
- Any reported "21-LTS build failure" or Java-class-version error.

## Canonical Rules (per repo `CLAUDE.md`)
- **`maven.compiler.release=26` across the reactor — non-negotiable.** Even though SDK code uses only record/sealed (21-era) features, the floor is 26 because: the downstream processor (`exeris-tooling/exeris-processor`) runs in the same `javac` invocation as a consumer's project and cannot lag the kernel; the kernel requires Virtual Threads (JDK 24 `synchronized`-pinning fix) + FFM.
- **Do NOT "fix" a build failure by lowering `maven.compiler.release`.** The answer to a 21-LTS failure is "build on 26 and consume over the wire," not a backport. Rationale lives in `README.md` + `ROADMAP.md` — keep aligned if it ever changes.
- **`jacoco-maven-plugin` ≥ 0.8.14** — earlier versions reject Java 26 class file v70. See [[exeris-sdk-coverage-gates-review]].
- **`exeris-sdk-ui-kit` is npm-only** — excluded from the Maven reactor (despite the README listing it). Don't re-add it to `<modules>`. See [[exeris-sdk-publish-readiness-review]].

## Review Procedure
1. **Compiler release floor** — `maven.compiler.release` stays at `26` everywhere. Any lowering → hard reject (it masks the real fix and breaks the downstream-processor contract).
2. **Lowering-as-fix smell** — if the diff lowers the release alongside a "build fix", reject and point to building on 26 + consuming over the wire.
3. **JaCoCo plugin version** — stays ≥ 0.8.14 (Java 26 class v70). A downgrade → reject.
4. **Reactor integrity** — `exeris-sdk-ui-kit` not added to `<modules>`; npm-only invariant preserved.
5. **Doc alignment** — if the floor rationale genuinely changes, `README.md` + `ROADMAP.md` updated in step. Hand off to `exeris-sdk-docs-adr`.
6. **Decision and report** — `APPROVE` / `CONDITIONAL` / `REJECT`.

## Decision Logic
- **APPROVE**: Release floor at 26; JaCoCo ≥0.8.14; ui-kit out of reactor; any rationale change mirrored in docs.
- **CONDITIONAL**: Legitimate build-plugin change that is sound but leaves a doc/rationale note stale — propose the doc sync.
- **REJECT**: `maven.compiler.release` lowered; JaCoCo plugin downgraded below 0.8.14; ui-kit re-added to the reactor.

## Completion Criteria
- Release-floor check, lowering-as-fix check, JaCoCo-version check, reactor-integrity check done.
- Verdict and remediation recorded.

## Review Output Template
1. **Scope analysed** (poms / plugin config / modules touched)
2. **Compiler release floor** (26 held / lowered — reject)
3. **Lowering-as-fix** (absent / masked build fix found)
4. **JaCoCo plugin version** (≥0.8.14 / downgraded)
5. **Reactor integrity** (ui-kit excluded / re-added)
6. **Doc alignment** (n/a / README+ROADMAP synced)
7. **Verdict** (`APPROVE` / `CONDITIONAL` / `REJECT`)
8. **Required actions** (precise and minimal)

## Non-Negotiable Rules
- Never approve lowering `maven.compiler.release` below 26.
- Never approve a `jacoco-maven-plugin` downgrade below 0.8.14.
- Never approve re-adding `exeris-sdk-ui-kit` to the Maven reactor.
