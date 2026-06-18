package eu.exeris.sdk.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container for repeated {@link Rule} annotations on a single element.
 *
 * <p>Top-level container, following the SDK's plural-name convention
 * ({@code @SagaStep} → {@code @SagaSteps}). It is rarely written by hand — the
 * compiler synthesizes it when {@code @Rule} is repeated — and the AST flattens
 * it, so consumers see a flat list, never the container.
 *
 * @since 0.7.0
 * @see Rule
 */
@Target({ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface Rules {

    /**
     * The repeated rules.
     *
     * @return the contained {@link Rule} annotations
     */
    Rule[] value();
}
