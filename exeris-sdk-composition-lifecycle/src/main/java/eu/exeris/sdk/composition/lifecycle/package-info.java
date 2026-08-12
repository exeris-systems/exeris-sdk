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
 * <h2>Call-site status — generated for a composed build</h2>
 * <p>The hooks are invoked by the boot conductor, and the conductor's call site is
 * <em>generated</em>: {@code exeris-tooling}'s {@code KernelApplicationGenerator} emits
 * {@code try (CompositionConductor conductor = CompositionConductor.from(capManifest()).start())}
 * inside {@code KernelBootstrap.boot(...)} — after {@code KERNEL READY}, never as a kernel
 * {@code Subsystem} (ADR-024, 2026-07-21 amendment; {@code KernelApplicationGenerator.java:449-458},
 * pinned end-to-end by {@code CapCompositionE2ETest.java:145-150}).
 *
 * <p><b>The one case with no generated call site is a cap-less build.</b> The emitter is gated on
 * the project declaring at least one {@code @CapabilityModule}; without one, not a single conductor
 * symbol is emitted — no import, no {@code capManifest()} seam, no try-with-resources
 * ({@code KernelApplicationGenerator.java:307-311}). That is deliberate: a Tier-3 app has no
 * composition to conduct. A Tier-3 app that later wants the lifecycle either declares a cap or
 * invokes the conductor from a hand-written entrypoint — the library contract here is identical in
 * both cases.
 *
 * @since 0.9.0
 */
package eu.exeris.sdk.composition.lifecycle;
