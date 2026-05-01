package eu.exeris.sdk.annotation.system;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field to store who created the entity.
 * <p>Automatically populated from security context.
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * @Field(label = "Created By", readOnly = true)
 * @AuditCreatedBy
 * private UUID createdBy;
 * }</pre>
 *
 * <h2>Supported Types:</h2>
 * <ul>
 *   <li>{@code UUID} - user ID</li>
 *   <li>{@code String} - username</li>
 * </ul>
 *
 * @author Exeris SDK Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditCreatedBy {

    /**
     * Whether field is immutable after creation.
     *
     * @return true if immutable
     */
    boolean immutable() default true;

    /**
     * SpEL expression to extract user identifier from security context.
     * <p>Default: {@code "principal.id"}
     *
     * @return SpEL expression
     */
    String expression() default "principal.id";
}
