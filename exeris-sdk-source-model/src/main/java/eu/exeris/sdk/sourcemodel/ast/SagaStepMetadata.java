package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Metadata for saga steps with full configuration for parallel execution,
 * conditions, dependencies, and compensation.
 *
 *
 * @param name the step's identity, unique within its saga. Renaming it is a breaking change for
 *        in-flight sagas rather than a refactor: a parked snapshot records the name it stopped
 *        at, and a wake whose plan no longer carries that name fails closed (kernel ADR-062)
 *
 * @param description human-readable prose for generated documentation
 * @param order the step's position in the saga's sequence. Live: both producers sort the step
 *        list by it, so list order is {@code order} and the generator's list-order emission
 *        follows it. Changing it is a drain-before-deploy operation for sagas already running
 *        (kernel ADR-062), not a refactor
 * @param service the service the step invokes
 * @param command the command the step sends to that service
 * @param compensation the command that undoes this step when a later one fails
 * @param timeout how long the step may run before it is treated as failed, as an ISO-8601
 *        duration
 *
 * @param maxRetries how many times the step is retried before the saga compensates
 * @param retryBackoff the backoff strategy between retries
 * @param parallel whether the step may run alongside its siblings rather than after them.
 *        <strong>Recorded intent only:</strong> the attribute is extracted into this component and
 *        no generator reads it — the emitted flow is a strict linear chain. Concurrency must first
 *        be expressible in the kernel's {@code FlowDefinition}
 * @param required whether the saga fails when this step fails, or carries on
 * @param condition an expression gating whether the step runs at all
 * @param skipOnConditionFalse whether a false {@link #condition()} skips the step rather than
 *        failing the saga
 *
 * @param dependsOn the names of steps that must complete before this one starts
 * @param producesEvents the events the step publishes on success
 * @param inputMapping how the step's input is built from the saga's accumulated state
 * @param outputMapping how the step's result is written back into that state
 * @param errorHandler the handler invoked when the step fails
 * @param kind the step's shape — the discriminator distinguishing an ordinary step from a
 *        compensation or a terminal one
 *
 * @since 0.1
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
    /**
     * Creates a minimal {@code SagaStepMetadata}, with only the essentials set.
     *
     * @param name the {@code name} the result carries
     * @param order the {@code order} the result carries
     * @param command the {@code command} the result carries
     * @return the {@code SagaStepMetadata}
     */
    public static SagaStepMetadata simple(String name, int order, String command) {
        return new SagaStepMetadata(name, null, order, null, command, null, "PT5M", 3, "PT1S",
                false, true, null, false, List.of(), List.of(), null, null, null, null);
    }

    /**
     * Starts a builder for a {@code SagaStepMetadata}.
     *
     * @param name the {@code name} the result carries
     * @param order the {@code order} the result carries
     * @return a new builder
     */
    public static Builder builder(String name, int order) {
        return new Builder(name, order);
    }

    /**
     * Whether a non-blank {@code compensation} is declared.
     *
     * @return {@code true} when {@link #compensation()} is set and not blank
     */
    public boolean hasCompensation() { return compensation != null && !compensation.isBlank(); }
    /**
     * Whether any {@code dependsOn} is declared.
     *
     * @return {@code true} when {@link #dependsOn()} is neither null nor empty
     */
    public boolean hasDependencies() { return dependsOn != null && !dependsOn.isEmpty(); }
    /**
     * Whether a non-blank {@code condition} is declared.
     *
     * @return {@code true} when {@link #condition()} is set and not blank
     */
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
     * @since 0.7
          *
     * @return the {@code StepKind}
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
     * @since 0.7
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
     *
     * @param expression an expression building the step's whole input from saga state
     * @param fieldMappings per-field mappings, when the input is assembled field by field
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InputMapping(
            String expression,
            List<FieldMapping> fieldMappings
    ) {
        /**
         * Creates a {@code InputMapping}.
         *
         * @param expr the {@code expr} the result carries
         * @return the {@code InputMapping}
         */
        public static InputMapping expression(String expr) {
            return new InputMapping(expr, null);
        }

        /**
         * Creates a {@code InputMapping}.
         *
         * @param mappings the {@code mappings} the result carries
         * @return the {@code InputMapping}
         */
        public static InputMapping fields(List<FieldMapping> mappings) {
            return new InputMapping(null, mappings);
        }
    }

    /**
     * Output mapping from step result to saga state.
     *
     * @param expression an expression writing the step's whole result back into saga state
     * @param fieldMappings per-field mappings, when the result is written back field by field
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OutputMapping(
            String expression,
            List<FieldMapping> fieldMappings
    ) {
        /**
         * Creates a {@code OutputMapping}.
         *
         * @param expr the {@code expr} the result carries
         * @return the {@code OutputMapping}
         */
        public static OutputMapping expression(String expr) {
            return new OutputMapping(expr, null);
        }
    }

    /**
     * Field-level mapping.
     *
     * @param source the field read from
     * @param target the field written to
     * @param transform the transformation applied in between
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FieldMapping(
            String source,
            String target,
            String transform
    ) {
        /**
         * Creates a {@code FieldMapping}.
         *
         * @param source the {@code source} the result carries
         * @param target the {@code target} the result carries
         * @return the {@code FieldMapping}
         */
        public static FieldMapping direct(String source, String target) {
            return new FieldMapping(source, target, null);
        }
    }

    /**
     * A mutable builder for {@code FieldMapping}.
     *
     * <p>Each setter sets the record component of the same name. Those components are
     * documented by the record's own {@code @param} tags and are deliberately not restated
     * here — a per-setter repetition of the component's meaning is filler, and filler is what
     * makes generated javadoc worth less than none.
     */
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

        /**
         * Builds the {@code SagaStepMetadata} from this builder's current state.
         *
         * @return the built {@code SagaStepMetadata}
         */
        public SagaStepMetadata build() {
            return new SagaStepMetadata(name, description, order, service, command, compensation, timeout,
                    maxRetries, retryBackoff, parallel, required, condition, skipOnConditionFalse,
                    dependsOn, producesEvents, inputMapping, outputMapping, errorHandler, kind);
        }
    }
}

