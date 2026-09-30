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
   - A route marked `PUBLIC` that simultaneously specifies required `permissions` is logically contradictory. The downstream `exeris-tooling` processor refuses the pair at the declaration site — on the entity, on an action's own `@RouteAccess`, and on an action that inherits the entity's `PUBLIC` while declaring `@Action.permissions`.
4. **Open-Core status — RESERVED, validated but not compiled into a route:**
   - Declared shape, not yet an enforced route. Downstream `exeris-tooling` validates `@RouteAccess` (rule 3) but does not extract it into `DomainMetadata.routeAccess` / `ActionMetadata.routeAccess`, and no generator emits a route policy from it. The `-io` reader does not read it.
   - Excluded from the 1.0.0 freeze while the kernel holds route authorization at tier `preview` (ADR-072).

## References

- ADR-072 (amended), obligations 9–15.
- Javadoc of `eu.exeris.sdk.annotation.security.RouteAccess`.
