package eu.exeris.sdk.sourcemodel.ast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EventHandlerMetadata builder, factory, convenience, normalization")
class EventHandlerMetadataTest {

    @Nested
    @DisplayName("Builder + defaults")
    class BuilderDefaults {
        @Test
        void builderAppliesAnnotationDefaults() {
            EventHandlerMetadata h = EventHandlerMetadata.builder("onEvent").build();
            assertThat(h.name()).isEqualTo("onEvent");
            assertThat(h.order()).isEqualTo(100);
            assertThat(h.priority()).isEqualTo("NORMAL");
            assertThat(h.idempotent()).isTrue();
            assertThat(h.async()).isFalse();
            assertThat(h.timeout()).isEqualTo("PT30S");
            assertThat(h.transactionMode()).isEqualTo("REQUIRED");
            // Empty-string defaults normalize to null; list defaults to empty.
            assertThat(h.event()).isNull();
            assertThat(h.description()).isNull();
            assertThat(h.topic()).isNull();
            assertThat(h.condition()).isNull();
            assertThat(h.triggerStep()).isNull();
            assertThat(h.events()).isEmpty();
            assertThat(h.eventClassNames()).isEmpty();
            assertThat(h.expectedEvents()).isEmpty();
            assertThat(h.failureEvents()).isEmpty();
        }

        @Test
        void builderCarriesEverySetter() {
            EventHandlerMetadata h = EventHandlerMetadata.builder("h")
                    .description("desc")
                    .event("OrderCreated")
                    .events(List.of("A", "B"))
                    .eventClassNames(List.of("com.acme.A"))
                    .topic("orders")
                    .condition("event.amount > 0")
                    .order(5)
                    .priority("HIGH")
                    .idempotent(false)
                    .async(true)
                    .timeout("PT1S")
                    .transactionMode("REQUIRES_NEW")
                    .triggerStep("step")
                    .expectedEvents(List.of("Done"))
                    .failureEvents(List.of("Failed"))
                    .build();
            assertThat(h.description()).isEqualTo("desc");
            assertThat(h.event()).isEqualTo("OrderCreated");
            assertThat(h.events()).containsExactly("A", "B");
            assertThat(h.eventClassNames()).containsExactly("com.acme.A");
            assertThat(h.topic()).isEqualTo("orders");
            assertThat(h.condition()).isEqualTo("event.amount > 0");
            assertThat(h.order()).isEqualTo(5);
            assertThat(h.priority()).isEqualTo("HIGH");
            assertThat(h.idempotent()).isFalse();
            assertThat(h.async()).isTrue();
            assertThat(h.timeout()).isEqualTo("PT1S");
            assertThat(h.transactionMode()).isEqualTo("REQUIRES_NEW");
            assertThat(h.triggerStep()).isEqualTo("step");
            assertThat(h.expectedEvents()).containsExactly("Done");
            assertThat(h.failureEvents()).containsExactly("Failed");
        }
    }

    @Nested
    @DisplayName("simple factory")
    class SimpleFactory {
        @Test
        void simpleSetsNameAndEventWithDefaults() {
            EventHandlerMetadata h = EventHandlerMetadata.simple("onCreated", "OrderCreated");
            assertThat(h.name()).isEqualTo("onCreated");
            assertThat(h.event()).isEqualTo("OrderCreated");
            assertThat(h.order()).isEqualTo(100);
            assertThat(h.idempotent()).isTrue();
        }
    }

    @Nested
    @DisplayName("convenience predicates")
    class Convenience {
        @Test
        void isSagaHandlerTrueWhenTriggerStepPresent() {
            assertThat(EventHandlerMetadata.builder("h").triggerStep("step").build().isSagaHandler()).isTrue();
        }

        @Test
        void isSagaHandlerTrueWhenExpectedEventsPresent() {
            assertThat(EventHandlerMetadata.builder("h").expectedEvents(List.of("Done")).build().isSagaHandler()).isTrue();
        }

        @Test
        void isSagaHandlerTrueWhenFailureEventsPresent() {
            assertThat(EventHandlerMetadata.builder("h").failureEvents(List.of("Failed")).build().isSagaHandler()).isTrue();
        }

        @Test
        void isSagaHandlerFalseForPlainProjectionHandler() {
            assertThat(EventHandlerMetadata.simple("h", "OrderCreated").isSagaHandler()).isFalse();
        }

        @Test
        void hasConditionReflectsPresence() {
            assertThat(EventHandlerMetadata.simple("h", "E").hasCondition()).isFalse();
            assertThat(EventHandlerMetadata.builder("h").condition("event.x").build().hasCondition()).isTrue();
        }
    }

    @Nested
    @DisplayName("compact-constructor normalization")
    class Normalization {
        @Test
        void blankStringsBecomeNull() {
            EventHandlerMetadata h = new EventHandlerMetadata(
                    "  ", "  ", "  ", null, null, "  ", "  ",
                    100, "  ", true, false, "  ", "  ",
                    "  ", null, null);
            assertThat(h.name()).isNull();
            assertThat(h.description()).isNull();
            assertThat(h.event()).isNull();
            assertThat(h.topic()).isNull();
            assertThat(h.condition()).isNull();
            assertThat(h.priority()).isNull();
            assertThat(h.timeout()).isNull();
            assertThat(h.transactionMode()).isNull();
            assertThat(h.triggerStep()).isNull();
        }

        @Test
        void nullListsBecomeEmpty() {
            EventHandlerMetadata h = new EventHandlerMetadata(
                    "h", null, null, null, null, null, null,
                    0, null, false, false, null, null,
                    null, null, null);
            assertThat(h.events()).isEmpty();
            assertThat(h.eventClassNames()).isEmpty();
            assertThat(h.expectedEvents()).isEmpty();
            assertThat(h.failureEvents()).isEmpty();
        }

        @Test
        void providedListsAreCopiedDefensively() {
            List<String> mutable = new java.util.ArrayList<>(Arrays.asList("A"));
            EventHandlerMetadata h = EventHandlerMetadata.builder("h").events(mutable).build();
            mutable.add("B");
            assertThat(h.events()).containsExactly("A");
        }
    }
}
