package eu.exeris.sdk.sourcemodel.mutation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Objects;

/**
 * The outcome of attempting to apply a {@link MutationOp} (ADR-042,
 * obligation 3). Four top-level outcomes, each a peer of the others:
 *
 * <ul>
 *   <li>{@link Success} — the op applied cleanly.</li>
 *   <li>{@link Conflict} — the target path (or an ancestor/descendant of it)
 *       drifted from the baseline to a value differing from <em>both</em> the
 *       baseline and the op's intent (obligation 4). Carries the path, the
 *       baseline value, the current drifted value, and the op's intended value.</li>
 *   <li>{@link ValidationError} — the op is malformed or inapplicable and is
 *       rejected structurally <em>before</em> any drift comparison (e.g. an
 *       unresolvable path, a duplicate-name op, a payload the AST grammar
 *       rejects). Distinct from a baseline collision.</li>
 *   <li>{@link NoBaseline} — there is no trustworthy last-codegen baseline to
 *       compare against (missing / stale digest / schema-version skew). Not a
 *       conflict variant; re-baselining is an explicit caller action.</li>
 * </ul>
 *
 * <p>The SDK <strong>reports</strong>; it does not decide how a conflict is
 * resolved — merge / prompt / auto-apply policy lives in LSP / Studio
 * (obligation 8).
 *
 * <h2>Wire format</h2>
 * <p>Polymorphic JSON with an {@code "outcome"} discriminator
 * ({@code SUCCESS} / {@code CONFLICT} / {@code VALIDATION_ERROR} /
 * {@code NO_BASELINE}). Serializes under the AST-wide Jackson 3 contract;
 * the round-trip guard is {@code MutationWireFormatTest}. The conflict-value
 * fields are the rendered values at the path — the detection slice produces them.
 *
 * @author Exeris SDK Team
 * @since 0.5.0
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "outcome")
@JsonSubTypes({
        @JsonSubTypes.Type(value = MutationResult.Success.class, name = "SUCCESS"),
        @JsonSubTypes.Type(value = MutationResult.Conflict.class, name = "CONFLICT"),
        @JsonSubTypes.Type(value = MutationResult.ValidationError.class, name = "VALIDATION_ERROR"),
        @JsonSubTypes.Type(value = MutationResult.NoBaseline.class, name = "NO_BASELINE")
})
public sealed interface MutationResult {

    /**
     * {@code true} only for {@link Success}. Convenience for non-pattern-matching callers.
     *
     * @return whether the mutation applied
     */
    boolean successful();

    /**
     * The op applied cleanly.
     *
     * @param path the mutation path naming what is being changed
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Success(String path) implements MutationResult {
        /**
         * Compact constructor; applies this record's normalization rules.
         */
        public Success {
            Objects.requireNonNull(path, MutationMessages.PATH_REQUIRED);
        }

        @Override
        public boolean successful() {
            return true;
        }
    }

    /**
     * The op collides with a user edit that drifted from the baseline.
     * Each value is the rendered form at {@code path}; {@code null} means
     * "absent at that side" (e.g. the field did not exist in the baseline).
          *
     * @param path the mutation path naming what is being changed
     * @param baselineValue the value the mutation was authored against
     * @param currentValue the value found now
     * @param intendedValue the value the mutation wanted to write
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Conflict(String path, String baselineValue, String currentValue, String intendedValue)
            implements MutationResult {
        /**
         * Compact constructor; applies this record's normalization rules.
         */
        public Conflict {
            Objects.requireNonNull(path, MutationMessages.PATH_REQUIRED);
        }

        @Override
        public boolean successful() {
            return false;
        }
    }

    /**
     * The op was rejected structurally before any drift comparison.
     *
     * @param path the mutation path naming what is being changed
     * @param message what was wrong with the operation
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ValidationError(String path, String message) implements MutationResult {
        /**
         * Compact constructor; applies this record's normalization rules.
         */
        public ValidationError {
            Objects.requireNonNull(message, MutationMessages.MESSAGE_REQUIRED);
        }

        @Override
        public boolean successful() {
            return false;
        }
    }

    /**
     * No trustworthy baseline to compare against.
     *
     * @param cause why no trusted baseline was available
     * @param detail the specifics, for a diagnostic
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record NoBaseline(NoBaselineCause cause, String detail) implements MutationResult {
        /**
         * Compact constructor; applies this record's normalization rules.
         */
        public NoBaseline {
            Objects.requireNonNull(cause, MutationMessages.CAUSE_REQUIRED);
        }

        @Override
        public boolean successful() {
            return false;
        }
    }

    /** Why the baseline is not trustworthy (ADR-042, obligation 5). */
    enum NoBaselineCause {
        /** No last-codegen {@code exeris-metadata/<entity>.json} for the entity. */
        MISSING_BASELINE,
        /** The current source digest does not match the baseline's {@code sourceDigest}. */
        STALE_DIGEST,
        /** The baseline's {@code schemaVersion} differs from the reader's schema version. */
        SCHEMA_VERSION_SKEW
    }
}
