package eu.exeris.sdk.sourcemodel.mutation;

import java.util.Objects;

/**
 * The path grammar shared by {@link MutationOp} targeting and conflict
 * reporting (ADR-042, obligation 2). A path addresses an entity or one of its
 * members:
 *
 * <pre>
 *   /entities/&lt;Entity&gt;                          (entity root)
 *   /entities/&lt;Entity&gt;/fields/&lt;name&gt;
 *   /entities/&lt;Entity&gt;/relationships/&lt;fieldName&gt;
 *   /entities/&lt;Entity&gt;/actions/&lt;name&gt;
 * </pre>
 *
 * <p>Ops and {@link MutationResult}s carry the path as a plain {@code String}
 * on the wire (it serializes as a string, not a nested object); this type is
 * the construction / parsing / comparison helper that gives that string a
 * grammar. The structure is recovered on demand via {@link #parse(String)}.
 *
 * <p>The capability mutation root ({@code /capabilities/<Module>/…}) is
 * reserved by ADR-042 but out of scope for the 0.5.0 first cut, so it is not
 * modelled here.
 *
 * @author Exeris SDK Team
 * @since 0.5.0
  *
 * @param entity the entity the path starts at
 * @param kind what kind of member the path targets
 * @param member the member's name; {@code null} when the path targets the entity itself
 */
public record MutationPath(String entity, TargetKind kind, String member) {

    /** What an addressable path points at. */
    public enum TargetKind {
        /** The entity itself, with no member segment. */
        ENTITY(null),
        /** A field on the entity. */
        FIELD("fields"),
        /** An association on the entity. */
        RELATIONSHIP("relationships"),
        /** An action on the entity. */
        ACTION("actions");

        private final String segment;

        TargetKind(String segment) {
            this.segment = segment;
        }

        /**
         * The collection segment in the path string, or {@code null} for the entity root.
         *
         * @return the path segment this kind contributes, or {@code null} for the entity itself
         */
        public String segment() {
            return segment;
        }

        static TargetKind fromSegment(String segment) {
            for (TargetKind k : values()) {
                // ENTITY has no collection segment (null) — it is detected by
                // segment count in parse(), never resolved here.
                if (k.segment != null && k.segment.equals(segment)) {
                    return k;
                }
            }
            throw new IllegalArgumentException("unknown path collection segment: '" + segment + "'");
        }
    }

    /**
     * Compact constructor; applies this record's normalization rules.
     */
    public MutationPath {
        Objects.requireNonNull(entity, MutationMessages.ENTITY_REQUIRED);
        Objects.requireNonNull(kind, MutationMessages.KIND_REQUIRED);
        if (entity.isBlank()) {
            throw new IllegalArgumentException("entity must not be blank");
        }
        // '/' is the path separator; a name carrying it would not survive a
        // toString → parse round-trip, so reject it at construction with a
        // legible message rather than letting parse() fail later (matters once
        // names come from user-authored Studio / LSP payloads, not just
        // annotation-derived Java identifiers).
        if (entity.indexOf('/') >= 0) {
            throw new IllegalArgumentException("entity must not contain '/': '" + entity + "'");
        }
        if (kind == TargetKind.ENTITY) {
            if (member != null) {
                throw new IllegalArgumentException("entity-root path carries no member");
            }
        } else {
            if (member == null || member.isBlank()) {
                throw new IllegalArgumentException(kind + " path requires a non-blank member");
            }
            if (member.indexOf('/') >= 0) {
                throw new IllegalArgumentException("member must not contain '/': '" + member + "'");
            }
        }
    }

    /**
     * Creates a {@code MutationPath}.
     *
     * @param entity the {@code entity} the result carries
     * @return the {@code MutationPath}
     */
    public static MutationPath entity(String entity) {
        return new MutationPath(entity, TargetKind.ENTITY, null);
    }

    /**
     * Creates a {@code MutationPath}.
     *
     * @param entity the {@code entity} the result carries
     * @param name the {@code name} the result carries
     * @return the {@code MutationPath}
     */
    public static MutationPath field(String entity, String name) {
        return new MutationPath(entity, TargetKind.FIELD, name);
    }

    /**
     * Creates a {@code MutationPath}.
     *
     * @param entity the {@code entity} the result carries
     * @param fieldName the {@code fieldName} the result carries
     * @return the {@code MutationPath}
     */
    public static MutationPath relationship(String entity, String fieldName) {
        return new MutationPath(entity, TargetKind.RELATIONSHIP, fieldName);
    }

    /**
     * Creates a {@code MutationPath}.
     *
     * @param entity the {@code entity} the result carries
     * @param name the {@code name} the result carries
     * @return the {@code MutationPath}
     */
    public static MutationPath action(String entity, String name) {
        return new MutationPath(entity, TargetKind.ACTION, name);
    }

    /**
     * Parse a path string against the grammar.
     *
     * @throws IllegalArgumentException if the string is not a structurally valid path
          * @param path the path in its string form
     * @return the parsed path
     */
    public static MutationPath parse(String path) {
        Objects.requireNonNull(path, MutationMessages.PATH_REQUIRED);
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("path must start with '/': '" + path + "'");
        }
        // Leading '/' yields an empty first segment; reject trailing '/' and blanks.
        String[] segments = path.split("/", -1);
        // segments[0] is always "" because of the leading slash.
        if (segments.length < 3 || !"entities".equals(segments[1])) {
            throw new IllegalArgumentException("path must begin with '/entities/<Entity>': '" + path + "'");
        }
        String entity = segments[2];
        if (entity.isBlank()) {
            throw new IllegalArgumentException("path entity segment is blank: '" + path + "'");
        }
        if (segments.length == 3) {
            return entity(entity);
        }
        if (segments.length != 5) {
            throw new IllegalArgumentException(
                    "member path must be '/entities/<Entity>/<collection>/<member>': '" + path + "'");
        }
        TargetKind kind = TargetKind.fromSegment(segments[3]);
        String member = segments[4];
        if (member.isBlank()) {
            throw new IllegalArgumentException("path member segment is blank: '" + path + "'");
        }
        return new MutationPath(entity, kind, member);
    }

    /** The canonical path string for this address. */
    @Override
    public String toString() {
        if (kind == TargetKind.ENTITY) {
            return "/entities/" + entity;
        }
        return "/entities/" + entity + "/" + kind.segment() + "/" + member;
    }

    /**
     * Whether this path is the same as, or an ancestor of, {@code other}.
     * The entity root is the ancestor of every member of that same entity;
     * a member path is the ancestor only of itself. Members of different
     * entities, and sibling members, are never in an ancestor relationship.
     *
     * <p>Pure path arithmetic — the ADR-042 "ancestor-or-descendant" conflict
     * rule (obligation 4) is {@code a.isSameOrAncestorOf(b) || b.isSameOrAncestorOf(a)},
     * assembled by the detection slice.
          *
     * @param other the path to test
     * @return whether this path targets {@code other} or something containing it
     */
    public boolean isSameOrAncestorOf(MutationPath other) {
        Objects.requireNonNull(other, MutationMessages.OTHER_REQUIRED);
        if (!entity.equals(other.entity)) {
            return false;
        }
        if (kind == TargetKind.ENTITY) {
            return true;
        }
        return this.equals(other);
    }
}
