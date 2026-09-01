package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

/**
 * Metadata for a service a capability depends on, read from a {@code @Requires}
 * declaration on a {@code @CapabilityModule} class.
 *
 * <p>{@code service} is the required service interface's name as written in source
 * (FQN from the processor path, source-written form from the {@code -io} reader;
 * tooling normalizes — see {@link ProvidesMetadata}). {@code versionRange} is a
 * Maven-style range, or {@code null} for any version. {@code optional} marks a
 * dependency the cap degrades gracefully without.
 *
 * <p>Uses {@code @JsonInclude(NON_DEFAULT)} so the primitive {@code optional}
 * drops from the wire when {@code false}. That drop is harmless on its own — an
 * absent property reads back as {@code false} on any mapper. The AST-wide
 * {@code FAIL_ON_NULL_FOR_PRIMITIVES=false} contract is for the other case: an
 * explicit {@code null} in a baseline this SDK did not write.
 *
 * @param service the service name required — a source-written string, not a type reference
 * @param versionRange the acceptable versions of that service
 * @param optional whether the capability still boots when the service is absent
 * @author Exeris SDK Team
 * @since 0.4.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public record RequiresMetadata(
        String service,
        String versionRange,
        boolean optional
) {
    /**
     * Compact constructor; applies this record's normalization rules.
     */
    public RequiresMetadata {
        Objects.requireNonNull(service, "service is required");
    }

    /**
     * A mandatory dependency with no version constraint.
     *
     * @param service the {@code service} the result carries
     * @return the {@code RequiresMetadata}
     */
    public static RequiresMetadata of(String service) {
        return new RequiresMetadata(service, null, false);
    }

    /**
     * A mandatory dependency pinned to a version range.
     *
     * @param service the {@code service} the result carries
     * @param versionRange the {@code versionRange} the result carries
     * @return the {@code RequiresMetadata}
     */
    public static RequiresMetadata of(String service, String versionRange) {
        return new RequiresMetadata(service, versionRange, false);
    }

    /**
     * An optional dependency with no version constraint.
     *
     * @param service the {@code service} the result carries
     * @return the {@code RequiresMetadata}
     */
    public static RequiresMetadata optional(String service) {
        return new RequiresMetadata(service, null, true);
    }

    /**
     * An optional dependency pinned to a version range.
     *
     * @param service the {@code service} the result carries
     * @param versionRange the {@code versionRange} the result carries
     * @return the {@code RequiresMetadata}
     */
    public static RequiresMetadata optional(String service, String versionRange) {
        return new RequiresMetadata(service, versionRange, true);
    }

    /**
     * Whether a non-blank {@code versionRange} is declared.
     *
     * @return {@code true} when {@link #versionRange()} is set and not blank
     */
    public boolean hasVersionRange() {
        return versionRange != null && !versionRange.isBlank();
    }
}
