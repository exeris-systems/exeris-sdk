package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Objects;

/**
 * Metadata for saga orchestrations.
 * Supports full saga configuration including triggers, compensation, and monitoring.
 *
 * @param name the saga's identity
 * @param description human-readable prose for generated documentation
 * @param version the plan version. The kernel keys its plan catalog by name and version: a
 *        parked saga resumes on the exact version it parked under, and an unregistered version
 *        fails closed rather than rebinding to the newest plan (kernel ADR-064)
 *
 * @param steps the saga's steps, in order
 * @param compensationStrategy how compensation is driven when a step fails
 * @param compensationOrder the order compensations run in
 * @param timeout how long the whole saga may run, as an ISO-8601 duration
 * @param compensationTimeout the same bound for the compensation phase
 * @param maxRetries how many times a failed step is retried before compensating
 * @param retryBackoff the backoff strategy between retries
 * @param trigger what starts the saga
 * @param persistent whether saga state survives a restart
 * @param stateClass the type carrying the saga's accumulated state
 * @param permissions the permissions required to start the saga
 * @param monitoring the metrics, tracing and alerting configuration
 * @param transitions the state-machine edges between steps
 * @author Exeris SDK Team
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
        MonitoringConfig monitoring,
        List<SagaTransition> transitions
) {
    /**
     * Compact constructor; applies this record's normalization rules.
     */
    public SagaMetadata {
        transitions = transitions == null ? List.of() : List.copyOf(transitions);
    }

    /**
     * Creates a minimal {@code SagaMetadata}, with only the essentials set.
     *
     * @param name the {@code name} the result carries
     * @return the {@code SagaMetadata}
     */
    public static SagaMetadata simple(String name) {
        return new SagaMetadata(name, null, 1, List.of(), CompensationStrategy.ALL_OR_NOTHING,
                CompensationOrder.REVERSE, "PT30M", "PT10M", 3, "PT1S", null, true, null, List.of(), null, List.of());
    }

    /**
     * Starts a builder for a {@code Builder}.
     *
     * @param name the {@code name} the result carries
     * @return a new builder
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * Whether any {@code steps} is declared.
     *
     * @return {@code true} when {@link #steps()} is neither null nor empty
     */
    public boolean hasSteps() { return steps != null && !steps.isEmpty(); }
    /**
     * Whether a {@code trigger} is declared.
     *
     * @return {@code true} when {@link #trigger()} is present
     */
    public boolean hasTrigger() { return trigger != null; }
    /**
     * Whether any {@code transitions} is declared.
     *
     * @return {@code true} when {@link #transitions()} is neither null nor empty
     */
    public boolean hasTransitions() { return transitions != null && !transitions.isEmpty(); }

    /**
     * How compensation is driven once a saga step fails.
     */
    public enum CompensationStrategy {
        /** All steps must be compensated or saga fails */
        ALL_OR_NOTHING,
        /** Best effort - continue compensation even on failures */
        BEST_EFFORT,
        /** Custom compensation handler decides */
        CUSTOM
    }

    /**
     * The order in which a failed saga's compensations run.
     */
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
     *
     * @param type what kind of occurrence starts the saga
     * @param source the originator the trigger listens to
     * @param topic the messaging topic carrying the triggering message
     * @param cronExpression the schedule, when the saga is started on one
     * @param condition an expression gating whether the trigger fires
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
        /**
         * Creates a {@code SagaTrigger}.
         *
         * @param eventClass the {@code eventClass} the result carries
         * @param topic the {@code topic} the result carries
         * @return the {@code SagaTrigger}
         */
        public static SagaTrigger onEvent(String eventClass, String topic) {
            return new SagaTrigger(TriggerType.EVENT, eventClass, topic, null, null);
        }

        /**
         * Creates a {@code SagaTrigger}.
         *
         * @param cron the {@code cron} the result carries
         * @return the {@code SagaTrigger}
         */
        public static SagaTrigger scheduled(String cron) {
            return new SagaTrigger(TriggerType.SCHEDULED, null, null, cron, null);
        }

        /**
         * A {@code SagaTrigger} started by hand rather than by an occurrence.
         *
         * @return the {@code SagaTrigger}
         */
        public static SagaTrigger manual() {
            return new SagaTrigger(TriggerType.MANUAL, null, null, null, null);
        }
    }

    /**
     * What kind of occurrence starts a saga.
     */
    public enum TriggerType {
        /** A published event starts the saga. */
        EVENT,
        /** A schedule starts it. */
        SCHEDULED,
        /** It is started by hand. */
        MANUAL,
        /** An API call starts it. */
        API
    }

    /**
     * Saga monitoring configuration.
     *
     * @param metricsEnabled whether the saga emits metrics
     * @param tracingEnabled whether the saga emits trace spans
     * @param alertOnFailure where a failure alert is sent
     * @param slaThreshold the duration beyond which the saga is treated as breaching its SLA
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MonitoringConfig(
            boolean metricsEnabled,
            boolean tracingEnabled,
            String alertOnFailure,
            String slaThreshold
    ) {
        /**
         * A {@code MonitoringConfig} with the feature turned on.
         *
         * @return the {@code MonitoringConfig}
         */
        public static MonitoringConfig enabled() {
            return new MonitoringConfig(true, true, null, null);
        }
    }

    /**
     * A typed transition between two saga steps — a first-class, outcome-edged
     * edge in the saga's state-machine graph. Where {@code SagaStepMetadata.order}
     * and {@code dependsOn} order steps without saying <em>on which outcome</em> a
     * branch fires, a {@code SagaTransition} carries exactly that: {@code from}
     * the source step, {@code on} the outcome that fires the edge, {@code to} the
     * next step. Loops are expressed by pointing {@code to} back at an earlier
     * step; a {@code null}/blank {@code to} marks a terminal edge (saga end /
     * abort). The optional {@code guard} (a SpEL expression) narrows an edge that
     * fires only when a condition holds.
     *
     * <p>Like the other saga config types this stores AST-owned enums and plain
     * step names — resolving the edge against the actual step set, and generating
     * the dispatch/await/compensate body, is build-time tooling's job.
     *
     * @param from the step the edge leaves
     * @param to the step the edge enters
     * @param on the outcome that takes this edge
     * @param guard an expression that must hold for the edge to be taken
     * @since 0.7.0
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SagaTransition(
            String from,
            String to,
            TransitionOutcome on,
            String guard
    ) {
        /**
         * Compact constructor; applies this record's normalization rules.
         */
        public SagaTransition {
            Objects.requireNonNull(from, "from");
            if (from.isBlank()) {
                throw new IllegalArgumentException("from must not be blank");
            }
            if (on == null) {
                on = TransitionOutcome.SUCCESS;
            }
            if (to != null && to.isBlank()) {
                to = null;
            }
            if (guard != null && guard.isBlank()) {
                guard = null;
            }
        }

        /**
         * An edge firing on {@code outcome} from {@code from} to {@code to}.
         *
         * @param from the {@code from} the result carries
         * @param to the {@code to} the result carries
         * @param outcome the {@code outcome} the result carries
         * @return the {@code SagaTransition}
         */
        public static SagaTransition ofOutcome(String from, String to, TransitionOutcome outcome) {
            return new SagaTransition(from, to, outcome, null);
        }

        /**
         * A {@code SUCCESS} edge from {@code from} to {@code to}.
         *
         * @param from the {@code from} the result carries
         * @param to the {@code to} the result carries
         * @return the {@code SagaTransition}
         */
        public static SagaTransition success(String from, String to) {
            return new SagaTransition(from, to, TransitionOutcome.SUCCESS, null);
        }

        /**
         * A {@code FAILURE} edge from {@code from} to {@code to}.
         *
         * @param from the {@code from} the result carries
         * @param to the {@code to} the result carries
         * @return the {@code SagaTransition}
         */
        public static SagaTransition failure(String from, String to) {
            return new SagaTransition(from, to, TransitionOutcome.FAILURE, null);
        }

        /**
         * A {@code TIMEOUT} edge from {@code from} to {@code to}.
         *
         * @param from the {@code from} the result carries
         * @param to the {@code to} the result carries
         * @return the {@code SagaTransition}
         */
        public static SagaTransition timeout(String from, String to) {
            return new SagaTransition(from, to, TransitionOutcome.TIMEOUT, null);
        }

        /**
         * Whether this edge is terminal (no target step — saga end / abort).
         * The compact constructor normalizes a blank {@code to} to {@code null},
         * so the terminal invariant is exactly {@code to == null}.
                  *
         * @return the {@code boolean}
         */
        public boolean isTerminal() { return to == null; }

        /**
         * Whether this edge carries a guard condition. The compact constructor
         * normalizes a blank {@code guard} to {@code null}, so a present
         * {@code guard} is always non-blank.
                  *
         * @return the {@code boolean}
         */
        public boolean hasGuard() { return guard != null; }
    }

    /**
     * The outcome of a step that fires a {@link SagaTransition} edge. AST-owned,
     * independent of any annotation-side type.
     *
     * @since 0.7.0
     */
    public enum TransitionOutcome {
        /** The step completed successfully. */
        SUCCESS,
        /** The step failed (typically routes to compensation). */
        FAILURE,
        /** The step timed out. */
        TIMEOUT,
        /** A compensation step finished (rollback edge). */
        COMPENSATED
    }

    /**
     * A mutable builder for {@code SagaTransition}.
     *
     * <p>Each setter sets the record component of the same name. Those components are
     * documented by the record's own {@code @param} tags and are deliberately not restated
     * here — a per-setter repetition of the component's meaning is filler, and filler is what
     * makes generated javadoc worth less than none.
     */
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
        private List<SagaTransition> transitions = List.of();

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
        public Builder transitions(List<SagaTransition> v) { this.transitions = v; return this; }

        /**
         * Builds the {@code SagaMetadata} from this builder's current state.
         *
         * @return the built {@code SagaMetadata}
         */
        public SagaMetadata build() {
            return new SagaMetadata(name, description, version, steps, compensationStrategy, compensationOrder,
                    timeout, compensationTimeout, maxRetries, retryBackoff, trigger, persistent, stateClass,
                    permissions, monitoring, transitions);
        }
    }
}

