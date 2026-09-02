package eu.exeris.sdk.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that an {@link Action} also fires on a schedule, rather than only when
 * a client calls it.
 *
 * <p>This is the Entity-First expression of the kernel's job-scheduling seam
 * (kernel ADR-057, {@code eu.exeris.kernel.spi.scheduling}). Before it, a domain
 * could not say "run this action every night" anywhere in the source of truth —
 * the schedule lived in a host-runtime configuration file, or in a hand-rolled
 * timer, at a distance from the action it fires.
 *
 * <h2>Basic usage</h2>
 * <pre>{@code
 * @Action(name = "reconcile", httpMethod = "POST")
 * @Schedule(cron = "0 3 * * *")          // 03:00 daily
 * public void reconcile() { ... }
 *
 * @Action(name = "refreshQuotes")
 * @Schedule(every = "PT15M")             // every 15 minutes
 * public void refreshQuotes() { ... }
 * }</pre>
 *
 * <h2>Exactly one attribute may be set</h2>
 * <p>{@link #cron()}, {@link #every()} and {@link #at()} are mutually exclusive —
 * they are the three trigger kinds {@code JobTrigger} covers, and a declaration
 * setting two of them is not a trigger. The SDK does not enforce this: it is a
 * design-time carrier that stores what the author wrote and interprets nothing
 * (zero runtime coupling). {@code exeris-tooling} rejects a multi-kind or empty
 * declaration at build time, and the AST collapses the three attributes into a
 * single {@code ScheduleMetadata.TriggerKind} discriminator so a nonsense
 * combination is unrepresentable downstream.
 *
 * <h2>Cron is the standard five-field syntax and nothing more</h2>
 * <p>No seconds field, no {@code @reboot}, no vendor extensions. The kernel states
 * that subset in its contract precisely so a driver cannot quietly widen it (ADR-057
 * obligation 7), and the SDK must not widen at the declaration site what the kernel
 * narrowed at the execution site. An application arriving from Quartz with
 * second-level triggers has to change its schedules; that is a deliberate cost of a
 * small surface, not a gap.
 *
 * <p>The kernel's fourth, event-driven kind is excluded there and therefore absent
 * here. Event-driven work is already expressible — see {@link EventHandler}.
 *
 * <h2>Open-Core status — RESERVED, extraction pending tooling</h2>
 * <p>Declared shape, not yet a running job. The kernel side demonstrably exists —
 * {@code JobScheduler} / {@code JobDescriptor} / {@code JobTrigger} shipped on the
 * kernel 0.11 line with {@code AbstractJobSchedulerTck} — but no
 * {@code exeris-tooling} processor extracts {@code @Schedule}, no generator submits
 * a job from it, and the {@code exeris-sdk-source-model-io} reader does not read it,
 * so declaring it today has no generated effect. The kernel holds
 * {@code …spi.scheduling} at tier {@code preview}, so this surface is
 * <strong>excluded from the 1.0.0 freeze</strong> and a 1.x minor may still change
 * it; it is promoted when the kernel package leaves {@code preview} <em>and</em> the
 * tooling transcription exists. See {@code docs/adr/ADR-072} and {@code ROADMAP.md}.
 *
 * <p><strong>One question this surface deliberately does not answer:</strong> which
 * identity a scheduled action runs as. The kernel captures {@code PrincipalContext}
 * and {@code StorageContext} <em>at submission</em> and makes a job with no captured
 * context fail closed rather than run under an ambient identity (ADR-057 obligation
 * 5) — correctly. A declared schedule has no submission event and therefore no
 * principal to capture. Naming a run-as identity here would put an authorization
 * decision inside a design-time annotation, which is the worse of the two available
 * answers; the other is a kernel-side notion of a service principal for declared
 * jobs. It is open, it is tracked in ADR-072, and it is one of the reasons this
 * surface ships reserved rather than live.
 *
 * @since 0.11.0
 * @see Action
 * @see EventHandler
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface Schedule {

    /**
     * A standard <strong>five-field</strong> cron expression
     * ({@code minute hour day-of-month month day-of-week}), e.g.
     * {@code "0 3 * * *"} for 03:00 daily.
     *
     * <p>No seconds field and no vendor extensions — see the class javadoc.
     * Mutually exclusive with {@link #every()} and {@link #at()}.
     *
     * @return the five-field cron expression, or empty if this is not a cron trigger
     */
    String cron() default "";

    /**
     * A fixed interval as an ISO-8601 duration, e.g. {@code "PT15M"} or
     * {@code "PT1H30M"}.
     *
     * <p>Mutually exclusive with {@link #cron()} and {@link #at()}.
     *
     * @return the ISO-8601 interval, or empty if this is not an interval trigger
     */
    String every() default "";

    /**
     * A one-shot fire time as an ISO-8601 instant, e.g.
     * {@code "2026-09-01T00:00:00Z"}.
     *
     * <p>Mutually exclusive with {@link #cron()} and {@link #every()}.
     *
     * @return the ISO-8601 instant, or empty if this is not a one-shot trigger
     */
    String at() default "";
}
