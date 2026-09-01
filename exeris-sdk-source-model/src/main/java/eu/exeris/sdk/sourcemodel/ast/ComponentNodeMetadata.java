package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * A node in the presentation composition tree — a typed {@link BlockType} block
 * with an optional {@link BindingMetadata} binding, opaque {@code props} JSON, a
 * recursive list of {@code children} of the same type, and an optional
 * {@code field} render facet. The recursion is what lets a region hold a nested
 * tree (a grid of cards, a nav of nav-items, …) rather than a flat list.
 *
 * <p>The {@code field} facet reuses {@link UIMetadata.UIFieldMetadata} — the same
 * record that carried per-field render detail under {@code @UI} (component type,
 * format, grid span, i18n keys, custom-component escape hatch). This is how the
 * one presentation model absorbs {@code @UI}'s field-level role: a leaf field
 * block carries its render detail here, rather than via a parallel surface. It is
 * {@code null} for non-field (container / layout) nodes.
 *
 * <p>Uses {@code @JsonInclude(NON_NULL)} (deliberate deviation from
 * {@code NON_DEFAULT}, per RFC-2026-06-25). Blank {@code props} normalizes to
 * {@code null}, a null {@code children} normalizes to an immutable empty list, and
 * a null {@code type} is tolerated on the wire with {@link #effectiveType()}
 * applying the {@link BlockType#CONTAINER} default. Part of the presentation IR
 * surface, structurally live: extracted by the {@code exeris-tooling} processor
 * and emitted by the codegen-ts Angular view generator (RFC-2026-06-28,
 * tooling). The {@code field} leaf facet below is the ADR-047 seed — the
 * processor passes {@code null} for it today, so field-level render detail
 * stays on {@code @UI} until the facet subsumption lands.
 *
 * @param type the node's block kind
 * @param customType the component to render, when {@link #type()} is the custom escape hatch
 * @param binding where the node's value comes from
 * @param props the node's configuration, carried as serialized properties
 * @param children the nodes nested inside this one
 * @param field the per-field presentation override, when the node renders a field
 * @since 0.8.0
 * @see BlockType
 * @see BindingMetadata
 * @see UIMetadata.UIFieldMetadata
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComponentNodeMetadata(
        BlockType type,
        String customType,
        BindingMetadata binding,
        String props,
        List<ComponentNodeMetadata> children,
        UIMetadata.UIFieldMetadata field
) {

    public ComponentNodeMetadata {
        if (customType != null && customType.isBlank()) {
            customType = null;
        }
        if (props != null && props.isBlank()) {
            props = null;
        }
        children = children == null ? List.of() : List.copyOf(children);
    }

    /** A leaf node of {@code type} with a binding and no children. */
    public static ComponentNodeMetadata leaf(BlockType type, BindingMetadata binding) {
        return new ComponentNodeMetadata(type, null, binding, null, List.of(), null);
    }

    /** A leaf field block of {@code type} with a binding and a render facet (the {@code @UI} successor). */
    public static ComponentNodeMetadata fieldLeaf(BlockType type, BindingMetadata binding, UIMetadata.UIFieldMetadata field) {
        return new ComponentNodeMetadata(type, null, binding, null, List.of(), field);
    }

    /** A container node of {@code type} holding {@code children} (no binding). */
    public static ComponentNodeMetadata container(BlockType type, List<ComponentNodeMetadata> children) {
        return new ComponentNodeMetadata(type, null, null, null, children, null);
    }

    /** The effective block type: the declared one, or {@link BlockType#CONTAINER}. */
    @JsonIgnore
    public BlockType effectiveType() {
        return type != null ? type : BlockType.CONTAINER;
    }

    /** Whether this node has child nodes. */
    @JsonIgnore
    public boolean hasChildren() {
        return !children.isEmpty();
    }

    /** Whether this node carries a leaf field-render facet (the {@code @UI} successor). */
    @JsonIgnore
    public boolean hasField() {
        return field != null;
    }
}
