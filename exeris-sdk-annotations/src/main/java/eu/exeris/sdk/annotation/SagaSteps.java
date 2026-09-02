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
 * <p><strong>Repeating a step compiles, and is then dropped.</strong> This holds
 * for a compiler-synthesized container and for a hand-written one alike. The
 * build-time processor matches the exact type
 * {@code eu.exeris.sdk.annotation.SagaStep} against a method's annotation
 * mirrors, and a repeated declaration presents only the {@code @SagaSteps}
 * mirror — so <em>no</em> step is extracted from that method. The {@code -io}
 * reader asks for the first {@code @SagaStep} on the declaration and therefore
 * keeps one. Both are silent: repetition is a clean compile that loses steps,
 * which is worse than an attribute that merely does nothing.
 *
 * <p>Declare each step on its own method until container handling lands in both
 * readers — a coordinated ADR-042 flip, cross-repo and unscheduled.
 * {@code @DomainEvent} is the only repeatable in the SDK whose container
 * <em>is</em> handled today, and is the reference shape for that flip.
 *
 * @since 0.9.0
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
