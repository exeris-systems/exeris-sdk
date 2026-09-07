package eu.exeris.sdk.sourcemodel.ast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A {@code null} element in a list component is refused by name, on every record that has one.
 *
 * <p>The list components already guarded a null <em>list</em>. A null <em>element</em> inside a
 * non-null list reached {@code List.copyOf}, which rejects it with a bare {@code NullPointerException}
 * carrying no message — and on the deserialization path that is raised from inside Jackson, so the
 * failure named neither the record, the component, nor the position. These tests pin the diagnosis
 * rather than the rejection: refusing the element was always correct.
 */
@DisplayName("AST list components: a null element is refused by name")
class AstListNullElementTest {

    private final ObjectMapper mapper = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
            .build();

    /** {@code Arrays.asList} rather than {@code List.of}, which cannot hold the null under test. */
    private static List<String> withNullAt1() {
        return Arrays.asList("Order", null);
    }

    @Nested
    @DisplayName("constructed directly")
    class Constructed {

        @Test
        @DisplayName("the message names the component and the index")
        void namesComponentAndIndex() {
            assertThatThrownBy(() -> ProjectionMetadata.builder("p").aggregateTypes(withNullAt1()).build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("aggregateTypes[1]")
                    .hasMessageContaining("must not contain null elements");
        }

        @Test
        @DisplayName("every record with a list component refuses one")
        void everyRecordRefusesOne() {
            assertThatThrownBy(() -> ProjectionMetadata.builder("p").events(withNullAt1()).build())
                    .hasMessageContaining("events[1]");
            assertThatThrownBy(() -> ActionMetadata.builder("a").permissions(withNullAt1()).build())
                    .hasMessageContaining("permissions[1]");
            assertThatThrownBy(() -> EventHandlerMetadata.builder("h").expectedEvents(withNullAt1()).build())
                    .hasMessageContaining("expectedEvents[1]");
            assertThatThrownBy(() -> DomainEventMetadata.builder("e").sensitiveFields(withNullAt1()).build())
                    .hasMessageContaining("sensitiveFields[1]");
            assertThatThrownBy(() -> BlobMetadata.ofContentTypes(withNullAt1()))
                    .hasMessageContaining("contentTypes[1]");
        }

        @Test
        @DisplayName("an absent list is still an empty one, and a good list still copies")
        void absentAndGoodListsAreUnchanged() {
            assertThat(ProjectionMetadata.builder("p").aggregateTypes(null).build().aggregateTypes()).isEmpty();
            ProjectionMetadata p = ProjectionMetadata.builder("p").aggregateTypes(List.of("Order")).build();
            assertThat(p.aggregateTypes()).containsExactly("Order");
            assertThatThrownBy(() -> p.aggregateTypes().add("Invoice"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("arriving over the wire")
    class Deserialized {

        @Test
        @DisplayName("a null element in a JSON array is diagnosed, not reported as a bare NPE")
        void jsonNullElementIsDiagnosed() {
            String doc = "{\"name\":\"X\",\"aggregateTypes\":[\"Order\",null],\"fields\":[]}";
            assertThatThrownBy(() -> mapper.readValue(doc, ProjectionMetadata.class))
                    .hasMessageContaining("aggregateTypes[1]")
                    .hasMessageContaining("must not contain null elements");
        }

        @Test
        @DisplayName("an absent array still deserializes to an empty list")
        void absentArrayStillEmpty() {
            ProjectionMetadata p = mapper.readValue("{\"name\":\"X\",\"fields\":[]}", ProjectionMetadata.class);
            assertThat(p.aggregateTypes()).isEmpty();
            assertThat(p.events()).isEmpty();
        }
    }
}
