package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

/**
 * Metadata for a service a capability exposes, read from a {@code @Provides}
 * declaration on a {@code @CapabilityModule} class.
 *
 * <p>{@code service} is the service interface's name as written in source — the
 * processor (JSR-269) yields the fully-qualified name, the {@code -io} reader the
 * source-written form; canonical FQN normalization is the build-time tooling's
 * job. {@code version} is the semantic version of the provided contract, or
 * {@code null} when unversioned (an empty {@code @Provides(version = "")} maps to
 * {@code null}).
 *
 * @param service the service name offered — a source-written string, not a type reference
 * @param version the version of that service this capability offers
 * @since 0.4
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProvidesMetadata(
        String service,
        String version
) {
    /**
     * Compact constructor; applies this record's normalization rules.
     */
    public ProvidesMetadata {
        Objects.requireNonNull(service, "service is required");
    }

    /**
     * A provided service with no declared version.
     *
     * @param service the {@code service} the result carries
     * @return the {@code ProvidesMetadata}
     */
    public static ProvidesMetadata of(String service) {
        return new ProvidesMetadata(service, null);
    }

    /**
     * A provided service with a version.
     *
     * @return the {@code ProvidesMetadata}
     */
    public static ProvidesMetadata of(String service, String version) {
        return new ProvidesMetadata(service, version);
    }

    /**
     * Whether a non-blank {@code version} is declared.
     *
     * @return {@code true} when {@link #version()} is set and not blank
     */
    public boolean hasVersion() {
        return version != null && !version.isBlank();
    }
}
