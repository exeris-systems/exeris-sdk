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
        void readsDomainLevelAttributesPresentOnly() {
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    @ExerisDomain(name = "Invoice", module = "billing", tenantScoped = true,
                                  softDelete = true, restApi = false)
                    public class Invoice {}
                    """;
            DomainMetadata d = reader.read(src).orElseThrow();

            assertThat(d.module()).isEqualTo("billing");
            assertThat(d.tenantScoped()).isTrue();
            assertThat(d.softDelete()).isTrue();
            assertThat(d.restApi()).isFalse();          // explicit override
            // absent attribute keeps the builder default (not read, not forced)
            assertThat(d.audited()).isFalse();
        }

        @Test
        void absentBooleanAttributeKeepsBuilderDefault() {
            // restApi defaults TRUE in the builder (the surprising branch — all other
            // booleans default false); an absent restApi must stay true, not be forced.
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    @ExerisDomain(name = "Invoice", module = "billing")
                    public class Invoice {}
                    """;
            DomainMetadata d = reader.read(src).orElseThrow();
            assertThat(d.restApi()).isTrue();
            assertThat(d.tenantScoped()).isFalse();
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
        void addRelationshipThrowsIllegalArgumentOnMalformedType() {
            // a type string that yields an unparseable annotation -> IllegalArgumentException
            assertThatThrownBy(() -> writer.addRelationship(ACCOUNT, "x", "Customer", "BAD)SYNTAX"))
                    .isInstanceOf(IllegalArgumentException.class);
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

        @Test
        void addActionAddsAnnotatedMethodAndIsIdempotent() {
            String added = writer.addAction(ACCOUNT, "approve");

            assertThat(added).contains("@Action");
            assertThat(added).contains("void approve()");
            assertThat(added).contains("name = \"approve\"");
            assertThat(added).contains("path = \"/approve\"");
            assertThat(added).contains("// human-readable label shown in the UI"); // preserved

            assertThat(writer.addAction(added, "approve")).isEqualTo(added); // idempotent
        }

        @Test
        void addActionNoOpWhenActionExists() {
            String once = writer.addAction(ACCOUNT, "approve");
            assertThat(writer.addAction(once, "approve")).isEqualTo(once);
        }

        @Test
        void removeActionRemovesMethodAndPreservesFields() {
            String withAction = writer.addAction(ACCOUNT, "approve");

            String removed = writer.removeAction(withAction, "approve");
            assertThat(removed).doesNotContain("@Action");
            assertThat(removed).doesNotContain("approve");
            assertThat(removed).contains("private String label;"); // fields preserved
        }

        @Test
        void removeActionMatchesByAnnotationNameAndSparesPlainMethods() {
            // action name "approve" lives on @Action.name, not the method name 'doApprove';
            // 'helper' has no @Action and must survive
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.Action;
                    @ExerisDomain(name = "Invoice")
                    public class Invoice {
                        @Action(name = "approve", path = "/a")
                        public void doApprove() {}
                        public void helper() {}
                    }
                    """;
            String removed = writer.removeAction(src, "approve");
            assertThat(removed).doesNotContain("doApprove");
            assertThat(removed).doesNotContain("@Action");
            assertThat(removed).contains("helper()"); // non-action method preserved
        }

        @Test
        void removeActionNoOpWhenAbsent() {
            assertThat(writer.removeAction(ACCOUNT, "missing")).isEqualTo(ACCOUNT);
        }

        @Test
        void addActionThrowsIllegalArgumentOnMalformedName() {
            // a name that yields an unparseable @Action annotation -> IllegalArgumentException
            assertThatThrownBy(() -> writer.addAction(ACCOUNT, "bad\"syntax"))
                    .isInstanceOf(IllegalArgumentException.class);
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

    @Nested
    @DisplayName("reader: round-trip completeness guard (unmodeledFacets)")
    class Completeness {

        @Test
        void emptyForFullyModeledEntity() {
            // ACCOUNT uses only @ExerisDomain(name), @Field (+ a non-facet @Deprecated)
            assertThat(reader.unmodeledFacets(ACCOUNT)).isEmpty();
        }

        @Test
        void fullyAnnotatedEntityHasNoDivergences() {
            // every processor facet is now read (Slices A-D): the guard is empty even
            // for an entity touching all of them. @Projection/@NavMenu/@PrimaryKey are
            // processor-gaps (never divergences); @Validation is read into FieldMetadata.
            String src = """
                    package app.budgethq.order;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.EventSourced;
                    import eu.exeris.sdk.annotation.Graph;
                    import eu.exeris.sdk.annotation.Saga;
                    import eu.exeris.sdk.annotation.Projection;
                    import eu.exeris.sdk.annotation.DomainEvent;
                    import eu.exeris.sdk.annotation.DomainEvent.Trigger;
                    import eu.exeris.sdk.annotation.NavMenu;
                    import eu.exeris.sdk.annotation.InternalApi;
                    import eu.exeris.sdk.annotation.Validation;
                    import eu.exeris.sdk.annotation.Field;
                    import eu.exeris.sdk.annotation.system.PrimaryKey;

                    @ExerisDomain(name = "Order", tenantScoped = true, roles = {"ADMIN"})
                    @EventSourced @Graph @Saga
                    @InternalApi(rateLimit = 100)
                    @DomainEvent(trigger = Trigger.CREATE, topic = "orders.created")
                    @Projection @NavMenu
                    public class Order {
                        @PrimaryKey private Long id;
                        @Field @Validation(min = 1, max = 9999) private String contact;
                        @Field private String code;
                    }
                    """;
            assertThat(reader.unmodeledFacets(src)).isEmpty();
        }

        @Test
        void modeledDomainAttributesAreNotFlagged() {
            // tenantScoped/softDelete are read (Slice A) -> guard stays empty
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    @ExerisDomain(name = "Bare", tenantScoped = true, softDelete = true)
                    public class Bare {}
                    """;
            assertThat(reader.unmodeledFacets(src)).isEmpty();
        }

        @Test
        void onlyNameAttributeIsNotFlagged() {
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    @ExerisDomain(name = "Bare")
                    public class Bare {}
                    """;
            assertThat(reader.unmodeledFacets(src)).isEmpty();
        }

        @Test
        void emptyWhenNoExerisDomainType() {
            assertThat(reader.unmodeledFacets("package x; public class Plain {}")).isEmpty();
        }
    }

    @Nested
    @DisplayName("reader: @DomainEvent extraction (events)")
    class Events {

        private static final String ORDER = """
                package app.budgethq.order;
                import eu.exeris.sdk.annotation.ExerisDomain;
                import eu.exeris.sdk.annotation.DomainEvent;
                import eu.exeris.sdk.annotation.DomainEvent.Trigger;
                import eu.exeris.sdk.annotation.Field;

                @ExerisDomain(name = "OrderEntity")
                @DomainEvent(name = "OrderPlaced", trigger = Trigger.CREATE,
                        topic = "orders.created", description = "Customer placed an order")
                @DomainEvent(trigger = Trigger.UPDATE, topic = "orders.updated")
                public class Order {
                    @Field private String code;
                }
                """;

        @Test
        void readsRepeatedDomainEventsWithExplicitName() {
            DomainMetadata domain = reader.read(ORDER).orElseThrow();

            assertThat(domain.events()).extracting("name")
                    .containsExactlyInAnyOrder("OrderPlaced", "OrderUpdatedEvent");
            assertThat(domain.events()).anySatisfy(e -> {
                assertThat(e.name()).isEqualTo("OrderPlaced");
                assertThat(e.topic()).isEqualTo("orders.created");
                assertThat(e.description()).isEqualTo("Customer placed an order");
                // aggregateType mirrors the processor: the *class* simple name, not @ExerisDomain(name)
                assertThat(e.aggregateType()).isEqualTo("Order");
            });
        }

        @Test
        void derivesEventNameFromTriggerWhenNameAbsent() {
            // @DomainEvent(trigger = UPDATE) with no name -> "Order" + "UpdatedEvent"
            DomainMetadata domain = reader.read(ORDER).orElseThrow();
            assertThat(domain.events()).anySatisfy(e -> {
                assertThat(e.name()).isEqualTo("OrderUpdatedEvent");
                assertThat(e.topic()).isEqualTo("orders.updated");
                assertThat(e.description()).isNull();
            });
        }

        @Test
        void defaultsTriggerToCreateWhenAbsent() {
            // no trigger attribute at all -> processor defaults to CREATE -> "CreatedEvent"
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.DomainEvent;
                    @ExerisDomain(name = "Thing")
                    @DomainEvent(topic = "things.created")
                    public class Thing {}
                    """;
            DomainMetadata domain = reader.read(src).orElseThrow();
            assertThat(domain.events()).singleElement()
                    .satisfies(e -> assertThat(e.name()).isEqualTo("ThingCreatedEvent"));
        }

        @Test
        void unmappedTriggerFallsBackToGenericSuffix() {
            // SNAPSHOT has no explicit suffix mapping -> generic "Event"
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.DomainEvent;
                    import eu.exeris.sdk.annotation.DomainEvent.Trigger;
                    @ExerisDomain(name = "Thing")
                    @DomainEvent(trigger = Trigger.SNAPSHOT, topic = "things.snap")
                    public class Thing {}
                    """;
            DomainMetadata domain = reader.read(src).orElseThrow();
            assertThat(domain.events()).singleElement()
                    .satisfies(e -> assertThat(e.name()).isEqualTo("ThingEvent"));
        }

        @Test
        void unwrapsHandWrittenDomainEventsContainer() {
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.DomainEvent;
                    import eu.exeris.sdk.annotation.DomainEvent.DomainEvents;
                    import eu.exeris.sdk.annotation.DomainEvent.Trigger;
                    @ExerisDomain(name = "Order")
                    @DomainEvents({
                        @DomainEvent(name = "Created", trigger = Trigger.CREATE, topic = "o.created"),
                        @DomainEvent(name = "Deleted", trigger = Trigger.DELETE, topic = "o.deleted")
                    })
                    public class Order {}
                    """;
            DomainMetadata domain = reader.read(src).orElseThrow();
            assertThat(domain.events()).extracting("name")
                    .containsExactlyInAnyOrder("Created", "Deleted");
        }

        @Test
        void unwrapsSingleElementContainerWithoutArrayBraces() {
            // legal Java: a single-element annotation array may omit the { } braces
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.DomainEvent;
                    import eu.exeris.sdk.annotation.DomainEvent.DomainEvents;
                    import eu.exeris.sdk.annotation.DomainEvent.Trigger;
                    @ExerisDomain(name = "Order")
                    @DomainEvents(@DomainEvent(name = "Created", trigger = Trigger.CREATE, topic = "o.created"))
                    public class Order {}
                    """;
            DomainMetadata domain = reader.read(src).orElseThrow();
            assertThat(domain.events()).singleElement()
                    .satisfies(e -> assertThat(e.name()).isEqualTo("Created"));
        }

        @Test
        void readsNestedClassEventLegacyForm() {
            // processor's legacy path: a nested @DomainEvent class -> name = class name, topic only
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.DomainEvent;
                    import eu.exeris.sdk.annotation.DomainEvent.Trigger;
                    @ExerisDomain(name = "Order")
                    public class Order {
                        @DomainEvent(trigger = Trigger.CREATE, topic = "orders.created")
                        public static class OrderCreated {}
                    }
                    """;
            DomainMetadata domain = reader.read(src).orElseThrow();
            assertThat(domain.events()).singleElement().satisfies(e -> {
                assertThat(e.name()).isEqualTo("OrderCreated");
                assertThat(e.topic()).isEqualTo("orders.created");
                assertThat(e.description()).isNull();
                assertThat(e.aggregateType()).isNull(); // withTopic leaves it null, mirroring the processor
            });
        }

        @Test
        void emptyEventsWhenNoDomainEvent() {
            DomainMetadata domain = reader.read(ACCOUNT).orElseThrow();
            assertThat(domain.events()).isEmpty();
        }
    }

    @Nested
    @DisplayName("reader: @Graph / @EventSourced / @Saga / @InternalApi extraction")
    class Facets {

        @Test
        void readsGraphLabelFromNodeClassElseClassName() {
            String withNodeClass = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.Graph;
                    @ExerisDomain(name = "Person")
                    @Graph(nodeClass = "PersonNode")
                    public class Person {}
                    """;
            assertThat(reader.read(withNodeClass).orElseThrow().graphMetadata()).satisfies(g -> {
                assertThat(g.label()).isEqualTo("PersonNode");
                // mirrors the processor: properties null, edges/queries empty
                assertThat(g.properties()).isNull();
                assertThat(g.edges()).isEmpty();
                assertThat(g.queries()).isEmpty();
            });

            String bareGraph = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.Graph;
                    @ExerisDomain(name = "Person")
                    @Graph
                    public class Person {}
                    """;
            // no nodeClass -> label falls back to the class simple name
            assertThat(reader.read(bareGraph).orElseThrow().graphMetadata().label()).isEqualTo("Person");
        }

        @Test
        void absentGraphLeavesMetadataNull() {
            assertThat(reader.read(ACCOUNT).orElseThrow().graphMetadata()).isNull();
        }

        @Test
        void translatesEventSourcedStreamPrefixAndSnapshotThreshold() {
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.EventSourced;
                    @ExerisDomain(name = "Order")
                    @EventSourced(streamPrefix = "orders", snapshotThreshold = 25)
                    public class Order {}
                    """;
            assertThat(reader.read(src).orElseThrow().eventSourced()).satisfies(es -> {
                // streamPrefix -> aggregateType, snapshotThreshold -> snapshotEvery
                assertThat(es.aggregateType()).isEqualTo("orders");
                assertThat(es.snapshotEvery()).isEqualTo(25);
                assertThat(es.enabled()).isTrue(); // builder default preserved
            });
        }

        @Test
        void eventSourcedDefaultsAggregateToClassNameAndSnapshotTo50() {
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.EventSourced;
                    @ExerisDomain(name = "OrderEntity")
                    @EventSourced
                    public class Order {}
                    """;
            assertThat(reader.read(src).orElseThrow().eventSourced()).satisfies(es -> {
                // empty streamPrefix -> class simple name (not @ExerisDomain name)
                assertThat(es.aggregateType()).isEqualTo("Order");
                assertThat(es.snapshotEvery()).isEqualTo(50);
            });
        }

        @Test
        void readsSagaWithStepsSortedByOrder() {
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.Saga;
                    import eu.exeris.sdk.annotation.SagaStep;
                    @ExerisDomain(name = "Fulfillment")
                    @Saga(name = "OrderFulfillment", description = "End-to-end fulfillment",
                            timeout = "PT10M", maxRetries = 7)
                    public class Fulfillment {
                        @SagaStep(order = 2, name = "ship", service = "shipping",
                                command = "ship", compensation = "unship", timeout = "PT1M", parallel = true)
                        public void ship() {}

                        @SagaStep(order = 1, service = "payment", command = "charge")
                        public void pay() {}
                    }
                    """;
            assertThat(reader.read(src).orElseThrow().sagaMetadata()).satisfies(s -> {
                assertThat(s.name()).isEqualTo("OrderFulfillment");
                assertThat(s.description()).isEqualTo("End-to-end fulfillment");
                assertThat(s.timeout()).isEqualTo("PT10M");
                assertThat(s.maxRetries()).isEqualTo(7);
                // sorted by order: pay(1) before ship(2)
                assertThat(s.steps()).extracting("name").containsExactly("pay", "ship");
                assertThat(s.steps()).anySatisfy(step -> {
                    assertThat(step.name()).isEqualTo("ship");
                    assertThat(step.order()).isEqualTo(2);
                    assertThat(step.service()).isEqualTo("shipping");
                    assertThat(step.command()).isEqualTo("ship");
                    assertThat(step.compensation()).isEqualTo("unship");
                    assertThat(step.timeout()).isEqualTo("PT1M");
                    assertThat(step.parallel()).isTrue();
                });
            });
        }

        @Test
        void sagaNameAndStepNameFallBackWhenAbsent() {
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.Saga;
                    import eu.exeris.sdk.annotation.SagaStep;
                    @ExerisDomain(name = "Thing")
                    @Saga
                    public class Settlement {
                        @SagaStep(service = "s", command = "c")
                        public void settle() {}
                    }
                    """;
            assertThat(reader.read(src).orElseThrow().sagaMetadata()).satisfies(s -> {
                assertThat(s.name()).isEqualTo("Settlement"); // class simple name, not @ExerisDomain name
                // present-only: absent timeout/maxRetries keep the SagaMetadata.Builder
                // defaults — and the processor (also present-only on the same builder)
                // produces exactly these, so it is parity, not a silent divergence.
                assertThat(s.timeout()).isEqualTo("PT30M");
                assertThat(s.maxRetries()).isEqualTo(3);
                assertThat(s.steps()).singleElement().satisfies(step -> {
                    assertThat(step.name()).isEqualTo("settle"); // method name fallback
                    assertThat(step.order()).isEqualTo(1);       // default order
                });
            });
        }

        @Test
        void readsInternalApiAsPresenceOnly() {
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.InternalApi;
                    @ExerisDomain(name = "Ledger")
                    @InternalApi(rateLimit = 100)
                    public class Ledger {}
                    """;
            assertThat(reader.read(src).orElseThrow().internalApi()).satisfies(api -> {
                // known SDK<->AST drift: only presence is extracted -> internal = true
                assertThat(api.internal()).isTrue();
                assertThat(api.hidden()).isFalse();
                assertThat(api.readOnly()).isFalse();
            });
        }

        @Test
        void absentFacetsLeaveMetadataNull() {
            DomainMetadata domain = reader.read(ACCOUNT).orElseThrow();
            assertThat(domain.graphMetadata()).isNull();
            assertThat(domain.eventSourced()).isNull();
            assertThat(domain.sagaMetadata()).isNull();
            assertThat(domain.internalApi()).isNull();
        }
    }

    @Nested
    @DisplayName("reader: @Field attribute surface + @Validation (field fidelity)")
    class Fields {

        private static final String PRODUCT = """
                package app.budgethq.product;
                import eu.exeris.sdk.annotation.ExerisDomain;
                import eu.exeris.sdk.annotation.Field;
                import eu.exeris.sdk.annotation.Validation;

                @ExerisDomain(name = "Product")
                public class Product {
                    @Field(label = "SKU", description = "Stock code", unique = true, indexed = true,
                            searchable = true, sortable = true, filterable = true, readOnly = true,
                            inCreate = false, inUpdate = false, computed = true,
                            computedFrom = {"a", "b"})
                    private String sku;

                    @Field
                    @Validation(min = 0, max = 100, pattern = "[A-Z]+")
                    private String code;

                    private long internalCounter; // no @Field
                }
                """;

        @Test
        void readsFullFieldAttributeSurfacePresentOnly() {
            DomainMetadata domain = reader.read(PRODUCT).orElseThrow();
            assertThat(domain.findField("sku")).get().satisfies(field -> {
                assertThat(field.displayName()).isEqualTo("SKU");
                assertThat(field.description()).isEqualTo("Stock code");
                assertThat(field.unique()).isTrue();
                assertThat(field.indexed()).isTrue();
                assertThat(field.searchable()).isTrue();
                assertThat(field.sortable()).isTrue();
                assertThat(field.filterable()).isTrue();
                assertThat(field.readOnly()).isTrue();
                assertThat(field.inCreate()).isFalse();
                assertThat(field.inUpdate()).isFalse();
                assertThat(field.computed()).isTrue();
                assertThat(field.computedFrom()).containsExactly("a", "b");
            });
        }

        @Test
        void plainFieldLeavesSearchSortFilterAtBuilderFalse() {
            // THE parity fix: a field WITH @Field uses the builder (search/sort/filter
            // default false), unlike the processor's no-@Field path which uses simple().
            DomainMetadata domain = reader.read(PRODUCT).orElseThrow();
            assertThat(domain.findField("code")).get().satisfies(field -> {
                assertThat(field.searchable()).isFalse();
                assertThat(field.sortable()).isFalse();
                assertThat(field.filterable()).isFalse();
                assertThat(field.inCreate()).isTrue();  // builder default
                assertThat(field.inUpdate()).isTrue();
            });
        }

        @Test
        void fieldWithoutAnnotationUsesSimpleDefaults() {
            // no @Field -> FieldMetadata.simple() -> search/sort/filter TRUE (the asymmetry)
            DomainMetadata domain = reader.read(PRODUCT).orElseThrow();
            assertThat(domain.findField("internalCounter")).get().satisfies(field -> {
                assertThat(field.searchable()).isTrue();
                assertThat(field.sortable()).isTrue();
                assertThat(field.filterable()).isTrue();
            });
        }

        @Test
        void readsValidationMinMaxPattern() {
            DomainMetadata domain = reader.read(PRODUCT).orElseThrow();
            assertThat(domain.findField("code")).get().satisfies(field -> {
                assertThat(field.min()).isEqualTo(0L);
                assertThat(field.max()).isEqualTo(100L);
                assertThat(field.pattern()).isEqualTo("[A-Z]+");
            });
        }

        @Test
        void readsNegativeValidationMin() {
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.Field;
                    import eu.exeris.sdk.annotation.Validation;
                    @ExerisDomain(name = "T")
                    public class T {
                        @Field @Validation(min = -10, max = -1) private int delta;
                    }
                    """;
            assertThat(reader.read(src).orElseThrow().findField("delta")).get().satisfies(field -> {
                assertThat(field.min()).isEqualTo(-10L); // unary minus unwrapped
                assertThat(field.max()).isEqualTo(-1L);
            });
        }

        @Test
        void deprecatedValidationRequiredFallsBackWhenFieldDoesNot() {
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.Field;
                    import eu.exeris.sdk.annotation.Validation;
                    @ExerisDomain(name = "T")
                    public class T {
                        @Field @Validation(required = true) private String a;
                        @Field(required = false) @Validation(required = true) private String b;
                    }
                    """;
            DomainMetadata domain = reader.read(src).orElseThrow();
            // a: @Field has no required -> deprecated @Validation.required carries over
            assertThat(domain.findField("a")).get().extracting("required").isEqualTo(true);
            // b: canonical @Field.required=false wins; the deprecated fallback does not override
            assertThat(domain.findField("b")).get().extracting("required").isEqualTo(false);
        }

        @Test
        void deprecatedValidateOnMapsToFormLifecycle() {
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.Field;
                    import eu.exeris.sdk.annotation.Validation;
                    @ExerisDomain(name = "T")
                    public class T {
                        @Field @Validation(validateOn = "CREATE") private String onCreate;
                        @Field @Validation(validateOn = "UPDATE") private String onUpdate;
                        @Field @Validation(validateOn = "WHENEVER") private String unknown;
                    }
                    """;
            DomainMetadata domain = reader.read(src).orElseThrow();
            // CREATE -> not in update form
            assertThat(domain.findField("onCreate")).get().satisfies(field -> {
                assertThat(field.inUpdate()).isFalse();
                assertThat(field.inCreate()).isTrue();
            });
            // UPDATE -> not in create form
            assertThat(domain.findField("onUpdate")).get().satisfies(field -> {
                assertThat(field.inCreate()).isFalse();
                assertThat(field.inUpdate()).isTrue();
            });
            // unrecognised value -> no fallback applied (both stay at builder default true)
            assertThat(domain.findField("unknown")).get().satisfies(field -> {
                assertThat(field.inCreate()).isTrue();
                assertThat(field.inUpdate()).isTrue();
            });
        }

        @Test
        void validationIgnoredWithoutField() {
            // @Validation alone (no @Field) -> processor uses simple(), validation dropped
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.Validation;
                    @ExerisDomain(name = "T")
                    public class T {
                        @Validation(min = 5, required = true) private String loose;
                    }
                    """;
            assertThat(reader.read(src).orElseThrow().findField("loose")).get().satisfies(field -> {
                assertThat(field.min()).isNull();        // validation not consulted
                assertThat(field.required()).isFalse();
                assertThat(field.searchable()).isTrue(); // simple() path
            });
        }
    }
}
