package eu.exeris.sdk.brief;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The brief records' wire format under Jackson 3: a brief read from YAML, written as JSON and
 * read back is equal to itself, and what it writes still validates against the emitted schema.
 */
@DisplayName("brief wire format: Jackson 3 round trip, schema-valid on the way out")
class BriefJsonRoundTripTest {

    @Test
    @DisplayName("the full fixture survives YAML → record → JSON → record, and every brief record is on it")
    void roundTrip() {
        Brief read = BriefDocuments.bind(BriefDocuments.fixture("ready.brief.yaml"));
        assertThat(read).isNotNull();

        String written = BriefDocuments.JSON.writeValueAsString(read);
        Brief back = BriefDocuments.JSON.readValue(written, Brief.class);
        assertThat(back).isEqualTo(read);

        JsonNode tree = BriefDocuments.JSON.readTree(written);
        assertThat(BriefDocuments.schemaErrors(tree)).as("the writer's output must validate").isEmpty();
        assertThat(tree.at("/workflows/0/criteria/0/kind").asString()).isEqualTo("when");
        assertThat(tree.at("/glossary/0/dataScope").asString())
                .as("DataScope is reused by type and keeps the AST's names").isEqualTo("TENANT");
        assertThat(tree.at("/openQuestions/2/blocking").isBoolean())
                .as("a false blocking flag is written, not dropped").isTrue();
        assertThat(tree.at("/actors/1").has("roles")).as("NON_NULL writes an empty list").isTrue();

        Set<Class<?>> reached = new TreeSet<>((a, b) -> a.getName().compareTo(b.getName()));
        collect(Brief.class, reached);
        assertThat(reached).as("the fixture must exercise every record the brief reaches")
                .extracting(Class::getSimpleName)
                .containsExactlyInAnyOrder("Actor", "Brief", "EarsCriterion", "FulfilledBy", "GlossaryTerm",
                        "Integration", "NonFunctionalRequirement", "OpenQuestion", "Workflow");
        assertThat(read.actors()).isNotEmpty();
        assertThat(read.glossary().getFirst().fulfilledBy()).isNotEmpty();
        assertThat(read.workflows().getFirst().criteria()).hasSize(4);
        assertThat(read.nonFunctional()).isNotEmpty();
        assertThat(read.integrations()).isNotEmpty();
        assertThat(read.openQuestions()).hasSize(3);
    }

    private static void collect(Class<?> type, Set<Class<?>> into) {
        if (!type.isRecord() || !into.add(type)) {
            return;
        }
        for (RecordComponent component : type.getRecordComponents()) {
            Class<?> carried = component.getType();
            if (component.getGenericType() instanceof ParameterizedType parameterized
                    && parameterized.getActualTypeArguments()[0] instanceof Class<?> element) {
                carried = element;
            }
            collect(carried, into);
        }
    }
}
