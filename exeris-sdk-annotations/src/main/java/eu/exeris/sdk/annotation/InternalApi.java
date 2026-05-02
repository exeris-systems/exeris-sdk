package eu.exeris.sdk.annotation;

import java. lang.annotation.*;

/**
 * Marks a method or class as internal API (service-to-service only).
 *
 * <p>Internal APIs are not exposed in public OpenAPI documentation
 * and require internal service authentication.
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * @InternalApi(
 *     consumers = {"billing-service", "notification-service"},
 *     rateLimit = 1000
 * )
 * @Action(name = "sync-balance", ...)
 * public void syncBalance() { ... }
 * }</pre>
 *
 * @author Exeris platform Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface InternalApi {

    /**
     * Services that are allowed to call this API.
     * <p>Empty = any internal service.
     *
     * @return allowed consumer service names
     */
    String[] consumers() default {};

    /**
     * Rate limit per minute for this endpoint.
     * <p>0 = no rate limit.
     *
     * @return requests per minute
     */
    int rateLimit() default 0;

    /**
     * Whether to require mutual TLS authentication.
     *
     * @return true to require mTLS
     */
    boolean requireMtls() default false;

    /**
     * Timeout for internal calls (ISO-8601 duration).
     *
     * @return timeout duration
     */
    String timeout() default "PT30S";

    /**
     * Whether to include in internal API documentation.
     *
     * @return true to document
     */
    boolean documented() default true;
}
