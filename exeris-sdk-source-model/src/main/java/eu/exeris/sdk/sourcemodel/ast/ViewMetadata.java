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
 * presentation IR surface, <strong>structurally live</strong> since the tooling
 * caught up: the {@code exeris-tooling} processor extracts {@code @View} /
 * {@code @Region} / {@code @Block} / {@code @Bind} into these records and the
 * codegen-ts Angular view generator emits the component tree (RFC-2026-06-28,
 * tooling). The remaining piece is the ADR-047 leaf-field facet — the
 * {@code ComponentNodeMetadata.field} seed exists but the processor does not
 * populate it yet, so field-level render detail stays on {@code @UI}.
 *
 * @param name the view's identity
 * @param kind whether the view is route-bearing or a fragment
 * @param route the path the view is reachable at; absent for a fragment
 * @param title the heading the view shows
 * @param titleKey the message-bundle key for {@link #title()}; the literal is the fallback
 * @param layout the layout the view's regions are arranged in
 * @param regions the view's regions, each holding components
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

    /**
     * Compact constructor; applies this record's normalization rules.
     */
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

    /**
     * A minimal presentation artifact — just a name and kind, no regions.
     *
     * @param name the {@code name} the result carries
     * @param kind the {@code kind} the result carries
     * @return the {@code ViewMetadata}
     */
    public static ViewMetadata of(String name, ViewKind kind) {
        return new ViewMetadata(name, kind, null, null, null, null, List.of());
    }

    /**
     * Starts a builder for a {@code ViewMetadata}.
     *
     * @param name the {@code name} the result carries
     * @return a new builder
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * The effective kind: the declared one, or {@link ViewKind#PAGE}.
     *
     * @return the {@code ViewKind}
     */
    @JsonIgnore
    public ViewKind effectiveKind() {
        return kind != null ? kind : ViewKind.PAGE;
    }

    /**
     * Whether this view carries any regions.
     *
     * @return the {@code boolean}
     */
    @JsonIgnore
    public boolean hasRegions() {
        return !regions.isEmpty();
    }

    /**
     * A mutable builder for {@code ViewMetadata}.
     *
     * <p>Each setter sets the record component of the same name. Those components are
     * documented by the record's own {@code @param} tags and are deliberately not restated
     * here — a per-setter repetition of the component's meaning is filler, and filler is what
     * makes generated javadoc worth less than none.
     */
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

        /**
         * Builds the {@code ViewMetadata} from this builder's current state.
         *
         * @return the built {@code ViewMetadata}
         */
        public ViewMetadata build() {
            return new ViewMetadata(name, kind, route, title, titleKey, layout, regions);
        }
    }
}
