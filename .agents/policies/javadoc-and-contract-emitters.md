# Policy: Javadoc Completeness & Contract Emitters

`exeris-sdk` publishes to Maven Central. Javadoc completeness and machine-readable schema artifacts are non-negotiable compile and release invariants.

## Javadoc Invariants

1. **Maven Central gate:** Every public member of publishable modules must carry complete Javadoc.
2. **`failOnWarnings=true` on 6 modules:**
   - `exeris-sdk-annotations`
   - `exeris-sdk-source-model-io`
   - `exeris-sdk-composition-spec`
   - `exeris-sdk-composition-lifecycle`
   - `exeris-sdk-composition-runtime`
   - `exeris-sdk-tck`
   Any undocumented public API fails compilation in these modules.
3. **`source-model` builder-setter exemption:**
   - Enforced by `JavadocCompletenessTest` instead of `failOnWarnings`.
   - Builder setter methods (`public Builder <name>(...)`) are **deliberately exempt** from Javadoc comments because the record component's `@param` documentation carries the semantics.
   - Do NOT add Javadoc to builder setters. Do NOT expand this exemption to any other public members.

## Annotation Catalog Emitter

`exeris-sdk-annotations` embeds `META-INF/exeris/annotation-catalog.json`:
- Emitted at build time by `AnnotationCatalogProcessor` (on javac processor path; introduces zero runtime dependencies).
- Extracted directly from AST sources, preserving `@param` prose, nested `@interface` declarations, and `@deprecated` replacement pointers.
- Strictly deterministic: contains no timestamps, ensuring reproducible jar hashes.
- Guarded by `AnnotationCatalogProcessorTest` and `AnnotationCatalogContractTest`.

## AST JSON Schema Emitter

`exeris-sdk-source-model` embeds `META-INF/exeris/ast-schema.json`:
- Emitted by `AstSchemaProcessor` on the javac processor path.
- Dual versioning: `sdkVersion` matches `${project.version}`; `astSchemaVersion` matches `ast.schema.version` in `pom.xml` and `SchemaVersion.CURRENT`. Always bump both in unison.
- Component descriptions are read from `@param` tags on the record header. Placing comments directly inside the record declaration body is prohibited and flagged by `AstComponentProseConventionTest`.
- No `required` array: properties with default postures (`NON_NULL`, `NON_EMPTY`, `NON_DEFAULT`) are omitted when empty.
- Guarded by `AstSchemaContractTest`.
