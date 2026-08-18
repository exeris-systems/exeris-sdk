package eu.exeris.sdk.sourcemodel.ast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ScheduleMetadata")
class ScheduleMetadataTest {

    @Test
    void factoriesSetTheMatchingKind() {
        assertThat(ScheduleMetadata.cron("0 3 * * *").kind())
                .isEqualTo(ScheduleMetadata.TriggerKind.CRON);
        assertThat(ScheduleMetadata.every("PT15M").kind())
                .isEqualTo(ScheduleMetadata.TriggerKind.INTERVAL);
        assertThat(ScheduleMetadata.at("2026-09-01T00:00:00Z").kind())
                .isEqualTo(ScheduleMetadata.TriggerKind.ONE_SHOT);
    }

    @Test
    void expressionIsStoredVerbatim() {
        // source-model interprets nothing — no cron parsing, no duration parsing.
        // Validating the expression is build-time tooling's job; storing something
        // other than what the author wrote would put an opinion in a carrier.
        assertThat(ScheduleMetadata.cron("0 3 * * *").expression()).isEqualTo("0 3 * * *");
        assertThat(ScheduleMetadata.every("PT1H30M").expression()).isEqualTo("PT1H30M");
    }

    @Test
    void onlyOneShotIsNonRecurring() {
        assertThat(ScheduleMetadata.cron("0 3 * * *").isRecurring()).isTrue();
        assertThat(ScheduleMetadata.every("PT15M").isRecurring()).isTrue();
        assertThat(ScheduleMetadata.at("2026-09-01T00:00:00Z").isRecurring()).isFalse();
    }

    @Test
    void kindAndExpressionAreBothRequired() {
        assertThatThrownBy(() -> new ScheduleMetadata(null, "0 3 * * *"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("kind");
        assertThatThrownBy(() -> new ScheduleMetadata(ScheduleMetadata.TriggerKind.CRON, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("expression");
        assertThatThrownBy(() -> new ScheduleMetadata(ScheduleMetadata.TriggerKind.CRON, "   "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("expression");
    }

    @Test
    void triggerKindsAreExactlyTheThreeTheKernelCovers() {
        // The kernel's JobTrigger covers cron, fixed-interval and one-shot, and
        // excludes event-driven deliberately (ADR-057 obligation 7). A fourth
        // constant appearing here would mean the SDK widened at the declaration
        // site what the kernel narrowed at the execution site.
        assertThat(ScheduleMetadata.TriggerKind.values())
                .containsExactly(ScheduleMetadata.TriggerKind.CRON,
                        ScheduleMetadata.TriggerKind.INTERVAL,
                        ScheduleMetadata.TriggerKind.ONE_SHOT);
    }
}
