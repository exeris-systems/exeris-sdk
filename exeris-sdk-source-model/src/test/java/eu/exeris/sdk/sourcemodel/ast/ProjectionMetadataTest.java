package eu.exeris.sdk.sourcemodel.ast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProjectionMetadata builder, factories, convenience, normalization")
class ProjectionMetadataTest {

    @Nested
    @DisplayName("Builder + defaults")
    class BuilderDefaults {
        @Test
        void builderAppliesDefaults() {
            ProjectionMetadata p = ProjectionMetadata.builder("orders").build();
            assertThat(p.name()).isEqualTo("orders");
            assertThat(p.cacheable()).isFalse();
            // Blank-string defaults normalize to null; list defaults to empty.
            assertThat(p.description()).isNull();
            assertThat(p.topicPattern()).isNull();
            assertThat(p.model()).isNull();
            assertThat(p.schema()).isNull();
            assertThat(p.aggregateTypes()).isEmpty();
            assertThat(p.events()).isEmpty();
            assertThat(p.eventClassNames()).isEmpty();
            assertThat(p.fields()).isEmpty();
        }

        @Test
        void builderCarriesEverySetter() {
            ProjectionMetadata p = ProjectionMetadata.builder("p")
                    .description("desc")
                    .aggregateTypes(List.of("Order", "Invoice"))
                    .events(List.of("OrderCreated"))
                    .eventClassNames(List.of("com.acme.OrderCreated"))
                    .topicPattern("orders\\..*")
                    .model("OrderView")
                    .schema("read_models")
                    .fields(List.of("id", "total"))
                    .cacheable(true)
                    .build();
            assertThat(p.description()).isEqualTo("desc");
            assertThat(p.aggregateTypes()).containsExactly("Order", "Invoice");
            assertThat(p.events()).containsExactly("OrderCreated");
            assertThat(p.eventClassNames()).containsExactly("com.acme.OrderCreated");
            assertThat(p.topicPattern()).isEqualTo("orders\\..*");
            assertThat(p.model()).isEqualTo("OrderView");
            assertThat(p.schema()).isEqualTo("read_models");
            assertThat(p.fields()).containsExactly("id", "total");
            assertThat(p.cacheable()).isTrue();
        }

        @Test
        void aggregateTypeSetsSingleSource() {
            assertThat(ProjectionMetadata.builder("p").aggregateType("Order").build().aggregateTypes())
                    .containsExactly("Order");
        }
    }

    @Nested
    @DisplayName("factories")
    class Factories {
        @Test
        void simplePreservesPre070Shape() {
            ProjectionMetadata p = ProjectionMetadata.simple("summary", List.of("id", "total"));
            assertThat(p.name()).isEqualTo("summary");
            assertThat(p.fields()).containsExactly("id", "total");
            assertThat(p.cacheable()).isFalse();
            assertThat(p.description()).isNull();
            assertThat(p.aggregateTypes()).isEmpty();
        }

        @Test
        void ofLinksSingleAggregateAndFields() {
            ProjectionMetadata p = ProjectionMetadata.of("orderView", "Order", List.of("id", "status"));
            assertThat(p.name()).isEqualTo("orderView");
            assertThat(p.aggregateTypes()).containsExactly("Order");
            assertThat(p.fields()).containsExactly("id", "status");
            assertThat(p.hasSourceAggregate()).isTrue();
        }
    }

    @Nested
    @DisplayName("convenience predicates")
    class Convenience {
        @Test
        void hasSourceAggregateReflectsPresence() {
            assertThat(ProjectionMetadata.simple("p", List.of("id")).hasSourceAggregate()).isFalse();
            assertThat(ProjectionMetadata.of("p", "Order", List.of("id")).hasSourceAggregate()).isTrue();
        }

        @Test
        void hasFieldsReflectsPresence() {
            assertThat(ProjectionMetadata.builder("p").build().hasFields()).isFalse();
            assertThat(ProjectionMetadata.simple("p", List.of("id")).hasFields()).isTrue();
        }
    }

    @Nested
    @DisplayName("compact-constructor normalization")
    class Normalization {
        @Test
        void blankStringsBecomeNullAndNullListsBecomeEmpty() {
            ProjectionMetadata p = new ProjectionMetadata(
                    "p", "  ", null, null, null, "  ", "  ", "  ", null, false);
            assertThat(p.description()).isNull();
            assertThat(p.topicPattern()).isNull();
            assertThat(p.model()).isNull();
            assertThat(p.schema()).isNull();
            assertThat(p.aggregateTypes()).isEmpty();
            assertThat(p.events()).isEmpty();
            assertThat(p.eventClassNames()).isEmpty();
            assertThat(p.fields()).isEmpty();
        }

        @Test
        void providedListsAreCopiedDefensively() {
            List<String> mutable = new ArrayList<>(Arrays.asList("id"));
            ProjectionMetadata p = ProjectionMetadata.builder("p").fields(mutable).build();
            mutable.add("total");
            assertThat(p.fields()).containsExactly("id");
        }
    }
}
