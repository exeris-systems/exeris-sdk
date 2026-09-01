package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Objects;

/**
 * AST facet for a {@code @Derived} field/value — a read-only value computed from
 * other fields or related entities (a roll-up, formula, or simple derivation),
 * the {@code @Derived} half of the declarative-behaviour layer (with
 * {@link RuleMetadata}). Carried by {@link FieldMetadata#derived()}.
 *
 * <p>The {@code expression} is stored verbatim and tagged by {@code language};
 * {@code source-model} interprets neither (zero runtime coupling). A blank
 * {@code language} normalizes to {@code null} and {@link #effectiveLanguage()}
 * applies the {@link #DEFAULT_LANGUAGE} default, so the common case carries no
 * language on the wire. {@code dependsOn} entries are dependency hints — sibling
 * field names or related-entity paths (e.g. {@code "customer.tier"}) — stored as
 * plain strings; resolution against the relationship graph is build-time
 * tooling's job. See {@code RFC-2026-06-18}.
 *
 * @param expression the expression producing the field's value
 * @param language the language {@link #expression()} is written in
 * @param dependsOn the fields the expression reads, which is what tells a generator when to
 *        recompute
 * @since 0.7.0
 * @see RuleMetadata
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DerivedMetadata(
        String expression,
        String language,
        List<String> dependsOn
) {

    /** The expression-language tag assumed when none is declared. */
    public static final String DEFAULT_LANGUAGE = "spel";

    public DerivedMetadata {
        Objects.requireNonNull(expression, "expression is required");
        if (expression.isBlank()) {
            throw new IllegalArgumentException("expression must not be blank");
        }
        if (language != null && language.isBlank()) {
            language = null;
        }
        // Empty dependsOn serializes as [] (the AST's NON_NULL list convention,
        // as for every other list-valued record) — intentionally not dropped.
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
    }

    /** A derivation with just an expression (default language, no dependency hints). */
    public static DerivedMetadata of(String expression) {
        return new DerivedMetadata(expression, null, List.of());
    }

    /** A derivation with an expression and explicit dependency hints. */
    public static DerivedMetadata of(String expression, List<String> dependsOn) {
        return new DerivedMetadata(expression, null, dependsOn);
    }

    /** The effective language tag: the declared one, or {@link #DEFAULT_LANGUAGE}. */
    @JsonIgnore
    public String effectiveLanguage() {
        return language != null ? language : DEFAULT_LANGUAGE;
    }

    /** Whether any dependency hints were declared. */
    @JsonIgnore
    public boolean hasDependencies() {
        return !dependsOn.isEmpty();
    }
}
