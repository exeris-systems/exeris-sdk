package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * A named region / slot in a {@link ViewMetadata} composition — a
 * container into which {@link ComponentNodeMetadata} nodes are placed.
 *
 * <p>Uses {@code @JsonInclude(NON_NULL)} (deliberate deviation from
 * {@code NON_DEFAULT}, per RFC-2026-06-25). Blank {@code slot} normalizes to
 * {@code null} (the slot name is then derived from the source member by future
 * tooling) and a null {@code components} normalizes to an immutable empty list.
 * Part of the reserved presentation IR surface — no Open-Core emitter honours it
 * yet.
 *
 * @since 0.8.0
 * @see ComponentNodeMetadata
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RegionMetadata(
        String slot,
        List<ComponentNodeMetadata> components
) {

    public RegionMetadata {
        if (slot != null && slot.isBlank()) {
            slot = null;
        }
        components = components == null ? List.of() : List.copyOf(components);
    }

    /** Whether this region carries any component nodes. */
    @JsonIgnore
    public boolean hasComponents() {
        return components != null && !components.isEmpty();
    }
}
