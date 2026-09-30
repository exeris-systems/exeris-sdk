package eu.exeris.sdk.annotation.system;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the field that carries the shared-scope key of a
 * {@code DataScope.UNIVERSE} entity — the value that widens reads across tenants
 * while writes stay pinned to the owning one.
 *
 * <p>This is the field-level twin of {@link TenantId}, and the parallel is exact.
 * A tenant-partitioned entity's generated RLS policy compares its
 * {@code @TenantId} column against the PostgreSQL session variable the kernel
 * publishes as {@code ConnectionInterceptor.SESSION_KEY_TENANT_ID}. A shared-world
 * entity's policy has to compare some column against
 * {@code SESSION_KEY_SHARED_SCOPE} — and until this marker existed there was no way
 * to say which column that is.
 *
 * <h2>Usage:</h2>
 * {@snippet lang="java" :
 * @ExerisDomain(module = "catalog", path = "/species", dataScope = DataScope.UNIVERSE)
 * public class Species {
 *
 *     @Field(label = "Owning organization")
 *     @TenantId
 *     private UUID organizationId;      // writes stay pinned here
 *
 *     @Field(label = "World")
 *     @SharedScope
 *     private UUID worldId;             // reads widen across tenants sharing this
 * }
 * }
 *
 * <h2>It does not replace {@link TenantId}, it accompanies it</h2>
 * <p>The kernel's shared tier is an <em>orthogonal row-visibility dimension</em>,
 * not a fourth isolation strategy: a universe row is owned by a tenant
 * <strong>and</strong> readable by everyone in its shared scope. Read-widening and
 * owner-pinned writes are two predicates over two columns, so an entity that
 * declares this marker and no owner has described a row nothing can write.
 *
 * <h2>Supported Types:</h2>
 * <ul>
 *   <li>{@code UUID} - recommended</li>
 *   <li>{@code String} - for legacy systems</li>
 * </ul>
 *
 * <p><strong>Status: PARTIAL</strong> — the {@code exeris-tooling} processor reads this
 * marker on a {@code DataScope.UNIVERSE} entity into
 * {@code SystemFieldsMetadata.sharedScopeField}, and the generators key the entity's
 * read-widening policy and its repository's shared-scope stamp on that field. On an entity of
 * any other tier the marker has no effect: the processor warns and does not record it. On a
 * {@code UNIVERSE} entity the processor refuses a missing marker, the marker on more than one
 * field, a field typed other than {@code UUID} or {@code String}, the marker on the owner
 * field itself, and a marked field declared {@code @Field(required = true)} — the repository
 * fills an absent shared scope from the bound storage context, and a row with none is
 * owner-private. The marker carries no attributes.
 *
 * @since 0.12
 * @see TenantId
 * @see eu.exeris.sdk.annotation.ExerisDomain#dataScope()
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface SharedScope {
}
