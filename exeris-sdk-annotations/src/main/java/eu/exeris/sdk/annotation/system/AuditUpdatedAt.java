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
 * <p><strong>Status: RESERVED</strong> — the {@code exeris-tooling} processor does not scan
 * fields for this marker, so writing it changes nothing in the emitted output. What
 * generates the column is the entity-level flag set on {@code @ExerisDomain}, with the
 * field-name override attributes choosing its name. See the package javadoc for the live
 * path.
 *
 * @author Exeris SDK Team
 * @version 0.1.0
 * @since 0.1.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface AuditUpdatedAt {

    /**
     * Whether to set on create as well as update.
     *
     * @return true if set on create
     */
    boolean setOnCreate() default true;
}
