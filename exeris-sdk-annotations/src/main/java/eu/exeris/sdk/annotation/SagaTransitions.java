package eu.exeris.sdk.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container for repeated {@link SagaTransition} annotations on a saga class.
 *
 * <p>Top-level container, following the SDK's plural-name convention
 * ({@code @Rule} → {@code @Rules}, {@code @SagaStep} → {@code @SagaSteps}). It
 * is rarely written by hand — the compiler synthesizes it when
 * {@code @SagaTransition} is repeated — and the AST flattens it
 * ({@code SagaMetadata.transitions} is a flat edge list), so consumers see a
 * flat list, never the container.
 *
 *
 * <p><strong>Status: RESERVED</strong> — a container is read exactly when the annotation it
 * holds is, and {@link SagaTransition @SagaTransition} is not extracted.
 * Compiler-synthesized; authors write repeated {@code @SagaTransition} directly and never
 * this type.
 * @since 0.9.0
 * @see SagaTransition
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface SagaTransitions {

    /**
     * The repeated transition edges.
     *
     * @return the contained {@link SagaTransition} annotations
     */
    SagaTransition[] value();
}
