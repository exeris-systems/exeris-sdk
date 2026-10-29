package eu.exeris.sdk.sourcemodel.io;

import eu.exeris.sdk.sourcemodel.ast.BlobMetadata;
import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.FieldMetadata;
import eu.exeris.sdk.sourcemodel.ast.GraphPropertyMetadata;
import eu.exeris.sdk.sourcemodel.ast.UIMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("reader: @GraphProperty, @UIGroup and @Blob in the shape ADR-042 fixes")
class SourceModelReaderGraphPropertyUiGroupBlobTest {

    private final SourceModelReader reader = new SourceModelReader();

    private DomainMetadata read(String source) {
        return reader.read(source).orElseThrow();
    }

    private static FieldMetadata field(DomainMetadata domain, String name) {
        return domain.fields().stream()
                .filter(f -> name.equals(f.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no field " + name));
    }

    @Nested
    @DisplayName("@GraphProperty → GraphMetadata.properties")
    class GraphProperties {

        private static final String PRODUCT = """
                package x;
                import eu.exeris.sdk.annotation.ExerisDomain;
                import eu.exeris.sdk.annotation.Graph;
                import eu.exeris.sdk.annotation.GraphProperty;
                import eu.exeris.sdk.annotation.GraphProperty.GraphType;
                import static eu.exeris.sdk.annotation.GraphProperty.GraphType.LONG;

                @ExerisDomain
                @Graph
                public class Product {
                    @GraphProperty(indexed = true) private String title;
                    @GraphProperty(name = "amt", graphType = GraphType.DOUBLE) private java.math.BigDecimal amount;
                    @GraphProperty(name = "", graphType = GraphProperty.GraphType.AUTO, indexed = false) private String plain;
                    @GraphProperty(graphType = LONG) private long a, b;
                    private String unmarked;
                }
                """;

        @Test
        void oneDeclaredPropertyPerAnnotatedFieldVariableInDeclarationOrder() {
            assertThat(read(PRODUCT).graphMetadata().properties()).containsExactly(
                    new GraphPropertyMetadata("title", null, true),
                    new GraphPropertyMetadata("amt", "DOUBLE", false, "amount"),
                    new GraphPropertyMetadata("plain", null, false),
                    new GraphPropertyMetadata("a", "LONG", false),
                    new GraphPropertyMetadata("b", "LONG", false));
        }

        @Test
        void autoIsNotDerivedIntoAGraphType() {
            // AUTO leaves the type to the field's Java type, which FieldMetadata.type carries.
            GraphPropertyMetadata title = read(PRODUCT).graphMetadata().properties().getFirst();
            assertThat(title.type()).isNull();
            assertThat(field(read(PRODUCT), title.effectiveField()).type()).isEqualTo("String");
        }

        @Test
        void aGraphWithNoPropertyCarriesAnEmptyList() {
            DomainMetadata domain = read("""
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.Graph;
                    @ExerisDomain @Graph
                    public class Product { private String title; }
                    """);
            assertThat(domain.graphMetadata().properties()).isEmpty();
        }

        @Test
        void aPropertyWithoutGraphIsNotRead() {
            DomainMetadata domain = read("""
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.GraphProperty;
                    @ExerisDomain
                    public class Product { @GraphProperty(indexed = true) private String title; }
                    """);
            assertThat(domain.graphMetadata()).isNull();
        }
    }

    @Nested
    @DisplayName("@UIGroup → UIMetadata.groups")
    class Groups {

        private static final String CUSTOMER = """
                package x;
                import eu.exeris.sdk.annotation.ExerisDomain;
                import eu.exeris.sdk.annotation.Relationship;
                import eu.exeris.sdk.annotation.UIGroup;

                @ExerisDomain
                public class Customer {
                    @UIGroup(name = "identity", label = "Identity", order = 2, icon = "person")
                    private String email;
                    @UIGroup(name = "media", label = "", description = "Uploads", order = 0, collapsible = false)
                    private String avatar;
                    @UIGroup(name = "identity", label = "Ignored", order = 7, collapsed = true, columns = 3)
                    private String displayName, nickname;
                    @UIGroup(name = "media", label = "Media")
                    @Relationship(targetEntity = Customer.class)
                    private Customer referrer;
                    @UIGroup(name = "") private String skipped;
                    private String ungrouped;
                }
                """;

        @Test
        void oneGroupPerNameInFirstDeclaredOrderEachAttributeFromItsFirstDeclaration() {
            assertThat(read(CUSTOMER).uiMetadata().groups()).containsExactly(
                    new UIMetadata.UIGroupMetadata("identity", "Identity", null, 2, true, true, "person", 12,
                            List.of("email", "displayName", "nickname")),
                    new UIMetadata.UIGroupMetadata("media", "Media", "Uploads", 0, false, false, null, 12,
                            List.of("avatar", "referrer")));
        }

        @Test
        void undeclaredAttributesTakeTheAnnotationDefaults() {
            UIMetadata.UIGroupMetadata group = read("""
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.UIGroup;
                    @ExerisDomain
                    public class Customer { @UIGroup(name = "main") private String email; }
                    """).uiMetadata().groups().getFirst();
            assertThat(group).isEqualTo(new UIMetadata.UIGroupMetadata("main", null, null,
                    UIMetadata.UIGroupMetadata.DEFAULT_ORDER, true, false, null,
                    UIMetadata.UIGroupMetadata.DEFAULT_GRID_SPAN, List.of("email")));
        }

        @Test
        void groupsWithoutUiCreateTheUiMetadataAtItsDefaults() {
            UIMetadata ui = read(CUSTOMER).uiMetadata();
            assertThat(new UIMetadata(ui.icon(), ui.color(), ui.listView(), ui.detailView(), ui.createForm(),
                    ui.editForm(), ui.searchable(), ui.filterable(), ui.exportable(), ui.bulkActions(),
                    ui.columns(), ui.defaultLayout(), List.of(), ui.fieldOverrides(), ui.tabs()))
                    .isEqualTo(UIMetadata.builder().build());
        }

        @Test
        void noGroupLeavesTheGroupsEmpty() {
            String source = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.UI;
                    @ExerisDomain @UI
                    public class Customer { private String email; }
                    """;
            assertThat(read(source).uiMetadata().groups()).isEmpty();
        }
    }

    @Nested
    @DisplayName("@Blob → FieldMetadata.blob")
    class Blobs {

        private static final String ASSET = """
                package x;
                import eu.exeris.sdk.annotation.Blob;
                import eu.exeris.sdk.annotation.ExerisDomain;
                import eu.exeris.sdk.annotation.Field;

                @ExerisDomain(dataScope = ExerisDomain.DataScope.GLOBAL)
                public class Asset {
                    @Field(label = "Scan", required = true)
                    @Blob(container = "scans", contentTypes = {"application/pdf", "image/png"})
                    private String scan;
                    @Blob private String raw;
                    @Blob(container = " ", contentTypes = "image/jpeg") private String photo;
                    private String plain;
                }
                """;

        @Test
        void aBlobBesideFieldKeepsEveryOtherAttribute() {
            FieldMetadata scan = field(read(ASSET), "scan");
            assertThat(scan.blob()).isEqualTo(new BlobMetadata("scans", List.of("application/pdf", "image/png")));
            assertThat(scan.required()).isTrue();
            assertThat(scan.displayName()).isEqualTo("Scan");
        }

        @Test
        void aBlobWithoutFieldIsOnTheSimpleField() {
            FieldMetadata raw = field(read(ASSET), "raw");
            assertThat(raw.blob()).isEqualTo(BlobMetadata.unrestricted());
            assertThat(raw).isEqualTo(FieldMetadata.builder("raw", "String")
                    .searchable(true).sortable(true).filterable(true).blob(BlobMetadata.unrestricted()).build());
        }

        @Test
        void aBlankContainerIsDerivedAndASingleContentTypeIsAList() {
            assertThat(field(read(ASSET), "photo").blob())
                    .isEqualTo(BlobMetadata.ofContentTypes(List.of("image/jpeg")));
        }

        @Test
        void aFieldWithoutBlobCarriesNone() {
            assertThat(field(read(ASSET), "plain").blob()).isNull();
            assertThat(field(read(ASSET), "plain")).isEqualTo(FieldMetadata.simple("plain", "String"));
        }

        @Test
        void aGlobalScopedEntityIsReadAsWritten() {
            // Refusing the combination is a build-time job for exeris-tooling, not the reader's.
            assertThat(read(ASSET).fields()).filteredOn(f -> f.blob() != null).hasSize(3);
        }
    }

    @Test
    @DisplayName("the three are not reported by unmodeledFacets, @Channel still is")
    void onlyChannelIsReportedAsUnmodeled() {
        String source = """
                package x;
                import eu.exeris.sdk.annotation.Blob;
                import eu.exeris.sdk.annotation.Channel;
                import eu.exeris.sdk.annotation.ExerisDomain;
                import eu.exeris.sdk.annotation.Graph;
                import eu.exeris.sdk.annotation.GraphProperty;
                import eu.exeris.sdk.annotation.UIGroup;
                @ExerisDomain @Graph
                public class Asset {
                    @GraphProperty @UIGroup(name = "main") @Blob private String scan;
                }
                """;
        assertThat(reader.unmodeledFacets(source)).isEmpty();
        assertThat(reader.unmodeledFacets(source.replace("@Graph\n", "@Graph @Channel(messageType = \"Ping\")\n")))
                .containsExactly("@Channel");
    }
}
