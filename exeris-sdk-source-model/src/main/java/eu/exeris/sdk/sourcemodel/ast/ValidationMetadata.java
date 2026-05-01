package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Metadata for field validation rules.
 *
 * @author Exeris Team
 * @since 0.1.0
 */
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

