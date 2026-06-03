package eu.exeris.sdk.sourcemodel.ast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RelationshipMetadata: factories, accessor defaults, builder")
class RelationshipMetadataTest {

    @Test
    void manyToOneFactoryPicksSafeDefaults() {
        RelationshipMetadata r = RelationshipMetadata.manyToOne("customer", "Customer");
        assertThat(r.name()).isEqualTo("customer");
        assertThat(r.fieldName()).isEqualTo("customer");
        assertThat(r.targetEntity()).isEqualTo("Customer");
        assertThat(r.type()).isEqualTo(RelationshipMetadata.RelationType.MANY_TO_ONE);
        assertThat(r.cascade()).isEqualTo(RelationshipMetadata.CascadeType.NONE);
        assertThat(r.fetch()).isEqualTo(RelationshipMetadata.FetchType.LAZY);
        assertThat(r.lazy()).isTrue();
        assertThat(r.optional()).isTrue();
        assertThat(r.orphanRemoval()).isFalse();
        assertThat(r.displayField()).isEqualTo("name");
        assertThat(r.valueField()).isEqualTo("id");
        assertThat(r.joinColumns()).isEmpty();
    }

    @Test
    void oneToManyFactoryUsesMappedByAndCascadeAll() {
        RelationshipMetadata r = RelationshipMetadata.oneToMany("items", "OrderItem", "order");
        assertThat(r.type()).isEqualTo(RelationshipMetadata.RelationType.ONE_TO_MANY);
        assertThat(r.mappedBy()).isEqualTo("order");
        assertThat(r.cascade()).isEqualTo(RelationshipMetadata.CascadeType.ALL);
    }

    @Test
    void accessorsFallBackWhenNull() {
        // Canonical constructor with all three accessor-backed fields null
        RelationshipMetadata r = new RelationshipMetadata("customer", null, "Customer",
                RelationshipMetadata.RelationType.MANY_TO_ONE, null, true,
                RelationshipMetadata.CascadeType.NONE, RelationshipMetadata.FetchType.LAZY,
                true, false, null, null, null, List.of());
        assertThat(r.fieldName()).isEqualTo("customer");
        assertThat(r.displayField()).isEqualTo("name");
        assertThat(r.valueField()).isEqualTo("id");
    }

    @Test
    void enumsHaveExpectedValues() {
        // Touching each enum constant ensures values() / valueOf() coverage.
        assertThat(RelationshipMetadata.RelationType.values())
                .containsExactly(
                        RelationshipMetadata.RelationType.ONE_TO_ONE,
                        RelationshipMetadata.RelationType.ONE_TO_MANY,
                        RelationshipMetadata.RelationType.MANY_TO_ONE,
                        RelationshipMetadata.RelationType.MANY_TO_MANY);
        assertThat(RelationshipMetadata.CascadeType.valueOf("ALL"))
                .isEqualTo(RelationshipMetadata.CascadeType.ALL);
        assertThat(RelationshipMetadata.FetchType.values())
                .containsExactly(RelationshipMetadata.FetchType.LAZY, RelationshipMetadata.FetchType.EAGER);
    }

    @Test
    void builderPropagatesEverySetter() {
        RelationshipMetadata r = RelationshipMetadata.builder("customer", "Customer")
                .fieldName("customerRef")
                .type(RelationshipMetadata.RelationType.MANY_TO_MANY)
                .mappedBy("orders")
                .lazy(false)
                .cascade(RelationshipMetadata.CascadeType.MERGE)
                .fetch(RelationshipMetadata.FetchType.EAGER)
                .optional(false)
                .orphanRemoval(true)
                .displayField("displayName")
                .valueField("uuid")
                .searchEndpoint("/api/customers/search")
                .joinColumns(List.of("customer_id"))
                .build();
        assertThat(r.fieldName()).isEqualTo("customerRef");
        assertThat(r.type()).isEqualTo(RelationshipMetadata.RelationType.MANY_TO_MANY);
        assertThat(r.mappedBy()).isEqualTo("orders");
        assertThat(r.lazy()).isFalse();
        assertThat(r.cascade()).isEqualTo(RelationshipMetadata.CascadeType.MERGE);
        assertThat(r.fetch()).isEqualTo(RelationshipMetadata.FetchType.EAGER);
        assertThat(r.optional()).isFalse();
        assertThat(r.orphanRemoval()).isTrue();
        assertThat(r.displayField()).isEqualTo("displayName");
        assertThat(r.valueField()).isEqualTo("uuid");
        assertThat(r.searchEndpoint()).isEqualTo("/api/customers/search");
        assertThat(r.joinColumns()).containsExactly("customer_id");
    }
}
