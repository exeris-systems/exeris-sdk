package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

/**
 * AST facet for a {@code @Schedule}d action — a trigger that fires the action
 * without a client call. Carried by {@link ActionMetadata#schedule()}.
 *
 * <p>The annotation declares three mutually exclusive attributes ({@code cron} /
 * {@code every} / {@code at}); this record collapses them into one
 * {@link TriggerKind} discriminator plus the verbatim {@code expression}. Three
 * parallel nullable strings on the wire would let a consumer read a combination the
 * annotation only forbids in prose — a cron <em>and</em> an interval — so the
 * discriminator is what makes that unrepresentable. Deriving the kind is the
 * extractor's job; rejecting a multi-kind or empty declaration is build-time
 * tooling's. {@code source-model} stores the expression and interprets nothing
 * (zero runtime coupling).
 *
 * <p>This record is class-level {@code NON_NULL} rather than the {@code NON_DEFAULT}
 * used on the larger AST records, and that is load-bearing: {@link TriggerKind#CRON}
 * is ordinal 0, so under {@code NON_DEFAULT} an explicit {@code CRON} would be
 * dropped on serialization and read back as {@code null}. Unlike the {@code GLOBAL}
 * case in ADR-059 there is no fallback accessor to catch it — the schedule would
 * simply cease to exist between write and read.
 *
 * <p><strong>Reserved surface (ADR-070).</strong> No {@code exeris-tooling}
 * processor populates this record and no generator consumes it; the kernel holds
 * {@code …spi.scheduling} at tier {@code preview}, so the shape is excluded from the
 * 1.0.0 freeze and a 1.x minor may still change it. Which identity a scheduled
 * action runs as is an open question tracked in ADR-070 — a declared trigger has no
 * submission event, and the kernel captures identity at submission.
 *
 * @since 0.11.0
 * @see ActionMetadata#schedule()
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScheduleMetadata(
        TriggerKind kind,
        String expression
) {

    /**
     * The trigger kinds the kernel's {@code JobTrigger} covers — exactly three.
     * The kernel's fourth, event-driven, is deliberately excluded there (ADR-057
     * obligation 7) and therefore here; event-driven work is expressed by
     * {@code @EventHandler}.
     */
    public enum TriggerKind {
        /** Standard five-field cron — no seconds field, no vendor extensions. */
        CRON,
        /** A fixed interval, expressed as an ISO-8601 duration. */
        INTERVAL,
        /** A single fire at an ISO-8601 instant. */
        ONE_SHOT
    }

    public ScheduleMetadata {
        Objects.requireNonNull(kind, "kind is required");
        Objects.requireNonNull(expression, "expression is required");
        if (expression.isBlank()) {
            throw new IllegalArgumentException("expression must not be blank");
        }
    }

    /** A cron trigger from a standard five-field expression. */
    public static ScheduleMetadata cron(String expression) {
        return new ScheduleMetadata(TriggerKind.CRON, expression);
    }

    /** A fixed-interval trigger from an ISO-8601 duration, e.g. {@code "PT15M"}. */
    public static ScheduleMetadata every(String isoDuration) {
        return new ScheduleMetadata(TriggerKind.INTERVAL, isoDuration);
    }

    /** A one-shot trigger from an ISO-8601 instant. */
    public static ScheduleMetadata at(String isoInstant) {
        return new ScheduleMetadata(TriggerKind.ONE_SHOT, isoInstant);
    }

    /** Whether this trigger repeats (cron or interval) rather than firing once. */
    @JsonIgnore
    public boolean isRecurring() {
        return kind != TriggerKind.ONE_SHOT;
    }
}
