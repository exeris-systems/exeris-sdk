package eu.exeris.sdk.annotation;

import java.lang.annotation.*;

/**
 * Marks an aggregate as event-sourced.
 *
 * <p>Event-sourced aggregates persist all changes as a sequence of events.
 * The aggregate state is reconstructed by replaying events from the event store.
 *
 * <h2>Basic Usage:</h2>
 * {@snippet lang="java" :
 * @ExerisDomain(module = "billing")
 * @EventSourced(
 *     snapshotThreshold = 50,
 *     streamPrefix = "BillingSubscription"
 * )
 * public class BillingSubscription extends EventSourcedAggregate {
 *     // Aggregate implementation
 * }
 * }
 *
 * <h2>With Custom Snapshot Strategy:</h2>
 * {@snippet lang="java" :
 * @EventSourced(
 *     snapshotThreshold = 100,
 *     snapshotStrategy = SnapshotStrategy.HYBRID,
 *     snapshotInterval = "PT1H",
 *     snapshotOnEvents = {"OrderCompletedEvent", "OrderCancelledEvent"}
 * )
 * public class Order extends EventSourcedAggregate {}
 * }
 *
 * <h2>With Caching:</h2>
 * {@snippet lang="java" :
 * @EventSourced(
 *     cacheAggregates = true,
 *     cacheTtlSeconds = 300,
 *     cacheMaxSize = 1000,
 *     cacheEviction = CacheEviction.LRU
 * )
 * public class Account extends EventSourcedAggregate {}
 * }
 *
 *
 * <p><strong>Status: PARTIAL</strong> — extracted into {@code EventSourcedMetadata}, so it
 * survives onto the wire, and read by no generator: event-sourcing emission is not
 * implemented yet. This is a tooling gap rather than a kernel one — the kernel line ships
 * the replayable read and the optimistic-concurrency write with both Community bindings and
 * a TCK.
 * @since 0.1
 * @see DomainEvent
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Documented
@SuppressWarnings({"PMD.ExcessivePublicCount", "PMD.TooManyMethods"})
public @interface EventSourced {

    // STREAM CONFIGURATION

    /**
     * Event stream name prefix.
     * <p>Final stream name: {prefix}-{aggregateId}
     * <p>Defaults to aggregate class name.
     *
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>"Order" → "Order-550e8400-e29b-41d4-a716-446655440000"</li>
     *   <li>"billing.subscription" → "billing.subscription-..."</li>
     * </ul>
     *
     * @return stream prefix
     */
    String streamPrefix() default "";

    /**
     * Stream category for event store.
     * <p>Used for subscriptions and projections that process
     * all aggregates of this type.
     *
     * <p><strong>Example:</strong> "$ce-Order" (category for all Order streams)
     *
     * @return stream category
     */
    String category() default "";

    /**
     * Maximum events per stream before archival.
     * <p>Older events are archived when limit exceeded.
     * Set to 0 for unlimited.
     *
     * @return max events per stream
     */
    int maxEventsPerStream() default 0;

    /**
     * Expected events per aggregate (optimization hint).
     * <p>Used to pre-allocate resources.
     *
     * @return expected event count
     */
    int expectedEventsPerAggregate() default 100;

    /**
     * Stream metadata to include.
     * <p>Static metadata added to stream.
     *
     * @return metadata entries
     */
    StreamMetadata[] streamMetadata() default {};

    // SNAPSHOT CONFIGURATION

    /**
     * Create snapshot every N events.
     * <p>Improves loading performance for aggregates with many events.
     * Set to 0 to disable automatic snapshots.
     *
     * @return snapshot threshold
     */
    int snapshotThreshold() default 50;

    /**
     * Snapshot creation strategy.
     * <p>Determines when snapshots are created.
     *
     * @return snapshot strategy
     */
    SnapshotStrategy snapshotStrategy() default SnapshotStrategy.COUNT_BASED;

    /**
     * Time interval for time-based snapshots (ISO-8601 duration).
     * <p>Only used when strategy is TIME_BASED or HYBRID.
     *
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>"PT1H" - Every hour</li>
     *   <li>"P1D" - Every day</li>
     *   <li>"PT30M" - Every 30 minutes</li>
     * </ul>
     *
     * @return snapshot interval
     */
    String snapshotInterval() default "PT1H";

    /**
     * Events that trigger immediate snapshot.
     * <p>Snapshot is created after these event types.
     *
     * <p><strong>Example:</strong>
     * {@snippet lang="java" :
     * snapshotOnEvents = {"OrderCompletedEvent", "OrderCancelledEvent"}
     * }
     *
     * @return event names that trigger snapshot
     */
    String[] snapshotOnEvents() default {};

    /**
     * Snapshot serialization format.
     *
     * @return snapshot format
     */
    SnapshotFormat snapshotFormat() default SnapshotFormat.JSON;

    /**
     * Custom snapshot serializer class.
     * <p>Must implement {@code SnapshotSerializer<T>}.
     *
     * @return serializer class
     */
    Class<?> snapshotSerializer() default void.class;

    /**
     * Compress snapshots using specified algorithm.
     *
     * @return compression algorithm
     */
    Compression snapshotCompression() default Compression.NONE;

    /**
     * Encrypt snapshots for sensitive data.
     * <p>Uses configured encryption key.
     *
     * @return true to encrypt snapshots
     */
    boolean encryptSnapshots() default false;

    /**
     * Encryption key alias (from key management).
     *
     * @return key alias
     */
    String snapshotEncryptionKeyAlias() default "";

    /**
     * Number of snapshots to retain per aggregate.
     * <p>Older snapshots are deleted. 0 = keep all.
     *
     * @return snapshot retention count
     */
    int snapshotRetention() default 5;

    /**
     * Snapshot storage location.
     *
     * @return storage type
     */
    SnapshotStorage snapshotStorage() default SnapshotStorage.EVENT_STORE;

    /**
     * External storage bucket/container (for OBJECT_STORAGE).
     *
     * @return storage bucket name
     */
    String snapshotStorageBucket() default "";

    /**
     * Validate snapshot integrity on load.
     * <p>Calculates and verifies checksum.
     *
     * @return true to validate
     */
    boolean validateSnapshotIntegrity() default true;

    // CONCURRENCY & CONSISTENCY

    /**
     * Enable optimistic concurrency control.
     * <p>Uses expected version for conflict detection.
     *
     * @return true for optimistic locking
     */
    boolean optimisticConcurrency() default true;

    /**
     * Lock strategy for concurrent modifications.
     *
     * @return lock strategy
     */
    LockStrategy lockStrategy() default LockStrategy.OPTIMISTIC;

    /**
     * Lock acquisition timeout in milliseconds.
     *
     * @return lock timeout
     */
    long lockTimeoutMs() default 5000;

    /**
     * Retry policy for version conflicts.
     *
     * @return retry policy
     */
    RetryPolicy retryPolicy() default @RetryPolicy;

    /**
     * Maximum concurrent commands per aggregate.
     * <p>Commands exceeding limit are queued.
     *
     * @return max concurrent commands
     */
    int maxConcurrentCommands() default 1;

    // EVENT HANDLING

    /**
     * Method naming pattern for event handlers.
     * <p>Supports placeholders: {eventName}
     *
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>"apply" → apply(OrderCreatedEvent event)</li>
     *   <li>"on{eventName}" → onOrderCreated(OrderCreatedEvent event)</li>
     *   <li>"handle{eventName}" → handleOrderCreated(OrderCreatedEvent event)</li>
     * </ul>
     *
     * @return method name pattern
     */
    String eventHandlerMethodPattern() default "apply";

    /**
     * Validate events before persisting.
     * <p>Calls aggregate's validate() method.
     *
     * @return true to validate
     */
    boolean validateEvents() default true;

    /**
     * Event validator class.
     * <p>Must implement {@code EventValidator<T>}.
     *
     * @return validator class
     */
    Class<?> eventValidator() default void.class;

    /**
     * Event upgrader/transformer class.
     * <p>Upgrades old event versions to current.
     * Must implement {@code EventUpgrader}.
     *
     * @return upgrader class
     */
    Class<?> eventUpgrader() default void.class;

    /**
     * Strict event handling mode.
     * <p>Fail if unknown event type encountered during replay.
     *
     * @return true for strict mode
     */
    boolean strictEventHandling() default false;

    /**
     * Event handler timeout in milliseconds.
     * <p>Maximum time for applying single event.
     *
     * @return handler timeout
     */
    long eventHandlerTimeoutMs() default 5000;

    /**
     * Log event application at specified level.
     *
     * @return log level
     */
    LogLevel eventHandlerLogLevel() default LogLevel.DEBUG;

    // RETENTION & ARCHIVAL

    /**
     * Event retention policy.
     *
     * @return retention policy
     */
    RetentionPolicy retentionPolicy() default RetentionPolicy.KEEP_ALL;

    /**
     * Keep events for N days (if policy = TIME_BASED).
     *
     * @return retention days
     */
    int retentionDays() default 0;

    /**
     * Keep N most recent events (if policy = COUNT_BASED).
     *
     * @return retention count
     */
    int retentionCount() default 0;

    /**
     * Archive old events to cold storage.
     *
     * @return true to archive
     */
    boolean archiveOldEvents() default false;

    /**
     * Archive events older than N days.
     *
     * @return archive threshold
     */
    int archiveAfterDays() default 365;

    /**
     * Archive storage location.
     * <p>S3 bucket, Azure blob container, etc.
     *
     * @return archive storage path
     */
    String archiveStorage() default "";

    /**
     * Archive compression algorithm.
     *
     * @return compression
     */
    Compression archiveCompression() default Compression.GZIP;

    /**
     * Delete archived events from event store.
     *
     * @return true to delete after archive
     */
    boolean deleteAfterArchive() default false;

    // ═══════════════════════════════════════════════════════════════════════════
    // CACHING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Cache loaded aggregates in memory.
     * <p>Improves performance for frequently accessed aggregates.
     *
     * @return true to cache
     */
    boolean cacheAggregates() default true;

    /**
     * Cache Time-To-Live in seconds.
     * <p>Aggregates evicted after TTL.
     *
     * @return cache TTL
     */
    int cacheTtlSeconds() default 300;

    /**
     * Maximum cached aggregates.
     * <p>Oldest evicted when limit reached.
     *
     * @return max cache size
     */
    int cacheMaxSize() default 1000;

    /**
     * Cache eviction policy.
     *
     * @return eviction policy
     */
    CacheEviction cacheEviction() default CacheEviction.LRU;

    /**
     * Cache region/namespace.
     * <p>For distributed caches.
     *
     * @return cache region
     */
    String cacheRegion() default "";

    /**
     * Invalidate cache on external events.
     * <p>Event types that should invalidate cached aggregate.
     *
     * @return invalidation events
     */
    String[] cacheInvalidationEvents() default {};

    /**
     * Warm cache on startup.
     * <p>Pre-load frequently accessed aggregates.
     *
     * @return true to warm cache
     */
    boolean warmCacheOnStartup() default false;

    /**
     * Aggregates to pre-load (SpEL expression returning IDs).
     *
     * @return warm cache expression
     */
    String warmCacheExpression() default "";

    // ═══════════════════════════════════════════════════════════════════════════
    // PROJECTIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Projections that consume this aggregate's events.
     * <p>For documentation and validation.
     *
     * @return projection names
     */
    String[] projections() default {};

    /**
     * Auto-rebuild projections on aggregate schema change.
     *
     * @return true to auto-rebuild
     */
    boolean autoRebuildProjections() default false;

    /**
     * Notify projections of schema changes.
     *
     * @return true to notify
     */
    boolean notifyProjectionsOnSchemaChange() default true;

    // ═══════════════════════════════════════════════════════════════════════════
    // MONITORING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Emit metrics for this aggregate.
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
     * Alert if event replay takes longer than threshold.
     *
     * @return replay alert threshold in ms
     */
    long replayAlertThresholdMs() default 1000;

    /**
     * Alert if aggregate has more than N events.
     *
     * @return event count alert threshold
     */
    int eventCountAlertThreshold() default 10_000;

    /**
     * Alert if snapshot is older than N hours.
     *
     * @return snapshot age alert threshold
     */
    int snapshotAgeAlertHours() default 24;

    /**
     * Health check endpoint for this aggregate.
     *
     * @return true to expose health check
     */
    boolean healthCheck() default true;

    // ═══════════════════════════════════════════════════════════════════════════
    // TESTING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Enable event sourcing test fixtures.
     * <p>Generates test helpers for this aggregate.
     *
     * @return true to generate test fixtures
     */
    boolean generateTestFixtures() default true;

    /**
     * In-memory event store for testing.
     *
     * @return true to use in-memory store in tests
     */
    boolean inMemoryStoreInTests() default true;

    // ═══════════════════════════════════════════════════════════════════════════
    // ENUMS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Strategy for creating snapshots of aggregate state.
     */
    enum SnapshotStrategy {
        /** Snapshot after N events. */
        COUNT_BASED,

        /** Snapshot after time interval. */
        TIME_BASED,

        /** Combination of count and time. */
        HYBRID,

        /** Snapshot on specific events. */
        EVENT_BASED,

        /** Manual snapshots only. */
        MANUAL,

        /** No snapshots. */
        NONE
    }

    /**
     * Serialization format for snapshots.
     */
    enum SnapshotFormat {
        /** JSON serialization. */
        JSON,

        /** Binary Java serialization. */
        BINARY,

        /** Protocol Buffers. */
        PROTOBUF,

        /** MessagePack. */
        MSGPACK,

        /** Custom serializer. */
        CUSTOM
    }

    /**
     * Storage backend for snapshots.
     */
    enum SnapshotStorage {
        /** Store in event store. */
        EVENT_STORE,

        /** Separate snapshot table/collection. */
        DEDICATED_TABLE,

        /** Redis or similar cache. */
        CACHE,

        /** S3/Blob storage. */
        OBJECT_STORAGE
    }

    /**
     * Compression algorithm for event data.
     */
    enum Compression {
        /** No compression. */
        NONE,
        /** GZIP compression. */
        GZIP,
        /** LZ4 compression. */
        LZ4,
        /** Snappy compression. */
        SNAPPY,
        /** Zstandard compression. */
        ZSTD
    }

    /**
     * Locking strategy for concurrent access.
     */
    enum LockStrategy {
        /** Optimistic locking with version. */
        OPTIMISTIC,

        /** Pessimistic database locking. */
        PESSIMISTIC,

        /** Distributed lock (Redis, etc.). */
        DISTRIBUTED,

        /** No locking (single writer assumption). */
        NONE
    }

    /**
     * Policy for retaining old events.
     */
    enum RetentionPolicy {
        /** Keep all events forever. */
        KEEP_ALL,

        /** Keep events for N days. */
        TIME_BASED,

        /** Keep only N most recent events. */
        COUNT_BASED,

        /** Keep events since last snapshot + buffer. */
        SNAPSHOT_BASED
    }

    /**
     * Cache eviction strategy for aggregate cache.
     */
    enum CacheEviction {
        /** Least Recently Used. */
        LRU,

        /** Least Frequently Used. */
        LFU,

        /** First In First Out. */
        FIFO,

        /** Time-based expiration. */
        TIME_BASED,

        /** Size-based eviction. */
        SIZE_BASED
    }

    /**
     * Log levels for event sourcing operations.
     */
    enum LogLevel {
        /** Trace-level logging (most verbose). */
        TRACE,
        /** Debug-level logging. */
        DEBUG,
        /** Info-level logging. */
        INFO,
        /** Warning-level logging. */
        WARN,
        /** Error-level logging. */
        ERROR,
        /** Logging disabled. */
        OFF
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NESTED ANNOTATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Retry policy for failed event operations.
     *
     * <p><strong>Status: RESERVED</strong> — {@link EventSourced#retryPolicy()} is not
     * extracted, so this reaches no AST component — narrower than {@code @EventSourced}
     * itself, which does reach the AST and stops there.
     */
    @Retention(java.lang.annotation.RetentionPolicy.SOURCE)
    @Target({})
    @interface RetryPolicy {
        /** Maximum number of retry attempts.
         * @return max attempts */
        int maxAttempts() default 3;

        /** Initial delay in milliseconds.
         * @return initial delay */
        long initialDelayMs() default 100;

        /** Backoff multiplier for exponential backoff.
         * @return multiplier */
        double multiplier() default 2.0;

        /** Maximum delay in milliseconds.
         * @return max delay */
        long maxDelayMs() default 5000;

        /** Exception types that should trigger a retry.
         * @return exception types to retry on */
        Class<? extends Throwable>[] retryOn() default {};

        /** Exception types that should NOT trigger a retry.
         * @return exception types to not retry on */
        Class<? extends Throwable>[] noRetryOn() default {};

        /** Whether to retry on concurrency failures.
         * @return true if should retry on concurrency failures */
        boolean retryOnConcurrencyFailure() default true;
    }

    /**
     * Custom metadata for event streams.
     *
     * <p><strong>Status: RESERVED</strong> — {@link EventSourced#streamMetadata()} is not
     * extracted, so this reaches no AST component.
     */
    @Retention(java.lang.annotation.RetentionPolicy.SOURCE)
    @Target({})
    @interface StreamMetadata {
        /** Metadata key.
         * @return the key */
        String key();

        /** Metadata value.
         * @return the value */
        String value();
    }
}


