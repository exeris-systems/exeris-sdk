package eu.exeris.sdk.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A named, declarative invariant (a guard / business rule) over an entity or a
 * field — the constraint half of the declarative-behaviour layer (with
 * {@link Derived}).
 *
 * <p>Repeatable; the container is the top-level {@link Rules} annotation (the
 * plural-name convention the SDK uses for {@code @SagaStep} → {@code @SagaSteps},
 * since {@code Rule} has a clean plural — unlike the capability annotations,
 * whose names already end in {@code -s} and therefore use a nested {@code .List}).
 * Strictly opt-in: complex domain logic that no rule expresses stays hand-written
 * (the escape hatch).
 *
 * <h2>Basic usage</h2>
 * {@snippet lang="java" :
 * @Rule(name = "discountRequiresApproval",
 *       expression = "discount <= 0 or approvedBy != null",
 *       message = "order.rule.discountRequiresApproval",
 *       severity = "ERROR")
 * public class Order { ... }
 * }
 *
 * <h2>Open-Core status — RESERVED, generation pending tooling</h2>
 * <p>This is a declared shape, not yet an enforced rule. The annotation is
 * defined now; its {@code RuleMetadata} AST record is the next slice, and no
 * build-time processor extracts {@code @Rule} nor does any code generator
 * consume it yet — so declaring it today has no generated effect. The surface is
 * reserved while the {@code RuleMetadata} record, the {@code exeris-tooling}
 * processor extraction, the {@code exeris-sdk-source-model-io} reader (in
 * lock-step parity), and the codegen consumer are wired; see {@code ROADMAP.md}
 * and the declarative-behaviour RFC {@code RFC-2026-06-18} (ACCEPTED).
 *
 * @since 0.7
 * @see Derived
 * @see Rules
 */
@Target({ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.SOURCE)
@Documented
@Repeatable(Rules.class)
public @interface Rule {

    /**
     * Rule name (unique within the annotated element). Used for diagnostics and
     * as the generated constraint identifier.
     *
     * @return the rule name
     */
    String name();

    /**
     * The boolean invariant expression, written in {@link #language()}. The rule
     * holds when the expression evaluates {@code true}.
     *
     * @return the invariant expression
     */
    String expression();

    /**
     * Violation message — a literal or an i18n bundle key (the same key-or-literal
     * convention as the 0.6 {@code *Key} fields). Empty leaves the message to the
     * generator.
     *
     * @return the violation message, or {@code ""} for none
     */
    String message() default "";

    /**
     * Severity tag — recognised hints are {@code "ERROR"} / {@code "WARN"} /
     * {@code "INFO"} (the exact vocabulary is finalized with the consuming
     * tooling). Empty selects the default ({@code "ERROR"}); the consumer applies
     * that default, so the common case carries no severity on the wire.
     *
     * @return the severity tag, or {@code ""} for the default
     */
    String severity() default "";

    /**
     * Expression-language tag. Empty selects the SDK default ({@code "spel"}).
     *
     * @return the expression-language tag, or {@code ""} for the default
     */
    String language() default "";
}
