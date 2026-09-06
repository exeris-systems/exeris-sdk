package eu.exeris.sdk.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container for repeated {@link GraphEdge} annotations. Normally synthesized by
 * the compiler when {@code @GraphEdge} is repeated on a field.
 *
 * <p>Public since 0.10.0: the container was previously package-private (a
 * top-level type declared inside {@code GraphEdge.java}), which made repeating
 * {@code @GraphEdge} a compile error from any other package — the compiler
 * requires the container to be at least as accessible as the repeatable
 * annotation at every use site. This is the same defect fixed for
 * {@link SagaSteps} in 0.9.0; these two top-level containers were the whole
 * class of it, and {@code AnnotationContractTest} now holds every
 * {@code @Repeatable} container to the rule.
 *
 * <p><strong>What this fix does and does not buy.</strong> It makes repeating
 * {@code @GraphEdge} <em>compile</em> outside this package. It does not make a
 * repetition <em>readable</em>: the processor unwraps this container, and then
 * refuses two edges on one field at the declaration, because
 * {@code GraphEdgeMetadata} cannot express the shape. One edge is extracted;
 * two are a build error rather than a silent loss.
 *
 * <p>{@code @GraphEdge} itself is read by the processor and consumed by the
 * graph-sync generator. Its siblings under {@code @Graph} —
 * {@code @GraphProperty} and {@code @GraphQuery} — are read by nobody, so
 * {@code GraphMetadata} carries a null property list and an empty query list.
 * The {@code -io} reader reads none of the three.
 *
 * <p><strong>Status: PARTIAL</strong> — one of the two readers handles this
 * container, and it admits only one edge. Compiler-synthesized; authors write
 * repeated {@code @GraphEdge} directly and never this type.
 * @since 0.10
 * @see GraphEdge
 * @see Graph
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface GraphEdges {
    /**
     * The repeated declarations. Written by the compiler when {@link GraphEdge} appears
     * more than once on a field; there is rarely a reason to write this container by hand.
     *
     * @return the repeated edge declarations
     */
    GraphEdge[] value();
}
