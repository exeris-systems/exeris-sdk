package eu.exeris.sdk.annotation;

import java.lang.annotation.*;

/**
 * Defines a CQRS projection that builds a read model from events.
 *
 * <p>Projections subscribe to domain events and maintain denormalized views
 * optimized for specific query patterns. They enable the Query side of CQRS.
 *
 * <h2>Basic Usage:</h2>
 * <pre>{@code
 * @Projection(
 *     name = "account-balance",
 *     description = "Real-time account balance view",
 *     events = {"TransactionCreatedEvent", "TransactionClearedEvent"},
 *     model = AccountBalanceView.class
 * )
 * public class AccountBalanceProjection implements Projection {
 *
 *     @EventHandler
 *     public void on(TransactionCreatedEvent event) {
 *         // Update read model
 *     }
 * }
 * }</pre>
 *
 * <h2>With Partitioning:</h2>
 * <pre>{@code
 * @Projection(
 *     name = "order-summary",
 *     events = {"OrderCreatedEvent", "OrderUpdatedEvent"},
 *     model = OrderSummaryView.class,
 *     partitioned = true,
 *     partitionKey = "customerId",
 *     partitionCount = 12
 * )
 * public class OrderSummaryProjection implements Projection {}
 * }</pre>
 *
 * <h2>With Rebuild Configuration:</h2>
 * <pre>{@code
 * @Projection(
 *     name = "monthly-report",
 *     events = {"*"}, // All events
 *     model = MonthlyReportView.class,
 *     rebuildStrategy = RebuildStrategy.BLUE_GREEN,
 *     onlineRebuild = true,
 *     rebuildParallelism = 8
 * )
 * public class MonthlyReportProjection implements Projection {}
 * }</pre>
 *
 * @author Exeris platform Team
 * @version 1.0.0
 * @since 1.0.0
 * @see DomainEvent
 * @see EventSourced
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SuppressWarnings({"PMD.ExcessivePublicCount", "PMD.TooManyMethods"})
public @interface Projection {

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // IDENTITY
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Unique projection name.
     * <p>Used for identification, checkpoints, and administration.
     *
     * <p><strong>Naming convention:</strong> kebab-case
     * <ul>
     *   <li>"account-balance"</li>
     *   <li>"order-summary"</li>
     *   <li>"monthly-revenue"</li>
     * </ul>
     *
     * @return projection name
     */
    String name();

    /**
     * Human-readable description.
     *
     * @return projection description
     */
    String description() default "";

    /**
     * Projection version.
     * <p>Increment to trigger rebuild.
     *
     * @return version number
     */
    int version() default 1;

    /**
     * Module this projection belongs to.
     *
     * @return module name
     */
    String module() default "";

    /**
     * Projection group (for related projections).
     *
     * @return group name
     */
    String group() default "";

    /**
     * Tags for categorization.
     *
     * @return tags
     */
    String[] tags() default {};

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // EVENT SUBSCRIPTION
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Event types this projection handles.
     * <p>Use "*" for all events in a category.
     *
     * @return event type names
     */
    String[] events();

    /**
     * Event classes (alternative to string names).
     *
     * @return event classes
     */
    Class<?>[] eventClasses() default {};

    /**
     * Aggregate types to subscribe to.
     * <p>Receives all events from these aggregates.
     *
     * @return aggregate type names
     */
    String[] aggregateTypes() default {};

    /**
     * Topic pattern for subscription (regex).
     *
     * @return topic pattern
     */
    String topicPattern() default "";

    /**
     * Specific topics to subscribe to.
     *
     * @return topic names
     */
    String[] topics() default {};

    /**
     * Filter expression for events (SpEL).
     * <p>Only process events matching filter.
     *
     * @return filter expression
     */
    String eventFilter() default "";

    /**
     * Consumer group ID.
     * <p>Defaults to projection name.
     *
     * @return consumer group
     */
    String consumerGroup() default "";

    /**
     * Start position for new subscription.
     *
     * @return start position
     */
    StartPosition startPosition() default StartPosition.EARLIEST;

    /**
     * Specific position to start from (if SPECIFIC).
     * <p>Format: "stream:position" or "timestamp:ISO-8601"
     *
     * @return specific position
     */
    String specificPosition() default "";

    /**
     * Subscribe to live events only (no replay).
     *
     * @return true for live only
     */
    boolean liveOnly() default false;

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // READ MODEL
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Primary read model class (view entity).
     *
     * @return model class
     */
    Class<?> model();

    /**
     * Additional models maintained by this projection.
     *
     * @return additional model classes
     */
    Class<?>[] additionalModels() default {};

    /**
     * Repository class for read model.
     *
     * @return repository class
     */
    Class<?> repository() default void.class;

    /**
     * Database schema for read model tables.
     *
     * @return schema name
     */
    String schema() default "";

    /**
     * Table name prefix for projection views.
     *
     * @return table prefix
     */
    String tablePrefix() default "";

    /**
     * Separate database for read model.
     * <p>DataSource name for read model storage.
     *
     * @return datasource name
     */
    String dataSource() default "";

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // PROCESSING CONFIGURATION
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Process events asynchronously.
     *
     * @return true for async
     */
    boolean async() default true;

    /**
     * Process events in parallel.
     *
     * @return true for parallel
     */
    boolean parallel() default false;

    /**
     * Number of parallel workers.
     *
     * @return parallelism level
     */
    int parallelism() default 1;

    /**
     * Batch size for event processing.
     * <p>Process N events in single transaction.
     *
     * @return batch size
     */
    int batchSize() default 100;

    /**
     * Maximum batch wait time.
     * <p>Flush batch after this duration even if not full.
     *
     * @return batch timeout
     */
    String batchTimeout() default "PT1S";

    /**
     * Processing timeout per event.
     *
     * @return processing timeout
     */
    String processingTimeout() default "PT30S";

    /**
     * Maximum processing lag before alert.
     *
     * @return max lag duration
     */
    String maxLag() default "PT5M";

    /**
     * Ordered processing (within partition).
     *
     * @return true for ordered
     */
    boolean ordered() default true;

    /**
     * Transaction mode for processing.
     *
     * @return transaction mode
     */
    TransactionMode transactionMode() default TransactionMode.PER_BATCH;

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // PARTITIONING
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Enable partitioned processing.
     *
     * @return true for partitioned
     */
    boolean partitioned() default false;

    /**
     * Partition key field (for ordering within partition).
     *
     * @return partition key field
     */
    String partitionKey() default "";

    /**
     * Number of partitions.
     * <p>0 = auto-detect from source.
     *
     * @return partition count
     */
    int partitionCount() default 0;

    /**
     * Partition assignment strategy.
     *
     * @return assignment strategy
     */
    PartitionAssignment partitionAssignment() default PartitionAssignment.RANGE;

    /**
     * Rebalance listener class.
     *
     * @return listener class
     */
    Class<?> rebalanceListener() default void.class;

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // ERROR HANDLING
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Error handling strategy.
     *
     * @return error strategy
     */
    ErrorStrategy errorStrategy() default ErrorStrategy.RETRY;

    /**
     * Max retries per event.
     *
     * @return max retries
     */
    int maxRetries() default 3;

    /**
     * Initial retry delay.
     *
     * @return retry delay
     */
    String retryDelay() default "PT1S";

    /**
     * Retry backoff multiplier.
     *
     * @return backoff multiplier
     */
    double retryBackoff() default 2.0;

    /**
     * Max retry delay.
     *
     * @return max delay
     */
    String maxRetryDelay() default "PT1M";

    /**
     * Exceptions that should trigger retry.
     *
     * @return retryable exceptions
     */
    Class<? extends Throwable>[] retryOn() default {};

    /**
     * Exceptions that should NOT retry.
     *
     * @return non-retryable exceptions
     */
    Class<? extends Throwable>[] noRetryOn() default {};

    /**
     * Dead letter topic for failed events.
     *
     * @return DLT topic
     */
    String deadLetterTopic() default "";

    /**
     * Continue processing on error (skip failed event).
     *
     * @return true to continue
     */
    boolean continueOnError() default false;

    /**
     * Pause projection on N consecutive failures.
     *
     * @return pause threshold
     */
    int pauseAfterFailures() default 10;

    /**
     * Error handler class.
     *
     * @return handler class
     */
    Class<?> errorHandler() default void.class;

    /**
     * Store failed events for later reprocessing.
     *
     * @return true to store
     */
    boolean storeFailedEvents() default true;

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // REBUILD CONFIGURATION
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Rebuild strategy.
     *
     * @return rebuild strategy
     */
    RebuildStrategy rebuildStrategy() default RebuildStrategy.FULL;

    /**
     * Allow online rebuild (while serving queries).
     *
     * @return true for online rebuild
     */
    boolean onlineRebuild() default true;

    /**
     * Parallelism for rebuild.
     *
     * @return rebuild parallelism
     */
    int rebuildParallelism() default 4;

    /**
     * Batch size for rebuild.
     *
     * @return rebuild batch size
     */
    int rebuildBatchSize() default 1000;

    /**
     * Events that trigger rebuild.
     * <p>Schema change events, etc.
     *
     * @return trigger events
     */
    String[] rebuildOnEvents() default {};

    /**
     * Method to call before rebuild.
     *
     * @return method name
     */
    String beforeRebuild() default "";

    /**
     * Method to call after rebuild.
     *
     * @return method name
     */
    String afterRebuild() default "";

    /**
     * Validate data after rebuild.
     *
     * @return true to validate
     */
    boolean validateAfterRebuild() default true;

    /**
     * Rebuild timeout.
     *
     * @return timeout duration
     */
    String rebuildTimeout() default "PT1H";

    /**
     * Keep old data during rebuild (for rollback).
     *
     * @return true to keep old
     */
    boolean keepOldDataDuringRebuild() default true;

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // CONSISTENCY
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Consistency guarantee.
     *
     * @return consistency level
     */
    Consistency consistency() default Consistency.EVENTUAL;

    /**
     * Checkpoint interval (event count).
     *
     * @return checkpoint interval
     */
    int checkpointInterval() default 100;

    /**
     * Checkpoint interval (time).
     *
     * @return checkpoint time interval
     */
    String checkpointTime() default "PT10S";

    /**
     * Checkpoint storage.
     *
     * @return storage type
     */
    CheckpointStorage checkpointStorage() default CheckpointStorage.DATABASE;

    /**
     * Enable idempotent processing.
     *
     * @return true for idempotent
     */
    boolean idempotent() default true;

    /**
     * Track processed event IDs for deduplication.
     *
     * @return true to track
     */
    boolean trackProcessedEvents() default true;

    /**
     * Event ID retention for deduplication.
     *
     * @return retention duration
     */
    String eventIdRetention() default "PT24H";

    /**
     * Event ID storage.
     *
     * @return storage type
     */
    EventIdStorage eventIdStorage() default EventIdStorage.DATABASE;

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // CACHING
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Cache read model.
     *
     * @return true to cache
     */
    boolean cacheReadModel() default false;

    /**
     * Cache TTL.
     *
     * @return cache TTL
     */
    String cacheTtl() default "PT5M";

    /**
     * Cache max size.
     *
     * @return max entries
     */
    int cacheMaxSize() default 10_000;

    /**
     * Cache region name.
     *
     * @return region name
     */
    String cacheRegion() default "";

    /**
     * Events that invalidate cache.
     *
     * @return invalidation events
     */
    String[] cacheInvalidationEvents() default {};

    /**
     * Cache invalidation strategy.
     *
     * @return invalidation strategy
     */
    CacheInvalidation cacheInvalidation() default CacheInvalidation.ENTRY;

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // MONITORING
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Emit metrics.
     *
     * @return true to emit
     */
    boolean emitMetrics() default true;

    /**
     * Metric name prefix.
     *
     * @return prefix
     */
    String metricPrefix() default "";

    /**
     * Health check endpoint.
     *
     * @return true to expose
     */
    boolean healthCheck() default true;

    /**
     * Alert on processing lag.
     *
     * @return lag threshold
     */
    String lagAlertThreshold() default "PT5M";

    /**
     * Alert on error rate.
     *
     * @return error rate threshold (0.0 - 1.0)
     */
    double errorRateAlertThreshold() default 0.01;

    /**
     * Alert on rebuild failure.
     *
     * @return true to alert
     */
    boolean alertOnRebuildFailure() default true;

    /**
     * Alert channels.
     *
     * @return channel names
     */
    String[] alertChannels() default {};

    /**
     * Log level.
     *
     * @return log level
     */
    LogLevel logLevel() default LogLevel.INFO;

    /**
     * Log processed events.
     *
     * @return true to log
     */
    boolean logProcessedEvents() default false;

    /**
     * Trace sample rate.
     *
     * @return sample rate (0.0 - 1.0)
     */
    double traceSampleRate() default 0.1;

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // LIFECYCLE
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Enable projection on startup.
     *
     * @return true to enable
     */
    boolean enabledOnStartup() default true;

    /**
     * Start priority (lower = starts first).
     *
     * @return priority
     */
    int priority() default 100;

    /**
     * Dependencies (projections that must be running).
     *
     * @return dependency names
     */
    String[] dependsOn() default {};

    /**
     * Method to call on projection start.
     *
     * @return method name
     */
    String onStart() default "";

    /**
     * Method to call on projection stop.
     *
     * @return method name
     */
    String onStop() default "";

    /**
     * Method to call on projection pause.
     *
     * @return method name
     */
    String onPause() default "";

    /**
     * Method to call on projection resume.
     *
     * @return method name
     */
    String onResume() default "";

    /**
     * Graceful shutdown timeout.
     *
     * @return shutdown timeout
     */
    String shutdownTimeout() default "PT30S";

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // QUERY API
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Expose REST query API.
     *
     * @return true to expose
     */
    boolean exposeRestApi() default true;

    /**
     * REST API base path.
     *
     * @return base path
     */
    String restApiPath() default "";

    /**
     * Expose GraphQL query API.
     *
     * @return true to expose
     */
    boolean exposeGraphqlApi() default true;

    /**
     * GraphQL type name.
     *
     * @return type name
     */
    String graphqlTypeName() default "";

    /**
     * Expose internal service client for this projection.
     * <p>Generates type-safe HTTP client for inter-service queries.
     *
     * @return true to expose internal client
     */
    boolean exposeInternalClient() default false;

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // TESTING
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Generate test fixtures.
     *
     * @return true to generate
     */
    boolean generateTestFixtures() default true;

    /**
     * Use in-memory storage in tests.
     *
     * @return true for in-memory
     */
    boolean inMemoryStorageInTests() default true;

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // ENUMS
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    enum StartPosition {
        /** Start from beginning of stream */
        EARLIEST,

        /** Start from current position (new events only) */
        LATEST,

        /** Start from last checkpoint */
        LAST_CHECKPOINT,

        /** Start from specific position */
        SPECIFIC
    }

    enum TransactionMode {
        /** One transaction per event */
        PER_EVENT,

        /** One transaction per batch */
        PER_BATCH,

        /** No transaction (manual) */
        NONE
    }

    enum PartitionAssignment {
        /** Range-based assignment */
        RANGE,

        /** Round-robin assignment */
        ROUND_ROBIN,

        /** Sticky assignment */
        STICKY,

        /** Cooperative sticky */
        COOPERATIVE_STICKY
    }

    enum ErrorStrategy {
        /** Retry failed events */
        RETRY,

        /** Skip failed events */
        SKIP,

        /** Send to dead letter topic */
        DEAD_LETTER,

        /** Stop projection */
        STOP,

        /** Pause and alert */
        PAUSE,

        /** Custom handler */
        CUSTOM
    }

    enum RebuildStrategy {
        /** Delete all and rebuild from scratch */
        FULL,

        /** Incremental rebuild from last position */
        INCREMENTAL,

        /** Build new table, then swap (zero downtime) */
        BLUE_GREEN,

        /** Shadow mode - rebuild alongside live */
        SHADOW,

        /** No automatic rebuild */
        MANUAL
    }

    enum Consistency {
        /** Eventually consistent (async processing) */
        EVENTUAL,

        /** Causal consistency (within partition) */
        CAUSAL,

        /** Strong consistency (sync processing) */
        STRONG,

        /** Session consistency */
        SESSION
    }

    enum CheckpointStorage {
        DATABASE,
        REDIS,
        KAFKA,
        FILE,
        CUSTOM
    }

    enum EventIdStorage {
        DATABASE,
        REDIS,
        BLOOM_FILTER,
        IN_MEMORY
    }

    enum CacheInvalidation {
        /** Invalidate single entry */
        ENTRY,

        /** Invalidate by pattern */
        PATTERN,

        /** Invalidate entire cache */
        ALL,

        /** Time-based expiration only */
        TTL_ONLY
    }

    enum LogLevel {
        TRACE,
        DEBUG,
        INFO,
        WARN,
        ERROR,
        OFF
    }
}


