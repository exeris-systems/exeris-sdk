package eu.exeris.sdk.annotation;

import java.lang.annotation.*;

/**
 * Configures relationship display and behavior for foreign key fields in Exeris domain entities.
 * <p>This annotation works in conjunction with {@link Field} to define how related entities
 * are displayed and managed in the UI. It supports:
 * <ul>
 *   <li>Display field configuration (which field to show from related entity)</li>
 *   <li>Cascade operations (delete, update)</li>
 *   <li>Fetch strategy (eager, lazy)</li>
 *   <li>UI component generation (dropdowns, autocomplete, modals)</li>
 *   <li>Relationship type specification (one-to-one, many-to-one, one-to-many, many-to-many)</li>
 * </ul>
 *
 * <h2>Basic Usage (Many-to-One):</h2>
 * {@snippet lang="java" :
 * @Field(
 *     label = "Customer",
 *     searchable = true,
 *     filterable = true
 * )
 * @UI(
 *     componentType = UI.ComponentType.SELECT,
 *     displayInList = true
 * )
 * @Relationship(
 *     targetEntity = Customer.class,
 *     displayField = "name"
 * )
 * @ManyToOne
 * @JoinColumn(name = "customer_id")
 * private Customer customer;
 * }
 *
 * <h2>With Autocomplete (Large Dataset):</h2>
 * {@snippet lang="java" :
 * @Field(
 *     label = "Product",
 *     required = true
 * )
 * @UI(
 *     componentType = UI.ComponentType.AUTOCOMPLETE,
 *     displayInList = true,
 *     displayOrder = 2
 * )
 * @Relationship(
 *     targetEntity = Product.class,
 *     displayField = "name",
 *     searchFields = {"name", "sku", "description"},
 *     fetchType = FetchType.LAZY
 * )
 * @ManyToOne
 * private Product product;
 * }
 *
 * <h2>With Cascade Delete:</h2>
 * {@snippet lang="java" :
 * @Field(label = "Order Items")
 * @Relationship(
 *     targetEntity = OrderItem.class,
 *     mappedBy = "order",
 *     cascadeDelete = true,
 *     fetchType = FetchType.LAZY
 * )
 * @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
 * private List<OrderItem> items = new ArrayList<>();
 * }
 *
 * <h2>Many-to-Many Relationship:</h2>
 * {@snippet lang="java" :
 * @Field(
 *     label = "Tags"
 * )
 * @UI(
 *     componentType = UI.ComponentType.MULTI_SELECT,
 *     displayInDetail = true
 * )
 * @Relationship(
 *     targetEntity = Tag.class,
 *     displayField = "name",
 *     relationshipType = RelationshipType.MANY_TO_MANY
 * )
 * @ManyToMany
 * @JoinTable(
 *     name = "product_tags",
 *     joinColumns = @JoinColumn(name = "product_id"),
 *     inverseJoinColumns = @JoinColumn(name = "tag_id")
 * )
 * private Set<Tag> tags = new HashSet<>();
 * }
 *
 * <h2>Composite Display Field:</h2>
 * {@snippet lang="java" :
 * @Field(label = "Customer")
 * @Relationship(
 *     targetEntity = Customer.class,
 *     displayField = "name",
 *     displayTemplate = "{name} ({email})",  // Show both name and email
 *     searchFields = {"name", "email", "phone"}
 * )
 * @ManyToOne
 * private Customer customer;
 * }
 *
 * <h2>Dependent Relationship:</h2>
 * {@snippet lang="java" :
 * // City dropdown depends on selected Country
 * @Field(label = "Country")
 * @Relationship(
 *     targetEntity = Country.class,
 *     displayField = "name"
 * )
 * @ManyToOne
 * private Country country;
 *
 * @Field(label = "City")
 * @Relationship(
 *     targetEntity = City.class,
 *     displayField = "name",
 *     dependsOn = "country",  // Filter cities by selected country
 *     dependsOnField = "country.id"
 * )
 * @ManyToOne
 * private City city;
 * }
 *
 * <h2>Target design — the UI this would drive:</h2>
 * <p>The SDK generates appropriate UI components based on relationship configuration:
 * <ul>
 *   <li><strong>SELECT</strong> - Small datasets (&lt;100 items)</li>
 *   <li><strong>AUTOCOMPLETE</strong> - Large datasets (100+ items)</li>
 *   <li><strong>MULTI_SELECT</strong> - Many-to-many relationships</li>
 *   <li><strong>MODAL</strong> - Complex entity selection with filters</li>
 * </ul>
 *
 *
 * <p><strong>Status: LIVE</strong> — extracted into {@code RelationshipMetadata} and read
 * by the repository, Flyway and application generators — the foreign key, its constraint
 * and the query path all come from here.
 * @since 0.1
 * @see Field
 * @see UI
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface Relationship {

    /**
     * Target entity class for this relationship.
     * <p>Specifies the related entity type.
     *
     * <p><strong>Example:</strong>
     * {@snippet lang="java" :
     * @Relationship(targetEntity = Customer.class)
     * private Customer customer;
     * }
     *
     * @return target entity class
     */
    Class<?> targetEntity();

    /**
     * Field name from target entity to display in UI.
     * <p>This field value is shown in dropdowns, lists, and autocomplete.
     * Should be a human-readable identifier (e.g., "name", "title", "code").
     *
     * <p><strong>Common patterns:</strong>
     * <ul>
     *   <li>Person entities: "name" or "fullName"</li>
     *   <li>Product entities: "name" or "sku"</li>
     *   <li>Code-based entities: "code" or "identifier"</li>
     * </ul>
     *
     * @return field name to display
     */
    String displayField();

    /**
     * Display template for composite labels.
     * <p>Allows combining multiple fields for display. Use placeholders
     * in curly braces matching target entity field names.
     *
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>{@code "{name} ({email})"} → "John Doe (john@example.com)"</li>
     *   <li>{@code "{code} - {name}"} → "PROD-001 - Widget"</li>
     *   <li>{@code "{firstName} {lastName}"} → "John Doe"</li>
     * </ul>
     *
     * <p>If not specified, only {@code displayField} is shown.
     *
     * @return display template with placeholders
     */
    String displayTemplate() default "";

    /**
     * Fields to search when filtering/autocompleting.
     * <p>Specifies which target entity fields should be searchable.
     * Used in autocomplete components and search filters.
     *
     * <p><strong>Example:</strong>
     * {@snippet lang="java" :
     * @Relationship(
     *     targetEntity = Customer.class,
     *     displayField = "name",
     *     searchFields = {"name", "email", "phone", "company"}
     * )
     * }
     *
     * @return array of searchable field names
     */
    String[] searchFields() default {};

    /**
     * Type of relationship.
     * <p>Specifies cardinality and helps SDK generate appropriate UI and queries.
     *
     * @return relationship type
     */
    RelationshipType relationshipType() default RelationshipType.MANY_TO_ONE;

    /**
     * Fetch strategy for loading related entities.
     * <p>Controls when related data is loaded:
     * <ul>
     *   <li><strong>EAGER</strong> - Load immediately with parent entity</li>
     *   <li><strong>LAZY</strong> - Load on-demand when accessed</li>
     * </ul>
     *
     * <p><strong>Guidelines:</strong>
     * <ul>
     *   <li>Use EAGER for: Small related data, always displayed fields</li>
     *   <li>Use LAZY for: Large collections, optional related data</li>
     * </ul>
     *
     * @return fetch type
     */
    FetchType fetchType() default FetchType.LAZY;

    /**
     * Whether to cascade delete operations to related entities.
     * <p>When {@code true}, deleting parent entity also deletes related entities.
     *
     * <p><strong>Use with caution!</strong> Only enable for:
     * <ul>
     *   <li>Dependent entities (order → order items)</li>
     *   <li>Owned relationships where child cannot exist without parent</li>
     * </ul>
     *
     * <p><strong>Do NOT enable for:</strong>
     * <ul>
     *   <li>Shared entities (product → category)</li>
     *   <li>Independent entities (order → customer)</li>
     * </ul>
     *
     * @return true to cascade deletes
     */
    boolean cascadeDelete() default false;

    /**
     * Whether to cascade update operations to related entities.
     * <p>When {@code true}, updating parent entity may update related entities.
     *
     * @return true to cascade updates
     */
    boolean cascadeUpdate() default false;

    /**
     * Field name in target entity that references this entity (for bidirectional relationships).
     * <p>Used in one-to-many and many-to-many relationships to establish
     * bidirectional mapping.
     *
     * <p><strong>Example:</strong>
     * {@snippet lang="java" :
     * // In Order.java
     * @Relationship(
     *     targetEntity = OrderItem.class,
     *     mappedBy = "order"
     * )
     * @OneToMany(mappedBy = "order")
     * private List<OrderItem> items;
     *
     * // In OrderItem.java
     * @ManyToOne
     * @JoinColumn(name = "order_id")
     * private Order order;
     * }
     *
     * @return field name in target entity
     */
    String mappedBy() default "";

    /**
     * Name of the field this relationship depends on.
     * <p>Used for dependent dropdowns where options are filtered based
     * on another field's value.
     *
     * <p><strong>Example:</strong>
     * {@snippet lang="java" :
     * @Relationship(
     *     targetEntity = City.class,
     *     displayField = "name",
     *     dependsOn = "country",
     *     dependsOnField = "country.id"
     * )
     * private City city;
     * }
     *
     * @return field name this depends on
     */
    String dependsOn() default "";

    /**
     * Field path in target entity to match against dependent field value.
     * <p>Specifies which field in target entity should match the dependent field value.
     *
     * <p><strong>Example:</strong> For city filtering by country:
     * {@snippet lang="java" :
     * dependsOn = "country",          // Field in current entity
     * dependsOnField = "country.id"   // Field in City entity to filter by
     * }
     *
     * @return field path for filtering
     */
    String dependsOnField() default "";

    /**
     * Whether to allow creating new related entities inline.
     * <p>When {@code true}, generates a "+" button to create new related
     * entities without leaving the current form.
     *
     * <p>Useful for:
     * <ul>
     *   <li>Creating customers while creating orders</li>
     *   <li>Adding products while creating invoices</li>
     *   <li>Quick category creation</li>
     * </ul>
     *
     * @return true to allow inline creation
     */
    boolean allowInlineCreate() default false;

    /**
     * Whether to allow editing related entities inline.
     * <p>When {@code true}, generates an "edit" button to modify related
     * entities in a modal dialog.
     *
     * @return true to allow inline editing
     */
    boolean allowInlineEdit() default false;

    /**
     * Sort order for related entities in dropdowns.
     * <p>Specifies field(s) to sort by, with optional direction.
     *
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>{@code "name"} - Sort by name ascending</li>
     *   <li>{@code "name ASC"} - Explicit ascending</li>
     *   <li>{@code "priority DESC, name ASC"} - Multiple fields</li>
     * </ul>
     *
     * @return sort expression
     */
    String orderBy() default "";

    /**
     * Additional filter condition for loading related entities.
     * <p>Use SQL WHERE clause syntax to filter available options.
     *
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>{@code "active = true"} - Only active entities</li>
     *   <li>{@code "status = 'APPROVED'"} - Only approved entities</li>
     *   <li>{@code "deleted_at IS NULL"} - Exclude soft-deleted</li>
     * </ul>
     *
     * @return filter condition
     */
    String filter() default "";

    /**
     * Maximum number of items to display in dropdown before switching to autocomplete.
     * <p>Default: 100
     * <ul>
     *   <li>&lt;= threshold: Use SELECT dropdown</li>
     *   <li>&gt; threshold: Use AUTOCOMPLETE component</li>
     * </ul>
     *
     * @return item threshold
     */
    int autocompleteThreshold() default 100;

    /**
     * Whether this relationship is required (cannot be null).
     * <p>When {@code true}:
     * <ul>
     *   <li>Form validation requires selection</li>
     *   <li>Database foreign key is NOT NULL</li>
     *   <li>API marks field as required</li>
     * </ul>
     *
     * @return true if relationship is required
     */
    boolean required() default false;

    /**
     * Icon to display next to relationship field.
     * <p>Material icon name for visual identification.
     *
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>"person" - For customer/user relationships</li>
     *   <li>"business" - For company relationships</li>
     *   <li>"category" - For category relationships</li>
     *   <li>"inventory" - For product relationships</li>
     * </ul>
     *
     * @return material icon name
     */
    String icon() default "";

    /**
     * Display mode for the relationship field.
     * <p>Determines which UI component is used to display and select related entities.
     *
     * @return display mode
     */
    Display display() default Display.AUTO;

    /**
     * Alias for {@link #allowInlineCreate()}.
     * <p>Provided for compatibility with task specification.
     *
     * @return true to allow creating new related entities
     */
    boolean allowCreate() default false;

    /**
     * Whether the relationship field is searchable.
     * <p>Alias combining {@link #searchFields()} behavior.
     * When true, enables search on primary display field.
     *
     * @return true if searchable
     */
    boolean searchable() default false;

    /**
     * Alias for {@link #displayField()}.
     * <p>Provided for compatibility with task specification.
     *
     * @return field name to display
     */
    String labelField() default "";

    /**
     * Additional fields to display alongside primary display field.
     * <p>Used in TABLE and MODAL display modes to show multiple columns.
     *
     * <p><strong>Example:</strong>
     * {@snippet lang="java" :
     * @Relationship(
     *     targetEntity = Customer.class,
     *     displayField = "name",
     *     displayFields = {"email", "phone", "company"},
     *     display = Display.TABLE
     * )
     * }
     *
     * @return array of additional field names to display
     */
    String[] displayFields() default {};

    /**
     * Display mode enum for relationships.
     */
    enum Display {
        /** Auto-select based on dataset size and relationship type. */
        AUTO,

        /** Dropdown select (best for small datasets &lt; 100 items). */
        SELECT,

        /** Autocomplete/typeahead (best for large datasets). */
        AUTOCOMPLETE,

        /** Radio button group (best for 2-5 options). */
        RADIO,

        /** Checkbox group (for multi-select with 2-10 options). */
        CHECKBOX,

        /** Data table with selection (for many-to-many or large datasets). */
        TABLE,

        /** Modal dialog with search and filters (for complex selection). */
        MODAL
    }

    /**
     * Relationship type enum.
     */
    enum RelationshipType {
        /** Many entities reference one entity (e.g., many orders → one customer). */
        MANY_TO_ONE,

        /** One entity references one entity (e.g., user → profile). */
        ONE_TO_ONE,

        /** One entity has many related entities (e.g., order → order items). */
        ONE_TO_MANY,

        /** Many entities reference many entities (e.g., products ↔ tags). */
        MANY_TO_MANY
    }

    /**
     * Fetch type enum.
     */
    enum FetchType {
        /** Load related data immediately with parent entity. */
        EAGER,

        /** Load related data on-demand when accessed. */
        LAZY
    }
}


