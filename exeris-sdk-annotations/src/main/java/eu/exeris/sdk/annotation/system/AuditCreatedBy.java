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
 * <p><strong>Status: PARTIAL</strong> — the {@code exeris-tooling} processor reads this
 * marker and records the annotated field as {@code SystemFieldsMetadata.createdByField},
 * which the generators use in place of the canonical name. It names the field; the column
 * exists only when {@code @ExerisDomain(audited = true)}. Neither attribute below is
 * carried, so setting one changes no emitted output, and the {@code -io} reader does not
 * read the marker. See the package javadoc.
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
