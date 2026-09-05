# Policy: Field vs Validation Canonical Scoping

The boundary between field structure and validation constraints was established in 0.2.0, finalized in 0.9.0 (ADR-054), and deprecations removed in 1.0.0. It eliminates attribute duplication across annotations and in the AST.

## Canonical Scoping Rules

1. **`@Field` owns lifecycle and persistence shape only:**
   - Declares: `required`, `inCreate`, `inUpdate`.
   - Never declares constraint attributes (`min`, `max`, `minLength`, `maxLength`, `pattern`, etc.).
2. **`@Validation` is the sole declaration site for constraint rules:**
   - Declares: `min`, `max`, `minLength`, `maxLength`, `pattern`, `email`, `url`, `future`, `past`, etc.
   - Never declares lifecycle flags (`required`, `validateOn` are obsolete/removed).
3. **`FieldMetadata` is the single AST carrier:**
   - `FieldMetadata` holds the constraint values (`minLength`, `maxLength`, `min`, `max`, `pattern`) extracted by the processor from `@Validation`.
   - There is no parallel `ValidationMetadata` carrier (removed in 0.9.0, ADR-054).
4. **Database NOT NULL / not-blank semantics are derived:**
   - Schema generators derive database nullability from `FieldMetadata.required`. They are not separately configured on `@Validation`.
5. **Authoritative rationale lives in `package-info.java`:**
   - `exeris-sdk-annotations/src/main/java/eu/exeris/sdk/annotation/package-info.java`
   - `exeris-sdk-source-model/src/main/java/eu/exeris/sdk/sourcemodel/ast/package-info.java`
   Any changes to scoping rationale must be updated simultaneously in both package-info files.

## Non-Negotiable Bans

- Never reintroduce constraint attributes on `@Field`.
- Never create a separate or duplicate validation container in the AST.
