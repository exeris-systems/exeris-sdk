package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Metadata for one graph edge (relationship), declared by a {@code @GraphEdge} on an entity field.
 *
 * <p><strong>Identity and field are separate.</strong> {@code name} identifies the edge and is
 * unique within its entity; {@code field} names the entity field that declares it, the field a
 * graph-sync writer reads the edge's other end from. A field that carries one edge uses its own
 * name as the identity, and then {@code field} is {@code null}: the compact constructor drops a
 * {@code field} equal to {@code name}, so the two spellings of that case are one value and the
 * wire form is the one written before the component existed. A field that carries more than one
 * edge needs a distinct {@code name} per edge, and every one of them carries the same
 * {@code field}. Read the declaring field through {@link #effectiveField()}.
 *
 * <p><strong>Direction follows the kernel graph model.</strong> {@code direction} and
 * {@code bidirectional} map onto {@code GraphEdgeDescriptor.direction} and
 * {@code .bidirectional}, and {@link #effectiveDirection()} applies the kernel's rule:
 * a bidirectional edge is traversed as {@code BOTH}, otherwise the declared direction applies,
 * and an undeclared direction is {@code OUTGOING}.
 *
 * <p><strong>Every component after {@code relationType} is {@code null} when the author declared
 * nothing</strong>, so the annotation default applies and a producer that does not extract the
 * component writes the JSON it wrote before. A declared {@code false} or {@code 0.0} is written,
 * since the record is {@code NON_NULL}. Blank strings normalise to {@code null}.
 *
 * <p><strong>Status: RESERVED, outside the 1.0.0 freeze</strong> for {@code direction},
 * {@code bidirectional}, {@code inverseType}, {@code weighted}, {@code weightField} and
 * {@code staticWeight}. They encode the kernel graph SPI, which is at tier {@code preview};
 * no generator reads them yet. They are promoted into the frozen surface when the kernel moves
 * {@code …spi.graph} to {@code stable}, which kernel ADR-102 leaves to a 1.x minor; the kernel
 * tier is the whole condition (ADR-072). The remaining {@code @GraphEdge} attributes have no component; each
 * says why on the annotation.
 *
 * @param name the edge's identity, unique within its entity
 * @param targetLabel the node label the edge points at
 * @param relationType the relationship the edge represents
 * @param field the entity field that declares the edge, or {@code null} when it equals
 *        {@code name}
 * @param direction the declared direction, or {@code null} for the default ({@code OUTGOING})
 * @param bidirectional whether the edge is traversed both ways, or {@code null} when undeclared
 * @param inverseType the relation type of the reverse edge, or {@code null} when undeclared
 * @param description the edge's free-text description, or {@code null}
 * @param weighted whether the edge carries a weight, or {@code null} when undeclared
 * @param weightField the entity field holding the edge's weight, or {@code null}
 * @param staticWeight the fixed weight of the edge, or {@code null} when undeclared
 * @since 0.1
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GraphEdgeMetadata(
        String name,
        String targetLabel,
        String relationType,
        String field,
        Direction direction,
        Boolean bidirectional,
        String inverseType,
        String description,
        Boolean weighted,
        String weightField,
        Double staticWeight
) {

    /**
     * Edge direction, relative to the declaring entity, with the meaning of the kernel's
     * {@code GraphEdgeDescriptor.Direction}.
     *
     * @since 0.13
     */
    public enum Direction {
        /** The edge runs from the declaring entity to the target. */
        OUTGOING,
        /** The edge runs from the target to the declaring entity. */
        INCOMING,
        /** The edge is traversed both ways. */
        BOTH
    }

    /**
     * Normalises blank strings to {@code null}, and a {@code field} equal to {@code name} to
     * {@code null}.
     */
    public GraphEdgeMetadata {
        field = blankToNull(field);
        if (field != null && field.equals(name)) {
            field = null;
        }
        inverseType = blankToNull(inverseType);
        description = blankToNull(description);
        weightField = blankToNull(weightField);
    }

    /**
     * Creates edge metadata with only the identity, target label and relation type, every later
     * component {@code null}. The identity is also the declaring field.
     *
     * @param name the edge's identity, which is also its declaring field
     * @param targetLabel the node label the edge points at
     * @param relationType the relationship the edge represents
     */
    public GraphEdgeMetadata(String name, String targetLabel, String relationType) {
        this(name, targetLabel, relationType, null, null, null, null, null, null, null, null);
    }

    /**
     * The entity field that declares the edge: {@code field} when set, else {@code name}.
     *
     * @return the declaring field's name
     * @since 0.13
     */
    public String effectiveField() {
        return field != null ? field : name;
    }

    /**
     * The direction a traversal follows, by the kernel's rule: {@code BOTH} when
     * {@code bidirectional} is {@code true}, else the declared {@code direction}, else
     * {@code OUTGOING}.
     *
     * @return the effective direction, never {@code null}
     * @since 0.13
     */
    public Direction effectiveDirection() {
        if (Boolean.TRUE.equals(bidirectional)) {
            return Direction.BOTH;
        }
        return direction != null ? direction : Direction.OUTGOING;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
