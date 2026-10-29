package eu.exeris.sdk.tck;

import eu.exeris.sdk.sourcemodel.ast.ActionMetadata;
import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.RouteAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What the corpus declares for route access, element by element, and the comparison the reader and
 * producer suites run against it.
 *
 * <p>Each element carries only what it declares. An action without a declaration of its own
 * carries a {@code null} {@code routeAccess} and no {@code permissions}; the generator that
 * compiles the route policy applies the nearest-declaration rule from these values.
 */
final class CorpusRouteAccess {

    /**
     * One element's declaration.
     *
     * @param path the entity, or {@code entity/actions/name}, the declaration belongs to
     * @param routeAccess the declared level, {@code null} when none is declared
     * @param permissions the declared permissions, in declaration order
     */
    record Declared(String path, RouteAccess routeAccess, List<String> permissions) {
    }

    /** Every declaration in the corpus that carries route access or permissions, and every inheriting action. */
    static final List<Declared> DECLARATIONS = List.of(
            new Declared(TckCorpus.ORDER, RouteAccess.AUTHENTICATED, List.of("order:read", "order:write")),
            new Declared(TckCorpus.ORDER + "/actions/submit", null, List.of()),
            new Declared(TckCorpus.ORDER + "/actions/approve", null, List.of("order:approve")),
            new Declared(TckCorpus.ORDER + "/actions/track", RouteAccess.PUBLIC, List.of()),
            new Declared(TckCorpus.CUSTOMER, null, List.of("customer:read")),
            new Declared(TckCorpus.PRODUCT, RouteAccess.PUBLIC, List.of()),
            new Declared(TckCorpus.PRODUCT + "/actions/reprice", RouteAccess.AUTHENTICATED, List.of("product:reprice")));

    private CorpusRouteAccess() {
    }

    /**
     * Compares the metadata produced or read for one corpus entity with what the corpus declares.
     *
     * @param metadata the entity's metadata
     * @return one line per disagreement, empty when the metadata matches
     */
    static List<String> gaps(DomainMetadata metadata) {
        List<String> gaps = new ArrayList<>();
        String entity = metadata.entityName();
        for (Declared declared : DECLARATIONS) {
            if (declared.path().equals(entity)) {
                check(gaps, declared, metadata.routeAccess(), metadata.permissions());
            } else if (declared.path().startsWith(entity + "/actions/")) {
                String action = declared.path().substring((entity + "/actions/").length());
                ActionMetadata found = metadata.actions() == null ? null : metadata.actions().stream()
                        .filter(a -> action.equals(a.name()))
                        .findFirst()
                        .orElse(null);
                if (found == null) {
                    gaps.add(declared.path() + " — no such action");
                } else {
                    check(gaps, declared, found.routeAccess(), found.permissions());
                }
            }
        }
        return gaps;
    }

    private static void check(List<String> gaps, Declared declared, RouteAccess routeAccess,
                              List<String> permissions) {
        if (!Objects.equals(declared.routeAccess(), routeAccess)) {
            gaps.add(declared.path() + "/routeAccess — declared " + render(declared.routeAccess())
                    + ", got " + render(routeAccess));
        }
        List<String> got = permissions == null ? List.of() : permissions;
        if (!declared.permissions().equals(got)) {
            gaps.add(declared.path() + "/permissions — declared " + declared.permissions() + ", got " + got);
        }
    }

    private static String render(Object value) {
        return value == null ? "<absent>" : String.valueOf(value);
    }
}
