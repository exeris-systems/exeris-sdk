package eu.exeris.sdk.annotation.capability;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the class that owns a capability's <strong>lifecycle hooks</strong> —
 * the four-phase {@code initialize} / {@code ready} / {@code drain} /
 * {@code terminate} sequence bound to the kernel bootstrap state machine.
 *
 * <p>This is a <strong>marker only</strong>. The lifecycle <em>interface</em>
 * itself is a kernel SPI type and deliberately stays kernel-side — the SDK must
 * not reference kernel bootstrap. The annotation merely records "this class is
 * the cap's lifecycle owner"; the kernel and the build-time tooling bind the
 * behaviour and derive the invocation order from the {@link Requires} graph.
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
 *     // initialize / ready / drain / terminate — interface from the kernel SPI
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
