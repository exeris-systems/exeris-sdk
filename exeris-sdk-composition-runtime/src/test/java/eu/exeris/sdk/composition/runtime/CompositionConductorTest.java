package eu.exeris.sdk.composition.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import eu.exeris.sdk.composition.CapManifest;
import eu.exeris.sdk.composition.CompositionBinding;
import eu.exeris.sdk.composition.lifecycle.CapabilityLifecycleHooks;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Boot-conductor behaviour (ADR-024 obligation 8a′): verbatim-initOrder phase driving, prefix
 * unwind, all-or-nothing discovery, the composition-wide drain budget, and the stamp gate.
 * Deterministic by construction — the tests inject a synchronous {@link DrainRunner} fake and a
 * fake nano-time source; there is no sleep and no wall-clock dependence anywhere. The recording
 * fixture hooks append {@code "<cap>.<phase>"} tokens to {@link #RECORD}; positional cap letters
 * (A = first in initOrder, B = second, C = third) are fixed by each hook class, independent of the
 * manifest qualifiedNames.
 */
class CompositionConductorTest {

    /** Shared recording — hook classes are instantiated reflectively, so the seam is static. */
    static final List<String> RECORD = new CopyOnWriteArrayList<>();

    @BeforeEach
    void clearRecording() {
        RECORD.clear();
    }

    // ---------------------------------------------------------------- fixture helpers

    /** A synchronous, calling-thread drain runner — fully deterministic. */
    private static final DrainRunner DIRECT_DRAIN = (hooks, remaining) -> {
        hooks.drain(remaining);
        return true;
    };

    /** A manifest with a VALID stamp over {@code modules} (same construction style as the assertion tests). */
    private static CapManifest manifest(List<String> initOrder, CapManifest.Module... modules) {
        List<CapManifest.Module> moduleList = List.of(modules);
        return new CapManifest(2,
                new CapManifest.Stamp(true, "1.0.0", CompositionBinding.compute(moduleList)),
                moduleList, initOrder);
    }

    private static CapManifest.Module module(String qualifiedName, Class<?> lifecycleOwner) {
        return new CapManifest.Module(qualifiedName, new CapManifest.ModuleBody(
                List.of(), lifecycleOwner == null ? null : lifecycleOwner.getName()));
    }

    private static CapManifest.Module module(String qualifiedName, String lifecycleOwnerFqn) {
        return new CapManifest.Module(qualifiedName,
                new CapManifest.ModuleBody(List.of(), lifecycleOwnerFqn));
    }

    /** Deterministic conductor builder: direct drain runner + a frozen nano clock. */
    private static CompositionConductor.Builder conductor(CapManifest manifest) {
        return CompositionConductor.from(manifest)
                .drainRunner(DIRECT_DRAIN)
                .nanoTime(() -> 0L);
    }

    private static CapManifest threeCaps(Class<?> aHooks, Class<?> bHooks, Class<?> cHooks) {
        return manifest(List.of("com.app.A", "com.app.B", "com.app.C"),
                module("com.app.A", aHooks),
                module("com.app.B", bHooks),
                module("com.app.C", cHooks));
    }

    // ---------------------------------------------------------------- 1. order pin

    @Test
    void drivesPhasesInVerbatimInitOrderForwardAndReverse() {
        // Deliberately non-alphabetical initOrder (Zulu, Alpha, Mike) with the modules array sorted
        // differently — proves the conductor replays initOrder verbatim, never re-sorting.
        CompositionConductor conductor = conductor(manifest(
                List.of("com.app.Zulu", "com.app.Alpha", "com.app.Mike"),
                module("com.app.Alpha", CapBHooks.class),
                module("com.app.Mike", CapCHooks.class),
                module("com.app.Zulu", CapAHooks.class)))
                .classLoader(CompositionConductorTest.class.getClassLoader())
                .start();

        assertThat(RECORD).containsExactly(
                "A.init", "B.init", "C.init", "A.ready", "B.ready", "C.ready");

        conductor.shutdown();
        assertThat(RECORD).containsExactly(
                "A.init", "B.init", "C.init", "A.ready", "B.ready", "C.ready",
                "C.drain", "B.drain", "A.drain", "C.term", "B.term", "A.term");
    }

    // ---------------------------------------------------------------- 2. prefix unwind

    @Test
    void initializeFailureUnwindsTheTouchedPrefixSkippingTheFailedCapsDrain() {
        CompositionBootException failure = catchThrowableOfType(CompositionBootException.class,
                () -> conductor(threeCaps(CapAHooks.class, InitFailsBHooks.class, CapCHooks.class)).start());

        // B never completed initialize → no intake → no drain for B; terminate is unconditional.
        assertThat(RECORD).containsExactly("A.init", "B.init", "A.drain", "B.term", "A.term");
        assertThat(failure.capName()).isEqualTo("com.app.B");
        assertThat(failure.phase()).isEqualTo("initialize");
        assertThat(failure.getCause())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("B initialize boom");
    }

    // ---------------------------------------------------------------- 3. full unwind

    @Test
    void readyFailureUnwindsAllCapsInReverse() {
        CompositionBootException failure = catchThrowableOfType(CompositionBootException.class,
                () -> conductor(threeCaps(CapAHooks.class, CapBHooks.class, ReadyFailsCHooks.class)).start());

        assertThat(RECORD).containsExactly(
                "A.init", "B.init", "C.init", "A.ready", "B.ready", "C.ready",
                "C.drain", "B.drain", "A.drain", "C.term", "B.term", "A.term");
        assertThat(failure.capName()).isEqualTo("com.app.C");
        assertThat(failure.phase()).isEqualTo("ready");
        assertThat(failure.getCause()).hasMessage("C ready boom");
    }

    @Test
    void readyFailureOnAMiddleCapStillUnwindsAllCaps() {
        // C.ready never runs (the ready loop stops at B), yet the unwind still drains and
        // terminates C — the documented "all caps" semantic: drain may be called on a cap whose
        // ready never ran; terminate is unconditional either way.
        CompositionBootException failure = catchThrowableOfType(CompositionBootException.class,
                () -> conductor(threeCaps(CapAHooks.class, ReadyFailsBHooks.class, CapCHooks.class)).start());

        assertThat(RECORD).containsExactly(
                "A.init", "B.init", "C.init", "A.ready", "B.ready",
                "C.drain", "B.drain", "A.drain", "C.term", "B.term", "A.term");
        assertThat(failure.capName()).isEqualTo("com.app.B");
        assertThat(failure.phase()).isEqualTo("ready");
        assertThat(failure.getCause()).hasMessage("B ready boom");
    }

    // ---------------------------------------------------------------- 4. discovery fail-fast

    @Test
    void unknownLifecycleOwnerFqnFailsDiscoveryWithZeroSideEffects() {
        // The resolvable hook-bearing cap sits FIRST in initOrder — discovery is all-or-nothing, so
        // even the resolvable cap must see no lifecycle effect.
        CompositionBootException failure = catchThrowableOfType(CompositionBootException.class,
                () -> conductor(manifest(List.of("com.app.Good", "com.app.Bad"),
                        module("com.app.Good", CapAHooks.class),
                        module("com.app.Bad", "no.such.LifecycleClazz"))).start());

        assertThat(failure.phase()).isEqualTo("discover");
        assertThat(failure.capName()).isEqualTo("com.app.Bad");
        assertThat(RECORD).isEmpty();
    }

    @Test
    void lifecycleOwnerNotImplementingHooksFailsDiscovery() {
        CompositionBootException failure = catchThrowableOfType(CompositionBootException.class,
                () -> conductor(manifest(List.of("com.app.X"),
                        module("com.app.X", "java.lang.String"))).start());

        assertThat(failure.phase()).isEqualTo("discover");
        assertThat(failure).hasMessageContaining("does not implement CapabilityLifecycleHooks");
        assertThat(RECORD).isEmpty();
    }

    @Test
    void lifecycleOwnerWithoutPublicNoArgConstructorFailsDiscovery() {
        CompositionBootException failure = catchThrowableOfType(CompositionBootException.class,
                () -> conductor(manifest(List.of("com.app.X"),
                        module("com.app.X", NoNoArgCtorHooks.class))).start());

        assertThat(failure.phase()).isEqualTo("discover");
        assertThat(failure).hasMessageContaining("no public no-arg constructor");
        assertThat(RECORD).isEmpty();
    }

    @Test
    void initOrderEntryWithoutAModuleFailsDiscovery() {
        CompositionBootException failure = catchThrowableOfType(CompositionBootException.class,
                () -> conductor(manifest(List.of("com.app.Ghost"),
                        module("com.app.Real", CapAHooks.class))).start());

        assertThat(failure.phase()).isEqualTo("discover");
        assertThat(failure.capName()).isEqualTo("com.app.Ghost");
        assertThat(RECORD).isEmpty();
    }

    // ---------------------------------------------------------------- 5. no-hooks paths

    @Test
    void capsWithoutLifecycleOwnerAreSkipped() {
        // Null body, null owner, and blank owner (normalized to null by the record) are all "no hooks".
        CompositionConductor conductor = conductor(manifest(
                List.of("com.app.NullBody", "com.app.NullOwner", "com.app.BlankOwner"),
                new CapManifest.Module("com.app.NullBody", null),
                module("com.app.NullOwner", (String) null),
                module("com.app.BlankOwner", "   "))).start();

        conductor.shutdown();
        assertThat(RECORD).isEmpty();
    }

    @Test
    void legacyManifestWithoutLifecycleOwnerFieldBootsHookless(@TempDir Path dir) throws Exception {
        // Pre-0.9.0 producer JSON: no lifecycleOwner key anywhere — must boot trivially ready.
        Path manifestFile = Files.writeString(dir.resolve("cap-manifest.json"), LEGACY_JSON);

        assertThatCode(() -> {
            CompositionConductor conductor = CompositionConductor.from(manifestFile)
                    .drainRunner(DIRECT_DRAIN)
                    .nanoTime(() -> 0L)
                    .start();
            conductor.shutdown();
        }).doesNotThrowAnyException();
        assertThat(RECORD).isEmpty();
    }

    @Test
    void nullInitOrderWithHookBearingModulesFailsDiscovery() {
        CompositionBootException failure = catchThrowableOfType(CompositionBootException.class,
                () -> conductor(manifest(null, module("com.app.X", CapAHooks.class))).start());

        assertThat(failure.phase()).isEqualTo("discover");
        assertThat(failure.capName()).isNull(); // pre-loop failure: not attributable to one cap's entry
        assertThat(RECORD).isEmpty();
    }

    @Test
    void nullInitOrderWithNoModulesIsTriviallyReady() {
        assertThatCode(() -> conductor(manifest(null)).start().shutdown())
                .doesNotThrowAnyException();
        assertThat(RECORD).isEmpty();
    }

    // ---------------------------------------------------------------- 6. deadline semantics

    @Test
    void timedOutDrainIsAbandonedAndEveryTerminateStillRuns() {
        // The fake runner reports C (first to drain in reverse order) as overrunning: its drain is
        // abandoned without completing, yet C.term/B.term/A.term all run unconditionally.
        DrainRunner cTimesOut = (hooks, remaining) -> {
            if (hooks instanceof CapCHooks) {
                return false; // overran, interrupted, abandoned — drain never completed
            }
            hooks.drain(remaining);
            return true;
        };
        CompositionConductor conductor = CompositionConductor
                .from(threeCaps(CapAHooks.class, CapBHooks.class, CapCHooks.class))
                .drainRunner(cTimesOut)
                .nanoTime(() -> 0L)
                .start();

        conductor.shutdown();
        assertThat(RECORD).containsExactly(
                "A.init", "B.init", "C.init", "A.ready", "B.ready", "C.ready",
                "B.drain", "A.drain", "C.term", "B.term", "A.term");
    }

    @Test
    void exhaustedBudgetSkipsRemainingDrainsButNeverTerminates() {
        // Fake nanos advance 20s per reading against the default 30s budget: the deadline reading
        // and C's reading leave 10s for C, then B and A observe an exhausted budget — their drains
        // are skipped ENTIRELY (not run with zero), while every terminate still runs.
        AtomicLong nanos = new AtomicLong();
        CompositionConductor conductor = CompositionConductor
                .from(threeCaps(CapAHooks.class, CapBHooks.class, CapCHooks.class))
                .drainRunner(DIRECT_DRAIN)
                .nanoTime(() -> nanos.getAndAdd(Duration.ofSeconds(20).toNanos()))
                .start();

        nanos.set(0); // budget arithmetic starts at shutdown
        conductor.shutdown();
        assertThat(RECORD).containsExactly(
                "A.init", "B.init", "C.init", "A.ready", "B.ready", "C.ready",
                "C.drain", "C.term", "B.term", "A.term");
    }

    @Test
    void eachDrainReceivesTheStrictlyRemainingBudget() {
        AtomicLong nanos = new AtomicLong();
        List<Duration> observed = new ArrayList<>();
        DrainRunner recordingRunner = (hooks, remaining) -> {
            observed.add(remaining);
            hooks.drain(remaining);
            return true;
        };
        CompositionConductor conductor = CompositionConductor
                .from(threeCaps(CapAHooks.class, CapBHooks.class, CapCHooks.class))
                .drainDeadline(Duration.ofSeconds(30))
                .drainRunner(recordingRunner)
                .nanoTime(() -> nanos.getAndAdd(Duration.ofMillis(1).toNanos()))
                .start();

        nanos.set(0);
        conductor.shutdown();

        assertThat(observed).hasSize(3);
        assertThat(observed.get(0)).isEqualTo(Duration.ofSeconds(30).minusMillis(1)); // C
        assertThat(observed.get(1)).isEqualTo(Duration.ofSeconds(30).minusMillis(2)); // B
        assertThat(observed.get(2)).isEqualTo(Duration.ofSeconds(30).minusMillis(3)); // A
        // Strictly smaller than the predecessor's — the budget is composition-wide, not per-cap.
        assertThat(observed.get(1)).isLessThan(observed.get(0));
        assertThat(observed.get(2)).isLessThan(observed.get(1));
    }

    // ---------------------------------------------------------------- 7. terminate never stops the unwind

    @Test
    void throwingTerminateIsLoggedAndNeverStopsTheUnwind() {
        CompositionConductor conductor = conductor(
                threeCaps(CapAHooks.class, CapBHooks.class, TerminateThrowsCHooks.class)).start();

        assertThatCode(conductor::shutdown).doesNotThrowAnyException();
        assertThat(RECORD).containsExactly(
                "A.init", "B.init", "C.init", "A.ready", "B.ready", "C.ready",
                "C.drain", "B.drain", "A.drain", "C.term", "B.term", "A.term");
    }

    // ---------------------------------------------------------------- 8. idempotent shutdown

    @Test
    void secondShutdownAndCloseAppendNothing() {
        CompositionConductor conductor = conductor(
                threeCaps(CapAHooks.class, CapBHooks.class, CapCHooks.class)).start();
        conductor.shutdown();
        List<String> afterFirst = List.copyOf(RECORD);

        conductor.shutdown();
        conductor.close();
        assertThat(RECORD).isEqualTo(afterFirst);
    }

    // ---------------------------------------------------------------- 9. stamp gate first

    @Test
    void invalidStampFailsBeforeAnyEffect() {
        // Hook-bearing modules behind a mismatched (well-formed) binding: the stamp gate must fire
        // FIRST — no discovery, no initialize, recording empty — and propagate untouched.
        List<CapManifest.Module> modules = List.of(module("com.app.A", CapAHooks.class));
        CapManifest tampered = new CapManifest(2,
                new CapManifest.Stamp(true, "1.0.0", "sha256:" + "0".repeat(64)),
                modules, List.of("com.app.A"));

        assertThatThrownBy(() -> conductor(tampered).start())
                .isInstanceOf(CompositionStampException.class)
                .hasMessageContaining("binding mismatch");
        assertThat(RECORD).isEmpty();
    }

    @Test
    void malformedManifestFileFailsAsStampExceptionBeforeAnyEffect(@TempDir Path dir) throws Exception {
        Path malformed = Files.writeString(dir.resolve("cap-manifest.json"), "{ not json");

        assertThatThrownBy(() -> CompositionConductor.from(malformed).start())
                .isInstanceOf(CompositionStampException.class)
                .hasMessageContaining("not parseable");
        assertThatThrownBy(() -> CompositionConductor.from(dir.resolve("absent.json")).start())
                .isInstanceOf(CompositionStampException.class)
                .hasMessageContaining("cannot read");
        assertThat(RECORD).isEmpty();
    }

    // ---------------------------------------------------------------- 10. exception typing

    @Test
    void bootExceptionIsUncheckedAndNotAStampException() {
        CompositionBootException e = new CompositionBootException("boom", "com.app.X", "discover");

        assertThat(e).isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(CompositionStampException.class);
        assertThat(e.capName()).isEqualTo("com.app.X");
        assertThat(e.phase()).isEqualTo("discover");
    }

    // ---------------------------------------------------------------- production DrainRunner (narrow)

    @Test
    void productionDrainRunnerReportsAnImmediateDrainAsCompleted() throws Exception {
        AtomicBoolean drained = new AtomicBoolean();

        boolean completed = DrainRunner.production().run(new CapabilityLifecycleHooks() {
            @Override
            public void drain(Duration remaining) {
                drained.set(true);
            }
        }, Duration.ofSeconds(30));

        assertThat(completed).isTrue();
        assertThat(drained).isTrue();
        assertThat(Thread.currentThread().isInterrupted()).isFalse(); // no leaked watchdog interrupt
    }

    @Test
    void productionDrainRunnerInterruptsAndAbandonsAnOverrunningDrain() throws Exception {
        // Deterministic without test-side sleeps: the drain blocks on a latch that is never counted
        // down, so the ONLY way run() can return is the watchdog interrupting after the ~1ns budget.
        boolean completed = DrainRunner.production().run(new CapabilityLifecycleHooks() {
            @Override
            public void drain(Duration remaining) throws Exception {
                new CountDownLatch(1).await();
            }
        }, Duration.ofNanos(1));

        assertThat(completed).isFalse();
        // Load-bearing: the interrupt must be cleared so it cannot leak into terminate (or the
        // next cap's drain) on the calling thread.
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    // ---------------------------------------------------------------- recording hook fixtures

    public static class CapAHooks implements CapabilityLifecycleHooks {
        @Override public void initialize() { RECORD.add("A.init"); }
        @Override public void ready() { RECORD.add("A.ready"); }
        @Override public void drain(Duration remaining) { RECORD.add("A.drain"); }
        @Override public void terminate() { RECORD.add("A.term"); }
    }

    public static class CapBHooks implements CapabilityLifecycleHooks {
        @Override public void initialize() { RECORD.add("B.init"); }
        @Override public void ready() { RECORD.add("B.ready"); }
        @Override public void drain(Duration remaining) { RECORD.add("B.drain"); }
        @Override public void terminate() { RECORD.add("B.term"); }
    }

    public static class CapCHooks implements CapabilityLifecycleHooks {
        @Override public void initialize() { RECORD.add("C.init"); }
        @Override public void ready() { RECORD.add("C.ready"); }
        @Override public void drain(Duration remaining) { RECORD.add("C.drain"); }
        @Override public void terminate() { RECORD.add("C.term"); }
    }

    /** Records the attempt, then fails — initialize must count as "touched" for the terminate phase. */
    public static class InitFailsBHooks implements CapabilityLifecycleHooks {
        @Override public void initialize() {
            RECORD.add("B.init");
            throw new IllegalStateException("B initialize boom");
        }
        @Override public void ready() { RECORD.add("B.ready"); }
        @Override public void drain(Duration remaining) { RECORD.add("B.drain"); }
        @Override public void terminate() { RECORD.add("B.term"); }
    }

    public static class ReadyFailsCHooks implements CapabilityLifecycleHooks {
        @Override public void initialize() { RECORD.add("C.init"); }
        @Override public void ready() {
            RECORD.add("C.ready");
            throw new IllegalStateException("C ready boom");
        }
        @Override public void drain(Duration remaining) { RECORD.add("C.drain"); }
        @Override public void terminate() { RECORD.add("C.term"); }
    }

    public static class ReadyFailsBHooks implements CapabilityLifecycleHooks {
        @Override public void initialize() { RECORD.add("B.init"); }
        @Override public void ready() {
            RECORD.add("B.ready");
            throw new IllegalStateException("B ready boom");
        }
        @Override public void drain(Duration remaining) { RECORD.add("B.drain"); }
        @Override public void terminate() { RECORD.add("B.term"); }
    }

    public static class TerminateThrowsCHooks implements CapabilityLifecycleHooks {
        @Override public void initialize() { RECORD.add("C.init"); }
        @Override public void ready() { RECORD.add("C.ready"); }
        @Override public void drain(Duration remaining) { RECORD.add("C.drain"); }
        @Override public void terminate() {
            RECORD.add("C.term");
            throw new IllegalStateException("C terminate boom");
        }
    }

    /** Violates the instantiation contract: no public no-arg constructor. */
    public static class NoNoArgCtorHooks implements CapabilityLifecycleHooks {
        public NoNoArgCtorHooks(String ignored) {
        }
    }

    // Pre-0.9.0 emitted manifest (golden binding, no lifecycleOwner key anywhere) — the legacy shape.
    private static final String LEGACY_JSON = """
            {
              "schemaVersion": 2,
              "stamp": {
                "validated": true,
                "compositionVersion": "0.0.0",
                "contentBinding": "sha256:83aae84863de8480b0c1ec943f7d350900a1ff2aab78b4c311684ca2ecc79e96"
              },
              "modules": [
                { "name": "Audit", "packageName": "com.app", "qualifiedName": "com.app.Audit",
                  "module": { "provides": [ {"service":"com.api.AuditLog","version":"1.0.0"} ] } },
                { "name": "Billing", "packageName": "com.app", "qualifiedName": "com.app.Billing",
                  "module": { "provides": [ {"service":"com.api.PaymentApi","version":"1.2.0"},
                                            {"service":"com.api.Invoice","version":"2.0.0"} ] } }
              ],
              "initOrder": ["com.app.Audit","com.app.Billing"]
            }
            """;
}
