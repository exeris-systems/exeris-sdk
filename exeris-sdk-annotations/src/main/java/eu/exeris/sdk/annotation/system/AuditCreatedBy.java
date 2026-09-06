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
 * {@snippet lang="java" :
 * @Field(label = "Created By", readOnly = true)
 * @AuditCreatedBy
 * private UUID createdBy;
 * }
 *
 * <h2>Supported Types:</h2>
 * <ul>
 *   <li>{@code UUID} - user ID</li>
 *   <li>{@code String} - username</li>
 * </ul>
 *
 * <p><strong>Status: RESERVED</strong> — the {@code exeris-tooling} processor does not scan
 * fields for this marker, so writing it changes nothing in the emitted output. What
 * generates the column is the entity-level flag set on {@code @ExerisDomain}, with the
 * field-name override attributes choosing its name. See the package javadoc for the live
 * path.
 *
 * @since 0.1
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
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
