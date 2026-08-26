package eu.exeris.sdk.annotation.system;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field to store who last updated the entity.
 * <p>Automatically updated from security context on every modification.
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * @Field(label = "Updated By", readOnly = true)
 * @AuditUpdatedBy
 * private UUID updatedBy;
 * }</pre>
 *
 * @author Exeris SDK Team
 * @version 0.1.0
 * @since 0.1.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface AuditUpdatedBy {

    /**
     * Whether to set on create as well as update.
     *
     * @return true if set on create
     */
    boolean setOnCreate() default true;

    /**
     * SpEL expression to extract user identifier from security context.
     *
     * @return SpEL expression
     */
    String expression() default "principal.id";
}
