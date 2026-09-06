package eu.exeris.sdk.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field (or accessor) whose value is <em>derived</em> — computed from
 * other fields or related entities rather than stored and set directly.
 *
 * <p>Part of the declarative-behaviour layer (with {@link Rule}): it lets a
 * design-time tool draw, and tooling generate, the <em>mechanical</em> slice of
 * behaviour (roll-ups, formulas, simple computed values) that is otherwise
 * hand-written. Genuinely-complex logic stays code — {@code @Derived} is strictly
 * opt-in, so an un-annotated value is hand-written by definition (the escape
 * hatch).
 *
 * <h2>Basic usage</h2>
 * {@snippet lang="java" :
 * @Derived(expression = "lineItems.sum(i -> i.amount)", dependsOn = {"lineItems"})
 * public BigDecimal total() { ... }   // body is the manual fallback until generated
 * }
 *
 * <p>Cross-aggregate derivations are allowed — a {@link #dependsOn()} entry may
 * be a related-entity path (e.g. {@code "customer.tier"}), not only a sibling
 * field name. The expression syntax is named by {@link #language()}; the SDK
 * stores it verbatim and interprets nothing (zero runtime coupling).
 *
 * <h2>Open-Core status — RESERVED, generation pending tooling</h2>
 * <p>This is a declared shape, not yet a live computation. The annotation is
 * defined now; its {@code DerivedMetadata} AST record is the next slice, and no
 * build-time processor extracts {@code @Derived} nor does any code generator
 * consume it yet — so declaring it today has no generated effect. The surface is
 * reserved while the {@code DerivedMetadata} record, the {@code exeris-tooling}
 * processor extraction, the {@code exeris-sdk-source-model-io} reader (in
 * lock-step parity), and the codegen consumer are wired; see {@code ROADMAP.md}
 * and the declarative-behaviour RFC {@code RFC-2026-06-18} (ACCEPTED).
 *
 * @since 0.7
 * @see Rule
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface Derived {

    /**
     * The expression that computes the value, written in {@link #language()}.
     *
     * @return the derivation expression
     */
    String expression();

    /**
     * Expression-language tag. Empty selects the SDK default ({@code "spel"}),
     * which is the convention the existing {@code condition} / {@code visibleWhen}
     * expressions already use; name an alternative evaluator explicitly only when
     * it differs.
     *
     * @return the expression-language tag, or {@code ""} for the default
     */
    String language() default "";

    /**
     * Dependency hints for the design-time dependency graph: sibling field names
     * or related-entity paths (e.g. {@code "customer.tier"}). Stored as plain
     * strings; path resolution against the relationship graph is build-time
     * tooling's job, not the SDK's.
     *
     * @return the dependency hints
     */
    String[] dependsOn() default {};
}
