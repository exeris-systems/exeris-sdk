package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

/**
 * AST facet for a {@code @Rule} — a named declarative invariant / guard over an
 * entity or field, the {@code @Rule} half of the declarative-behaviour layer
 * (with {@link DerivedMetadata}). Carried as a list by
 * {@link DomainMetadata#rules()}.
 *
 * <p>The {@code expression} is stored verbatim and tagged by {@code language};
 * {@code source-model} interprets neither (zero runtime coupling). {@code message}
 * is a literal or an i18n bundle key (the same key-or-literal convention as the
 * 0.6 {@code *Key} fields). Blank {@code message} / {@code severity} /
 * {@code language} normalize to {@code null}; the consumer applies the semantic
 * default ({@link #effectiveSeverity()} ⇒ {@link #DEFAULT_SEVERITY},
 * {@link #effectiveLanguage()} ⇒ {@link #DEFAULT_LANGUAGE}), so the common case
 * carries neither on the wire. See {@code RFC-2026-06-18}.
 *
 * @param name the rule's identity
 * @param expression the invariant that must hold
 * @param message the text reported when it does not
 * @param severity how a violation is treated
 * @param language the language {@link #expression()} is written in
 * @since 0.7.0
 * @see DerivedMetadata
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RuleMetadata(
        String name,
        String expression,
        String message,
        String severity,
        String language
) {

    /** The severity assumed when none is declared. */
    public static final String DEFAULT_SEVERITY = "ERROR";

    /** The expression-language tag assumed when none is declared. */
    public static final String DEFAULT_LANGUAGE = "spel";

    /**
     * Compact constructor; applies this record's normalization rules.
     */
    public RuleMetadata {
        Objects.requireNonNull(name, "name is required");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        Objects.requireNonNull(expression, "expression is required");
        if (expression.isBlank()) {
            throw new IllegalArgumentException("expression must not be blank");
        }
        if (message != null && message.isBlank()) {
            message = null;
        }
        if (severity != null && severity.isBlank()) {
            severity = null;
        }
        if (language != null && language.isBlank()) {
            language = null;
        }
    }

    /**
     * A rule with just a name + invariant expression (default severity / language, no message).
     *
     * @param name the {@code name} the result carries
     * @param expression the {@code expression} the result carries
     * @return the {@code RuleMetadata}
     */
    public static RuleMetadata of(String name, String expression) {
        return new RuleMetadata(name, expression, null, null, null);
    }

    /**
     * The effective severity: the declared one, or {@link #DEFAULT_SEVERITY}.
     *
     * @return the {@code String}
     */
    @JsonIgnore
    public String effectiveSeverity() {
        return severity != null ? severity : DEFAULT_SEVERITY;
    }

    /**
     * The effective language tag: the declared one, or {@link #DEFAULT_LANGUAGE}.
     *
     * @return the {@code String}
     */
    @JsonIgnore
    public String effectiveLanguage() {
        return language != null ? language : DEFAULT_LANGUAGE;
    }

    /**
     * Whether a violation message was declared. The compact constructor
     * normalizes a blank {@code message} to {@code null}, so a present message
     * is always non-blank.
          *
     * @return the {@code boolean}
     */
    @JsonIgnore
    public boolean hasMessage() {
        return message != null;
    }
}
