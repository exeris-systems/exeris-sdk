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
 * {@code @GraphEdge} <em>compile</em> outside this package. It does not make it
 * <em>read</em>: the member annotations of {@code @Graph} — {@code @GraphEdge},
 * {@code @GraphProperty} and {@code @GraphQuery} — are extracted by neither the
 * build-time processor nor the {@code -io} reader, so a single edge declaration
 * reaches no AST today either. {@code GraphMetadata} carries an empty edge list
 * on both paths. Declaring edges records author intent and generates nothing.
 *
 * @since 0.10.0
 * @see GraphEdge
 * @see Graph
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface GraphEdges {
    GraphEdge[] value();
}
