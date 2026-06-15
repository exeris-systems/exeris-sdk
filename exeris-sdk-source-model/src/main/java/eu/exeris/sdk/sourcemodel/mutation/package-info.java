/**
 * The bidirectional mutation surface (SDK 0.5.0, ADR-042).
 *
 * <p>Where {@link eu.exeris.sdk.sourcemodel.ast} models <em>what a domain is</em>
 * (metadata extracted from annotations), this package models <em>operations
 * over</em> that metadata and their outcomes — the vocabulary LSP, Studio, and
 * IDE plugins carry across the wire to drive design-time edits:
 *
 * <ul>
 *   <li>{@link eu.exeris.sdk.sourcemodel.mutation.MutationOp} — a sealed family
 *       of path-addressed mutations (add/remove/rename/change field, add/remove
 *       relationship, change cardinality, add/remove action).</li>
 *   <li>{@link eu.exeris.sdk.sourcemodel.mutation.MutationResult} — the four
 *       outcomes of applying an op: {@code SUCCESS}, {@code CONFLICT},
 *       {@code VALIDATION_ERROR}, {@code NO_BASELINE}.</li>
 *   <li>{@link eu.exeris.sdk.sourcemodel.mutation.MutationPath} — the path
 *       grammar shared by op targeting and conflict reporting.</li>
 * </ul>
 *
 * <h2>Layering (ADR-037, ADR-042)</h2>
 * <p>These are <strong>pure, JavaParser-free, Jackson-serializable</strong>
 * records — they live here so LSP and codegen can depend on them without
 * pulling JavaParser. Drift detection and conflict-aware <em>application</em>
 * live in {@code exeris-sdk-source-model-io} (slices 2 and 4); this module
 * defines and serializes the vocabulary only. The SDK reports outcomes; merge /
 * prompt / auto-apply policy is the host's (LSP / Studio).
 *
 * <h2>Wire-format contract</h2>
 * <p>Both {@code MutationOp} and {@code MutationResult} are polymorphic JSON
 * with a string discriminator ({@code "op"} / {@code "outcome"}). They obey the
 * same Jackson 3 contract as the AST records — consumers MUST configure
 * {@code FAIL_ON_NULL_FOR_PRIMITIVES = false} (see
 * {@link eu.exeris.sdk.sourcemodel.ast} package-info). The round-trip guard is
 * {@code MutationWireFormatTest}.
 *
 * @since 0.5.0
 * @see eu.exeris.sdk.sourcemodel.ast
 */
package eu.exeris.sdk.sourcemodel.mutation;
