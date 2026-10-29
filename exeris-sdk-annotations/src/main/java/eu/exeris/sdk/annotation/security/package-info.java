/**
 * Security annotations for the Exeris SDK.
 * <p>This package contains two annotations, both DEPRECATED for removal in 1.0.0:
 * <ul>
 *   <li>{@link eu.exeris.sdk.annotation.security.Encrypted @Encrypted} - field-level encryption at
 *       rest</li>
 *   <li>{@link eu.exeris.sdk.annotation.security.RowLevelSecurity @RowLevelSecurity} - row-level
 *       security policies</li>
 * </ul>
 *
 * <h2>Status — DEPRECATED, ruled out by the kernel at 1.0</h2>
 * <p>Neither annotation is extracted by a processor, carried by an AST record or read by a
 * generator, and neither will be: kernel ADR-012 §4c.1 rules out, at 1.0, the contract each
 * would compile against.
 * <ul>
 *   <li><strong>{@code @RowLevelSecurity}.</strong> The kernel publishes exactly two session keys
 *       to the database session, {@code exeris.tenant_id} and {@code exeris.shared_scope}, and no
 *       principal key at 1.0, so a policy keyed on a principal ({@code OWNER}, {@code TEAM},
 *       {@code HIERARCHY}, {@code DEPARTMENT}, a principal-reading {@code CUSTOM} predicate)
 *       belongs to the application. Tenant isolation, the one policy the published keys support,
 *       is {@code @ExerisDomain(dataScope = DataScope.TENANT)}, which emits the tenant column,
 *       the RLS policy and the query filter, with {@code @TenantId}, the {@code tenantIdField}
 *       override or the canonical accessor name saying which field holds the tenant. Route roles
 *       and permissions are {@code @ExerisDomain.roles} / {@code permissions},
 *       {@code @Action.roles} / {@code permissions} and {@code @RouteAccess}.</li>
 *   <li><strong>{@code @Encrypted}.</strong> Field-level encryption at rest is outside kernel
 *       scope at 1.0, in every tier, and tooling must not invent a scheme, so the annotation has
 *       no kernel target. {@code @Field.encrypted} is deprecated by the same ruling.</li>
 * </ul>
 * <p>They are not kept as a reserved surface outside the 1.0.0 freeze (ADR-072): that reservation
 * requires the enforcing platform binding to exist, and here the kernel has ruled the binding
 * out rather than shipped it in preview. Each annotation's javadoc maps every attribute to its
 * replacement, or states that it has none.
 *
 * @since 0.1
 */
package eu.exeris.sdk.annotation.security;
