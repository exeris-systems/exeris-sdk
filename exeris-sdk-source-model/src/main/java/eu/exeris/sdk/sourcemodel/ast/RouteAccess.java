package eu.exeris.sdk.sourcemodel.ast;

/**
 * What a generated route demands of its caller — the identity half of the kernel's
 * route-authorization decision (kernel ADR-061).
 *
 * <p>AST-owned (mirroring {@code @RouteAccess.Level} on the annotation side without
 * depending on it), null-tolerated on the wire — an absent value means the author
 * declared nothing and the generated policy's default decides.
 *
 * <p>There is deliberately no {@code UNSPECIFIED} constant, for the same reason
 * {@link DataScope} states: the AST expresses "not declared" as an absent field. On
 * the annotation side the sentinel is unnecessary too, because the whole annotation
 * can be absent — this is the one facet where both sides express the third state
 * structurally rather than by constant.
 *
 * <p>The scope-bearing kinds the kernel also offers ({@code ANY_SCOPE} /
 * {@code ALL_SCOPES}) are absent here because {@code permissions} on
 * {@link DomainMetadata} and {@link ActionMetadata} already carries them. One fact,
 * one carrier — the ADR-054 discipline.
 *
 * <p>The SDK encodes <em>author intent</em> only. Compiling that intent into a
 * {@code HttpRoutePolicy} — the URL-to-requirement table, its unmatched answer, and
 * how a generated table composes with routes the consumer wrote by hand — is
 * {@code exeris-tooling}'s transcription and is not represented here. See ADR-072.
 *
 * @since 0.12.0
 */
public enum RouteAccess {

    /** No identity required; the handler runs with no principal bound. */
    PUBLIC,

    /** A verified identity is required, and nothing further. */
    AUTHENTICATED
}
