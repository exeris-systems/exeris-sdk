# Policy: Route Access & Security Invariants

`@RouteAccess(PUBLIC | AUTHENTICATED)` (introduced in 0.12.0, ADR-072) is the sole mechanism to declare that an endpoint or route admits unauthenticated callers.

## Hard Rules

1. **Never overload empty roles or permissions to mean "public":**
   - In `@ExerisDomain` and `@Action`, an empty `roles = {}` or `permissions = {}` array means "nothing explicitly declared". It never declares a route public: the only declaration of a public route is an explicit `@RouteAccess(PUBLIC)`. No SDK artifact, reader or document may infer public access from emptiness.
   - `roles` is not route authorization in either direction. Nothing extracts or enforces it, empty or not, and it is deprecated for removal in 1.0.0. Route authorization is `permissions` plus `@RouteAccess`; no SDK artifact or document may present `roles` as a protection or as a replacement for another attribute.
   - Using empty arrays to imply public access causes severe security regressions by accidentally opening protected endpoints.
   - What an undeclared route gets at runtime is not an SDK declaration. It is the generated application's `unmatchedRoutes()`, which defaults to `permitAll()` and is documented as fail-open; an application that wants undeclared routes authenticated-only overrides it (exeris-tooling ADR-105 obligations 7–9, ACCEPTED). The SDK states that fallback and does not call `AUTHENTICATED` "the generated default".
2. **Never add an `UNSPECIFIED` enum constant:**
   - In `@RouteAccess.Level` and `sourcemodel.ast.RouteAccess`, there are exactly two constants: `PUBLIC` and `AUTHENTICATED`.
   - The unstated/unspecified condition is represented structurally by the absence of the annotation (producing a `null` value in `DomainMetadata.routeAccess` and `ActionMetadata.routeAccess`).
3. **`PUBLIC` cannot combine with non-empty permissions:**
   - An unauthenticated (public) route has no authenticated principal bound to the execution context.
   - A route marked `PUBLIC` that simultaneously specifies required `permissions` is logically contradictory. The downstream `exeris-tooling` processor refuses the pair at the declaration site — on the entity, on an action's own `@RouteAccess`, and on an action that inherits the entity's `PUBLIC` while declaring `@Action.permissions`.
4. **Each element carries only what it declares:**
   - The `-io` reader reads `@RouteAccess` into `DomainMetadata.routeAccess` / `ActionMetadata.routeAccess`, `PUBLIC` included, and `permissions` in declaration order. An action declaring neither keeps a `null` `routeAccess` and empty `permissions`; the nearest-declaration rule is the route-policy generator's, applied from those values (exeris-tooling ADR-105 obligation 12). Never resolve inheritance into the metadata.
   - Every change to what the reader writes for these four components lands with a case in `exeris-sdk-tck`'s `ROUTE_ACCESS` facet, the parity suite a processor binding runs against the reader (ADR-042).
5. **Open-Core status — RESERVED, validated but not compiled into a route:**
   - Declared shape, not yet an enforced route. Downstream `exeris-tooling` validates `@RouteAccess` (rule 3); it extracts the declarations and emits the route policy in the release that pins the reader of rule 4 (ADR-105 obligation 26). Until then every generated route is registered as if the annotation were absent.
   - Part of the 1.0.0 freeze: the kernel holds route authorization at tier `stable` (kernel ADR-100), so `@RouteAccess`, `Level`, the AST `RouteAccess` and both `routeAccess` components are frozen as declared and may only grow additively in 1.x (ADR-072 obligation 29).

## References

- ADR-072 (amended), obligations 9–15 and 29.
- exeris-tooling ADR-105 (generated route-policy emission), obligations 10, 12 and 26.
- Javadoc of `eu.exeris.sdk.annotation.RouteAccess`.
