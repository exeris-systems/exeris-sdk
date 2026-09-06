package eu.exeris.sdk.annotation;

import java.lang.annotation.*;

/**
 * Configures the navigation menu placement for a domain entity.
 * <p>Defines where and how this entity appears in the application's
 * sidebar navigation, enabling automatic menu generation.
 *
 * <h2>Basic Usage:</h2>
 * {@snippet lang="java" :
 * @ExerisDomain(module = "sales", path = "/orders")
 * @NavMenu(
 *     group = "Sales",
 *     label = "Orders",
 *     icon = "shopping_cart",
 *     order = 1
 * )
 * public class Order { }
 * }
 *
 * <h2>Nested Menu Structure:</h2>
 * {@snippet lang="java" :
 * // Parent menu group
 * @ExerisDomain(module = "settings", path = "/settings/general")
 * @NavMenu(
 *     group = "Settings",
 *     label = "General",
 *     icon = "settings",
 *     order = 1
 * )
 * public class GeneralSettings { }
 *
 * // Nested under Settings > Security
 * @ExerisDomain(module = "settings", path = "/settings/security")
 * @NavMenu(
 *     group = "Settings",
 *     parent = "Security",
 *     label = "Permissions",
 *     icon = "security",
 *     order = 2
 * )
 * public class SecuritySettings { }
 * }
 *
 * <h2>Role-Based Menu Visibility:</h2>
 * {@snippet lang="java" :
 * @ExerisDomain(module = "admin", path = "/users")
 * @NavMenu(
 *     group = "Administration",
 *     label = "Users",
 *     icon = "people",
 *     roles = {"ADMIN", "USER_MANAGER"},
 *     order = 1
 * )
 * public class User { }
 * }
 *
 * <h2>Hidden from Menu:</h2>
 * {@snippet lang="java" :
 * @ExerisDomain(module = "internal", path = "/audit-logs")
 * @NavMenu(hidden = true)  // Not shown in navigation
 * public class AuditLog { }
 * }
 *
 * <h2>Target design — the menu this would emit:</h2>
 * <pre>
 * +-- Dashboard
 * +-- Sales
 * |   +-- Orders (order=1)
 * |   +-- Customers (order=2)
 * |   +-- Invoices (order=3)
 * +-- Inventory
 * |   +-- Products
 * |   +-- Warehouses
 * +-- Settings
 *     +-- General
 *     +-- Security
 *         +-- Permissions
 * </pre>
 *
 *
 * <p><strong>Status: RESERVED</strong> — no navigation artefact is emitted from this
 * annotation on either side. The generated TypeScript app shell builds its sidebar from the
 * entity list and the {@link View @View} routes, never from a menu declared here.
 * @since 0.1
 * @see ExerisDomain
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface NavMenu {

    /**
     * Top-level menu group name.
     * <p>Entities with the same group are organized together.
     * If group doesn't exist, it will be created.
     *
     * <p><strong>Common groups:</strong>
     * <ul>
     *   <li>"Dashboard" - main overview</li>
     *   <li>"Sales" - sales-related entities</li>
     *   <li>"Inventory" - product/stock entities</li>
     *   <li>"CRM" - customer management</li>
     *   <li>"Reports" - reporting views</li>
     *   <li>"Settings" - configuration</li>
     *   <li>"Administration" - admin tools</li>
     * </ul>
     *
     * @return menu group name
     */
    String group() default "";

    /**
     * Parent menu item for nested navigation.
     * <p>Creates a submenu under the specified parent.
     * If empty, item appears directly under the group.
     *
     * @return parent menu item name
     */
    String parent() default "";

    /**
     * Display label in the navigation menu.
     * <p>Defaults to entity class name if empty.
     *
     * @return menu item label
     */
    String label() default "";

    /**
     * Material icon name for the menu item.
     * <p>Examples: "home", "shopping_cart", "person", "settings"
     *
     * @see <a href="https://fonts.google.com/icons">Material Icons</a>
     * @return icon name
     */
    String icon() default "";

    /**
     * Display order within the group/parent.
     * <p>Lower numbers appear first (top).
     *
     * @return display order (1-based)
     */
    int order() default 999;

    /**
     * Order of the group itself in the main navigation.
     * <p>Only effective when this is the first item in a new group.
     *
     * @return group order
     */
    int groupOrder() default 999;

    /**
     * Icon for the menu group.
     * <p>Only effective when this is the first item in a new group.
     *
     * @return group icon name
     */
    String groupIcon() default "";

    /**
     * Whether this entity is hidden from navigation.
     * <p>Hidden entities can still be accessed via URL,
     * but don't appear in the sidebar menu.
     *
     * @return true to hide from navigation
     */
    boolean hidden() default false;

    /**
     * Roles required to see this menu item.
     * <p>Empty array means visible to all authenticated users.
     * If user lacks all listed roles, menu item is hidden.
     *
     * @return required role names
     */
    String[] roles() default {};

    /**
     * Permissions required to see this menu item.
     * <p>More granular than roles. User must have at least one
     * of the listed permissions to see the menu item.
     *
     * @return required permission names
     */
    String[] permissions() default {};

    /**
     * Badge text or expression to display on menu item.
     * <p>Can be static text or SpEL expression referencing a service.
     *
     * <p><strong>Examples:</strong>
     * {@snippet lang="java" :
     * badge = "NEW"                           // Static text
     * badge = "@orderService.pendingCount()"  // Dynamic count
     * badge = "#{stats.unreadMessages}"       // SpEL expression
     * }
     *
     * @return badge text or expression
     */
    String badge() default "";

    /**
     * Color of the badge.
     * <p>Options: "primary", "accent", "warn"
     *
     * @return badge color
     */
    String badgeColor() default "accent";

    /**
     * Tooltip text shown on hover.
     * <p>Provides additional context for the menu item.
     *
     * @return tooltip text
     */
    String tooltip() default "";

    /**
     * Whether this menu item opens in a new tab/window.
     * <p>Useful for external links or standalone tools.
     *
     * @return true to open in new tab
     */
    boolean openInNewTab() default false;

    /**
     * Custom route path override.
     * <p>If empty, uses the entity's path from @ExerisDomain.
     * Use this for custom navigation targets.
     *
     * @return custom route path
     */
    String routePath() default "";

    /**
     * Query parameters to append to the route.
     * <p>Format: {@code "key1=value1&key2=value2"}
     *
     * @return query parameters string
     */
    String queryParams() default "";

    /**
     * Whether menu item should be expanded by default (for groups).
     * <p>Only applies to menu items that have children.
     *
     * @return true if expanded by default
     */
    boolean expanded() default false;

    /**
     * Divider placement relative to this menu item.
     */
    enum Divider {
        /** No divider. */
        NONE,
        /** Divider line before this item. */
        BEFORE,
        /** Divider line after this item. */
        AFTER
    }

    /**
     * Whether to show a divider line around this menu item.
     * <p>Useful for visual separation of menu sections.
     *
     * @return divider placement
     */
    Divider divider() default Divider.NONE;
}

