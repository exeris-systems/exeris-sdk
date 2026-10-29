package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Metadata for one graph node property, declared by a {@code @GraphProperty} on an entity field.
 *
 * <p><strong>Name and field are separate.</strong> {@code name} is the property's name on the
 * graph node; {@code field} names the entity field whose value the property holds, the field a
 * graph-sync writer reads it from. A property that keeps its field's name leaves {@code field}
 * {@code null}: the compact constructor drops a {@code field} equal to {@code name}, so the two
 * spellings of that case are one value, and its JSON carries no {@code field} key. Read the declaring field through {@link #effectiveField()}. The rule is the one
 * {@link GraphEdgeMetadata} applies to its own {@code field}.
 *
 * <p><strong>{@code type} is the declared graph type, or {@code null}.</strong> It holds the
 * {@code @GraphProperty.graphType} constant name ({@code "STRING"}, {@code "DOUBLE"}, …) when the
 * author declared one, and is {@code null} for {@code AUTO}, the annotation default: the store
 * type then follows the declaring field's Java type, which its {@link FieldMetadata#type()}
 * already carries. No producer derives a graph type from a Java type, so no two producers can
 * derive different ones.
 *
 * <p><strong>Who fills it.</strong> The {@code -io} reader fills it from {@code @GraphProperty},
 * one property per annotated field, under a type-level {@code @Graph} only; the
 * {@code exeris-tooling} processor does not extract it yet, and no generator reads it. The
 * {@code @GraphProperty} attributes with no component here are read by nobody.
 *
 * @param name the property's name on the graph node
 * @param type the property's type in the graph store, as the {@code GraphType} constant name the
 *        source declares; absent means {@code AUTO}, the type of the declaring field
 * @param indexed whether the graph store indexes the property
 * @param field the entity field that holds the property's value, or {@code null} when it equals
 *        {@code name}
 * @since 0.1
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GraphPropertyMetadata(String name, String type, boolean indexed, String field) {

    /**
     * Normalises a blank {@code type} or {@code field} to {@code null}, and a {@code field} equal
     * to {@code name} to {@code null}.
     */
    public GraphPropertyMetadata {
        type = blankToNull(type);
        field = blankToNull(field);
        if (field != null && field.equals(name)) {
            field = null;
        }
    }

    /**
     * A delegating constructor for backward compatibility with code compiled against a previous
     * version of this record. Omits {@code field}, which defaults to {@code null}: the property
     * keeps its field's name.
     *
     * @param name the property's name, which is also its declaring field
     * @param type the {@code type} the result carries
     * @param indexed the {@code indexed} the result carries
     */
    public GraphPropertyMetadata(String name, String type, boolean indexed) {
        this(name, type, indexed, null);
    }

    /**
     * The entity field that holds the property's value: {@code field} when set, else {@code name}.
     *
     * @return the declaring field's name
     * @since 0.13
     */
    public String effectiveField() {
        return field != null ? field : name;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
