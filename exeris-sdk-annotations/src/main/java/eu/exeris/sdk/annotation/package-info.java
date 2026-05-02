/**
 * Exeris SDK - Core Annotations for Domain-First Development.
 *
 * <h2>Overview</h2>
 * <p>This package contains the foundational annotations that enable the
 * "Write Domain Logic, Generate Everything Else" philosophy of the Exeris platform.
 * By annotating your domain entities with these annotations, the SDK automatically
 * generates:
 * <ul>
 *   <li>REST API endpoints with full CRUD operations</li>
 *   <li>GraphQL schema and resolvers</li>
 *   <li>Angular UI components (list views, detail views, forms)</li>
 *   <li>Database migrations (Flyway scripts)</li>
 *   <li>OpenAPI/Swagger documentation</li>
 *   <li>Multi-tenant data isolation (if enabled)</li>
 * </ul>
 *
 * <h2>Quick Start</h2>
 * <pre>{@code
 * // 1. Add SDK dependency to your pom.xml
 * <dependency>
 *     <groupId>eu.exeris</groupId>
 *     <artifactId>exeris-sdk-annotations</artifactId>
 *     <version>0.1.0-SNAPSHOT</version>
 * </dependency>
 *
 * // 2. Annotate your domain entity
 * @ExerisDomain(
 *     module = "sales",
 *     aggregate = "Order",
 *     path = "/orders",
 *     tenantScoped = true,
 *     ui = @UI(listView = true, detailView = true, createForm = true)
 * )
 * @Entity
 * public class Order extends BaseTenantEntity {
 *
 *     @Field(
 *         label = "Order Number",
 *         required = true,
 *         ui = @UI(displayInList = true, displayOrder = 1)
 *     )
 *     private String orderNumber;
 *
 *     @Field(
 *         label = "Customer",
 *         ui = @UI(displayInList = true, displayOrder = 2)
 *     )
 *     @Relationship(
 *         targetEntity = Customer.class,
 *         displayField = "name"
 *     )
 *     private UUID customerId;
 * }
 *
 * // 3. Run SDK code generator
 * mvn exeris:generate
 *
 * // 4. Start your application - generated endpoints are ready!
 * }</pre>
 *
 * <h2>Core Annotations</h2>
 * <dl>
 *   <dt>{@link eu.exeris.sdk.annotation.ExerisDomain @ExerisDomain}</dt>
 *   <dd>Marks a class as a domain aggregate root, enabling full code generation</dd>
 *
 *   <dt>{@link eu.exeris.sdk.annotation.Field @Field}</dt>
 *   <dd>Configures field-level metadata for API, UI, and validation</dd>
 *
 *   <dt>{@link eu.exeris.sdk.annotation.UI @UI}</dt>
 *   <dd>Specifies UI component type and display behavior</dd>
 *
 *   <dt>{@link eu.exeris.sdk.annotation.Validation @Validation}</dt>
 *   <dd>Defines validation rules applied in both backend and frontend</dd>
 *
 *   <dt>{@link eu.exeris.sdk.annotation.Relationship @Relationship}</dt>
 *   <dd>Configures how relationships are displayed in the UI</dd>
 *
 *   <dt>{@link eu.exeris.sdk.annotation.Action @Action}</dt>
 *   <dd>Exposes domain methods as API endpoints with UI buttons</dd>
 * </dl>
 *
 * <h2>Architecture Principles</h2>
 * <ul>
 *   <li><strong>Java 26 Only:</strong> Built for Virtual Threads (Project Loom)</li>
 *   <li><strong>Schema-First:</strong> Annotations define contracts, code is generated</li>
 *   <li><strong>Hyper-Density:</strong> Optimized for minimal resource usage</li>
 *   <li><strong>Multi-Tenant Native:</strong> Built-in tenant isolation via PostgreSQL RLS</li>
 *   <li><strong>No Vendor Lock-In:</strong> Pure Java annotations, no framework dependencies</li>
 * </ul>
 *
 * <h2>Multi-Tenancy</h2>
 * <p>Enable multi-tenant isolation with {@code tenantScoped = true}:
 * <pre>{@code
 * @ExerisDomain(
 *     module = "sales",
 *     aggregate = "Order",
 *     tenantScoped = true  // Automatic tenant isolation
 * )
 * public class Order extends BaseTenantEntity {
 *     // tenant_id is automatically added and enforced
 * }
 * }</pre>
 *
 * <p>The SDK will:
 * <ul>
 *   <li>Add tenant_id column to the database</li>
 *   <li>Apply PostgreSQL Row-Level Security (RLS) policies</li>
 *   <li>Filter all queries by the current tenant context</li>
 *   <li>Validate tenant access in all API endpoints</li>
 * </ul>
 *
 * <h2>Soft Delete</h2>
 * <p>Enable soft delete with {@code softDelete = true}:
 * <pre>{@code
 * @ExerisDomain(
 *     module = "sales",
 *     aggregate = "Order",
 *     softDelete = true  // Logical deletion
 * )
 * public class Order {
 *     // deleted_at is automatically added
 * }
 * }</pre>
 *
 * <p>The SDK will:
 * <ul>
 *   <li>Add deleted_at timestamp column</li>
 *   <li>Modify DELETE operations to set deleted_at instead of removing rows</li>
 *   <li>Filter soft-deleted records from all queries</li>
 *   <li>Generate "restore" endpoints</li>
 * </ul>
 *
 * <h2>Best Practices</h2>
 * <ul>
 *   <li>Use {@code @ExerisDomain} on aggregate roots only (not on child entities)</li>
 *   <li>Always specify meaningful {@code label} values in {@code @Field}</li>
 *   <li>Use {@code displayOrder} to control field ordering in UI</li>
 *   <li>Enable {@code tenantScoped} for all business entities</li>
 *   <li>Use {@code softDelete} for entities with legal/audit requirements</li>
 *   <li>Document complex validations with {@code message} attribute</li>
 * </ul>
 *
 * @author Exeris SDK Team
 * @version 0.1.0
 * @since 0.1.0
 */
package eu.exeris.sdk.annotation;

