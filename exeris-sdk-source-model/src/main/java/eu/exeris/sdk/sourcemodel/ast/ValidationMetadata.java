package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Metadata for field validation rules.
 *
 * @deprecated since 0.9.0, for removal in 1.0.0 (ADR-054).
 *     {@link FieldMetadata} is the canonical AST carrier of the constraint
 *     values ({@code min} / {@code max} / {@code minLength} / {@code maxLength}
 *     / {@code pattern}), populated from {@code @Validation} by the build-time
 *     processor and the {@code -io} reader. This record was never populated by
 *     any processor or reader and is not referenced by {@code DomainMetadata}
 *     — it never appeared on the domain wire. DB NOT NULL / not-blank
 *     semantics derive from {@link FieldMetadata#required()} at generator
 *     level (the {@code notNull} / {@code notBlank} components here were an
 *     unrealized aspiration); {@code patternMessage} is dropped as unconsumed
 *     (it has no {@code @Validation} source). Compile-time references migrate
 *     to {@code FieldMetadata}; see {@code MIGRATION.md} (0.8.x → 0.9.x).
 *
 * @author Exeris SDK Team
 * @since 0.1.0
 */
@Deprecated(since = "0.9.0", forRemoval = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ValidationMetadata(
        boolean notNull,
        boolean notBlank,
        Integer minLength,
        Integer maxLength,
        Long min,
        Long max,
        String pattern,
        String patternMessage,
        boolean email,
        boolean url,
        boolean future,
        boolean past
) {
    public static ValidationMetadata withNotNull() {
        return new ValidationMetadata(true, false, null, null, null, null, null, null, false, false, false, false);
    }

    public static ValidationMetadata withNotBlank() {
        return new ValidationMetadata(false, true, null, null, null, null, null, null, false, false, false, false);
    }

    public static ValidationMetadata withLength(int min, int max) {
        return new ValidationMetadata(false, false, min, max, null, null, null, null, false, false, false, false);
    }
}

