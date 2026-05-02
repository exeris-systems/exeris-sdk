package eu.exeris.sdk.annotation;

import java.lang.annotation.*;

/**
 * Defines a parameter for a domain action method.
 *
 * <p>Used to configure action parameters with labels, validation,
 * default values, and UI configuration for generated forms.
 *
 * <h2>Basic Usage:</h2>
 * <pre>{@code
 * @Action(name = "reject", label = "Reject Order")
 * public void reject(
 *     @ActionParam(
 *         name = "reason",
 *         label = "Rejection Reason",
 *         required = true,
 *         ui = @UI(componentType = UI.ComponentType.TEXT_AREA)
 *     )
 *     String reason,
 *
 *     @ActionParam(
 *         name = "notifyCustomer",
 *         label = "Notify Customer",
 *         defaultValue = "true"
 *     )
 *     boolean notifyCustomer
 * ) {
 *     // action logic
 * }
 * }</pre>
 *
 * <h2>With Validation:</h2>
 * <pre>{@code
 * @ActionParam(
 *     name = "amount",
 *     label = "Refund Amount",
 *     required = true,
 *     validation = @Validation(
 *         positive = true,
 *         max = 10000,
 *         message = "Amount must be between 0 and 10,000"
 *     ),
 *     ui = @UI(
 *         componentType = UI.ComponentType.NUMBER_INPUT,
 *         format = "currency"
 *     )
 * )
 * BigDecimal amount
 * }</pre>
 *
 * <h2>With Options (Enum/Select):</h2>
 * <pre>{@code
 * @ActionParam(
 *     name = "priority",
 *     label = "Priority Level",
 *     required = true,
 *     options = {"LOW", "MEDIUM", "HIGH", "CRITICAL"},
 *     optionLabels = {"Low Priority", "Medium Priority", "High Priority", "Critical"},
 *     defaultValue = "MEDIUM"
 * )
 * String priority
 * }</pre>
 *
 * <h2>With Relationship (Entity Selection):</h2>
 * <pre>{@code
 * @ActionParam(
 *     name = "assigneeId",
 *     label = "Assign To",
 *     required = true,
 *     relationship = @Relationship(
 *         targetEntity = User.class,
 *         displayField = "fullName",
 *         display = Relationship.Display.AUTOCOMPLETE,
 *         filter = "active = true AND role = 'AGENT'"
 *     )
 * )
 * UUID assigneeId
 * }</pre>
 *
 * @author Exeris platform Team
 * @version 1.0.0
 * @since 1.0.0
 * @see Action
 * @see UI
 * @see Validation
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.SOURCE)
@Documented
@SuppressWarnings("PMD.ExcessivePublicCount")
public @interface ActionParam {

    // ═══════════════════════════════════════════════════════════════════════════
    // IDENTITY
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Parameter name (used in API request body).
     * <p>Defaults to the Java parameter name if not specified.
     *
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>"reason"</li>
     *   <li>"categoryId"</li>
     *   <li>"effectiveDate"</li>
     * </ul>
     *
     * @return parameter name
     */
    String name() default "";

    /**
     * Display label for the parameter in UI forms.
     * <p>Human-readable label shown in action dialogs.
     *
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>"Rejection Reason"</li>
     *   <li>"New Category"</li>
     *   <li>"Effective Date"</li>
     * </ul>
     *
     * @return display label
     */
    String label();

    /**
     * Description/help text for the parameter.
     * <p>Displayed as tooltip or help text in action dialog.
     *
     * @return parameter description
     */
    String description() default "";

    // ═══════════════════════════════════════════════════════════════════════════
    // VALUE CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Whether this parameter is required.
     * <p>When {@code true}, action cannot be executed without this value.
     *
     * @return true if required
     */
    boolean required() default true;

    /**
     * Default value for the parameter (as string).
     * <p>Value is parsed according to parameter type.
     *
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>String: "Pending"</li>
     *   <li>Number: "0"</li>
     *   <li>Boolean: "true"</li>
     *   <li>Date: "2025-01-01"</li>
     *   <li>Enum: "MEDIUM"</li>
     * </ul>
     *
     * @return default value as string
     */
    String defaultValue() default "";

    /**
     * Expression to compute default value (SpEL).
     * <p>Evaluated against the entity context.
     *
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>"entity.amount"</li>
     *   <li>"entity.customer.email"</li>
     *   <li>"T(java.time.LocalDate).now()"</li>
     *   <li>"entity.price * 0.1"</li>
     * </ul>
     *
     * @return SpEL expression for default value
     */
    String defaultValueExpression() default "";

    /**
     * Whether this parameter is nullable.
     * <p>When {@code true}, null is a valid value even if required is false.
     *
     * @return true if nullable
     */
    boolean nullable() default false;

    // ═══════════════════════════════════════════════════════════════════════════
    // OPTIONS (FOR SELECT/RADIO)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Static options for select/radio components.
     * <p>Array of option values (used when parameter is not an enum).
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * options = {"LOW", "MEDIUM", "HIGH"}
     * }</pre>
     *
     * @return array of option values
     */
    String[] options() default {};

    /**
     * Display labels for options.
     * <p>Must match length of {@code options} array.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * options = {"LOW", "MEDIUM", "HIGH"},
     * optionLabels = {"Low Priority", "Medium Priority", "High Priority"}
     * }</pre>
     *
     * @return array of option labels
     */
    String[] optionLabels() default {};

    /**
     * Icons for options (Material icon names).
     * <p>Must match length of {@code options} array.
     *
     * @return array of icon names
     */
    String[] optionIcons() default {};

    /**
     * Expression to load options dynamically (SpEL).
     * <p>Returns list of option objects.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * optionsExpression = "@categoryService.findActiveCategories()"
     * }</pre>
     *
     * @return SpEL expression returning options
     */
    String optionsExpression() default "";

    /**
     * Field name for option value (when using dynamic options).
     *
     * @return value field name
     */
    String optionValueField() default "id";

    /**
     * Field name for option label (when using dynamic options).
     *
     * @return label field name
     */
    String optionLabelField() default "name";

    // ═══════════════════════════════════════════════════════════════════════════
    // UI CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * UI component configuration.
     * <p>Specifies how the parameter input should be rendered.
     *
     * @return UI configuration
     */
    UI ui() default @UI;

    /**
     * Placeholder text for input fields.
     *
     * @return placeholder text
     */
    String placeholder() default "";

    /**
     * Help text displayed below the input.
     *
     * @return help text
     */
    String helpText() default "";

    /**
     * Icon to display with the input (Material icon name).
     *
     * @return icon name
     */
    String icon() default "";

    /**
     * Display order within action dialog (lower = first).
     *
     * @return display order
     */
    int order() default 100;

    /**
     * Number of grid columns to span in dialog layout.
     * <p>Standard 12-column grid system.
     *
     * @return grid span (1-12)
     */
    int gridSpan() default 12;

    /**
     * Group name for grouping parameters in dialog.
     * <p>Parameters with same group are displayed together.
     *
     * @return group name
     */
    String group() default "";

    // ═══════════════════════════════════════════════════════════════════════════
    // VALIDATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Validation rules for this parameter.
     *
     * @return validation configuration
     */
    Validation validation() default @Validation;

    // ═══════════════════════════════════════════════════════════════════════════
    // RELATIONSHIP (FOR ENTITY SELECTION)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Relationship configuration for entity selection parameters.
     * <p>Use when parameter is a reference to another entity.
     *
     * @return relationship configuration
     */
    Relationship relationship() default @Relationship(targetEntity = void.class, displayField = "");

    // ═══════════════════════════════════════════════════════════════════════════
    // CONDITIONAL
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Condition for showing this parameter (SpEL).
     * <p>Evaluated against form state and entity.
     *
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>"entity.status == 'ACTIVE'"</li>
     *   <li>"params.type == 'REFUND'"</li>
     *   <li>"entity.amount > 1000"</li>
     * </ul>
     *
     * @return visibility condition
     */
    String visibleWhen() default "";

    /**
     * Condition for enabling this parameter (SpEL).
     * <p>When false, parameter is visible but disabled.
     *
     * @return enabled condition
     */
    String enabledWhen() default "";

    /**
     * Condition for requiring this parameter (SpEL).
     * <p>Dynamic required validation based on other values.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * requiredWhen = "params.immediate == true"
     * }</pre>
     *
     * @return required condition
     */
    String requiredWhen() default "";

    // ═══════════════════════════════════════════════════════════════════════════
    // TYPE HINTS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Data type hint for code generation.
     * <p>Used when Java type is ambiguous.
     *
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>"currency" - Money input</li>
     *   <li>"percentage" - Percentage input</li>
     *   <li>"email" - Email input</li>
     *   <li>"phone" - Phone input</li>
     *   <li>"url" - URL input</li>
     *   <li>"color" - Color picker</li>
     *   <li>"file" - File upload</li>
     * </ul>
     *
     * @return data type hint
     */
    String dataType() default "";

    /**
     * Format pattern for the value.
     * <p>Used for display formatting.
     *
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>"yyyy-MM-dd" - Date format</li>
     *   <li>"#,##0.00" - Number format</li>
     *   <li>"uppercase" - Transform to uppercase</li>
     * </ul>
     *
     * @return format pattern
     */
    String format() default "";

    // ═══════════════════════════════════════════════════════════════════════════
    // ADVANCED
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Whether to mask value in UI (for sensitive data).
     *
     * @return true to mask value
     */
    boolean masked() default false;

    /**
     * Whether to encrypt value in transit/storage.
     *
     * @return true to encrypt
     */
    boolean encrypted() default false;

    /**
     * Maximum file size in bytes (for file uploads).
     *
     * @return max file size
     */
    long maxFileSize() default 10_485_760; // 10MB

    /**
     * Allowed file types (for file uploads).
     * <p>MIME types or extensions.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * allowedFileTypes = {"image/*", "application/pdf", ".docx"}
     * }</pre>
     *
     * @return allowed file types
     */
    String[] allowedFileTypes() default {};

    /**
     * Whether to allow multiple file selection.
     *
     * @return true for multiple files
     */
    boolean multipleFiles() default false;

    /**
     * Transform expression applied before submitting (SpEL).
     * <p>Transform parameter value before sending to backend.
     *
     * @return transform expression
     */
    String transformExpression() default "";
}


