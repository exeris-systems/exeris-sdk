package eu.exeris.sdk.annotation.security;

import java.lang.annotation.*;

/**
 * Marks a field for encryption at rest (Field-Level Encryption).
 * <p>Encrypted fields are automatically encrypted before persisting to database
 * and decrypted when reading. Uses AES-256-GCM by default.
 *
 * <h2>Basic Usage:</h2>
 * {@snippet lang="java" :
 * @ExerisDomain(module = "crm", path = "/customers")
 * public class Customer {
 *
 *     @Field(label = "Name")
 *     private String name;
 *
 *     @Field(label = "SSN")
 *     @Encrypted
 *     private String ssn;
 *
 *     @Field(label = "Credit Card")
 *     @Encrypted(algorithm = Algorithm.AES_256_GCM, keyId = "payment-key")
 *     private String creditCardNumber;
 * }
 * }
 *
 * <h2>Searchable Encrypted Fields:</h2>
 * {@snippet lang="java" :
 * @Field(label = "Email")
 * @Encrypted(
 *     deterministic = true,  // Allows equality search
 *     keyId = "email-key"
 * )
 * private String email;
 * }
 *
 * <h2>PII Data with Masking:</h2>
 * {@snippet lang="java" :
 * @Field(label = "Phone Number")
 * @Encrypted(
 *     pii = true,
 *     maskPattern = "XXX-XXX-####",  // Show only last 4 digits
 *     maskInLogs = true
 * )
 * private String phoneNumber;
 * }
 *
 * <h2>Key Rotation:</h2>
 * {@snippet lang="java" :
 * @Encrypted(
 *     keyId = "customer-data-key-v2",
 *     previousKeyIds = {"customer-data-key-v1"}  // For migration
 * )
 * private String sensitiveData;
 * }
 *
 * <h2>Target design — the converter this would emit:</h2>
 * {@snippet lang="java" :
 * @Convert(converter = EncryptedStringConverter.class)
 * @Column(name = "ssn", columnDefinition = "bytea")
 * private String ssn;
 * }
 *
 * <h2>Security Considerations:</h2>
 * <ul>
 *   <li>Keys are managed via Vault/AWS KMS/Azure Key Vault</li>
 *   <li>Each tenant can have separate encryption keys (multi-tenant isolation)</li>
 *   <li>Encrypted fields cannot be used in ORDER BY or LIKE queries</li>
 *   <li>Deterministic encryption allows equality search but is less secure</li>
 * </ul>
 *
 * <p><strong>Status: RESERVED</strong> — the {@code exeris-tooling} processor does not
 * extract this marker, so writing it changes nothing in the emitted output. See the package
 * javadoc for the live path.
 *
 * @since 0.1
 * @see RowLevelSecurity
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface Encrypted {

    /**
     * Encryption algorithm to use.
     */
    enum Algorithm {
        /** AES-256 with Galois/Counter Mode (default, recommended). */
        AES_256_GCM,
        /** AES-256 with CBC mode. */
        AES_256_CBC,
        /** ChaCha20-Poly1305 (alternative to AES). */
        CHACHA20_POLY1305,
        /** RSA-OAEP for asymmetric encryption. */
        RSA_OAEP
    }

    /**
     * Encryption algorithm.
     * <p>Default: AES-256-GCM (authenticated encryption).
     *
     * @return encryption algorithm
     */
    Algorithm algorithm() default Algorithm.AES_256_GCM;

    /**
     * Key identifier in the key management system.
     * <p>If empty, uses the default tenant key.
     *
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>"customer-pii-key"</li>
     *   <li>"payment-data-key"</li>
     *   <li>"vault:secret/data/keys/customer"</li>
     *   <li>"aws-kms:alias/customer-key"</li>
     * </ul>
     *
     * @return key identifier
     */
    String keyId() default "";

    /**
     * Previous key IDs for key rotation support.
     * <p>During decryption, if current key fails, previous keys are tried.
     * Useful during key rotation migrations.
     *
     * @return array of previous key identifiers
     */
    String[] previousKeyIds() default {};

    /**
     * Whether to use deterministic encryption.
     * <p>Deterministic encryption:
     * <ul>
     *   <li>Produces same ciphertext for same plaintext + key</li>
     *   <li>Allows equality searches (WHERE encrypted_field = ?)</li>
     *   <li>Less secure than randomized encryption</li>
     *   <li>Use only when search functionality is required</li>
     * </ul>
     *
     * @return true for deterministic encryption
     */
    boolean deterministic() default false;

    /**
     * Whether this field contains Personally Identifiable Information (PII).
     * <p>When true:
     * <ul>
     *   <li>Additional audit logging for access</li>
     *   <li>Automatic masking in logs and error messages</li>
     *   <li>GDPR/CCPA compliance controls apply</li>
     *   <li>Right to erasure (deletion) support</li>
     * </ul>
     *
     * @return true if field contains PII
     */
    boolean pii() default false;

    /**
     * Mask pattern for display purposes.
     * <p>Use '#' for visible characters, 'X' for masked.
     *
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>"XXX-XX-####" - SSN showing last 4</li>
     *   <li>"****-****-****-####" - Credit card showing last 4</li>
     *   <li>"****@example.com" - Email domain visible</li>
     * </ul>
     *
     * @return mask pattern
     */
    String maskPattern() default "";

    /**
     * Whether to mask value in application logs.
     * <p>When true, value is replaced with "[ENCRYPTED]" in logs.
     *
     * @return true to mask in logs
     */
    boolean maskInLogs() default true;

    /**
     * Whether to mask value in API responses (except detail view).
     * <p>When true, list endpoints return masked values.
     *
     * @return true to mask in lists
     */
    boolean maskInList() default false;

    /**
     * Roles required to view the decrypted value.
     * <p>If user lacks these roles, they see the masked value.
     *
     * @return required roles for decryption
     */
    String[] viewRoles() default {};

    /**
     * Whether encrypted data is searchable via blind index.
     * <p>Uses HMAC-based blind index for search without exposing plaintext.
     * More secure than deterministic encryption but slightly slower.
     *
     * @return true to enable blind index search
     */
    boolean searchable() default false;

    /**
     * Column name for the blind index (if searchable).
     * <p>Defaults to "{fieldName}_idx" if empty.
     *
     * @return blind index column name
     */
    String indexColumn() default "";
}

