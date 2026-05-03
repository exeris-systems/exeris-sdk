package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Metadata for entity relationships with UI configuration for autocomplete/select.
 *
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

    public enum RelationType {
        ONE_TO_ONE, ONE_TO_MANY, MANY_TO_ONE, MANY_TO_MANY
    }

    public enum CascadeType {
        ALL, PERSIST, MERGE, REMOVE, REFRESH, DETACH, NONE
    }

    public enum FetchType {
        LAZY, EAGER
    }

    public static RelationshipMetadata manyToOne(String name, String target) {
        return new RelationshipMetadata(name, name, target, RelationType.MANY_TO_ONE, null, true,
                CascadeType.NONE, FetchType.LAZY, true, false, "name", "id", null, List.of());
    }

    public static RelationshipMetadata oneToMany(String name, String target, String mappedBy) {
        return new RelationshipMetadata(name, name, target, RelationType.ONE_TO_MANY, mappedBy, true,
                CascadeType.ALL, FetchType.LAZY, true, false, "name", "id", null, List.of());
    }

    public static Builder builder(String name, String targetEntity) {
        return new Builder(name, targetEntity);
    }

    public String fieldName() {
        return fieldName != null ? fieldName : name;
    }

    public String displayField() {
        return displayField != null ? displayField : "name";
    }

    public String valueField() {
        return valueField != null ? valueField : "id";
    }

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

        public RelationshipMetadata build() {
            return new RelationshipMetadata(name, fieldName, targetEntity, type, mappedBy, lazy,
                    cascade, fetch, optional, orphanRemoval, displayField, valueField, searchEndpoint, joinColumns);
        }
    }
}

