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
 * @author Exeris SDK Team
 * @since 0.5.0
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "op")
@JsonSubTypes({
        @JsonSubTypes.Type(value = MutationOp.AddField.class, name = "addField"),
        @JsonSubTypes.Type(value = MutationOp.RemoveField.class, name = "removeField"),
        @JsonSubTypes.Type(value = MutationOp.RenameField.class, name = "renameField"),
        @JsonSubTypes.Type(value = MutationOp.ChangeFieldType.class, name = "changeFieldType"),
        @JsonSubTypes.Type(value = MutationOp.AddRelationship.class, name = "addRelationship"),
        @JsonSubTypes.Type(value = MutationOp.RemoveRelationship.class, name = "removeRelationship"),
        @JsonSubTypes.Type(value = MutationOp.AddAction.class, name = "addAction"),
        @JsonSubTypes.Type(value = MutationOp.RemoveAction.class, name = "removeAction"),
        @JsonSubTypes.Type(value = MutationOp.ChangeRelationshipCardinality.class, name = "changeRelationshipCardinality")
})
public sealed interface MutationOp {

    /** The target path this op addresses (see {@link MutationPath}). */
    String path();

    // ---- field ops -------------------------------------------------------

    /** Add a field; {@code path} is the new field's path, {@code field} the shape to write. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AddField(String path, FieldMetadata field) implements MutationOp {
        public AddField {
            Objects.requireNonNull(path, "path is required");
            Objects.requireNonNull(field, "field is required");
        }
    }

    /** Remove the field at {@code path}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RemoveField(String path) implements MutationOp {
        public RemoveField {
            Objects.requireNonNull(path, "path is required");
        }
    }

    /** Rename the field at {@code path} to {@code newName}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RenameField(String path, String newName) implements MutationOp {
        public RenameField {
            Objects.requireNonNull(path, "path is required");
            Objects.requireNonNull(newName, "newName is required");
        }
    }

    /** Change the Java type of the field at {@code path} to {@code newType}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ChangeFieldType(String path, String newType) implements MutationOp {
        public ChangeFieldType {
            Objects.requireNonNull(path, "path is required");
            Objects.requireNonNull(newType, "newType is required");
        }
    }

    // ---- relationship ops ------------------------------------------------

    /** Add a relationship; {@code path} is the new relationship's path. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AddRelationship(String path, RelationshipMetadata relationship) implements MutationOp {
        public AddRelationship {
            Objects.requireNonNull(path, "path is required");
            Objects.requireNonNull(relationship, "relationship is required");
        }
    }

    /** Remove the relationship at {@code path}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RemoveRelationship(String path) implements MutationOp {
        public RemoveRelationship {
            Objects.requireNonNull(path, "path is required");
        }
    }

    /** Change the cardinality of the relationship at {@code path}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ChangeRelationshipCardinality(String path, RelationshipMetadata.RelationType newCardinality)
            implements MutationOp {
        public ChangeRelationshipCardinality {
            Objects.requireNonNull(path, "path is required");
            Objects.requireNonNull(newCardinality, "newCardinality is required");
        }
    }

    // ---- action ops ------------------------------------------------------

    /** Add an action; {@code path} is the new action's path. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AddAction(String path, ActionMetadata action) implements MutationOp {
        public AddAction {
            Objects.requireNonNull(path, "path is required");
            Objects.requireNonNull(action, "action is required");
        }
    }

    /** Remove the action at {@code path}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RemoveAction(String path) implements MutationOp {
        public RemoveAction {
            Objects.requireNonNull(path, "path is required");
        }
    }
}
