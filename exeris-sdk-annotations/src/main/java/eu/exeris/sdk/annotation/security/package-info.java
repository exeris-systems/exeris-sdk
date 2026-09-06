/**
 * Security annotations for the Exeris SDK.
 * <p>This package contains annotations for:
 * <ul>
 *   <li>{@link eu.exeris.sdk.annotation.security.Encrypted @Encrypted} - Field-level encryption</li>
 *   <li>{@link eu.exeris.sdk.annotation.security.RowLevelSecurity @RowLevelSecurity} - Row-level security policies</li>
 * </ul>
 *
 * <h2>Field-Level Encryption:</h2>
 * {@snippet lang="java" :
 * @Field(label = "SSN")
 * @Encrypted(pii = true, maskPattern = "XXX-XX-####")
 * private String ssn;
 * }
 *
 * <h2>Row-Level Security:</h2>
 * {@snippet lang="java" :
 * @ExerisDomain(module = "sales", path = "/orders")
 * @RowLevelSecurity(policy = Policy.TENANT_ISOLATION)
 * public class Order { }
 * }
 *
 * <h2>Open-Core status — RESERVED, not yet consumed</h2>
 * <p>Both annotations are declared-but-unconsumed: their javadocs promise
 * generated encryption / RLS policies, but no Open-Core generator implements
 * them today — the {@code exeris-tooling} processor does not extract either
 * marker. The <em>live</em> tenancy path is
 * {@code @ExerisDomain(dataScope = DataScope.TENANT)} — which emits the tenant
 * column, the RLS policy and the query filter — plus the system-field override
 * attributes / canonical accessor names, not {@code @RowLevelSecurity}.
 * ({@code tenantScoped = true} is the deprecated spelling of the same tier; see
 * ADR-059.) They ship as a reserved surface until a consumer lands.
 *
 * @since 0.1
 */
package eu.exeris.sdk.annotation.security;

