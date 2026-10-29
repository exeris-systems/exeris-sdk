package eu.exeris.sdk.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares whether a generated route admits unauthenticated callers.
 *
 * <p>This is the Entity-First expression of the kernel's route-authorization seam
 * (kernel ADR-061, {@code eu.exeris.kernel.spi.http.HttpRoutePolicy} /
 * {@code RouteRequirement}). It is a separate annotation because the other
 * security-shaped attributes cannot say that a route is public:
 * {@link ExerisDomain#permissions()} and {@link Action#permissions()}, like the
 * deprecated, never-enforced {@code roles} beside them, read an empty value as
 * "nothing declared". The empty case cannot also mean "public" without colliding
 * with that meaning.
 *
 * <h2>Basic usage</h2>
 * {@snippet lang="java" :
 * @Action(name = "login", label = "Log in", httpMethod = "POST", path = "/login")
 * @RouteAccess(RouteAccess.Level.PUBLIC)
 * public AuthToken login(@ActionParam(name = "credentials") Credentials credentials) { ... }
 * }
 *
 * <h2>Absence is the third state — deliberately</h2>
 * <p>There is no {@code UNSPECIFIED} constant. An element that carries no
 * {@code @RouteAccess} has declared nothing, and the generated policy's default
 * decides; an element that carries one has decided explicitly. Expressing "not
 * declared" by <em>not declaring</em> is the same reasoning
 * {@code sourcemodel.ast.DataScope} records for omitting {@code UNSPECIFIED} on the
 * AST side, applied one level up: an annotation attribute needs a sentinel because
 * it cannot default to absent, but an annotation itself can simply be absent.
 *
 * <p>This matters more than it looks. The whole reason this annotation exists is
 * that "unspecified" and "public" were indistinguishable; re-introducing a sentinel
 * constant would rebuild the same ambiguity one level down.
 *
 * <h2>Nearest declaration wins</h2>
 * <p>On a {@code TYPE} it covers the routes generated for that entity; on a
 * {@code METHOD} beside an {@link Action} it covers that action's route and
 * overrides the entity-level declaration. An action is the granularity that
 * matters in practice — a sign-in or registration endpoint is public while the rest
 * of its entity is not — and an entity-level declaration without a per-action
 * override would force the coarse choice.
 *
 * <h2>Scopes, not roles — and what this does not duplicate</h2>
 * <p>The kernel's edge decides on <strong>scopes</strong>:
 * {@code RouteRequirement} offers {@code permitAll()}, {@code authenticated()} and
 * the scope-bearing shapes, and declares no role kind at all, because roles resolve
 * at the <em>method</em> level through {@code @RequiresRole} against a build-time
 * {@code methodId} the kernel cannot derive from a URL. {@code permissions} is
 * therefore the half of the existing surface that maps onto a named scope. This
 * annotation supplies the half {@code permissions} cannot express — whether a
 * verified identity is required <em>at all</em> — and duplicates none of it. Do not
 * read {@link Level#AUTHENTICATED} as a place to restate permissions.
 *
 * <h2>One combination the platform refuses</h2>
 * <p>{@link Level#PUBLIC} together with a non-empty {@code permissions} on the same
 * element is contradictory, not merely redundant. A permit-all route runs its
 * handler with <strong>no {@code PrincipalContext} bound</strong> — the kernel binds
 * identity only on the path that demands it, so a scope check on a public route can
 * never be satisfied rather than merely passing vacuously. The {@code exeris-tooling}
 * processor refuses the pair at the declaration site, at the level each route gets:
 * the entity's own {@code @RouteAccess} against {@code @ExerisDomain.permissions}, an
 * action's own {@code @RouteAccess} against {@code @Action.permissions}, and an action
 * without one — which inherits the entity's level — against its
 * {@code @Action.permissions}.
 *
 * <h2>Open-Core status — RESERVED, validated but not compiled into a route</h2>
 * <p>Declared shape, not yet an enforced route. The kernel provides
 * {@code HttpRoutePolicy} / {@code RouteRequirement} with
 * {@code AbstractHttpRoutePolicyTck}. The {@code exeris-sdk-source-model-io} reader
 * reads it into {@code DomainMetadata.routeAccess} and {@code ActionMetadata.routeAccess},
 * {@code PUBLIC} included, each element carrying only what it declares. The
 * {@code exeris-tooling} processor validates the declaration — the refusal above — and
 * extracts it, compiling the URL-to-policy table, in the release that pins this reader
 * (exeris-tooling ADR-105); until then every generated route is registered as if the
 * annotation were absent. The kernel holds route authorization at tier
 * {@code stable} in its {@code docs/stability-matrix.md} (kernel ADR-100), so this
 * surface is part of the 1.0.0 freeze: frozen as declared, with the route-policy
 * emission arriving additively. See {@code docs/adr/ADR-072} and
 * {@code ROADMAP.md}.
 *
 * @since 0.12
 * @see ExerisDomain#permissions()
 * @see Action#permissions()
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface RouteAccess {

    /**
     * What the annotated route demands of its caller.
     *
     * <p>Mandatory: the annotation exists to make a statement, and a default would
     * make a route nobody declared indistinguishable from one declared on purpose.
     *
     * @return the declared access level
     */
    Level value();

    /**
     * The two admission decisions a route can state, mirroring the kernel's
     * {@code RouteRequirement} kinds that identity — rather than scope — decides.
     *
     * <p>The scope-bearing kinds ({@code ANY_SCOPE} / {@code ALL_SCOPES}) are
     * deliberately absent: {@code permissions} already declares those, and a second
     * declaration site for one fact is the shape ADR-054 closed.
     *
     * @since 0.12
     */
    enum Level {

        /**
         * No identity required — the route admits unauthenticated callers, and its
         * handler runs with no principal bound ({@code RouteRequirement.permitAll()}).
         */
        PUBLIC,

        /**
         * A verified identity is required, and nothing further
         * ({@code RouteRequirement.authenticated()}).
         *
         * <p>Worth stating explicitly: it distinguishes a route whose author decided
         * from one whose author was silent, and it survives a change to the default. A
         * route that declares nothing is decided by the generated application's
         * {@code unmatchedRoutes()}, permit-all unless the application overrides it; an
         * empty {@code permissions} never declares a route public, only {@code PUBLIC} does.
         */
        AUTHENTICATED
    }
}
