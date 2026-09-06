package eu.exeris.sdk.sourcemodel.io;

import eu.exeris.sdk.sourcemodel.mutation.MutationResult;

/**
 * The result of a conflict-aware {@link SourceModelMutationApplier#apply} (ADR-042
 * slice 4): the {@link MutationResult} verdict plus the resulting source.
 *
 * <ul>
 *   <li>On {@link MutationResult.Success} {@link #source} is the mutated source
 *       (or, when the op was convergent, the unchanged source — the underlying
 *       writer is idempotent).</li>
 *   <li>On any non-success outcome ({@code CONFLICT}, {@code NO_BASELINE},
 *       {@code VALIDATION_ERROR}) the op was <strong>not applied</strong> and
 *       {@link #source} is the input source, returned verbatim.</li>
 * </ul>
 *
 * @param outcome what the mutation decided — applied, rejected, or refused for want of a trusted baseline
 * @param source the resulting Java source text; unchanged from the input when the mutation did not apply
 * @since 0.5
 */
public record ApplyResult(MutationResult outcome, String source) {

    /**
     * Whether the op applied (the outcome is a {@link MutationResult.Success}).
     * Note this is {@code true} for a convergent no-op too — the verdict, not a
     * byte-changed signal. A caller that needs "did the source change?" should
     * compare {@link #source} against the input.
     *
     * @return whether the mutation changed the source
     */
    public boolean applied() {
        return outcome.successful();
    }
}
