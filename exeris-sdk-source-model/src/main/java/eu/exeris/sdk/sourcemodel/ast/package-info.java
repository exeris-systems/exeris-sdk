/**
 * Domain metadata models for Exeris annotation processing.
 * <p>
 * Contains records representing metadata extracted from SDK annotations.
 * These models are serialized to JSON and consumed by code generators.
 *
 * <h2>JSON wire-format contract for consumers</h2>
 * <p>The processor writes these records to {@code *.json} files at build
 * time and downstream tooling (codegen, LSP, IDE plugins) reads them back.
 * For round-trip fidelity, any consumer constructing its own Jackson
 * {@code ObjectMapper} <strong>must</strong> configure:
 * <ul>
 *   <li>{@code FAIL_ON_NULL_FOR_PRIMITIVES = false} — Jackson 3 defaults this
 *       to {@code true}, but our records use primitive booleans heavily with
 *       {@code @JsonInclude(NON_DEFAULT)} / {@code NON_NULL}, so absent fields
 *       arrive as {@code null}. Without this flag, deserialization throws on
 *       any record that has a default-valued boolean.</li>
 * </ul>
 * The wire-format guard test {@code AstJsonRoundTripTest} configures the
 * mapper accordingly and is the canonical reference.
 *
 * <h2>FieldMetadata vs ValidationMetadata — canonical scoping (0.2.0)</h2>
 * <p>The annotation surface ({@code @Field} / {@code @Validation}) historically
 * exposed overlapping attributes. The AST mirrors the resolution chosen on the
 * annotation side:
 * <ul>
 *   <li><strong>{@link eu.exeris.sdk.sourcemodel.ast.FieldMetadata}</strong>
 *       owns {@code required}, {@code inCreate}, {@code inUpdate}. These are
 *       field-shape / lifecycle facts — populated from {@code @Field} only.
 *       {@code @Validation.required} (deprecated, removal in 1.0.0) is ignored
 *       by the processor.</li>
 *   <li><strong>{@link eu.exeris.sdk.sourcemodel.ast.ValidationMetadata}</strong>
 *       owns the constraint rules: {@code minLength}, {@code maxLength},
 *       {@code min}, {@code max}, {@code pattern}, {@code email}, {@code url},
 *       {@code future}, {@code past}. {@code notNull} / {@code notBlank} are
 *       <em>expected to be derived</em> from {@code FieldMetadata.required}
 *       at processor-build time (see {@code ExerisDomainProcessor} in the
 *       tooling repo) — they are not separately configurable on
 *       {@code @Validation} since 0.2.0.</li>
 * </ul>
 * <p>{@code FieldMetadata} also carries a few constraint-shaped fields
 * ({@code minLength}, {@code maxLength}, {@code min}, {@code max},
 * {@code pattern}) that overlap with {@code ValidationMetadata}. Those remain
 * for now — they describe basic shape hints used by the processor before
 * {@code @Validation} is consulted. A full deduplication is tracked in the
 * 0.6.0–0.9.0 cleanup phase once budgetHQ usage informs the right cut.
 *
 * <h2>Capability surface (0.4.0)</h2>
 * <p>Capabilities are a top-level concept, parallel to entities — a
 * {@code @CapabilityModule} class is read into a
 * {@link eu.exeris.sdk.sourcemodel.ast.CapabilityModuleMetadata} the way an
 * {@code @ExerisDomain} class is read into a
 * {@link eu.exeris.sdk.sourcemodel.ast.DomainMetadata} (it is <em>not</em> a
 * facet of {@code DomainMetadata}). It holds the services the cap
 * {@link eu.exeris.sdk.sourcemodel.ast.ProvidesMetadata provides} and
 * {@link eu.exeris.sdk.sourcemodel.ast.RequiresMetadata requires}, plus the
 * {@code lifecycleOwner}. Service identity is stored as the
 * <strong>source-written</strong> name (FQN from the processor path, written
 * form from the {@code -io} reader); canonical FQN normalization is the
 * build-time tooling's responsibility, not the SDK's. Resolution, the
 * dependency DAG, version-range intersection, and the cap manifest are likewise
 * tooling concerns — the AST carries only the declared shape. See ADR-038.
 *
 * @since 0.1.0
 * @see eu.exeris.sdk.sourcemodel.ast.CapabilityModuleMetadata
 */
package eu.exeris.sdk.sourcemodel.ast;
