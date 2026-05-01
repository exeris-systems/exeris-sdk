package eu.exeris.sdk.annotation.system;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as the tenant identifier for multi-tenant isolation.
 * <p>When {@code @ExerisDomain(tenantScoped = true)}, exactly one field
 * must be annotated with {@code @TenantId} OR {@code tenantIdField} must be specified.
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * @ExerisDomain(module = "sales", path = "/orders", tenantScoped = true)
 * public class Order {
 *
 *     @Field(label = "Organization")
 *     @TenantId
 *     private UUID organizationId;
 * }
 * }</pre>
 *
 * <h2>Supported Types:</h2>
 * <ul>
 *   <li>{@code UUID} - recommended</li>
 *   <li>{@code String} - for legacy systems</li>
 *   <li>{@code Long} - for integer-based tenant IDs</li>
 * </ul>
 *
 * <h2>Generated Behavior:</h2>
 * <ul>
 *   <li>Repository queries automatically filter by tenant</li>
 *   <li>Service layer injects current tenant from security context</li>
 *   <li>API responses never expose tenant ID (security)</li>
 *   <li>Indexes created for tenant + primary key</li>
 * </ul>
 *
 * @author Exeris SDK Team
 * @version 1.0.0
 * @since 1.0.0
 * @see eu.exeris.sdk.annotation.ExerisDomain#tenantScoped()
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TenantId {

    /**
     * Whether to automatically set tenant ID from security context on create.
     * <p>Default: {@code true}
     *
     * @return true if auto-populated
     */
    boolean autoPopulate() default true;

    /**
     * Whether to validate tenant ID matches current user's tenant on update/delete.
     * <p>Default: {@code true}
     *
     * @return true if validated
     */
    boolean validateOnMutation() default true;

    /**
     * Whether to include tenant ID in composite unique constraints.
     * <p>When true, unique constraints are scoped to tenant.
     * <p>Default: {@code true}
     *
     * @return true if included in unique constraints
     */
    boolean scopeUniqueConstraints() default true;

    /**
     * Whether to expose tenant ID in API responses.
     * <p>Default: {@code false} (security best practice)
     *
     * @return true if exposed in responses
     */
    boolean exposeInApi() default false;
}
