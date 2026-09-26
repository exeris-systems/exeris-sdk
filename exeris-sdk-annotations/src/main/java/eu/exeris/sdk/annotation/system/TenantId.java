package eu.exeris.sdk.annotation.system;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as the tenant identifier for multi-tenant isolation.
 * <p>When {@code @ExerisDomain(dataScope = TENANT)}, at most one field may carry
 * {@code @TenantId}; with none, the {@code tenantIdField} override or the canonical
 * name {@code tenantId} applies.
 *
 * <h2>Usage:</h2>
 * {@snippet lang="java" :
 * @ExerisDomain(module = "sales", path = "/orders", dataScope = DataScope.TENANT)
 * public class Order {
 *
 *     @Field(label = "Organization")
 *     @TenantId
 *     private UUID organizationId;
 * }
 * }
 *
 * <h2>Supported Types:</h2>
 * <ul>
 *   <li>{@code UUID} - recommended</li>
 *   <li>{@code String} - for legacy systems</li>
 *   <li>{@code Long} - for integer-based tenant IDs</li>
 * </ul>
 *
 * <h2>Emitted today — by {@code dataScope = TENANT}, not by this marker:</h2>
 * <ul>
 *   <li>Reads and writes are confined to the current tenant by a generated
 *       row-level-security policy ({@code USING} and {@code WITH CHECK}, forced so
 *       the table owner is bound by it too)</li>
 *   <li>A written row whose tenant is unset is stamped with the acting tenant from
 *       the kernel's {@code StorageContext} — in the repository, not a service
 *       layer</li>
 *   <li>The tenant column is indexed on its own</li>
 * </ul>
 *
 * <h2>Target design — not emitted today:</h2>
 * <ul>
 *   <li>API responses never expose tenant ID (security) — today a create answers
 *       with the entity, tenant field included</li>
 *   <li>A composite tenant + primary-key index</li>
 * </ul>
 *
 * <p><strong>Status: PARTIAL</strong> — the {@code exeris-tooling} processor reads this
 * marker and records the annotated field as {@code SystemFieldsMetadata.tenantIdField},
 * which the generators use for the tenant column in place of the canonical name. It names
 * the field; it does not partition the entity — that is {@code dataScope = TENANT} on
 * {@code @ExerisDomain}. The processor refuses the marker on more than one field, and a
 * {@code tenantIdField} override that names a different field. None of the attributes
 * below is carried ({@code SystemFieldsMetadata} holds one field name per role), so
 * setting one changes no emitted output, and the {@code -io} reader does not read the
 * marker. See the package javadoc.
 *
 * @since 0.1
 * @see eu.exeris.sdk.annotation.ExerisDomain#dataScope()
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
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
