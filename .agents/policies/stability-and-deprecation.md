# Policy: API Stability & Deprecation Pipeline

`exeris-sdk` is the foundational contract for all Exeris code generation and domain processing. Stability discipline protects downstream consumers from silent build breakages.

## Version Lifecycle

- **0.x (Development Line):** Breaking changes are permitted between minors, but must follow the deprecation pipeline whenever feasible. Downstream consumers pin exact SDK versions.
- **1.0.0 (GA Freeze):** Public API is permanently frozen. Only strictly backwards-compatible additive minors and patch fixes are admitted.

## Deprecation Pipeline Rules

When removing or renaming any public API (annotation, attribute, AST record, public method):

1. **Annotate with `@Deprecated(forRemoval = true)`** on the target element.
2. **Provide canonical replacement in `@deprecated` javadoc:**
   Point callers to the replacement type or method using `{@link ...}` syntax.
3. **Preserve fallback in processor for ≥ 1 minor release:**
   The downstream processor (`exeris-processor`) must continue to read the deprecated construct with a compiler warning for at least one minor release before hard removal.
4. **Document in `MIGRATION.md`:**
   Add a dedicated section detailing:
   - What changed and why.
   - Code before / after diffs.
   - The deprecation milestone and scheduled removal milestone.

## Non-Negotiable Bans

- Never remove a public annotation or AST component without first evaluating downstream consumer impact in `exeris-tooling` and `exeris-platform`.
