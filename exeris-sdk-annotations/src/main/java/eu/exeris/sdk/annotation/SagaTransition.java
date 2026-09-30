package eu.exeris.sdk.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a typed transition between two saga steps — a first-class,
 * outcome-edged edge in the saga's state-machine graph.
 *
 * <p>{@link SagaStep#order()} and {@link SagaStep#dependsOn()} order steps
 * without saying <em>on which outcome</em> a branch fires, so a branch the
 * consumer must encode by hand (via the returned outcome) cannot be drawn or
 * generated. A {@code @SagaTransition} carries exactly that: {@link #from()}
 * the source step, {@link #on()} the outcome that fires the edge, {@link #to()}
 * the next step. Declaring the edges promotes the step list + {@code dependsOn}
 * DAG into a state-machine graph a design-time tool can draw and build-time
 * tooling can turn into the await/dispatch/compensate body.
 *
 * <p>A transition is a saga-level fact — an edge between two steps, owned by
 * neither — so it annotates the {@link Saga @Saga} class (not a step method)
 * and is repeatable there, one annotation per edge.
 *
 * <h2>Basic usage</h2>
 * {@snippet lang="java" :
 * @Saga(name = "OrderFulfillmentSaga", ...)
 * @SagaTransition(from = "reserveInventory", to = "processPayment")   // SUCCESS edge (default)
 * @SagaTransition(from = "processPayment", to = "releaseInventory",
 *                 on = TransitionOutcome.FAILURE)                     // failure -> compensate
 * @SagaTransition(from = "processPayment", on = TransitionOutcome.TIMEOUT) // terminal: abort
 * @SagaTransition(from = "releaseInventory", on = TransitionOutcome.COMPENSATED) // terminal: rolled back
 * public class OrderFulfillmentSaga { ... }
 * }
 *
 * <h2>Terminal edges and loops</h2>
 * <p>A blank {@link #to()} (the default) marks a <em>terminal</em> edge — the
 * saga ends (or aborts) when the edge fires; the AST normalizes blank to
 * {@code null}, so the terminal invariant downstream is exactly
 * {@code to == null}. Loops are expressed by pointing {@code to} back at an
 * earlier step.
 *
 * <h2>Guards</h2>
 * <p>The optional {@link #guard()} (a SpEL expression, the existing
 * {@code condition} / {@code visibleWhen} convention) narrows an edge so it
 * fires only when the condition holds — e.g. two {@code SUCCESS} edges out of
 * the same step, distinguished by guards. The SDK stores the expression
 * verbatim and interprets nothing (zero runtime coupling); evaluating it is
 * build-time / runtime tooling's job.
 *
 * <h2>Open-Core status — RESERVED, gated on kernel routing</h2>
 * <p>The AST twin already exists: {@code SagaMetadata.transitions} (a list of
 * {@code SagaMetadata.SagaTransition} edges) is this annotation's canonical
 * carrier, and {@link #on()} maps by name onto the AST-owned
 * {@code SagaMetadata.TransitionOutcome}. No build-time processor extracts this
 * annotation into the record, and the {@code -io} reader deliberately stays in
 * parity by omission (ADR-042 lock-step parity).
 *
 * <p>What gates the extraction is the kernel, not the tooling. The kernel's flow
 * plan precomputes exactly one next step per step — the {@code "default"}-tagged
 * transition, else the first declared one, else the following step — and never
 * routes on an outcome or a condition tag; a failure unwinds the compensation
 * stack rather than following an edge. So only an unguarded {@code SUCCESS} edge
 * is expressible there: a {@code FAILURE}, {@code TIMEOUT} or {@code COMPENSATED}
 * edge, a {@link #guard()}, or a second {@code SUCCESS} edge out of one step is
 * not. Until the kernel routes by outcome or tag, the {@code exeris-tooling}
 * saga generator chains the steps in declaration order, and declaring
 * transitions has no generated effect.
 *
 * @since 0.9
 * @see Saga
 * @see SagaStep
 * @see SagaTransitions
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Documented
@Repeatable(SagaTransitions.class)
public @interface SagaTransition {

    /**
     * The source step name — must match a {@link SagaStep#name()} declared on
     * this saga. Stored as a plain step name; resolving the edge against the
     * actual step set is build-time tooling's job, not the SDK's.
     *
     * @return the source step name
     */
    String from();

    /**
     * The target step name. Blank (the default) marks a terminal edge — the
     * saga ends (or aborts) when this edge fires. Pointing back at an earlier
     * step expresses a loop.
     *
     * @return the target step name, or {@code ""} for a terminal edge
     */
    String to() default "";

    /**
     * The step outcome that fires this edge.
     *
     * @return the firing outcome
     */
    TransitionOutcome on() default TransitionOutcome.SUCCESS;

    /**
     * Optional guard condition (SpEL) narrowing the edge: when non-empty, the
     * edge fires only if the expression evaluates to {@code true} against the
     * saga state. Stored verbatim; the SDK interprets nothing.
     *
     * @return the guard expression, or {@code ""} for an unguarded edge
     */
    String guard() default "";

    /**
     * The outcome of a step that fires a {@link SagaTransition} edge.
     *
     * <p>A mirror of the AST-owned {@code SagaMetadata.TransitionOutcome} —
     * two separate types mapped by name at extraction time (the
     * {@code UI.ComponentType} precedent), never an import of the AST type,
     * preserving the annotation module's zero-dependency contract.
     *
     * @since 0.9
     */
    enum TransitionOutcome {
        /** The step completed successfully. */
        SUCCESS,
        /** The step failed (typically routes to compensation). */
        FAILURE,
        /** The step timed out. */
        TIMEOUT,
        /** A compensation step finished (rollback edge). */
        COMPENSATED
    }
}
