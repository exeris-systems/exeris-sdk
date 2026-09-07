package eu.exeris.sdk.annotation;

import java.lang.annotation.*;

/**
 * Configures UI component generation for Exeris domain entities and fields.
 *
 * <p><strong>Status: PARTIAL, and the two levels differ. Read this before
 * writing {@code @UI} anywhere.</strong>
 * <ul>
 *   <li><strong>Entity level</strong> — applied as a sibling of the annotated
 *       type, the view flags ({@code listView}, {@code detailView},
 *       {@code createForm}, {@code editForm}, {@code searchable},
 *       {@code filterable}, {@code exportable}) are extracted into
 *       {@code UIMetadata} and reach the AST. No emitter gates on them: which
 *       Angular artifacts are generated is a codegen CLI setting, not an
 *       annotation. The only {@code UIMetadata} member a generator reads is
 *       {@code listColumns}, and no {@code @UI} attribute populates it. So the
 *       flags are honest design intent that currently changes nothing emitted.</li>
 *   <li><strong>Field level</strong> — RESERVED. No reader extracts a
 *       field-level {@code @UI} in either form, so none of the attributes below
 *       ({@code componentType}, {@code displayInList}, {@code displayOrder},
 *       {@code placeholder}, {@code width}, {@code format}, …) has any effect
 *       today. They are the declared shape of the leaf field facet described
 *       under "Successor" below.</li>
 * </ul>
 * <p>Both levels are read <strong>only in the sibling form</strong>. The nested
 * members {@code @ExerisDomain(ui = @UI(...))} and {@code @Field(ui = @UI(...))}
 * compile and are looked at by nobody — see "The nested-form trap" in the
 * {@linkplain eu.exeris.sdk.annotation package documentation}.
 *
 * <p><strong>Successor:</strong> {@code @View} and the unified presentation IR
 * ({@code ViewMetadata}, RFC-2026-06-25) are the single presentation model into
 * which {@code @UI} is being absorbed — entity-level view selection becomes a
 * {@code @View}, and the field-level render detail here is reused as the leaf
 * field facet of {@code ViewMetadata} (the {@code UIMetadata.UIFieldMetadata}
 * record). {@code @View} structural generation is live (tooling processor
 * extraction + the codegen-ts Angular view generator, RFC-2026-06-28), but the
 * ADR-047 leaf-field facet subsumption is not implemented yet. {@code @UI} is
 * therefore <em>not</em> deprecated — the rule is that
 * {@code @Deprecated(forRemoval)} runs only once {@code @View} can actually
 * replace it, and the facet it would be replaced by does not exist. That is a
 * reason to keep the annotation, not evidence that it works: neither level
 * currently produces output, and the field level is not extracted at all.
 * Consequence: with 1.x minors additive-only, {@code @UI} is frozen through the
 * 1.x line; the deprecation is targeted at the 1.x minor where the ADR-047 facet
 * completes, with removal at 2.0. See RFC-2026-06-25 for the convergence plan.
 *
 * <h2>Entity-Level Usage — sibling of the annotated type</h2>
 * <p>The flags reach {@code UIMetadata}; no emitter gates on them yet.
 * {@snippet lang="java" :
 * @ExerisDomain(module = "sales", aggregate = "Order", path = "/orders")
 * @UI(                        // sibling — NOT @ExerisDomain(ui = @UI(...))
 *     listView = true,        // intent: generate list/table view
 *     detailView = true,      // intent: generate detail/view page
 *     createForm = true,      // intent: generate create form
 *     editForm = true,        // intent: generate edit form
 *     searchable = true,
 *     filterable = true,
 *     exportable = true
 * )
 * public class Order { }
 * }
 * <p>Note the reader convention: when a standalone {@code @UI} is present, the
 * four view flags default to {@code true} and {@code exportable} to
 * {@code false} — "you added {@code @UI}, so the views are on" — which is
 * stronger than the per-attribute defaults declared on this annotation.
 *
 * <h2>Field-Level Usage — RESERVED, no reader</h2>
 * <p>Shown in the sibling form for when a reader lands. Neither this nor the
 * nested {@code @Field(ui = @UI(...))} form is extracted today:
 * {@snippet lang="java" :
 * @Field(label = "Order Number")
 * @UI(                                  // sibling — and currently a no-op
 *     displayInList = true,
 *     displayInDetail = true,
 *     editableInForm = true,
 *     displayOrder = 1,
 *     componentType = ComponentType.TEXT_INPUT,
 *     width = "200px",
 *     format = "uppercase"
 * )
 * private String orderNumber;
 * }
 *
 * <h2>Component Types:</h2>
 * <p>The {@code componentType} attribute names which Angular component a
 * field-level reader would generate. RESERVED along with the rest of the
 * field-level surface — no component is selected from it today:
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
 * @since 0.1
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
     * {@snippet lang="java" :
     * enumSource = "UserRole"              // Same package
     * enumSource = "com.app.enums.Status"  // Fully qualified
     * }
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
     * Custom component identifier, used as the escape hatch out of the closed
     * {@link ComponentType} enum.
     * <p>Only meaningful when {@link #componentType()} (or {@link #component()})
     * is {@link ComponentType#CUSTOM}: it names the application-supplied control
     * the generator should render (e.g. an Angular component selector or a
     * registry key). For any built-in {@code ComponentType} it is ignored.
     *
     * <p><strong>Example:</strong>
     * {@snippet lang="java" :
     * @UI(componentType = ComponentType.CUSTOM, customComponent = "app-geo-point-picker")
     * }
     *
     * <p>Declaring {@code CUSTOM} without a {@code customComponent} is a
     * generator-side error; the SDK only carries the declared shape.
     *
     * @return the custom component identifier, or {@code ""} when not custom
     */
    String customComponent() default "";

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
     * Optional i18n message key for {@link #placeholder()}.
     * <p>When non-empty it is resolved against the message bundle, with
     * {@link #placeholder()} as the fallback text; when empty the literal
     * placeholder is used. See {@link Field#labelKey()} for the convention.
     *
     * @return the message key, or {@code ""} to use the literal placeholder
     */
    String placeholderKey() default "";

    /**
     * Help text displayed below the field.
     * <p>Used to provide additional guidance to users.
     *
     * @return the help text
     */
    String helpText() default "";

    /**
     * Optional i18n message key for {@link #helpText()}.
     * <p>Same resolution contract as {@link #placeholderKey()}.
     *
     * @return the message key, or {@code ""} to use the literal help text
     */
    String helpTextKey() default "";

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
     * {@snippet lang="java" :
     * props = "{\"rows\": 5, \"cols\": 80}"              // Text area size
     * props = "{\"min\": 0, \"max\": 100, \"step\": 5}" // Slider config
     * props = "{\"multiple\": true, \"max\": 5}"        // File upload
     * props = "{\"dateFormat\": \"YYYY-MM-DD\"}"        // Date picker
     * }
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
     * {@snippet lang="java" :
     * visibleWhen = "type == 'PREMIUM'"           // Show only for premium
     * visibleWhen = "amount > 1000"               // Show if amount exceeds threshold
     * visibleWhen = "country == 'US'"             // Show for specific country
     * visibleWhen = "hasRole('ADMIN')"            // Show for admins only
     * }
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
     * {@snippet lang="java" :
     * enabledWhen = "status == 'DRAFT'"          // Editable only in draft state
     * enabledWhen = "!locked"                     // Disabled when locked
     * enabledWhen = "hasPermission('EDIT')"       // Based on permission
     * enabledWhen = "createdBy == currentUser()"  // Only creator can edit
     * }
     *
     * @return enabled condition expression
     */
    String enabledWhen() default "";

    /**
     * Enum representing available UI component types.
     */
    enum ComponentType {
        /** Auto-detect based on field type. */
        AUTO,
        /** Standard single-line text input. */
        TEXT_INPUT,
        /** Multi-line text area. */
        TEXT_AREA,
        /** Numeric input with increment/decrement controls. */
        NUMBER_INPUT,
        /** Date picker (calendar). */
        DATE_PICKER,
        /** Date and time picker. */
        DATETIME_PICKER,
        /** Boolean checkbox. */
        CHECKBOX,
        /** Single-select dropdown. */
        SELECT,
        /** Multi-select dropdown. */
        MULTI_SELECT,
        /** Radio button group. */
        RADIO_GROUP,
        /** File upload component. */
        FILE_UPLOAD,
        /** Rich text WYSIWYG editor. */
        RICH_TEXT_EDITOR,
        /** Color picker. */
        COLOR_PICKER,
        /** Password input (masked). */
        PASSWORD,
        /** Email input with validation. */
        EMAIL,
        /** URL input with validation. */
        URL,
        /** Phone number input with formatting. */
        PHONE,
        /** Slider for numeric ranges. */
        SLIDER,
        /** Toggle switch (alternative to checkbox). */
        TOGGLE,
        /** Autocomplete/typeahead input. */
        AUTOCOMPLETE,
        /**
         * Application-supplied custom component (escape hatch out of this closed
         * enum). The concrete control is named by {@link UI#customComponent()}.
         */
        CUSTOM
    }
}


