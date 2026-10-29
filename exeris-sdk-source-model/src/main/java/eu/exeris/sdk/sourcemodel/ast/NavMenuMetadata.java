package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * AST facet for an entity's entry in the generated application's navigation — declared with
 * {@code @NavMenu} and carried by {@link DomainMetadata#navMenu()}.
 *
 * <p><strong>The record's presence is the declaration.</strong> Every component is optional, so
 * an entity that declares {@code @NavMenu} and nothing further is a record with every component
 * unset, which serialises to {@code {"roles":[],"permissions":[]}} rather than to nothing. Absent
 * means the author placed nothing, and a generator lays the entity out by its own default.
 *
 * <p><strong>Undeclared is {@code null}, not the annotation default.</strong> A string, number or
 * enum component is {@code null} when the author did not declare its attribute, and the
 * {@code effective*} accessors apply the annotation's default. A blank string is stored as
 * {@code null}, so {@code ""} and absent are one state on the wire. The four booleans are
 * primitive because every one of them defaults to {@code false} on the annotation, so
 * {@code false} and undeclared mean the same. The record is class-level {@code NON_NULL}, so a
 * declared {@code 0} order survives serialisation.
 *
 * <p>Two components default to another part of the entity rather than to a constant: an absent
 * {@code label} means the entity name, and an absent {@code routePath} means the entity's route,
 * {@link DomainMetadata#effectivePath()}. They have no {@code effective*} accessor here because
 * this record does not know the entity.
 *
 * @param group the top-level menu group the entry is listed under; absent means none
 * @param parent the menu entry this one is nested under, within its group; absent means the
 *        entry sits directly in the group
 * @param label the text of the entry; absent means the entity name
 * @param icon the icon shown beside the entry
 * @param order the entry's position among its siblings, lower first; absent means {@code 999},
 *        read through {@link #effectiveOrder()}
 * @param groupOrder the group's position in the top-level navigation, lower first; absent means
 *        {@code 999}, read through {@link #effectiveGroupOrder()}
 * @param groupIcon the icon shown beside the group heading
 * @param hidden whether the entry is left out of the navigation; the entity stays reachable by
 *        its route
 * @param roles the roles of which a viewer needs at least one to see the entry; empty means no
 *        role is required
 * @param permissions the permissions of which a viewer needs at least one to see the entry;
 *        empty means no permission is required
 * @param badge the text shown as a badge on the entry, as written in source — a literal or an
 *        expression a generator may evaluate
 * @param badgeColor the badge's colour name; absent means {@code "accent"}, read through
 *        {@link #effectiveBadgeColor()}
 * @param tooltip the text shown when the entry is hovered
 * @param openInNewTab whether following the entry opens a new browser tab
 * @param routePath the route the entry links to; absent means the entity's route
 * @param queryParams the query string appended to the route, as written: {@code key=value} pairs
 *        joined by {@code &}
 * @param expanded whether an entry with children starts expanded
 * @param divider where a divider line is drawn relative to the entry; absent means
 *        {@link Divider#NONE}, read through {@link #effectiveDivider()}
 * @since 0.13
 * @see DomainMetadata#navMenu()
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NavMenuMetadata(
        String group,
        String parent,
        String label,
        String icon,
        Integer order,
        Integer groupOrder,
        String groupIcon,
        boolean hidden,
        List<String> roles,
        List<String> permissions,
        String badge,
        String badgeColor,
        String tooltip,
        boolean openInNewTab,
        String routePath,
        String queryParams,
        boolean expanded,
        Divider divider
) {

    /** The position {@code order} and {@code groupOrder} take when undeclared. */
    public static final int DEFAULT_ORDER = 999;

    /** The badge colour {@code badgeColor} takes when undeclared. */
    public static final String DEFAULT_BADGE_COLOR = "accent";

    /**
     * Where a divider line is drawn relative to a navigation entry. AST-owned, mirroring
     * {@code @NavMenu.Divider} by constant name without depending on it.
     */
    public enum Divider {
        /** No divider. */
        NONE,
        /** A divider line before the entry. */
        BEFORE,
        /** A divider line after the entry. */
        AFTER
    }

    /**
     * Compact constructor; applies this record's normalization rules.
     *
     * <p>Every string component is stored as {@code null} when blank, and both lists are copied
     * immutable, absent becoming empty and a {@code null} element refused by name.
     */
    public NavMenuMetadata {
        group = blankToNull(group);
        parent = blankToNull(parent);
        label = blankToNull(label);
        icon = blankToNull(icon);
        groupIcon = blankToNull(groupIcon);
        roles = AstLists.copyOfNoNulls(roles, "roles");
        permissions = AstLists.copyOfNoNulls(permissions, "permissions");
        badge = blankToNull(badge);
        badgeColor = blankToNull(badgeColor);
        tooltip = blankToNull(tooltip);
        routePath = blankToNull(routePath);
        queryParams = blankToNull(queryParams);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /**
     * An entry that declares nothing beyond its own existence.
     *
     * @return the {@code NavMenuMetadata}
     */
    public static NavMenuMetadata declared() {
        return builder().build();
    }

    /**
     * Starts a builder for a {@code NavMenuMetadata}.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * The entry's position among its siblings: the declared {@code order}, or {@value #DEFAULT_ORDER}.
     *
     * @return the effective order
     */
    @JsonIgnore
    public int effectiveOrder() {
        return order != null ? order : DEFAULT_ORDER;
    }

    /**
     * The group's position in the top-level navigation: the declared {@code groupOrder}, or
     * {@value #DEFAULT_ORDER}.
     *
     * @return the effective group order
     */
    @JsonIgnore
    public int effectiveGroupOrder() {
        return groupOrder != null ? groupOrder : DEFAULT_ORDER;
    }

    /**
     * The badge colour: the declared {@code badgeColor}, or {@value #DEFAULT_BADGE_COLOR}.
     *
     * @return the effective badge colour
     */
    @JsonIgnore
    public String effectiveBadgeColor() {
        return badgeColor != null ? badgeColor : DEFAULT_BADGE_COLOR;
    }

    /**
     * The divider placement: the declared {@code divider}, or {@link Divider#NONE}.
     *
     * @return the effective divider placement
     */
    @JsonIgnore
    public Divider effectiveDivider() {
        return divider != null ? divider : Divider.NONE;
    }

    /**
     * A mutable builder for {@code NavMenuMetadata}.
     *
     * <p>Each setter sets the record component of the same name. Those components are
     * documented by the record's own {@code @param} tags and are deliberately not restated
     * here — a per-setter repetition of the component's meaning is filler, and filler is what
     * makes generated javadoc worth less than none.
     */
    public static final class Builder {
        private String group;
        private String parent;
        private String label;
        private String icon;
        private Integer order;
        private Integer groupOrder;
        private String groupIcon;
        private boolean hidden;
        private List<String> roles = List.of();
        private List<String> permissions = List.of();
        private String badge;
        private String badgeColor;
        private String tooltip;
        private boolean openInNewTab;
        private String routePath;
        private String queryParams;
        private boolean expanded;
        private Divider divider;

        private Builder() {
            // Every component starts unset; see the field initialisers.
        }

        public Builder group(String v) { this.group = v; return this; }
        public Builder parent(String v) { this.parent = v; return this; }
        public Builder label(String v) { this.label = v; return this; }
        public Builder icon(String v) { this.icon = v; return this; }
        public Builder order(Integer v) { this.order = v; return this; }
        public Builder groupOrder(Integer v) { this.groupOrder = v; return this; }
        public Builder groupIcon(String v) { this.groupIcon = v; return this; }
        public Builder hidden(boolean v) { this.hidden = v; return this; }
        public Builder roles(List<String> v) { this.roles = v; return this; }
        public Builder permissions(List<String> v) { this.permissions = v; return this; }
        public Builder badge(String v) { this.badge = v; return this; }
        public Builder badgeColor(String v) { this.badgeColor = v; return this; }
        public Builder tooltip(String v) { this.tooltip = v; return this; }
        public Builder openInNewTab(boolean v) { this.openInNewTab = v; return this; }
        public Builder routePath(String v) { this.routePath = v; return this; }
        public Builder queryParams(String v) { this.queryParams = v; return this; }
        public Builder expanded(boolean v) { this.expanded = v; return this; }
        public Builder divider(Divider v) { this.divider = v; return this; }

        /**
         * Builds the {@code NavMenuMetadata} from this builder's current state.
         *
         * @return the built {@code NavMenuMetadata}
         */
        public NavMenuMetadata build() {
            return new NavMenuMetadata(group, parent, label, icon, order, groupOrder, groupIcon,
                    hidden, roles, permissions, badge, badgeColor, tooltip, openInNewTab,
                    routePath, queryParams, expanded, divider);
        }
    }
}
