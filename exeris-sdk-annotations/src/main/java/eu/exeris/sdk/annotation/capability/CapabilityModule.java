package eu.exeris.sdk.annotation.capability;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a <strong>capability module</strong> — the carrier for a
 * capability's {@link Provides} and {@link Requires} declarations.
 *
 * <p>A capability ("cap") is a self-contained Exeris feature shipped from its own
 * {@code exeris-caps-*} repository. The capability module class is its public
 * composition surface: it declares the service interfaces the cap exposes
 * ({@code @Provides}) and the ones it consumes from other caps or kernel SPIs
 * ({@code @Requires}). It lives in the cap's API package
 * ({@code eu.exeris.caps.<cap-name>.api}); the {@code exeris-tooling} build-time
 * pipeline reads it, resolves the {@code @Requires}→{@code @Provides} graph, and
 * emits the cap manifest.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @CapabilityModule
 * @Provides(service = RouteRegistry.class, version = "1.0.0")
 * @Provides(service = BackendHealthMonitor.class, version = "1.0.0")
 * @Requires(service = KernelTransport.class)
 * @Requires(service = MetricsSink.class, optional = true)
 * public final class GatewayCoreModule { }
 * }</pre>
 *
 * <h2>Scope</h2>
 * <p>This is a pure marker. A cap's <em>identity</em> comes from its repository
 * coordinate and licensing taxonomy (a per-cap-repo property — see the capability
 * licensing taxonomy ADR), not from an attribute here, which is why the annotation
 * carries no name field. Build-time validation (dependency DAG, version-range
 * intersection, the cap-tier Wall checks) and the cap manifest are the tooling's
 * responsibility, not the SDK's.
 *
 * @author Exeris SDK Team
 * @version 0.4.0
 * @since 0.4.0
 * @see Provides
 * @see Requires
 * @see CapabilityLifecycle
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface CapabilityModule {
}
