package eu.exeris.sdk.annotation;

import java.lang.annotation.*;

/**
 * Configures UI component generation for Exeris domain entities and fields.
 * <p>This annotation can be used at two levels:
 * <ul>
 *   <li><strong>Entity level:</strong> Defines which views should be generated (list, detail, forms)</li>
 *   <li><strong>Field level:</strong> Configures how a field should be displayed in the UI</li>
 * </ul>
 *
 * <h2>Entity-Level Usage (within @ExerisDomain):</h2>
 * <pre>{@code
 * @ExerisDomain(
 *     module = "sales",
 *     aggregate = "Order",
 *     ui = @UI(
 *         listView = true,        // Generate list/table view
 *         detailView = true,      // Generate detail/view page
 *         createForm = true,      // Generate create form
 *         editForm = true,        // Generate edit form
 *         searchable = true,      // Add search functionality
 *         filterable = true,      // Add filter panel
 *         exportable = true       // Add export (CSV, Excel, PDF)
 *     )
 * )
 * public class Order { }
 * }</pre>
 *
 * <h2>Field-Level Usage (within @Field):</h2>
 * <pre>{@code
 * @Field(
 *     label = "Order Number",
 *     ui = @UI(
 *         displayInList = true,       // Show in list view
 *         displayInDetail = true,     // Show in detail view
 *         editableInForm = true,      // Allow editing in forms
 *         displayOrder = 1,           // Display position (1 = first)
 *         componentType = ComponentType.TEXT_INPUT,
 *         width = "200px",
 *         format = "uppercase"        // Text transformation
 *     )
 * )
 * private String orderNumber;
 * }</pre>
 *
 * <h2>Component Types:</h2>
 * <p>The {@code componentType} attribute determines which Angular component is generated:
 * <ul>
 *   <li>{@code TEXT_INPUT} - Standard text input field</li>
 *   <li>{@code TEXT_AREA} - Multi-line text area</li>
 *   <li>{@code NUMBER_INPUT} - Numeric input with spinners</li>
 *   <li>{@code DATE_PICKER} - Calendar date picker</li>
 *   <li>{@code DATETIME_PICKER} - Date and time picker</li>
 *   <li>{@code CHECKBOX} - Boolean checkbox</li>
 *   <li>{@code SELECT} - Dropdown select (single choice)</li>
 *   <li>{@code MULTI_SELECT} - Multi-select dropdown</li>
 *   <li>{@code RADIO_GROUP} - Radio button group</li>
 *   <li>{@code FILE_UPLOAD} - File upload component</li>
 *   <li>{@code RICH_TEXT_EDITOR} - WYSIWYG editor (e.g., TinyMCE)</li>
 *   <li>{@code AUTO} - SDK chooses based on field type</li>
 * </ul>
 *
 * @author Exeris platform Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Target({ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface UI {

    // ========== Entity-Level Attributes ==========

    /**
     * Icon name for this entity (e.g., "building", "user", "shopping_cart").
     * <p>Used in navigation menus, breadcrumbs, and list headers.
     * Supports Material Icons, Font Awesome, or custom icon sets.
     *
     * @return the icon name
     */
    String icon() default "";

    /**
     * Display label for this entity (singular form).
     * <p>Example: "Tenant", "User", "Order"
     *
     * @return the entity label
     */
    String label() default "";

    /**
     * Display label for this entity (plural form).
     * <p>Example: "Tenants", "Users", "Orders"
     *
     * @return the plural label
     */
    String pluralLabel() default "";

    /**
     * Short description of this entity for tooltips and help text.
     *
     * @return the description
     */
    String description() default "";

    /**
     * Title for the list/table view page.
     * <p>Example: "All Tenants", "User Management"
     *
     * @return the list view title
     */
    String listTitle() default "";

    /**
     * Title for the create form page.
     * <p>Example: "Create New Tenant", "Add User"
     *
     * @return the create form title
     */
    String createTitle() default "";

    /**
     * Title for the edit form page.
     * <p>Example: "Edit Tenant", "Update User"
     *
     * @return the edit form title
     */
    String editTitle() default "";

    /**
     * Title for the detail view page.
     * <p>Example: "Tenant Details", "User Profile"
     *
     * @return the detail view title
     */
    String detailTitle() default "";

    /**
     * Color theme for this entity (used in badges, headers, etc.).
     * <p>Examples: "primary", "secondary", "success", "warning", "danger", "#FF5722"
     *
     * @return the color theme
     */
    String color() default "";

    /**
     * Whether to generate a list/table view component.
     * <p>Generates an Angular component with:
     * <ul>
     *   <li>Data table with sorting and pagination</li>
     *   <li>Column configuration based on field @UI settings</li>
     *   <li>Action buttons (view, edit, delete)</li>
     *   <li>Bulk operations (if enabled)</li>
     * </ul>
     *
     * @return true if list view should be generated
     */
    boolean listView() default false;

    /**
     * Whether to generate a detail/view page component.
     * <p>Displays a read-only view of the entity with all fields.
     *
     * @return true if detail view should be generated
     */
    boolean detailView() default false;

    /**
     * Whether to generate a create form component.
     * <p>Includes form validation, error handling, and submit logic.
     *
     * @return true if create form should be generated
     */
    boolean createForm() default false;

    /**
     * Whether to generate an edit form component.
     * <p>Pre-populates with existing data, includes validation and update logic.
     *
     * @return true if edit form should be generated
     */
    boolean editForm() default false;

    /**
     * Whether to add search functionality to list view.
     * <p>Generates a search bar that filters by searchable fields.
     *
     * @return true if search should be enabled
     */
    boolean searchable() default false;

    /**
     * Whether to add filter panel to list view.
     * <p>Generates filter controls based on field types and configurations.
     *
     * @return true if filters should be enabled
     */
    boolean filterable() default false;

    /**
     * Whether to add export functionality (CSV, Excel, PDF).
     * <p>Generates export buttons and backend endpoints.
     *
     * @return true if export should be enabled
     */
    boolean exportable() default false;

    // ========== Field-Level Attributes ==========

    /**
     * Source enum class name for SELECT/MULTI_SELECT components.
     * <p>The enum values will be used as options in the dropdown.
     * Can be a simple name (resolved from same package) or fully qualified.
     *
     * <p><strong>Examples:</strong>
     * <pre>{@code
     * enumSource = "UserRole"              // Same package
     * enumSource = "com.app.enums.Status"  // Fully qualified
     * }</pre>
     *
     * @return the enum class name
     */
    String enumSource() default "";

    /**
     * Whether to display this field in the list/table view.
     *
     * @return true if field should appear in list
     */
    boolean displayInList() default false;

    /**
     * Whether to display this field in the detail view.
     *
     * @return true if field should appear in detail view
     */
    boolean displayInDetail() default true;

    /**
     * Whether this field is editable in create/edit forms.
     * <p>Set to false for read-only fields (e.g., calculated values, system fields).
     *
     * @return true if field is editable
     */
    boolean editableInForm() default true;

    /**
     * Display order in UI (lower numbers appear first).
     * <p>Use this to control field ordering across all views.
     *
     * @return display position (1-based)
     */
    int displayOrder() default 999;

    /**
     * The UI component type to use for this field.
     * <p>If set to {@code AUTO}, the SDK will choose based on the Java field type:
     * <ul>
     *   <li>String → TEXT_INPUT</li>
     *   <li>Integer/Long/BigDecimal → NUMBER_INPUT</li>
     *   <li>Boolean → CHECKBOX</li>
     *   <li>LocalDate → DATE_PICKER</li>
     *   <li>LocalDateTime/Instant → DATETIME_PICKER</li>
     *   <li>Enum → SELECT</li>
     * </ul>
     *
     * @return the component type
     */
    ComponentType componentType() default ComponentType.AUTO;

    /**
     * CSS width for the field in forms (e.g., "100%", "200px", "50vw").
     *
     * @return the field width
     */
    String width() default "";

    /**
     * Display format for the field value.
     * <p>Examples:
     * <ul>
     *   <li>"currency" - Format as currency (e.g., $1,234.56)</li>
     *   <li>"percentage" - Format as percentage (e.g., 45.5%)</li>
     *   <li>"date:short" - Short date format (e.g., 12/31/2023)</li>
     *   <li>"date:long" - Long date format (e.g., December 31, 2023)</li>
     *   <li>"uppercase" - Transform to uppercase</li>
     *   <li>"lowercase" - Transform to lowercase</li>
     *   <li>"number:2" - Number with 2 decimal places</li>
     * </ul>
     *
     * @return the format string
     */
    String format() default "";

    /**
     * Placeholder text for input fields.
     * <p>Displayed when the field is empty.
     *
     * @return the placeholder text
     */
    String placeholder() default "";

    /**
     * Help text displayed below the field.
     * <p>Used to provide additional guidance to users.
     *
     * @return the help text
     */
    String helpText() default "";

    /**
     * CSS class name(s) to apply to the field container.
     *
     * @return CSS class names
     */
    String cssClass() default "";

    /**
     * Whether this field is visible (can be toggled programmatically).
     *
     * @return true if visible
     */
    boolean visible() default true;


    /**
     * Alias for {@link #componentType()}.
     * <p>Provided for backward compatibility and shorter syntax.
     * If both {@code component} and {@code componentType} are specified,
     * {@code componentType} takes precedence.
     *
     * @return the component type
     */
    ComponentType component() default ComponentType.AUTO;

    /**
     * Whether this field is read-only (displayed but not editable).
     * <p>When {@code true}:
     * <ul>
     *   <li>Field is displayed in forms</li>
     *   <li>Input is disabled/readonly</li>
     *   <li>Visual indicator shows field is not editable</li>
     * </ul>
     *
     * @return true if field is read-only
     */
    boolean readOnly() default false;

    /**
     * Whether this field is hidden (not displayed in UI).
     * <p>Opposite of {@link #visible()}. When {@code true}:
     * <ul>
     *   <li>Field is not rendered in UI</li>
     *   <li>Value may still be submitted in forms</li>
     *   <li>Useful for hidden fields carrying state</li>
     * </ul>
     *
     * <p>Note: If both {@code hidden} and {@code visible} are specified,
     * {@code hidden} takes precedence.
     *
     * @return true if field is hidden
     */
    boolean hidden() default false;

    /**
     * Number of grid columns this field should span in form layout.
     * <p>Used for responsive grid layouts (e.g., Bootstrap, CSS Grid):
     * <ul>
     *   <li>1 = narrow field (checkboxes, short text)</li>
     *   <li>2 = medium field (default)</li>
     *   <li>3-4 = wide field (text areas, multi-selects)</li>
     *   <li>12 = full-width field</li>
     * </ul>
     *
     * <p>Default: 0 means auto-detect based on component type.
     *
     * @return number of columns to span
     */
    int gridSpan() default 0;

    /**
     * Additional component-specific properties as JSON object.
     * <p>Allows passing custom configuration to UI components.
     * Format: JSON object string.
     *
     * <p><strong>Examples:</strong>
     * <pre>{@code
     * props = "{\"rows\": 5, \"cols\": 80}"              // Text area size
     * props = "{\"min\": 0, \"max\": 100, \"step\": 5}" // Slider config
     * props = "{\"multiple\": true, \"max\": 5}"        // File upload
     * props = "{\"dateFormat\": \"YYYY-MM-DD\"}"        // Date picker
     * }</pre>
     *
     * @return JSON configuration string
     */
    String props() default "";

    /**
     * Expression that determines when this field is visible.
     * <p>Uses SpEL (Spring Expression Language) syntax.
     * Evaluated against form data or entity state.
     *
     * <p><strong>Examples:</strong>
     * <pre>{@code
     * visibleWhen = "type == 'PREMIUM'"           // Show only for premium
     * visibleWhen = "amount > 1000"               // Show if amount exceeds threshold
     * visibleWhen = "country == 'US'"             // Show for specific country
     * visibleWhen = "hasRole('ADMIN')"            // Show for admins only
     * }</pre>
     *
     * @return visibility condition expression
     */
    String visibleWhen() default "";

    /**
     * Expression that determines when this field is enabled (editable).
     * <p>Uses SpEL (Spring Expression Language) syntax.
     * When expression evaluates to {@code false}, field becomes read-only.
     *
     * <p><strong>Examples:</strong>
     * <pre>{@code
     * enabledWhen = "status == 'DRAFT'"          // Editable only in draft state
     * enabledWhen = "!locked"                     // Disabled when locked
     * enabledWhen = "hasPermission('EDIT')"       // Based on permission
     * enabledWhen = "createdBy == currentUser()"  // Only creator can edit
     * }</pre>
     *
     * @return enabled condition expression
     */
    String enabledWhen() default "";

    /**
     * Enum representing available UI component types.
     */
    enum ComponentType {
        /** Auto-detect based on field type */
        AUTO,
        /** Standard single-line text input */
        TEXT_INPUT,
        /** Multi-line text area */
        TEXT_AREA,
        /** Numeric input with increment/decrement controls */
        NUMBER_INPUT,
        /** Date picker (calendar) */
        DATE_PICKER,
        /** Date and time picker */
        DATETIME_PICKER,
        /** Boolean checkbox */
        CHECKBOX,
        /** Single-select dropdown */
        SELECT,
        /** Multi-select dropdown */
        MULTI_SELECT,
        /** Radio button group */
        RADIO_GROUP,
        /** File upload component */
        FILE_UPLOAD,
        /** Rich text WYSIWYG editor */
        RICH_TEXT_EDITOR,
        /** Color picker */
        COLOR_PICKER,
        /** Password input (masked) */
        PASSWORD,
        /** Email input with validation */
        EMAIL,
        /** URL input with validation */
        URL,
        /** Phone number input with formatting */
        PHONE,
        /** Slider for numeric ranges */
        SLIDER,
        /** Toggle switch (alternative to checkbox) */
        TOGGLE,
        /** Autocomplete/typeahead input */
        AUTOCOMPLETE
    }
}


