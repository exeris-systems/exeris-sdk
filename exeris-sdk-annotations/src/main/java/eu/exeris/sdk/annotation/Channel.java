package eu.exeris.sdk.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that an {@link ExerisDomain} entity exposes a <strong>duplex</strong>
 * channel — a persistent connection on which the client may also speak, not only
 * listen.
 *
 * <p>This is the Entity-First expression of the kernel's WebSocket seam (kernel
 * ADR-084, {@code eu.exeris.kernel.spi.websocket}). It is the one thing the SDK's
 * existing streaming surface cannot say. {@link ExerisDomain#realTimeApi()} and
 * {@link Action#streaming()} both describe server push over SSE, which is
 * one-directional <em>by construction</em>: a client subscribed to an SSE stream has
 * no channel back. An entity whose clients edit it concurrently — each one both
 * sending its own changes and receiving everyone else's — has no way to declare that
 * shape, and the difference is structural rather than a matter of degree.
 *
 * <h2>Basic usage</h2>
 * <pre>{@code
 * @ExerisDomain(name = "Article", dataScope = DataScope.TENANT)
 * @Channel(messageType = "ArticleEdit")
 * public class Article {
 *     @Field(required = true) private String title;
 *     @Field private String body;
 * }
 * }</pre>
 *
 * <h2>Entity-level, and deliberately not per action</h2>
 * <p>The channel belongs to the entity, the way {@code realTimeApi} does. A per-action
 * duplex channel would describe a command that talks back while it runs, and that
 * shape already has a declaration site in {@link Action#realTimeUpdates()} —
 * subscribe-to-progress — which is itself reserved and awaiting a generator. Adding a
 * second spelling of the same idea before the first one has a consumer is how a
 * surface grows two ways of saying one thing.
 *
 * <h2>What this deliberately does not declare</h2>
 * <ul>
 *   <li><strong>No message-size limit.</strong> {@code WebSocketConfig.maxMessageBytes}
 *       is an operational limit, and kernel ADR-071 makes every operational limit a
 *       configuration path with a chosen default. Declaring it per entity would put a
 *       deployment concern in a design-time annotation — the same reason
 *       {@link Blob} declares no {@code maxSizeBytes}.</li>
 *   <li><strong>No origin allowlist.</strong> A WebSocket handshake is not subject to
 *       CORS, so the kernel filters origins before the handler runs and
 *       <em>refuses by default</em>. That default is what makes forgetting safe, and a
 *       per-entity attribute could only widen it — quietly, in a place nobody
 *       re-reads. Admission is {@link RouteAccess}'s question; which origins may open a
 *       socket is the deployment's.</li>
 *   <li><strong>No binary frames.</strong> The kernel's application surface is text
 *       only; a binary frame is a protocol error the connection closes on, not a
 *       payload a handler is offered. There is nothing here to choose.</li>
 *   <li><strong>Nothing about reconnection.</strong> A connection's identity does not
 *       survive a reconnect, by kernel decision — resumption without buffering
 *       resumes identity rather than the stream, and buffering the gap is the
 *       unbounded on-heap queue ADR-043 forbids. Continuity is the consumer's to
 *       build on the visible handshake.</li>
 * </ul>
 *
 * <h2>Open-Core status — RESERVED, extraction pending tooling</h2>
 * <p>Declared shape, not yet a socket. The kernel side demonstrably exists —
 * {@code WebSocketProvider} / {@code WebSocketExchange} / {@code WebSocketSession}
 * shipped in kernel v0.12.0 with {@code AbstractWebSocketExchangeTck}, an RFC 6455
 * codec in Core and a Community transport binding — but no {@code exeris-tooling}
 * processor extracts {@code @Channel}, no generator opens an endpoint from it, and
 * the {@code exeris-sdk-source-model-io} reader does not read it, so declaring it
 * today has no generated effect.
 *
 * <p>The kernel holds {@code …spi.websocket} at tier {@code preview}, so this surface
 * is <strong>excluded from the 1.0.0 freeze</strong> and a 1.x minor may still change
 * it. Worth reading precisely: that {@code preview} is <em>benchmark-gated, not
 * shape-gated</em>. Kernel ADR-084 §10 states the contract is settled and that
 * promotion waits on evidence a TCK structurally cannot supply — concurrent
 * connection count, frame throughput, backpressure under a slow reader, teardown of a
 * dead peer. What is unproven is durability under load, not what the surface looks
 * like, which is why a design-time carrier can be shaped against it now.
 *
 * <p>The consumer is named rather than hoped for: {@code exeris-platform}'s Studio
 * and CMS wiring. The CMS content types are {@code @ExerisDomain} declarations today
 * (see {@code studio/docs/cms-strategy.md} §4) and the migration that gives them
 * real-time editing is the one this channel describes.
 *
 * @since 0.12.0
 * @see ExerisDomain#realTimeApi()
 * @see Action#streaming()
 * @see RouteAccess
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface Channel {

    /**
     * The name of the message type exchanged on this channel, e.g.
     * {@code "ArticleEdit"}.
     *
     * <p>The kernel has no opinion here — its application surface sends and receives
     * {@code String}, and a frame's payload is opaque to it. This exists for the same
     * reason {@link Action#streamEventType()} does: a generator emitting a typed
     * client needs a name for what crosses the wire, and deriving one from the entity
     * would make every channel carry a type nobody chose.
     *
     * <p>Empty means the author named no type.
     *
     * @return the message type name, or empty if none was declared
     */
    String messageType() default "";

    /**
     * The WebSocket subprotocol this channel speaks, e.g. {@code "exeris.edit.v1"}.
     *
     * <p>Unlike {@link #messageType()} the kernel does have an opinion about this one:
     * the handshake callback accepts a connection and may name the negotiated
     * subprotocol (kernel ADR-084 §6), so a declared value has somewhere real to land.
     *
     * <p>Empty means none is negotiated, which is the ordinary case.
     *
     * @return the subprotocol name, or empty if none is negotiated
     */
    String subprotocol() default "";
}
