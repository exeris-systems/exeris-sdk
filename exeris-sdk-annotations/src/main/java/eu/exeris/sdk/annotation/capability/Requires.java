package eu.exeris.sdk.annotation.capability;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a service that the annotated {@link CapabilityModule}
 * <strong>depends on</strong> — provided by another capability on the same
 * composition, or by a kernel SPI.
 *
 * <p>The service is referenced by its interface {@link Class} literal, exactly as
 * {@link Provides}. At build time each {@code @Requires} edge is matched against a
 * {@code @Provides} declaration; the resolved graph also derives capability
 * lifecycle ordering (no manual priorities). The SDK only records the edge; the
 * resolution, dependency DAG, and version-range intersection are the tooling's.
 *
 * <p>Repeatable: a cap may require several services from a single module class.
 *
 * {@snippet lang="java" :
 * @CapabilityModule
 * @Requires(service = KernelTransport.class)
 * @Requires(service = RouteRegistry.class, versionRange = "[1.0.0,2.0.0)")
 * @Requires(service = MetricsSink.class, optional = true)
 * public final class GatewayEdgeModule { }
 * }
 *
 *
 * <p><strong>Status: LIVE</strong> — extracted into {@code RequiresMetadata} and consumed
 * by the capability graph, which resolves it against the {@code @Provides} declarations of
 * every other module.
 * @since 0.4
 * @see Provides
 * @see CapabilityModule
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Documented
@Repeatable(Requires.List.class)
public @interface Requires {

    /**
     * The service interface this capability depends on.
     *
     * @return the service interface type
     */
    Class<?> service();

    /**
     * Acceptable version range of the required service contract, in Maven-style
     * range syntax (e.g. {@code "[1.0.0,2.0.0)"}). Empty means any version. Range
     * syntax and intersection are enforced by the build-time validator, not the
     * SDK.
     *
     * @return the required version range, or empty
     */
    String versionRange() default "";

    /**
     * Whether the dependency is optional. An optional {@code @Requires} permits
     * the cap to degrade gracefully when no provider is present — typically for
     * cross-cutting concerns such as observability and telemetry hooks.
     *
     * @return {@code true} if the cap functions without a provider for this service
     */
    boolean optional() default false;

    /**
     * Container for repeated {@link Requires} declarations on one
     * {@link CapabilityModule} class. Synthesized by the compiler; authors use
     * repeated {@code @Requires} directly and never write this type.
     *
     *
     * <p><strong>Status: LIVE</strong> — a container is read exactly when the annotation it
     * holds is, and {@code @Requires} drives the cap manifest.
     * @see Requires
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.SOURCE)
    @Documented
    @interface List {

        /**
         * The repeated declarations.
         *
         * @return the {@link Requires} array
         */
        Requires[] value();
    }
}
