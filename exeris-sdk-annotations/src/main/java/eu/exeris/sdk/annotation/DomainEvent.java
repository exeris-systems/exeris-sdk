package eu.exeris.sdk.annotation;

import java.lang.annotation.*;

/**
 * Marks a record/class as a domain event.
 *
 * <p>Domain events represent something that happened in the domain.
 * They are immutable facts that are published to messaging infrastructure
 * and can be consumed by projections, sagas, and external systems.
 *
 * <h2>Basic Usage (Entity Lifecycle):</h2>
 * <pre>{@code
 * @DomainEvent(
 *     trigger = Trigger.CREATE,
 *     topic = "orders.created",
 *     partitionKey = "customerId"
 * )
 * public record OrderCreatedEvent(
 *     UUID eventId,
 *     Instant occurredAt,
 *     UUID aggregateId,
 *     UUID customerId,
 *     BigDecimal totalAmount
 * ) implements DomainEvent {}
 * }</pre>
 *
 * <h2>Action-Triggered Event:</h2>
 * <pre>{@code
 * @DomainEvent(
 *     trigger = Trigger.ACTION,
 *     action = "approve",
 *     topic = "orders.approved",
 *     partitionKey = "aggregateId",
 *     includePreviousValues = true
 * )
 * public record OrderApprovedEvent(
 *     UUID eventId,
 *     Instant occurredAt,
 *     UUID aggregateId,
 *     UUID approvedBy,
 *     OrderStatus previousStatus,
 *     OrderStatus newStatus
 * ) implements DomainEvent {}
 * }</pre>
 *
 * <h2>Field Change Event:</h2>
 * <pre>{@code
 * @DomainEvent(
 *     trigger = Trigger.FIELD_CHANGED,
 *     field = "status",
 *     topic = "orders.status-changed",
 *     includePreviousValues = true
 * )
 * public record OrderStatusChangedEvent(...) implements DomainEvent {}
 * }</pre>
 *
 * <h2>Conditional Event:</h2>
 * <pre>{@code
 * @DomainEvent(
 *     trigger = Trigger.UPDATE,
 *     topic = "orders.high-value-updated",
 *     condition = "entity.totalAmount > 10000",
 *     priority = Priority.HIGH
 * )
 * public record HighValueOrderUpdatedEvent(...) implements DomainEvent {}
 * }</pre>
 *
 * @author Exeris SDK Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Documented
@Repeatable(DomainEvent.DomainEvents.class)
@SuppressWarnings({"PMD.ExcessivePublicCount", "PMD.TooManyMethods"})
public @interface DomainEvent {

    // ═══════════════════════════════════════════════════════════════════════════
    // IDENTITY
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Event name/type identifier.
     * <p>Defaults to class name without "Event" suffix.
     * Used in event type headers, schema registry, and routing.
     *
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>"OrderCreated"</li>
     *   <li>"PaymentProcessed"</li>
     *   <li>"SubscriptionRenewed"</li>
     * </ul>
     *
     * @return event name
     */
    String name() default "";

    /**
     * Event version for schema evolution.
     * <p>Increment when making breaking changes to event structure.
     * Used for event upcasting and schema compatibility checks.
     *
     * @return event version (default: 1)
     */
    int version() default 1;

    /**
     * Human-readable description of the event.
     * <p>Used in documentation and AsyncAPI specs.
     *
     * @return event description
     */
    String description() default "";

    /**
     * Tags for categorization and filtering.
     * <p>Used in documentation and monitoring.
     *
     * @return array of tags
     */
    String[] tags() default {};

    // ═══════════════════════════════════════════════════════════════════════════
    // TRIGGER CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * What triggers this event.
     * <p>Determines when the event is automatically published.
     *
     * @return trigger type
     */
    Trigger trigger();

    /**
     * Action name that triggers this event.
     * <p>Required when {@code trigger = Trigger.ACTION}.
     *
     * <p><strong>Example:</strong> "approve", "cancel", "ship"
     *
     * @return action name
     */
    String action() default "";

    /**
     * Field name that triggers this event when changed.
     * <p>Required when {@code trigger = Trigger.FIELD_CHANGED}.
     *
     * <p><strong>Example:</strong> "status", "price", "assignee"
     *
     * @return field name
     */
    String field() default "";

    /**
     * State transition that triggers this event.
     * <p>Format: "FROM_STATE -> TO_STATE" or "* -> TO_STATE" or "FROM_STATE -> *"
     * <p>Required when {@code trigger = Trigger.STATE_TRANSITION}.
     *
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>"PENDING -> APPROVED"</li>
     *   <li>"* -> CANCELLED" (any state to cancelled)</li>
     *   <li>"DRAFT -> *" (draft to any state)</li>
     * </ul>
     *
     * @return state transition pattern
     */
    String stateTransition() default "";

    /**
     * Condition expression for publishing event (SpEL).
     * <p>Event is only published if condition evaluates to true.
     *
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>"entity.amount > 1000"</li>
     *   <li>"entity.priority == 'HIGH'"</li>
     *   <li>"entity.customer.vip == true"</li>
     * </ul>
     *
     * @return condition expression
     */
    String condition() default "";

    // ═══════════════════════════════════════════════════════════════════════════
    // MESSAGING CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Topic/queue name for publishing.
     * <p>Kafka topic, RabbitMQ queue, or SQS queue name.
     *
     * <p><strong>Naming conventions:</strong>
     * <ul>
     *   <li>Kafka: "domain.aggregate.event" (e.g., "billing.subscriptions.renewed")</li>
     *   <li>RabbitMQ: "domain.aggregate.event" or use exchange + routing key</li>
     * </ul>
     *
     * <p><strong>Open-Core status (ADR-050):</strong> {@code topic} is a
     * binding-agnostic routing target. The generator lands it on the per-type
     * {@code EventTypeSpec} (the kernel registration record) — not on the
     * per-instance event descriptor, which stays primitive-only. Broker bindings
     * (e.g. Kafka) map it to the concrete broker topic on publish and subscribe;
     * the Open-Core in-memory event bus treats it as advisory and routes by
     * event-type ordinal only (topic-blind by design). A blank {@code topic}
     * means "no override" — the binding falls back to its default routing.
     *
     * @return topic name
     */
    String topic();

    /**
     * Partition key field for message ordering.
     * <p>Messages with same partition key are processed in order.
     * Must be a field name in the event payload.
     *
     * <p><strong>Common patterns:</strong>
     * <ul>
     *   <li>"aggregateId" - Order by aggregate</li>
     *   <li>"tenantId" - Order by tenant</li>
     *   <li>"customerId" - Order by customer</li>
     * </ul>
     *
     * @return partition key field name
     */
    String partitionKey() default "aggregateId";

    /**
     * Exchange name (for RabbitMQ).
     * <p>If specified, message is published to exchange with routing key.
     *
     * @return exchange name
     */
    String exchange() default "";

    /**
     * Routing key (for RabbitMQ).
     * <p>Used with exchange for topic-based routing.
     * Supports placeholders: {aggregateId}, {tenantId}, {eventType}
     *
     * <p><strong>Example:</strong> "orders.{status}.changed"
     *
     * @return routing key pattern
     */
    String routingKey() default "";

    /**
     * Event priority.
     * <p>Affects processing order and resource allocation.
     *
     * @return event priority
     */
    Priority priority() default Priority.NORMAL;

    /**
     * Message Time-To-Live in milliseconds.
     * <p>Message expires after TTL if not consumed.
     * Set to 0 for no expiration.
     *
     * @return TTL in milliseconds
     */
    long ttlMs() default 0;

    /**
     * Whether message delivery is mandatory.
     * <p>When true, error if message cannot be routed to any queue.
     *
     * @return true for mandatory delivery
     */
    boolean mandatory() default false;

    /**
     * Whether to persist message to disk.
     * <p>Ensures message survives broker restart.
     *
     * @return true for persistent messages
     */
    boolean persistent() default true;

    // ═══════════════════════════════════════════════════════════════════════════
    // PAYLOAD CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Fields to include in event payload.
     * <p>If empty, includes all fields from entity.
     * Specify to limit payload size or hide internal fields.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * includeFields = {"id", "status", "amount", "customerId"}
     * }</pre>
     *
     * @return array of field names to include
     */
    String[] includeFields() default {};

    /**
     * Fields to exclude from event payload.
     * <p>Applied after includeFields.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * excludeFields = {"password", "internalNotes", "auditLog"}
     * }</pre>
     *
     * @return array of field names to exclude
     */
    String[] excludeFields() default {};

    /**
     * Include complete aggregate state snapshot.
     * <p>When true, entire aggregate state is included in event.
     * Useful for consumers that need full context.
     *
     * @return true to include snapshot
     */
    boolean includeSnapshot() default false;

    /**
     * Include previous values for changed fields.
     * <p>Adds "previous" object with old values.
     * Useful for auditing and change tracking.
     *
     * @return true to include previous values
     */
    boolean includePreviousValues() default false;

    /**
     * Include computed/derived fields.
     * <p>List of computed fields to calculate and include.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * includeComputed = {"totalWithTax", "formattedAddress", "fullName"}
     * }</pre>
     *
     * @return array of computed field names
     */
    String[] includeComputed() default {};

    /**
     * Fields containing sensitive data to mask.
     * <p>Values are masked/hashed before publishing.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * sensitiveFields = {"email", "phone", "ssn", "creditCard"}
     * }</pre>
     *
     * @return array of sensitive field names
     */
    String[] sensitiveFields() default {};

    /**
     * Fields to encrypt in the payload.
     * <p>Values are encrypted using configured encryption key.
     *
     * @return array of field names to encrypt
     */
    String[] encryptFields() default {};

    /**
     * Custom payload transformer class.
     * <p>Must implement {@code EventPayloadTransformer<E, P>}.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * @Component
     * public class OrderEventTransformer implements EventPayloadTransformer<Order, OrderEventPayload> {
     *     public OrderEventPayload transform(Order entity, DomainEvent event) {
     *         return new OrderEventPayload(...);
     *     }
     * }
     * }</pre>
     *
     * @return transformer class
     */
    Class<?> payloadTransformer() default void.class;

    /**
     * Maximum payload size in bytes.
     * <p>Event is rejected if payload exceeds limit.
     * Set to 0 for no limit.
     *
     * @return max payload size in bytes
     */
    int maxPayloadSize() default 0;

    // ═══════════════════════════════════════════════════════════════════════════
    // SCHEMA CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Schema format for serialization.
     * <p>Determines how event is serialized for messaging.
     *
     * @return schema format
     */
    SchemaFormat schemaFormat() default SchemaFormat.INHERIT;

    /**
     * Schema namespace (for Avro).
     * <p>Avro namespace for generated schema.
     *
     * <p><strong>Example:</strong> "com.example.billing.event"
     *
     * @return Avro namespace
     */
    String schemaNamespace() default "";

    /**
     * Schema subject name (for Schema Registry).
     * <p>Defaults to "{topic}-value".
     *
     * @return schema subject name
     */
    String schemaSubject() default "";

    /**
     * Schema compatibility mode.
     * <p>Determines allowed schema changes.
     *
     * @return compatibility mode
     */
    SchemaCompatibility compatibility() default SchemaCompatibility.BACKWARD;

    /**
     * Register schema in registry on startup.
     * <p>Automatically registers event schema.
     *
     * @return true to register schema
     */
    boolean registerSchema() default true;

    /**
     * Schema documentation.
     * <p>Description included in generated schema.
     *
     * @return schema documentation
     */
    String schemaDoc() default "";

    // ═══════════════════════════════════════════════════════════════════════════
    // HEADERS & METADATA
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Static headers to include with event.
     * <p>Key-value pairs added to every event message.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * headers = {
     *     @Header(name = "source", value = "billing-service"),
     *     @Header(name = "content-type", value = "application/avro")
     * }
     * }</pre>
     *
     * @return array of headers
     */
    Header[] headers() default {};

    /**
     * Include trace context (correlation ID, span ID, trace ID).
     * <p>Enables distributed tracing across services.
     *
     * @return true to include trace context
     */
    boolean includeTraceContext() default true;

    /**
     * Include tenant context (tenant ID, tenant name).
     * <p>Required for multi-tenant systems.
     *
     * @return true to include tenant context
     */
    boolean includeTenantContext() default true;

    /**
     * Include user context (user ID, username, roles).
     * <p>Identifies who triggered the event.
     *
     * @return true to include user context
     */
    boolean includeUserContext() default true;

    /**
     * Include causation chain (what caused this event).
     * <p>Links events to their causes for debugging.
     *
     * @return true to include causation
     */
    boolean includeCausation() default true;

    /**
     * Include timestamp metadata.
     * <p>Adds occurredAt, publishedAt timestamps.
     *
     * @return true to include timestamps
     */
    boolean includeTimestamps() default true;

    /**
     * Include source metadata (service, instance, version).
     * <p>Identifies the publishing service.
     *
     * @return true to include source
     */
    boolean includeSource() default true;

    // ═══════════════════════════════════════════════════════════════════════════
    // RETENTION & ARCHIVAL
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Topic retention period in days.
     * <p>Events are deleted from topic after this period.
     *
     * @return retention days
     */
    int retentionDays() default 30;

    /**
     * Archive events to cold storage after N days.
     * <p>Events are moved to S3/blob storage after this period.
     * Set to 0 to disable archival.
     *
     * @return archive after days
     */
    int archiveAfterDays() default 0;

    /**
     * Compress events in topic after N days.
     * <p>Enables log compaction for older events.
     *
     * @return compress after days
     */
    int compressAfterDays() default 7;

    /**
     * Store event in event store (for event sourcing).
     * <p>When true, event is persisted in event store.
     *
     * @return true to store in event store
     */
    boolean storeInEventStore() default true;

    // ═══════════════════════════════════════════════════════════════════════════
    // CONSUMER CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Known consumers of this event.
     * <p>Documentation for event consumers.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * consumers = {"notification-service", "analytics-service", "audit-service"}
     * }</pre>
     *
     * @return array of consumer names
     */
    String[] consumers() default {};

    /**
     * Expected processing SLA in milliseconds.
     * <p>Alert if processing exceeds this threshold.
     *
     * @return expected processing time
     */
    long expectedProcessingTimeMs() default 1000;

    /**
     * Whether consumers should process idempotently.
     * <p>Indicates event may be delivered multiple times.
     *
     * @return true if idempotency required
     */
    boolean requiresIdempotency() default true;

    /**
     * Whether message ordering is required.
     * <p>Indicates order of events matters for correctness.
     *
     * @return true if ordering required
     */
    boolean orderingRequired() default true;

    /**
     * Consumer processing guarantees required.
     *
     * @return processing guarantee
     */
    ProcessingGuarantee processingGuarantee() default ProcessingGuarantee.AT_LEAST_ONCE;

    // ═══════════════════════════════════════════════════════════════════════════
    // OUTBOX CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Use transactional outbox pattern.
     * <p>Event is written to outbox table within same transaction.
     *
     * @return true to use outbox
     */
    boolean useOutbox() default true;

    /**
     * Outbox table name (if different from default).
     *
     * @return outbox table name
     */
    String outboxTable() default "";

    /**
     * Outbox aggregation type for deduplication.
     * <p>Multiple events with same key within window are aggregated.
     *
     * @return aggregation type
     */
    OutboxAggregation outboxAggregation() default OutboxAggregation.NONE;

    /**
     * Outbox aggregation window in seconds.
     *
     * @return aggregation window
     */
    int outboxAggregationWindowSeconds() default 0;

    // ═══════════════════════════════════════════════════════════════════════════
    // MONITORING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Emit metrics for this event type.
     * <p>Publishes count, latency, error metrics.
     *
     * @return true to emit metrics
     */
    boolean emitMetrics() default true;

    /**
     * Log event publication at specified level.
     *
     * @return log level
     */
    LogLevel logLevel() default LogLevel.DEBUG;

    /**
     * Alert on publication failure.
     *
     * @return true to alert on failure
     */
    boolean alertOnFailure() default false;

    /**
     * Sample rate for distributed tracing (0.0 - 1.0).
     * <p>1.0 = trace all, 0.1 = trace 10%.
     *
     * @return sample rate
     */
    double traceSampleRate() default 1.0;

    // ═══════════════════════════════════════════════════════════════════════════
    // TESTING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Disable event publication in tests.
     * <p>Event is not published when running tests.
     *
     * @return true to disable in tests
     */
    boolean disableInTests() default false;

    /**
     * Mock consumer for testing.
     * <p>Class that simulates event consumption.
     *
     * @return mock consumer class
     */
    Class<?> mockConsumer() default void.class;

    // ═══════════════════════════════════════════════════════════════════════════
    // ENUMS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Event trigger types.
     */
    enum Trigger {
        /** Published on entity creation */
        CREATE,

        /** Published on entity update (any field change) */
        UPDATE,

        /** Published on entity deletion (including soft delete) */
        DELETE,

        /** Published when specific action is executed */
        ACTION,

        /** Published when specific field changes value */
        FIELD_CHANGED,

        /** Published on state machine transition */
        STATE_TRANSITION,

        /** Published on scheduled interval */
        SCHEDULED,

        /** Published manually via code */
        MANUAL,

        /** Published on aggregate snapshot */
        SNAPSHOT
    }

    /**
     * Event priority levels.
     */
    enum Priority {
        /** Low priority - process when resources available */
        LOW,

        /** Normal priority - standard processing */
        NORMAL,

        /** High priority - process before normal events */
        HIGH,

        /** Critical priority - process immediately */
        CRITICAL
    }

    /**
     * Schema serialization formats.
     */
    enum SchemaFormat {
        /** Inherit from module/global configuration */
        INHERIT,

        /** JSON with JSON Schema */
        JSON,

        /** Apache Avro */
        AVRO,

        /** MessagePack */
        MSGPACK
    }

    /**
     * Schema compatibility modes.
     */
    enum SchemaCompatibility {
        /** No compatibility checking */
        NONE,

        /** New schema can read old data */
        BACKWARD,

        /** Old schema can read new data */
        FORWARD,

        /** Both backward and forward compatible */
        FULL,

        /** Backward compatible with all previous versions */
        BACKWARD_TRANSITIVE,

        /** Forward compatible with all previous versions */
        FORWARD_TRANSITIVE,

        /** Fully compatible with all previous versions */
        FULL_TRANSITIVE
    }

    /**
     * Message processing guarantees.
     */
    enum ProcessingGuarantee {
        /** Message may be lost */
        AT_MOST_ONCE,

        /** Message may be processed multiple times */
        AT_LEAST_ONCE,

        /** Message processed exactly once */
        EXACTLY_ONCE
    }

    /**
     * Outbox aggregation strategies.
     */
    enum OutboxAggregation {
        /** No aggregation */
        NONE,

        /** Keep only last event per key */
        LAST_WINS,

        /** Keep only first event per key */
        FIRST_WINS,

        /** Merge events with same key */
        MERGE
    }

    /**
     * Log levels for event processing.
     */
    enum LogLevel {
        /** Trace-level logging (most verbose) */
        TRACE,
        /** Debug-level logging */
        DEBUG,
        /** Info-level logging */
        INFO,
        /** Warning-level logging */
        WARN,
        /** Error-level logging */
        ERROR,
        /** Logging disabled */
        OFF
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NESTED ANNOTATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Message header definition.
     */
    @interface Header {
        /**
         * Header name.
         * @return the header name
         */
        String name();

        /**
         * Header value (static).
         * @return the static header value
         */
        String value() default "";

        /**
         * Header value expression (SpEL).
         * @return the SpEL expression for the header value
         */
        String expression() default "";
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONTAINER ANNOTATION (for @Repeatable)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Container annotation for multiple {@link DomainEvent} annotations.
     *
     * <p>This allows declaring multiple domain events on a single entity class:
     * <pre>{@code
     * @DomainEvent(trigger = Trigger.CREATE, topic = "orders.created")
     * @DomainEvent(trigger = Trigger.UPDATE, topic = "orders.updated")
     * @DomainEvent(trigger = Trigger.DELETE, topic = "orders.deleted")
     * public class Order { ... }
     * }</pre>
     *
     * @see DomainEvent
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.SOURCE)
    @Documented
    @interface DomainEvents {
        /**
         * Array of domain event annotations.
         * @return array of domain events
         */
        DomainEvent[] value();
    }
}

