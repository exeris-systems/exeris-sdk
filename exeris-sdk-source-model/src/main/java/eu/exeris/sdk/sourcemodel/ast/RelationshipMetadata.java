package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Metadata for entity relationships with UI configuration for autocomplete/select.
 *
 * @param name the relationship's identity
 * @param fieldName the field on this entity that holds the association
 * @param targetEntity the entity on the other end
 * @param type the relationship's cardinality
 * @param mappedBy the field on the target that owns the association, when this side does not
 * @param lazy whether the association is loaded on demand rather than eagerly
 * @param cascade which operations propagate to the target
 * @param fetch how the association is fetched
 * @param optional whether the association may be absent
 * @param orphanRemoval whether a target detached from this entity is deleted
 * @param displayField the target's field shown when the association is presented
 * @param valueField the target's field carried as the stored value
 * @param searchEndpoint the endpoint a generated lookup queries for targets
 * @param joinColumns the columns joining the two tables
 * @author Exeris SDK Team
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RelationshipMetadata(
        String name,
        String fieldName,
        String targetEntity,
        RelationType type,
        String mappedBy,
        boolean lazy,
        CascadeType cascade,
        FetchType fetch,
        boolean optional,
        boolean orphanRemoval,
        String displayField,
        String valueField,
        String searchEndpoint,
        List<String> joinColumns
) {

    /**
     * The cardinality of an association between two entities.
     */
    public enum RelationType {
        /** One row on each side. */
        ONE_TO_ONE,
        /** One owner, many targets. */
        ONE_TO_MANY,
        /** Many owners, one target. */
        MANY_TO_ONE,
        /** Many on both sides. */
        MANY_TO_MANY
    }

    /**
     * Which operations on this entity propagate to the association's target.
     */
    public enum CascadeType {
        /** Every operation cascades. */
        ALL,
        /** Saving the owner saves the target. */
        PERSIST,
        /** Merging the owner merges the target. */
        MERGE,
        /** Deleting the owner deletes the target. */
        REMOVE,
        /** Reloading the owner reloads the target. */
        REFRESH,
        /** Detaching the owner detaches the target. */
        DETACH,
        /** Nothing cascades. */
        NONE
    }

    /**
     * Whether an association is loaded with its owner or on first access.
     */
    public enum FetchType {
        /** The target is loaded on first access. */
        LAZY,
        /** The target is loaded with its owner. */
        EAGER
    }

    /**
     * Creates a {@code RelationshipMetadata}.
     *
     * @param name the {@code name} the result carries
     * @param target the {@code target} the result carries
     * @return the {@code RelationshipMetadata}
     */
    public static RelationshipMetadata manyToOne(String name, String target) {
        return new RelationshipMetadata(name, name, target, RelationType.MANY_TO_ONE, null, true,
                CascadeType.NONE, FetchType.LAZY, true, false, "name", "id", null, List.of());
    }

    /**
     * Creates a {@code RelationshipMetadata}.
     *
     * @param name the {@code name} the result carries
     * @param target the {@code target} the result carries
     * @param mappedBy the {@code mappedBy} the result carries
     * @return the {@code RelationshipMetadata}
     */
    public static RelationshipMetadata oneToMany(String name, String target, String mappedBy) {
        return new RelationshipMetadata(name, name, target, RelationType.ONE_TO_MANY, mappedBy, true,
                CascadeType.ALL, FetchType.LAZY, true, false, "name", "id", null, List.of());
    }

    /**
     * Starts a builder for a {@code Builder}.
     *
     * @param name the {@code name} the result carries
     * @param targetEntity the {@code targetEntity} the result carries
     * @return a new builder
     */
    public static Builder builder(String name, String targetEntity) {
        return new Builder(name, targetEntity);
    }

    /**
     * The field holding the association; falls back to {@link #name()} when unset.
     *
     * @return {@code fieldName} as this record reports it
     */
    public String fieldName() {
        return fieldName != null ? fieldName : name;
    }

    /**
     * The target's field shown when the association is presented.
     *
     * @return {@code displayField} as this record reports it
     */
    public String displayField() {
        return displayField != null ? displayField : "name";
    }

    /**
     * The target's field carried as the stored value.
     *
     * @return {@code valueField} as this record reports it
     */
    public String valueField() {
        return valueField != null ? valueField : "id";
    }

    /**
     * A mutable builder for {@code RelationshipMetadata}.
     *
     * <p>Each setter sets the record component of the same name. Those components are
     * documented by the record's own {@code @param} tags and are deliberately not restated
     * here — a per-setter repetition of the component's meaning is filler, and filler is what
     * makes generated javadoc worth less than none.
     */
    public static final class Builder {
        private final String name;
        private final String targetEntity;
        private String fieldName;
        private RelationType type = RelationType.MANY_TO_ONE;
        private String mappedBy;
        private boolean lazy = true;
        private CascadeType cascade = CascadeType.NONE;
        private FetchType fetch = FetchType.LAZY;
        private boolean optional = true;
        private boolean orphanRemoval = false;
        private String displayField = "name";
        private String valueField = "id";
        private String searchEndpoint;
        private List<String> joinColumns = List.of();

        private Builder(String name, String targetEntity) {
            this.name = name;
            this.targetEntity = targetEntity;
            this.fieldName = name;
        }

        public Builder fieldName(String v) { this.fieldName = v; return this; }
        public Builder type(RelationType v) { this.type = v; return this; }
        public Builder mappedBy(String v) { this.mappedBy = v; return this; }
        public Builder lazy(boolean v) { this.lazy = v; return this; }
        public Builder cascade(CascadeType v) { this.cascade = v; return this; }
        public Builder fetch(FetchType v) { this.fetch = v; return this; }
        public Builder optional(boolean v) { this.optional = v; return this; }
        public Builder orphanRemoval(boolean v) { this.orphanRemoval = v; return this; }
        public Builder displayField(String v) { this.displayField = v; return this; }
        public Builder valueField(String v) { this.valueField = v; return this; }
        public Builder searchEndpoint(String v) { this.searchEndpoint = v; return this; }
        public Builder joinColumns(List<String> v) { this.joinColumns = v; return this; }

        /**
         * Builds the {@code RelationshipMetadata} from this builder's current state.
         *
         * @return the built {@code RelationshipMetadata}
         */
        public RelationshipMetadata build() {
            return new RelationshipMetadata(name, fieldName, targetEntity, type, mappedBy, lazy,
                    cascade, fetch, optional, orphanRemoval, displayField, valueField, searchEndpoint, joinColumns);
        }
    }
}

