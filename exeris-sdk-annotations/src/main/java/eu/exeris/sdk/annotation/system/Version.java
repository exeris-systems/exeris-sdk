package eu.exeris.sdk.annotation.system;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation. Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field for optimistic locking version control.
 * <p>When {@code @ExerisDomain(versioned = true)}, exactly one field
 * must be annotated with {@code @Version} OR {@code versionField} must be specified.
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * @ExerisDomain(module = "sales", path = "/orders", versioned = true)
 * public class Order {
 *
 *     @Field(label = "Version")
 *     @Version
 *     private Long version;
 * }
 * }</pre>
 *
 * <h2>Supported Types:</h2>
 * <ul>
 *   <li>{@code Long} / {@code long} - recommended</li>
 *   <li>{@code Integer} / {@code int}</li>
 *   <li>{@code Short} / {@code short}</li>
 *   <li>{@code Instant} / {@code Timestamp} - timestamp-based versioning</li>
 * </ul>
 *
 * <h2>Target design — not emitted today:</h2>
 * <ul>
 *   <li>JPA {@code @Version} annotation added</li>
 *   <li>Update operations require version in request</li>
 *   <li>Concurrent modification throws {@code OptimisticLockException}</li>
 *   <li>Version exposed in ETag header for HTTP caching</li>
 * </ul>
 *
 * <p><strong>Status: RESERVED</strong> — the {@code exeris-tooling} processor does not scan
 * fields for this marker, so writing it changes nothing in the emitted output. What
 * generates the column is the entity-level flag set on {@code @ExerisDomain}, with the
 * field-name override attributes choosing its name. See the package javadoc for the live
 * path.
 *
 * @since 0.1
 * @see eu.exeris.sdk.annotation.ExerisDomain#versioned()
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface Version {

    /**
     * Initial version value for new entities.
     *
     * @return initial version
     */
    long initialValue() default 0L;

    /**
     * Whether to include version in ETag header.
     * <p>Enables HTTP conditional requests (If-Match, If-None-Match).
     *
     * @return true if used for ETag
     */
    boolean useForETag() default true;

    /**
     * Whether version is required in update requests.
     * <p>When true, updates without version are rejected.
     *
     * @return true if required
     */
    boolean requiredOnUpdate() default true;
}
