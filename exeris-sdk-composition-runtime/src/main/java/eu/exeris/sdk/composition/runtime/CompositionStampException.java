package eu.exeris.sdk.composition.runtime;

/**
 * Thrown when the boot-time composition stamp assertion fails (ADR-024 obligation 8). The SKU
 * bootstrap call-site lets this propagate so startup aborts <em>before any cap enters
 * {@code initialize}</em>, with a message naming the specific divergence (missing/malformed stamp,
 * schema handshake, version drift, or binding mismatch) — an early, legible refusal instead of a
 * confusing mid-boot crash.
 *
 * @since 0.8
 */
public final class CompositionStampException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a stamp mismatch.
     *
     * @param message what disagreed
     */
    public CompositionStampException(String message) {
        super(message);
    }

    /**
     * The same, carrying the failure that caused it.
     *
     * @param message what disagreed
     * @param cause   the underlying failure
     */
    public CompositionStampException(String message, Throwable cause) {
        super(message, cause);
    }
}
