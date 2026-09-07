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
 * {@snippet lang="java" :
 * @Field(label = "Created At", readOnly = true)
 * @AuditCreatedAt
 * private Instant createdAt;
 * }
 *
 * <p><strong>Status: RESERVED</strong> — the {@code exeris-tooling} processor does not scan
 * fields for this marker, so writing it changes nothing in the emitted output. What
 * generates the column is the entity-level flag set on {@code @ExerisDomain}, with the
 * field-name override attributes choosing its name. See the package javadoc for the live
 * path.
 *
 * @since 0.1
 * @see eu.exeris.sdk.annotation.ExerisDomain#audited()
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface AuditCreatedAt {

    /**
     * Whether field is immutable after creation.
     *
     * @return true if immutable
     */
    boolean immutable() default true;
}
