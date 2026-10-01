package eu.exeris.sdk.annotation.system;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a boolean field as the soft delete flag.
 * <p>When {@code @ExerisDomain(softDelete = true)}, at most one field may carry
 * {@code @SoftDelete}; with none, the {@code softDeleteField} override or the canonical
 * name applies.
 *
 * <h2>Usage:</h2>
 * {@snippet lang="java" :
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
 * }
 *
 * <h2>Emitted today — by {@code @ExerisDomain(softDelete = true)}, not by this marker:</h2>
 * <ul>
 *   <li>DELETE operations set the flag instead of removing the row</li>
 *   <li>Every generated read — by id, list, count and the typed finders —
 *       filters out soft-deleted rows</li>
 * </ul>
 *
 * <h2>Target design — not emitted today:</h2>
 * <ul>
 *   <li>Restore operation available to undelete</li>
 *   <li>Optional: hard delete for compliance (GDPR right to erasure)</li>
 * </ul>
 *
 * <p><strong>Status: LIVE</strong> — the {@code exeris-tooling} processor reads this
 * marker and records the annotated field as {@code SystemFieldsMetadata.softDeleteField},
 * which the generators use in place of the canonical name. It names the field; the column
 * exists only when {@code @ExerisDomain(softDelete = true)}. None of the attributes below is
 * carried, so setting one changes no emitted output. The {@code -io} reader reads the
 * marker the same way. See the package javadoc.
 *
 * @since 0.1
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
