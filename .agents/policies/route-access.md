# Policy: Route Access & Security Invariants

`@RouteAccess(PUBLIC | AUTHENTICATED)` (introduced in 0.12.0, ADR-072) is the sole mechanism to declare that an endpoint or route admits unauthenticated callers.

## Hard Rules

1. **Never overload empty roles or permissions to mean "public":**
   - In `@ExerisDomain` and `@Action`, an empty `roles = {}` or `permissions = {}` array means "nothing explicitly declared", defaulting to authenticated-only access.
   - Using empty arrays to imply public access causes severe security regressions by accidentally opening protected endpoints.
2. **Never add an `UNSPECIFIED` enum constant:**
   - In `@RouteAccess.Level` and `sourcemodel.ast.RouteAccess`, there are exactly two constants: `PUBLIC` and `AUTHENTICATED`.
   - The unstated/unspecified condition is represented structurally by the absence of the annotation (producing a `null` value in `DomainMetadata.routeAccess` and `ActionMetadata.routeAccess`).
3. **`PUBLIC` cannot combine with non-empty permissions:**
   - An unauthenticated (public) route has no authenticated principal bound to the execution context.
   - A route marked `PUBLIC` that simultaneously specifies required `permissions` is logically contradictory and must be rejected at compile time.

## References

- ADR-072 (amended), obligations 9–15.
- Javadoc of `eu.exeris.sdk.annotation.security.RouteAccess`.
