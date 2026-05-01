package eu.exeris.sdk.annotation.system;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a temporal field as the creation timestamp.
 * <p>Automatically populated when entity is first persisted.
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * @Field(label = "Created At", readOnly = true)
 * @AuditCreatedAt
 * private Instant createdAt;
 * }</pre>
 *
 * @author Exeris SDK Team
 * @version 1.0.0
 * @since 1.0.0
 * @see eu.exeris.sdk.annotation.ExerisDomain#audited()
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditCreatedAt {

    /**
     * Whether field is immutable after creation.
     *
     * @return true if immutable
     */
    boolean immutable() default true;
}
