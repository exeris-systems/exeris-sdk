package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Objects;

/**
 * The root of the framework-neutral presentation IR — a page / section /
 * component / fragment ({@link ViewKind}) composed of {@link RegionMetadata}
 * regions, each holding a tree of {@link ComponentNodeMetadata} nodes whose
 * leaves carry {@link BindingMetadata} bindings. It is the <strong>front facet</strong>
 * of a unit, orthogonal to the backend (entity / action / capability) facet and
 * referencing the data source of truth only by name; it never generates
 * persistence.
 *
 * <p>This is the <strong>shared</strong> IR authored through two front-doors: the
 * backend-anchored Java annotations ({@code @View} / {@code @Region} /
 * {@code @Block} / {@code @Bind}) here in the SDK, and a frontend-only path
 * (Studio / hand-authored presentation-IR JSON) owned downstream. Both converge on
 * this record; the AST itself is front-door-agnostic.
 *
 * <p>Uses {@code @JsonInclude(NON_NULL)} (deliberate deviation from
 * {@code NON_DEFAULT}, per RFC-2026-06-25, to avoid the boxed-zero trap on future
 * numeric fields). {@code name} is required and non-blank; blank optional strings
 * normalize to {@code null}, a null {@code regions} normalizes to an immutable
 * empty list, and a null {@code kind} is tolerated on the wire with
 * {@link #effectiveKind()} applying the {@link ViewKind#PAGE} default. Part of the
 * reserved presentation IR surface — no Open-Core processor / codegen honours it
 * yet (generation is {@code exeris-tooling} work, pending RFC-2026-06-25's build
 * gate).
 *
 * @since 0.8.0
 * @see ViewKind
 * @see RegionMetadata
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ViewMetadata(
        String name,
        ViewKind kind,
        String route,
        String title,
        String titleKey,
        String layout,
        List<RegionMetadata> regions
) {

    public ViewMetadata {
        Objects.requireNonNull(name, "name is required");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (route != null && route.isBlank()) {
            route = null;
        }
        if (title != null && title.isBlank()) {
            title = null;
        }
        if (titleKey != null && titleKey.isBlank()) {
            titleKey = null;
        }
        if (layout != null && layout.isBlank()) {
            layout = null;
        }
        regions = regions == null ? List.of() : List.copyOf(regions);
    }

    /** A minimal presentation artifact — just a name and kind, no regions. */
    public static ViewMetadata of(String name, ViewKind kind) {
        return new ViewMetadata(name, kind, null, null, null, null, List.of());
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    /** The effective kind: the declared one, or {@link ViewKind#PAGE}. */
    @JsonIgnore
    public ViewKind effectiveKind() {
        return kind != null ? kind : ViewKind.PAGE;
    }

    /** Whether this view carries any regions. */
    @JsonIgnore
    public boolean hasRegions() {
        return !regions.isEmpty();
    }

    public static final class Builder {
        private final String name;
        private ViewKind kind;
        private String route;
        private String title;
        private String titleKey;
        private String layout;
        private List<RegionMetadata> regions = List.of();

        private Builder(String name) {
            this.name = name;
        }

        public Builder kind(ViewKind v) { this.kind = v; return this; }
        public Builder route(String v) { this.route = v; return this; }
        public Builder title(String v) { this.title = v; return this; }
        public Builder titleKey(String v) { this.titleKey = v; return this; }
        public Builder layout(String v) { this.layout = v; return this; }
        public Builder regions(List<RegionMetadata> v) { this.regions = v; return this; }

        public ViewMetadata build() {
            return new ViewMetadata(name, kind, route, title, titleKey, layout, regions);
        }
    }
}
