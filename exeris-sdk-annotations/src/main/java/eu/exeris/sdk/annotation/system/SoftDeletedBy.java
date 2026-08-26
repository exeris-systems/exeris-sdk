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
 * <pre>{@code
 * @Field(label = "Deleted By")
 * @SoftDeletedBy
 * private UUID deletedBy;
 * }</pre>
 *
 * @author Exeris SDK Team
 * @version 0.1.0
 * @since 0.1.0
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
