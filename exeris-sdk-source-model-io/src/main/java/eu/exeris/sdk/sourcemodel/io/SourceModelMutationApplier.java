package eu.exeris.sdk.sourcemodel.io;

import eu.exeris.sdk.sourcemodel.mutation.MutationOp;
import eu.exeris.sdk.sourcemodel.mutation.MutationPath;
import eu.exeris.sdk.sourcemodel.mutation.MutationResult;
import eu.exeris.sdk.sourcemodel.mutation.SourceDigest;

import java.util.Objects;

/**
 * Conflict-aware application of a {@link MutationOp} to Java source (ADR-042
 * slice 4) — the last step of the bidirectional mutation surface. Composes the
 * {@link SourceModelConflictDetector} (detect first) with the idempotent
 * {@link SourceModelWriter} (apply only on a clean verdict), so a tooling
 * mutation is never written over a non-convergent user edit.
 *
 * <h2>Flow</h2>
 * <ol>
 *   <li><b>Optimistic concurrency (slice 4).</b> When a concurrency token is
 *       supplied, reject with {@link MutationResult.NoBaselineCause#STALE_DIGEST}
 *       if the live source's {@link SourceDigest} no longer matches it — the
 *       source moved under the tool between computing the batch and applying it
 *       (ADR-042 obligation 7).</li>
 *   <li><b>Structural validation (slice 4 {@code VALIDATION_ERROR}).</b>
 *       {@code changeRelationshipCardinality} has no writer mutation behind it
 *       (cardinality change is structural, not an attribute flip) and is rejected
 *       as inapplicable. The path / kind / entity rejections come from detection.</li>
 *   <li><b>Detection (slices 2–3).</b> Trust gate + three-way drift. A non-success
 *       verdict ({@code NO_BASELINE} / {@code CONFLICT} / {@code VALIDATION_ERROR})
 *       short-circuits — the op is not applied.</li>
 *   <li><b>Apply.</b> On a {@code SUCCESS} verdict, write via the idempotent
 *       writer; a convergent op is a no-op (the user already made the edit). A
 *       writer rejection maps to {@code VALIDATION_ERROR}.</li>
 * </ol>
 *
 * <p><b>Idempotency note.</b> Because the writer is idempotent, an op that "would
 * produce a duplicate" (e.g. adding a field that already exists) is a no-op
 * success, not a {@code VALIDATION_ERROR} — consistent with ADR-037.
 *
 * <p><b>Batch apply is deferred.</b> Only single-op apply is offered. A
 * conflict-aware <em>batch</em> must thread the accumulating tooling edits
 * ("ours") through detection so op <i>n</i> does not see ops <i>1..n-1</i> as
 * user drift — non-trivial semantics that warrant their own treatment. The
 * caller applies ops one at a time against the evolving source.
 *
 * @since 0.5.0
 */
public final class SourceModelMutationApplier {

    private final SourceModelConflictDetector detector = new SourceModelConflictDetector();
    private final SourceModelWriter writer = new SourceModelWriter();

    /**
     * Creates an applier over a fresh {@link SourceModelConflictDetector} and
     * {@link SourceModelWriter}. Holds no caller-visible state beyond those two, so
     * one instance may be shared or created per call.
     *
     * <p>Declared rather than left implicit: 1.0.0 freezes the public surface, and
     * an undeclared constructor is still part of it. Stating it makes instantiation
     * a documented affordance instead of a language default nobody chose.
     */
    public SourceModelMutationApplier() {
        // Intentionally empty: both collaborators are field initialisers above, so
        // there is nothing left to do here. Declared rather than left implicit
        // because 1.0.0 freezes the public surface and an undeclared constructor is
        // still part of it.
    }

    /**
     * Apply {@code op} to {@code currentSource} if it does not conflict with a
     * user edit relative to {@code baselineJson}. No concurrency check.
     *
     * @param op            the mutation to apply
     * @param baselineJson  the last-codegen baseline JSON, or {@code null}
     * @param currentSource the live {@code @ExerisDomain} source
     * @return the verdict and the resulting source (mutated only on success)
     */
    public ApplyResult apply(MutationOp op, String baselineJson, String currentSource) {
        return apply(op, baselineJson, currentSource, null);
    }

    /**
     * Apply {@code op}, first rejecting the write if {@code concurrencyToken} (the
     * {@link SourceDigest} the batch was computed against) no longer matches the
     * live source — the optimistic-concurrency guard.
     *
     * @param op               the mutation to apply
     * @param baselineJson     the last-codegen baseline JSON, or {@code null}
     * @param currentSource    the live {@code @ExerisDomain} source
     * @param concurrencyToken the digest the op was computed against, or
     *                         {@code null} to skip the concurrency check
     * @return the verdict and the resulting source (mutated only on success)
     */
    public ApplyResult apply(MutationOp op, String baselineJson, String currentSource, String concurrencyToken) {
        Objects.requireNonNull(op, "op is required");
        Objects.requireNonNull(currentSource, "currentSource is required");

        if (concurrencyToken != null && !concurrencyToken.equals(SourceDigest.of(currentSource))) {
            return new ApplyResult(new MutationResult.NoBaseline(
                    MutationResult.NoBaselineCause.STALE_DIGEST,
                    "current source changed since the op was computed (concurrency token mismatch)"),
                    currentSource);
        }

        if (op instanceof MutationOp.ChangeRelationshipCardinality) {
            return new ApplyResult(new MutationResult.ValidationError(op.path(),
                    "changeRelationshipCardinality is not applicable: cardinality change is structural and has "
                            + "no writer mutation behind it yet"),
                    currentSource);
        }

        MutationResult detected;
        String mutated;
        try {
            detected = detector.detect(op, baselineJson, currentSource);
            if (!detected.successful()) {
                return new ApplyResult(detected, currentSource);
            }
            mutated = write(op, currentSource);
        } catch (RuntimeException cannotProcess) {
            // Safety net for the "ApplyResult never throws" contract. The expected
            // throw is IllegalArgumentException — an unparseable current source
            // (reader) or a writer rejection — but any unchecked exception from the
            // detect/read/write path is surfaced as VALIDATION_ERROR rather than
            // escaping the result. (MutationPath parse errors are already handled
            // inside the detector; the unreachable write() guard cannot fire here.)
            return new ApplyResult(new MutationResult.ValidationError(op.path(),
                    "could not apply op: " + cannotProcess.getMessage()), currentSource);
        }
        return new ApplyResult(detected, mutated);
    }

    /** Dispatch a (detection-cleared, supported) op to the idempotent writer. */
    private String write(MutationOp op, String source) {
        String member = MutationPath.parse(op.path()).member();
        return switch (op) {
            case MutationOp.AddField a -> writer.addField(source, a.field().type(), member);
            case MutationOp.RemoveField ignored -> writer.removeField(source, member);
            case MutationOp.RenameField r -> writer.renameField(source, member, r.newName());
            case MutationOp.ChangeFieldType c -> writer.changeFieldType(source, member, c.newType());
            case MutationOp.AddRelationship a -> writer.addRelationship(
                    source, member, a.relationship().targetEntity(), a.relationship().type().name());
            case MutationOp.RemoveRelationship ignored -> writer.removeRelationship(source, member);
            case MutationOp.AddAction ignored -> writer.addAction(source, member);
            case MutationOp.RemoveAction ignored -> writer.removeAction(source, member);
            // guarded before detection — cardinality has no writer mutation
            case MutationOp.ChangeRelationshipCardinality ignored ->
                    throw new IllegalStateException("unreachable: changeRelationshipCardinality is rejected earlier");
        };
    }
}
