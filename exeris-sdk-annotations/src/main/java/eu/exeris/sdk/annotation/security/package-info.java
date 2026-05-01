/**
 * Security annotations for the Exeris SDK.
 * <p>This package contains annotations for:
 * <ul>
 *   <li>{@link eu.exeris.sdk.annotation.security.Encrypted @Encrypted} - Field-level encryption</li>
 *   <li>{@link eu.exeris.sdk.annotation.security.RowLevelSecurity @RowLevelSecurity} - Row-level security policies</li>
 * </ul>
 *
 * <h2>Field-Level Encryption:</h2>
 * <pre>{@code
 * @Field(label = "SSN")
 * @Encrypted(pii = true, maskPattern = "XXX-XX-####")
 * private String ssn;
 * }</pre>
 *
 * <h2>Row-Level Security:</h2>
 * <pre>{@code
 * @ExerisDomain(module = "sales", path = "/orders")
 * @RowLevelSecurity(policy = Policy.TENANT_ISOLATION)
 * public class Order { }
 * }</pre>
 *
 * @author Exeris SDK Team
 * @version 1.0.0
 * @since 1.0.0
 */
package eu.exeris.sdk.annotation.security;

