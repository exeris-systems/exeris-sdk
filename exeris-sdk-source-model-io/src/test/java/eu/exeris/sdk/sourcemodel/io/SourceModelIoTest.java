package eu.exeris.sdk.sourcemodel.io;

import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.EnumMetadata;
import eu.exeris.sdk.sourcemodel.ast.RelationshipMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test suite for {@code exeris-sdk-source-model-io} (ADR-037): the
 * JavaParser-based reader (entity name, fields, {@code @Relationship}s, enums)
 * and the idempotent, comment/annotation-preserving writer, exercised against
 * budgetHQ-shaped entities.
 */
@DisplayName("source-model-io: read (fields/relationships/enums) + preserving write")
class SourceModelIoTest {

    /**
     * Representative entity: header comment, a non-Exeris annotation
     * ({@code @Deprecated}), a field comment, and the canonical
     * {@code @Field(required = true)} shape. budgetHQ doesn't yet author
     * {@code @ExerisDomain} sources, so this stands in for the real corpus;
     * the full budgetHQ round-trip is deferred until BHQ adopts the SDK.
     */
    private static final String ACCOUNT = """
            /*
             * Copyright 2026 budgetHQ. Licensed under Apache-2.0.
             */
            package app.budgethq.account;

            import eu.exeris.sdk.annotation.ExerisDomain;
            import eu.exeris.sdk.annotation.Field;

            @ExerisDomain(name = "Account")
            public class Account {

                // human-readable label shown in the UI
                @Field(required = true)
                private String label;

                @Deprecated
                private double balance;

                @Field
                private String iban;
            }
            """;

    private final SourceModelReader reader = new SourceModelReader();
    private final SourceModelWriter writer = new SourceModelWriter();

    @Nested
    @DisplayName("reader: .java -> DomainMetadata")
    class Reader {

        @Test
        void extractsEntityNamePackageAndFields() {
            DomainMetadata domain = reader.read(ACCOUNT).orElseThrow();

            assertThat(domain.entityName()).isEqualTo("Account");
            assertThat(domain.packageName()).isEqualTo("app.budgethq.account");
            assertThat(domain.fields()).extracting("name")
                    .containsExactly("label", "balance", "iban");
        }

        @Test
        void honoursFieldRequiredShape() {
            DomainMetadata domain = reader.read(ACCOUNT).orElseThrow();

            // @Field(required = true) -> required
            assertThat(domain.findField("label")).get()
                    .extracting("required").isEqualTo(true);
            // no @Field -> not required
            assertThat(domain.findField("balance")).get()
                    .extracting("required").isEqualTo(false);
            // bare @Field marker -> not required
            assertThat(domain.findField("iban")).get()
                    .extracting("required").isEqualTo(false);
        }

        @Test
        void entityNameComesFromAnnotationNotClassName() {
            // canonical pattern: @ExerisDomain(name=...) differs from class name
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    @ExerisDomain(name = "BillingAccount")
                    public class Account {}
                    """;
            assertThat(reader.read(src).orElseThrow().entityName()).isEqualTo("BillingAccount");
        }

        @Test
        void entityNameFallsBackToClassNameWhenAnnotationOmitsName() {
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    @ExerisDomain
                    public class Ledger {}
                    """;
            assertThat(reader.read(src).orElseThrow().entityName()).isEqualTo("Ledger");
        }

        @Test
        void returnsEmptyWhenNoExerisDomainType() {
            Optional<DomainMetadata> domain = reader.read(
                    "package x; public class Plain { private int n; }");
            assertThat(domain).isEmpty();
        }

        @Test
        void throwsOnInvalidSource() {
            assertThatThrownBy(() -> reader.read("%%% not valid java %%%"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("writer: idempotent, formatting/comment-preserving edits")
    class Writer {

        @Test
        void addsFieldPreservingCommentsAndNonExerisAnnotations() {
            String result = writer.addField(ACCOUNT, "String", "currency");

            // new field present
            assertThat(result).contains("private String currency;");
            // user comment preserved verbatim
            assertThat(result).contains("// human-readable label shown in the UI");
            // non-Exeris annotation preserved
            assertThat(result).contains("@Deprecated");
            // Exeris annotation preserved verbatim (not reformatted)
            assertThat(result).contains("@Field(required = true)");
            // header comment preserved
            assertThat(result).contains("Copyright 2026 budgetHQ");
        }

        @Test
        void isIdempotentWhenFieldAlreadyExists() {
            // 'label' already exists -> no-op, source returned unchanged
            assertThat(writer.addField(ACCOUNT, "String", "label")).isEqualTo(ACCOUNT);
        }

        @Test
        void throwsWhenNoExerisDomainType() {
            assertThatThrownBy(() -> writer.addField(
                    "package x; public class Plain {}", "String", "x"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("@ExerisDomain");
        }

        @Test
        void throwsOnInvalidSource() {
            assertThatThrownBy(() -> writer.addField("%%% nope %%%", "String", "x"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void renameFieldPreservesCommentsAndAnnotationsAndIsIdempotent() {
            String renamed = writer.renameField(ACCOUNT, "label", "title");

            assertThat(renamed).contains("private String title;");
            assertThat(renamed).doesNotContain("String label;");
            assertThat(renamed).contains("// human-readable label shown in the UI");
            assertThat(renamed).contains("@Field(required = true)");

            // re-applying with the old name is a no-op (label no longer exists)
            assertThat(writer.renameField(renamed, "label", "title")).isEqualTo(renamed);
        }

        @Test
        void renameIsNoOpWhenSourceAbsentOrTargetExists() {
            assertThat(writer.renameField(ACCOUNT, "missing", "x")).isEqualTo(ACCOUNT);
            // 'iban' already exists -> renaming onto it would duplicate, so no-op
            assertThat(writer.renameField(ACCOUNT, "label", "iban")).isEqualTo(ACCOUNT);
            // from == to -> 'to' is by definition present, so also a no-op
            assertThat(writer.renameField(ACCOUNT, "label", "label")).isEqualTo(ACCOUNT);
        }

        @Test
        void changeFieldTypePreservesAndIsNoOpWhenUnchanged() {
            String changed = writer.changeFieldType(ACCOUNT, "balance", "java.math.BigDecimal");

            assertThat(changed).contains("java.math.BigDecimal balance;");
            assertThat(changed).contains("@Deprecated"); // sibling annotation preserved

            assertThat(writer.changeFieldType(ACCOUNT, "balance", "double")).isEqualTo(ACCOUNT);
            assertThat(writer.changeFieldType(ACCOUNT, "missing", "int")).isEqualTo(ACCOUNT);
        }

        @Test
        void removeFieldDropsDeclarationAndPreservesOthers() {
            String removed = writer.removeField(ACCOUNT, "balance");

            assertThat(removed).doesNotContain("balance");
            assertThat(removed).doesNotContain("@Deprecated"); // whole declaration removed
            assertThat(removed).contains("private String label;");
            assertThat(removed).contains("// human-readable label shown in the UI");
            assertThat(removed).contains("private String iban;");

            assertThat(writer.removeField(removed, "balance")).isEqualTo(removed); // idempotent
        }

        @Test
        void removeFieldFromMultiVariableDeclarationDropsOnlyThatVariable() {
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    @ExerisDomain(name = "Point")
                    public class Point { private int a, b; }
                    """;
            String removed = writer.removeField(src, "a");
            assertThat(removed).doesNotContain("int a");   // 'a' dropped from the declaration
            assertThat(removed).contains("int b");         // sibling kept, separator not mangled
        }

        @Test
        void addRelationshipAddsAnnotatedFieldAndIsIdempotent() {
            String added = writer.addRelationship(ACCOUNT, "customer", "Customer", "MANY_TO_ONE");

            assertThat(added).contains("Customer customer");
            assertThat(added).contains("@Relationship");
            assertThat(added).contains("RelationshipType.MANY_TO_ONE");
            assertThat(added).contains("// human-readable label shown in the UI"); // preserved

            // re-applying with the same field name is a no-op
            assertThat(writer.addRelationship(added, "customer", "Customer", "MANY_TO_ONE"))
                    .isEqualTo(added);
        }

        @Test
        void addRelationshipNoOpWhenFieldExists() {
            assertThat(writer.addRelationship(ACCOUNT, "label", "Customer", "MANY_TO_ONE"))
                    .isEqualTo(ACCOUNT);
        }

        @Test
        void removeRelationshipRemovesOnlyRelationshipFields() {
            String withRel = writer.addRelationship(ACCOUNT, "customer", "Customer", "ONE_TO_ONE");

            String removed = writer.removeRelationship(withRel, "customer");
            assertThat(removed).doesNotContain("customer");
            assertThat(removed).doesNotContain("@Relationship");
            assertThat(removed).contains("private String label;"); // other fields preserved
        }

        @Test
        void removeRelationshipIsNoOpForPlainFieldOrAbsent() {
            // 'balance' is a plain @Deprecated field, NOT a @Relationship -> must not be deleted
            assertThat(writer.removeRelationship(ACCOUNT, "balance")).isEqualTo(ACCOUNT);
            assertThat(writer.removeRelationship(ACCOUNT, "missing")).isEqualTo(ACCOUNT);
        }
    }

    @Nested
    @DisplayName("reader: @Relationship extraction")
    class Relationships {

        // NB: @Relationship declares required attributes (targetEntity, displayField)
        // with no defaults; they're omitted here on purpose — JavaParser parses
        // source syntactically and does not validate annotation completeness, and
        // the reader derives targetEntity from the field type, not the attribute.
        private static final String ORDER = """
                package app.budgethq.order;
                import eu.exeris.sdk.annotation.ExerisDomain;
                import eu.exeris.sdk.annotation.Relationship;
                import eu.exeris.sdk.annotation.Relationship.RelationshipType;
                import java.util.List;

                @ExerisDomain(name = "Order")
                public class Order {
                    @Relationship(relationshipType = RelationshipType.MANY_TO_ONE)
                    private Customer customer;

                    @Relationship(relationshipType = RelationshipType.ONE_TO_MANY, mappedBy = "order")
                    private List<OrderLine> lines;
                }
                """;

        @Test
        void readsTypeTargetAndMappedBy() {
            DomainMetadata domain = reader.read(ORDER).orElseThrow();

            assertThat(domain.relationships()).hasSize(2);

            assertThat(domain.relationships()).anySatisfy(r -> {
                assertThat(r.fieldName()).isEqualTo("customer");
                assertThat(r.targetEntity()).isEqualTo("Customer");
                assertThat(r.type()).isEqualTo(RelationshipMetadata.RelationType.MANY_TO_ONE);
            });
        }

        @Test
        void unwrapsCollectionElementTypeForTargetEntity() {
            DomainMetadata domain = reader.read(ORDER).orElseThrow();

            assertThat(domain.relationships()).anySatisfy(r -> {
                assertThat(r.fieldName()).isEqualTo("lines");
                assertThat(r.targetEntity()).isEqualTo("OrderLine"); // List<OrderLine> unwrapped
                assertThat(r.type()).isEqualTo(RelationshipMetadata.RelationType.ONE_TO_MANY);
                assertThat(r.mappedBy()).isEqualTo("order");
            });
        }

        @Test
        void relationshipFieldsAreNotAlsoPlainFields() {
            DomainMetadata domain = reader.read(ORDER).orElseThrow();
            assertThat(domain.fields()).isEmpty();
        }
    }

    @Nested
    @DisplayName("reader: enum extraction")
    class Enums {

        @Test
        void readsEnumNamePackageAndValues() {
            String src = """
                    package app.budgethq.account;
                    public enum AccountType { CHECKING, SAVINGS, CREDIT }
                    """;
            List<EnumMetadata> enums = reader.readEnums(src);

            assertThat(enums).hasSize(1);
            EnumMetadata type = enums.get(0);
            assertThat(type.name()).isEqualTo("AccountType");
            assertThat(type.packageName()).isEqualTo("app.budgethq.account");
            assertThat(type.qualifiedName()).isEqualTo("app.budgethq.account.AccountType");
            assertThat(type.values()).extracting("name")
                    .containsExactly("CHECKING", "SAVINGS", "CREDIT");
        }

        @Test
        void qualifiedNameIsBareNameWhenNoPackage() {
            List<EnumMetadata> enums = reader.readEnums("public enum Color { RED, GREEN }");
            assertThat(enums).singleElement()
                    .satisfies(e -> assertThat(e.qualifiedName()).isEqualTo("Color"));
        }

        @Test
        void returnsEmptyWhenNoEnums() {
            assertThat(reader.readEnums("package x; public class NoEnumsHere {}")).isEmpty();
        }
    }

    @Nested
    @DisplayName("reader: @Action + @ActionParam extraction")
    class Actions {

        private static final String INVOICE = """
                package app.budgethq.invoice;
                import eu.exeris.sdk.annotation.ExerisDomain;
                import eu.exeris.sdk.annotation.Action;
                import eu.exeris.sdk.annotation.ActionParam;
                import eu.exeris.sdk.annotation.Field;

                @ExerisDomain(name = "Invoice")
                public class Invoice {
                    @Field private String number;

                    @Action(name = "approve", label = "Approve", httpMethod = "POST", path = "/approve",
                            description = "Approve the invoice")
                    public void approve(
                            @ActionParam(label = "Reason", required = true, description = "Why it is approved") String reason,
                            @ActionParam(label = "Notify", required = false, defaultValue = "true") boolean notify,
                            @ActionParam(label = "Memo") String memo,
                            @ActionParam(name = "ccEmail", label = "CC") String cc) { }

                    @Action(name = "archive", label = "Archive", path = "/archive", async = true)
                    public void archive() { }

                    // no name attribute -> falls back to the method name
                    @Action(label = "Refresh", path = "/refresh")
                    public void refresh() { }
                }
                """;

        @Test
        void readsActionNameLabelHttpMethodAndAsync() {
            DomainMetadata domain = reader.read(INVOICE).orElseThrow();

            assertThat(domain.actions()).extracting("name")
                    .containsExactlyInAnyOrder("approve", "archive", "refresh");

            assertThat(domain.actions()).anySatisfy(a -> {
                assertThat(a.name()).isEqualTo("approve");
                assertThat(a.displayName()).isEqualTo("Approve");
                assertThat(a.httpMethod()).isEqualTo("POST");
                assertThat(a.description()).isEqualTo("Approve the invoice");
                assertThat(a.async()).isFalse();
            });
            assertThat(domain.actions()).anySatisfy(a -> {
                assertThat(a.name()).isEqualTo("archive");
                assertThat(a.async()).isTrue();
                assertThat(a.params()).isEmpty();
            });
        }

        @Test
        void actionNameFallsBackToMethodNameWhenAttributeAbsent() {
            DomainMetadata domain = reader.read(INVOICE).orElseThrow();
            assertThat(domain.actions()).anySatisfy(a -> assertThat(a.name()).isEqualTo("refresh"));
        }

        @Test
        void readsActionParamsWithRequiredDefaultAndType() {
            DomainMetadata domain = reader.read(INVOICE).orElseThrow();

            var approve = domain.actions().stream()
                    .filter(a -> a.name().equals("approve")).findFirst().orElseThrow();
            assertThat(approve.params()).extracting("name")
                    .containsExactly("reason", "notify", "memo", "ccEmail");

            assertThat(approve.params()).anySatisfy(p -> {
                assertThat(p.name()).isEqualTo("reason");
                assertThat(p.type()).isEqualTo("String");
                assertThat(p.required()).isTrue();
                assertThat(p.description()).isEqualTo("Why it is approved");
            });
            // @ActionParam(name = "ccEmail") overrides the parameter name "cc"
            assertThat(approve.params()).anySatisfy(p -> assertThat(p.name()).isEqualTo("ccEmail"));
            assertThat(approve.params()).anySatisfy(p -> {
                assertThat(p.name()).isEqualTo("notify");
                assertThat(p.type()).isEqualTo("boolean");
                assertThat(p.required()).isFalse();
                assertThat(p.defaultValue()).isEqualTo("true");
            });
            // 'memo' omits required -> defaults to true (mirrors @ActionParam.required)
            assertThat(approve.params()).anySatisfy(p -> {
                assertThat(p.name()).isEqualTo("memo");
                assertThat(p.required()).isTrue();
            });
        }

        @Test
        void actionMethodsAreNotReadAsFields() {
            DomainMetadata domain = reader.read(INVOICE).orElseThrow();
            assertThat(domain.fields()).extracting("name").containsExactly("number");
        }
    }

    @Nested
    @DisplayName("reader: class-level @UI extraction")
    class Ui {

        private static final String PRODUCT = """
                package app.budgethq.product;
                import eu.exeris.sdk.annotation.ExerisDomain;
                import eu.exeris.sdk.annotation.UI;
                import eu.exeris.sdk.annotation.Field;

                @ExerisDomain(name = "Product")
                @UI(listView = true, exportable = true, editForm = false)
                public class Product {
                    @Field private String sku;
                }
                """;

        @Test
        void readsViewFlagsWithProcessorDefaultTrueConvention() {
            var ui = reader.read(PRODUCT).orElseThrow().uiMetadata();

            assertThat(ui).isNotNull();
            assertThat(ui.listView()).isTrue();      // explicit
            assertThat(ui.exportable()).isTrue();     // explicit (overrides default false)
            assertThat(ui.editForm()).isFalse();      // explicit (overrides default true)
            // absent attributes default to true (matches the processor)
            assertThat(ui.detailView()).isTrue();
            assertThat(ui.createForm()).isTrue();
            assertThat(ui.searchable()).isTrue();
            assertThat(ui.filterable()).isTrue();
        }

        @Test
        void uiMetadataIsNullWhenNoUiAnnotation() {
            String noUi = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    @ExerisDomain(name = "Bare")
                    public class Bare {}
                    """;
            assertThat(reader.read(noUi).orElseThrow().uiMetadata()).isNull();
        }

        @Test
        void bareMarkerUiUsesAllDefaults() {
            // @UI with no attributes -> every view flag defaults true, exportable false
            String marker = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.UI;
                    @ExerisDomain(name = "Bare")
                    @UI
                    public class Bare {}
                    """;
            var ui = reader.read(marker).orElseThrow().uiMetadata();
            assertThat(ui).isNotNull();
            assertThat(ui.listView()).isTrue();
            assertThat(ui.detailView()).isTrue();
            assertThat(ui.createForm()).isTrue();
            assertThat(ui.editForm()).isTrue();
            assertThat(ui.searchable()).isTrue();
            assertThat(ui.filterable()).isTrue();
            assertThat(ui.exportable()).isFalse();
        }

        @Test
        void nestedExerisDomainUiAttributeIsNotRead() {
            // Parity: the processor (findAnnotation over directly-present annotations)
            // reads ONLY a standalone class-level @UI — never @ExerisDomain(ui=@UI(...)).
            // The reader matches: the nested form yields null UI. (@ExerisDomain.ui()
            // is effectively unconsumed SDK-wide; changing that must move processor +
            // reader together and is out of scope here.)
            String nested = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.UI;
                    @ExerisDomain(name = "Order", ui = @UI(listView = true, exportable = true))
                    public class Order {}
                    """;
            assertThat(reader.read(nested).orElseThrow().uiMetadata()).isNull();
        }
    }
}
