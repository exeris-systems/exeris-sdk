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
    BASELINE_TRUST;

    /** The facets no binder may declare unsupported. */
    public static final Set<Facet> MANDATORY = Set.of(IDENTITY, FIELDS);
}
