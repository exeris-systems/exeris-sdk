package eu.exeris.sdk.tck;

import java.util.Set;

/**
 * A slice of the metadata hand-off a binder may or may not have implemented yet.
 *
 * <p>The kit runs against real implementations mid-build, and a producer that extracts fields but
 * not yet actions is not non-conforming — it is unfinished. Declaring the facet lets its cases skip
 * with a reason instead of failing with a misleading one.
 *
 * <p>{@link #MANDATORY} is the guard against the obvious abuse: a binder that declared everything
 * unsupported would pass the suite with every case skipped, which is worse than failing because it
 * reads as conformance. Identity and fields are the hand-off — a producer emitting neither has
 * produced nothing to be compatible with.
 */
public enum Facet {

    /** Entity name and package — the identity the whole baseline is addressed by. Mandatory. */
    IDENTITY,

    /** Field names, types and {@code required} — the shape of the entity. Mandatory. */
    FIELDS,

    /** {@code @Validation} constraint values carried on {@code FieldMetadata} (ADR-054). */
    VALIDATION_BOUNDS,

    /** {@code @Relationship} edges, including the declared cardinality. */
    RELATIONSHIPS,

    /** {@code @Action} methods and their HTTP framing. */
    ACTIONS,

    /** The ADR-042 {@code sourceDigest} / {@code schemaVersion} baseline-trust siblings. */
    BASELINE_TRUST,

    /**
     * {@code @Saga} on an entity, carried as {@code DomainMetadata.sagaMetadata} — including the
     * {@code version} that kernel ADR-064 makes half of a saga plan's identity.
     *
     * @since 0.12
     */
    SAGA,

    /**
     * The compensation section of {@code @Saga} — {@code compensationTimeout} and the six
     * components that are {@code null} when undeclared — including a declared {@code 0} and
     * {@code false}, and the failure handler as a fully-qualified name.
     *
     * @since 0.13
     */
    SAGA_COMPENSATION,

    /**
     * {@code ActionMetadata.resultType} — each {@code @Action} method's return type, fully
     * qualified with its type arguments, and {@code "void"} for an action that returns nothing.
     * The streaming generators type an action's result frame from it, and emit none for
     * {@code "void"}.
     *
     * @since 0.13
     */
    ACTION_RESULT_TYPE,

    /**
     * {@code @RouteAccess} and {@code permissions}, on the entity and on each action, carried as
     * {@code routeAccess} and {@code permissions} on {@code DomainMetadata} and
     * {@code ActionMetadata} (exeris-tooling ADR-105) — {@code PUBLIC} included, each element
     * carrying only what it declares.
     *
     * @since 0.13
     */
    ROUTE_ACCESS,

    /**
     * {@code @QueryParam} on an {@code @Action} parameter, carried on {@code ActionParamMetadata}
     * as {@code bindingSource = QUERY} and, where it differs from the parameter name, the
     * {@code queryKey} — including a parameter carrying {@code @QueryParam} alone and a declared
     * {@code required = false}.
     *
     * @since 0.13
     */
    QUERY_PARAMS,

    /**
     * {@code @NavMenu} on an entity, carried as {@code DomainMetadata.navMenu} — including a
     * declared {@code order = 0}, which a producer that writes only non-default values drops.
     *
     * @since 0.13
     */
    NAV_MENU,

    /**
     * {@code @Tab} on an entity's fields, carried as {@code UIMetadata.tabs}: one tab per name, in
     * the order each is first declared, with its member fields and the attributes the first
     * declaring member gives it — on an entity that declares no {@code @UI}.
     *
     * @since 0.13
     */
    TABS,

    /**
     * {@code @GraphProperty} on the fields of a {@code @Graph} entity, carried as
     * {@code GraphMetadata.properties}: one property per annotated field, in declaration order,
     * with {@code type} absent for {@code AUTO} and {@code field} naming the declaring field of a
     * renamed property.
     *
     * @since 0.13
     */
    GRAPH_PROPERTIES,

    /**
     * {@code @UIGroup} on an entity's fields, carried as {@code UIMetadata.groups}: one group per
     * name, in the order each is first declared, with its member fields, the attributes the first
     * declaring member gives it, and the annotation defaults written out for the rest — including
     * a declared {@code collapsible = false} — on an entity that declares no {@code @UI}.
     *
     * @since 0.13
     */
    UI_GROUPS,

    /**
     * {@code @Blob} on an entity's fields, carried as {@code FieldMetadata.blob} as written —
     * including a {@code @Blob} on a field that carries no {@code @Field}, and the media types in
     * declaration order.
     *
     * @since 0.13
     */
    BLOBS;

    /** The facets no binder may declare unsupported. */
    public static final Set<Facet> MANDATORY = Set.of(IDENTITY, FIELDS);
}
