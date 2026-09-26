# Reference: Build & Testing Model

This reference summarizes build commands, coverage thresholds, and verification gates across `exeris-sdk`.

## Primary Build Commands

```bash
# Full Maven reactor build and test verification
mvn clean install -Djapicmp.skip=true

# Targeted module build with dependencies
mvn -pl exeris-sdk-source-model -am verify -Djapicmp.skip=true
mvn -pl exeris-sdk-annotations -am test    # requires catalog module installed once first

# Run UI kit tests and coverage (npm-only)
cd exeris-sdk-ui-kit && npm ci && npm run test:coverage
```

> **japicmp baseline:** any goal that reaches `verify` needs `-Djapicmp.skip=true` on a machine without a local `0.11.0` install. japicmp compares against the last released version, `0.11.0`, which was never published to Maven Central, and an absent baseline is configured to fail rather than pass. CI passes the flag in `build.yml`, `release.yml`, `release-assets.yml` and `guardrails.yml`. The flag comes off when the 0.13.0 line opens against a Central-resolvable `0.12.0` baseline; the root `pom.xml` comment beside `japicmp.baseline.version` is canonical. `test` stops before `verify`, so it needs no flag.

> **Build wrinkle:** `annotationProcessorPaths` is not a Maven dependency edge. On a clean checkout, a partial build like `mvn -pl exeris-sdk-annotations -am ...` fails to resolve `AnnotationCatalogProcessor` until `exeris-sdk-annotation-catalog` has been installed at least once. Run the full reactor `mvn clean install` first (or `mvn -pl exeris-sdk-annotation-catalog install`).


## Module Coverage & Quality Gates

| Module | Mechanism | Threshold / Invariant |
|:---|:---|:---|
| `exeris-sdk-source-model` | JaCoCo (`jacoco-maven-plugin` ≥ 0.8.14) | **0.85 BUNDLE-level** on `INSTRUCTION` and `LINE` |
| `exeris-sdk-annotations` | Reflection test (`AnnotationContractTest`) | 100% `@Retention(SOURCE)` and presence of `@Target` |
| `exeris-sdk-ui-kit` | Vitest (v8 provider) | **85% per-file** on lines / statements / branches / functions |
| Reactor (all modules) | Bytecode check (`ClassFileBaselineTest`) | Emitted class-file major ≤ 69 (JDK 25 LTS) |

## Specialized Verification Suites

- **Wire-Format:** `AstJsonRoundTripTest` exercises Jackson serialization, deserialization, and deep equality on all AST records.
- **Polymorphic Mutations:** `MutationWireFormatTest` validates round-trip serialization through sealed `MutationOp` and `MutationResult` hierarchies.
- **UI Kit Drift Tests:**
  - `theme.test.js`: Checks parity between v3 preset, v4 `@theme`, and `index.css`.
  - `default-theme-drift.test.js`: Validates `defaultTheme` vs `index.css` bidirectionally.
  - `tailwind-v4-compile.test.js`: Compiles `theme.css` via real Tailwind v4 and validates utility mapping.
  - `dark-mode-signal.test.js`: Asserts absence of `@media (prefers-color-scheme: dark)` when `.dark` class mode is configured.
