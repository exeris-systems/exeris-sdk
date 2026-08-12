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
 *   <li>{@link eu.exeris.sdk.sourcemodel.io.SourceModelMutationApplier} —
 *       conflict-aware application (ADR-042 slice 4): detect, then apply via the
 *       writer only on a clean verdict, returning an
 *       {@link eu.exeris.sdk.sourcemodel.io.ApplyResult}.</li>
 * </ul>
 *
 * <p><b>Status.</b> Reader (entity attributes, fields, {@code @Relationship}s,
 * actions, {@code @UI}, events, graph/saga/event-sourcing/internal-API, and via
 * {@code readEnums} enum declarations) and capability reader are complete (0.3.0
 * / 0.4.0). Writer does lexical-preserving field/relationship/action mutations.
 * The 0.5.0 mutation surface (ADR-042) is complete on the SDK side: slice 2
 * (the {@code SourceModelConflictDetector} two-{@code DomainMetadata}
 * {@code detect}) detects drift; slice 3 adds the baseline-trust gate (the
 * JSON/source {@code detect} overloads + {@code checkBaselineTrust}) mapping a
 * missing / unparseable / schema-skewed baseline to {@code NO_BASELINE}; slice 4
 * ({@code SourceModelMutationApplier}) applies conflict-aware, with the
 * {@code sourceDigest} as the apply-time {@code STALE_DIGEST} concurrency token.
 *
 * <p><b>Cross-repo status — the baseline-trust stamp is live.</b> The tooling
 * half shipped: {@code ExerisDomainProcessor.buildMetadataNode} writes
 * {@code schemaVersion} and {@code sourceDigest} as sibling fields into each
 * {@code exeris-metadata/<entity>.json}
 * ({@code ExerisDomainProcessor.java:1969-1977}), on the {@code @ExerisDomain}
 * path and on the standalone-{@code @Saga} path alike ({@code :861,887}), so no
 * emitted metadata file is left unstamped. Two properties a caller should know:
 * the digest is {@code SourceDigest.of} over the same raw source text this
 * reader recomputes against, so the {@code STALE_DIGEST} token agrees
 * byte-for-byte; and off a real {@code javac} (no Compiler Tree API) the digest
 * degrades to absent and only {@code schemaVersion} is stamped. A digest-less
 * baseline is still a <em>trusted</em> one — {@code checkBaselineTrust} gates on
 * {@code schemaVersion} alone — it simply leaves the caller with no
 * {@code concurrencyToken}, and {@code apply(..., null)} skips the
 * {@code STALE_DIGEST} check rather than failing it. Conflict-aware
 * <em>batch</em> apply remains deferred.
 *
 * @since 0.3.0
 */
package eu.exeris.sdk.sourcemodel.io;
