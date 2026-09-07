package eu.exeris.sdk.composition.runtime;

/**
 * Thrown when the {@link CompositionConductor} fails to boot the composition (ADR-024 obligation
 * 8a′) — hook discovery failed, or a cap's {@code initialize()} / {@code ready()} threw. By the time
 * this propagates, the conductor has already unwound the touched caps (drain within the budget, then
 * unconditional terminate), so the SKU entrypoint can simply let it abort startup.
 *
 * <p>Deliberately <b>not</b> a {@link CompositionStampException} subtype: a stamp failure means
 * "this is not the composition that was validated" (refuse before any effect), while a boot failure
 * means "the validated composition failed to start" (effects happened and were unwound). Callers that
 * need to tell them apart can.
 *
 * @since 0.9
 */
public final class CompositionBootException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** The capability whose boot failed. */
    private final String capName;

    /** The lifecycle phase it failed in. */
    private final String phase;

    /**
     * Creates a boot failure naming the capability and the phase it failed in.
     *
     * @param message what went wrong
     * @param capName the capability that failed
     * @param phase   the lifecycle phase it failed in
     */
    public CompositionBootException(String message, String capName, String phase) {
        super(message);
        this.capName = capName;
        this.phase = phase;
    }

    /**
     * The same, carrying the failure that caused it.
     *
     * @param message what went wrong
     * @param capName the capability that failed
     * @param phase   the lifecycle phase it failed in
     * @param cause   the underlying failure
     */
    public CompositionBootException(String message, String capName, String phase, Throwable cause) {
        super(message, cause);
        this.capName = capName;
        this.phase = phase;
    }

    /**
     * The qualified name of the cap whose phase failed, or {@code null} for a failure not
     * attributable to a single cap (e.g. a hook-bearing manifest with no {@code initOrder},
     * detected before the discovery loop).
     *
     * @return the capability that failed
     */
    public String capName() {
        return capName;
    }

    /**
     * The failed phase: {@code "discover"}, {@code "initialize"} or {@code "ready"}.
     *
     * @return the lifecycle phase it failed in
     */
    public String phase() {
        return phase;
    }
}
