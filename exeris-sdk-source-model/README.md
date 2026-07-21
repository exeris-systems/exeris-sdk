# exeris-sdk-source-model

Canonical AST/metadata describing an Exeris domain.

## What lives here

`eu.exeris.sdk.sourcemodel.ast` — Java records describing the shape of a domain:
`DomainMetadata`, `FieldMetadata`, `ActionMetadata`, `ActionParamMetadata`,
`RelationshipMetadata`, `DomainEventMetadata`, `ProjectionMetadata`,
`SagaMetadata`, `SagaStepMetadata`, `EventSourcedMetadata`, `EnumMetadata`,
`GraphMetadata`, `GraphPropertyMetadata`, `GraphEdgeMetadata`, `GraphQueryMetadata`,
`UIMetadata`, `SystemFieldsMetadata`, `InternalApiMetadata`.
(`ValidationMetadata` was removed in 0.9.0 — `FieldMetadata` is the constraint
carrier; ADR-054.)

The records are Jackson-serializable (annotated with `@JsonProperty`,
`@JsonInclude`, `@JsonIgnoreProperties`) so they round-trip through the
`exeris-metadata/<entity>.json` format used as the build-time hand-off
between annotation processor and codegen.

## Future shape

These planned subpackages will be added in subsequent milestones:

- `eu.exeris.sdk.sourcemodel.parser` — JavaParser-based reader: `*.java` source → `DomainMetadata`
- `eu.exeris.sdk.sourcemodel.writer` — idempotent emitter: `DomainMetadata` → `*.java`, preserving comments/format
- `eu.exeris.sdk.sourcemodel.mutator` — pure-functional patches over `DomainMetadata`
- `eu.exeris.sdk.sourcemodel.diagnostics` — domain-level validation independent of `javax.lang.model`

These power Studio and IDE plugins (bidirectional sync) without going through
the Maven compile cycle.

## Why here, not in tooling

Every consumer (annotation processor, codegen, Studio, IDE plugin, future
capability registry) must agree on one shape of "what a domain is." Putting
the model in the SDK ensures it has a stable home that does not depend on
any tool's implementation.
