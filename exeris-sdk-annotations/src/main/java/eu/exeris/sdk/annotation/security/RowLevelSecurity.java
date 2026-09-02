package eu.exeris.sdk.annotation.security;

import java.lang.annotation.*;

/**
 * Configures Row-Level Security (RLS) policies for a domain entity.
 * <p>RLS policies are implemented using PostgreSQL native RLS features,
 * ensuring data isolation at the database level (defense in depth).
 *
 * <h2>Basic Tenant Isolation:</h2>
 * <pre>{@code
 * @ExerisDomain(module = "sales", path = "/orders", tenantScoped = true)
 * @RowLevelSecurity(policy = Policy.TENANT_ISOLATION)
 * public class Order {
 *     @TenantId
 *     private UUID organizationId;
 *     // ... other fields
 * }
 * }</pre>
 *
 * <h2>Owner-Based Access:</h2>
 * <pre>{@code
 * @ExerisDomain(module = "crm", path = "/contacts")
 * @RowLevelSecurity(
 *     policy = Policy.OWNER,
 *     ownerField = "assignedUserId"
 * )
 * public class Contact {
 *     private UUID assignedUserId;
 *     // ... other fields
 * }
 * }</pre>
 *
 * <h2>Role-Based Access:</h2>
 * <pre>{@code
 * @ExerisDomain(module = "hr", path = "/salaries")
 * @RowLevelSecurity(
 *     policy = Policy.CUSTOM,
 *     readRoles = {"HR_MANAGER", "PAYROLL"},
 *     writeRoles = {"PAYROLL"},
 *     condition = "department_id IN (SELECT department_id FROM user_departments WHERE user_id = current_user_id())"
 * )
 * public class Salary { }
 * }</pre>
 *
 * <h2>Hierarchical Access (Manager sees team):</h2>
 * <pre>{@code
 * @RowLevelSecurity(
 *     policy = Policy.HIERARCHY,
 *     hierarchyField = "managerId",
 *     hierarchyDepth = 3  // Up to 3 levels deep
 * )
 * public class Employee { }
 * }</pre>
 *
 * <h2>Target design — the policy this would emit:</h2>
 * <pre>{@code
 * -- Enable RLS on table
 * ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
 *
 * -- Tenant isolation policy
 * CREATE POLICY orders_tenant_isolation ON orders
 *     USING (organization_id = current_setting('app.tenant_id')::uuid);
 *
 * -- Owner-based policy
 * CREATE POLICY contacts_owner_access ON contacts
 *     USING (assigned_user_id = current_setting('app.user_id')::uuid);
 * }</pre>
 *
 * <p><strong>Status: RESERVED</strong> — the {@code exeris-tooling} processor does not
 * extract this marker, so writing it changes nothing in the emitted output. See the package
 * javadoc for the live path.
 *
 * @author Exeris SDK Team
 * @version 0.1.0
 * @since 0.1.0
 * @see Encrypted
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface RowLevelSecurity {

    /**
     * Predefined RLS policy types.
     */
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
     * <pre>{@code
     * condition = "status = 'PUBLISHED' OR created_by = current_user_id()"
     * }</pre>
     *
     * @return SQL condition expression
     */
    String condition() default "";

    /**
     * Roles that bypass this RLS policy entirely.
     * <p>Users with these roles can see all records.
     * <p>Example: "SUPERADMIN", "DATA_EXPORT"
     *
     * @return bypass role names
     */
    String[] bypassRoles() default {};

    /**
     * Roles required for read (SELECT) access.
     * <p>Empty array means all authenticated users.
     *
     * @return required roles for reading
     */
    String[] readRoles() default {};

    /**
     * Roles required for write (INSERT/UPDATE/DELETE) access.
     * <p>Empty array means all authenticated users with read access.
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
     */
    enum AuditLevel {
        /** No audit logging */
        NONE,
        /** Log only violations */
        VIOLATIONS,
        /** Log all access attempts */
        ALL
    }

    /**
     * Audit logging level for this policy.
     *
     * @return audit level
     */
    AuditLevel auditLevel() default AuditLevel.VIOLATIONS;
}

