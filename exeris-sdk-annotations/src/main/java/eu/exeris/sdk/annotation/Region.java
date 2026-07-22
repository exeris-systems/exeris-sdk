package eu.exeris.sdk.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a member (a field or record component of a {@link View} class) as a
 * <strong>region / slot</strong> in the presentation composition tree. A region
 * is a named container into which {@link Block} component nodes are placed.
 *
 * <p><strong>Open-Core status — structurally live:</strong> extracted by the
 * {@code exeris-tooling} processor and emitted by the codegen-ts Angular view
 * generator (RFC-2026-06-28, tooling). The remaining piece is the ADR-047
 * leaf-field facet ({@code @UI}'s field-level render detail) — until it lands,
 * field-level presentation stays on {@code @UI}.
 *
 * @author Exeris SDK Team
 * @version 0.8.0
 * @since 0.8.0
 * @see View
 * @see Block
 */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface Region {

    /**
     * The slot name for this region. Empty means the slot name is derived from
     * the annotated member's name by the structure-walking tooling.
     *
     * @return the slot name, or empty to derive it from the member name
     */
    String slot() default "";
}
