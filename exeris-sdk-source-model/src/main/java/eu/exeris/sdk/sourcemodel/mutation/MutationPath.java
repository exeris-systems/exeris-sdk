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
 */
public record MutationPath(String entity, TargetKind kind, String member) {

    /** What an addressable path points at. */
    public enum TargetKind {
        ENTITY(null),
        FIELD("fields"),
        RELATIONSHIP("relationships"),
        ACTION("actions");

        private final String segment;

        TargetKind(String segment) {
            this.segment = segment;
        }

        /** The collection segment in the path string, or {@code null} for the entity root. */
        public String segment() {
            return segment;
        }

        static TargetKind fromSegment(String segment) {
            for (TargetKind k : values()) {
                if (segment.equals(k.segment)) {
                    return k;
                }
            }
            throw new IllegalArgumentException("unknown path collection segment: '" + segment + "'");
        }
    }

    public MutationPath {
        Objects.requireNonNull(entity, "entity is required");
        Objects.requireNonNull(kind, "kind is required");
        if (entity.isBlank()) {
            throw new IllegalArgumentException("entity must not be blank");
        }
        if (kind == TargetKind.ENTITY) {
            if (member != null) {
                throw new IllegalArgumentException("entity-root path carries no member");
            }
        } else {
            if (member == null || member.isBlank()) {
                throw new IllegalArgumentException(kind + " path requires a non-blank member");
            }
        }
    }

    public static MutationPath entity(String entity) {
        return new MutationPath(entity, TargetKind.ENTITY, null);
    }

    public static MutationPath field(String entity, String name) {
        return new MutationPath(entity, TargetKind.FIELD, name);
    }

    public static MutationPath relationship(String entity, String fieldName) {
        return new MutationPath(entity, TargetKind.RELATIONSHIP, fieldName);
    }

    public static MutationPath action(String entity, String name) {
        return new MutationPath(entity, TargetKind.ACTION, name);
    }

    /**
     * Parse a path string against the grammar.
     *
     * @throws IllegalArgumentException if the string is not a structurally valid path
     */
    public static MutationPath parse(String path) {
        Objects.requireNonNull(path, "path is required");
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
     */
    public boolean isSameOrAncestorOf(MutationPath other) {
        Objects.requireNonNull(other, "other is required");
        if (!entity.equals(other.entity)) {
            return false;
        }
        if (kind == TargetKind.ENTITY) {
            return true;
        }
        return this.equals(other);
    }
}
