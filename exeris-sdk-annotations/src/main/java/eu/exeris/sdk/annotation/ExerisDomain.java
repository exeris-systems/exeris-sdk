package eu.exeris.sdk.annotation;

import java.lang.annotation.*;

/**
 * Marks a class as an Exeris domain entity (aggregate root) — the root of the
 * build-time generation chain.
 *
 * <p><strong>Status: LIVE.</strong> This annotation is extracted by the
 * {@code exeris-tooling} processor and by the SDK's own {@code -io} reader, and
 * drives every emitter. Individual attributes below carry their own status —
 * see the status vocabulary in the {@linkplain eu.exeris.sdk.annotation package
 * documentation}; not every attribute of a LIVE annotation is itself LIVE.
 *
 * <p>What the toolchain emits from an annotated class today:
 * <ul>
 *   <li>REST handler, service layer, and repository with dynamic queries</li>
 *   <li>A Flyway migration for the entity table</li>
 *   <li>The OpenAPI document for the emitted REST surface</li>
 *   <li>TypeScript types and Angular service / form / list components</li>
 *   <li>Domain event, event handler, saga driver, and graph-sync writer where
 *       the corresponding annotations are present</li>
 * </ul>
 * <p>There is <strong>no</strong> GraphQL emitter in the toolchain;
 * {@link #graphqlApi()} reaches the AST and is read by no generator.
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
 * <p>System columns are derived from the <em>entity-level flags</em> below. The
 * {@code eu.exeris.sdk.annotation.system} annotations ({@code @PrimaryKey},
 * {@code @TenantId}, {@code @SoftDelete}, {@code @Version}, {@code @Audit*}) are
 * RESERVED — no reader extracts them, so adding them changes nothing. They are
 * omitted here deliberately, so that the example shows what actually generates
 * the columns:
 * <pre>{@code
 * @ExerisDomain(
 *     module = "sales",
 *     path = "/orders",
 *     dataScope = ExerisDomain.DataScope.TENANT,   // tenant column + RLS policy + query filter
 *     softDelete = true,                           // deleted_at column + filtered reads
 *     audited = true,                              // created/updated audit columns
 *     versioned = true                             // optimistic-locking version column
 * )
 * public class Order {
 *     @Field(label = "ID")
 *     private UUID id;
 *
 *     @Field(label = "Order Number", required = true, unique = true)
 *     @Validation(minLength = 3, maxLength = 32)   // sibling, never nested inside @Field
 *     private String orderNumber;
 *
 *     @Field(label = "Status", filterable = true)
 *     private OrderStatus status;
 * }
 * }</pre>
 * <p>The class needs no superclass — the SDK ships no base entity type.
 *
 * @author Exeris SDK Team
 * @since 0.1.0
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
     *
     * <p><strong>Not consumed by any generator.</strong> This used to say the value
     * is "used in URL path: {@code /api/{version}/{path}}". No emitted artifact
     * publishes that prefix: the generated router registers routes at the derived
     * domain path, the OpenAPI document publishes the same, and the generated
     * typed client was aligned onto it after the mismatch was measured — a client
     * prepending {@code /api/<version>} could not reach the router it was generated
     * beside, so every cross-service call answered 404. The two streaming TypeScript
     * emitters that still folded the version into their SSE routes were aligned in
     * the same batch, and {@code -Aexeris.strict} now reports the attribute as inert.
     *
     * <p>The value still reaches {@code DomainMetadata.apiVersion()}, so it is
     * carried on the wire and available to a future consumer — unlike
     * {@link Action#path()}, which reaches no AST component at all. Whether the
     * router and the published contract should serve {@code /api/<version>/…} is a
     * real and defensible design question, but taking it changes every emitted route,
     * so it is a decision rather than a bug: until it is taken, setting this
     * attribute changes nothing about where an endpoint is served.
     *
     * @return API version (e.g., "v1", "v2"); carried into the AST, read by no
     *         generator today
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
     * Whether to generate a GraphQL schema and resolvers.
     *
     * <p><strong>Status: PARTIAL.</strong> The value is extracted into
     * {@code DomainMetadata} and survives the wire, but there is no GraphQL
     * emitter anywhere in {@code exeris-tooling} — no generator reads it, so
     * setting it to {@code true} produces nothing. It is kept as the declared
     * intent for a GraphQL emitter rather than removed, so that domains which
     * already express the intent do not have to be rewritten when one lands.
     *
     * <p>Unrelated to the {@code @Graph} / {@code @GraphEdge} /
     * {@code @GraphProperty} family, which describes projection into a graph
     * <em>database</em>.
     *
     * @return true to declare a GraphQL API — currently with no generated effect
     */
    boolean graphqlApi() default false;

    /**
     * Whether to generate real-time streaming endpoints (SSE/WebTransport).
     *
     * <p><strong>Open-Core status (kernel v0.10.0, ADR-043):</strong> live at
     * the entity level. The kernel server-push SPI (SSE-first
     * {@code HttpStreamExchange} / {@code HttpStreamHandler} /
     * {@code HttpRouter.streamRoute}) shipped with kernel v0.10.0, and the
     * tooling emits the entity-level stream route + TS {@code EventSource}
     * client backed by the real domain-event feed. SSE is the shipped
     * transport; the WebTransport/WebSocket half stays deferred kernel-side.
     * See also {@link Action#streaming()} / {@link Action#realTimeUpdates()}.
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
     * The data-scope tier of this entity — the single mutually-exclusive
     * discriminator for how its rows are partitioned and made visible.
     *
     * <p>A mirror of the AST-owned {@code eu.exeris.sdk.sourcemodel.ast.DataScope}
     * — two separate types mapped by name at extraction time (the
     * {@code SagaStep.StepKind} / {@code UI.ComponentType} precedent), never an
     * import of the AST type, preserving the annotation module's zero-dependency
     * contract — plus {@link DataScope#UNSPECIFIED}, which exists only on this
     * side because annotation attributes cannot default to {@code null}.
     *
     * <p>This is the canonical successor of the deprecated {@link #tenantScoped()}
     * boolean: {@code GLOBAL} is today's {@code tenantScoped = false} and
     * {@code TENANT} is today's {@code tenantScoped = true}, with {@code UNIVERSE}
     * adding the third tier the boolean could not express. Leaving this attribute
     * {@code UNSPECIFIED} falls back to {@code tenantScoped} for the duration of
     * the deprecation window (see {@code MIGRATION.md}); declaring both a tier
     * here and a contradicting {@code tenantScoped} is a build-time error the
     * processor reports rather than silently resolving.
     *
     * <p><strong>Open-Core status — {@code GLOBAL} / {@code TENANT} live,
     * {@code UNIVERSE} refused:</strong> the kernel carrier for the shared tier
     * ({@code sharedScopeKey} + a {@code SHARED_WORLD} row-visibility mode
     * composing with the physical isolation strategy, read-widen + owner-scoped
     * write) is fixed by the kernel ADR-012 §4b amendment and implemented with
     * its read-widen/write-pin TCK on the kernel 0.11 line. The
     * {@code exeris-tooling} transcription — mapping {@code UNIVERSE} onto
     * presence of that carrier — is not built yet, and until it is,
     * <em>declaring {@code UNIVERSE} fails the build</em>: the processor refuses
     * it at the declaration site, naming the tier and the reason.
     *
     * <p>This note used to say the tier had "no generated effect", which was
     * never true and became less true. Without the transcription the tier is not
     * inert — it falls through to the {@code TENANT} emission (owner column,
     * owner-pinned policy, a repository binding {@code getTenantId()}), and a
     * shared-world row is precisely one with no owner property, so the archetypal
     * {@code UNIVERSE} entity used to fail with {@code cannot find symbol} inside
     * generated code its author is told not to edit. Tooling turned that into a
     * refusal at the declaration; the honest reading of "reserved" here is
     * therefore "rejected", not "ignored". There is no way to obtain
     * cross-tenant read-widening from this build yet — if the entity really is
     * partitioned by an owner, declare {@code TENANT} and give it a tenant
     * property. {@code GLOBAL} and {@code TENANT} carry exactly the semantics
     * {@code tenantScoped} already carried, and are live through the same path.
     *
     * @return the data-scope tier, or {@link DataScope#UNSPECIFIED} to defer to
     *         {@link #tenantScoped()}
     * @since 0.10.0
     * @see DataScope
     */
    DataScope dataScope() default DataScope.UNSPECIFIED;

    /**
     * The data-scope tier of an entity (see {@link #dataScope()}).
     *
     * <p>A mirror of the AST-owned {@code DataScope} — see {@link #dataScope()}
     * for why the two types are separate — plus {@link #UNSPECIFIED}.
     *
     * @since 0.10.0
     */
    enum DataScope {
        /**
         * No explicit tier declared (the default). Falls back to
         * {@link ExerisDomain#tenantScoped()} — {@code true} reads as
         * {@link #TENANT}, {@code false} as {@link #GLOBAL} — for the duration
         * of that attribute's deprecation window. Maps to an absent AST
         * {@code dataScope}; {@code DomainMetadata.effectiveDataScope()} then
         * applies the same fallback.
         */
        UNSPECIFIED,

        /**
         * Not tenant-partitioned — a global singleton or reference table,
         * identical for every tenant. Today's {@code tenantScoped = false}.
         */
        GLOBAL,

        /**
         * Tenant-private rows — row-level isolation, one tenant never sees
         * another's rows. Today's {@code tenantScoped = true}; requires a
         * {@code @TenantId} field or {@link ExerisDomain#tenantIdField()}.
         */
        TENANT,

        /**
         * Shared world — rows owned by a tenant but readable across tenants
         * (a common reference dataset, a cross-tenant collaboration space).
         * Reads widen beyond the owning tenant; writes stay pinned to it.
         *
         * <p><strong>Reserved — declaring it fails the build</strong>, which is
         * a stronger statement than "has no effect" and is the one that holds:
         * the tooling transcription onto the kernel carrier is not built, so the
         * processor refuses the tier rather than half-emitting the {@code TENANT}
         * shape for it. See the Open-Core status note on
         * {@link ExerisDomain#dataScope()}.
         */
        UNIVERSE
    }

    /**
     * Whether entity is scoped to tenants (multi-tenancy).
     * <p>When true, requires either:
     * <ul>
     *   <li>A field annotated with {@code @TenantId}, OR</li>
     *   <li>{@code tenantIdField} to be specified</li>
     * </ul>
     *
     * @return true for multi-tenant isolation
     * @deprecated since 0.10.0, for removal in 1.0.0. A boolean cannot express
     *         the third data-scope tier ({@link DataScope#UNIVERSE}), so it did
     *         double duty for "not partitioned" and "tenant-private". Replaced by
     *         {@link #dataScope()}: {@code tenantScoped = true} becomes
     *         {@code dataScope = DataScope.TENANT}, {@code false} becomes
     *         {@code DataScope.GLOBAL}. The processor reads this attribute as a
     *         fallback with a build warning while {@code dataScope} is
     *         {@link DataScope#UNSPECIFIED}; that window closes at 1.0.0. See
     *         {@code MIGRATION.md} and RFC-2026-06-24.
     */
    @Deprecated(since = "0.10.0", forRemoval = true)
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
     * <p><strong>Open-Core status (kernel v0.11, ADR-061):</strong> declared but
     * not extracted. No processor reads this attribute into the AST and no
     * generator consumes it, so today it documents intent and nothing enforces
     * it — do not treat it as access control. The gap was that the kernel had
     * no contract to compile it down to: edge authorization was a hardcoded
     * {@code /secure} path convention, and method-level {@code @RequiresRole}
     * is written against the kernel by hand. Kernel ADR-061 changes that by
     * making route authorization a declarable, path-shaped policy behind an
     * {@code Optional} slot on {@code HttpKernelProviders} — the first surface
     * {@link #permissions()} could actually generate into. Wiring it is a
     * {@code exeris-tooling} slice against a preview-tier SPI, so it is a 1.x
     * item, not a pre-1.0 one; see {@code ROADMAP.md}.
     *
     * <p><strong>Roles specifically have no destination even after ADR-061.</strong>
     * {@code RouteRequirement} declares no role kind — it decides through
     * {@code PrincipalContext.hasScope} and nothing else — and ADR-063
     * (owned by {@code exeris-spring-runtime}) deliberately keeps
     * {@code hasRole(...)} out of the edge DSL rather than inventing a
     * {@code ROLE_x}-to-scope convention, because that would put a second,
     * silently-diverging authority model at the edge. Roles resolve at the
     * <em>method</em> level, through {@code @RequiresRole} against a build-time
     * {@code methodId} the kernel cannot derive from a URL. So a path-shaped
     * route policy is a destination for {@code permissions} and not for this
     * attribute; what {@code roles} would compile into, if anything, is
     * undecided.
     *
     * @return array of role names
     */
    String[] roles() default {};

    /**
     * Default permissions required to access this entity's API.
     *
     * <p><strong>Open-Core status (kernel v0.11, ADR-061):</strong> declared but
     * not extracted, on the same terms as {@link #roles()} — see that attribute
     * for why. Of the two, this is the half with a destination: the kernel's
     * {@code RouteRequirement} decides on named scopes, so a permission is what
     * a generated URL-to-policy table could carry. That table is
     * {@code exeris-tooling}'s to emit and is not built.
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
     * <p><strong>Open-Core status — inert by construction (reserved):</strong>
     * this attribute governs the strictness of system-field <em>marker</em>
     * validation ({@code @TenantId}, {@code @Version}, …), but the tooling
     * processor does not scan fields for those markers, so no mode has
     * anything to validate — the attribute governs nothing until marker
     * scanning lands. The live system-field path is the field-name override
     * attributes above / the canonical accessor names.
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

