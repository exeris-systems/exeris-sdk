package eu.exeris.sdk.annotation;

import java.lang.annotation.*;

/**
 * Organizes fields into tabbed sections for complex entity forms.
 * <p>Use tabs to break up large forms into logical sections,
 * improving user experience in Enterprise applications.
 *
 * <h2>Basic Usage:</h2>
 * <pre>{@code
 * @ExerisDomain(module = "hr", path = "/employees")
 * public class Employee {
 *
 *     @Field(label = "First Name")
 *     @Tab(name = "basic", label = "Basic Info", order = 1)
 *     private String firstName;
 *
 *     @Field(label = "Last Name")
 *     @Tab(name = "basic")
 *     private String lastName;
 *
 *     @Field(label = "Salary")
 *     @Tab(name = "compensation", label = "Compensation", order = 2, icon = "attach_money")
 *     private BigDecimal salary;
 *
 *     @Field(label = "Bonus")
 *     @Tab(name = "compensation")
 *     private BigDecimal bonus;
 *
 *     @Field(label = "Emergency Contact")
 *     @Tab(name = "emergency", label = "Emergency", order = 3, icon = "emergency")
 *     private String emergencyContact;
 * }
 * }</pre>
 *
 * <h2>Tabs with Badges:</h2>
 * <pre>{@code
 * @Field(label = "Notes")
 * @Tab(
 *     name = "notes",
 *     label = "Notes",
 *     icon = "note",
 *     badgeField = "notesCount",  // Show count badge
 *     badgeColor = "primary"
 * )
 * private List<Note> notes;
 * }</pre>
 *
 * <h2>Lazy-Loaded Tabs:</h2>
 * <pre>{@code
 * @Field(label = "Order History")
 * @Tab(
 *     name = "history",
 *     label = "Order History",
 *     lazy = true,  // Load content only when tab is selected
 *     icon = "history"
 * )
 * private List<Order> orderHistory;
 * }</pre>
 *
 * <h2>Target design — the template this would emit:</h2>
 * <pre>{@code
 * <mat-tab-group>
 *   <mat-tab label="Basic Info">
 *     <mat-icon>person</mat-icon>
 *     <!-- Fields in 'basic' tab -->
 *   </mat-tab>
 *   <mat-tab label="Compensation">
 *     <mat-icon>attach_money</mat-icon>
 *     <!-- Fields in 'compensation' tab -->
 *   </mat-tab>
 * </mat-tab-group>
 * }</pre>
 *
 *
 * <p><strong>Status: RESERVED</strong> — presentation grouping is emitted from {@link View @View} — its regions and blocks — and, at the leaf level, from {@link UI @UI}. This
 * annotation feeds neither, so a tab declared here appears in no generated template.
 * @author Exeris SDK Team
 * @version 0.1.0
 * @since 0.1.0
 * @see UIGroup
 * @see UI
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface Tab {

    /**
     * Unique identifier for this tab.
     * <p>Fields with the same tab name are rendered in the same tab panel.
     *
     * @return tab identifier
     */
    String name();

    /**
     * Display label for the tab header.
     * <p>Only needed on the first field in the tab;
     * subsequent fields inherit from the first declaration.
     *
     * @return tab header label
     */
    String label() default "";

    /**
     * Display order of this tab relative to other tabs.
     * <p>Lower numbers appear first (leftmost in horizontal tabs).
     *
     * @return display order (1-based)
     */
    int order() default 999;

    /**
     * Material icon name displayed in tab header.
     * <p>Examples: "person", "settings", "payment", "history"
     *
     * @return icon name
     */
    String icon() default "";

    /**
     * Whether tab content should be lazy-loaded.
     * <p>When true, tab content is only loaded when user selects the tab.
     * Useful for tabs with heavy content (lists, graphs).
     *
     * @return true for lazy loading
     */
    boolean lazy() default false;

    /**
     * Field name whose value is displayed as a badge count.
     * <p>Shows a numeric badge on the tab header.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * @Tab(name = "comments", badgeField = "commentCount")
     * }</pre>
     * Result: Tab shows "Comments (5)" where 5 is value of commentCount
     *
     * @return field name for badge value
     */
    String badgeField() default "";

    /**
     * Color of the badge.
     * <p>Options: "primary", "accent", "warn"
     *
     * @return badge color
     */
    String badgeColor() default "primary";

    /**
     * Whether this tab is disabled (not clickable).
     * <p>Disabled tabs are visible but cannot be selected.
     *
     * @return true if disabled
     */
    boolean disabled() default false;

    /**
     * SpEL expression determining when this tab is disabled.
     *
     * <p><strong>Examples:</strong>
     * <pre>{@code
     * disabledWhen = "status == 'DRAFT'"       // Disable until published
     * disabledWhen = "!hasPermission('VIEW')"  // Disable without permission
     * }</pre>
     *
     * @return disabled condition expression
     */
    String disabledWhen() default "";

    /**
     * SpEL expression determining when this tab is visible.
     * <p>When expression evaluates to false, the tab is not rendered.
     *
     * <p><strong>Examples:</strong>
     * <pre>{@code
     * visibleWhen = "type == 'PREMIUM'"        // Show for premium only
     * visibleWhen = "hasRole('ADMIN')"         // Show for admins only
     * }</pre>
     *
     * @return visibility condition expression
     */
    String visibleWhen() default "";

    /**
     * CSS class to apply to the tab panel content.
     *
     * @return CSS class name(s)
     */
    String cssClass() default "";
}

