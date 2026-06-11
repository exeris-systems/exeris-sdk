/*
 * budgetHQ corpus member — ported from
 * budgetHQ/backend/identity-service/src/main/java/app/budgethq/identity/domain/User.java
 * (JPA original; Entity-First port per ADR-003). Representative subset of the
 * real ~25-field surface: credential/MFA lockout counters, the IdentityProvider
 * enum (see IdentityProvider.java in this corpus), profile preferences. The
 * lock action mirrors identity-service's admin lockout endpoint.
 */
package app.budgethq.identity.domain;

import eu.exeris.sdk.annotation.Action;
import eu.exeris.sdk.annotation.ActionParam;
import eu.exeris.sdk.annotation.ExerisDomain;
import eu.exeris.sdk.annotation.Field;
import eu.exeris.sdk.annotation.Validation;

import java.time.Instant;
import java.util.UUID;

@ExerisDomain(name = "User", module = "identity", audited = true, sensitive = true,
              description = "Identity-service account aggregate")
public class User {

    private UUID id;

    @Field(label = "Email", required = true, searchable = true, sortable = true)
    @Validation(pattern = "^[^@\\s]+@[^@\\s]+$", maxLength = 320)
    private String email;

    /** BCrypt hash — never leaves the service. */
    @Field(label = "Password hash", sensitive = true, inList = false, inDetail = false)
    private String passwordHash;

    @Field(label = "Email verified", filterable = true, readOnly = true)
    private boolean emailVerified;

    /** Lockout counter — reset on successful login. */
    @Field(label = "Failed login attempts", readOnly = true, inCreate = false, inUpdate = false)
    private int failedLoginAttempts;

    @Field(label = "Locked until", readOnly = true, inCreate = false, inUpdate = false)
    private Instant lockedUntil;

    @Field(label = "MFA enabled", filterable = true)
    private boolean mfaEnabled;

    /** Origin IdP — LOCAL for password accounts, else the external provider. */
    @Field(label = "Identity provider", filterable = true, readOnly = true)
    private IdentityProvider idpOrigin;

    @Field(label = "Display name", searchable = true)
    @Validation(maxLength = 128)
    private String displayName;

    /** BCP 47 tag, e.g. "pl-PL". */
    @Field(label = "Locale")
    @Validation(maxLength = 35)
    private String locale;

    /** ISO 4217, same convention as the sync-service money fields. */
    @Field(label = "Default currency")
    @Validation(pattern = "[A-Z]{3}")
    private String defaultCurrency;

    @Field(label = "Created at", readOnly = true, inCreate = false, inUpdate = false)
    private Instant createdAt;

    @Action(name = "lock", label = "Lock account", httpMethod = "POST", path = "/lock")
    public void lock(
            @ActionParam(label = "Reason", required = true) String reason,
            @ActionParam(label = "Until", required = false) Instant until) {
    }

    @Action(name = "forceLogout", label = "Force logout", path = "/force-logout", async = true)
    public void forceLogout() {
    }
}
