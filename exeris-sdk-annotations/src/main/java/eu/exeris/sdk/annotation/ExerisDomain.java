package eu.exeris.sdk.annotation;

import java.lang.annotation.*;

/**
 * Marks a class as an Exeris domain entity (aggregate root).
 * <p>This is the primary annotation that enables code generation for:
 * <ul>
 *   <li>REST API controllers and endpoints</li>
 *   <li>GraphQL schema and resolvers</li>
 *   <li>Service layer with business logic</li>
 *   <li>Repository with dynamic queries</li>
 *   <li>DTOs and mappers</li>
 *   <li>Angular components and services</li>
 * </ul>
 *
 * <h2>Minimal Example:</h2>
 * <pre>{@code
 * @ExerisDomain(module = "sales", path = "/orders")
 * public class Order {
 *     @Field(label = "ID")
 *     private UUID id;
 *
 *     @Field(label = "Status")
 *     private OrderStatus status;
 * }
 * }</pre>
 *
 * <h2>Full Example with System Fields:</h2>
 * <pre>{@code
 * @ExerisDomain(
 *     module = "sales",
 *     path = "/orders",
 *     tenantScoped = true,
 *     softDelete = true,
 *     audited = true,
 *     versioned = true
 * )
 * public class Order {
 *     @Field(label = "ID")
 *     @PrimaryKey
 *     private UUID id;
 *
 *     @Field(label = "Organization")
 *     @TenantId
 *     private UUID organizationId;
 *
 *     @Field(label = "Archived")
 *     @SoftDelete
 *     private boolean archived;
 *
 *     @Field(label = "Version")
 *     @Version
 *     private Long version;
 *
 *     @Field(label = "Created At")
 *     @AuditCreatedAt
 *     private Instant createdAt;
 * }
 * }</pre>
 *
 * @author Exeris SDK Team
 * @version 2.0.0
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface ExerisDomain {

    // ═══════════════════════════════════════════════════════════════════════════
    // IDENTITY
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Module name for grouping related entities.
     * <p>Used for:
     * <ul>
     *   <li>Package structure generation</li>
     *   <li>API path prefixing</li>
     *   <li>Kafka topic naming</li>
     *   <li>Cache region naming</li>
     * </ul>
     *
     * @return module name (e.g., "sales", "inventory", "crm")
     */
    String module();

    /**
     * Base API path for this entity.
     * <p>Must start with "/".
     *
     * @return API path (e.g., "/orders", "/customers")
     */
    String path();

    /**
     * Aggregate root name.
     * <p>Defaults to entity class name if empty.
     *
     * @return aggregate name
     */
    String aggregate() default "";

    /**
     * Human-readable description for documentation.
     *
     * @return description
     */
    String description() default "";

    /**
     * API version.
     * <p>Used in URL path: /api/{version}/{path}
     *
     * @return API version (e.g., "v1", "v2")
     */
    String apiVersion() default "v1";

    /**
     * Tags for OpenAPI/AsyncAPI grouping.
     *
     * @return array of tags
     */
    String[] tags() default {};

    // ═══════════════════════════════════════════════════════════════════════════
    // API CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Whether to generate REST API endpoints.
     *
     * @return true to generate REST API
     */
    boolean restApi() default true;

    /**
     * Whether to generate GraphQL schema and resolvers.
     *
     * @return true to generate GraphQL API
     */
    boolean graphqlApi() default false;

    /**
     * Whether to generate real-time streaming endpoints (SSE/WebTransport).
     *
     * <p><strong>Open-Core status:</strong> server-push is not wired in
     * Open-Core — there is no streaming HTTP affordance behind it yet (a
     * kernel-owned SPI). Setting this is inert until that affordance lands;
     * the attribute gains meaning once it does. See also
     * {@link Action#streaming()} / {@link Action#realTimeUpdates()}.
     *
     * @return true to generate real-time API
     */
    boolean realTimeApi() default false;

    /**
     * Whether to generate internal HTTP client for service-to-service calls.
     *
     * @return true to generate internal client
     */
    boolean internalClient() default false;

    // ═══════════════════════════════════════════════════════════════════════════
    // DATA MANAGEMENT FLAGS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Whether entity is scoped to tenants (multi-tenancy).
     * <p>When true, requires either:
     * <ul>
     *   <li>A field annotated with {@code @TenantId}, OR</li>
     *   <li>{@code tenantIdField} to be specified</li>
     * </ul>
     *
     * @return true for multi-tenant isolation
     */
    boolean tenantScoped() default false;

    /**
     * Whether to use soft delete instead of hard delete.
     * <p>When true, requires either:
     * <ul>
     *   <li>A field annotated with {@code @SoftDelete}, OR</li>
     *   <li>{@code softDeleteField} to be specified</li>
     * </ul>
     *
     * @return true for soft delete
     */
    boolean softDelete() default false;

    /**
     * Whether to enable audit trail (created/updated timestamps and users).
     * <p>When true, looks for fields annotated with:
     * <ul>
     *   <li>{@code @AuditCreatedAt}, {@code @AuditCreatedBy}</li>
     *   <li>{@code @AuditUpdatedAt}, {@code @AuditUpdatedBy}</li>
     * </ul>
     * Or uses field name overrides.
     *
     * @return true for audit trail
     */
    boolean audited() default false;

    /**
     * Whether to enable optimistic locking with version field.
     * <p>When true, requires either:
     * <ul>
     *   <li>A field annotated with {@code @Version}, OR</li>
     *   <li>{@code versionField} to be specified</li>
     * </ul>
     *
     * @return true for optimistic locking
     */
    boolean versioned() default false;

    // ═══════════════════════════════════════════════════════════════════════════
    // SECURITY
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Default roles required to access this entity's API.
     *
     * @return array of role names
     */
    String[] roles() default {};

    /**
     * Default permissions required to access this entity's API.
     *
     * @return array of permission names
     */
    String[] permissions() default {};

    /**
     * Whether this entity contains sensitive/PII data.
     * <p>When true:
     * <ul>
     *   <li>Additional logging restrictions apply</li>
     *   <li>Encryption at rest may be enforced</li>
     *   <li>Export restrictions apply</li>
     * </ul>
     *
     * @return true if contains sensitive data
     */
    boolean sensitive() default false;

    // ═══════════════════════════════════════════════════════════════════════════
    // CACHING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Whether to enable caching for this entity.
     *
     * @return true to enable caching
     */
    boolean cacheable() default false;

    /**
     * Cache Time-To-Live (ISO-8601 duration).
     * <p>Examples: "PT5M" (5 minutes), "PT1H" (1 hour), "P1D" (1 day)
     *
     * @return cache TTL
     */
    String cacheTtl() default "PT5M";

    /**
     * Cache region/namespace.
     * <p>Defaults to module + entity name if empty.
     *
     * @return cache region name
     */
    String cacheRegion() default "";

    // ═══════════════════════════════════════════════════════════════════════════
    // SEARCH
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Whether to enable full-text search for this entity.
     * <p>Uses PostgreSQL tsvector/tsquery for searchable fields.
     *
     * @return true to enable full-text search
     */
    boolean fullTextSearch() default false;

    /**
     * PostgreSQL text search configuration.
     * <p>Examples: "english", "german", "simple"
     *
     * @return text search config name
     */
    String searchConfig() default "english";

    // ═══════════════════════════════════════════════════════════════════════════
    // FIELD NAME OVERRIDES
    // Use these when not using @TenantId, @SoftDelete, etc. annotations
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Name of the primary key field.
     * <p>Only needed if not using {@code @PrimaryKey} annotation or if
     * primary key field is not named "id".
     *
     * @return primary key field name
     */
    String primaryKeyField() default "id";

    /**
     * Name of the tenant ID field.
     * <p>Only needed if {@code tenantScoped = true} and not using {@code @TenantId} annotation.
     *
     * @return tenant ID field name, empty to auto-detect from @TenantId
     */
    String tenantIdField() default "";

    /**
     * Name of the soft delete flag field (boolean).
     * <p>Only needed if {@code softDelete = true} and not using {@code @SoftDelete} annotation.
     *
     * @return soft delete flag field name, empty to auto-detect from @SoftDelete
     */
    String softDeleteField() default "";

    /**
     * Name of the soft delete timestamp field.
     * <p>Optional - used with soft delete to track when entity was deleted.
     *
     * @return soft delete timestamp field name
     */
    String softDeleteTimestampField() default "";

    /**
     * Name of the soft deleted by field.
     * <p>Optional - used with soft delete to track who deleted the entity.
     *
     * @return soft deleted by field name
     */
    String softDeletedByField() default "";

    /**
     * Name of the version field for optimistic locking.
     * <p>Only needed if {@code versioned = true} and not using {@code @Version} annotation.
     *
     * @return version field name, empty to auto-detect from @Version
     */
    String versionField() default "";

    /**
     * Name of the created at audit field.
     * <p>Only needed if {@code audited = true} and not using {@code @AuditCreatedAt} annotation.
     *
     * @return created at field name
     */
    String createdAtField() default "";

    /**
     * Name of the created by audit field.
     * <p>Only needed if {@code audited = true} and not using {@code @AuditCreatedBy} annotation.
     *
     * @return created by field name
     */
    String createdByField() default "";

    /**
     * Name of the updated at audit field.
     * <p>Only needed if {@code audited = true} and not using {@code @AuditUpdatedAt} annotation.
     *
     * @return updated at field name
     */
    String updatedAtField() default "";

    /**
     * Name of the updated by audit field.
     * <p>Only needed if {@code audited = true} and not using {@code @AuditUpdatedBy} annotation.
     *
     * @return updated by field name
     */
    String updatedByField() default "";

    // ═══════════════════════════════════════════════════════════════════════════
    // VALIDATION & PROCESSING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Validation mode for system field requirements.
     */
    enum ValidationMode {
        /** Missing required fields cause compilation error */
        STRICT,
        /** Missing required fields cause warning, defaults are used */
        LENIENT,
        /** No validation, fields are optional */
        NONE
    }

    /**
     * How strictly to validate system field requirements.
     * <p>Examples:
     * <ul>
     *   <li>STRICT: {@code tenantScoped=true} without @TenantId = error</li>
     *   <li>LENIENT: {@code tenantScoped=true} without @TenantId = warning, feature disabled</li>
     *   <li>NONE: no validation</li>
     * </ul>
     *
     * @return validation mode
     */
    ValidationMode validationMode() default ValidationMode.STRICT;

    // ═══════════════════════════════════════════════════════════════════════════
    // UI CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * UI generation configuration.
     *
     * @return UI configuration
     */
    UI ui() default @UI;
}

