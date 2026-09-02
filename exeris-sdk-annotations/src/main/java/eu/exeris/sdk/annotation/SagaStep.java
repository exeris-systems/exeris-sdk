package eu.exeris.sdk.annotation;

import java.lang.annotation.*;

/**
 * Defines a step within a saga orchestration.
 *
 * <p>Each step represents an action to be executed as part of the saga,
 * with optional compensation for rollback on failure.
 *
 * <h2>Basic Usage:</h2>
 * <pre>{@code
 * @SagaStep(
 *     order = 1,
 *     name = "reserveInventory",
 *     service = "inventory-service",
 *     command = "ReserveInventoryCommand",
 *     compensation = "ReleaseInventoryCommand"
 * )
 * public SagaAction reserveInventory() {
 *     return new SagaAction.ExecuteCommand(
 *         new ReserveInventoryCommand(state.getProductId(), state.getQuantity())
 *     );
 * }
 * }</pre>
 *
 * <h2>Parallel Execution — declared, not yet executed:</h2>
 * <pre>{@code
 * @SagaStep(order = 2, name = "notifyWarehouse", parallel = true, ...)
 * public SagaAction notifyWarehouse() { ... }
 *
 * @SagaStep(order = 2, name = "notifyShipping", parallel = true, ...)
 * public SagaAction notifyShipping() { ... }
 * // Declares that these two may run together. Today they do not: the generated
 * // flow is a strict linear chain, so notifyShipping runs after notifyWarehouse.
 * // See parallel() for why, and for what has to change first.
 * }</pre>
 *
 * <h2>Conditional Step:</h2>
 * <pre>{@code
 * @SagaStep(
 *     order = 3,
 *     name = "applyDiscount",
 *     condition = "state.totalAmount > 100",
 *     skipOnConditionFalse = true
 * )
 * public SagaAction applyDiscount() { ... }
 * }</pre>
 *
 * <h2>With Dependencies:</h2>
 * <pre>{@code
 * @SagaStep(
 *     order = 4,
 *     name = "finalizeOrder",
 *     dependsOn = {"reserveInventory", "processPayment"},
 *     required = true
 * )
 * public SagaAction finalizeOrder() { ... }
 * }</pre>
 *
 * @author Exeris SDK Team
 * @version 0.1.0
 * @since 0.1.0
 * @see Saga
 * @see DomainEvent
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
@Documented
@Repeatable(SagaSteps.class)
@SuppressWarnings({"PMD.ExcessivePublicCount", "PMD.TooManyMethods"})
public @interface SagaStep {

    // ═══════════════════════════════════════════════════════════════════════════
    // IDENTITY
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Step execution order (lower = first).
     *
     * <p><strong>This is live, and it is the only thing that decides the sequence.</strong> Both
     * producers read it and sort by it — {@code ExerisDomainProcessor.extractSagaSteps} and the
     * {@code exeris-sdk-source-model-io} reader, which mirrors it deliberately. The Java kernel
     * generator then emits a strict linear chain in list order and says it does not consult
     * {@code order()}; that is true of the generator alone and harmless, because the list it
     * receives was already sorted. Where a method sits in the file decides nothing except a tie:
     * the sort is stable, so equal {@code order} keeps declaration sequence.
     *
     * <p><strong>Changing it is a breaking change for sagas already running.</strong> Kernel
     * ADR-062 binds resume to a step's <em>name at its position</em>: a parked flow records where
     * it stopped, and a wake whose plan carries a different name there fails closed rather than
     * resuming into the wrong step. Reordering steps is therefore a drain-before-deploy operation,
     * not a refactor — the same procedure a rename requires.
     *
     * <p><strong>No default, deliberately.</strong> Giving one would let every step share it, and
     * a stable sort would then quietly hand the sequence to declaration position — turning an
     * innocuous method move into the breaking change described above, with nothing in the source
     * to show it happened. Requiring the value keeps the ordering a statement the author made.
     *
     * <p>For what "same order" is documented to mean, see {@link #parallel()} — and read its note
     * on what the platform does with it today.
     *
     * @return execution order
     */
    int order();

    /**
     * Step name (unique within saga).
     * <p>Used for logging, monitoring, and dependency references.
     *
     * <p><strong>Kernel v0.11 (ADR-062) — this name is step identity.</strong>
     * The uniqueness above stopped being a convention: the kernel's
     * {@code FlowDefinition} rejects a definition whose step names collide, so
     * a duplicate is a registration failure downstream rather than a confusing
     * log line. A parked flow also records the name of the step it stopped at,
     * and resume fails closed when the plan at that position now carries a
     * different name — which makes <em>renaming</em> a step a breaking change
     * for sagas already in flight, not just a refactor. The kernel's prescribed
     * procedure is to drain in-flight sagas before deploying a rename or a
     * reorder.
     *
     * @return step name
     */
    String name();

    /**
     * Human-readable description.
     *
     * @return step description
     */
    String description() default "";

    /**
     * Step tags for categorization.
     *
     * @return tags
     */
    String[] tags() default {};

    /**
     * The behavioural kind of this step — invoke / compensate / await-event /
     * await-timer. Today the kind is only <em>implied</em> by which of
     * {@link #service()} / {@link #command()} / {@link #compensation()} is
     * set; declaring it makes the await/dispatch/compensate body generable
     * rather than hand-written.
     *
     * <p>{@link StepKind#UNSPECIFIED} (the default) exists only on the
     * annotation side — annotation attributes cannot default to {@code null} —
     * and maps to an <em>absent</em> AST {@code kind}: the AST's
     * {@code SagaStepMetadata.effectiveKind()} then infers {@code INVOKE} /
     * {@code COMPENSATE} from the step's command/compensation structure. The
     * await kinds are never inferred — a pure await step looks identical to an
     * empty step from the structural fields alone — so
     * {@link StepKind#AWAIT_EVENT} / {@link StepKind#AWAIT_TIMER} always
     * require this attribute explicitly.
     *
     * <p><strong>Open-Core status — reserved, extraction pending tooling:</strong>
     * the AST twin ({@code SagaStepMetadata.kind}, since 0.7.0) already exists,
     * but no build-time processor extracts this attribute yet and the
     * {@code -io} reader deliberately stays in parity by omission — the two
     * flip together in {@code exeris-tooling} (ADR-042 lock-step parity).
     * Until that coordinated flip, declaring a kind has no generated effect.
     *
     * @return the step kind, or {@link StepKind#UNSPECIFIED} to defer to
     *         structural inference
     * @since 0.9.0
     */
    StepKind kind() default StepKind.UNSPECIFIED;

    // ═══════════════════════════════════════════════════════════════════════════
    // TARGET SERVICE
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Target service name.
     * <p>Service that will execute the command.
     *
     * @return service name
     */
    String service();

    /**
     * Target service version.
     * <p>For versioned service routing.
     *
     * @return service version
     */
    String serviceVersion() default "";

    /**
     * Service endpoint (for HTTP dispatch).
     * <p>Override default endpoint derived from command.
     *
     * @return endpoint path
     */
    String endpoint() default "";

    /**
     * Service instance selection strategy.
     *
     * @return instance selection
     */
    InstanceSelection instanceSelection() default InstanceSelection.ANY;

    /**
     * Specific instance ID to route to (if selection = SPECIFIC).
     *
     * @return instance ID
     */
    String instanceId() default "";

    /**
     * Sticky session key (if selection = STICKY).
     * <p>SpEL expression for session affinity key.
     *
     * @return sticky key expression
     */
    String stickyKey() default "";

    // ═══════════════════════════════════════════════════════════════════════════
    // COMMAND CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Command type name to execute.
     * <p>Can be fully qualified class name or simple name.
     *
     * @return command name
     */
    String command();

    /**
     * Command class (alternative to string name).
     *
     * @return command class
     */
    Class<?> commandClass() default void.class;

    /**
     * Method name that builds the command.
     * <p>Method in saga class that creates command from state.
     *
     * @return builder method name
     */
    String commandBuilder() default "";

    /**
     * Command payload expression (SpEL).
     * <p>Build command inline from state.
     *
     * @return payload expression
     */
    String commandPayloadExpression() default "";

    /**
     * Command headers.
     *
     * @return headers
     */
    CommandHeader[] commandHeaders() default {};

    /**
     * Expected success events.
     * <p>Events that indicate command completed successfully.
     *
     * @return success event names
     */
    String[] expectedEvents() default {};

    /**
     * Events that indicate command failure.
     * <p>Receiving these events triggers compensation.
     *
     * @return failure event names
     */
    String[] failureEvents() default {};

    /**
     * Timeout events.
     * <p>Events that indicate timeout (optional, can use default timeout).
     *
     * @return timeout event names
     */
    String[] timeoutEvents() default {};

    // ═══════════════════════════════════════════════════════════════════════════
    // COMPENSATION CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Compensation command name.
     * <p>Command to execute on rollback. Empty = no compensation.
     *
     * @return compensation command
     */
    String compensation() default "";

    /**
     * Compensation command class.
     *
     * @return compensation class
     */
    Class<?> compensationClass() default void.class;

    /**
     * Method that builds compensation command.
     *
     * @return builder method name
     */
    String compensationBuilder() default "";

    /**
     * Compensation payload expression (SpEL).
     *
     * @return payload expression
     */
    String compensationPayloadExpression() default "";

    /**
     * Service for compensation (if different from main service).
     *
     * @return compensation service
     */
    String compensationService() default "";

    /**
     * Whether compensation is required.
     * <p>If false, compensation failure doesn't block saga.
     *
     * @return true if required
     */
    boolean compensationRequired() default true;

    /**
     * Compensation timeout.
     *
     * @return timeout duration
     */
    String compensationTimeout() default "PT1M";

    /**
     * Max retries for compensation.
     *
     * @return max retries
     */
    int compensationRetries() default 5;

    /**
     * Delay between compensation retries.
     *
     * @return retry delay
     */
    String compensationRetryDelay() default "PT5S";

    /**
     * Continue saga compensation if this step's compensation fails.
     *
     * @return true to continue
     */
    boolean continueOnCompensationFailure() default true;

    /**
     * Expected events for successful compensation.
     *
     * @return success events
     */
    String[] compensationExpectedEvents() default {};

    // ═══════════════════════════════════════════════════════════════════════════
    // TIMEOUT & RETRY
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Step timeout (ISO-8601 duration).
     * <p>Step fails if not completed within timeout.
     *
     * @return timeout duration
     */
    String timeout() default "PT30S";

    /**
     * Maximum retry attempts for this step.
     *
     * @return max retries
     */
    int retries() default 3;

    /**
     * Initial delay before first retry.
     *
     * @return initial delay
     */
    String retryDelay() default "PT1S";

    /**
     * Retry backoff multiplier.
     *
     * @return backoff multiplier
     */
    double retryBackoff() default 2.0;

    /**
     * Maximum delay between retries.
     *
     * @return max delay
     */
    String maxRetryDelay() default "PT30S";

    /**
     * Jitter factor for retry delays (0.0 - 1.0).
     *
     * @return jitter factor
     */
    double retryJitter() default 0.1;

    /**
     * Exceptions that should trigger retry.
     *
     * @return retryable exceptions
     */
    Class<? extends Throwable>[] retryOn() default {};

    /**
     * Exceptions that should NOT trigger retry.
     *
     * @return non-retryable exceptions
     */
    Class<? extends Throwable>[] noRetryOn() default {};

    /**
     * Retry on timeout.
     *
     * @return true to retry on timeout
     */
    boolean retryOnTimeout() default true;

    /**
     * Retry on network errors.
     *
     * @return true to retry on network error
     */
    boolean retryOnNetworkError() default true;

    // ═══════════════════════════════════════════════════════════════════════════
    // EXECUTION OPTIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Whether step is required for saga success.
     * <p>Optional steps can fail without triggering compensation.
     *
     * @return true if required
     */
    boolean required() default true;

    /**
     * Step can run in parallel with other parallel steps of same order.
     *
     * <p><strong>Not honoured today — the generated flow is sequential.</strong> The attribute is
     * extracted and reaches {@code SagaStepMetadata.parallel()}, and there it stops: no generator
     * reads it, and the Java kernel generator emits a strict linear chain of transitions. Two
     * steps sharing an {@link #order()} and both declaring {@code parallel = true} are emitted one
     * after the other.
     *
     * <p>This is not a gap {@code exeris-tooling} can close on its own. Concurrency has to be
     * expressible in the kernel's {@code FlowDefinition} before a generator can emit it; until it
     * is, a linear chain is the only correct compilation of these steps, and the honest reading of
     * this attribute is "recorded author intent", not "runs in parallel".
     *
     * @return true for parallel execution
     */
    boolean parallel() default false;

    /**
     * Steps that must complete before this step starts.
     * <p>Overrides order-based sequencing.
     *
     * @return dependency step names
     */
    String[] dependsOn() default {};

    /**
     * Condition expression to execute this step (SpEL).
     * <p>Evaluated against saga state.
     *
     * @return condition expression
     */
    String condition() default "";

    /**
     * Skip step if condition is false (vs fail).
     *
     * @return true to skip, false to fail
     */
    boolean skipOnConditionFalse() default true;

    /**
     * Wait for all parallel steps to complete before continuing.
     * <p>Only applies to parallel steps.
     *
     * <p><strong>Inert while {@link #parallel()} is.</strong> It qualifies a concurrency the
     * generated flow does not yet express — a linear chain has nothing to wait for — and it is
     * neither extracted nor carried on {@code SagaStepMetadata}. It becomes meaningful in the same
     * change that makes {@code parallel} meaningful, and not before.
     *
     * @return true to wait for all
     */
    boolean waitForAll() default true;

    /**
     * Fail fast on parallel step failure.
     * <p>Cancel other parallel steps immediately.
     *
     * <p><strong>Inert while {@link #parallel()} is</strong>, on the same terms as
     * {@link #waitForAll()}: it describes cancelling siblings that a linear chain never starts
     * concurrently, and it is neither extracted nor carried on {@code SagaStepMetadata}.
     *
     * @return true for fail fast
     */
    boolean failFast() default false;

    // ═══════════════════════════════════════════════════════════════════════════
    // IDEMPOTENCY
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Whether step execution is idempotent.
     * <p>Idempotent steps can be safely retried.
     *
     * @return true if idempotent
     */
    boolean idempotent() default true;

    /**
     * Idempotency key expression (SpEL).
     * <p>Unique key for deduplication.
     *
     * @return key expression
     */
    String idempotencyKey() default "";

    /**
     * Check idempotency before execution.
     * <p>Skip if already executed with same key.
     *
     * @return true to check
     */
    boolean checkIdempotency() default true;

    // ═══════════════════════════════════════════════════════════════════════════
    // STATE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Fields to store in saga state after step completes.
     * <p>Field paths from response to copy to state.
     *
     * @return field mappings (format: "responseField:stateField")
     */
    String[] storeInState() default {};

    /**
     * Response handler method.
     * <p>Method to process command response and update state.
     *
     * @return handler method name
     */
    String responseHandler() default "";

    /**
     * Error handler method.
     * <p>Called on step failure before compensation decision.
     *
     * @return error handler method name
     */
    String errorHandler() default "";

    /**
     * State snapshot before step execution.
     * <p>Store state snapshot for potential rollback.
     *
     * @return true to snapshot
     */
    boolean snapshotStateBeforeStep() default false;

    /**
     * Rollback state on step failure.
     * <p>Restore state snapshot if step fails.
     *
     * @return true to rollback state
     */
    boolean rollbackStateOnFailure() default false;

    // ═══════════════════════════════════════════════════════════════════════════
    // INPUT/OUTPUT
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Input mapping from state to command (SpEL).
     * <p>Map of command field to state expression.
     *
     * @return input mappings
     */
    InputMapping[] inputMappings() default {};

    /**
     * Output mapping from response to state (SpEL).
     * <p>Map of state field to response expression.
     *
     * @return output mappings
     */
    OutputMapping[] outputMappings() default {};

    /**
     * Transform response before processing.
     * <p>SpEL expression to transform response.
     *
     * @return transform expression
     */
    String responseTransform() default "";

    /**
     * Validate response before accepting.
     * <p>SpEL expression returning boolean.
     *
     * @return validation expression
     */
    String responseValidation() default "";

    // ═══════════════════════════════════════════════════════════════════════════
    // MONITORING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Emit metrics for this step.
     *
     * @return true to emit
     */
    boolean emitMetrics() default true;

    /**
     * Custom metric tags.
     *
     * @return metric tags
     */
    MetricTag[] metricTags() default {};

    /**
     * Alert if step duration exceeds threshold.
     *
     * @return alert threshold
     */
    String alertThreshold() default "";

    /**
     * Alert on step failure.
     *
     * @return true to alert
     */
    boolean alertOnFailure() default false;

    /**
     * Log level for this step.
     *
     * @return log level
     */
    LogLevel logLevel() default LogLevel.INFO;

    /**
     * Log command payload.
     * <p>Warning: May expose sensitive data.
     *
     * @return true to log payload
     */
    boolean logPayload() default false;

    /**
     * Log response payload.
     *
     * @return true to log response
     */
    boolean logResponse() default false;

    /**
     * Trace span name override.
     *
     * @return span name
     */
    String traceSpanName() default "";

    // ═══════════════════════════════════════════════════════════════════════════
    // SECURITY
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Required roles to execute this step.
     *
     * @return required roles
     */
    String[] roles() default {};

    /**
     * Required permissions.
     *
     * @return required permissions
     */
    String[] permissions() default {};

    /**
     * Propagate security context to service.
     *
     * @return true to propagate
     */
    boolean propagateSecurityContext() default true;

    // ═══════════════════════════════════════════════════════════════════════════
    // CIRCUIT BREAKER
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Enable circuit breaker for this step.
     *
     * @return true to enable
     */
    boolean circuitBreaker() default false;

    /**
     * Circuit breaker name (shared across steps).
     *
     * @return circuit breaker name
     */
    String circuitBreakerName() default "";

    /**
     * Failure threshold to open circuit.
     *
     * @return failure threshold (percentage)
     */
    int circuitBreakerThreshold() default 50;

    /**
     * Time circuit stays open before half-open.
     *
     * @return open duration
     */
    String circuitBreakerOpenDuration() default "PT30S";

    /**
     * Fallback method when circuit is open.
     *
     * @return fallback method name
     */
    String circuitBreakerFallback() default "";

    // ═══════════════════════════════════════════════════════════════════════════
    // ENUMS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * The behavioural kind of a saga step (see {@link #kind()}).
     *
     * <p>A mirror of the AST-owned {@code SagaStepMetadata.StepKind} — two
     * separate types mapped by name at extraction time (the
     * {@code UI.ComponentType} precedent), never an import of the AST type,
     * preserving the annotation module's zero-dependency contract — plus
     * {@link #UNSPECIFIED}, which exists only on this side because annotation
     * attributes cannot default to {@code null}.
     *
     * @since 0.9.0
     */
    enum StepKind {
        /**
         * No explicit kind declared (the default). Maps to an absent AST
         * {@code kind}; {@code SagaStepMetadata.effectiveKind()} then infers
         * {@code INVOKE} / {@code COMPENSATE} from the command/compensation
         * structure. The await kinds are never inferred.
         */
        UNSPECIFIED,

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
     * Which service instance a step's command is routed to.
     */
    enum InstanceSelection {
        /** Any available instance */
        ANY,

        /** Same instance as previous step */
        STICKY,

        /** Round-robin selection */
        ROUND_ROBIN,

        /** Least loaded instance */
        LEAST_LOADED,

        /** Consistent hashing by key */
        CONSISTENT_HASH,

        /** Specific instance by ID */
        SPECIFIC,

        /** Local instance (in-process) */
        LOCAL
    }

    /**
     * How much detail the generated code logs for this surface.
     */
    enum LogLevel {
        /** Finest detail; every step and its payload. */
        TRACE,
        /** Diagnostic detail useful while developing. */
        DEBUG,
        /** Normal progress worth recording. */
        INFO,
        /** Something unexpected that did not stop the work. */
        WARN,
        /** A failure. */
        ERROR,
        /** No logging at all. */
        OFF
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NESTED ANNOTATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * One header on the command a step dispatches.
     *
     * <p>Supply either a literal {@code value} or a SpEL {@code expression} evaluated
     * against saga state; they are alternatives, and leaving both unset leaves the header
     * empty.
     *
     * <p><strong>Status: RESERVED</strong> — {@link SagaStep#commandHeaders()} is not
     * extracted.
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target({})
    @interface CommandHeader {
        /**
         * Header name.
         *
         * @return the header name
         */
        String name();

        /**
         * Literal header value. Leave unset when {@code expression} supplies it.
         *
         * @return the literal value
         */
        String value() default "";

        /**
         * SpEL expression, evaluated against saga state, producing the value.
         * Leave unset when {@code value} supplies it.
         *
         * @return the value expression
         */
        String expression() default "";
    }

    /**
     * One field of the dispatched command, populated from saga state.
     *
     * <p>Carried by {@link SagaStep#inputMappings()}. Both attributes are mandatory: a
     * mapping that names no target field, or produces no value, describes nothing.
     *
     * <p><strong>Status: RESERVED</strong> — {@link SagaStep#inputMappings()} is not
     * extracted, so the dispatched command is populated by generated defaults rather than
     * by a mapping declared here.
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target({})
    @interface InputMapping {
        /**
         * Field on the command to populate.
         *
         * @return the command field name
         */
        String commandField();

        /**
         * SpEL expression, evaluated against saga state, producing the field's value.
         *
         * @return the state expression
         */
        String stateExpression();
    }

    /**
     * One field of saga state, populated from the step's response.
     *
     * <p>The mirror of {@link InputMapping}, applied after the step completes, so a later
     * step reads what this one returned.
     *
     * <p><strong>Status: RESERVED</strong> — {@link SagaStep#outputMappings()} is not
     * extracted.
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target({})
    @interface OutputMapping {
        /**
         * Field on the saga state to populate.
         *
         * @return the state field name
         */
        String stateField();

        /**
         * SpEL expression, evaluated against the step response, producing the field's value.
         *
         * @return the response expression
         */
        String responseExpression();
    }

    /**
     * One tag on the metrics a step emits.
     *
     * <p>Read only while {@link SagaStep#emitMetrics()} is on. Supply either a literal
     * {@code value} or a SpEL {@code expression} evaluated against saga state.
     *
     * <p><strong>Status: RESERVED</strong> — {@link SagaStep#metricTags()} is not
     * extracted.
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target({})
    @interface MetricTag {
        /**
         * Tag name.
         *
         * @return the tag name
         */
        String name();

        /**
         * Literal tag value. Leave unset when {@code expression} supplies it.
         *
         * @return the literal value
         */
        String value() default "";

        /**
         * SpEL expression, evaluated against saga state, producing the value.
         * Leave unset when {@code value} supplies it.
         *
         * @return the value expression
         */
        String expression() default "";
    }
}
