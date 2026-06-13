/*
 * budgetHQ corpus member — ported from identity-service's IdentityProvider
 * enum (the User.idpOrigin discriminator). One of the few enums the anti-enum
 * convention (DEC-22) permits: the value set is owned by budgetHQ, not by an
 * external aggregator.
 */
package app.budgethq.identity.domain;

public enum IdentityProvider {
    LOCAL,
    GOOGLE,
    MICROSOFT
}
