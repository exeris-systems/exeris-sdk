package eu.exeris.sdk.annotation.security;

import java.lang.annotation.*;

/**
 * Marks a field for field-level encryption at rest.
 *
 * <p><strong>Status: DEPRECATED</strong>, for removal in 1.0.0. No processor extracts it, no AST
 * record carries it and no generator reads it, so the field is stored as plaintext exactly as if
 * the annotation were absent. Nothing in the toolchain will ever honour it at 1.0: kernel ADR-012
 * §4c.1 rules field-level encryption at rest "outside kernel scope at 1.0, in every tier". The
 * kernel's cryptography is TLS transport, and "Tooling must not invent a scheme either, so
 * {@code @Encrypted} has no kernel target at 1.0." The same ruling leaves an Enterprise-tier
 * capability open after 1.0 "without a commitment": it returns, in any tier, only through its
 * own ADR, which then defines its own key model rather than inheriting this annotation's.
 *
 * <p>None of the attributes has a replacement in the SDK:
 * <ul>
 *   <li>{@link #algorithm()}, {@link #keyId()}, {@link #previousKeyIds()},
 *       {@link #deterministic()}, {@link #searchable()}, {@link #indexColumn()} describe an
 *       encryption scheme, which is the thing no kernel tier and no generator defines.
 *       Encryption at rest is the application's: it encrypts the value before it reaches the
 *       generated repository, or the database encrypts the storage.</li>
 *   <li>{@link #pii()}, {@link #maskPattern()}, {@link #maskInLogs()}, {@link #maskInList()}
 *       are not encryption. Their field-level declaration sites are
 *       {@link eu.exeris.sdk.annotation.Field#sensitive() @Field.sensitive} and
 *       {@link eu.exeris.sdk.annotation.Field#maskPattern() @Field.maskPattern}, which no
 *       processor extracts either.</li>
 *   <li>{@link #viewRoles()} is authorization: who may read the entity is
 *       {@code @ExerisDomain.roles} / {@code permissions}, {@code @Action.roles} /
 *       {@code permissions} and {@code @RouteAccess}.</li>
 * </ul>
 *
 * @since 0.1
 * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. Field-level encryption
 *         at rest is outside kernel scope at 1.0 in every tier (kernel ADR-012 §4c.1), so no
 *         generator can honour it; encrypt in the application or in the database. Masking and
 *         PII flags are declared on {@code @Field.sensitive} / {@code @Field.maskPattern}. See
 *         {@code MIGRATION.md}.
 * @see RowLevelSecurity
 */
@Deprecated(since = "0.13.0", forRemoval = true)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface Encrypted {

    /**
     * Encryption algorithm to use.
     *
     * @deprecated since 0.13.0, for removal in 1.0.0, with {@link Encrypted}. No replacement:
     *         no kernel tier defines an encryption-at-rest scheme at 1.0 (kernel ADR-012 §4c.1).
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
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

