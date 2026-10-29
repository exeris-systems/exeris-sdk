package eu.exeris.sdk.annotation;

import java.lang.annotation.*;

/**
 * Defines a saga (long-running transaction) that orchestrates
 * distributed operations across multiple services.
 *
 * <p>Sagas coordinate distributed transactions using compensating actions
 * to maintain eventual consistency when spanning multiple aggregates/services.
 *
 * <h2>Basic Usage:</h2>
 * {@snippet lang="java" :
 * @Saga(
 *     name = "OrderFulfillmentSaga",
 *     description = "Orchestrates order placement, payment, and shipping",
 *     timeout = "PT30M",
 *     maxRetries = 3
 * )
 * public class OrderFulfillmentSaga implements Saga<OrderFulfillmentState> {
 *
 *     @SagaStep(order = 1, name = "reserveInventory", ...)
 *     public SagaAction reserveInventory() { ... }
 *
 *     @SagaStep(order = 2, name = "processPayment", ...)
 *     public SagaAction processPayment() { ... }
 * }
 * }
 *
 * <h2>With Event Trigger:</h2>
 * {@snippet lang="java" :
 * @Saga(
 *     name = "SubscriptionRenewalSaga",
 *     trigger = @SagaTrigger(
 *         type = TriggerType.EVENT,
 *         source = SubscriptionDueForRenewalEvent.class,
 *         topic = "billing.subscriptions.due-for-renewal"
 *     )
 * )
 * public class SubscriptionRenewalSaga implements Saga<RenewalState> {}
 * }
 *
 * <h2>One saga per type</h2>
 * A type hosts at most one saga. This annotation is not {@link Repeatable}, and the metadata
 * both producers build for a type ({@code DomainMetadata.sagaMetadata}) holds a single saga, so
 * there is no place for a second one to land. A second flow over the same entity — a renewal
 * next to a fulfilment, say — is a second type carrying its own {@code @Saga}, with its own
 * {@link #name()} and its own {@link SagaStep} methods.
 *
 * <h2>Several processes over one entity</h2>
 * A {@code @Saga} type needs no {@code @ExerisDomain}, so each process that concerns one entity is
 * hosted on its own type and correlated by the entity's id. The instance id space is shared by
 * every definition the engine runs, and a {@code Start} for an id it holds is a no-op whatever
 * definition its plan names, so each process derives its own instance id from the entity's id
 * rather than reusing it. The kernel offers no external cancel or abort: one process interrupts
 * another cooperatively. The long process parks between legs ({@code FlowOutcome.PARK}); an event
 * wakes it through a {@code FlowChoreographyMapper}; on waking it reads the entity's state and
 * either fails, which compensates the legs it completed, or parks again. The kernel fences one
 * instance's own snapshot row, not the shared entity, and has no distributed lock (kernel
 * ADR-013), so status writes from two processes need a versioned entity
 * ({@code @ExerisDomain(versioned = true)} with {@code @Version}) and a transition guard that
 * refuses a status change from an unexpected state.
 *
 * <h2>Finished instances</h2>
 * With a snapshot store bound, a completed instance leaves a {@code COMPLETED} row and a failed
 * one a {@code FAILED_ROLLEDBACK} row; the kernel never deletes either, so retention belongs to
 * whoever binds the store. While that row exists a redelivered {@code Start} for the same
 * instance is a no-op, whatever definition its plan names, and the retention window is therefore
 * the deduplication window. Deleting the row lets the instance id run again. With no store bound,
 * the no-op holds only for the life of the engine (kernel ADR-013 A1).
 *
 * <p><strong>Status: LIVE</strong> — extracted into {@code SagaMetadata} and read by
 * {@code KernelSagaGenerator} and the TypeScript saga emitter. The generated flow carries
 * {@link #name()}, {@link #version()}, {@link #timeout()} and {@link #maxRetries()} to the kernel
 * and runs its steps as a strict linear chain in declaration order. {@link #trigger()} is read by
 * the {@code -io} reader only. Attributes that name behaviour no kernel flow contract has —
 * per-saga persistence, command dispatch, idempotency, concurrency limits, lifecycle hooks,
 * monitoring — are deprecated for removal, and each one says where that behaviour lives instead.
 * @since 0.1
 * @see DomainEvent
 * @see EventSourced
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Documented
@SuppressWarnings({"PMD.ExcessivePublicCount", "PMD.TooManyMethods"})
public @interface Saga {

    // ═══════════════════════════════════════════════════════════════════════════
    // IDENTITY
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Unique saga name.
     * <p>Used for identification, logging, and monitoring.
     *
     * <p><strong>Naming convention:</strong> PascalCase with "Saga" suffix
     * <ul>
     *   <li>"OrderFulfillmentSaga"</li>
     *   <li>"SubscriptionRenewalSaga"</li>
     *   <li>"TransferMoneySaga"</li>
     * </ul>
     *
     * @return saga name
     */
    String name();

    /**
     * Human-readable description.
     * <p>Explains what this saga orchestrates.
     *
     * @return saga description
     */
    String description() default "";

    /**
     * Saga version — part of the saga's runtime identity.
     *
     * <p><strong>Kernel contract (ADR-064).</strong> This attribute is not a
     * free-form "increment on breaking changes" marker: the kernel keys its plan
     * catalog by {@code (name, version)}, so registering v2 leaves v1 registered
     * and both serve traffic — new instances start on the newest registered version,
     * parked ones resume on the exact version they parked under. A snapshot
     * whose version is not registered <em>fails closed</em> rather than being
     * rebound to the newest plan, and moving a parked saga across versions
     * happens only through a migration transform the application registers
     * ({@code FlowExecutionPlanFactory.registerMigration}). The value is
     * deliberately application-declared rather than derived from the
     * definition's content: a content hash would make an added comment a new
     * version.
     *
     * <p><strong>What that means for an author.</strong> Bumping this is a
     * deployment decision, not a comment. Leaving it alone while changing step
     * structure keeps in-flight sagas resuming onto the changed plan, which is
     * the data-corruption class ADR-062 and ADR-064 exist to close; bumping it
     * without registering a migration parks the old instances until their
     * version is registered again. Monotonic within a name; a definition built
     * without one is version {@code 1}.
     *
     * <p><strong>Open-Core status — extracted on both build paths.</strong>
     * The value reaches {@code SagaMetadata.version} through both the build-time
     * processor and the {@code -io} reader, so the two AST producers agree on it
     * as ADR-042 requires. Two edges, identical on both paths: an absent
     * attribute is {@code 1}, and a value below {@code 1} is carried as
     * written rather than repaired — the kernel refuses
     * it, which is the diagnostic an author needs. One edge differs: the
     * {@code -io} reader is syntactic, so a version supplied through a constant
     * reference or expression, which the processor receives folded by javac,
     * reads there as {@code 1}. Declare it as a literal.
     *
     * <p><strong>Reaching the kernel.</strong> {@code exeris-tooling}
     * transcribes the value into the generated {@code *SagaFlow} through
     * {@code FlowDefinitionBuilder.version(int)}. A generated flow that does not
     * make that call carries the value in metadata and reaches no plan with it,
     * so confirm the generated flow calls {@code version(...)} before relying on
     * a bump to protect in-flight sagas. Do not work around its absence by
     * rebuilding the {@code FlowDefinition} record by hand: for a name never
     * assembled through the builder, that route compiles to a plan carrying its
     * steps and no declared edges, with no diagnostic (the kernel's own note on
     * that setter).
     *
     * @return saga version
     */
    int version() default 1;

    /**
     * Module this saga belongs to, as a descriptive label.
     *
     * <p><strong>Status: RESERVED.</strong> Neither producer reads it and no AST component
     * carries it, so it routes nothing and changes no generated artifact.
     *
     * @return module name
     */
    String module() default "";

    /**
     * Tags for categorization.
     *
     * <p><strong>Status: RESERVED.</strong> Neither producer reads them and no AST component
     * carries them, so they change no generated artifact.
     *
     * @return array of tags
     */
    String[] tags() default {};

    // ═══════════════════════════════════════════════════════════════════════════
    // TRIGGER CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * What triggers this saga.
     * <p>Defines how saga instances are started.
     *
     * @return trigger configuration
     */
    SagaTrigger trigger() default @SagaTrigger;

    /**
     * Correlation ID expression (SpEL).
     * <p>Extracts correlation ID from trigger payload.
     * Used to identify saga instance.
     *
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>"#event.aggregateId"</li>
     *   <li>"#command.orderId"</li>
     *   <li>"#headers['correlationId']"</li>
     * </ul>
     *
     * @return correlation ID expression
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. The kernel identifies an
     *         instance by the 128-bit id the caller passes to {@code FlowScheduler.schedule}, or
     *         that a {@code FlowChoreographyMapper} returns in its {@code Start} or {@code Wake}
     *         decision. The mapper receives the event's routing metadata and no payload, and no
     *         kernel contract evaluates an expression. Derive the id where the saga is scheduled,
     *         or in the mapper.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String correlationIdExpression() default "#trigger.correlationId ?: T(java.util.UUID).randomUUID().toString()";

    /**
     * Tenant ID expression (SpEL).
     * <p>Extracts tenant ID from trigger payload.
     *
     * @return tenant ID expression
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. A flow instance carries
     *         no tenant: {@code FlowContext} exposes the instance id, the definition name, the
     *         current step, the state and the deadline, and nothing else. Keep it with the
     *         application data the steps read, keyed by the instance id.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String tenantIdExpression() default "#trigger.tenantId";

    /**
     * User ID expression (SpEL).
     * <p>Extracts user ID who initiated the saga.
     *
     * @return user ID expression
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. A flow instance carries
     *         no user: {@code FlowContext} exposes the instance id, the definition name, the
     *         current step, the state and the deadline, and nothing else. Keep it with the
     *         application data the steps read, keyed by the instance id.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String userIdExpression() default "#trigger.userId ?: #securityContext.userId";

    /**
     * Initial state factory expression (SpEL).
     * <p>Creates initial saga state from trigger.
     *
     * @return state factory expression
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. A flow instance carries
     *         no application state: {@code FlowContext} exposes the instance id, the definition
     *         name, the current step, the state and the deadline, and nothing else. The application
     *         writes the data its steps read, keyed by the instance id, before it schedules the
     *         saga.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String initialStateExpression() default "";

    /**
     * Initial state factory class.
     * <p>Must implement {@code SagaStateFactory<T, S>}.
     *
     * @return factory class
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. A flow instance carries
     *         no application state: {@code FlowContext} exposes the instance id, the definition
     *         name, the current step, the state and the deadline, and nothing else. The application
     *         writes the data its steps read, keyed by the instance id, before it schedules the
     *         saga.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    Class<?> stateFactory() default void.class;

    // ═══════════════════════════════════════════════════════════════════════════
    // TIMEOUT & RETRY CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Overall saga timeout (ISO-8601 duration).
     *
     * <p>The generated flow passes it to the kernel's {@code FlowDefinitionBuilder.timeoutDuration},
     * the deadline of each instance. The kernel's Core runtime checks the deadline before it runs
     * a step and compensates an instance past it. Both producers carry a declared value; when the
     * attribute is absent they carry {@code "PT30M"}, the metadata default, not the
     * {@code "PT5M"} written below.
     *
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>"PT5M" - 5 minutes</li>
     *   <li>"PT1H" - 1 hour</li>
     *   <li>"P1D" - 1 day</li>
     * </ul>
     *
     * @return saga timeout
     */
    String timeout() default "PT5M";

    /**
     * How many times a failed step may be retried before the saga compensates.
     *
     * <p>The generated flow passes it to the kernel's {@code FlowDefinitionBuilder.maxRetries},
     * whose contract is a step-level retry count, not a restart of the saga. The kernel's Core
     * runtime records the count on the definition and does not re-run a failed step: a step that
     * fails goes to compensation. The generator treats a value below {@code 1} as absent and emits
     * {@code 3}, so {@code maxRetries = 0} does not declare "no retries".
     *
     * @return max retries
     */
    int maxRetries() default 3;

    /**
     * Retry policy configuration.
     *
     * @return retry policy
     * @deprecated since 0.13.0, for removal in 1.0.0, replaced by {@link #maxRetries()} for the
     *         count. The kernel's one retry setting is that count,
     *         {@code FlowDefinitionBuilder.maxRetries}; no kernel contract spaces retries or
     *         filters them by exception type. A step that needs either retries inside its own
     *         method.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    SagaRetryPolicy retryPolicy() default @SagaRetryPolicy;

    /**
     * Delay before first saga retry (ISO-8601 duration).
     *
     * @return initial retry delay
     * @deprecated since 0.13.0, for removal in 1.0.0, replaced by {@link #maxRetries()} for the
     *         count. The kernel's one retry setting is that count,
     *         {@code FlowDefinitionBuilder.maxRetries}; no kernel contract spaces retries or
     *         filters them by exception type. A step that needs either retries inside its own
     *         method.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String retryInitialDelay() default "PT1S";

    /**
     * Maximum delay between saga retries.
     *
     * @return max retry delay
     * @deprecated since 0.13.0, for removal in 1.0.0, replaced by {@link #maxRetries()} for the
     *         count. The kernel's one retry setting is that count,
     *         {@code FlowDefinitionBuilder.maxRetries}; no kernel contract spaces retries or
     *         filters them by exception type. A step that needs either retries inside its own
     *         method.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String retryMaxDelay() default "PT1M";

    /**
     * Retry backoff multiplier.
     * <p>Each retry delay = previous * multiplier.
     *
     * @return backoff multiplier
     * @deprecated since 0.13.0, for removal in 1.0.0, replaced by {@link #maxRetries()} for the
     *         count. The kernel's one retry setting is that count,
     *         {@code FlowDefinitionBuilder.maxRetries}; no kernel contract spaces retries or
     *         filters them by exception type. A step that needs either retries inside its own
     *         method.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    double retryBackoffMultiplier() default 2.0;

    /**
     * Jitter factor for retry delays (0.0 - 1.0).
     * <p>Adds randomness to prevent thundering herd.
     *
     * @return jitter factor
     * @deprecated since 0.13.0, for removal in 1.0.0, replaced by {@link #maxRetries()} for the
     *         count. The kernel's one retry setting is that count,
     *         {@code FlowDefinitionBuilder.maxRetries}; no kernel contract spaces retries or
     *         filters them by exception type. A step that needs either retries inside its own
     *         method.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    double retryJitter() default 0.1;

    /**
     * Exceptions that should trigger saga retry.
     * <p>Empty = retry on all exceptions.
     *
     * @return retryable exceptions
     * @deprecated since 0.13.0, for removal in 1.0.0, replaced by {@link #maxRetries()} for the
     *         count. The kernel's one retry setting is that count,
     *         {@code FlowDefinitionBuilder.maxRetries}; no kernel contract spaces retries or
     *         filters them by exception type. A step that needs either retries inside its own
     *         method.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    Class<? extends Throwable>[] retryOn() default {};

    /**
     * Exceptions that should NOT trigger retry.
     * <p>These exceptions cause immediate failure.
     *
     * @return non-retryable exceptions
     * @deprecated since 0.13.0, for removal in 1.0.0, replaced by {@link #maxRetries()} for the
     *         count. The kernel's one retry setting is that count,
     *         {@code FlowDefinitionBuilder.maxRetries}; no kernel contract spaces retries or
     *         filters them by exception type. A step that needs either retries inside its own
     *         method.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    Class<? extends Throwable>[] noRetryOn() default {};

    // ═══════════════════════════════════════════════════════════════════════════
    // COMPENSATION CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Order of compensation execution.
     *
     * <p><strong>Open-Core status — RESERVED; declared but not extracted.</strong> No
     * {@code exeris-tooling} processor reads this attribute and the
     * {@code exeris-sdk-source-model-io} reader does not read it either, so
     * {@code SagaMetadata.compensationOrder()} holds its builder default on every
     * build path and setting it here changes no generated artifact. The reader leaves it
     * unread because {@link CompensationOrder#CUSTOM} has no counterpart in
     * {@code SagaMetadata.CompensationOrder}, so a read by constant name would carry a wrong
     * value.
     *
     * @return compensation order
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. The order is fixed: when
     *         a step fails, the kernel runs the compensations of the completed steps in reverse
     *         order ({@code FlowOutcome.FAIL}).
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    CompensationOrder compensationOrder() default CompensationOrder.REVERSE;

    /**
     * Compensation execution strategy.
     *
     * <p><strong>Open-Core status — RESERVED; declared but not extracted.</strong> No
     * {@code exeris-tooling} processor reads this attribute and the
     * {@code exeris-sdk-source-model-io} reader does not read it either, so
     * {@code SagaMetadata.compensationStrategy()} holds its builder default on every
     * build path and setting it here changes no generated artifact. The reader leaves it
     * unread because {@link CompensationStrategy#STOP_ON_FAILURE} and
     * {@link CompensationStrategy#MANUAL} have no counterpart in
     * {@code SagaMetadata.CompensationStrategy}, so a read by constant name would carry a wrong
     * value.
     *
     * @return compensation strategy
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. The kernel's flow
     *         contract has no strategy setting. Its Core runtime runs every registered
     *         compensation, reports one that throws and runs the rest, and the instance ends
     *         {@code FAILED_ROLLEDBACK}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    CompensationStrategy compensationStrategy() default CompensationStrategy.ALL_OR_NOTHING;

    /**
     * Timeout for entire compensation phase (ISO-8601 duration).
     *
     * <p><strong>Open-Core status — PARTIAL.</strong> The
     * {@code exeris-sdk-source-model-io} reader reads it into
     * {@code SagaMetadata.compensationTimeout()}; no {@code exeris-tooling} processor extracts it
     * yet, so a build-time baseline holds the builder default, {@code "PT10M"}, and no generator
     * reads it, so setting it here changes no generated artifact.
     *
     * <p>The kernel's flow contract has no compensation deadline: an instance has one deadline,
     * {@link #timeout()}.
     *
     * @return compensation timeout
     */
    String compensationTimeout() default "PT10M";

    /**
     * Max retries for individual compensation steps.
     *
     * <p><strong>Open-Core status — PARTIAL.</strong> The
     * {@code exeris-sdk-source-model-io} reader reads it into
     * {@code SagaMetadata.compensationMaxRetries()}; no {@code exeris-tooling} processor extracts it yet,
     * and no generator reads it, so setting it here changes no generated artifact.
     *
     * <p>The kernel's flow contract has no compensation retry setting.
     *
     * @return compensation max retries
     */
    int compensationMaxRetries() default 5;

    /**
     * Delay between compensation retries.
     *
     * <p><strong>Open-Core status — PARTIAL.</strong> The
     * {@code exeris-sdk-source-model-io} reader reads it into
     * {@code SagaMetadata.compensationRetryDelay()}; no {@code exeris-tooling} processor extracts it yet,
     * and no generator reads it, so setting it here changes no generated artifact.
     *
     * <p>The kernel's flow contract has no compensation retry setting.
     *
     * @return compensation retry delay
     */
    String compensationRetryDelay() default "PT5S";

    /**
     * Continue compensation on individual step failure.
     * <p>When true, continues to next compensation even if one fails.
     *
     * <p><strong>Open-Core status — PARTIAL.</strong> The
     * {@code exeris-sdk-source-model-io} reader reads it into
     * {@code SagaMetadata.continueCompensationOnFailure()}; no {@code exeris-tooling} processor extracts it yet,
     * and no generator reads it, so setting it here changes no generated artifact.
     *
     * <p>The kernel's flow contract has no such setting, and its Core runtime always continues: a
     * compensation that throws is reported and the remaining ones still run.
     *
     * @return continue on failure
     */
    boolean continueCompensationOnFailure() default true;

    /**
     * Dead letter queue for failed compensations.
     * <p>Unrecoverable compensation failures are sent here.
     *
     * <p><strong>Open-Core status — PARTIAL.</strong> The
     * {@code exeris-sdk-source-model-io} reader reads it into
     * {@code SagaMetadata.compensationDlq()}; no {@code exeris-tooling} processor extracts it yet,
     * and no generator reads it, so setting it here changes no generated artifact.
     *
     * <p>The kernel's flow contract names no destination for a failed compensation; its Core
     * runtime reports the failure as a flight-recorder event.
     *
     * @return DLQ topic name
     */
    String compensationDlq() default "";

    /**
     * Custom compensation handler class.
     * <p>Called when automatic compensation fails.
     *
     * <p><strong>Open-Core status — PARTIAL.</strong> The
     * {@code exeris-sdk-source-model-io} reader reads it into
     * {@code SagaMetadata.compensationFailureHandler()}; no {@code exeris-tooling} processor extracts it yet,
     * and no generator reads it, so setting it here changes no generated artifact. The component
     * carries the class's fully-qualified name.
     *
     * <p>The kernel's flow contract registers no compensation-failure handler; its Core runtime
     * reports the failure as a flight-recorder event.
     *
     * @return handler class
     */
    Class<?> compensationFailureHandler() default void.class;

    /**
     * Trigger manual intervention on compensation failure.
     *
     * <p><strong>Open-Core status — PARTIAL.</strong> The
     * {@code exeris-sdk-source-model-io} reader reads it into
     * {@code SagaMetadata.manualInterventionOnCompensationFailure()}; no {@code exeris-tooling} processor extracts it yet,
     * and no generator reads it, so setting it here changes no generated artifact.
     *
     * <p>The kernel's flow contract has no intervention state: a failed instance ends
     * {@code FAILED_ROLLEDBACK}.
     *
     * @return true to trigger intervention
     */
    boolean manualInterventionOnCompensationFailure() default true;

    // ═══════════════════════════════════════════════════════════════════════════
    // PERSISTENCE CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Saga state persistence strategy.
     *
     * @return persistence type
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. How saga state is stored
     *         is decided once per engine, not per saga: {@code FlowEngineConfig.persistenceEnabled}
     *         turns persistence on, and the {@code FlowSnapshotStore} the bootstrapper binds
     *         decides the storage, its table and its encoding. The kernel never deletes a finished
     *         instance's row; retention and archiving belong to whoever binds the store.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    SagaPersistence persistence() default SagaPersistence.DATABASE;

    /**
     * Database table name for saga state.
     * <p>Defaults to "saga_{name}_state".
     *
     * @return table name
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. How saga state is stored
     *         is decided once per engine, not per saga: {@code FlowEngineConfig.persistenceEnabled}
     *         turns persistence on, and the {@code FlowSnapshotStore} the bootstrapper binds
     *         decides the storage, its table and its encoding. The kernel never deletes a finished
     *         instance's row; retention and archiving belong to whoever binds the store.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String stateTable() default "";

    /**
     * State serialization format.
     *
     * @return state format
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. How saga state is stored
     *         is decided once per engine, not per saga: {@code FlowEngineConfig.persistenceEnabled}
     *         turns persistence on, and the {@code FlowSnapshotStore} the bootstrapper binds
     *         decides the storage, its table and its encoding. The kernel never deletes a finished
     *         instance's row; retention and archiving belong to whoever binds the store.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    StateFormat stateFormat() default StateFormat.JSON;

    /**
     * Deprecated, no effect: the saga's state is not encrypted at rest.
     * <p>Setting it to {@code true} changes nothing. No processor extracts it, no AST record
     * carries it and no generator reads it. The kernel's durable saga state is the
     * {@code FlowSnapshotStore} contract ({@code eu.exeris.kernel.spi.flow.model}), which stores
     * the snapshot it is given and names no encryption scheme. Kernel ADR-012 §4c.1 rules
     * encryption at rest outside kernel scope at 1.0, in every tier, and rules that tooling must
     * not invent a scheme, so no generator will honour it.
     *
     * @return true to encrypt
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. Encrypt the state in
     *         the application: bind a {@code FlowSnapshotStore} that encrypts what it writes, or
     *         let the database encrypt its storage. See {@code MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    boolean encryptState() default false;

    /**
     * Deprecated, no effect: the key alias for {@link #encryptState()}, which encrypts nothing.
     *
     * @return key alias
     * @deprecated since 0.13.0, for removal in 1.0.0, with {@link #encryptState()} and with no
     *         replacement. No kernel tier defines a key registry at 1.0 (kernel ADR-012 §4c.1).
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String stateEncryptionKeyAlias() default "";

    /**
     * Compress saga state.
     *
     * @return compression algorithm
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. How saga state is stored
     *         is decided once per engine, not per saga: {@code FlowEngineConfig.persistenceEnabled}
     *         turns persistence on, and the {@code FlowSnapshotStore} the bootstrapper binds
     *         decides the storage, its table and its encoding. The kernel never deletes a finished
     *         instance's row; retention and archiving belong to whoever binds the store.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    Compression stateCompression() default Compression.NONE;

    /**
     * State retention period in days.
     * <p>Completed saga states are deleted after this period.
     *
     * @return retention days
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. How saga state is stored
     *         is decided once per engine, not per saga: {@code FlowEngineConfig.persistenceEnabled}
     *         turns persistence on, and the {@code FlowSnapshotStore} the bootstrapper binds
     *         decides the storage, its table and its encoding. The kernel never deletes a finished
     *         instance's row; retention and archiving belong to whoever binds the store.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    int stateRetentionDays() default 90;

    /**
     * Clean up completed sagas after N hours.
     * <p>0 = keep until retention period.
     *
     * @return cleanup delay hours
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. How saga state is stored
     *         is decided once per engine, not per saga: {@code FlowEngineConfig.persistenceEnabled}
     *         turns persistence on, and the {@code FlowSnapshotStore} the bootstrapper binds
     *         decides the storage, its table and its encoding. The kernel never deletes a finished
     *         instance's row; retention and archiving belong to whoever binds the store.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    int cleanupAfterHours() default 24;

    /**
     * Archive completed sagas.
     *
     * @return true to archive
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. How saga state is stored
     *         is decided once per engine, not per saga: {@code FlowEngineConfig.persistenceEnabled}
     *         turns persistence on, and the {@code FlowSnapshotStore} the bootstrapper binds
     *         decides the storage, its table and its encoding. The kernel never deletes a finished
     *         instance's row; retention and archiving belong to whoever binds the store.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    boolean archiveCompletedSagas() default false;

    /**
     * Archive storage location.
     *
     * @return archive path
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. How saga state is stored
     *         is decided once per engine, not per saga: {@code FlowEngineConfig.persistenceEnabled}
     *         turns persistence on, and the {@code FlowSnapshotStore} the bootstrapper binds
     *         decides the storage, its table and its encoding. The kernel never deletes a finished
     *         instance's row; retention and archiving belong to whoever binds the store.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String archiveStorage() default "";

    // ═══════════════════════════════════════════════════════════════════════════
    // COMMAND DISPATCH
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Command dispatch method.
     * <p>How saga commands are sent to services.
     *
     * @return dispatch method
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. The kernel dispatches no
     *         command for a saga: a step is a {@code FlowStepAction} that returns an outcome, so
     *         the step method sends what it sends, over the transport it chooses, with the timeout
     *         and acknowledgement that transport offers. A transactional outbox is the persistence
     *         engine's, used from that method.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    CommandDispatch commandDispatch() default CommandDispatch.KAFKA;

    /**
     * Command topic pattern.
     * <p>Supports placeholders: {service}, {command}, {module}
     *
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>"saga.commands.{service}"</li>
     *   <li>"{module}.{service}.commands"</li>
     * </ul>
     *
     * @return topic pattern
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. The kernel dispatches no
     *         command for a saga: a step is a {@code FlowStepAction} that returns an outcome, so
     *         the step method sends what it sends, over the transport it chooses, with the timeout
     *         and acknowledgement that transport offers. A transactional outbox is the persistence
     *         engine's, used from that method.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String commandTopicPattern() default "saga.commands.{service}";

    /**
     * Reply topic for saga responses.
     * <p>Where services send command results.
     *
     * @return reply topic
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. The kernel dispatches no
     *         command for a saga: a step is a {@code FlowStepAction} that returns an outcome, so
     *         the step method sends what it sends, over the transport it chooses, with the timeout
     *         and acknowledgement that transport offers. A transactional outbox is the persistence
     *         engine's, used from that method.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String replyTopic() default "";

    /**
     * Use transactional outbox for commands.
     * <p>Commands are written to outbox within saga transaction.
     *
     * @return true to use outbox
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. The kernel dispatches no
     *         command for a saga: a step is a {@code FlowStepAction} that returns an outcome, so
     *         the step method sends what it sends, over the transport it chooses, with the timeout
     *         and acknowledgement that transport offers. A transactional outbox is the persistence
     *         engine's, used from that method.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    boolean useOutbox() default true;

    /**
     * Command timeout (ISO-8601 duration).
     * <p>Default timeout for all steps (can be overridden per step).
     *
     * @return command timeout
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. The kernel dispatches no
     *         command for a saga: a step is a {@code FlowStepAction} that returns an outcome, so
     *         the step method sends what it sends, over the transport it chooses, with the timeout
     *         and acknowledgement that transport offers. A transactional outbox is the persistence
     *         engine's, used from that method.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String defaultCommandTimeout() default "PT30S";

    /**
     * Command acknowledgment mode.
     *
     * @return ack mode
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. The kernel dispatches no
     *         command for a saga: a step is a {@code FlowStepAction} that returns an outcome, so
     *         the step method sends what it sends, over the transport it chooses, with the timeout
     *         and acknowledgement that transport offers. A transactional outbox is the persistence
     *         engine's, used from that method.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    AckMode commandAckMode() default AckMode.AUTO;

    // ═══════════════════════════════════════════════════════════════════════════
    // IDEMPOTENCY
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Ensure saga is started only once per correlation ID.
     *
     * @return true for idempotent start
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. Deduplication is not
     *         configurable: the kernel treats a {@code Start} for an instance id it already holds
     *         as a no-op, and with a snapshot store bound a finished instance keeps its terminal
     *         row, so the window is how long whoever binds the store retains that row (see
     *         <em>Finished instances</em> above). The key is the instance id the saga is scheduled
     *         under.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    boolean idempotent() default true;

    /**
     * Idempotency key expression (SpEL).
     * <p>Unique key for deduplication.
     *
     * @return idempotency key expression
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. Deduplication is not
     *         configurable: the kernel treats a {@code Start} for an instance id it already holds
     *         as a no-op, and with a snapshot store bound a finished instance keeps its terminal
     *         row, so the window is how long whoever binds the store retains that row (see
     *         <em>Finished instances</em> above). The key is the instance id the saga is scheduled
     *         under.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String idempotencyKeyExpression() default "#correlationId";

    /**
     * Idempotency window duration.
     * <p>How long to remember processed saga IDs.
     *
     * @return idempotency window
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. Deduplication is not
     *         configurable: the kernel treats a {@code Start} for an instance id it already holds
     *         as a no-op, and with a snapshot store bound a finished instance keeps its terminal
     *         row, so the window is how long whoever binds the store retains that row (see
     *         <em>Finished instances</em> above). The key is the instance id the saga is scheduled
     *         under.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String idempotencyWindow() default "PT24H";

    /**
     * Idempotency storage.
     *
     * @return storage type
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. Deduplication is not
     *         configurable: the kernel treats a {@code Start} for an instance id it already holds
     *         as a no-op, and with a snapshot store bound a finished instance keeps its terminal
     *         row, so the window is how long whoever binds the store retains that row (see
     *         <em>Finished instances</em> above). The key is the instance id the saga is scheduled
     *         under.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    IdempotencyStorage idempotencyStorage() default IdempotencyStorage.DATABASE;

    // ═══════════════════════════════════════════════════════════════════════════
    // LIFECYCLE HOOKS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Method to call before saga starts.
     * <p>Can modify initial state or validate preconditions.
     *
     * @return method name
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. The kernel's flow
     *         contract has no lifecycle callback: {@code FlowEngine} offers plans, the scheduler,
     *         the registry, capabilities, statistics, choreography-mapper registration, start and
     *         close. Put the work in a step or compensation method; a saga past its
     *         {@link #timeout()} is compensated.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String beforeStart() default "";

    /**
     * Method to call after saga starts.
     * <p>Called after first step begins.
     *
     * @return method name
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. The kernel's flow
     *         contract has no lifecycle callback: {@code FlowEngine} offers plans, the scheduler,
     *         the registry, capabilities, statistics, choreography-mapper registration, start and
     *         close. Put the work in a step or compensation method; a saga past its
     *         {@link #timeout()} is compensated.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String afterStart() default "";

    /**
     * Method to call before each step.
     *
     * @return method name
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. The kernel's flow
     *         contract has no lifecycle callback: {@code FlowEngine} offers plans, the scheduler,
     *         the registry, capabilities, statistics, choreography-mapper registration, start and
     *         close. Put the work in a step or compensation method; a saga past its
     *         {@link #timeout()} is compensated.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String beforeStep() default "";

    /**
     * Method to call after each step completes.
     *
     * @return method name
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. The kernel's flow
     *         contract has no lifecycle callback: {@code FlowEngine} offers plans, the scheduler,
     *         the registry, capabilities, statistics, choreography-mapper registration, start and
     *         close. Put the work in a step or compensation method; a saga past its
     *         {@link #timeout()} is compensated.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String afterStep() default "";

    /**
     * Method to call when saga completes successfully.
     *
     * @return method name
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. The kernel's flow
     *         contract has no lifecycle callback: {@code FlowEngine} offers plans, the scheduler,
     *         the registry, capabilities, statistics, choreography-mapper registration, start and
     *         close. Put the work in a step or compensation method; a saga past its
     *         {@link #timeout()} is compensated.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String onComplete() default "";

    /**
     * Method to call when saga fails.
     *
     * @return method name
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. The kernel's flow
     *         contract has no lifecycle callback: {@code FlowEngine} offers plans, the scheduler,
     *         the registry, capabilities, statistics, choreography-mapper registration, start and
     *         close. Put the work in a step or compensation method; a saga past its
     *         {@link #timeout()} is compensated.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String onFail() default "";

    /**
     * Method to call before compensation starts.
     *
     * @return method name
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. The kernel's flow
     *         contract has no lifecycle callback: {@code FlowEngine} offers plans, the scheduler,
     *         the registry, capabilities, statistics, choreography-mapper registration, start and
     *         close. Put the work in a step or compensation method; a saga past its
     *         {@link #timeout()} is compensated.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String beforeCompensate() default "";

    /**
     * Method to call after compensation completes.
     *
     * @return method name
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. The kernel's flow
     *         contract has no lifecycle callback: {@code FlowEngine} offers plans, the scheduler,
     *         the registry, capabilities, statistics, choreography-mapper registration, start and
     *         close. Put the work in a step or compensation method; a saga past its
     *         {@link #timeout()} is compensated.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String afterCompensate() default "";

    /**
     * Method to call on saga timeout.
     *
     * @return method name
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. The kernel's flow
     *         contract has no lifecycle callback: {@code FlowEngine} offers plans, the scheduler,
     *         the registry, capabilities, statistics, choreography-mapper registration, start and
     *         close. Put the work in a step or compensation method; a saga past its
     *         {@link #timeout()} is compensated.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String onTimeout() default "";

    // ═══════════════════════════════════════════════════════════════════════════
    // CONCURRENCY
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Maximum concurrent saga instances.
     * <p>0 = unlimited.
     *
     * @return max instances
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. The kernel bounds
     *         concurrency per engine, not per saga: {@code FlowEngineConfig.maxConcurrentFlows} and
     *         {@code schedulerQueueCapacity}, and a scheduler that cannot admit an instance refuses
     *         it with {@code QUEUE_FULL}. Nothing counts instances per definition or tenant, and
     *         nothing queues on overflow.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    int maxConcurrentInstances() default 0;

    /**
     * Maximum concurrent instances per tenant.
     *
     * @return max per tenant
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. The kernel bounds
     *         concurrency per engine, not per saga: {@code FlowEngineConfig.maxConcurrentFlows} and
     *         {@code schedulerQueueCapacity}, and a scheduler that cannot admit an instance refuses
     *         it with {@code QUEUE_FULL}. Nothing counts instances per definition or tenant, and
     *         nothing queues on overflow.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    int maxConcurrentPerTenant() default 0;

    /**
     * Maximum concurrent instances per correlation key.
     * <p>Prevents duplicate saga for same entity.
     *
     * @return max per key
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. One instance per key is
     *         the instance id: schedule the saga under an id derived from the key, and a second
     *         {@code Start} for that id is a no-op. Exclusion between different instances is not
     *         the kernel's: it fences each instance's own snapshot row and has no distributed lock
     *         (kernel ADR-013), so two instances that write one entity need that entity versioned.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    int maxConcurrentPerKey() default 1;

    /**
     * Behavior when concurrency limit reached.
     *
     * @return overflow behavior
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. The kernel bounds
     *         concurrency per engine, not per saga: {@code FlowEngineConfig.maxConcurrentFlows} and
     *         {@code schedulerQueueCapacity}, and a scheduler that cannot admit an instance refuses
     *         it with {@code QUEUE_FULL}. Nothing counts instances per definition or tenant, and
     *         nothing queues on overflow.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    OverflowBehavior overflowBehavior() default OverflowBehavior.QUEUE;

    /**
     * Queue size when overflow = QUEUE.
     *
     * @return queue size
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. The kernel bounds
     *         concurrency per engine, not per saga: {@code FlowEngineConfig.maxConcurrentFlows} and
     *         {@code schedulerQueueCapacity}, and a scheduler that cannot admit an instance refuses
     *         it with {@code QUEUE_FULL}. Nothing counts instances per definition or tenant, and
     *         nothing queues on overflow.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    int overflowQueueSize() default 1000;

    // ═══════════════════════════════════════════════════════════════════════════
    // MONITORING & ALERTING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Emit metrics for this saga.
     *
     * @return true to emit metrics
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. The kernel's flow
     *         telemetry is per engine, not per saga: flight-recorder events and
     *         {@code FlowEngine.stats()}. No kernel contract names a metric prefix, an alert, a
     *         channel, a severity, a health check or a status endpoint; alerting is built over that
     *         telemetry.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    boolean emitMetrics() default true;

    /**
     * Metric name prefix.
     *
     * @return metric prefix
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. The kernel's flow
     *         telemetry is per engine, not per saga: flight-recorder events and
     *         {@code FlowEngine.stats()}. No kernel contract names a metric prefix, an alert, a
     *         channel, a severity, a health check or a status endpoint; alerting is built over that
     *         telemetry.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String metricPrefix() default "";

    /**
     * Alert if saga duration exceeds threshold.
     *
     * @return duration alert threshold
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. The kernel's flow
     *         telemetry is per engine, not per saga: flight-recorder events and
     *         {@code FlowEngine.stats()}. No kernel contract names a metric prefix, an alert, a
     *         channel, a severity, a health check or a status endpoint; alerting is built over that
     *         telemetry.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String durationAlertThreshold() default "PT10M";

    /**
     * Alert if saga fails.
     *
     * @return true to alert on failure
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. The kernel's flow
     *         telemetry is per engine, not per saga: flight-recorder events and
     *         {@code FlowEngine.stats()}. No kernel contract names a metric prefix, an alert, a
     *         channel, a severity, a health check or a status endpoint; alerting is built over that
     *         telemetry.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    boolean alertOnFailure() default true;

    /**
     * Alert if compensation fails.
     *
     * @return true to alert on compensation failure
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. The kernel's flow
     *         telemetry is per engine, not per saga: flight-recorder events and
     *         {@code FlowEngine.stats()}. No kernel contract names a metric prefix, an alert, a
     *         channel, a severity, a health check or a status endpoint; alerting is built over that
     *         telemetry.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    boolean alertOnCompensationFailure() default true;

    /**
     * Alert if saga times out.
     *
     * @return true to alert on timeout
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. The kernel's flow
     *         telemetry is per engine, not per saga: flight-recorder events and
     *         {@code FlowEngine.stats()}. No kernel contract names a metric prefix, an alert, a
     *         channel, a severity, a health check or a status endpoint; alerting is built over that
     *         telemetry.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    boolean alertOnTimeout() default true;

    /**
     * Alert if step retries exceed threshold.
     *
     * @return retry alert threshold
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. The kernel's flow
     *         telemetry is per engine, not per saga: flight-recorder events and
     *         {@code FlowEngine.stats()}. No kernel contract names a metric prefix, an alert, a
     *         channel, a severity, a health check or a status endpoint; alerting is built over that
     *         telemetry.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    int retryAlertThreshold() default 2;

    /**
     * Notification channels for alerts.
     * <p>Slack channels, email addresses, PagerDuty keys.
     *
     * @return alert channels
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. The kernel's flow
     *         telemetry is per engine, not per saga: flight-recorder events and
     *         {@code FlowEngine.stats()}. No kernel contract names a metric prefix, an alert, a
     *         channel, a severity, a health check or a status endpoint; alerting is built over that
     *         telemetry.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String[] alertChannels() default {};

    /**
     * Alert severity levels.
     *
     * @return alert severities
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. The kernel's flow
     *         telemetry is per engine, not per saga: flight-recorder events and
     *         {@code FlowEngine.stats()}. No kernel contract names a metric prefix, an alert, a
     *         channel, a severity, a health check or a status endpoint; alerting is built over that
     *         telemetry.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    AlertSeverity failureAlertSeverity() default AlertSeverity.ERROR;

    /**
     * Health check endpoint.
     *
     * @return true to expose health check
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. The kernel's flow
     *         telemetry is per engine, not per saga: flight-recorder events and
     *         {@code FlowEngine.stats()}. No kernel contract names a metric prefix, an alert, a
     *         channel, a severity, a health check or a status endpoint; alerting is built over that
     *         telemetry.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    boolean healthCheck() default true;

    /**
     * Expose saga status API endpoint.
     *
     * @return true to expose API
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. The kernel's flow
     *         telemetry is per engine, not per saga: flight-recorder events and
     *         {@code FlowEngine.stats()}. No kernel contract names a metric prefix, an alert, a
     *         channel, a severity, a health check or a status endpoint; alerting is built over that
     *         telemetry.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    boolean exposeStatusApi() default true;

    // ═══════════════════════════════════════════════════════════════════════════
    // DEBUGGING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Log level for saga execution.
     *
     * @return log level
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. No kernel contract sets
     *         a log level, state logging, tracing or a trace sample rate per saga; the engine's
     *         flow telemetry is its flight-recorder events.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    LogLevel logLevel() default LogLevel.INFO;

    /**
     * Log full state on each step.
     * <p>Warning: May expose sensitive data in logs.
     *
     * @return true to log state
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. No kernel contract sets
     *         a log level, state logging, tracing or a trace sample rate per saga; the engine's
     *         flow telemetry is its flight-recorder events.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    boolean logStateOnStep() default false;

    /**
     * Enable distributed tracing.
     *
     * @return true to enable tracing
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. No kernel contract sets
     *         a log level, state logging, tracing or a trace sample rate per saga; the engine's
     *         flow telemetry is its flight-recorder events.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    boolean enableTracing() default true;

    /**
     * Trace sample rate (0.0 - 1.0).
     *
     * @return sample rate
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. No kernel contract sets
     *         a log level, state logging, tracing or a trace sample rate per saga; the engine's
     *         flow telemetry is its flight-recorder events.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    double traceSampleRate() default 1.0;

    // ═══════════════════════════════════════════════════════════════════════════
    // TESTING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Generate test fixtures for this saga.
     *
     * @return true to generate
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. No test generation reads
     *         it: a generated saga flow gets a generated test whatever this says, and that test
     *         runs against a recording double with no persistence and no services to mock.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    boolean generateTestFixtures() default true;

    /**
     * Use in-memory persistence in tests.
     *
     * @return true for in-memory
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. No test generation reads
     *         it: a generated saga flow gets a generated test whatever this says, and that test
     *         runs against a recording double with no persistence and no services to mock.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    boolean inMemoryPersistenceInTests() default true;

    /**
     * Mock all external services in tests.
     *
     * @return true to mock
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. No test generation reads
     *         it: a generated saga flow gets a generated test whatever this says, and that test
     *         runs against a recording double with no persistence and no services to mock.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    boolean mockServicesInTests() default true;

    // ═══════════════════════════════════════════════════════════════════════════
    // ENUMS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * The order in which a failed saga's compensations run.
     *
     * @deprecated since 0.13.0, for removal in 1.0.0, with {@link Saga#compensationOrder()} and
     *         with no replacement. The order is fixed: when a step fails, the kernel runs the
     *         compensations of the completed steps in reverse order ({@code FlowOutcome.FAIL}).
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    enum CompensationOrder {
        /** Execute compensation in reverse order of execution. */
        REVERSE,

        /** Execute all compensations in parallel. */
        PARALLEL,

        /** Execute compensation in forward order. */
        FORWARD,

        /** Custom order defined by compensation dependencies. */
        CUSTOM
    }

    /**
     * How compensation is driven once a step fails.
     *
     * @deprecated since 0.13.0, for removal in 1.0.0, with {@link Saga#compensationStrategy()} and
     *         with no replacement. The kernel's flow contract has no strategy setting. Its Core
     *         runtime runs every registered compensation, reports one that throws and runs the
     *         rest, and the instance ends {@code FAILED_ROLLEDBACK}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    enum CompensationStrategy {
        /** All compensations must succeed or saga remains in failed state. */
        ALL_OR_NOTHING,

        /** Best effort - continue on failure, log issues. */
        BEST_EFFORT,

        /** Stop compensation on first failure. */
        STOP_ON_FAILURE,

        /** No automatic compensation - manual intervention required. */
        MANUAL
    }

    /**
     * Where saga state is kept between steps.
     *
     * @deprecated since 0.13.0, for removal in 1.0.0, with {@link Saga#persistence()} and with no
     *         replacement. How saga state is stored is decided once per engine, not per saga:
     *         {@code FlowEngineConfig.persistenceEnabled} turns persistence on, and the
     *         {@code FlowSnapshotStore} the bootstrapper binds decides the storage, its table and
     *         its encoding. The kernel never deletes a finished instance's row; retention and
     *         archiving belong to whoever binds the store.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    enum SagaPersistence {
        /** PostgreSQL/MySQL database. */
        DATABASE,

        /** Redis. */
        REDIS,

        /** Event store (EventStoreDB, etc.). */
        EVENT_STORE,

        /** In-memory (testing only). */
        IN_MEMORY,

        /** Custom implementation. */
        CUSTOM
    }

    /**
     * How saga state is encoded when persisted.
     *
     * @deprecated since 0.13.0, for removal in 1.0.0, with {@link Saga#stateFormat()} and with no
     *         replacement. How saga state is stored is decided once per engine, not per saga:
     *         {@code FlowEngineConfig.persistenceEnabled} turns persistence on, and the
     *         {@code FlowSnapshotStore} the bootstrapper binds decides the storage, its table and
     *         its encoding. The kernel never deletes a finished instance's row; retention and
     *         archiving belong to whoever binds the store.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    enum StateFormat {
        /** Human-readable JSON. */
        JSON,
        /** A compact binary encoding. */
        BINARY,
        /** Protocol Buffers. */
        PROTOBUF,
        /** MessagePack. */
        MSGPACK
    }

    /**
     * How persisted saga state is compressed.
     *
     * @deprecated since 0.13.0, for removal in 1.0.0, with {@link Saga#stateCompression()} and with
     *         no replacement. How saga state is stored is decided once per engine, not per saga:
     *         {@code FlowEngineConfig.persistenceEnabled} turns persistence on, and the
     *         {@code FlowSnapshotStore} the bootstrapper binds decides the storage, its table and
     *         its encoding. The kernel never deletes a finished instance's row; retention and
     *         archiving belong to whoever binds the store.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    enum Compression {
        /** Stored uncompressed. */
        NONE,
        /** gzip — widely supported, moderate ratio. */
        GZIP,
        /** LZ4 — fastest, lowest ratio. */
        LZ4,
        /** Snappy — fast, moderate ratio. */
        SNAPPY,
        /** Zstandard — best ratio at comparable speed. */
        ZSTD
    }

    /**
     * How a step's command reaches the service that runs it.
     *
     * @deprecated since 0.13.0, for removal in 1.0.0, with {@link Saga#commandDispatch()} and with
     *         no replacement. The kernel dispatches no command for a saga: a step is a
     *         {@code FlowStepAction} that returns an outcome, so the step method sends what it
     *         sends, over the transport it chooses, with the timeout and acknowledgement that
     *         transport offers. A transactional outbox is the persistence engine's, used from that
     *         method.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    enum CommandDispatch {
        /** Apache Kafka. */
        KAFKA,

        /** RabbitMQ. */
        RABBITMQ,

        /** HTTP REST calls. */
        HTTP,

        /** In-process method calls. */
        IN_PROCESS,

        /** AWS SQS. */
        SQS,

        /** Azure Service Bus. */
        AZURE_SERVICE_BUS
    }

    /**
     * When a consumed message is acknowledged.
     *
     * @deprecated since 0.13.0, for removal in 1.0.0, with {@link Saga#commandAckMode()} and with
     *         no replacement. The kernel dispatches no command for a saga: a step is a
     *         {@code FlowStepAction} that returns an outcome, so the step method sends what it
     *         sends, over the transport it chooses, with the timeout and acknowledgement that
     *         transport offers. A transactional outbox is the persistence engine's, used from that
     *         method.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    enum AckMode {
        /** Automatic acknowledgment. */
        AUTO,

        /** Manual acknowledgment required. */
        MANUAL,

        /** Acknowledge after processing. */
        AFTER_PROCESS
    }

    /**
     * Where the keys guarding against duplicate work are kept.
     *
     * @deprecated since 0.13.0, for removal in 1.0.0, with {@link Saga#idempotencyStorage()} and
     *         with no replacement. Deduplication is not configurable: the kernel treats a
     *         {@code Start} for an instance id it already holds as a no-op, and with a snapshot
     *         store bound a finished instance keeps its terminal row, so the window is how long
     *         whoever binds the store retains that row (see <em>Finished instances</em> above). The
     *         key is the instance id the saga is scheduled under.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    enum IdempotencyStorage {
        /** In the application's own database. */
        DATABASE,
        /** In Redis. */
        REDIS,
        /** In process memory; lost on restart. */
        IN_MEMORY
    }

    /**
     * What happens when the saga's work queue is full.
     *
     * @deprecated since 0.13.0, for removal in 1.0.0, with {@link Saga#overflowBehavior()} and with
     *         no replacement. The kernel bounds concurrency per engine, not per saga:
     *         {@code FlowEngineConfig.maxConcurrentFlows} and {@code schedulerQueueCapacity}, and a
     *         scheduler that cannot admit an instance refuses it with {@code QUEUE_FULL}. Nothing
     *         counts instances per definition or tenant, and nothing queues on overflow.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    enum OverflowBehavior {
        /** Queue excess requests. */
        QUEUE,

        /** Reject excess requests. */
        REJECT,

        /** Drop oldest queued request. */
        DROP_OLDEST,

        /** Process anyway (ignore limit). */
        IGNORE
    }

    /**
     * How urgent an alert raised by this saga is.
     *
     * @deprecated since 0.13.0, for removal in 1.0.0, with {@link Saga#failureAlertSeverity()} and
     *         with no replacement. The kernel's flow telemetry is per engine, not per saga:
     *         flight-recorder events and {@code FlowEngine.stats()}. No kernel contract names a
     *         metric prefix, an alert, a channel, a severity, a health check or a status endpoint;
     *         alerting is built over that telemetry.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    enum AlertSeverity {
        /** Worth recording; no action expected. */
        INFO,
        /** Worth looking at when convenient. */
        WARNING,
        /** Something failed and needs attention. */
        ERROR,
        /** Needs attention now. */
        CRITICAL
    }

    /**
     * How much detail the generated code logs for this surface.
     *
     * @deprecated since 0.13.0, for removal in 1.0.0, with {@link Saga#logLevel()} and with no
     *         replacement. No kernel contract sets a log level, state logging, tracing or a trace
     *         sample rate per saga; the engine's flow telemetry is its flight-recorder events.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
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
     * What starts a saga instance.
     *
     * <p>Names the event that begins the flow — by class when the type is on the
     * classpath, by name or pattern when it is not. {@link Saga#trigger()} defaults to
     * {@code @SagaTrigger} with every attribute at its own default, i.e. a
     * {@link TriggerType#MANUAL} trigger naming no source: the saga is started by
     * application code.
     *
     * <p><strong>Status: PARTIAL</strong> — the {@code exeris-sdk-source-model-io} reader reads
     * {@link Saga#trigger()} into {@code SagaMetadata.trigger()}: {@link #type()} by constant name
     * (the deprecated {@code SCHEDULE}, {@code HTTP} and {@code COMMAND} read as
     * {@code SCHEDULED}, {@code API} and {@code MANUAL}), {@link #schedule()} as
     * {@code cronExpression}, {@link #source()} (else {@link #sourceName()}) as {@code source}, and
     * {@link #topic()}. A saga that declares no {@code trigger} carries none. No
     * {@code exeris-tooling} processor extracts it yet, and no generator reads it, so it changes no
     * generated artifact. {@link #sourcePattern()}, {@link #consumerGroup()}, {@link #filter()},
     * {@link #startPosition()}, {@link #httpPath()} and {@link #httpMethod()} are not carried.
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target({})
    @interface SagaTrigger {
        /**
         * What kind of occurrence starts the saga.
         *
         * @return the trigger kind
         */
        TriggerType type() default TriggerType.MANUAL;

        /**
         * The event type that starts the saga, when it is on the classpath.
         *
         * @return the source type, or {@code void.class} when named instead
         */
        Class<?> source() default void.class;

        /**
         * The source named as a string, for a type this module cannot reference.
         *
         * @return the source name, empty when {@link #source()} is used
         */
        String sourceName() default "";

        /**
         * A regular expression matching several source names at once.
         *
         * @return the pattern, empty when a single source is named
         */
        String sourcePattern() default "";

        /**
         * The messaging topic the trigger listens on.
         *
         * @return the topic, empty when the trigger is not topic-driven
         */
        String topic() default "";

        /**
         * The consumer group the subscription joins, which is what shares the topic
         * across instances instead of duplicating work.
         *
         * @return the consumer group, empty for the default
         */
        String consumerGroup() default "";

        /**
         * An expression gating whether a received message actually starts a saga.
         *
         * @return the filter expression, empty when every message triggers
         */
        String filter() default "";

        /**
         * Where in the source stream the trigger begins reading.
         *
         * @return the start position
         */
        StartPosition startPosition() default StartPosition.LATEST;

        /**
         * The schedule that starts the saga, when {@link #type()} is {@link TriggerType#SCHEDULED}.
         *
         * @return the schedule expression, empty otherwise
         */
        String schedule() default "";

        /**
         * The path a request must hit to start the saga, when {@link #type()} is
         * {@link TriggerType#API}.
         *
         * @return the path, empty otherwise
         */
        String httpPath() default "";

        /**
         * The HTTP method that request must use, when {@link #type()} is {@link TriggerType#API}.
         *
         * @return the method name
         */
        String httpMethod() default "POST";

        /**
         * What kind of occurrence starts the saga.
         *
         * <p>A mirror of the AST-owned {@code SagaMetadata.TriggerType}: two separate types
         * with the same constant names, mapped by name at extraction time, never an import of
         * the AST type, which preserves the annotation module's zero-dependency contract. Every
         * constant not deprecated for removal has exactly one AST counterpart of the same name,
         * and every AST constant has one here. A constant deprecated for removal has no AST
         * counterpart; its deprecation note names the constant it is read as.
         */
        enum TriggerType {
            /**
             * Started by a command.
             *
             * @deprecated since 0.13.0, for removal in 1.0.0. The kernel starts a flow in two
             *         ways only: application code schedules it, or a choreography mapper starts
             *         it from an event. A command handled in-process is the first, so write
             *         {@link #MANUAL}, which this constant is read as; a command delivered as a
             *         message on the event bus is the second, so write {@link #EVENT}.
             */
            @Deprecated(since = "0.13.0", forRemoval = true)
            COMMAND,

            /**
             * Started by a domain event, through a choreography mapper keyed on the event type.
             * Maps onto {@code SagaMetadata.TriggerType.EVENT}.
             */
            EVENT,

            /**
             * Started on a schedule (cron), given by {@link SagaTrigger#schedule()}.
             * Maps onto {@code SagaMetadata.TriggerType.SCHEDULED}.
             *
             * @since 0.13
             */
            SCHEDULED,

            /**
             * Started on schedule (cron).
             *
             * @deprecated since 0.13.0, for removal in 1.0.0. Renamed to {@link #SCHEDULED},
             *         the spelling of the AST constant and of {@code DomainEvent.Trigger},
             *         and read as it.
             */
            @Deprecated(since = "0.13.0", forRemoval = true)
            SCHEDULE,

            /**
             * Started by an API call, bound by {@link SagaTrigger#httpPath()} and
             * {@link SagaTrigger#httpMethod()}. Maps onto {@code SagaMetadata.TriggerType.API}.
             *
             * @since 0.13
             */
            API,

            /**
             * Started via HTTP API.
             *
             * @deprecated since 0.13.0, for removal in 1.0.0. Renamed to {@link #API}, the
             *         spelling of the AST constant, and read as it.
             */
            @Deprecated(since = "0.13.0", forRemoval = true)
            HTTP,

            /**
             * Started programmatically, by application code scheduling the flow. The default.
             * Maps onto {@code SagaMetadata.TriggerType.MANUAL}.
             */
            MANUAL
        }

        /**
         * Where in the source stream the trigger begins reading.
         */
        enum StartPosition {
            /** From the beginning of the stream. */
            EARLIEST,
            /** From messages arriving after the trigger is registered. */
            LATEST,
            /** From a position named elsewhere on the trigger. */
            SPECIFIC
        }
    }

    /**
     * Retry behaviour for a saga, shaped as exponential backoff with jitter.
     *
     * <p>Durations are ISO-8601 ({@code PT1S}, {@code PT1M}). The two throwable lists
     * narrow what is retried: {@code retryOn} restricts retries to the types it names when
     * it is non-empty, {@code noRetryOn} excludes the types it names.
     *
     * <p><strong>Status: RESERVED</strong> — {@link Saga#retryPolicy()} is not extracted,
     * so the emitted saga applies no declared retry behaviour.
     *
     * @deprecated since 0.13.0, for removal in 1.0.0, with {@link Saga#retryPolicy()}; replaced by
     *         {@link Saga#maxRetries()} for the count. The kernel's one retry setting is that
     *         count, {@code FlowDefinitionBuilder.maxRetries}; no kernel contract spaces retries or
     *         filters them by exception type. A step that needs either retries inside its own
     *         method.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    @Retention(RetentionPolicy.SOURCE)
    @Target({})
    @interface SagaRetryPolicy {
        /**
         * How many attempts in total, counting the first.
         *
         * @return the attempt ceiling
         */
        int maxAttempts() default 3;

        /**
         * Delay before the first retry, as an ISO-8601 duration.
         *
         * @return the initial delay
         */
        String initialDelay() default "PT1S";

        /**
         * Ceiling the backoff grows to, as an ISO-8601 duration.
         *
         * @return the maximum delay
         */
        String maxDelay() default "PT1M";

        /**
         * Factor the delay is multiplied by after each failed attempt.
         *
         * @return the backoff multiplier
         */
        double multiplier() default 2.0;

        /**
         * Random fraction of the delay added or subtracted, so that retries from many
         * instances do not synchronize.
         *
         * @return the jitter fraction
         */
        double jitter() default 0.1;

        /**
         * When non-empty, only these throwable types are retried.
         *
         * @return the retryable types
         */
        Class<? extends Throwable>[] retryOn() default {};

        /**
         * Throwable types excluded from retry.
         *
         * @return the non-retryable types
         */
        Class<? extends Throwable>[] noRetryOn() default {};

        /**
         * Whether a timeout counts as retryable.
         *
         * @return true to retry a timed-out attempt
         */
        boolean retryOnTimeout() default true;

        /**
         * Whether a network error counts as retryable.
         *
         * @return true to retry after a network error
         */
        boolean retryOnNetworkError() default true;
    }
}


