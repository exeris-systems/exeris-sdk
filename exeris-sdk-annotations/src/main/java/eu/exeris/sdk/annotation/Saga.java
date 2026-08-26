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
 * <pre>{@code
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
 * }</pre>
 *
 * <h2>With Event Trigger:</h2>
 * <pre>{@code
 * @Saga(
 *     name = "SubscriptionRenewalSaga",
 *     trigger = @SagaTrigger(
 *         type = TriggerType.EVENT,
 *         source = SubscriptionDueForRenewalEvent.class,
 *         topic = "billing.subscriptions.due-for-renewal"
 *     )
 * )
 * public class SubscriptionRenewalSaga implements Saga<RenewalState> {}
 * }</pre>
 *
 * <h2>With Compensation Configuration:</h2>
 * <pre>{@code
 * @Saga(
 *     name = "TransferMoneySaga",
 *     compensationOrder = CompensationOrder.REVERSE,
 *     compensationStrategy = CompensationStrategy.ALL_OR_NOTHING,
 *     compensationTimeout = "PT10M"
 * )
 * public class TransferMoneySaga implements Saga<TransferState> {}
 * }</pre>
 *
 * @author Exeris SDK Team
 * @version 0.1.0
 * @since 0.1.0
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
     * <p><strong>Kernel contract (v0.11, ADR-064).</strong> This attribute used
     * to be documented as a free-form "increment on breaking changes" marker.
     * It is not one any more: the kernel keys its plan catalog by
     * {@code (name, version)}, so registering v2 no longer evicts v1 and both
     * serve traffic — new instances start on the newest registered version,
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
     * <p><strong>Open-Core status — declared, not extracted (verified
     * 2026-08-12).</strong> Neither AST producer reads this attribute:
     * the build-time processor's saga extraction takes {@code name},
     * {@code description}, {@code timeout} and {@code maxRetries} only, and the
     * {@code -io} reader mirrors exactly that set. {@code SagaMetadata} does
     * carry a {@code version} component, but it holds the builder default
     * {@code 1} on every run — so writing {@code version = 3} produces an AST
     * that says {@code 1}, silently, at any strictness. Both producers are
     * blind in the same way, so this is not an ADR-042 divergence; it is a hole
     * in both. Until extraction lands, the kernel contract above is reachable
     * only by hand-written {@code FlowDefinition} code, not by declaring it
     * here.
     *
     * @return saga version
     */
    int version() default 1;

    /**
     * Module this saga belongs to.
     * <p>Used for organization and routing.
     *
     * @return module name
     */
    String module() default "";

    /**
     * Tags for categorization.
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
     */
    String correlationIdExpression() default "#trigger.correlationId ?: T(java.util.UUID).randomUUID().toString()";

    /**
     * Tenant ID expression (SpEL).
     * <p>Extracts tenant ID from trigger payload.
     *
     * @return tenant ID expression
     */
    String tenantIdExpression() default "#trigger.tenantId";

    /**
     * User ID expression (SpEL).
     * <p>Extracts user ID who initiated the saga.
     *
     * @return user ID expression
     */
    String userIdExpression() default "#trigger.userId ?: #securityContext.userId";

    /**
     * Initial state factory expression (SpEL).
     * <p>Creates initial saga state from trigger.
     *
     * @return state factory expression
     */
    String initialStateExpression() default "";

    /**
     * Initial state factory class.
     * <p>Must implement {@code SagaStateFactory<T, S>}.
     *
     * @return factory class
     */
    Class<?> stateFactory() default void.class;

    // ═══════════════════════════════════════════════════════════════════════════
    // TIMEOUT & RETRY CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Overall saga timeout (ISO-8601 duration).
     * <p>Saga is considered failed if not completed within timeout.
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
     * Maximum retry attempts for the entire saga.
     * <p>Saga restarts from beginning on failure.
     *
     * @return max retries
     */
    int maxRetries() default 3;

    /**
     * Retry policy configuration.
     *
     * @return retry policy
     */
    SagaRetryPolicy retryPolicy() default @SagaRetryPolicy;

    /**
     * Delay before first saga retry (ISO-8601 duration).
     *
     * @return initial retry delay
     */
    String retryInitialDelay() default "PT1S";

    /**
     * Maximum delay between saga retries.
     *
     * @return max retry delay
     */
    String retryMaxDelay() default "PT1M";

    /**
     * Retry backoff multiplier.
     * <p>Each retry delay = previous * multiplier.
     *
     * @return backoff multiplier
     */
    double retryBackoffMultiplier() default 2.0;

    /**
     * Jitter factor for retry delays (0.0 - 1.0).
     * <p>Adds randomness to prevent thundering herd.
     *
     * @return jitter factor
     */
    double retryJitter() default 0.1;

    /**
     * Exceptions that should trigger saga retry.
     * <p>Empty = retry on all exceptions.
     *
     * @return retryable exceptions
     */
    Class<? extends Throwable>[] retryOn() default {};

    /**
     * Exceptions that should NOT trigger retry.
     * <p>These exceptions cause immediate failure.
     *
     * @return non-retryable exceptions
     */
    Class<? extends Throwable>[] noRetryOn() default {};

    // ═══════════════════════════════════════════════════════════════════════════
    // COMPENSATION CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Order of compensation execution.
     *
     * @return compensation order
     */
    CompensationOrder compensationOrder() default CompensationOrder.REVERSE;

    /**
     * Compensation execution strategy.
     *
     * @return compensation strategy
     */
    CompensationStrategy compensationStrategy() default CompensationStrategy.ALL_OR_NOTHING;

    /**
     * Timeout for entire compensation phase (ISO-8601 duration).
     *
     * @return compensation timeout
     */
    String compensationTimeout() default "PT10M";

    /**
     * Max retries for individual compensation steps.
     *
     * @return compensation max retries
     */
    int compensationMaxRetries() default 5;

    /**
     * Delay between compensation retries.
     *
     * @return compensation retry delay
     */
    String compensationRetryDelay() default "PT5S";

    /**
     * Continue compensation on individual step failure.
     * <p>When true, continues to next compensation even if one fails.
     *
     * @return continue on failure
     */
    boolean continueCompensationOnFailure() default true;

    /**
     * Dead letter queue for failed compensations.
     * <p>Unrecoverable compensation failures are sent here.
     *
     * @return DLQ topic name
     */
    String compensationDlq() default "";

    /**
     * Custom compensation handler class.
     * <p>Called when automatic compensation fails.
     *
     * @return handler class
     */
    Class<?> compensationFailureHandler() default void.class;

    /**
     * Trigger manual intervention on compensation failure.
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
     */
    SagaPersistence persistence() default SagaPersistence.DATABASE;

    /**
     * Database table name for saga state.
     * <p>Defaults to "saga_{name}_state".
     *
     * @return table name
     */
    String stateTable() default "";

    /**
     * State serialization format.
     *
     * @return state format
     */
    StateFormat stateFormat() default StateFormat.JSON;

    /**
     * Encrypt saga state.
     * <p>For sagas handling sensitive data.
     *
     * @return true to encrypt
     */
    boolean encryptState() default false;

    /**
     * Encryption key alias.
     *
     * @return key alias
     */
    String stateEncryptionKeyAlias() default "";

    /**
     * Compress saga state.
     *
     * @return compression algorithm
     */
    Compression stateCompression() default Compression.NONE;

    /**
     * State retention period in days.
     * <p>Completed saga states are deleted after this period.
     *
     * @return retention days
     */
    int stateRetentionDays() default 90;

    /**
     * Clean up completed sagas after N hours.
     * <p>0 = keep until retention period.
     *
     * @return cleanup delay hours
     */
    int cleanupAfterHours() default 24;

    /**
     * Archive completed sagas.
     *
     * @return true to archive
     */
    boolean archiveCompletedSagas() default false;

    /**
     * Archive storage location.
     *
     * @return archive path
     */
    String archiveStorage() default "";

    // ═══════════════════════════════════════════════════════════════════════════
    // COMMAND DISPATCH
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Command dispatch method.
     * <p>How saga commands are sent to services.
     *
     * @return dispatch method
     */
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
     */
    String commandTopicPattern() default "saga.commands.{service}";

    /**
     * Reply topic for saga responses.
     * <p>Where services send command results.
     *
     * @return reply topic
     */
    String replyTopic() default "";

    /**
     * Use transactional outbox for commands.
     * <p>Commands are written to outbox within saga transaction.
     *
     * @return true to use outbox
     */
    boolean useOutbox() default true;

    /**
     * Command timeout (ISO-8601 duration).
     * <p>Default timeout for all steps (can be overridden per step).
     *
     * @return command timeout
     */
    String defaultCommandTimeout() default "PT30S";

    /**
     * Command acknowledgment mode.
     *
     * @return ack mode
     */
    AckMode commandAckMode() default AckMode.AUTO;

    // ═══════════════════════════════════════════════════════════════════════════
    // IDEMPOTENCY
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Ensure saga is started only once per correlation ID.
     *
     * @return true for idempotent start
     */
    boolean idempotent() default true;

    /**
     * Idempotency key expression (SpEL).
     * <p>Unique key for deduplication.
     *
     * @return idempotency key expression
     */
    String idempotencyKeyExpression() default "#correlationId";

    /**
     * Idempotency window duration.
     * <p>How long to remember processed saga IDs.
     *
     * @return idempotency window
     */
    String idempotencyWindow() default "PT24H";

    /**
     * Idempotency storage.
     *
     * @return storage type
     */
    IdempotencyStorage idempotencyStorage() default IdempotencyStorage.DATABASE;

    // ═══════════════════════════════════════════════════════════════════════════
    // LIFECYCLE HOOKS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Method to call before saga starts.
     * <p>Can modify initial state or validate preconditions.
     *
     * @return method name
     */
    String beforeStart() default "";

    /**
     * Method to call after saga starts.
     * <p>Called after first step begins.
     *
     * @return method name
     */
    String afterStart() default "";

    /**
     * Method to call before each step.
     *
     * @return method name
     */
    String beforeStep() default "";

    /**
     * Method to call after each step completes.
     *
     * @return method name
     */
    String afterStep() default "";

    /**
     * Method to call when saga completes successfully.
     *
     * @return method name
     */
    String onComplete() default "";

    /**
     * Method to call when saga fails.
     *
     * @return method name
     */
    String onFail() default "";

    /**
     * Method to call before compensation starts.
     *
     * @return method name
     */
    String beforeCompensate() default "";

    /**
     * Method to call after compensation completes.
     *
     * @return method name
     */
    String afterCompensate() default "";

    /**
     * Method to call on saga timeout.
     *
     * @return method name
     */
    String onTimeout() default "";

    // ═══════════════════════════════════════════════════════════════════════════
    // CONCURRENCY
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Maximum concurrent saga instances.
     * <p>0 = unlimited.
     *
     * @return max instances
     */
    int maxConcurrentInstances() default 0;

    /**
     * Maximum concurrent instances per tenant.
     *
     * @return max per tenant
     */
    int maxConcurrentPerTenant() default 0;

    /**
     * Maximum concurrent instances per correlation key.
     * <p>Prevents duplicate saga for same entity.
     *
     * @return max per key
     */
    int maxConcurrentPerKey() default 1;

    /**
     * Behavior when concurrency limit reached.
     *
     * @return overflow behavior
     */
    OverflowBehavior overflowBehavior() default OverflowBehavior.QUEUE;

    /**
     * Queue size when overflow = QUEUE.
     *
     * @return queue size
     */
    int overflowQueueSize() default 1000;

    // ═══════════════════════════════════════════════════════════════════════════
    // MONITORING & ALERTING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Emit metrics for this saga.
     *
     * @return true to emit metrics
     */
    boolean emitMetrics() default true;

    /**
     * Metric name prefix.
     *
     * @return metric prefix
     */
    String metricPrefix() default "";

    /**
     * Alert if saga duration exceeds threshold.
     *
     * @return duration alert threshold
     */
    String durationAlertThreshold() default "PT10M";

    /**
     * Alert if saga fails.
     *
     * @return true to alert on failure
     */
    boolean alertOnFailure() default true;

    /**
     * Alert if compensation fails.
     *
     * @return true to alert on compensation failure
     */
    boolean alertOnCompensationFailure() default true;

    /**
     * Alert if saga times out.
     *
     * @return true to alert on timeout
     */
    boolean alertOnTimeout() default true;

    /**
     * Alert if step retries exceed threshold.
     *
     * @return retry alert threshold
     */
    int retryAlertThreshold() default 2;

    /**
     * Notification channels for alerts.
     * <p>Slack channels, email addresses, PagerDuty keys.
     *
     * @return alert channels
     */
    String[] alertChannels() default {};

    /**
     * Alert severity levels.
     *
     * @return alert severities
     */
    AlertSeverity failureAlertSeverity() default AlertSeverity.ERROR;

    /**
     * Health check endpoint.
     *
     * @return true to expose health check
     */
    boolean healthCheck() default true;

    /**
     * Expose saga status API endpoint.
     *
     * @return true to expose API
     */
    boolean exposeStatusApi() default true;

    // ═══════════════════════════════════════════════════════════════════════════
    // DEBUGGING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Log level for saga execution.
     *
     * @return log level
     */
    LogLevel logLevel() default LogLevel.INFO;

    /**
     * Log full state on each step.
     * <p>Warning: May expose sensitive data in logs.
     *
     * @return true to log state
     */
    boolean logStateOnStep() default false;

    /**
     * Enable distributed tracing.
     *
     * @return true to enable tracing
     */
    boolean enableTracing() default true;

    /**
     * Trace sample rate (0.0 - 1.0).
     *
     * @return sample rate
     */
    double traceSampleRate() default 1.0;

    // ═══════════════════════════════════════════════════════════════════════════
    // TESTING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Generate test fixtures for this saga.
     *
     * @return true to generate
     */
    boolean generateTestFixtures() default true;

    /**
     * Use in-memory persistence in tests.
     *
     * @return true for in-memory
     */
    boolean inMemoryPersistenceInTests() default true;

    /**
     * Mock all external services in tests.
     *
     * @return true to mock
     */
    boolean mockServicesInTests() default true;

    // ═══════════════════════════════════════════════════════════════════════════
    // ENUMS
    // ═══════════════════════════════════════════════════════════════════════════

    enum CompensationOrder {
        /** Execute compensation in reverse order of execution */
        REVERSE,

        /** Execute all compensations in parallel */
        PARALLEL,

        /** Execute compensation in forward order */
        FORWARD,

        /** Custom order defined by compensation dependencies */
        CUSTOM
    }

    enum CompensationStrategy {
        /** All compensations must succeed or saga remains in failed state */
        ALL_OR_NOTHING,

        /** Best effort - continue on failure, log issues */
        BEST_EFFORT,

        /** Stop compensation on first failure */
        STOP_ON_FAILURE,

        /** No automatic compensation - manual intervention required */
        MANUAL
    }

    enum SagaPersistence {
        /** PostgreSQL/MySQL database */
        DATABASE,

        /** Redis */
        REDIS,

        /** Event store (EventStoreDB, etc.) */
        EVENT_STORE,

        /** In-memory (testing only) */
        IN_MEMORY,

        /** Custom implementation */
        CUSTOM
    }

    enum StateFormat {
        JSON,
        BINARY,
        PROTOBUF,
        MSGPACK
    }

    enum Compression {
        NONE,
        GZIP,
        LZ4,
        SNAPPY,
        ZSTD
    }

    enum CommandDispatch {
        /** Apache Kafka */
        KAFKA,

        /** RabbitMQ */
        RABBITMQ,

        /** HTTP REST calls */
        HTTP,

        /** In-process method calls */
        IN_PROCESS,

        /** AWS SQS */
        SQS,

        /** Azure Service Bus */
        AZURE_SERVICE_BUS
    }

    enum AckMode {
        /** Automatic acknowledgment */
        AUTO,

        /** Manual acknowledgment required */
        MANUAL,

        /** Acknowledge after processing */
        AFTER_PROCESS
    }

    enum IdempotencyStorage {
        DATABASE,
        REDIS,
        IN_MEMORY
    }

    enum OverflowBehavior {
        /** Queue excess requests */
        QUEUE,

        /** Reject excess requests */
        REJECT,

        /** Drop oldest queued request */
        DROP_OLDEST,

        /** Process anyway (ignore limit) */
        IGNORE
    }

    enum AlertSeverity {
        INFO,
        WARNING,
        ERROR,
        CRITICAL
    }

    enum LogLevel {
        TRACE,
        DEBUG,
        INFO,
        WARN,
        ERROR,
        OFF
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NESTED ANNOTATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    @interface SagaTrigger {
        /** Trigger type */
        TriggerType type() default TriggerType.COMMAND;

        /** Trigger source class (command or event) */
        Class<?> source() default void.class;

        /** Trigger source name (when class is not available) */
        String sourceName() default "";

        /** Trigger source name pattern (regex) */
        String sourcePattern() default "";

        /** Topic to listen on */
        String topic() default "";

        /** Consumer group for topic */
        String consumerGroup() default "";

        /** Filter expression (SpEL) */
        String filter() default "";

        /** Start position for event triggers */
        StartPosition startPosition() default StartPosition.LATEST;

        /** Schedule expression (for SCHEDULE type) */
        String schedule() default "";

        /** HTTP path (for HTTP type) */
        String httpPath() default "";

        /** HTTP method (for HTTP type) */
        String httpMethod() default "POST";

        enum TriggerType {
            /** Started by command */
            COMMAND,

            /** Started by domain event */
            EVENT,

            /** Started on schedule (cron) */
            SCHEDULE,

            /** Started via HTTP API */
            HTTP,

            /** Started programmatically */
            MANUAL
        }

        enum StartPosition {
            EARLIEST,
            LATEST,
            SPECIFIC
        }
    }

    @interface SagaRetryPolicy {
        int maxAttempts() default 3;
        String initialDelay() default "PT1S";
        String maxDelay() default "PT1M";
        double multiplier() default 2.0;
        double jitter() default 0.1;
        Class<? extends Throwable>[] retryOn() default {};
        Class<? extends Throwable>[] noRetryOn() default {};
        boolean retryOnTimeout() default true;
        boolean retryOnNetworkError() default true;
    }
}


