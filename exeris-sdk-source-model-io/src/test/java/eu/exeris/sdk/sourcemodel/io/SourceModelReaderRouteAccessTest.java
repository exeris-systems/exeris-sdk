package eu.exeris.sdk.sourcemodel.io;

import eu.exeris.sdk.sourcemodel.ast.ActionMetadata;
import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.RouteAccess;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @RouteAccess} and {@code permissions}, read as each element declares them
 * (exeris-tooling ADR-105, ADR-042).
 */
@DisplayName("SourceModelReader reads route access and permissions")
class SourceModelReaderRouteAccessTest {

    private final SourceModelReader reader = new SourceModelReader();

    private DomainMetadata read(String source) {
        return reader.read(source).orElseThrow();
    }

    private static ActionMetadata action(DomainMetadata domain, String name) {
        return domain.actions().stream()
                .filter(a -> a.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no action " + name));
    }

    private static final String ORDER = """
            package x;
            import eu.exeris.sdk.annotation.Action;
            import eu.exeris.sdk.annotation.ExerisDomain;
            import eu.exeris.sdk.annotation.RouteAccess;
            @ExerisDomain(permissions = {"order:read", "order:write"})
            @RouteAccess(RouteAccess.Level.AUTHENTICATED)
            public class Order {
                private String reference;
                @Action(name = "submit", label = "Submit")
                public void submit() {}
                @Action(name = "approve", label = "Approve", permissions = {"order:approve"})
                public void approve() {}
                @Action(name = "track", label = "Track", httpMethod = "GET")
                @RouteAccess(RouteAccess.Level.PUBLIC)
                public void track() {}
            }
            """;

    @Test
    @DisplayName("the entity's @RouteAccess and permissions land on DomainMetadata, in declaration order")
    void entityDeclarationIsRead() {
        DomainMetadata order = read(ORDER);
        assertThat(order.routeAccess()).isEqualTo(RouteAccess.AUTHENTICATED);
        assertThat(order.permissions()).containsExactly("order:read", "order:write");
    }

    @Test
    @DisplayName("an action's own permissions replace nothing on the entity and carry no route access")
    void actionPermissionsAreTheActionsOwn() {
        ActionMetadata approve = action(read(ORDER), "approve");
        assertThat(approve.permissions()).containsExactly("order:approve");
        assertThat(approve.routeAccess()).isNull();
    }

    @Test
    @DisplayName("an action's @RouteAccess(PUBLIC) is carried, not skipped as a default")
    void actionPublicIsCarried() {
        ActionMetadata track = action(read(ORDER), "track");
        assertThat(track.routeAccess()).isEqualTo(RouteAccess.PUBLIC);
        assertThat(track.permissions()).isEmpty();
    }

    @Test
    @DisplayName("an action declaring nothing inherits at generation time, so its metadata stays empty")
    void inheritedActionCarriesNothing() {
        ActionMetadata submit = action(read(ORDER), "submit");
        assertThat(submit.routeAccess()).isNull();
        assertThat(submit.permissions()).isEmpty();
    }

    @Test
    @DisplayName("PUBLIC reaches the wire on both carriers")
    void publicReachesTheWire() {
        DomainMetadata domain = read("""
                package x;
                import eu.exeris.sdk.annotation.Action;
                import eu.exeris.sdk.annotation.ExerisDomain;
                import eu.exeris.sdk.annotation.RouteAccess;
                @ExerisDomain
                @RouteAccess(RouteAccess.Level.PUBLIC)
                public class Product {
                    @Action(name = "view", label = "View")
                    @RouteAccess(RouteAccess.Level.PUBLIC)
                    public void view() {}
                }
                """);
        JsonNode tree = JsonMapper.builder().build().valueToTree(domain);
        assertThat(tree.path("routeAccess").asString("")).isEqualTo("PUBLIC");
        assertThat(tree.path("actions").get(0).path("routeAccess").asString("")).isEqualTo("PUBLIC");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "@RouteAccess(RouteAccess.Level.PUBLIC)",
            "@RouteAccess(Level.PUBLIC)",
            "@RouteAccess(PUBLIC)",
            "@RouteAccess(value = RouteAccess.Level.PUBLIC)",
            "@eu.exeris.sdk.annotation.RouteAccess(eu.exeris.sdk.annotation.RouteAccess.Level.PUBLIC)"})
    @DisplayName("every spelling of the level is read")
    void everySpellingIsRead(String declaration) {
        DomainMetadata domain = read("""
                package x;
                import eu.exeris.sdk.annotation.ExerisDomain;
                import eu.exeris.sdk.annotation.RouteAccess;
                import eu.exeris.sdk.annotation.RouteAccess.Level;
                import static eu.exeris.sdk.annotation.RouteAccess.Level.PUBLIC;
                @ExerisDomain
                %s
                public class A {}
                """.formatted(declaration));
        assertThat(domain.routeAccess()).isEqualTo(RouteAccess.PUBLIC);
    }

    @Test
    @DisplayName("an element without @RouteAccess declares nothing, and an unknown constant reads as absent")
    void absentAndUnknownReadAsNull() {
        assertThat(read("""
                package x;
                import eu.exeris.sdk.annotation.ExerisDomain;
                @ExerisDomain
                public class A {}
                """).routeAccess()).isNull();
        assertThat(read("""
                package x;
                import eu.exeris.sdk.annotation.ExerisDomain;
                import eu.exeris.sdk.annotation.RouteAccess;
                @ExerisDomain
                @RouteAccess(RouteAccess.Level.SOMETIMES)
                public class A {}
                """).routeAccess()).isNull();
    }

    @Test
    @DisplayName("a single permission written without braces is read")
    void braceElidedPermissionIsRead() {
        DomainMetadata domain = read("""
                package x;
                import eu.exeris.sdk.annotation.Action;
                import eu.exeris.sdk.annotation.ExerisDomain;
                @ExerisDomain(permissions = "customer:read")
                public class Customer {
                    @Action(name = "export", label = "Export", permissions = "customer:export")
                    public void export() {}
                }
                """);
        assertThat(domain.permissions()).containsExactly("customer:read");
        assertThat(action(domain, "export").permissions()).containsExactly("customer:export");
    }

    @Test
    @DisplayName("literal permissions leave the completeness guard silent")
    void literalPermissionsAreNotReported() {
        assertThat(reader.unmodeledFacets(ORDER)).isEmpty();
    }

    @Test
    @DisplayName("a permission the reader cannot resolve is reported on the annotation carrying it")
    void unresolvedPermissionIsReported() {
        String entityLevel = """
                package x;
                import eu.exeris.sdk.annotation.ExerisDomain;
                @ExerisDomain(permissions = {"order:read", Perms.WRITE})
                public class A {}
                """;
        assertThat(reader.unmodeledFacets(entityLevel)).containsExactly("@ExerisDomain");

        String actionLevel = """
                package x;
                import eu.exeris.sdk.annotation.Action;
                import eu.exeris.sdk.annotation.ExerisDomain;
                @ExerisDomain
                public class A {
                    @Action(name = "approve", label = "Approve", permissions = Perms.APPROVE)
                    public void approve() {}
                }
                """;
        assertThat(reader.unmodeledFacets(actionLevel)).containsExactly("@Action");
    }
}
