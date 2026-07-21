package eu.exeris.sdk.composition.runtime;

import eu.exeris.sdk.composition.lifecycle.CapabilityLifecycleHooks;
import java.time.Duration;

/**
 * Package-private determinism seam: runs one cap's {@code drain(remaining)} under a timeout and
 * reports whether it completed ({@code true}) or overran and was abandoned ({@code false}). A drain
 * that throws propagates to the conductor's drain loop, which logs and proceeds — a failing drain
 * never stops the unwind. Tests inject a synchronous fake; production uses {@link #production()}.
 *
 * <p><b>Why the production runner is calling-thread + watchdog, not {@code StructuredTaskScope}.</b>
 * The preferred shape was a structured fork (fork the drain, join with deadline, interrupt on
 * overrun), because {@code kernelMain} runs inside the kernel's {@code ScopedValue} scope and
 * structured forks inherit {@code ScopedValue} bindings. But on the JDK 26 toolchain this repo pins,
 * {@code java.util.concurrent.StructuredTaskScope} is still a <b>preview API</b> (verified: javac
 * 26.0.1 rejects it without {@code --enable-preview}), and this reactor carries no preview flags and
 * must not gain them. So the production runner executes the drain on the <b>calling thread</b> —
 * which preserves {@code ScopedValue} bindings trivially — while a single daemon watchdog thread
 * interrupts the calling thread on deadline overrun; the interrupt flag is cleared afterwards so a
 * late interrupt can never leak into the next cap's drain or the terminate phase. The known
 * limitation (accepted for 0.9.0): interruption is cooperative — a drain that neither blocks on
 * interruptible operations nor checks the interrupt flag delays shutdown past the budget. Revisit
 * when {@code StructuredTaskScope} finalizes.
 *
 * @since 0.9.0
 */
@FunctionalInterface
interface DrainRunner {

    /**
     * Run {@code hooks.drain(remaining)} bounded by {@code remaining}.
     *
     * @return {@code true} if the drain returned within the deadline; {@code false} if it overran
     *         and was abandoned (interrupted)
     * @throws Exception a drain failure (not an overrun) — the caller logs it and proceeds
     */
    boolean run(CapabilityLifecycleHooks hooks, Duration remaining) throws Exception;

    /** The production runner: calling-thread drain + watchdog interrupt (see the type javadoc). */
    static DrainRunner production() {
        return (hooks, remaining) -> {
            Thread caller = Thread.currentThread();
            Object gate = new Object();
            boolean[] drainOver = {false};
            Thread watchdog = new Thread(() -> {
                try {
                    Thread.sleep(remaining);
                } catch (InterruptedException cancelled) {
                    // The drain finished in time — the caller cancelled this watchdog. Restore the
                    // flag per convention (S2142); the watchdog thread dies on return, so it goes
                    // nowhere.
                    Thread.currentThread().interrupt();
                    return;
                }
                synchronized (gate) {
                    if (!drainOver[0]) {
                        caller.interrupt();
                    }
                }
            }, "exeris-cap-drain-watchdog");
            watchdog.setDaemon(true);
            watchdog.start();
            try {
                hooks.drain(remaining);
                return true;
            } catch (InterruptedException overrun) {
                // The watchdog fired — the drain is abandoned. Restore the flag per convention
                // (S2142); the gated clear in the finally below is deliberately the last word, so
                // the self-inflicted interrupt still never leaks into terminate.
                Thread.currentThread().interrupt();
                return false;
            } finally {
                // Once drainOver is set under the gate, the watchdog can never interrupt again, so
                // the subsequent flag-clear is guaranteed to be the last word — no leaked interrupt.
                synchronized (gate) {
                    drainOver[0] = true;
                }
                watchdog.interrupt(); // cancel a still-sleeping watchdog
                Thread.interrupted(); // clear a late (pre-gate) interrupt off the calling thread
            }
        };
    }
}
