---
title: Glossary
type: reference
visibility: public
owning-repo: exeris-sdk
status: active
last-verified: 2026-10-29
---

# Glossary

Terms as `exeris-sdk` uses them, in alphabetical order. Each entry names the owning type, decision or policy; follow the link for the full rule.

**AST carrier.** The AST record that holds one annotation's data on the wire, for example `FieldMetadata` for `@Field`. `FieldMetadata` is the sole carrier of constraint values such as `minLength` and `pattern`; no separate validation record exists ([ADR-054](adr/ADR-054-field-validation-min-max-pattern-cut.md)).

**Baseline.** The `exeris-metadata/<entity>.json` file the build writes for one entity. A mutation is checked against it by `SourceModelConflictDetector` ([ADR-042](adr/ADR-042-bidirectional-mutation-surface.md)).

**Baseline trust.** The two sibling fields `sourceDigest` and `schemaVersion` stored beside the domain fields of a baseline, modeled by `BaselineTrust`. A missing or mismatched value makes the baseline untrusted, never assumed compatible.

**Brief.** A pre-construction description of what one bounded area of an application is for, read from `docs/briefs/<area>.brief.yaml`. It references the source model and never re-encodes it; `BriefReadiness` decides whether it is ready ([ADR-106](adr/ADR-106-pre-construction-brief-contract.md)).

**Capability.** A self-contained Exeris feature shipped from its own repository, declared through a capability module.

**Capability module.** A class marked `@CapabilityModule` that carries a capability's `@Provides` and `@Requires` declarations. The build-time pipeline resolves them into the manifest ([ADR-038](adr/ADR-038-capability-annotation-surface.md), [ADR-024](adr/ADR-024.link.md)).

**`cap-manifest.json`.** The composition manifest the build emits, read by `CapManifest` in `exeris-sdk-composition-spec`. It lists the composed modules, their `initOrder` and the composition stamp ([ADR-024](adr/ADR-024.link.md)).

**Composition stamp.** The `Stamp` record inside `CapManifest`: a validated flag, a `compositionVersion` and a `contentBinding` hash. `CompositionStampAssertion` checks it at boot and throws `CompositionStampException` on drift.

**Data scope.** The `DataScope` enum, `GLOBAL`, `TENANT` or `UNIVERSE`, that states how an entity's rows are partitioned and shared. It supersedes the `tenantScoped` boolean and encodes author intent only ([ADR-059](adr/ADR-059-data-scope-expression.md)).

**Domain.** The business entity described by an `@ExerisDomain` class. After extraction it is a `DomainMetadata` record.

**Entity-First.** The development model in which the annotated Java class is the only definition of a domain, and SQL, endpoints, DTOs and UI are generated from it ([ADR-003](adr/ADR-003-entity-first-development-strategy.md)).

**Facet.** Two related meanings. `Facet` in `exeris-sdk-tck` names a slice of the metadata hand-off a binder may not have implemented yet, such as `RELATIONSHIPS` or `SAGA`. `SourceModelReader.unmodeledFacets` reports the annotations whose data the reader cannot vouch for.

**Freeze tier.** What the 1.0.0 promise covers for one surface: frozen as declared, preview, removed at 1.0, or members removed at 1.0. It is independent of the extraction status below. Current assignments are in [`stability-matrix.md`](stability-matrix.md).

**LIVE, PARTIAL, RESERVED, DEPRECATED.** The extraction-status words in an annotation's Javadoc `Status:` line. The vocabulary is defined in the `package-info.java` of `eu.exeris.sdk.annotation`.

**Mutation op.** One element of the sealed `MutationOp` family, such as `AddField` or `RenameField`, addressed by a path in the grammar of `MutationPath` and serialized with an `op` discriminator. `SourceModelMutationApplier` applies one op at a time.

**Parity.** The discipline that the `-io` reader reads what the processor writes. `AbstractMetadataParityTck` makes it an executable gate ([ADR-042](adr/ADR-042-bidirectional-mutation-surface.md)).

**Record growth.** The stance that an AST record may gain a trailing component and keep its previous constructor. The rule is in [`MIGRATION-0.x-to-1.0.md`](../MIGRATION-0.x-to-1.0.md); `RecordConstructorLedgerTest` enforces the constructor half.

**SchemaVersion.** The constant `SchemaVersion.CURRENT` naming the wire-format shape. It is independent of the Maven version and changes only when an AST shape changes.

**SKU.** A vertical product artifact composed from a set of capabilities. `CapManifest` describes the capability modules a SKU composes, and the generated SKU bootstrap invokes the composition runtime ([ADR-024](adr/ADR-024.link.md)).

**Source digest.** The hex SHA-256 of the normalized source text, computed by `SourceDigest.of`. It doubles as the optimistic-concurrency token for a mutation.

**Wire format.** The JSON form of the AST records and mutation family, written by the processor and read by `-io`, tooling and the LSP. The consumer obligations are in the `package-info.java` of `eu.exeris.sdk.sourcemodel.ast`.

**Zero runtime coupling.** The rule that the SDK depends on no downstream repository and that annotations are `@Retention(SOURCE)`. See [`zero-runtime-coupling.md`](https://github.com/exeris-systems/exeris-sdk/blob/main/.agents/policies/zero-runtime-coupling.md).

## Where this does not apply

This page defines SDK terms. It does not define kernel, tooling or platform vocabulary, and it does not list which annotation has which status today. Use [`stability-matrix.md`](stability-matrix.md) for the latter.
