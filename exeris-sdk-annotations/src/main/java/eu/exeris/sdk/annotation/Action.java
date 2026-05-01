package eu.exeris.sdk.annotation;


import java.lang.annotation.*;

/**
 * Exposes domain methods as API endpoints with corresponding UI actions.
 * <p>This annotation allows you to expose business logic methods from your
 * domain entities as REST API endpoints and automatically generate UI buttons
 * to trigger these actions.
 *
 * <h2>Basic Usage:</h2>
 * <pre>{@code
 * @ExerisDomain(module = "sales", aggregate = "Order")
 * public class Order {
 *
 *     @Action(
 *         name = "approve",
 *         label = "Approve Order",
 *         httpMethod = "POST",
 *         path = "/{id}/approve",
 *         roles = {"ROLE_MANAGER", "ROLE_ADMIN"}
 *     )
 *     public void approve() {
 *         if (this.status != OrderStatus.PENDING) {
 *             throw new IllegalStateException("Only pending orders can be approved");
 *         }
 *         this.status = OrderStatus.APPROVED;
 *         this.approvedAt = LocalDateTime.now();
 *     }
 * }
 * }</pre>
 *
 * <h2>With Parameters:</h2>
 * <pre>{@code
 * @Action(
 *     name = "reject",
 *     label = "Reject Order",
 *     httpMethod = "POST",
 *     path = "/{id}/reject",
 *     roles = {"ROLE_MANAGER", "ROLE_ADMIN"},
 *     confirmationMessage = "Are you sure you want to reject this order?"
 * )
 * public void reject(
 *     @ActionParam(label = "Reason", required = true) String reason,
 *     @ActionParam(label = "Notify Customer", defaultValue = "true") boolean notifyCustomer
 * ) {
 *     this.status = OrderStatus.REJECTED;
 *     this.rejectionReason = reason;
 *     if (notifyCustomer) {
 *         sendRejectionEmail();
 *     }
 * }
 * }</pre>
 *
 * <h2>With Return Value:</h2>
 * <pre>{@code
 * @Action(
 *     name = "calculate-total",
 *     label = "Recalculate Total",
 *     httpMethod = "GET",
 *     path = "/{id}/calculate-total",
 *     showResultMessage = true
 * )
 * public BigDecimal calculateTotal() {
 *     BigDecimal total = items.stream()
 *         .map(OrderItem::getSubtotal)
 *         .reduce(BigDecimal.ZERO, BigDecimal::add);
 *     this.totalAmount = total;
 *     return total;
 * }
 * }</pre>
 *
 * <h2>Bulk Action (Multiple Entities):</h2>
 * <pre>{@code
 * @Action(
 *     name = "bulk-approve",
 *     label = "Approve Selected",
 *     httpMethod = "POST",
 *     path = "/bulk-approve",
 *     bulk = true,
 *     roles = {"ROLE_MANAGER"},
 *     confirmationMessage = "Approve {count} selected orders?",
 *     ui = @UI(
 *         icon = "check_circle",
 *         cssClass = "btn-success"
 *     )
 * )
 * public static List<Order> bulkApprove(List<UUID> orderIds) {
 *     // Approve multiple orders
 *     return orderRepository.findAllById(orderIds).stream()
 *         .peek(Order::approve)
 *         .collect(Collectors.toList());
 * }
 * }</pre>
 *
 * <h2>Conditional Action (Show/Hide Based on State):</h2>
 * <pre>{@code
 * @Action(
 *     name = "cancel",
 *     label = "Cancel Order",
 *     httpMethod = "POST",
 *     path = "/{id}/cancel",
 *     condition = "status == 'PENDING' || status == 'APPROVED'",
 *     roles = {"ROLE_USER"},
 *     ui = @UI(
 *         icon = "cancel",
 *         cssClass = "btn-danger"
 *     )
 * )
 * public void cancel() {
 *     if (!canBeCancelled()) {
 *         throw new IllegalStateException("Order cannot be cancelled");
 *     }
 *     this.status = OrderStatus.CANCELLED;
 * }
 * }</pre>
 *
 * <h2>Generated Code:</h2>
 *
 * <p><strong>REST Controller Endpoint:</strong>
 * <pre>{@code
 * @PostMapping("/{id}/approve")
 * @PreAuthorize("hasAnyRole('ROLE_MANAGER', 'ROLE_ADMIN')")
 * public ResponseEntity<OrderResponse> approve(@PathVariable UUID id) {
 *     Order order = orderService.findById(id)
 *         .orElseThrow(() -> new NotFoundException("Order not found"));
 *     order.approve();
 *     orderRepository.save(order);
 *     return ResponseEntity.ok(mapper.toResponse(order));
 * }
 * }</pre>
 *
 * <p><strong>Angular UI Button:</strong>
 * <pre>{@code
 * <button
 *   mat-raised-button
 *   color="primary"
 *   (click)="approve(order.id)"
 *   *ngIf="hasRole(['ROLE_MANAGER', 'ROLE_ADMIN'])">
 *   <mat-icon>check_circle</mat-icon>
 *   Approve Order
 * </button>
 * }</pre>
 *
 * @author Exeris platform Team
 * @version 1.0.0
 * @since 1.0.0
 * @see Field
 * @see UI
 * @see ExerisDomain
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SuppressWarnings("PMD.ShortMethodName")
public @interface Action {

    /**
     * Action name (used in URL path and method naming).
     * <p>Should be lowercase, kebab-case for REST endpoints.
     *
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>"approve"</li>
     *   <li>"reject"</li>
     *   <li>"send-email"</li>
     *   <li>"calculate-total"</li>
     * </ul>
     *
     * @return action name
     */
    String name();

    /**
     * Display label for UI button.
     * <p>Human-readable text shown on buttons and in menus.
     *
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>"Approve Order"</li>
     *   <li>"Send Email"</li>
     *   <li>"Mark as Paid"</li>
     *   <li>"Download Invoice"</li>
     * </ul>
     *
     * @return button label
     */
    String label();

    /**
     * HTTP method for the endpoint.
     * <p>Choose based on action semantics:
     * <ul>
     *   <li><strong>GET</strong> - Read-only operations, calculations</li>
     *   <li><strong>POST</strong> - State-changing operations (most actions)</li>
     *   <li><strong>PUT</strong> - Full updates</li>
     *   <li><strong>PATCH</strong> - Partial updates</li>
     *   <li><strong>DELETE</strong> - Deletion operations</li>
     * </ul>
     *
     * @return HTTP method
     */
    String httpMethod() default "POST";

    /**
     * URL path for the endpoint.
     * <p>Path can include placeholders:
     * <ul>
     *   <li>{@code {id}} - Entity ID</li>
     *   <li>{@code {otherId}} - Other path variables</li>
     * </ul>
     *
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>{@code "/{id}/approve"}</li>
     *   <li>{@code "/{id}/send-email"}</li>
     *   <li>{@code "/bulk-approve"} (for bulk actions)</li>
     * </ul>
     *
     * @return URL path
     */
    String path();

    /**
     * Required roles to execute this action.
     * <p>User must have at least one of these roles.
     *
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>{@code {"ROLE_ADMIN"}}</li>
     *   <li>{@code {"ROLE_MANAGER", "ROLE_ADMIN"}}</li>
     *   <li>{@code {"ROLE_USER"}}</li>
     * </ul>
     *
     * <p>If empty, action is accessible to all authenticated users.
     *
     * @return required role names
     */
    String[] roles() default {};

    /**
     * Permissions required to execute this action.
     * <p>More fine-grained than roles, supports permission-based access control.
     *
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>{@code {"order:approve"}}</li>
     *   <li>{@code {"order:delete"}}</li>
     *   <li>{@code {"invoice:send"}}</li>
     * </ul>
     *
     * @return required permission names
     */
    String[] permissions() default {};

    /**
     * Whether this is a bulk action (operates on multiple entities).
     * <p>When {@code true}:
     * <ul>
     *   <li>Method must be static</li>
     *   <li>Method receives list of entity IDs</li>
     *   <li>UI shows checkbox selection</li>
     *   <li>Button appears above table</li>
     * </ul>
     *
     * @return true for bulk actions
     */
    boolean bulk() default false;

    /**
     * Condition expression for showing/enabling the action.
     * <p>Uses SpEL (Spring Expression Language) syntax.
     * Evaluated against entity state.
     *
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>{@code "status == 'PENDING'"}</li>
     *   <li>{@code "amount > 0 && paid == false"}</li>
     *   <li>{@code "createdAt < T(java.time.LocalDateTime).now().minusDays(7)"}</li>
     * </ul>
     *
     * @return condition expression
     */
    String condition() default "";

    /**
     * Confirmation message to show before executing action.
     * <p>If specified, shows a confirmation dialog.
     * Can include placeholders for entity fields.
     *
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>"Are you sure you want to delete this order?"</li>
     *   <li>"Approve order #{orderNumber}?"</li>
     *   <li>"Send email to {customerEmail}?"</li>
     * </ul>
     *
     * @return confirmation message
     */
    String confirmationMessage() default "";

    /**
     * Success message to show after action completes.
     * <p>Displayed as toast notification.
     *
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>"Order approved successfully"</li>
     *   <li>"Email sent to {customerEmail}"</li>
     *   <li>"{count} orders approved"</li>
     * </ul>
     *
     * @return success message
     */
    String successMessage() default "";

    /**
     * Error message to show if action fails.
     * <p>Displayed as error toast notification.
     *
     * @return error message
     */
    String errorMessage() default "Action failed";

    /**
     * Whether to show result value in a message.
     * <p>When {@code true} and action returns a value,
     * displays the result in a toast notification.
     *
     * @return true to show result
     */
    boolean showResultMessage() default false;

    /**
     * Whether to refresh the view after action completes.
     * <p>When {@code true}:
     * <ul>
     *   <li>Detail view: Reloads entity data</li>
     *   <li>List view: Refreshes table data</li>
     * </ul>
     *
     * @return true to refresh
     */
    boolean refreshAfter() default true;

    /**
     * Whether to close the detail view after action completes.
     * <p>Useful for actions that delete or finalize entities.
     *
     * @return true to close view
     */
    boolean closeAfter() default false;

    /**
     * URL to redirect to after action completes.
     * <p>Can include placeholders:
     * <ul>
     *   <li>{@code {id}} - Entity ID</li>
     *   <li>{@code {result}} - Action result</li>
     * </ul>
     *
     * <p><strong>Example:</strong>
     * {@code "/orders/{id}/invoice"}
     *
     * @return redirect URL
     */
    String redirectTo() default "";

    /**
     * UI configuration for the action button.
     * <p>Specifies icon, styling, and placement.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * ui = @UI(
     *     icon = "check_circle",
     *     cssClass = "btn-success"
     * )
     * }</pre>
     *
     * @return UI configuration
     */
    UI ui() default @UI;


    /**
     * Display order for the button (lower numbers appear first).
     *
     * @return display order
     */
    int order() default 100;

    /**
     * Whether this action is async (long-running).
     * <p>When {@code true}:
     * <ul>
     *   <li>Action executes in background</li>
     *   <li>Shows progress indicator</li>
     *   <li>User can continue working</li>
     *   <li>Notification when complete</li>
     * </ul>
     *
     * @return true for async execution
     */
    boolean async() default false;

    /**
     * Timeout in seconds for async actions.
     * <p>If action doesn't complete within timeout,
     * shows timeout error.
     *
     * @return timeout in seconds
     */
    int timeout() default 300;

    /**
     * Whether to show progress bar for async actions.
     *
     * @return true to show progress
     */
    boolean showProgress() default false;

    /**
     * Description of what this action does.
     * <p>Displayed as tooltip on hover.
     *
     * @return action description
     */
    String description() default "";

    /**
     * Alias for {@link #confirmationMessage()}.
     * <p>Provided for compatibility with task specification.
     *
     * @return confirmation message
     */
    String confirm() default "";

    /**
     * Visual style variant for the button.
     * <p>Determines button color and emphasis level.
     *
     * @return button variant
     */
    Variant variant() default Variant.PRIMARY;

    /**
     * <p>Provided for compatibility with task specification.
     * Location determines where the action button appears.
     *
     * @return button location
     */
    Location location() default Location.DETAIL;

    /**
     * Alias for {@link #condition()}.
     * <p>Provided for compatibility with task specification.
     * Expression that determines when action is visible.
     *
     * @return visibility condition
     */
    String visibleWhen() default "";

    /**
     * Alias for {@link #httpMethod()}.
     * <p>Provided for compatibility with task specification.
     *
     * @return HTTP method
     */
    String method() default "";

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // STREAMING & REAL-TIME
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Whether this action returns a streaming response.
     * <p>When {@code true}:
     * <ul>
     *   <li>Response is streamed using Server-Sent Events or WebTransport</li>
     *   <li>Client receives progress updates during execution</li>
     *   <li>Useful for long-running operations (reports, exports, batch processing)</li>
     * </ul>
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * @Action(
     *     name = "generate-report",
     *     streaming = true,
     *     streamEventType = "ReportProgress"
     * )
     * public Flux<ReportProgress> generateReport() { ...  }
     * }</pre>
     *
     * @return true for streaming response
     */
    boolean streaming() default false;

    /**
     * Event type name for streaming responses.
     * <p>Used in SSE event type or WebTransport message type.
     *
     * @return stream event type name
     */
    String streamEventType() default "";

    /**
     * Whether this action supports real-time subscription.
     * <p>When {@code true}:
     * <ul>
     *   <li>Clients can subscribe to action progress via WebTransport/SSE</li>
     *   <li>Action publishes progress events during execution</li>
     *   <li>Useful for tracking async operation status</li>
     * </ul>
     *
     * @return true for real-time subscription support
     */
    boolean realTimeUpdates() default false;

    /**
     * Maximum concurrent executions of this action per entity.
     * <p>Prevents duplicate execution of same action on same entity.
     * 0 = unlimited.
     *
     * @return max concurrent executions
     */
    int maxConcurrent() default 1;

    /**
     * Debounce time for rapid action invocations (ISO-8601 duration).
     * <p>Prevents rapid repeated calls within debounce window.
     *
     * <p><strong>Example:</strong> "PT1S" - 1 second debounce
     *
     * @return debounce duration
     */
    String debounce() default "";

    /**
     * Button variant enum for visual styling.
     */
    enum Variant {
        /** Primary action (main CTA) - typically blue/brand color */
        PRIMARY,

        /** Secondary action - typically gray/neutral */
        SECONDARY,

        /** Success action - typically green (approve, confirm) */
        SUCCESS,

        /** Warning action - typically orange/yellow (needs attention) */
        WARNING,

        /** Danger action - typically red (delete, reject) */
        DANGER,

        /** Ghost/text action - minimal styling, low emphasis */
        GHOST
    }

    /**
     * Button location enum (matches task specification).
     */
    enum Location {
        /** Show in detail page */
        DETAIL,

        /** Show in list view for each row */
        LIST_ROW,

        /** Show for bulk operations (multiple selected rows) */
        LIST_BULK,

        /** Show in list header/toolbar */
        LIST_HEADER
    }

}


