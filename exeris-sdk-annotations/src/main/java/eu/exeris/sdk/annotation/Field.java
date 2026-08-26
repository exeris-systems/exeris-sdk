package eu.exeris.sdk.annotation;

import java.lang.annotation.*;

/**
 * Configures field-level metadata for Exeris domain entities.
 * <p>This annotation provides comprehensive field configuration including:
 * <ul>
 *   <li>Display labels and descriptions</li>
 *   <li>UI component configuration via a sibling {@link UI} annotation</li>
 *   <li>Validation rules via a sibling {@link Validation} annotation</li>
 *   <li>Search, filter, and sort capabilities</li>
 *   <li>Visibility control across different views (list, detail, create, update)</li>
 *   <li>Display ordering</li>
 * </ul>
 *
 * <h2>Sibling form, not nested values</h2>
 * <p>{@link UI} and {@link Validation} are declared as <strong>separate
 * annotations applied to the same field</strong>, next to {@code @Field} — not as
 * the {@link #ui()} / {@link #validation()} nested values on this annotation.
 * Both readers resolve them that way and only that way: the build-time processor
 * walks the field's own annotation mirrors ({@code ExerisDomainProcessor:1225},
 * {@code findAnnotation} at {@code :1871-1878}) and the {@code -io} reader calls
 * {@code getAnnotationByName("Validation")} on the field declaration
 * ({@code SourceModelReader:951}). Neither ever inspects the nested values, so
 * writing {@code @Field(validation = @Validation(...))} is legal Java that
 * contributes nothing and warns about nothing. Every example below uses the
 * sibling form.
 *
 * <h2>Open-Core status — partially live</h2>
 * <p>Of the 29 attributes declared here, 12 reach a code generator, 2 stop at the
 * AST, and 15 are read by nobody. Declaring an attribute from the last two groups
 * records author intent and changes no generated artifact.
 * <ul>
 *   <li><strong>Live</strong> — extracted and consumed by a reachable generator:
 *       {@link #label()}, {@link #description()}, {@link #required()},
 *       {@link #unique()}, {@link #searchable()}, {@link #sortable()},
 *       {@link #filterable()}, {@link #readOnly()}, {@link #dataType()},
 *       {@link #computed()}, {@link #computedFrom()}, and {@link #inCreate()}
 *       (Angular/TS side only — the generated Java handler does not filter by it).</li>
 *   <li><strong>Extracted, no consumer</strong> — {@link #indexed()} and
 *       {@link #inUpdate()} reach {@code exeris-metadata/<entity>.json} and stop
 *       there. Generated indexes come from {@link #searchable()} and
 *       {@link #unique()} only, so {@code indexed = true} creates no index; the
 *       generated update DTO is not filtered by {@code inUpdate}.</li>
 *   <li><strong>Not extracted</strong> — {@link #labelKey()},
 *       {@link #descriptionKey()}, {@link #inList()}, {@link #inDetail()},
 *       {@link #order()}, {@link #ui()}, {@link #validation()},
 *       {@link #defaultValue()}, {@link #cssClass()}, {@link #group()},
 *       {@link #sensitive()}, {@link #encrypted()}, {@link #maskPattern()},
 *       {@link #writeOnly()} and {@link #compositeUnique()}. No processor reads
 *       them into the AST and no generator consumes them. The four
 *       security-flavoured entries in that list enforce nothing — see their
 *       individual notes.</li>
 * </ul>
 * <p>Nothing warns when a reserved attribute is set: {@code -Aexeris.strict}
 * audits only attributes that are extracted-but-unread, so it is structurally
 * silent on the whole third group.
 *
 * <h2>Basic Usage:</h2>
 * <pre>{@code
 * @Field(
 *     label = "Order Number",
 *     description = "Unique identifier for the order",
 *     required = true,
 *     searchable = true,
 *     sortable = true,
 *     inList = true,
 *     order = 1
 * )
 * private String orderNumber;
 * }</pre>
 *
 * <h2>With UI Configuration:</h2>
 * <pre>{@code
 * @Field(
 *     label = "Total Amount",
 *     description = "Total order amount in USD",
 *     required = true,
 *     ui = @UI(
 *         componentType = UI.ComponentType.NUMBER_INPUT,
 *         format = "currency",
 *         displayInList = true,
 *         displayOrder = 3
 *     )
 * )
 * private BigDecimal totalAmount;
 * }</pre>
 *
 * <h2>With Validation:</h2>
 * <pre>{@code
 * @Field(
 *     label = "Email Address",
 *     description = "Customer's email for notifications",
 *     required = true,
 *     validation = @Validation(
 *         email = true,
 *         message = "Please enter a valid email address"
 *     ),
 *     ui = @UI(
 *         componentType = UI.ComponentType.EMAIL,
 *         placeholder = "customer@example.com"
 *     )
 * )
 * private String email;
 * }</pre>
 *
 * <h2>Search and Filter Configuration:</h2>
 * <pre>{@code
 * @Field(
 *     label = "Status",
 *     searchable = false,  // Don't include in text search
 *     filterable = true,   // But allow filtering by dropdown
 *     sortable = true,
 *     ui = @UI(
 *         componentType = UI.ComponentType.SELECT,
 *         displayInList = true
 *     )
 * )
 * @Enumerated(EnumType.STRING)
 * private OrderStatus status;
 * }</pre>
 *
 * <h2>Visibility Control:</h2>
 * <pre>{@code
 * @Field(
 *     label = "Created At",
 *     inList = true,      // Show in list view
 *     inDetail = true,    // Show in detail view
 *     inCreate = false,   // Hide in create form (auto-generated)
 *     inUpdate = false,   // Hide in update form (immutable)
 *     sortable = true,
 *     ui = @UI(
 *         componentType = UI.ComponentType.DATETIME_PICKER,
 *         format = "date:long"
 *     )
 * )
 * private LocalDateTime createdAt;
 * }</pre>
 *
 * <h2>Display Ordering:</h2>
 * <p>The {@code order} attribute controls field display position across all views.
 * Lower numbers appear first:
 * <pre>{@code
 * @Field(label = "Order Number", order = 1)  // First
 * private String orderNumber;
 *
 * @Field(label = "Customer", order = 2)      // Second
 * private String customerName;
 *
 * @Field(label = "Total", order = 3)         // Third
 * private BigDecimal total;
 * }</pre>
 *
 * <h2>Integration with Relationships:</h2>
 * <pre>{@code
 * @Field(
 *     label = "Customer",
 *     description = "Order customer",
 *     required = true,
 *     searchable = true,  // Search by customer name
 *     filterable = true,  // Filter by customer
 *     ui = @UI(
 *         componentType = UI.ComponentType.AUTOCOMPLETE,
 *         displayInList = true,
 *         displayOrder = 2
 *     )
 * )
 * @Relationship(
 *     targetEntity = Customer.class,
 *     displayField = "name"
 * )
 * private UUID customerId;
 * }</pre>
 *
 * <h2>Best Practices:</h2>
 * <ul>
 *   <li>Always provide meaningful {@code label} values</li>
 *   <li>Use {@code description} for additional context</li>
 *   <li>Set {@code order} explicitly to control field positioning</li>
 *   <li>Mark searchable fields appropriately (text fields, enums)</li>
 *   <li>Enable filtering on fields used for data segmentation</li>
 *   <li>Control visibility with {@code inList}, {@code inDetail}, {@code inCreate}, {@code inUpdate}</li>
 *   <li>Combine with {@code @UI} for precise UI control</li>
 *   <li>Combine with {@code @Validation} for data integrity</li>
 * </ul>
 *
 * @author Exeris SDK Team
 * @version 0.1.0
 * @since 0.1.0
 * @see UI
 * @see Validation
 * @see Relationship
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
@Documented
@SuppressWarnings("PMD.ShortMethodName")
public @interface Field {

    /**
     * Display label for the field.
     * <p>This label is used across all UI views (list, detail, forms).
     * Should be human-readable and localized if necessary.
     *
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>"Order Number"</li>
     *   <li>"Customer Name"</li>
     *   <li>"Total Amount"</li>
     *   <li>"Email Address"</li>
     * </ul>
     *
     * @return the field label
     */
    String label();

    /**
     * Optional i18n message key for the field label.
     * <p>When non-empty, downstream UI generation resolves this key against the
     * application's message bundle and uses {@link #label()} as the default /
     * fallback text (shown at design time and when a translation is missing).
     * When empty, {@link #label()} is used verbatim.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * @Field(label = "Order Number", labelKey = "order.field.orderNumber.label")
     * }</pre>
     *
     * @return the message key, or {@code ""} to use the literal label
     */
    String labelKey() default "";

    /**
     * Detailed description of the field.
     * <p>Displayed as help text or tooltip in forms.
     * Use this to provide additional context or usage guidance.
     *
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>"Unique identifier automatically generated for each order"</li>
     *   <li>"Customer's primary email address for order notifications"</li>
     *   <li>"Total amount including taxes and shipping"</li>
     * </ul>
     *
     * @return the field description
     */
    String description() default "";

    /**
     * Optional i18n message key for the field description.
     * <p>Same resolution contract as {@link #labelKey()}: when non-empty it is
     * the bundle key and {@link #description()} is the fallback text; when empty
     * {@link #description()} is used verbatim.
     *
     * @return the message key, or {@code ""} to use the literal description
     */
    String descriptionKey() default "";

    /**
     * Whether this field is required (cannot be null or empty).
     * <p>When {@code true}:
     * <ul>
     *   <li>Field is marked as required in create/update forms</li>
     *   <li>Backend validation enforces non-null constraint</li>
     *   <li>Frontend validation prevents submission if empty</li>
     *   <li>API documentation marks field as required</li>
     * </ul>
     *
     * @return true if field is required
     */
    boolean required() default false;

    /**
     * Whether this field is included in text search.
     * <p>When {@code true}, the field is added to the global search index
     * and included in search queries. Typically enabled for:
     * <ul>
     *   <li>Text fields (names, descriptions, codes)</li>
     *   <li>Enum values (statuses, types)</li>
     *   <li>Email addresses, phone numbers</li>
     * </ul>
     *
     * <p><strong>Not recommended for:</strong>
     * <ul>
     *   <li>Numeric IDs</li>
     *   <li>Timestamps</li>
     *   <li>Binary data</li>
     *   <li>Large text fields (use full-text search instead)</li>
     * </ul>
     *
     * @return true if field is searchable
     */
    boolean searchable() default false;

    /**
     * Whether this field can be used for filtering.
     * <p>When {@code true}, generates filter controls in list views:
     * <ul>
     *   <li>Text fields → Text input filter</li>
     *   <li>Enums → Dropdown filter</li>
     *   <li>Booleans → Checkbox filter</li>
     *   <li>Dates → Date range filter</li>
     *   <li>Numbers → Range filter</li>
     * </ul>
     *
     * <p>Best for fields used to segment data:
     * <ul>
     *   <li>Status fields</li>
     *   <li>Category fields</li>
     *   <li>Date ranges</li>
     *   <li>Boolean flags</li>
     * </ul>
     *
     * @return true if field is filterable
     */
    boolean filterable() default false;

    /**
     * Whether this field can be sorted.
     * <p>When {@code true}:
     * <ul>
     *   <li>Column header becomes clickable in list views</li>
     *   <li>Backend adds sorting capability to query</li>
     *   <li>Database index should be added for performance</li>
     * </ul>
     *
     * <p>Recommended for:
     * <ul>
     *   <li>Timestamps (created_at, updated_at)</li>
     *   <li>Names and titles</li>
     *   <li>Numeric values (amounts, quantities)</li>
     *   <li>Status/priority fields</li>
     * </ul>
     *
     * @return true if field is sortable
     */
    boolean sortable() default false;

    /**
     * Whether to display this field in list/table views.
     * <p>When {@code true}, field appears as a column in data tables.
     *
     * <p><strong>Guidelines:</strong>
     * <ul>
     *   <li>Show 4-7 most important fields</li>
     *   <li>Include identifiers (order number, customer name)</li>
     *   <li>Include status/state fields</li>
     *   <li>Include key metrics (totals, counts)</li>
     *   <li>Avoid large text fields</li>
     * </ul>
     *
     * @return true if field appears in list view
     */
    boolean inList() default false;

    /**
     * Whether to display this field in detail views.
     * <p>When {@code true}, field appears in the entity detail page.
     *
     * <p>Typically {@code true} for most fields except:
     * <ul>
     *   <li>Internal system fields</li>
     *   <li>Redundant calculated fields</li>
     *   <li>Fields only relevant in specific contexts</li>
     * </ul>
     *
     * @return true if field appears in detail view
     */
    boolean inDetail() default true;

    /**
     * Whether this field is editable in create forms.
     * <p>When {@code false}, field is hidden or read-only in create forms.
     *
     * <p>Set to {@code false} for:
     * <ul>
     *   <li>Auto-generated fields (IDs, timestamps)</li>
     *   <li>System-managed fields</li>
     *   <li>Calculated fields</li>
     * </ul>
     *
     * @return true if field is editable in create forms
     */
    boolean inCreate() default true;

    /**
     * Whether this field is editable in update forms.
     * <p>When {@code false}, field is read-only in update forms.
     *
     * <p>Set to {@code false} for:
     * <ul>
     *   <li>Immutable fields (order number, created_at)</li>
     *   <li>System-managed fields</li>
     *   <li>Fields that cannot be changed after creation</li>
     * </ul>
     *
     * @return true if field is editable in update forms
     */
    boolean inUpdate() default true;

    /**
     * Display order for this field (lower numbers appear first).
     * <p>Controls field positioning across all views. Use consistent
     * ordering across list, detail, and form views.
     *
     * <p><strong>Recommended ordering:</strong>
     * <ul>
     *   <li>1-10: Primary identifiers (order number, name)</li>
     *   <li>11-20: Key business fields (customer, status, date)</li>
     *   <li>21-30: Secondary details (amounts, quantities)</li>
     *   <li>31-40: Metadata (created_at, updated_at)</li>
     *   <li>100+: Low-priority fields</li>
     * </ul>
     *
     * <p>Default value of 100 places fields at the end if not specified.
     *
     * @return display order (1-based, lower = first)
     */
    int order() default 100;

    /**
     * UI configuration for this field.
     * <p>Specifies UI component type, display format, and behavior.
     * If not specified, SDK auto-detects component type based on field type.
     *
     * <p><strong>Auto-detection rules:</strong>
     * <ul>
     *   <li>String → TEXT_INPUT</li>
     *   <li>Integer/Long/BigDecimal → NUMBER_INPUT</li>
     *   <li>Boolean → CHECKBOX</li>
     *   <li>LocalDate → DATE_PICKER</li>
     *   <li>LocalDateTime/Instant → DATETIME_PICKER</li>
     *   <li>Enum → SELECT</li>
     * </ul>
     *
     * <p>Override for custom behavior:
     * <pre>{@code
     * @Field(
     *     label = "Description",
     *     ui = @UI(componentType = UI.ComponentType.TEXT_AREA)
     * )
     * private String description;
     * }</pre>
     *
     * @return UI configuration
     */
    UI ui() default @UI;

    /**
     * Validation rules for this field.
     * <p>Defines constraints applied in both backend and frontend.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * @Field(
     *     label = "Email",
     *     validation = @Validation(
     *         required = true,
     *         email = true,
     *         message = "Valid email required"
     *     )
     * )
     * private String email;
     * }</pre>
     *
     * @return validation configuration
     */
    Validation validation() default @Validation;

    /**
     * Data type hint for code generation.
     * <p>Used when Java type is not sufficient to determine behavior.
     *
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>"currency" - Format as money</li>
     *   <li>"percentage" - Display with % symbol</li>
     *   <li>"url" - Render as clickable link</li>
     *   <li>"email" - Render as mailto link</li>
     *   <li>"phone" - Format phone number</li>
     *   <li>"json" - Syntax highlight JSON</li>
     * </ul>
     *
     * <p>Modelled in the AST as {@code FieldMetadata.dataType} (since 0.6.0) and
     * propagated end-to-end: the build-time processor and the {@code -io} reader
     * extract it in lock-step (ADR-042), and the Angular emitter renders it
     * ({@code currency}/{@code percent} pipes, {@code url} as a link); see the
     * {@code dataType} vs {@code format} note in the
     * {@code eu.exeris.sdk.sourcemodel.ast} package-info.
     *
     * @return data type hint
     */
    String dataType() default "";

    /**
     * Default value for this field (as string).
     * <p>Used in create forms to pre-populate the field.
     * Value is parsed according to field type.
     *
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>String: "Pending"</li>
     *   <li>Number: "0"</li>
     *   <li>Boolean: "false"</li>
     *   <li>Date: "2025-01-01"</li>
     * </ul>
     *
     * @return default value as string
     */
    String defaultValue() default "";

    /**
     * Whether this field is read-only (never editable).
     * <p>When {@code true}:
     * <ul>
     *   <li>Field is displayed but never editable</li>
     *   <li>Useful for calculated or system-managed fields</li>
     *   <li>Different from {@code inCreate=false, inUpdate=false} which hides field</li>
     * </ul>
     *
     * @return true if field is always read-only
     */
    boolean readOnly() default false;

    /**
     * CSS class names to apply to field container.
     * <p>Space-separated list of CSS classes for custom styling.
     *
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>"col-md-6" - Half-width field</li>
     *   <li>"text-danger" - Red text</li>
     *   <li>"font-weight-bold" - Bold text</li>
     * </ul>
     *
     * @return CSS class names
     */
    String cssClass() default "";

    /**
     * Group name for field grouping in forms.
     * <p>Fields with the same group name are displayed together.
     * Useful for organizing complex forms into logical sections.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * @Field(label = "Street", group = "Address")
     * private String street;
     *
     * @Field(label = "City", group = "Address")
     * private String city;
     *
     * @Field(label = "Country", group = "Address")
     * private String country;
     * }</pre>
     *
     * @return group name
     */
    String group() default "";


    // ═══════════════════════════════════════════════════════════════════════════
    // SECURITY & PRIVACY
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Whether this field contains sensitive/PII data.
     * <p>When {@code true}:
     * <ul>
     *   <li>Field is masked in logs (e.g., "email" → "j***@example.com")</li>
     *   <li>Included in GDPR data export</li>
     *   <li>Subject to data retention policies</li>
     *   <li>Enhanced audit logging for access</li>
     * </ul>
     *
     * <p><strong>Examples of sensitive fields:</strong>
     * <ul>
     *   <li>Email addresses</li>
     *   <li>Phone numbers</li>
     *   <li>Social security numbers</li>
     *   <li>Credit card numbers</li>
     *   <li>Personal addresses</li>
     * </ul>
     *
     * @return true if field contains sensitive data
     */
    boolean sensitive() default false;

    /**
     * Whether this field should be encrypted at rest.
     * <p>When {@code true}:
     * <ul>
     *   <li>Value is encrypted before storing in database</li>
     *   <li>Value is decrypted when reading</li>
     *   <li>Uses configured encryption provider</li>
     *   <li>Key rotation supported</li>
     * </ul>
     *
     * <p>Requires entity-level {@code @ExerisDomain(sensitive=true)}.
     *
     * @return true if field should be encrypted
     */
    boolean encrypted() default false;

    /**
     * Masking pattern for logs and non-privileged views.
     * <p>How to mask the value when displaying to non-privileged users
     * or in logs.
     *
     * <p><strong>Patterns:</strong>
     * <ul>
     *   <li>"email" - j***@example.com</li>
     *   <li>"phone" - +1 ***-***-1234</li>
     *   <li>"card" - ****-****-****-1234</li>
     *   <li>"full" - ********</li>
     *   <li>"partial:3" - abc***</li>
     *   <li>Custom regex: "(?&lt;=.{3}).(?=.{4})" → "abc****xyz"</li>
     * </ul>
     *
     * @return masking pattern
     */
    String maskPattern() default "";

    /**
     * Whether this field should be excluded from API responses.
     * <p>When {@code true}:
     * <ul>
     *   <li>Field is never included in REST/GraphQL responses</li>
     *   <li>Field is still stored and can be used internally</li>
     *   <li>Useful for internal flags, hashes, tokens</li>
     * </ul>
     *
     * @return true if field should be excluded from responses
     */
    boolean writeOnly() default false;

    // ═══════════════════════════════════════════════════════════════════════════
    // INDEXING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Whether to create a database index on this field.
     * <p>Recommended for fields that are frequently:
     * <ul>
     *   <li>Used in WHERE clauses (filterable)</li>
     *   <li>Used in ORDER BY (sortable)</li>
     *   <li>Used in JOINs (relationships)</li>
     * </ul>
     *
     * @return true if database index should be created
     */
    boolean indexed() default false;

    /**
     * Whether this field must have unique values.
     * <p>When {@code true}:
     * <ul>
     *   <li>Creates unique constraint in database</li>
     *   <li>Validates uniqueness before insert/update</li>
     *   <li>Returns 409 Conflict on duplicate</li>
     * </ul>
     *
     * @return true if field must be unique
     */
    boolean unique() default false;

    /**
     * Composite unique constraint name.
     * <p>Fields with same composite name form a composite unique constraint.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * @Field(label = "Code", compositeUnique = "tenant_code")
     * private String code;
     *
     * @Field(label = "Tenant", compositeUnique = "tenant_code")
     * private UUID tenantId;
     * // Creates: UNIQUE(code, tenant_id)
     * }</pre>
     *
     * @return composite unique constraint name
     */
    String compositeUnique() default "";

    // •••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••
    // COMPUTED FIELDS
    // •••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••

    /**
     * Whether this field is computed from other fields.
     * <p>When {@code true}:
     * <ul>
     *   <li>Field is read-only (automatically set via computeXxx() method)</li>
     *   <li>Frontend generates Effect to listen to dependent fields</li>
     *   <li>Generator creates stub computeXxx() method to implement</li>
     *   <li>Field can be displayed in forms (disabled/readonly UI)</li>
     * </ul>
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * @Field(
     *     label = "Max Users",
     *     computed = true,
     *     computedFrom = {"plan"},
     *     inCreate = true,
     *     readOnly = true
     * )
     * private Integer maxUsers;
     * }</pre>
     *
     * @return true if field is computed
     */
    boolean computed() default false;

    /**
     * List of field names that this computed field depends on.
     * <p>Used only when {@code computed = true}.
     * <p>Frontend Effect will listen to changes in these fields and recompute.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * @Field(
     *     label = "Total",
     *     computed = true,
     *     computedFrom = {"unitPrice", "quantity", "discountPercent"}
     * )
     * private BigDecimal total;
     * }</pre>
     *
     * @return array of dependency field names
     */
    String[] computedFrom() default {};
}


