/**
 * Capability composition annotations — the SDK realization of the ecosystem
 * capability-composition model.
 *
 * <h2>Overview</h2>
 * <p>A <em>capability</em> ("cap") is a self-contained Exeris feature shipped
 * from its own {@code exeris-caps-*} repository. These annotations let a cap
 * declare, in pure source, what it exposes and what it depends on, so the
 * {@code exeris-tooling} build-time pipeline can compose caps into a running
 * system — resolving dependencies, ordering lifecycles, and emitting a manifest.
 *
 * <dl>
 *   <dt>{@link eu.exeris.sdk.annotation.capability.CapabilityModule @CapabilityModule}</dt>
 *   <dd>Marks the cap's composition surface — the carrier class for its
 *       {@code @Provides} / {@code @Requires} declarations.</dd>
 *
 *   <dt>{@link eu.exeris.sdk.annotation.capability.Provides @Provides}</dt>
 *   <dd>A service interface the cap exposes (repeatable).</dd>
 *
 *   <dt>{@link eu.exeris.sdk.annotation.capability.Requires @Requires}</dt>
 *   <dd>A service the cap depends on — another cap's service or a kernel SPI
 *       (repeatable).</dd>
 *
 *   <dt>{@link eu.exeris.sdk.annotation.capability.CapabilityLifecycle @CapabilityLifecycle}</dt>
 *   <dd>Marks the class owning the cap's lifecycle hooks (marker only — the
 *       {@code CapabilityLifecycleHooks} interface lives SDK-side, with the
 *       boot conductor in the composition runtime; ADR-024 amendment
 *       2026-06-25).</dd>
 * </dl>
 *
 * <h2>Service references</h2>
 * <p>Services are referenced by their interface {@link java.lang.Class} literal
 * ({@code @Provides(service = RouteRegistry.class)}). This is type-safe and
 * refactor-friendly and unifies "cap service" with "kernel SPI" into one
 * mechanism, <strong>without</strong> coupling the SDK to anything: the attribute
 * is a bare {@code Class<?>}, so the SDK imports nothing, and the build-time
 * pipeline records the service by name (normalized to its fully-qualified form)
 * rather than loading the class.
 *
 * <h2>What stays out of this module</h2>
 * <p>These annotations are SOURCE-retained markers and data only. Deliberately
 * <em>not</em> here:
 * <ul>
 *   <li><strong>The lifecycle interface</strong> — not an annotation:
 *       {@code CapabilityLifecycleHooks} is a runtime type that lands with the
 *       boot conductor in {@code exeris-sdk-composition-runtime} (ADR-024
 *       amendments 2026-06-25 / 2026-07-21, planned 0.9.0); this module ships
 *       only the {@code @CapabilityLifecycle} marker.</li>
 *   <li><strong>Licensing</strong> — {@code community} / {@code commercial} /
 *       {@code enterprise-private} is a per-cap-repository property, never an
 *       annotation field.</li>
 *   <li><strong>Resolution, the dependency DAG, version-range intersection, the
 *       cap-tier Wall checks, and the cap manifest</strong> — all build-time
 *       responsibilities of {@code exeris-tooling}.</li>
 * </ul>
 *
 * <h2>Open-Core status — reserved, not yet consumed</h2>
 * <p>This is a declared shape, not yet a live composition. The AST records and
 * the {@code exeris-sdk-source-model-io} reader for the capability surface
 * exist (0.4.0), but the build-time annotation processor does not yet extract
 * these annotations and no code generator consumes them — so declaring
 * {@code @CapabilityModule} / {@code @Provides} / {@code @Requires} today has
 * no generated effect. The surface is reserved while the {@code exeris-tooling}
 * registry consumer that wires the cap graph end-to-end is built; the
 * annotations gain meaning once it lands.
 *
 * @author Exeris SDK Team
 * @version 0.4.0
 * @since 0.4.0
 */
package eu.exeris.sdk.annotation.capability;
