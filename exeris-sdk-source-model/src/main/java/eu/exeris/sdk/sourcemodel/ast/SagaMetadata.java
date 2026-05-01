package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Metadata for saga orchestrations.
 * Supports full saga configuration including triggers, compensation, and monitoring.
 *
 * @author Exeris Team
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SagaMetadata(
        String name,
        String description,
        int version,
        List<SagaStepMetadata> steps,
        CompensationStrategy compensationStrategy,
        CompensationOrder compensationOrder,
        String timeout,
        String compensationTimeout,
        int maxRetries,
        String retryBackoff,
        SagaTrigger trigger,
        boolean persistent,
        String stateClass,
        List<String> permissions,
        MonitoringConfig monitoring
) {
    public static SagaMetadata simple(String name) {
        return new SagaMetadata(name, null, 1, List.of(), CompensationStrategy.ALL_OR_NOTHING,
                CompensationOrder.REVERSE, "PT30M", "PT10M", 3, "PT1S", null, true, null, List.of(), null);
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public boolean hasSteps() { return steps != null && !steps.isEmpty(); }
    public boolean hasTrigger() { return trigger != null; }

    public enum CompensationStrategy {
        /** All steps must be compensated or saga fails */
        ALL_OR_NOTHING,
        /** Best effort - continue compensation even on failures */
        BEST_EFFORT,
        /** Custom compensation handler decides */
        CUSTOM
    }

    public enum CompensationOrder {
        /** Compensate in reverse order of execution */
        REVERSE,
        /** Compensate in same order as execution */
        FORWARD,
        /** Parallel compensation */
        PARALLEL
    }

    /**
     * Saga trigger configuration.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SagaTrigger(
            TriggerType type,
            String source,
            String topic,
            String cronExpression,
            String condition
    ) {
        public static SagaTrigger onEvent(String eventClass, String topic) {
            return new SagaTrigger(TriggerType.EVENT, eventClass, topic, null, null);
        }

        public static SagaTrigger scheduled(String cron) {
            return new SagaTrigger(TriggerType.SCHEDULED, null, null, cron, null);
        }

        public static SagaTrigger manual() {
            return new SagaTrigger(TriggerType.MANUAL, null, null, null, null);
        }
    }

    public enum TriggerType {
        EVENT,
        SCHEDULED,
        MANUAL,
        API
    }

    /**
     * Saga monitoring configuration.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MonitoringConfig(
            boolean metricsEnabled,
            boolean tracingEnabled,
            String alertOnFailure,
            String slaThreshold
    ) {
        public static MonitoringConfig enabled() {
            return new MonitoringConfig(true, true, null, null);
        }
    }

    public static final class Builder {
        private final String name;
        private String description;
        private int version = 1;
        private List<SagaStepMetadata> steps = List.of();
        private CompensationStrategy compensationStrategy = CompensationStrategy.ALL_OR_NOTHING;
        private CompensationOrder compensationOrder = CompensationOrder.REVERSE;
        private String timeout = "PT30M";
        private String compensationTimeout = "PT10M";
        private int maxRetries = 3;
        private String retryBackoff = "PT1S";
        private SagaTrigger trigger;
        private boolean persistent = true;
        private String stateClass;
        private List<String> permissions = List.of();
        private MonitoringConfig monitoring;

        private Builder(String name) { this.name = name; }

        public Builder description(String v) { this.description = v; return this; }
        public Builder version(int v) { this.version = v; return this; }
        public Builder steps(List<SagaStepMetadata> v) { this.steps = v; return this; }
        public Builder compensationStrategy(CompensationStrategy v) { this.compensationStrategy = v; return this; }
        public Builder compensationOrder(CompensationOrder v) { this.compensationOrder = v; return this; }
        public Builder timeout(String v) { this.timeout = v; return this; }
        public Builder compensationTimeout(String v) { this.compensationTimeout = v; return this; }
        public Builder maxRetries(int v) { this.maxRetries = v; return this; }
        public Builder retryBackoff(String v) { this.retryBackoff = v; return this; }
        public Builder trigger(SagaTrigger v) { this.trigger = v; return this; }
        public Builder persistent(boolean v) { this.persistent = v; return this; }
        public Builder stateClass(String v) { this.stateClass = v; return this; }
        public Builder permissions(List<String> v) { this.permissions = v; return this; }
        public Builder monitoring(MonitoringConfig v) { this.monitoring = v; return this; }

        public SagaMetadata build() {
            return new SagaMetadata(name, description, version, steps, compensationStrategy, compensationOrder,
                    timeout, compensationTimeout, maxRetries, retryBackoff, trigger, persistent, stateClass,
                    permissions, monitoring);
        }
    }
}

