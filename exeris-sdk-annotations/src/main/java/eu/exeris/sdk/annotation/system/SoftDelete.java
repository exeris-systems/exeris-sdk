package eu.exeris.sdk.annotation.system;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a boolean field as the soft delete flag.
 * <p>When {@code @ExerisDomain(softDelete = true)}, exactly one field
 * must be annotated with {@code @SoftDelete} OR {@code softDeleteField} must be specified.
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * @ExerisDomain(module = "sales", path = "/orders", softDelete = true)
 * public class Order {
 *
 *     @Field(label = "Archived")
 *     @SoftDelete
 *     private boolean archived;
 *
 *     @Field(label = "Archived At")
 *     @SoftDeleteTimestamp
 *     private Instant archivedAt;
 * }
 * }</pre>
 *
 * <h2>Generated Behavior:</h2>
 * <ul>
 *   <li>DELETE operations set flag to true instead of removing row</li>
 *   <li>All queries automatically filter out soft-deleted records</li>
 *   <li>Restore operation available to undelete</li>
 *   <li>Optional: hard delete for compliance (GDPR right to erasure)</li>
 * </ul>
 *
 * @author Exeris SDK Team
 * @version 0.1.0
 * @since 0.1.0
 * @see SoftDeleteTimestamp
 * @see SoftDeletedBy
 * @see eu.exeris.sdk.annotation.ExerisDomain#softDelete()
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface SoftDelete {

    /**
     * Default value for the flag (false = not deleted).
     *
     * @return default value
     */
    boolean defaultValue() default false;

    /**
     * Whether to allow hard delete (permanent removal).
     * <p>When true, generates additional hardDelete() method.
     * <p>Default: {@code false}
     *
     * @return true if hard delete allowed
     */
    boolean allowHardDelete() default false;

    /**
     * Retention period before hard delete is allowed (ISO-8601 duration).
     * <p>Example: "P30D" = 30 days after soft delete
     * <p>Only applies when {@code allowHardDelete = true}
     *
     * @return retention period
     */
    String retentionPeriod() default "";

    /**
     * Whether soft-deleted records should be excluded from unique constraints.
     * <p>When true, allows "reusing" unique values after soft delete.
     * <p>Default: {@code true}
     *
     * @return true if excluded from unique constraints
     */
    boolean excludeFromUniqueConstraints() default true;
}
