package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Metadata for saga steps with full configuration for parallel execution,
 * conditions, dependencies, and compensation.
 *
 * @author Exeris SDK Team
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SagaStepMetadata(
        String name,
        String description,
        int order,
        String service,
        String command,
        String compensation,
        String timeout,
        int maxRetries,
        String retryBackoff,
        boolean parallel,
        boolean required,
        String condition,
        boolean skipOnConditionFalse,
        List<String> dependsOn,
        List<String> producesEvents,
        InputMapping inputMapping,
        OutputMapping outputMapping,
        String errorHandler,
        StepKind kind
) {
    public static SagaStepMetadata simple(String name, int order, String command) {
        return new SagaStepMetadata(name, null, order, null, command, null, "PT5M", 3, "PT1S",
                false, true, null, false, List.of(), List.of(), null, null, null, null);
    }

    public static Builder builder(String name, int order) {
        return new Builder(name, order);
    }

    public boolean hasCompensation() { return compensation != null && !compensation.isBlank(); }
    public boolean hasDependencies() { return dependsOn != null && !dependsOn.isEmpty(); }
    public boolean hasCondition() { return condition != null && !condition.isBlank(); }

    /**
     * The step's effective {@link StepKind}: the explicit {@link #kind()} if one
     * was declared, otherwise inferred from which command/compensation facet is
     * set — {@code COMPENSATE} when only a compensation command is present,
     * {@code INVOKE} when a forward command or target service is present.
     *
     * <p>{@code AWAIT_EVENT} / {@code AWAIT_TIMER} are <strong>not</strong>
     * inferred: a pure await step looks identical to an empty step from the
     * structural fields alone, which is precisely why {@link #kind()} exists.
     * Returns {@code null} when no kind is set and none can be inferred.
     *
     * @since 0.7.0
     */
    public StepKind effectiveKind() {
        if (kind != null) {
            return kind;
        }
        boolean hasForwardCommand = (command != null && !command.isBlank())
                || (service != null && !service.isBlank());
        if (!hasForwardCommand && hasCompensation()) {
            return StepKind.COMPENSATE;
        }
        if (hasForwardCommand) {
            return StepKind.INVOKE;
        }
        return null;
    }

    /**
     * The behavioural kind of a saga step. Today the kind is only <em>implied</em>
     * by which of {@code service}/{@code command}/{@code compensation} is set;
     * declaring it makes the await/dispatch/compensate body generable rather than
     * hand-written. Stored as an AST-owned enum (consistent with the other
     * {@code SagaMetadata} enums), independent of any annotation-side type.
     *
     * @since 0.7.0
     */
    public enum StepKind {
        /** Dispatch a forward command to a service. */
        INVOKE,
        /** Run a compensation (rollback) command. */
        COMPENSATE,
        /** Block until an expected domain event arrives. */
        AWAIT_EVENT,
        /** Block until a timer / timeout elapses. */
        AWAIT_TIMER
    }

    /**
     * Input mapping from saga state to step command.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InputMapping(
            String expression,
            List<FieldMapping> fieldMappings
    ) {
        public static InputMapping expression(String expr) {
            return new InputMapping(expr, null);
        }

        public static InputMapping fields(List<FieldMapping> mappings) {
            return new InputMapping(null, mappings);
        }
    }

    /**
     * Output mapping from step result to saga state.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OutputMapping(
            String expression,
            List<FieldMapping> fieldMappings
    ) {
        public static OutputMapping expression(String expr) {
            return new OutputMapping(expr, null);
        }
    }

    /**
     * Field-level mapping.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FieldMapping(
            String source,
            String target,
            String transform
    ) {
        public static FieldMapping direct(String source, String target) {
            return new FieldMapping(source, target, null);
        }
    }

    public static final class Builder {
        private final String name;
        private final int order;
        private String description;
        private String service;
        private String command;
        private String compensation;
        private String timeout = "PT5M";
        private int maxRetries = 3;
        private String retryBackoff = "PT1S";
        private boolean parallel = false;
        private boolean required = true;
        private String condition;
        private boolean skipOnConditionFalse = false;
        private List<String> dependsOn = List.of();
        private List<String> producesEvents = List.of();
        private InputMapping inputMapping;
        private OutputMapping outputMapping;
        private String errorHandler;
        private StepKind kind;

        private Builder(String name, int order) {
            this.name = name;
            this.order = order;
        }

        public Builder description(String v) { this.description = v; return this; }
        public Builder service(String v) { this.service = v; return this; }
        public Builder command(String v) { this.command = v; return this; }
        public Builder compensation(String v) { this.compensation = v; return this; }
        public Builder timeout(String v) { this.timeout = v; return this; }
        public Builder maxRetries(int v) { this.maxRetries = v; return this; }
        public Builder retryBackoff(String v) { this.retryBackoff = v; return this; }
        public Builder parallel(boolean v) { this.parallel = v; return this; }
        public Builder required(boolean v) { this.required = v; return this; }
        public Builder condition(String v) { this.condition = v; return this; }
        public Builder skipOnConditionFalse(boolean v) { this.skipOnConditionFalse = v; return this; }
        public Builder dependsOn(List<String> v) { this.dependsOn = v; return this; }
        public Builder producesEvents(List<String> v) { this.producesEvents = v; return this; }
        public Builder inputMapping(InputMapping v) { this.inputMapping = v; return this; }
        public Builder outputMapping(OutputMapping v) { this.outputMapping = v; return this; }
        public Builder errorHandler(String v) { this.errorHandler = v; return this; }
        public Builder kind(StepKind v) { this.kind = v; return this; }

        public SagaStepMetadata build() {
            return new SagaStepMetadata(name, description, order, service, command, compensation, timeout,
                    maxRetries, retryBackoff, parallel, required, condition, skipOnConditionFalse,
                    dependsOn, producesEvents, inputMapping, outputMapping, errorHandler, kind);
        }
    }
}

