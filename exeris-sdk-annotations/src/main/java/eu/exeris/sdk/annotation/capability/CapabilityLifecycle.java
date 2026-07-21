package eu.exeris.sdk.annotation.capability;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the class that owns a capability's <strong>lifecycle hooks</strong> —
 * the four-phase {@code initialize} / {@code ready} / {@code drain} /
 * {@code terminate} sequence the boot conductor drives after the kernel
 * reports {@code KERNEL READY}.
 *
 * <p>This is a <strong>marker only</strong>. The lifecycle <em>interface</em>
 * ({@code CapabilityLifecycleHooks}) lives SDK-side — in the zero-dependency
 * {@code exeris-sdk-composition-lifecycle} module, driven by the boot
 * conductor in {@code exeris-sdk-composition-runtime} (ADR-024 amendments
 * 2026-06-25 / 2026-07-21, planned for 0.9.0) — never kernel-side: the
 * conductor is invoked by the generated SKU bootstrap and is not a kernel
 * {@code Subsystem}, so neither side references kernel bootstrap. The
 * annotation merely records "this class is the cap's lifecycle owner"; the
 * build-time tooling derives the invocation order from the {@link Requires}
 * graph and emits it as the manifest {@code initOrder} the conductor replays.
 *
 * <h2>Cardinality</h2>
 * <p>Zero or one per capability. Absence is valid — a cap with no
 * bootstrap-bound lifecycle owner. More than one {@code @CapabilityLifecycle}
 * class per capability is a build error the tooling validator rejects; it cannot
 * be expressed as a Java type constraint. The annotated class may be distinct
 * from the {@link CapabilityModule} class.
 *
 * <pre>{@code
 * @CapabilityLifecycle
 * public final class GatewayLifecycle implements CapabilityLifecycleHooks {
 *     // initialize / ready / drain / terminate — interface from the SDK composition runtime
 * }
 * }</pre>
 *
 * @author Exeris SDK Team
 * @version 0.4.0
 * @since 0.4.0
 * @see CapabilityModule
 * @see Requires
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface CapabilityLifecycle {
}
