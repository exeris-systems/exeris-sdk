# exeris-sdk-source-model

Canonical AST/metadata describing an Exeris domain.

The module page [`docs/modules/02-source-model.md`](../docs/modules/02-source-model.md) owns the Contract, Hot path, Failure modes and Owning ADRs for this module.

## What lives here

`eu.exeris.sdk.sourcemodel.ast` — Java records describing the shape of a domain:
`DomainMetadata`, `FieldMetadata`, `ActionMetadata`, `ActionParamMetadata`,
`RelationshipMetadata`, `DomainEventMetadata`, `ProjectionMetadata`,
`SagaMetadata`, `SagaStepMetadata`, `EventSourcedMetadata`, `EnumMetadata`,
`GraphMetadata`, `GraphPropertyMetadata`, `GraphEdgeMetadata`, `GraphQueryMetadata`,
`UIMetadata`, `SystemFieldsMetadata`, `InternalApiMetadata`.
(There is no `ValidationMetadata` record: `FieldMetadata` is the constraint
carrier; ADR-054.)

The records are Jackson-serializable (annotated with `@JsonProperty`,
`@JsonInclude`, `@JsonIgnoreProperties`) so they round-trip through the
`exeris-metadata/<entity>.json` format used as the build-time hand-off
between annotation processor and codegen.

## Related packages and modules

- `eu.exeris.sdk.sourcemodel.mutation` (this module) — `MutationOp`, `MutationResult`, `MutationPath` and the baseline-trust types, all JavaParser-free.
- `eu.exeris.sdk.brief` (this module) — the pre-construction brief records.
- `exeris-sdk-source-model-io` — the JavaParser-based reader (`*.java` to `DomainMetadata`), the lexical-preserving writer, and conflict-aware mutation. It is a separate module so JavaParser stays off this module's dependency tree (ADR-037).

These power Studio and IDE plugins (bidirectional sync) without going through
the Maven compile cycle.

## Why here, not in tooling

Every consumer (annotation processor, codegen, Studio, IDE plugin, future
capability registry) must agree on one shape of "what a domain is." Putting
the model in the SDK ensures it has a stable home that does not depend on
any tool's implementation.
