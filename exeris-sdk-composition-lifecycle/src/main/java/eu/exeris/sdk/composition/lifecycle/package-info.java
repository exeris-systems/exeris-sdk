/**
 * The cap-facing composition lifecycle contract — a single zero-dependency interface,
 * {@link eu.exeris.sdk.composition.lifecycle.CapabilityLifecycleHooks}, realizing the ADR-024
 * four-phase cap lifecycle ({@code initialize → ready → drain → terminate}, obligation 8a).
 *
 * <h2>Charter — why its own module</h2>
 * <p>This module exists so a cap author's compile classpath stays minimal: implementing the hooks
 * requires <b>{@code exeris-sdk-annotations} + this jar only</b>. The interface deliberately lives in
 * neither sibling composition module:
 * <ul>
 *   <li><b>Not {@code exeris-sdk-composition-spec}</b> — the spec's charter is the
 *       {@code cap-manifest.json} schema + content-binding shared by the tooling emitter and the
 *       SKU-boot asserter; putting a cap-implemented runtime interface there would put the hooks on
 *       the tooling emitter's classpath (and the emitter on the cap author's radar) for no reason.</li>
 *   <li><b>Not {@code exeris-sdk-composition-runtime}</b> — the runtime (stamp asserter + boot
 *       conductor) depends on {@code jackson-databind}; hosting the interface there would drag
 *       Jackson onto every cap author's classpath. The dependency points the other way: the runtime
 *       depends on this module to <em>invoke</em> the hooks, cap code never depends on the runtime.</li>
 * </ul>
 * <p>The zero-dependency contract is enforcer-proven: the module's build bans every
 * compile/runtime-scope dependency (test scope exempt).
 *
 * <h2>Call-site status</h2>
 * <p>Invoked by the SKU bootstrap inside {@code KernelBootstrap.boot(kernelMain)}, after
 * {@code KERNEL READY} (ADR-024, 2026-07-21 amendment). The generated SKU bootstrap that emits this
 * call site ships with the {@code exeris-tooling} bootstrap emitter (gateway-caps plan, Phase 1/2);
 * until then a hand-written SKU entrypoint invokes the conductor directly — the library contract is
 * identical in both cases.
 *
 * @since 0.9.0
 */
package eu.exeris.sdk.composition.lifecycle;
