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
- **Annotation processing:** `~/exeris-systems/exeris-tooling/exeris-processor/`
- **Code generation:** `~/exeris-systems/exeris-tooling/exeris-codegen-*/`
- **IDE sync & diagnostics:** `~/exeris-systems/exeris-platform/exeris-platform-lsp/`
- **Production entity examples:** `~/exeris-systems/budgetHQ/backend/` and `~/exeris-systems/pbm/`
