# Policy: AST Records & Jackson 3 Wire-Format Contract

The records under `eu.exeris.sdk.sourcemodel.ast.*` constitute the build-time wire format (`exeris-metadata/<entity>.json`) passed from annotation processors (`exeris-processor`) to code generators (`exeris-codegen-*`) and the IDE language server (`exeris-platform-lsp`).

Every consumer must agree on one canonical definition of domain metadata. Adding, modifying, or removing an AST component is a contract change, not an internal refactor.

## Hard Rules

1. **AST types MUST be records, never classes.**
   Jackson 3 relies on record component accessors to discover properties. Using a `class` with record-style accessors causes Jackson 3 to silently omit fields during serialization.
2. **Downstream Jackson consumers must set `FAIL_ON_NULL_FOR_PRIMITIVES=false`.**
   Jackson 3 defaults this feature to `true`. Because the AST contains primitive boolean fields, an explicit `null` in a serialized document will throw an exception unless this posture is disabled.
3. **Handle boxed numeric zero with `@JsonInclude(NON_NULL)`.**
   Under class-level `@JsonInclude(NON_DEFAULT)`, Jackson 3 treats boxed numbers equal to `0` (e.g. `Long(0)`) as default/empty and omits them. For numeric bounds (such as `min`, `max`, `minLength`, `maxLength` in `FieldMetadata`), use per-component `@JsonInclude(NON_NULL)` so that zero values survive serialization (ADR-054).
4. **`jackson-annotations` remains on the 2.x line (2.22).**
   Jackson 3.x intentionally keeps annotations on the 2.x track (`3.0-rc*` was abandoned). The BOM pairs `jackson.version` (databind 3.2.2) with `jackson.annotations.version` (2.22), as dictated by `jackson-bom 3.2.2`. Never unify these two properties to a single version number.
5. **Round-trip test coverage is mandatory for every AST record.**
   Any new or modified AST record must be covered by `AstJsonRoundTripTest` (serialize → deserialize → deep equality assertion).
6. **Mutation surface polymorphic handling.**
   The mutation surface in `eu.exeris.sdk.sourcemodel.mutation` (`MutationOp` / `MutationResult`, ADR-042) must be tested through the sealed interface via `MutationWireFormatTest` to verify that polymorphic type discriminators (`op`, `outcome`) resolve correctly.

## Verification

- `mvn -pl exeris-sdk-source-model test -Dtest=AstJsonRoundTripTest`
- `mvn -pl exeris-sdk-source-model test -Dtest=MutationWireFormatTest`
