package eu.exeris.sdk.annotation.security;

import java.lang.annotation.*;

/**
 * Declares a row-level security (RLS) policy for a domain entity.
 *
 * <p><strong>Status: DEPRECATED</strong>, for removal in 1.0.0. No processor extracts it, no AST
 * record carries it and no generator reads it, so writing it changes nothing in the emitted
 * output. It will not gain a carrier: no part of it can be compiled against a kernel contract
 * that tenant isolation does not already use.
 *
 * <p>Kernel ADR-012 §4c.1 rules that the kernel publishes exactly two session keys to the
 * database session, {@code exeris.tenant_id} and {@code exeris.shared_scope}, and "no principal
 * key at 1.0". Every part of this annotation falls on one side of that ruling:
 * <ul>
 *   <li><strong>{@link Policy#TENANT_ISOLATION}</strong> is the one policy that compiles against
 *       a published key, and {@code @ExerisDomain(dataScope = DataScope.TENANT)} already emits
 *       it: the tenant column, the RLS policy and the query filter. A second declaration site for
 *       the same tier could only repeat {@code dataScope} or contradict it.</li>
 *   <li><strong>{@link Policy#OWNER}, {@link Policy#TEAM}, {@link Policy#HIERARCHY},
 *       {@link Policy#DEPARTMENT}, {@link Policy#TENANT_AND_OWNER}</strong>, and a
 *       {@link Policy#CUSTOM} predicate that reads the caller, are keyed on a principal. ADR-012
 *       §4c.1: "Policies keyed on a principal ({@code OWNER}, {@code TEAM}, {@code HIERARCHY},
 *       {@code DEPARTMENT}, and a {@code CUSTOM} predicate that needs one) belong to the
 *       application or its generated binding." The SQL functions {@link #condition()} lists
 *       ({@code current_user_id()}, {@code has_role(…)}, …) are defined by no kernel and no
 *       generated schema.</li>
 *   <li><strong>{@link #readRoles()}, {@link #writeRoles()}, {@link #bypassRoles()}</strong>
 *       cannot be evaluated by a policy, because no role reaches the database session. Which
 *       callers may reach an entity is route authorization:
 *       {@link eu.exeris.sdk.annotation.ExerisDomain#roles() @ExerisDomain.roles} /
 *       {@link eu.exeris.sdk.annotation.ExerisDomain#permissions() permissions},
 *       {@link eu.exeris.sdk.annotation.Action#roles() @Action.roles} /
 *       {@link eu.exeris.sdk.annotation.Action#permissions() permissions}, and
 *       {@link eu.exeris.sdk.annotation.RouteAccess @RouteAccess}.</li>
 *   <li><strong>{@link #policyName()}, {@link #restrictive()}, {@link #applyOnInsert()},
 *       {@link #applyOnUpdate()}, {@link #applyOnDelete()}, {@link #auditLevel()}</strong> shape a
 *       policy that, for tenant isolation, {@code dataScope} emits and the kernel's reference
 *       policy fixes. Two of them would break it: {@code applyOnInsert = false} removes, for inserts,
 *       the write check that ADR-012 §4c.2 makes pin the tenant and the shared-scope tag, and
 *       {@code restrictive = true} combines the tenant policy with the {@code FOR SELECT}
 *       shared-scope policy by AND, so the read widening could never admit another tenant's
 *       row.</li>
 * </ul>
 *
 * <p>A policy that claims conformance with kernel ADR-012 §4c.3 raises when no tenant key is
 * published, for reads and for writes: a request without a tenant bound fails the statement
 * ({@code EX-PERS-5003}) and does not read an empty table. The shared-scope arm differs, because
 * an absent scope is the tenant-private case and matches no row. The platform refuses to boot a
 * pool whose database role carries {@code SUPERUSER} or {@code BYPASSRLS} unless
 * {@code persistence.rlsAllowBypassRole} is set (see {@link #bypassRoles()}), because such a role
 * skips every policy.
 *
 * <p>Migration:
 * {@snippet lang="java" :
 * // before
 * @ExerisDomain(module = "sales", path = "/orders")
 * @RowLevelSecurity(policy = Policy.TENANT_ISOLATION, readRoles = "SALES")
 * public class Order { }
 *
 * // after
 * @ExerisDomain(module = "sales", path = "/orders",
 *               dataScope = ExerisDomain.DataScope.TENANT, roles = "SALES")
 * public class Order { }
 * }
 *
 * <p>A principal-keyed policy has no SDK replacement. An application that needs one publishes
 * its own session key with its own {@code ConnectionInterceptor}, registered through
 * {@code PersistenceEngine.registerInterceptor}, and writes the policy itself. That key is not a
 * kernel contract, and the interceptor must publish it on every connection acquisition, absent
 * value included, because a session-scoped setting survives connection reuse (ADR-012 §4c.1).
 *
 * @since 0.1
 * @deprecated since 0.13.0, for removal in 1.0.0. Tenant isolation is
 *         {@code @ExerisDomain(dataScope = DataScope.TENANT)}; route roles and permissions are
 *         {@code @ExerisDomain.roles} / {@code permissions}, {@code @Action.roles} /
 *         {@code permissions} and {@code @RouteAccess}. Principal-keyed policies have no
 *         replacement: the kernel publishes no principal session key at 1.0 (kernel ADR-012
 *         §4c.1), so they belong to the application. See {@code MIGRATION.md}.
 * @see Encrypted
 */
@Deprecated(since = "0.13.0", forRemoval = true)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface RowLevelSecurity {

    /**
     * Predefined RLS policy types.
     *
     * @deprecated since 0.13.0, for removal in 1.0.0, with {@link RowLevelSecurity}. Tenant
     *         isolation is {@code @ExerisDomain(dataScope = DataScope.TENANT)}; the
     *         principal-keyed values have no kernel contract at 1.0 (kernel ADR-012 §4c.1).
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    enum Policy {
        /**
         * No RLS policy (use application-level security only).
         */
        NONE,

        /**
         * Tenant isolation - users see only their tenant's data.
         * <p>Requires {@code @TenantId} annotated field.
         */
        TENANT_ISOLATION,

        /**
         * Owner-based access - users see only records they own.
         * <p>Requires {@code ownerField} to be specified.
         */
        OWNER,

        /**
         * Team-based access - users see records owned by team members.
         * <p>Requires {@code teamField} to be specified.
         */
        TEAM,

        /**
         * Hierarchical access - managers see subordinates' records.
         * <p>Requires {@code hierarchyField} to be specified.
         */
        HIERARCHY,

        /**
         * Department-based access - users see department records.
         * <p>Requires {@code departmentField} to be specified.
         */
        DEPARTMENT,

        /**
         * Custom policy with SQL condition.
         * <p>Requires {@code condition} to be specified.
         */
        CUSTOM,

        /**
         * Combined tenant + owner isolation.
         */
        TENANT_AND_OWNER
    }

    /**
     * The RLS policy type to apply.
     *
     * @return policy type
     */
    Policy policy() default Policy.TENANT_ISOLATION;

    /**
     * Field name containing the owner user ID.
     * <p>Required when {@code policy = OWNER} or {@code policy = TENANT_AND_OWNER}.
     *
     * @return owner field name
     */
    String ownerField() default "";

    /**
     * Field name containing the team ID.
     * <p>Required when {@code policy = TEAM}.
     *
     * @return team field name
     */
    String teamField() default "";

    /**
     * Field name for hierarchical relationship (e.g., managerId).
     * <p>Required when {@code policy = HIERARCHY}.
     *
     * @return hierarchy field name
     */
    String hierarchyField() default "";

    /**
     * Maximum depth for hierarchical queries.
     * <p>Limits recursive CTE depth for performance.
     *
     * @return maximum hierarchy depth
     */
    int hierarchyDepth() default 5;

    /**
     * Field name containing department ID.
     * <p>Required when {@code policy = DEPARTMENT}.
     *
     * @return department field name
     */
    String departmentField() default "";

    /**
     * Custom SQL condition for the policy.
     * <p>Required when {@code policy = CUSTOM}.
     *
     * <p><strong>Available functions:</strong>
     * <ul>
     *   <li>{@code current_tenant_id()} - Current tenant UUID</li>
     *   <li>{@code current_user_id()} - Current user UUID</li>
     *   <li>{@code current_user_roles()} - Array of user roles</li>
     *   <li>{@code has_role(role_name)} - Check if user has role</li>
     *   <li>{@code has_permission(perm_name)} - Check if user has permission</li>
     * </ul>
     *
     * <p><strong>Example:</strong>
     * {@snippet lang="java" :
     * condition = "status = 'PUBLISHED' OR created_by = current_user_id()"
     * }
     *
     * @return SQL condition expression
     */
    String condition() default "";

    /**
     * Roles that bypass this RLS policy entirely.
     * <p>Users with these roles can see all records.
     * <p>Example: "SUPERADMIN", "DATA_EXPORT"
     *
     * <p>No application role can bypass a policy: no role reaches the database session. Which
     * callers may reach the entity is route authorization ({@code @ExerisDomain.roles} /
     * {@code permissions}, {@code @Action.roles} / {@code permissions}, {@code @RouteAccess}).
     * The bypass PostgreSQL itself knows is a database-role attribute, {@code SUPERUSER} or
     * {@code BYPASSRLS}, and the kernel refuses to boot a pool whose role carries one unless
     * {@code persistence.rlsAllowBypassRole} is set (kernel ADR-012 §4c.4).
     *
     * @return bypass role names
     */
    String[] bypassRoles() default {};

    /**
     * Roles required for read (SELECT) access.
     * <p>Empty array means all authenticated users.
     *
     * <p>A policy cannot evaluate it, because no role reaches the database session. Use route
     * authorization: {@code @ExerisDomain.roles} / {@code permissions}, {@code @Action.roles} /
     * {@code permissions}, {@code @RouteAccess}.
     *
     * @return required roles for reading
     */
    String[] readRoles() default {};

    /**
     * Roles required for write (INSERT/UPDATE/DELETE) access.
     * <p>Empty array means all authenticated users with read access.
     *
     * <p>A policy cannot evaluate it, because no role reaches the database session. Use route
     * authorization: {@code @ExerisDomain.roles} / {@code permissions}, {@code @Action.roles} /
     * {@code permissions}, {@code @RouteAccess}.
     *
     * @return required roles for writing
     */
    String[] writeRoles() default {};

    /**
     * Whether to apply policy to INSERT operations.
     * <p>When true, validates that new records match the policy.
     *
     * @return true to apply on insert
     */
    boolean applyOnInsert() default true;

    /**
     * Whether to apply policy to UPDATE operations.
     *
     * @return true to apply on update
     */
    boolean applyOnUpdate() default true;

    /**
     * Whether to apply policy to DELETE operations.
     *
     * @return true to apply on delete
     */
    boolean applyOnDelete() default true;

    /**
     * Name of the PostgreSQL policy.
     * <p>Defaults to "{table_name}_{policy_type}" if empty.
     *
     * @return policy name
     */
    String policyName() default "";

    /**
     * Whether the policy is enforced (PERMISSIVE or RESTRICTIVE).
     * <p>PERMISSIVE: Rows are visible if ANY permissive policy allows.
     * <p>RESTRICTIVE: Rows are visible only if ALL restrictive policies allow.
     *
     * @return true for restrictive policy
     */
    boolean restrictive() default false;

    /**
     * Audit log level for policy violations.
     *
     * @deprecated since 0.13.0, for removal in 1.0.0, with {@link RowLevelSecurity}. No
     *         replacement.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    enum AuditLevel {
        /** No audit logging. */
        NONE,
        /** Log only violations. */
        VIOLATIONS,
        /** Log all access attempts. */
        ALL
    }

    /**
     * Audit logging level for this policy.
     *
     * @return audit level
     */
    AuditLevel auditLevel() default AuditLevel.VIOLATIONS;
}

