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
 * <p><strong>Open-Core status — reserved:</strong> the SDK carries the declared
 * presentation shape, but no Open-Core processor / codegen honours it yet.
 * Generation (the Angular 22 signal-first emitter) is {@code exeris-tooling} work,
 * pending the build gate in RFC-2026-06-25. Declaring it has no generated effect
 * today.
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
     * the annotated member's name by the (future) structure-walking tooling.
     *
     * @return the slot name, or empty to derive it from the member name
     */
    String slot() default "";
}
