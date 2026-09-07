/**
 * The shared composition-spec — the single definition of the ADR-024 {@code cap-manifest.json}
 * schema ({@link eu.exeris.sdk.composition.CapManifest}) and content-binding algorithm
 * ({@link eu.exeris.sdk.composition.CompositionBinding}), depended on by both the build-time
 * tooling that <em>emits</em> the manifest and the SKU-boot runtime that <em>asserts</em> it
 * (ADR-024 obligation 8b, 2026-06-25 "Composition Runtime Placement" amendment).
 *
 * <h2>What this is, and is not</h2>
 * <ul>
 *   <li><b>Is</b>: the manifest read-schema + the one canonical binding, golden-vector pinned.</li>
 *   <li><b>Is not</b>: a validator. It does not resolve the {@code @Requires}/{@code @Provides}
 *       DAG (that is build-time, in {@code exeris-tooling}) and does not assert the stamp (that is
 *       the SKU-boot runtime). It carries no graph-building, no Jackson, no kernel awareness.</li>
 * </ul>
 *
 * <h2>Dependency-free on purpose</h2>
 * <p>This module has zero runtime dependencies — pure records plus {@code MessageDigest}. That is
 * what lets the tooling emitter and the SKU-boot runtime both depend on it without either dragging
 * the build-time pipeline or a JSON binder onto the other's classpath. Consumers bring their own
 * mapper; see {@link eu.exeris.sdk.composition.CapManifest} for the required mapper configuration.
 *
 * <h2>Why the SDK</h2>
 * <p>Per the ADR-024 re-amendment the spec lives in the SDK because its consumers are the
 * <em>coupled</em> tooling↔runtime pair (the tooling already depends on the SDK to read its
 * annotations), with the platform a transitive consumer through the runtime library. This is the
 * inverse of {@code exeris-telemetry-spec}, which earned its own repo only because its emitter
 * (kernel) and decoder (enterprise-observability) are independent repos that must not couple. The
 * documented extraction trigger: if an SDK-independent tool ever needs to read
 * {@code cap-manifest.json} directly, extract this into a standalone {@code exeris-composition-spec}.
 *
 * @since 0.8
 */
package eu.exeris.sdk.composition;
