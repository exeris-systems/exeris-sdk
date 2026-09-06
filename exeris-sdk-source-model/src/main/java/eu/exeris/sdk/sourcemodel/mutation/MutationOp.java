package eu.exeris.sdk.sourcemodel.mutation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import eu.exeris.sdk.sourcemodel.ast.ActionMetadata;
import eu.exeris.sdk.sourcemodel.ast.FieldMetadata;
import eu.exeris.sdk.sourcemodel.ast.RelationshipMetadata;

import java.util.Objects;

/**
 * A single, serializable metadata mutation that LSP / Studio / IDE plugins ask
 * the {@code -io} layer to apply (ADR-042, obligation 1). The sealed family
 * covers the eight mutations the {@code -io} writer already applies idempotently
 * (ADR-037, PRs #23–#25) plus relationship-cardinality change.
 *
 * <p>Every op is <strong>path-addressed</strong> ({@link #path()}, grammar in
 * {@link MutationPath}) and carries only the payload its kind needs. Slice 4
 * dispatches over the sealed family with an exhaustive {@code switch}; this
 * module only defines and serializes the vocabulary (no JavaParser, no
 * application logic).
 *
 * <h2>Wire format</h2>
 * <p>Polymorphic JSON with an {@code "op"} discriminator (e.g.
 * {@code {"op":"addField","path":"/entities/Order/fields/total",...}}).
 * Serializes under the AST-wide Jackson 3 contract — consumers must configure
 * {@code FAIL_ON_NULL_FOR_PRIMITIVES=false} (see the {@code ast} package-info);
 * the round-trip guard is {@code MutationWireFormatTest}.
 *
 * @since 0.5
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "op")
@JsonSubTypes({
        @JsonSubTypes.Type(value = MutationOp.AddField.class, name = "addField"),
        @JsonSubTypes.Type(value = MutationOp.RemoveField.class, name = "removeField"),
        @JsonSubTypes.Type(value = MutationOp.RenameField.class, name = "renameField"),
        @JsonSubTypes.Type(value = MutationOp.ChangeFieldType.class, name = "changeFieldType"),
        @JsonSubTypes.Type(value = MutationOp.AddRelationship.class, name = "addRelationship"),
        @JsonSubTypes.Type(value = MutationOp.RemoveRelationship.class, name = "removeRelationship"),
        @JsonSubTypes.Type(value = MutationOp.ChangeRelationshipCardinality.class, name = "changeRelationshipCardinality"),
        @JsonSubTypes.Type(value = MutationOp.AddAction.class, name = "addAction"),
        @JsonSubTypes.Type(value = MutationOp.RemoveAction.class, name = "removeAction")
})
public sealed interface MutationOp {

    /**
     * The target path this op addresses (see {@link MutationPath}).
     *
     * @return the mutation path this operation targets
     */
    String path();

    // ---- field ops -------------------------------------------------------

    /**
     * Add a field; {@code path} is the new field's path, {@code field} the shape to write.
     *
     * @param path the mutation path naming what is being changed
     * @param field the field to add
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AddField(String path, FieldMetadata field) implements MutationOp {
        /**
         * Compact constructor; applies this record's normalization rules.
         */
        public AddField {
            Objects.requireNonNull(path, MutationMessages.PATH_REQUIRED);
            Objects.requireNonNull(field, MutationMessages.FIELD_REQUIRED);
        }
    }

    /**
     * Remove the field at {@code path}.
     *
     * @param path the mutation path naming what is being changed
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RemoveField(String path) implements MutationOp {
        /**
         * Compact constructor; applies this record's normalization rules.
         */
        public RemoveField {
            Objects.requireNonNull(path, MutationMessages.PATH_REQUIRED);
        }
    }

    /**
     * Rename the field at {@code path} to {@code newName}.
     *
     * @param path the mutation path naming what is being changed
     * @param newName the name to give it
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RenameField(String path, String newName) implements MutationOp {
        /**
         * Compact constructor; applies this record's normalization rules.
         */
        public RenameField {
            Objects.requireNonNull(path, MutationMessages.PATH_REQUIRED);
            Objects.requireNonNull(newName, MutationMessages.NEW_NAME_REQUIRED);
        }
    }

    /**
     * Change the Java type of the field at {@code path} to {@code newType}.
     *
     * @param path the mutation path naming what is being changed
     * @param newType the type to give it
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ChangeFieldType(String path, String newType) implements MutationOp {
        /**
         * Compact constructor; applies this record's normalization rules.
         */
        public ChangeFieldType {
            Objects.requireNonNull(path, MutationMessages.PATH_REQUIRED);
            Objects.requireNonNull(newType, MutationMessages.NEW_TYPE_REQUIRED);
        }
    }

    // ---- relationship ops ------------------------------------------------

    /**
     * Add a relationship; {@code path} is the new relationship's path.
     *
     * @param path the mutation path naming what is being changed
     * @param relationship the relationship to add
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AddRelationship(String path, RelationshipMetadata relationship) implements MutationOp {
        /**
         * Compact constructor; applies this record's normalization rules.
         */
        public AddRelationship {
            Objects.requireNonNull(path, MutationMessages.PATH_REQUIRED);
            Objects.requireNonNull(relationship, MutationMessages.RELATIONSHIP_REQUIRED);
        }
    }

    /**
     * Remove the relationship at {@code path}.
     *
     * @param path the mutation path naming what is being changed
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RemoveRelationship(String path) implements MutationOp {
        /**
         * Compact constructor; applies this record's normalization rules.
         */
        public RemoveRelationship {
            Objects.requireNonNull(path, MutationMessages.PATH_REQUIRED);
        }
    }

    /**
     * Change the cardinality of the relationship at {@code path}.
     *
     * @param path the mutation path naming what is being changed
     * @param newCardinality the cardinality to give it
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ChangeRelationshipCardinality(String path, RelationshipMetadata.RelationType newCardinality)
            implements MutationOp {
        /**
         * Compact constructor; applies this record's normalization rules.
         */
        public ChangeRelationshipCardinality {
            Objects.requireNonNull(path, MutationMessages.PATH_REQUIRED);
            Objects.requireNonNull(newCardinality, MutationMessages.NEW_CARDINALITY_REQUIRED);
        }
    }

    // ---- action ops ------------------------------------------------------

    /**
     * Add an action; {@code path} is the new action's path.
     *
     * @param path the mutation path naming what is being changed
     * @param action the action to add
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AddAction(String path, ActionMetadata action) implements MutationOp {
        /**
         * Compact constructor; applies this record's normalization rules.
         */
        public AddAction {
            Objects.requireNonNull(path, MutationMessages.PATH_REQUIRED);
            Objects.requireNonNull(action, MutationMessages.ACTION_REQUIRED);
        }
    }

    /**
     * Remove the action at {@code path}.
     *
     * @param path the mutation path naming what is being changed
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RemoveAction(String path) implements MutationOp {
        /**
         * Compact constructor; applies this record's normalization rules.
         */
        public RemoveAction {
            Objects.requireNonNull(path, MutationMessages.PATH_REQUIRED);
        }
    }
}
