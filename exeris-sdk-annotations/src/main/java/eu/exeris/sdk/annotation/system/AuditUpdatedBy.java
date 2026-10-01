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
 * {@snippet lang="java" :
 * @Field(label = "Updated By", readOnly = true)
 * @AuditUpdatedBy
 * private UUID updatedBy;
 * }
 *
 * <p><strong>Status: LIVE</strong> — the {@code exeris-tooling} processor reads this
 * marker and records the annotated field as {@code SystemFieldsMetadata.updatedByField},
 * which the generators use in place of the canonical name. It names the field; the column
 * exists only when {@code @ExerisDomain(audited = true)}. Neither attribute below is
 * carried, so setting one changes no emitted output. The {@code -io} reader reads the
 * marker the same way. See the package javadoc.
 *
 * @since 0.1
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
