/**
 * JavaParser-based reader/writer between Java source and the canonical
 * {@link eu.exeris.sdk.sourcemodel.ast.DomainMetadata} model.
 *
 * <p><b>Why a separate module (ADR-037).</b> JavaParser is the SDK's only heavy
 * third-party dependency. Keeping it here — not in {@code exeris-sdk-source-model}
 * — means every consumer that only needs the AST records (the annotation
 * processor's JSON layer, the codegen emitters) never inherits it. Only callers
 * that parse or rewrite {@code .java} source (the Studio/IDE LSP) depend on this
 * module. This preserves the SDK's zero-runtime-coupling invariant.
 *
 * <p><b>Two directions, plus conflict detection.</b>
 * <ul>
 *   <li>{@link eu.exeris.sdk.sourcemodel.io.SourceModelReader} — {@code .java}
 *       → {@code DomainMetadata} (no {@code javac}; usable in an editor).</li>
 *   <li>{@link eu.exeris.sdk.sourcemodel.io.SourceModelWriter} — idempotent
 *       edits to {@code .java}, preserving user comments, formatting, and
 *       non-Exeris annotations via JavaParser's {@code LexicalPreservingPrinter}.</li>
 *   <li>{@link eu.exeris.sdk.sourcemodel.io.SourceModelConflictDetector} —
 *       AST-level three-way drift detection (ADR-042 slice 2): does a
 *       {@code MutationOp} collide with a user edit made since the last codegen
 *       baseline? Compares {@code read(currentSource)} against the baseline
 *       {@code DomainMetadata} and reports a
 *       {@link eu.exeris.sdk.sourcemodel.mutation.MutationResult}.</li>
 * </ul>
 *
 * <p><b>Status.</b> Reader (entity attributes, fields, {@code @Relationship}s,
 * actions, {@code @UI}, events, graph/saga/event-sourcing/internal-API, and via
 * {@code readEnums} enum declarations) and capability reader are complete (0.3.0
 * / 0.4.0). Writer does lexical-preserving field/relationship/action mutations.
 * Conflict detection (0.5.0, ADR-042) is in progress: slice 2 (this
 * {@code SourceModelConflictDetector}) detects drift; the {@code sourceDigest} /
 * {@code schemaVersion} baseline-trust mapping to {@code NO_BASELINE} (slice 3,
 * cross-repo with {@code exeris-tooling}) and conflict-aware application
 * (slice 4) are still to come.
 *
 * @since 0.3.0
 */
package eu.exeris.sdk.sourcemodel.io;
