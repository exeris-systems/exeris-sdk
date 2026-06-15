package eu.exeris.sdk.sourcemodel.mutation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The two baseline-trust fields codegen emits at the top level of each
 * {@code exeris-metadata/<entity>.json}, alongside the serialized
 * {@link eu.exeris.sdk.sourcemodel.ast.DomainMetadata} (ADR-042, obligation 5).
 *
 * <p>They sit as sibling properties in the same JSON object as the domain
 * fields, not in a wrapper — so the file stays "the entity JSON, plus two
 * trust fields." Because every AST record reads with
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)}, a plain
 * {@code DomainMetadata} read of the file ignores these two; and a
 * {@code BaselineTrust} read of the <em>same</em> file ignores all the domain
 * fields and picks up just these two. Two reads of one JSON, each blind to the
 * other's fields — no change to {@code DomainMetadata}.
 *
 * <ul>
 *   <li>{@code sourceDigest} — {@link SourceDigest#of} of the source the
 *       baseline was derived from; the optimistic-concurrency token (checked at
 *       apply time, slice 4).</li>
 *   <li>{@code schemaVersion} — the {@link SchemaVersion} the JSON was written
 *       with; a mismatch with {@link SchemaVersion#CURRENT} makes the baseline
 *       untrustworthy ({@link MutationResult.NoBaselineCause#SCHEMA_VERSION_SKEW}).</li>
 * </ul>
 *
 * <p>Either field may be {@code null} when read from a pre-0.5.0 baseline that
 * predates the trust fields; a {@code null} {@code schemaVersion} is treated as
 * a skew (refuse), never as "compatible."
 *
 * @since 0.5.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BaselineTrust(String sourceDigest, String schemaVersion) {

    /** The trust fields a fresh baseline gets: the current schema version and the given digest. */
    public static BaselineTrust current(String sourceDigest) {
        return new BaselineTrust(sourceDigest, SchemaVersion.CURRENT);
    }
}
