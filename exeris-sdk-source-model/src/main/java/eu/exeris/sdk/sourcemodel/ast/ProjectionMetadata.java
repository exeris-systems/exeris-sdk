package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Metadata for projections (read-only views).
 *
 * @author Exeris Team
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectionMetadata(
        String name,
        String description,
        List<String> fields,
        boolean cacheable
) {
    public static ProjectionMetadata simple(String name, List<String> fields) {
        return new ProjectionMetadata(name, null, fields, false);
    }
}

