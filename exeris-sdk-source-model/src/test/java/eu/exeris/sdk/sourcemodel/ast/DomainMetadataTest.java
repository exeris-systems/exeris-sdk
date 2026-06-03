package eu.exeris.sdk.sourcemodel.ast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the {@link DomainMetadata} convenience methods and Builder defaults.
 * The round-trip wire-format test ({@link AstJsonRoundTripTest}) exercises
 * full-object serialization; this class targets branching logic
 * (effective*, plural rules, find*, is*) that round-trip alone cannot reach.
 */
@DisplayName("DomainMetadata: derived methods + Builder")
class DomainMetadataTest {

    private static DomainMetadata.Builder base() {
        return DomainMetadata.builder("Order", "com.acme.domain");
    }

    @Nested
    @DisplayName("Identity / naming derivations")
    class Naming {
        @Test
        void fullyQualifiedNameJoinsPackageAndEntity() {
            assertThat(base().build().fullyQualifiedName()).isEqualTo("com.acme.domain.Order");
        }

        @Test
        void effectiveTableNameUsesExplicitWhenProvided() {
            assertThat(base().tableName("orders_v2").build().effectiveTableName()).isEqualTo("orders_v2");
        }

        @Test
        void effectiveTableNameDerivesSnakeCaseWhenBlank() {
            // Empty string is the Builder default for tableName.
            assertThat(base().build().effectiveTableName()).isEqualTo("order");
        }

        @Test
        void effectiveTableNameSnakeCasesCamelEntityName() {
            assertThat(DomainMetadata.builder("OrderLineItem", "p").build().effectiveTableName())
                    .isEqualTo("order_line_item");
        }

        @Test
        void effectivePathUsesExplicitWhenProvided() {
            assertThat(base().path("/v2/orders").build().effectivePath()).isEqualTo("/v2/orders");
        }

        @Test
        void effectivePathDerivesKebabPluralWhenBlank() {
            assertThat(base().build().effectivePath()).isEqualTo("/orders");
            assertThat(DomainMetadata.builder("OrderLineItem", "p").build().effectivePath())
                    .isEqualTo("/order-line-items");
        }

        @Test
        void effectiveAggregateFallsBackToEntityName() {
            assertThat(base().build().effectiveAggregate()).isEqualTo("Order");
            assertThat(base().aggregate("CustomerOrder").build().effectiveAggregate()).isEqualTo("CustomerOrder");
        }

        @Test
        void displayNameSplitsOnCapitals() {
            assertThat(DomainMetadata.builder("OrderLineItem", "p").build().displayName())
                    .isEqualTo("Order Line Item");
            assertThat(base().build().displayName()).isEqualTo("Order");
        }
    }

    @Nested
    @DisplayName("pluralName covers each branch")
    class Plural {
        @Test
        void simpleNounAddsS() {
            assertThat(DomainMetadata.builder("Order", "p").build().pluralName()).isEqualTo("Orders");
        }

        @Test
        void sibilantEndingAddsEs() {
            assertThat(DomainMetadata.builder("Address", "p").build().pluralName()).isEqualTo("Addresses");
            assertThat(DomainMetadata.builder("Box", "p").build().pluralName()).isEqualTo("Boxes");
            assertThat(DomainMetadata.builder("Quiz", "p").build().pluralName()).isEqualTo("Quizes");
            assertThat(DomainMetadata.builder("Branch", "p").build().pluralName()).isEqualTo("Branches");
            assertThat(DomainMetadata.builder("Brush", "p").build().pluralName()).isEqualTo("Brushes");
        }

        @Test
        void consonantYBecomesIes() {
            assertThat(DomainMetadata.builder("Category", "p").build().pluralName()).isEqualTo("Categories");
            assertThat(DomainMetadata.builder("Country", "p").build().pluralName()).isEqualTo("Countries");
        }

        @Test
        void vowelYJustAddsS() {
            assertThat(DomainMetadata.builder("Day", "p").build().pluralName()).isEqualTo("Days");
            assertThat(DomainMetadata.builder("Toy", "p").build().pluralName()).isEqualTo("Toys");
        }

        @Test
        void singleLetterYDoesNotTriggerIesRule() {
            // Branch coverage: length-1 guards the substring math from blowing up.
            assertThat(DomainMetadata.builder("Y", "p").build().pluralName()).isEqualTo("Ys");
        }
    }

    @Nested
    @DisplayName("Collection-aware predicates")
    class Predicates {
        @Test
        void hasFieldsTrueOnlyWhenNonEmpty() {
            assertThat(base().build().hasFields()).isFalse();
            assertThat(base().fields(List.of(FieldMetadata.simple("name", "String"))).build().hasFields()).isTrue();
        }

        @Test
        void hasActionsTrueOnlyWhenNonEmpty() {
            assertThat(base().build().hasActions()).isFalse();
            assertThat(base().actions(List.of(ActionMetadata.simple("approve"))).build().hasActions()).isTrue();
        }

        @Test
        void hasEventsTrueOnlyWhenNonEmpty() {
            assertThat(base().build().hasEvents()).isFalse();
            assertThat(base().events(List.of(DomainEventMetadata.simple("OrderCreated"))).build().hasEvents()).isTrue();
        }

        @Test
        void hasRelationshipsTrueOnlyWhenNonEmpty() {
            assertThat(base().build().hasRelationships()).isFalse();
            assertThat(base().relationships(List.of(RelationshipMetadata.manyToOne("customer", "Customer")))
                    .build().hasRelationships()).isTrue();
        }

        @Test
        void hasFieldsHandlesNullList() {
            // Builder default is List.of(), but consumer code may construct via the
            // canonical constructor with null lists — the helper must tolerate it.
            DomainMetadata nullLists = new DomainMetadata("Order", "p", "", "", "", "", "v1",
                    null, true, false, false, false, false, false, false, false,
                    null, null, false, false, "PT5M", "", false, "english", null,
                    null, null, null, null, null, null, null, null, null, null, null);
            assertThat(nullLists.hasFields()).isFalse();
            assertThat(nullLists.hasActions()).isFalse();
            assertThat(nullLists.hasEvents()).isFalse();
            assertThat(nullLists.hasRelationships()).isFalse();
        }
    }

    @Nested
    @DisplayName("Composite-feature flags")
    class CompositeFlags {
        @Test
        void isEventSourcedReflectsPresence() {
            assertThat(base().build().isEventSourced()).isFalse();
            assertThat(base().eventSourced(EventSourcedMetadata.enabled("Order")).build().isEventSourced()).isTrue();
        }

        @Test
        void isSagaReflectsPresence() {
            assertThat(base().build().isSaga()).isFalse();
            assertThat(base().sagaMetadata(SagaMetadata.simple("OrderSaga")).build().isSaga()).isTrue();
        }

        @Test
        void hasGraphMetadataReflectsPresence() {
            assertThat(base().build().hasGraphMetadata()).isFalse();
            assertThat(base().graphMetadata(GraphMetadata.simple("Order")).build().hasGraphMetadata()).isTrue();
        }

        @Test
        void isInternalRequiresHiddenInternalApi() {
            assertThat(base().build().isInternal()).isFalse();
            // Non-hidden InternalApi present but not hidden → still false.
            assertThat(base().internalApi(InternalApiMetadata.readOnly("audit")).build().isInternal()).isFalse();
            assertThat(base().internalApi(InternalApiMetadata.hidden("legacy")).build().isInternal()).isTrue();
        }
    }

    @Nested
    @DisplayName("findField branching")
    class FindField {
        @Test
        void findFieldReturnsMatchingFieldByName() {
            FieldMetadata amount = FieldMetadata.simple("amount", "BigDecimal");
            DomainMetadata d = base().fields(List.of(FieldMetadata.simple("orderNumber", "String"), amount)).build();

            Optional<FieldMetadata> found = d.findField("amount");
            assertThat(found).isPresent().get().isEqualTo(amount);
        }

        @Test
        void findFieldReturnsEmptyWhenAbsent() {
            DomainMetadata d = base().fields(List.of(FieldMetadata.simple("orderNumber", "String"))).build();
            assertThat(d.findField("nope")).isEmpty();
        }

        @Test
        void findFieldReturnsEmptyOnNullName() {
            DomainMetadata d = base().fields(List.of(FieldMetadata.simple("orderNumber", "String"))).build();
            assertThat(d.findField(null)).isEmpty();
        }

        @Test
        void findFieldReturnsEmptyOnEmptyFieldsList() {
            // Construct directly with an empty fields list to exercise the guard.
            DomainMetadata d = new DomainMetadata("Order", "p", "", "", "", "", "v1",
                    List.of(), true, false, false, false, false, false, false, false,
                    List.of(), List.of(), false, false, "PT5M", "", false, "english", null,
                    null, null, null, null, null, null, null, null, null, null, null);
            assertThat(d.findField("anything")).isEmpty();
        }
    }

    @Nested
    @DisplayName("Builder default invariants")
    class BuilderDefaults {
        @Test
        void unsetFieldsHaveSafeDefaults() {
            DomainMetadata d = base().build();
            assertThat(d.entityName()).isEqualTo("Order");
            assertThat(d.packageName()).isEqualTo("com.acme.domain");
            assertThat(d.apiVersion()).isEqualTo("v1");
            assertThat(d.cacheTtl()).isEqualTo("PT5M");
            assertThat(d.searchConfig()).isEqualTo("english");
            assertThat(d.restApi()).isTrue();
            assertThat(d.graphqlApi()).isFalse();
            assertThat(d.tenantScoped()).isFalse();
            assertThat(d.tags()).isEmpty();
            assertThat(d.fields()).isEmpty();
            assertThat(d.actions()).isEmpty();
            assertThat(d.events()).isEmpty();
            assertThat(d.relationships()).isEmpty();
            assertThat(d.projections()).isEmpty();
            assertThat(d.uiMetadata()).isNull();
        }

        @Test
        void builderSettersAreFluent() {
            DomainMetadata d = base()
                    .module("sales").path("/orders").aggregate("CustomerOrder").description("desc")
                    .apiVersion("v2").tags(List.of("a", "b"))
                    .restApi(false).graphqlApi(true).realTimeApi(true).internalClient(true)
                    .tenantScoped(true).softDelete(true).audited(true).versioned(true)
                    .roles(List.of("ROLE_X")).permissions(List.of("p:r")).sensitive(true)
                    .cacheable(true).cacheTtl("PT10M").cacheRegion("region")
                    .fullTextSearch(true).searchConfig("german")
                    .tableName("orders")
                    .projections(List.of(ProjectionMetadata.simple("summary", List.of("id"))))
                    .uiMetadata(UIMetadata.defaults())
                    .graphMetadata(GraphMetadata.simple("Order"))
                    .sagaMetadata(SagaMetadata.simple("S"))
                    .eventSourced(EventSourcedMetadata.disabled())
                    .internalApi(InternalApiMetadata.internal("svc"))
                    .systemFields(SystemFieldsMetadata.defaults())
                    .build();

            assertThat(d.module()).isEqualTo("sales");
            assertThat(d.aggregate()).isEqualTo("CustomerOrder");
            assertThat(d.tags()).containsExactly("a", "b");
            assertThat(d.realTimeApi()).isTrue();
            assertThat(d.internalClient()).isTrue();
            assertThat(d.versioned()).isTrue();
            assertThat(d.permissions()).containsExactly("p:r");
            assertThat(d.sensitive()).isTrue();
            assertThat(d.cacheRegion()).isEqualTo("region");
            assertThat(d.fullTextSearch()).isTrue();
            assertThat(d.searchConfig()).isEqualTo("german");
            assertThat(d.tableName()).isEqualTo("orders");
            assertThat(d.projections()).hasSize(1);
            assertThat(d.uiMetadata()).isNotNull();
            assertThat(d.graphMetadata()).isNotNull();
            assertThat(d.sagaMetadata()).isNotNull();
            assertThat(d.eventSourced()).isNotNull();
            assertThat(d.internalApi()).isNotNull();
            assertThat(d.systemFields()).isNotNull();
        }
    }
}
