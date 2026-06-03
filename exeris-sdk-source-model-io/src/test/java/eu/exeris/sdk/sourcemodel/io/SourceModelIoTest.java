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
}
