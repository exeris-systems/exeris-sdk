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
 * <p><strong>Status: LIVE</strong> — the {@code exeris-tooling} processor reads this
 * marker and records the annotated field as {@code SystemFieldsMetadata.createdAtField},
 * which the generators use in place of the canonical name. It names the field; the column
 * exists only when {@code @ExerisDomain(audited = true)}. The attribute below is not
 * carried, so setting it changes no emitted output. The {@code -io} reader reads the
 * marker the same way. See the package javadoc.
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
