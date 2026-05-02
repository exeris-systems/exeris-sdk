package eu.exeris.sdk.annotation;

import java.lang.annotation.*;

/**
 * Groups related fields together in UI forms for better visual organization.
 * <p>Fields within the same group are rendered in a collapsible section
 * with a shared header.
 *
 * <h2>Basic Usage:</h2>
 * <pre>{@code
 * @ExerisDomain(module = "crm", path = "/customers")
 * public class Customer {
 *
 *     @Field(label = "First Name")
 *     @UIGroup(name = "personalInfo", label = "Personal Information", order = 1)
 *     private String firstName;
 *
 *     @Field(label = "Last Name")
 *     @UIGroup(name = "personalInfo")
 *     private String lastName;
 *
 *     @Field(label = "Email")
 *     @UIGroup(name = "contactInfo", label = "Contact Information", order = 2)
 *     private String email;
 *
 *     @Field(label = "Phone")
 *     @UIGroup(name = "contactInfo")
 *     private String phone;
 * }
 * }</pre>
 *
 * <h2>Collapsible Groups:</h2>
 * <pre>{@code
 * @Field(label = "Billing Address")
 * @UIGroup(
 *     name = "billing",
 *     label = "Billing Information",
 *     collapsible = true,
 *     collapsed = false,
 *     icon = "payment"
 * )
 * private Address billingAddress;
 * }</pre>
 *
 * <h2>Generated Angular Template:</h2>
 * <pre>{@code
 * <mat-expansion-panel [expanded]="!collapsed">
 *   <mat-expansion-panel-header>
 *     <mat-panel-title>
 *       <mat-icon>{{ icon }}</mat-icon> {{ label }}
 *     </mat-panel-title>
 *   </mat-expansion-panel-header>
 *   <!-- Fields in this group -->
 * </mat-expansion-panel>
 * }</pre>
 *
 * @author Exeris SDK Team
 * @version 1.0.0
 * @since 1.0.0
 * @see Tab
 * @see UI
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface UIGroup {

    /**
     * Unique identifier for this group.
     * <p>Fields with the same group name are rendered together.
     *
     * @return group identifier
     */
    String name();

    /**
     * Display label for the group header.
     * <p>Only needed on the first field in the group;
     * subsequent fields inherit from the first declaration.
     *
     * @return group header label
     */
    String label() default "";

    /**
     * Description shown as tooltip or subtitle for the group.
     *
     * @return group description
     */
    String description() default "";

    /**
     * Display order of this group relative to other groups.
     * <p>Lower numbers appear first.
     *
     * @return display order (1-based)
     */
    int order() default 999;

    /**
     * Material icon name displayed in group header.
     * <p>Examples: "person", "payment", "location_on", "settings"
     *
     * @return icon name
     */
    String icon() default "";

    /**
     * Whether the group can be collapsed/expanded.
     *
     * @return true if collapsible
     */
    boolean collapsible() default true;

    /**
     * Whether the group is initially collapsed.
     * <p>Only applies when {@code collapsible = true}.
     *
     * @return true if initially collapsed
     */
    boolean collapsed() default false;

    /**
     * Number of columns in the group's grid layout.
     * <p>Fields within the group use CSS grid with this column count.
     * <ul>
     *   <li>1 = single column (vertical stacking)</li>
     *   <li>2 = two columns (side-by-side)</li>
     *   <li>3 = three columns</li>
     *   <li>4 = four columns</li>
     *   <li>12 = full Bootstrap-like grid</li>
     * </ul>
     *
     * @return number of columns
     */
    int columns() default 2;

    /**
     * CSS class to apply to the group container.
     *
     * @return CSS class name(s)
     */
    String cssClass() default "";

    /**
     * SpEL expression determining when this group is visible.
     * <p>When expression evaluates to false, the entire group is hidden.
     *
     * <p><strong>Examples:</strong>
     * <pre>{@code
     * visibleWhen = "type == 'BUSINESS'"        // Show for business customers
     * visibleWhen = "hasRole('ADMIN')"          // Show for admins only
     * visibleWhen = "premiumFeatures"           // Show when premium enabled
     * }</pre>
     *
     * @return visibility condition expression
     */
    String visibleWhen() default "";
}

