/**
 * SKU-boot composition runtime — boot-time validation-stamp assertion (ADR-024 obligation 8/8a).
 *
 * <p><b>What this is.</b> A generic, once-tested library every SKU bootstrap invokes at startup,
 * before any cap enters {@code initialize}. It asserts the validation stamp the {@code exeris-tooling}
 * pipeline emits into {@code cap-manifest.json} (ADR-024 obligation 7) — presence, well-formedness,
 * content-binding-match, and (multi-manifest) version-match — and {@linkplain
 * eu.exeris.sdk.composition.runtime.CompositionStampException fails fast} on drift. Entry point:
 * {@link eu.exeris.sdk.composition.runtime.CompositionStampAssertion}.
 *
 * <p><b>What this is NOT.</b> Not a re-validation: no {@code @Requires}→{@code @Provides} DAG
 * re-resolution (that stays a build-time concern in the tooling). Not a security or licensing gate —
 * the SKU runtime is source-available and forkable, so this is a correctness / operability assertion
 * that catches honest config drift early (ADR-024 amendment, 2026-06-17). A hardened,
 * signature-backed boot gate would be a sealed-enterprise-substrate concern with its own ADR.
 *
 * <p><b>Placement (ADR-024 re-amendment, 2026-06-25).</b> This is the SDK-side runtime module that
 * realizes obligation 8 — shipped <em>into</em> each SKU artefact, not hosted in {@code exeris-platform}
 * (which is the deploy-time control plane that only <em>consumes</em> this library, obligation 8c). The
 * content-binding it recomputes is the one canonical {@link eu.exeris.sdk.composition.CompositionBinding}
 * in {@code exeris-sdk-composition-spec} (obligation 8b) — there is no byte-verbatim port; the spec's
 * golden test vector is the cross-module conformance pin.
 *
 * <p><b>Boundaries.</b> The open kernel stays cap-blind (ADR-024 obligation 9): this module depends on
 * no {@code exeris-kernel} or {@code exeris-tooling} type — only the SDK composition-spec, the
 * composition-lifecycle interface, and a JSON mapper — and no kernel package calls into it.
 *
 * <p><b>The boot conductor (landed, SDK 0.9.0).</b> The larger composition-runtime piece — the boot
 * conductor (ADR-024 obligation 8a′, amendment 2026-07-21 "Boot Conductor Call Site"),
 * {@link eu.exeris.sdk.composition.runtime.CompositionConductor} — drives each cap through the
 * four-phase lifecycle ({@code initialize → ready → drain → terminate}) in the tooling-supplied
 * {@code initOrder}, replayed verbatim (stamp assertion first, then reflective hook discovery from
 * the manifest's per-module {@code lifecycleOwner}, then the phase loops — with no DAG
 * re-resolution); a boot failure unwinds the touched caps and surfaces as
 * {@link eu.exeris.sdk.composition.runtime.CompositionBootException}. The hooks module is split on
 * purpose: the {@code CapabilityLifecycleHooks} interface lives in the zero-dependency
 * {@code exeris-sdk-composition-lifecycle} module, so cap authors implementing the hooks never
 * depend on this jar (or its Jackson dependency). Call-site model: the generated SKU bootstrap
 * invokes the conductor inside {@code kernelMain} after the kernel reports {@code KERNEL READY};
 * the conductor is <em>never</em> registered as a kernel {@code Subsystem}, and the cap layer
 * contributes no node to the kernel bootstrap DAG — the kernel stays cap-blind and the SKU artefact
 * boots standalone (the code-detachment guarantee). Shutdown is SKU-entrypoint-driven: caps drain
 * and terminate in reverse {@code initOrder} (honouring the composition-wide drain deadline), then
 * the kernel stops. The composition-manifest format the conductor consumes is fixed as JSON
 * (ADR-053). Tooling-emitter status: the generated SKU bootstrap that emits this call site ships
 * with the {@code exeris-tooling} bootstrap emitter (gateway-caps plan, Phase 1/2); until then a
 * hand-written SKU entrypoint invokes the conductor directly — the library contract is identical in
 * both cases.
 *
 * @since 0.8
 */
package eu.exeris.sdk.composition.runtime;
