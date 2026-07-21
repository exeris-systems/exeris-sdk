package eu.exeris.sdk.composition.lifecycle;

import java.time.Duration;

/**
 * The cap-facing four-phase lifecycle contract (ADR-024 obligation 8a) — implemented by the class a
 * capability marks {@code @CapabilityLifecycle}, and driven by the boot conductor in
 * {@code exeris-sdk-composition-runtime} in the tooling-emitted manifest {@code initOrder} (forward
 * on boot, reverse on shutdown). All four methods default to no-ops: <b>implement the subset you
 * need</b>.
 *
 * <h2>The four phases</h2>
 * <dl>
 *   <dt>{@link #initialize()}</dt>
 *   <dd>Invoked after the kernel reports {@code KERNEL READY} and before the cap serves its first
 *       request — acquire resources, open connections, warm caches. Must be <b>idempotent</b>:
 *       a second {@code initialize()} on the same instance must be replay-safe (the conductor may
 *       re-drive it for validation dry-runs). Throwing aborts boot: the conductor unwinds the
 *       already-initialized prefix (drain, then terminate) and startup fails with a
 *       {@code CompositionBootException}.</dd>
 *
 *   <dt>{@link #ready()}</dt>
 *   <dd>The all-caps barrier: invoked only after <em>every</em> cap in the composition has completed
 *       {@code initialize()}. Cross-cap interaction (calling a service another cap
 *       {@code @Provides}) is safe here, not in {@code initialize()}.</dd>
 *
 *   <dt>{@link #drain(Duration)}</dt>
 *   <dd>Graceful shutdown, phase one: stop accepting new work, finish in-flight work, flush buffers —
 *       within the passed {@code remaining} slice of the <b>composition-wide</b> drain budget
 *       (default 30 seconds, per-SKU configurable). The deadline is cooperative <em>and</em>
 *       conductor-enforced: an implementation should watch {@code remaining} and return in time, but
 *       the conductor also enforces the budget — an overrunning drain is interrupted and abandoned,
 *       and once the budget is exhausted the remaining caps' drains are skipped entirely.</dd>
 *
 *   <dt>{@link #terminate()}</dt>
 *   <dd>Release resources <b>unconditionally</b> — invoked for every cap that entered
 *       {@code initialize()}, whether or not its drain ran, completed, or timed out. Must be
 *       <b>re-entry-safe</b> (a second call is a no-op) and must not throw: it is the last line of
 *       cleanup, so the signature declares no checked exceptions and any runtime exception is caught
 *       and logged by the conductor rather than stopping the unwind. It must also be
 *       <b>defensive</b>: a timed-out drain has <em>no happens-before edge</em> into
 *       {@code terminate()} — the abandoned drain may still be running on another thread while
 *       {@code terminate()} releases the resources it touches.</dd>
 * </dl>
 *
 * <h2>Instantiation contract</h2>
 * <p>Implementations must have a <b>public no-arg constructor</b>: the conductor instantiates the
 * class reflectively from the manifest's per-module {@code lifecycleOwner}. The hooks class is a
 * lifecycle participant, not a service-wiring container — resolve collaborators inside the phase
 * methods (or via the cap's own wiring), never through constructor parameters.
 *
 * @since 0.9.0
 */
public interface CapabilityLifecycleHooks {

    /**
     * Phase 1 — after {@code KERNEL READY}, before the cap's first request. Idempotent
     * (replay-safe for validation dry-runs). Throwing aborts boot with a prefix unwind.
     *
     * @throws Exception to abort boot; the conductor chains it into the {@code CompositionBootException}
     */
    default void initialize() throws Exception {
    }

    /**
     * Phase 2 — the all-caps barrier: every cap in the composition has completed
     * {@link #initialize()}. Cross-cap service interaction is safe from here on.
     *
     * @throws Exception to abort boot; the conductor unwinds all caps (drain, then terminate)
     */
    default void ready() throws Exception {
    }

    /**
     * Phase 3 — stop intake, finish in-flight work, flush, within {@code remaining} (this cap's
     * strictly-remaining slice of the composition-wide budget; default 30s, per-SKU configurable).
     * Cooperative and conductor-enforced: an overrun is interrupted and abandoned.
     *
     * @param remaining the strictly-remaining composition-wide drain budget for this cap
     * @throws Exception logged by the conductor; never stops the unwind
     */
    default void drain(Duration remaining) throws Exception {
    }

    /**
     * Phase 4 — release unconditionally. Re-entry-safe, must not throw (runtime exceptions are
     * caught and logged by the conductor), and defensive against a still-running abandoned drain
     * (no happens-before edge from a timed-out {@link #drain(Duration)}).
     */
    default void terminate() {
    }
}
