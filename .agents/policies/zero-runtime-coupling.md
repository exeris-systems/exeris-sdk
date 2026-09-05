# Policy: Zero Runtime Coupling

`exeris-sdk` is the **most upstream** repository in the Exeris Systems ecosystem. Downstream consumers (`exeris-tooling`, `exeris-platform`, `exeris-kernel`, `budgetHQ`) depend on the SDK, but the SDK depends on none of them.

## Hard Rules

1. **Annotations are `@Retention(SOURCE)`.**
   Every `@interface` declared in `exeris-sdk-annotations` must be `@Retention(RetentionPolicy.SOURCE)`. It must never leak into bytecode as `CLASS` or `RUNTIME`. Annotations exist solely to mark intent for downstream annotation processors (such as `exeris-processor`) and tooling.
2. **Annotations module has ZERO compile-time dependencies.**
   `exeris-sdk-annotations` must have an empty `<dependencies>` block in its `pom.xml`. It depends only on standard Java runtime libraries.
3. **Source-model module pulls only `jackson-annotations`.**
   `exeris-sdk-source-model` contains the canonical AST records. It pulls only `com.fasterxml.jackson.core:jackson-annotations` to define serialization metadata. It must never depend on `jackson-databind` (which is a toolchain/consumer concern) or runtime frameworks.
4. **Heavy dependencies are confined to leaf modules.**
   - JavaParser is strictly confined to `exeris-sdk-source-model-io` (ADR-037). It must never appear transitively or directly in `annotations` or `source-model`.
   - `exeris-sdk-composition-spec` has zero dependencies.
   - `exeris-sdk-composition-lifecycle` has zero dependencies (enforcer-proven).
5. **Never import downstream code.**
   Never add dependencies or imports pointing to `exeris-tooling`, `exeris-platform`, `exeris-kernel`, or application code.

## Verification

- `AnnotationContractTest` in `exeris-sdk-annotations` reflects across all annotations in root, `system`, and `security` packages to enforce `@Retention(SOURCE)` and presence of `@Target`.
- Maven dependency tree audits (`mvn dependency:tree`).
