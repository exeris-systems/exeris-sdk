package eu.exeris.sdk.annotation;

import java.lang.annotation.*;

/**
 * Marks a method as an event handler in projections or sagas.
 *
 * <p>Event handlers process domain events to update read models,
 * trigger saga steps, or perform other side effects.
 *
 * <h2>In Projection:</h2>
 * <pre>{@code
 * @Projection(name = "order-summary", ...)
 * public class OrderSummaryProjection {
 *
 *     @EventHandler
 *     public void on(OrderCreatedEvent event) {
 *         // Create new read model entry
 *     }
 *
 *     @EventHandler(
 *         order = 1,
 *         idempotent = true,
 *         condition = "event.amount > 1000"
 *     )
 *     public void on(OrderCompletedEvent event) {
 *         // Update read model
 *     }
 * }
 * }</pre>
 *
 * <h2>In Saga:</h2>
 * <pre>{@code
 * @Saga(name = "OrderFulfillmentSaga", ...)
 * public class OrderFulfillmentSaga {
 *
 *     @EventHandler(
 *         triggerStep = "processPayment",
 *         expectedEvents = {"PaymentCompletedEvent"}
 *     )
 *     public SagaAction onPaymentCompleted(PaymentCompletedEvent event) {
 *         return nextStep("shipOrder");
 *     }
 * }
 * }</pre>
 *
 * @author Exeris SDK Team
 * @version 0.1.0
 * @since 0.1.0
 * @see Projection
 * @see Saga
 * @see DomainEvent
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.SOURCE)
@Documented
@Repeatable(EventHandler.EventHandlers.class)
public @interface EventHandler {

    // ═══════════════════════════════════════════════════════════════════════════
    // IDENTITY
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Handler name (defaults to method name).
     *
     * @return handler name
     */
    String name() default "";

    /**
     * Handler description.
     *
     * @return description
     */
    String description() default "";

    // ═══════════════════════════════════════════════════════════════════════════
    // EVENT SELECTION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Single event type this handler processes (convenience for one event).
     * <p>Use when subscribing to a single event type.
     * For multiple events, use {@link #events()}.
     *
     * @return event type name
     */
    String event() default "";

    /**
     * Topic/queue to listen on.
     * <p>Kafka topic, RabbitMQ queue, or message channel name.
     *
     * @return topic name
     */
    String topic() default "";

    /**
     * Event types this handler processes.
     * <p>If empty, inferred from method parameter type.
     *
     * @return event type names
     */
    String[] events() default {};

    /**
     * Event classes this handler processes.
     *
     * @return event classes
     */
    Class<?>[] eventClasses() default {};

    /**
     * Filter expression for events (SpEL).
     * <p>Only process events matching condition.
     *
     * @return filter expression
     */
    String condition() default "";

    /**
     * Event version(s) this handler supports.
     * <p>For handling schema evolution.
     *
     * @return supported versions
     */
    int[] versions() default {};

    // ═══════════════════════════════════════════════════════════════════════════
    // ORDERING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Handler execution order (lower = first).
     * <p>When multiple handlers match same event.
     *
     * @return execution order
     */
    int order() default 100;

    /**
     * Priority (alternative to order).
     *
     * @return priority
     */
    Priority priority() default Priority.NORMAL;

    // ═══════════════════════════════════════════════════════════════════════════
    // EXECUTION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Handler is idempotent (safe to retry).
     *
     * @return true if idempotent
     */
    boolean idempotent() default true;

    /**
     * Async execution.
     *
     * @return true for async
     */
    boolean async() default false;

    /**
     * Timeout for handler execution.
     *
     * @return timeout duration
     */
    String timeout() default "PT30S";

    /**
     * Transaction mode.
     *
     * @return transaction mode
     */
    TransactionMode transactionMode() default TransactionMode.REQUIRED;

    /**
     * Retry policy on failure.
     *
     * @return retry policy
     */
    RetryPolicy retryPolicy() default @RetryPolicy;

    // ═══════════════════════════════════════════════════════════════════════════
    // SAGA-SPECIFIC
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Saga step this handler responds to.
     * <p>Only for saga event handlers.
     *
     * @return step name
     */
    String triggerStep() default "";

    /**
     * Expected events that trigger this handler.
     * <p>For saga step completion.
     *
     * @return expected event names
     */
    String[] expectedEvents() default {};

    /**
     * Failure events that trigger compensation.
     *
     * @return failure event names
     */
    String[] failureEvents() default {};

    // ═══════════════════════════════════════════════════════════════════════════
    // MONITORING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Emit metrics for this handler.
     *
     * @return true to emit
     */
    boolean emitMetrics() default true;

    /**
     * Log level for this handler.
     *
     * @return log level
     */
    LogLevel logLevel() default LogLevel.DEBUG;

    /**
     * Alert on handler failure.
     *
     * @return true to alert
     */
    boolean alertOnFailure() default false;

    // ═══════════════════════════════════════════════════════════════════════════
    // ENUMS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Event handler execution priority.
     */
    enum Priority {
        /** Lowest priority (executes last) */
        LOWEST,
        /** Low priority */
        LOW,
        /** Normal priority (default) */
        NORMAL,
        /** High priority */
        HIGH,
        /** Highest priority (executes first) */
        HIGHEST
    }

    /**
     * Transaction propagation mode for event handlers.
     */
    enum TransactionMode {
        /** Require existing transaction */
        REQUIRED,

        /** Create new transaction */
        REQUIRES_NEW,

        /** No transaction */
        NONE,

        /** Support existing, but don't require */
        SUPPORTS
    }

    /**
     * Log levels for event handler logging.
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
     * Retry policy for failed event processing.
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target({})
    @interface RetryPolicy {
        /** Maximum number of retry attempts
         * @return max attempts */
        int maxAttempts() default 3;

        /** Initial delay between retries (ISO-8601 duration)
         * @return delay duration */
        String delay() default "PT1S";

        /** Backoff multiplier for exponential backoff
         * @return backoff multiplier */
        double backoff() default 2.0;

        /** Maximum delay between retries (ISO-8601 duration)
         * @return max delay */
        String maxDelay() default "PT1M";

        /** Exception types that should trigger a retry
         * @return exception types to retry on */
        Class<? extends Throwable>[] retryOn() default {};

        /** Exception types that should NOT trigger a retry
         * @return exception types to not retry on */
        Class<? extends Throwable>[] noRetryOn() default {};
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONTAINER ANNOTATION (for @Repeatable)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Container annotation for multiple {@link EventHandler} annotations.
     *
     * <p>Allows declaring multiple event handlers on a single class or method:
     * <pre>{@code
     * @EventHandler(event = "OrderCreated", topic = "orders.created")
     * @EventHandler(event = "OrderCancelled", topic = "orders.cancelled")
     * public class OrderProcessor { ... }
     * }</pre>
     *
     * @see EventHandler
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.SOURCE)
    @Documented
    @interface EventHandlers {
        /**
         * Array of event handler annotations.
         * @return array of event handlers
         */
        EventHandler[] value();
    }
}


