package eu.exeris.sdk.sourcemodel.io;

import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.GraphEdgeMetadata;
import eu.exeris.sdk.sourcemodel.ast.SystemFieldsMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
 *
 * <p>The {@code PrimaryKey*} fixtures pin the {@code @PrimaryKey} marker ahead of its processor
 * half: their expected JSON is what ADR-104 obligations 11 and 12 require the processor to emit
 * once it reads the marker, under the rules of the other ten system-field markers.
 *
 * <p>The {@code GraphProperties}, {@code GraphNoProperties}, {@code UiGroups} and {@code Blobs}
 * fixtures pin {@code @GraphProperty}, {@code @UIGroup} and {@code @Blob} ahead of their processor
 * half in the same way: their expected {@code graphMetadata.properties}, {@code uiMetadata} and
 * {@code fields} are the shape ADR-042's reader-shape amendment requires the processor to emit.
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
            "Plain", "Edges", "EdgesMixed", "PrimaryKeyMarker", "PrimaryKeyAgreeing", "PrimaryKeyOnId"})
    void readerEmitsWhatTheProcessorEmits(String name) {
        JsonNode expected = MAPPER.readTree(fixture(name + ".expected.json"));
        JsonNode actual = MAPPER.valueToTree(read(fixture(name + ".java")));

        assertThat(actual.path("systemFields")).isEqualTo(expected.path("systemFields"));
        assertThat(actual.path("graphMetadata")).isEqualTo(expected.path("graphMetadata"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"GraphProperties", "GraphNoProperties", "UiGroups", "Blobs"})
    void readerEmitsTheFixedShapeForPropertiesGroupsAndBlobs(String name) {
        JsonNode expected = MAPPER.readTree(fixture(name + ".expected.json"));
        JsonNode actual = MAPPER.valueToTree(read(fixture(name + ".java")));

        assertThat(actual.path("graphMetadata").path("properties"))
                .isEqualTo(expected.path("graphMetadata").path("properties"));
        assertThat(actual.path("uiMetadata")).isEqualTo(expected.path("uiMetadata"));
        assertThat(blobsOf(actual)).isEqualTo(blobsOf(expected));
    }

    /**
     * The name and {@code blob} of each field carrying one, in field order — the one
     * {@code FieldMetadata} component these fixtures pin.
     */
    private static List<String> blobsOf(JsonNode metadata) {
        List<String> blobs = new ArrayList<>();
        for (JsonNode field : metadata.path("fields")) {
            if (field.has("blob")) {
                blobs.add(field.path("name").asString() + " " + field.get("blob"));
            }
        }
        return blobs;
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

        /**
         * A refused {@code @PrimaryKey} leaves the key to the override, else {@code id}; an
         * {@code expected} of {@code null} means no {@code systemFields} record at all.
         */
        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {
                "contradicting its override | (primaryKeyField = \"named\") | @PrimaryKey private java.util.UUID marked; private java.util.UUID named; | named",
                "contradicting an explicit default | (primaryKeyField = \"id\") | @PrimaryKey private java.util.UUID marked; |",
                "on two fields | | @PrimaryKey private java.util.UUID one; @PrimaryKey private java.util.UUID two; |"})
        void aRefusedPrimaryKeyMarkerIsNotRead(String refusal, String domainAttributes, String members,
                                               String expected) {
            DomainMetadata domain = read("""
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.system.PrimaryKey;
                    @ExerisDomain%s
                    public class A { %s }
                    """.formatted(domainAttributes == null ? "" : domainAttributes, members));
            if (expected == null) {
                assertThat(domain.systemFields()).as(refusal).isNull();
            } else {
                assertThat(domain.systemFields().primaryKeyField()).as(refusal).isEqualTo(expected);
            }
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"PrimaryKey.Strategy.SEQUENCE", "Strategy.IDENTITY", "NONE"})
        void aPrimaryKeyMarkerWithARefusedStrategyIsNotRead(String strategy) {
            DomainMetadata domain = read("""
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.system.PrimaryKey;
                    import eu.exeris.sdk.annotation.system.PrimaryKey.Strategy;
                    import static eu.exeris.sdk.annotation.system.PrimaryKey.Strategy.NONE;
                    @ExerisDomain(versioned = true, versionField = "rev")
                    public class A {
                        @PrimaryKey(strategy = %s) private java.util.UUID orderId;
                        private long rev;
                    }
                    """.formatted(strategy));
            assertThat(domain.systemFields().primaryKeyField()).isEqualTo("id");
            assertThat(domain.systemFields().versionField()).isEqualTo("rev");
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
