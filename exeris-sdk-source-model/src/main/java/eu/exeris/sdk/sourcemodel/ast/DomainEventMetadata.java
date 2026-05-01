package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Metadata for domain events.
 *
 * @author Exeris Team
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DomainEventMetadata(
        String name,
        String topic,
        String description,
        String aggregateType
) {
    public static DomainEventMetadata simple(String name) {
        return new DomainEventMetadata(name, null, null, null);
    }

    public static DomainEventMetadata withTopic(String name, String topic) {
        return new DomainEventMetadata(name, topic, null, null);
    }
}
