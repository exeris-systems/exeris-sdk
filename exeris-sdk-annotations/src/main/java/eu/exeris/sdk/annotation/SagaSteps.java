package eu.exeris.sdk.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container for repeated {@link SagaStep} annotations. Normally synthesized by
 * the compiler when {@code @SagaStep} is repeated; hand-written use is
 * supported and read identically. The AST flattens the container — consumers
 * see a list of step metadata, never the container annotation.
 *
 * <p>Public since 0.9.0: the container was previously package-private (a
 * top-level type declared inside {@code SagaStep.java}), which made repeating
 * {@code @SagaStep} a compile error from any other package — the compiler
 * requires the container to be at least as accessible as the repeatable
 * annotation at every use site.
 *
 * @since 0.9.0
 * @see SagaStep
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface SagaSteps {
    SagaStep[] value();
}
