package eu.exeris.sdk.sourcemodel.mutation;

/**
 * Shared validation messages for the mutation package. Several records in this
 * package guard the same component (every {@link MutationOp} is path-addressed,
 * so {@code "path is required"} would otherwise be repeated a dozen times);
 * centralizing the strings keeps the messages consistent and greppable.
 *
 * <p>Package-private — these are an implementation detail, not API.
 */
final class MutationMessages {

    private MutationMessages() {
    }

    static final String PATH_REQUIRED = "path is required";
    static final String FIELD_REQUIRED = "field is required";
    static final String NEW_NAME_REQUIRED = "newName is required";
    static final String NEW_TYPE_REQUIRED = "newType is required";
    static final String RELATIONSHIP_REQUIRED = "relationship is required";
    static final String NEW_CARDINALITY_REQUIRED = "newCardinality is required";
    static final String ACTION_REQUIRED = "action is required";
    static final String MESSAGE_REQUIRED = "message is required";
    static final String CAUSE_REQUIRED = "cause is required";

    static final String ENTITY_REQUIRED = "entity is required";
    static final String KIND_REQUIRED = "kind is required";
    static final String OTHER_REQUIRED = "other is required";
}
