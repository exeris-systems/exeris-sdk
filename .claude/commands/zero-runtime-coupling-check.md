---
description: Enforce zero runtime coupling — annotations module has NO compile-time deps; source-model only pulls `jackson-annotations`; nothing in this repo imports from kernel / tooling / platform.
argument-hint: PR diff or pom / source change to audit
---

Audit this change for zero runtime coupling.

Coupling rules (per repo `CLAUDE.md`):
- This is the most upstream Exeris repo — nothing in here depends on kernel, tooling, or platform.
- Annotations are `@Retention(SOURCE)`. They must NOT introduce kernel or framework dependencies, even transitively.
- `exeris-sdk-annotations` has NO compile-time deps.
- `exeris-sdk-source-model` only pulls `jackson-annotations`.
- One-way dependency: SDK → consumers (processor / codegen / LSP). SDK NEVER imports from consumers.

Change:
$ARGUMENTS

Please review:
1. Does this PR add any compile-scope dep to `exeris-sdk-annotations/pom.xml`? Hard reject.
2. Does this PR add any compile-scope dep to `exeris-sdk-source-model/pom.xml` beyond `jackson-annotations`? Justify or reject.
3. Does any new import statement in any module reference:
   - `eu.exeris.kernel.*` → hard reject.
   - `eu.exeris.tooling.*` / `eu.exeris.processor.*` / `eu.exeris.codegen.*` → hard reject.
   - `eu.exeris.platform.*` → hard reject.
   - `org.springframework.*` / `jakarta.servlet.*` / `io.netty.*` / `io.projectreactor.*` → hard reject.
4. Does this PR pull in transitive runtime deps via a new third-party library? Investigate.
5. Does the BOM (`exeris-sdk-bom`) accidentally elevate a kernel / framework dep into a managed version? Hard reject.
6. Minimal correction if zero runtime coupling is at risk.

The whole point of being the upstream-most repo is that consumers can pin to it without inheriting kernel / framework baggage. Don't break that property.
