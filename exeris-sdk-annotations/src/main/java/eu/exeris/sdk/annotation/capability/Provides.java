package eu.exeris.sdk.annotation.capability;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a service interface that the annotated {@link CapabilityModule}
 * <strong>exposes</strong> to other capabilities and to user / SKU code.
 *
 * <p>The service is referenced by its interface {@link Class} literal — the
 * single mechanism that unifies "another cap's service" and "a kernel SPI": both
 * are just interface types the cap already has on its compile classpath. The
 * annotation attribute is a bare {@code Class<?>}, so the SDK depends on nothing;
 * the build-time pipeline records the service by name (normalized to its
 * fully-qualified form) and never loads the class.
 *
 * <p>Repeatable: a "substrate-aggregate" cap may provide several services from a
 * single module class.
 *
 * <pre>{@code
 * @CapabilityModule
 * @Provides(service = RouteRegistry.class, version = "1.0.0")
 * @Provides(service = UpstreamPool.class, version = "1.1.0")
 * public final class GatewayCoreModule { }
 * }</pre>
 *
 *
 * <p><strong>Status: LIVE</strong> — extracted into {@code ProvidesMetadata} and consumed
 * by the capability graph and the composition stamp — this is one half of the edge set
 * behind {@code cap-manifest.json}.
 * @author Exeris SDK Team
 * @version 0.4.0
 * @since 0.4.0
 * @see Requires
 * @see CapabilityModule
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Documented
@Repeatable(Provides.List.class)
public @interface Provides {

    /**
     * The service interface this capability exposes.
     *
     * @return the service interface type
     */
    Class<?> service();

    /**
     * Semantic version of the provided service contract (e.g. {@code "1.0.0"}).
     * Empty means unversioned; range matching against consumers' {@link Requires}
     * is performed by the build-time validator, not the SDK.
     *
     * @return the service contract version, or empty
     */
    String version() default "";

    /**
     * Container for repeated {@link Provides} declarations on one
     * {@link CapabilityModule} class. Synthesized by the compiler; authors use
     * repeated {@code @Provides} directly and never write this type.
     *
     *
     * <p><strong>Status: LIVE</strong> — the processor unwraps this container to reach the
     * repeated {@code @Provides} declarations, so its status is {@code @Provides}'s own.
     * @see Provides
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.SOURCE)
    @Documented
    @interface List {

        /**
         * The repeated declarations.
         *
         * @return the {@link Provides} array
         */
        Provides[] value();
    }
}
