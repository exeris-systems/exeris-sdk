package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * AST facet for a {@code @Channel}-bearing entity — a duplex connection its clients
 * may write to as well as read from. Carried by {@link DomainMetadata#channel()}.
 *
 * <p><strong>The record's presence is the declaration.</strong> Both components are
 * optional, so an entity that declares a channel and says nothing further is
 * {@code ChannelMetadata(null, null)}, which serialises to an empty object rather
 * than to nothing. That is deliberate and is the whole reason this is a record and
 * not a boolean beside {@link DomainMetadata#realTimeApi()}: absent means no channel,
 * present-and-empty means a channel with no further declaration, and a flag cannot
 * express the second without the first collapsing into it.
 *
 * <p>This record is class-level {@code NON_NULL} — the posture every small facet
 * record in this package uses ({@link DerivedMetadata}, {@link ActionParamMetadata},
 * {@link ScheduleMetadata}); the {@code NON_DEFAULT} on the larger records is the
 * exception. Here it also carries the meaning above: an unset component is omitted,
 * so {@code {}} is a well-formed channel rather than a defective one.
 *
 * <p><strong>What this facet does not carry, and why it is not an oversight.</strong>
 * No message-size limit (an operational limit with a kernel configuration path,
 * ADR-071), no origin allowlist (a deployment's security posture, fail-closed in the
 * kernel before any handler runs), no frame-format choice (the kernel's application
 * surface is text only), and nothing about reconnection (a connection's identity does
 * not survive one, by kernel decision). Each is stated on the annotation.
 *
 * <p><strong>Reserved surface (ADR-072).</strong> No {@code exeris-tooling} processor
 * populates this record and no generator consumes it; the kernel holds
 * {@code …spi.websocket} at tier {@code preview}, so the shape is excluded from the
 * 1.0.0 freeze and a 1.x minor may still change it. That {@code preview} is
 * benchmark-gated rather than shape-gated (kernel ADR-084 §10): the contract is
 * settled and what waits on evidence is durability under load, which is why a
 * design-time carrier can be shaped against it now.
 *
 * @param messageType the name of the message type exchanged on the channel, or {@code null} if
 *        the author named none. The kernel is opaque to payloads; this exists for a generator
 *        emitting a typed client, as {@link ActionMetadata#streamEventType()} does
 *
 * @param subprotocol the negotiated WebSocket subprotocol, or {@code null} if none. Unlike
 *        {@code messageType} this one has a kernel landing site — the handshake callback may
 *        name the accepted subprotocol
 *
 * @since 0.12
 * @see DomainMetadata#channel()
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChannelMetadata(
        String messageType,
        String subprotocol
) {

    /**
     * Compact constructor; applies this record's normalization rules.
     *
     * <p>Blank normalises to {@code null} so that "" and absent do not become two
     * spellings of one state on the wire — the treatment {@code streamEventType}
     * already receives, and the reason a reader can test these with {@code != null}
     * rather than with a blank check of its own.
     */
    public ChannelMetadata {
        messageType = blankToNull(messageType);
        subprotocol = blankToNull(subprotocol);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /**
     * A channel that declares nothing beyond its own existence.
     *
     * @return the {@code ChannelMetadata}
     */
    public static ChannelMetadata declared() {
        return new ChannelMetadata(null, null);
    }

    /**
     * A channel carrying a named message type.
     *
     * @param messageType the {@code messageType} the result carries
     * @return the {@code ChannelMetadata}
     */
    public static ChannelMetadata of(String messageType) {
        return new ChannelMetadata(messageType, null);
    }

    /**
     * Whether a message type was declared.
     *
     * @return the {@code boolean}
     */
    @JsonIgnore
    public boolean hasMessageType() {
        return messageType != null;
    }

    /**
     * Whether a subprotocol was declared.
     *
     * @return the {@code boolean}
     */
    @JsonIgnore
    public boolean hasSubprotocol() {
        return subprotocol != null;
    }
}
