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
 * <pre>{@code
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
 * }</pre>
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
 * <p><strong>Status: RESERVED</strong> — the {@code exeris-tooling} processor does not scan
 * fields for this marker, so writing it changes nothing in the emitted output; it joins the
 * ten markers already in this package on the same footing. Declaring
 * {@code dataScope = UNIVERSE} is refused at the declaration site today, so there is
 * currently no build in which this field would be read.
 *
 * <p><strong>Why it lands before the transcription that reads it.</strong> The kernel half is
 * complete only as of v0.12.0, and it completed in two steps that are easy to mistake for
 * one. {@code StorageContext.sharedScopeKey()} — what an application reads — has existed
 * since the 0.11 line. The session-variable <em>name</em>, which is what emitted SQL writes,
 * became a published constant only in v0.12.0; before that a generator would have been
 * transcribing a string no kernel surface defined. With both halves published, the one thing
 * still missing for a transcription is the SDK saying which column to compare — which is this.
 *
 * @since 0.12.0
 * @see TenantId
 * @see eu.exeris.sdk.annotation.ExerisDomain#dataScope()
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface SharedScope {
}
