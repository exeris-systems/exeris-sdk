# Reference: Downstream Consumers & Architecture

`exeris-sdk` is the foundational contract layer. Downstream repositories consume its annotations and AST records over a one-way dependency boundary.

```
exeris-sdk-annotations  (compile-time markers, RetentionPolicy.SOURCE)
        │
        ▼
exeris-sdk-source-model (Jackson-serializable AST records)
        │
        ▼  (one-way; SDK never imports downstream code)
        ▼
exeris-tooling/exeris-processor      ← extracts AST from annotated Java sources
exeris-tooling/exeris-codegen-*      ← emits backend + frontend code from AST
exeris-platform/exeris-platform-lsp  ← bidirectional Studio/IDE synchronization
```

## Architectural Premise: Entity-First (ADR-003)

The Exeris platform operates on the Entity-First paradigm:
- The Java domain class annotated with `@ExerisDomain` is the **single source of truth**.
- Database schemas, REST/GraphQL endpoints, DTOs, and Angular UI views are derived generated artifacts.
- No hand-written `.proto` or OpenAPI contracts.

## Reference-First Lookup Guidelines

Before altering annotation attributes, AST components, or lifecycle hooks, verify how downstream components consume them:
- **Annotation processing:** `exeris-tooling`, module `exeris-processor/`
- **Code generation:** `exeris-tooling`, modules `exeris-codegen-*/`
- **IDE sync & diagnostics:** `exeris-platform`, module `exeris-platform-lsp/`
- **Production entity examples:** `budgetHQ` (`backend/`) and `pbm`

These are **sibling repositories in a full ecosystem checkout**, and the paths inside them are
repository-relative. A clone of `exeris-sdk` alone does not contain them: a grep that finds nothing
means they are not checked out, not that the pattern is unused.
