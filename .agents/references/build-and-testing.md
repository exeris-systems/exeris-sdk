# Reference: Build & Testing Model

This reference summarizes build commands, coverage thresholds, and verification gates across `exeris-sdk`.

## Primary Build Commands

```bash
# Full Maven reactor build and test verification
mvn clean install

# Targeted module build with dependencies
mvn -pl exeris-sdk-source-model -am verify
mvn -pl exeris-sdk-annotations -am test

# Run UI kit tests and coverage (npm-only)
cd exeris-sdk-ui-kit && npm ci && npm run test:coverage
```

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
