package eu.exeris.sdk.sourcemodel.io;

import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.GraphEdgeMetadata;
import eu.exeris.sdk.sourcemodel.ast.SystemFieldsMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reader ↔ processor parity over the two facets both readers build from field-level annotations:
 * {@link DomainMetadata#systemFields} and {@code GraphMetadata.edges} (ADR-042).
 *
 * <p>Each {@code processor-parity/<Name>.java} fixture has a {@code <Name>.expected.json} beside it
 * holding the {@code systemFields} and {@code graphMetadata} the {@code exeris-tooling} processor
 * emits for that source, with a key absent where the processor omits the facet. The reader has
 * to produce the same two JSON values, absence included. The fixtures cover
 * only declarations the processor accepts; the ones it refuses are pinned below against the
 * processor's documented outcome, since a refused build emits nothing to compare against.
 */
@DisplayName("reader↔processor parity: systemFields and graph edges")
class SourceModelReaderProcessorParityTest {

    private static final Path FIXTURES = Path.of("src", "test", "resources", "processor-parity");
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final SourceModelReader reader = new SourceModelReader();

    private DomainMetadata read(String source) {
        return reader.read(source).orElseThrow();
    }

    private static String fixture(String fileName) {
        try {
            return Files.readString(FIXTURES.resolve(fileName));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"OverrideOnly", "Markers", "Universe", "SharedScopeOffTier", "SharedScopeOnly",
            "Plain", "Edges", "EdgesMixed"})
    void readerEmitsWhatTheProcessorEmits(String name) {
        JsonNode expected = MAPPER.readTree(fixture(name + ".expected.json"));
        JsonNode actual = MAPPER.valueToTree(read(fixture(name + ".java")));

        assertThat(actual.path("systemFields")).isEqualTo(expected.path("systemFields"));
        assertThat(actual.path("graphMetadata")).isEqualTo(expected.path("graphMetadata"));
    }

    @Nested
    @DisplayName("declarations the processor refuses")
    class Refused {

        @Test
        void aMarkerOnTwoFieldsLeavesTheRoleToTheOverride() {
            DomainMetadata domain = read("""
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.system.Version;
                    @ExerisDomain(versionField = "rev")
                    public class A {
                        @Version private long rev;
                        @Version private long other;
                    }
                    """);
            assertThat(domain.systemFields().versionField()).isEqualTo("rev");
        }

        @Test
        void aMarkerOnAMultiVariableDeclarationIsOnTwoFields() {
            DomainMetadata domain = read("""
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.system.Version;
                    @ExerisDomain
                    public class A {
                        @Version private long rev, other;
                    }
                    """);
            assertThat(domain.systemFields()).isNull();
        }

        @Test
        void aMarkerContradictingItsOverrideLeavesTheRoleToTheOverride() {
            DomainMetadata domain = read("""
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.system.Version;
                    import eu.exeris.sdk.annotation.system.AuditCreatedAt;
                    @ExerisDomain(versionField = "named")
                    public class A {
                        @Version private long marked;
                        @AuditCreatedAt private java.time.Instant born;
                    }
                    """);
            assertThat(domain.systemFields()).satisfies(fields -> {
                assertThat(fields.versionField()).isEqualTo("named");
                assertThat(fields.createdAtField()).isEqualTo("born");
            });
        }

        @Test
        void aRepeatedEdgeIsDropped() {
            DomainMetadata domain = read("""
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.Graph;
                    import eu.exeris.sdk.annotation.GraphEdge;
                    import eu.exeris.sdk.annotation.GraphEdges;
                    @ExerisDomain
                    @Graph
                    public class A {
                        @GraphEdge(type = "ONE") @GraphEdge(type = "TWO") private Object repeated;
                        @GraphEdges({@GraphEdge(type = "THREE"), @GraphEdge(type = "FOUR")}) private Object contained;
                        @GraphEdge(type = "KEPT") private Object single;
                    }
                    """);
            assertThat(domain.graphMetadata().edges())
                    .containsExactly(new GraphEdgeMetadata("single", null, "KEPT"));
        }
    }

    @Test
    void edgesAreReadOnlyUnderGraph() {
        DomainMetadata domain = read("""
                package x;
                import eu.exeris.sdk.annotation.ExerisDomain;
                import eu.exeris.sdk.annotation.GraphEdge;
                @ExerisDomain
                public class A {
                    @GraphEdge(type = "OWNS") private Object car;
                }
                """);
        assertThat(domain.graphMetadata()).isNull();
    }

    @Test
    void aQualifiedMarkerMatchesBySimpleName() {
        DomainMetadata domain = read("""
                package x;
                import eu.exeris.sdk.annotation.ExerisDomain;
                @ExerisDomain
                public class A {
                    @eu.exeris.sdk.annotation.system.TenantId private java.util.UUID owner;
                }
                """);
        assertThat(domain.systemFields())
                .isEqualTo(SystemFieldsMetadata.builder().tenantIdField("owner").build());
    }

    @Test
    void anExplicitDefaultPrimaryKeyAloneDeclaresNothing() {
        DomainMetadata domain = read("""
                package x;
                import eu.exeris.sdk.annotation.ExerisDomain;
                @ExerisDomain(primaryKeyField = "id", versionField = "  ")
                public class A {}
                """);
        assertThat(domain.systemFields()).isNull();
    }
}
