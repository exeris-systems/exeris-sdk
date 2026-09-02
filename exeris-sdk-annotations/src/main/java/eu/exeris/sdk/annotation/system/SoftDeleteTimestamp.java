package eu.exeris.sdk.annotation.system;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a temporal field as the soft delete timestamp.
 * <p>Automatically populated when entity is soft-deleted.
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * @Field(label = "Deleted At")
 * @SoftDeleteTimestamp
 * private Instant deletedAt;
 * }</pre>
 *
 * <h2>Supported Types:</h2>
 * <ul>
 *   <li>{@code Instant} - recommended (UTC)</li>
 *   <li>{@code LocalDateTime}</li>
 *   <li>{@code OffsetDateTime}</li>
 *   <li>{@code ZonedDateTime}</li>
 * </ul>
 *
 * <p><strong>Status: RESERVED</strong> — the {@code exeris-tooling} processor does not scan
 * fields for this marker, so writing it changes nothing in the emitted output. What
 * generates the column is the entity-level flag set on {@code @ExerisDomain}, with the
 * field-name override attributes choosing its name. See the package javadoc for the live
 * path.
 *
 * @author Exeris SDK Team
 * @version 0.1.0
 * @since 0.1.0
 * @see SoftDelete
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface SoftDeleteTimestamp {

    /**
     * Whether to clear timestamp on restore (undelete).
     * <p>Default: {@code true}
     *
     * @return true if cleared on restore
     */
    boolean clearOnRestore() default true;
}
