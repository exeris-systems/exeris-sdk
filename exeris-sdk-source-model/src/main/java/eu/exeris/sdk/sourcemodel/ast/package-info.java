/**
 * Domain metadata models for Exeris annotation processing.
 * <p>
 * Contains records representing metadata extracted from SDK annotations.
 * These models are serialized to JSON and consumed by code generators.
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
 * @since 0.1.0
 */
package eu.exeris.sdk.sourcemodel.ast;
