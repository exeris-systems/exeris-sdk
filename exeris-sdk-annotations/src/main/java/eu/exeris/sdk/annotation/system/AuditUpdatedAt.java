package eu.exeris.sdk.annotation.system;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a temporal field as the last update timestamp.
 * <p>Automatically updated on every modification.
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * @Field(label = "Updated At", readOnly = true)
 * @AuditUpdatedAt
 * private Instant updatedAt;
 * }</pre>
 *
 * @author Exeris SDK Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditUpdatedAt {

    /**
     * Whether to set on create as well as update.
     *
     * @return true if set on create
     */
    boolean setOnCreate() default true;
}
