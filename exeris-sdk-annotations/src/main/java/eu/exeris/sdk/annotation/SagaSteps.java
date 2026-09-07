package eu.exeris.sdk.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container for repeated {@link SagaStep} annotations. Normally synthesized by
 * the compiler when {@code @SagaStep} is repeated. The AST flattens step
 * metadata — consumers see a list of steps, never the container annotation.
 *
 * <p>Public since 0.9.0: the container was previously package-private (a
 * top-level type declared inside {@code SagaStep.java}), which made repeating
 * {@code @SagaStep} a compile error from any other package — the compiler
 * requires the container to be at least as accessible as the repeatable
 * annotation at every use site.
 *
 * <p><strong>Repeating a step is read by one reader and loses steps in the
 * other.</strong> The build-time processor unwraps the container and extracts
 * every step it holds. The {@code -io} reader asks for the first
 * {@code @SagaStep} on the declaration and therefore keeps one, silently: on
 * that path a repetition is a clean compile that loses steps, which is worse
 * than an attribute that merely does nothing.
 *
 * <p>Declare each step on its own method unless the processor is the only reader
 * in play. Bringing {@code -io} level is a coordinated ADR-042 flip, cross-repo
 * and unscheduled; {@code @DomainEvent} is the one repeatable both readers
 * handle, and is the reference shape for it.
 *
 * <p><strong>Status: PARTIAL</strong> — one of the two readers handles this
 * container. The processor unwraps it to reach the repeated
 * {@link SagaStep @SagaStep} declarations, so on that path its status is
 * {@code @SagaStep}'s own; the {@code -io} reader does not, and keeps one step.
 * @since 0.9
 * @see SagaStep
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface SagaSteps {
    /**
     * The repeated declarations. Written by the compiler when {@link SagaStep} appears
     * more than once on a method; there is rarely a reason to write this container by hand.
     *
     * @return the repeated step declarations
     */
    SagaStep[] value();
}
