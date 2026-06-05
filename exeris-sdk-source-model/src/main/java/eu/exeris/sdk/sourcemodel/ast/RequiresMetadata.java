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
 * drops from the wire when {@code false}; consumers must set
 * {@code FAIL_ON_NULL_FOR_PRIMITIVES=false} (the AST-wide contract) so the absent
 * field reads back as {@code false}.
 *
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
    public RequiresMetadata {
        Objects.requireNonNull(service, "service is required");
    }

    /** A mandatory dependency with no version constraint. */
    public static RequiresMetadata of(String service) {
        return new RequiresMetadata(service, null, false);
    }

    /** A mandatory dependency pinned to a version range. */
    public static RequiresMetadata of(String service, String versionRange) {
        return new RequiresMetadata(service, versionRange, false);
    }

    /** An optional dependency with no version constraint. */
    public static RequiresMetadata optional(String service) {
        return new RequiresMetadata(service, null, true);
    }

    /** An optional dependency pinned to a version range. */
    public static RequiresMetadata optional(String service, String versionRange) {
        return new RequiresMetadata(service, versionRange, true);
    }

    public boolean hasVersionRange() {
        return versionRange != null && !versionRange.isBlank();
    }
}
