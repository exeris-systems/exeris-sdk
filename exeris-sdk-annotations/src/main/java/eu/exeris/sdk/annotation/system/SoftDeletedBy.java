package eu.exeris.sdk.annotation.system;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field to store who performed the soft delete.
 * <p>Automatically populated from security context.
 *
 * <h2>Usage:</h2>
 * {@snippet lang="java" :
 * @Field(label = "Deleted By")
 * @SoftDeletedBy
 * private UUID deletedBy;
 * }
 *
 * <p><strong>Status: LIVE</strong> — the {@code exeris-tooling} processor reads this
 * marker and records the annotated field as {@code SystemFieldsMetadata.softDeletedByField},
 * which the generators use in place of the canonical name. It names the field; the column
 * exists only when {@code @ExerisDomain(softDelete = true)}. The attribute below is not
 * carried, so setting it changes no emitted output. The {@code -io} reader reads the
 * marker the same way. See the package javadoc.
 *
 * @since 0.1
 * @see SoftDelete
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface SoftDeletedBy {

    /**
     * Whether to clear on restore.
     *
     * @return true if cleared on restore
     */
    boolean clearOnRestore() default true;
}
