package eu.exeris.sdk.annotation.system;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation. Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field for optimistic locking version control.
 * <p>When {@code @ExerisDomain(versioned = true)}, at most one field may carry
 * {@code @Version}; with none, the {@code versionField} override or the canonical
 * name {@code version} applies.
 *
 * <h2>Usage:</h2>
 * {@snippet lang="java" :
 * @ExerisDomain(module = "sales", path = "/orders", versioned = true)
 * public class Order {
 *
 *     @Field(label = "Version")
 *     @Version
 *     private Long version;
 * }
 * }
 *
 * <h2>Supported Types:</h2>
 * <ul>
 *   <li>{@code Long} / {@code long} - recommended</li>
 *   <li>{@code Integer} / {@code int}</li>
 *   <li>{@code Short} / {@code short}</li>
 *   <li>{@code Instant} / {@code Timestamp} - timestamp-based versioning</li>
 * </ul>
 *
 * <h2>Emitted today — by {@code @ExerisDomain(versioned = true)}, not by this marker:</h2>
 * <ul>
 *   <li>An update matches on the id <em>and</em> the expected version, and writes
 *       the incremented one</li>
 *   <li>A stale update — no row at the expected version — is rejected with a
 *       version-conflict error the generated handler answers with 409</li>
 * </ul>
 *
 * <h2>Target design — not emitted today:</h2>
 * <ul>
 *   <li>Update operations require version in request — today a missing version
 *       is read as {@code 0}</li>
 *   <li>Version exposed in ETag header for HTTP caching</li>
 * </ul>
 *
 * <p><strong>Status: LIVE</strong> — the {@code exeris-tooling} processor reads this
 * marker and records the annotated field as {@code SystemFieldsMetadata.versionField},
 * which the generators use in place of the canonical name. It names the field; the column
 * exists only when {@code @ExerisDomain(versioned = true)}. None of the attributes below is
 * carried, so setting one changes no emitted output. The {@code -io} reader reads the
 * marker the same way. See the package javadoc.
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
