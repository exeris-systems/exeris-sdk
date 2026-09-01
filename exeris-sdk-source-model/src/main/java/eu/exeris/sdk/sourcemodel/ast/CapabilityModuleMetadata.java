package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Metadata for a capability module — the AST shape read from a
 * {@code @CapabilityModule} class. The capability analogue of
 * {@link DomainMetadata} for entities.
 *
 * <p>Holds the services the cap {@link ProvidesMetadata provides} and
 * {@link RequiresMetadata requires} (the {@code @Provides}/{@code @Requires}
 * containers are flattened into these lists) and the {@code lifecycleOwner} —
 * the fully-qualified name of the {@code @CapabilityLifecycle} class, or
 * {@code null} when the cap has none. Unlike a referenced service type, the
 * declaring lifecycle class is FQN-resolvable on both the processor and
 * {@code -io} paths, so it carries no normalization gap.
 *
 * <p>Out of scope here (build-time tooling, per ADR-024 + ADR-038):
 * {@code @Requires}→{@code @Provides} resolution, the dependency DAG,
 * version-range intersection, the cap-tier Wall checks, and the cap manifest.
 *
 * @param provides the services this capability offers to others
 * @param requires the services it needs from others
 * @param lifecycleOwner the type implementing the capability's lifecycle hooks
 * @author Exeris SDK Team
 * @since 0.4.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CapabilityModuleMetadata(
        List<ProvidesMetadata> provides,
        List<RequiresMetadata> requires,
        String lifecycleOwner
) {
    /**
     * An empty capability module — provides/requires nothing, no lifecycle owner.
     *
     * @return the {@code CapabilityModuleMetadata}
     */
    public static CapabilityModuleMetadata empty() {
        return new CapabilityModuleMetadata(List.of(), List.of(), null);
    }

    /**
     * Starts a builder for a {@code CapabilityModuleMetadata}.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Whether any {@code provides} is declared.
     *
     * @return {@code true} when {@link #provides()} is neither null nor empty
     */
    public boolean hasProvides() {
        return provides != null && !provides.isEmpty();
    }

    /**
     * Whether any {@code requires} is declared.
     *
     * @return {@code true} when {@link #requires()} is neither null nor empty
     */
    public boolean hasRequires() {
        return requires != null && !requires.isEmpty();
    }

    /**
     * Whether a non-blank {@code lifecycleOwner} is declared.
     *
     * @return {@code true} when {@link #lifecycleOwner()} is set and not blank
     */
    public boolean hasLifecycleOwner() {
        return lifecycleOwner != null && !lifecycleOwner.isBlank();
    }

    /**
     * A mutable builder for {@code CapabilityModuleMetadata}.
     *
     * <p>Each setter sets the record component of the same name. Those components are
     * documented by the record's own {@code @param} tags and are deliberately not restated
     * here — a per-setter repetition of the component's meaning is filler, and filler is what
     * makes generated javadoc worth less than none.
     */
    public static final class Builder {
        private List<ProvidesMetadata> provides = List.of();
        private List<RequiresMetadata> requires = List.of();
        private String lifecycleOwner;

        private Builder() { }

        public Builder provides(List<ProvidesMetadata> v) { this.provides = v; return this; }
        public Builder requires(List<RequiresMetadata> v) { this.requires = v; return this; }
        public Builder lifecycleOwner(String v) { this.lifecycleOwner = v; return this; }

        /**
         * Builds the {@code CapabilityModuleMetadata} from this builder's current state.
         *
         * @return the built {@code CapabilityModuleMetadata}
         */
        public CapabilityModuleMetadata build() {
            return new CapabilityModuleMetadata(provides, requires, lifecycleOwner);
        }
    }
}
