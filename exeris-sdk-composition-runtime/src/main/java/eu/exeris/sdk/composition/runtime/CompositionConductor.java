package eu.exeris.sdk.composition.runtime;

import eu.exeris.sdk.composition.CapManifest;
import eu.exeris.sdk.composition.lifecycle.CapabilityLifecycleHooks;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * The boot conductor (ADR-024 obligation 8a′, amendment 2026-07-21 "Boot Conductor Call Site"):
 * drives every cap in the composition through the four-phase lifecycle declared by
 * {@link CapabilityLifecycleHooks} — {@code initialize → ready} on boot, {@code drain → terminate}
 * on shutdown — in the tooling-emitted manifest {@code initOrder}, replayed <b>verbatim</b> (no
 * re-sort, no {@code @Requires}→{@code @Provides} DAG re-resolution: composition stays a build-time
 * concern).
 *
 * <h2>Boot ({@link Builder#start()})</h2>
 * <ol>
 *   <li><b>Stamp assertion first.</b> The manifest passes {@link CompositionStampAssertion} before
 *       anything else; a {@link CompositionStampException} (including an unreadable or unparseable
 *       manifest file) propagates untouched, before any effect.</li>
 *   <li><b>Discovery.</b> Every {@code initOrder} entry is resolved to its module and every non-null
 *       {@code lifecycleOwner} is loaded and instantiated (public no-arg constructor, must implement
 *       {@code CapabilityLifecycleHooks}) <em>before any lifecycle effect</em> — a discovery failure
 *       is a {@link CompositionBootException} with {@code phase="discover"} and zero side effects.
 *       An absent / blank {@code lifecycleOwner} means the cap has no hooks and is skipped.</li>
 *   <li><b>{@code initialize} forward.</b> In verbatim {@code initOrder}. A failure at cap
 *       <i>k</i> unwinds the touched prefix in reverse — drain (skipping cap <i>k</i>, which never
 *       completed initialize and so has no intake), then terminate <em>including</em> cap <i>k</i>
 *       (unconditional; the hooks contract makes terminate re-entry-safe) — then throws
 *       {@link CompositionBootException} with the cause chained.</li>
 *   <li><b>{@code ready} forward.</b> The all-caps barrier. A failure unwinds <em>all</em> caps
 *       (drain, then terminate) and throws.</li>
 *   <li>One INFO line: {@code composition ready — N caps, M with lifecycle hooks}.</li>
 * </ol>
 * <p><b>0.9.0 non-guarantee:</b> {@code initialize()} / {@code ready()} calls are <em>not</em>
 * time-bounded — a hanging initialize hangs boot. Only the drain phase is deadline-enforced.
 *
 * <h2>Shutdown ({@link #shutdown()} / {@link #close()})</h2>
 * <p>Reverse-{@code initOrder} drain under <b>one composition-wide budget</b>
 * ({@link Builder#drainDeadline(Duration)}, default {@link #DEFAULT_DRAIN_DEADLINE}): each cap's
 * drain receives the strictly-remaining {@link Duration}; a cap that overruns is interrupted and
 * abandoned (logged, unwind proceeds); once the budget is exhausted the remaining caps' drains are
 * skipped entirely. Then the terminate phase runs for <em>every</em> cap unconditionally — a
 * throwing terminate is caught and logged, never stopping the unwind. Idempotent (a second
 * {@code shutdown()} / {@code close()} is a no-op) and never throws.
 *
 * <h2>Call site</h2>
 * <p>Invoked by the SKU bootstrap inside {@code KernelBootstrap.boot(kernelMain)}, after the kernel
 * reports {@code KERNEL READY} — never registered as a kernel {@code Subsystem}; the kernel stays
 * cap-blind (ADR-024 obligation 9) and the cap layer contributes no node to the kernel bootstrap
 * DAG. Shutdown is SKU-entrypoint-driven: the entrypoint calls {@code shutdown()} (caps drain and
 * terminate in reverse {@code initOrder}), then stops the kernel.
 *
 * <pre>{@code
 * try (var conductor = CompositionConductor.from(Path.of("cap-manifest.json")).start()) {
 *     // serve until the SKU entrypoint decides to stop
 * } // close() == shutdown(): reverse drain within the budget, then unconditional terminate
 * }</pre>
 *
 * @since 0.9.0
 */
public final class CompositionConductor implements AutoCloseable {

    /** The default composition-wide drain budget (per-SKU configurable via the {@link Builder}). */
    public static final Duration DEFAULT_DRAIN_DEADLINE = Duration.ofSeconds(30);

    private static final System.Logger LOG = System.getLogger(CompositionConductor.class.getName());

    /** Same mapper posture as {@link CompositionStampAssertion} and the assertion tests. */
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
            .build();

    /** One conducted cap: its manifest {@code qualifiedName} + its instantiated hooks. */
    private record Cap(String name, CapabilityLifecycleHooks hooks) {}

    private final List<Cap> caps;
    private final Duration drainDeadline;
    private final DrainRunner drainRunner;
    private final LongSupplier nanoTime;
    private final AtomicBoolean shutdownDone = new AtomicBoolean();

    private CompositionConductor(List<Cap> caps, Duration drainDeadline,
                                 DrainRunner drainRunner, LongSupplier nanoTime) {
        this.caps = caps;
        this.drainDeadline = drainDeadline;
        this.drainRunner = drainRunner;
        this.nanoTime = nanoTime;
    }

    /**
     * Conduct the manifest at {@code manifestPath}. Reading and parsing happen in
     * {@link Builder#start()}, with the same mapper posture and failure class as
     * {@link CompositionStampAssertion#assertConsistent(Path)} — an unreadable or unparseable
     * manifest is a {@link CompositionStampException} before any effect.
     */
    public static Builder from(Path manifestPath) {
        return new Builder(Objects.requireNonNull(manifestPath, "manifestPath"), null);
    }

    /** Conduct an already-parsed manifest (still stamp-asserted first in {@link Builder#start()}). */
    public static Builder from(CapManifest manifest) {
        return new Builder(null, Objects.requireNonNull(manifest, "manifest"));
    }

    /**
     * Drain and terminate every cap in reverse {@code initOrder} (see the class javadoc for the
     * budget semantics). Idempotent; never throws.
     */
    public void shutdown() {
        if (!shutdownDone.compareAndSet(false, true)) {
            return; // second shutdown()/close() is a no-op
        }
        drainReverse(caps.size() - 1);
        terminateReverse(caps.size() - 1);
    }

    /** Alias of {@link #shutdown()}. */
    @Override
    public void close() {
        shutdown();
    }

    // ---------------------------------------------------------------- boot

    private void boot(int totalCaps) {
        for (int i = 0; i < caps.size(); i++) {
            Cap cap = caps.get(i);
            try {
                cap.hooks().initialize();
            } catch (Exception failure) {
                unwind(i - 1, i); // drain the completed prefix only; terminate includes cap i
                throw new CompositionBootException("cap '" + cap.name() + "' failed initialize — "
                        + "boot aborted after unwinding the touched prefix",
                        cap.name(), "initialize", failure);
            }
        }
        for (Cap cap : caps) {
            try {
                cap.hooks().ready();
            } catch (Exception failure) {
                unwind(caps.size() - 1, caps.size() - 1); // full unwind — every cap initialized
                throw new CompositionBootException("cap '" + cap.name() + "' failed ready — "
                        + "boot aborted after unwinding all caps",
                        cap.name(), "ready", failure);
            }
        }
        LOG.log(Level.INFO, "composition ready — " + totalCaps + " caps, "
                + caps.size() + " with lifecycle hooks");
    }

    private void unwind(int drainFromInclusive, int terminateFromInclusive) {
        shutdownDone.set(true); // the unwind IS the shutdown — a later shutdown() must be a no-op
        drainReverse(drainFromInclusive);
        terminateReverse(terminateFromInclusive);
    }

    // ---------------------------------------------------------------- unwind phases

    private void drainReverse(int fromInclusive) {
        if (fromInclusive < 0) {
            return;
        }
        long deadlineNanos = nanoTime.getAsLong() + drainDeadline.toNanos();
        for (int i = fromInclusive; i >= 0; i--) {
            Cap cap = caps.get(i);
            long remainingNanos = deadlineNanos - nanoTime.getAsLong();
            if (remainingNanos <= 0) {
                LOG.log(Level.WARNING, "composition drain budget (" + drainDeadline
                        + ") exhausted — skipping drain of cap '" + cap.name() + "'");
                continue;
            }
            try {
                boolean completed = drainRunner.run(cap.hooks(), Duration.ofNanos(remainingNanos));
                if (!completed) {
                    LOG.log(Level.WARNING, "cap '" + cap.name()
                            + "' overran its drain deadline — abandoned (interrupted), proceeding");
                }
            } catch (Exception drainFailure) {
                LOG.log(Level.WARNING, "cap '" + cap.name()
                        + "' drain threw — proceeding with the unwind", drainFailure);
            }
        }
    }

    private void terminateReverse(int fromInclusive) {
        for (int i = fromInclusive; i >= 0; i--) {
            Cap cap = caps.get(i);
            try {
                cap.hooks().terminate();
            } catch (RuntimeException terminateFailure) {
                // The hooks contract says terminate must not throw; if one does anyway, it never
                // stops the unwind — the remaining caps still get their unconditional terminate.
                LOG.log(Level.WARNING, "cap '" + cap.name()
                        + "' terminate threw — continuing the unwind", terminateFailure);
            }
        }
    }

    // ---------------------------------------------------------------- discovery

    private static List<Cap> discover(CapManifest manifest, ClassLoader loader) {
        List<CapManifest.Module> modules = manifest.modules() != null ? manifest.modules() : List.of();
        List<String> initOrder = manifest.initOrder();
        if (initOrder == null) {
            // Legal only for a hook-less composition: with hooks declared but no order to replay,
            // conducting would mean re-resolving the DAG here — exactly what the conductor must not do.
            String hookBearing = modules.stream()
                    .filter(m -> lifecycleOwnerOf(m) != null)
                    .map(CapManifest.Module::qualifiedName)
                    .findFirst()
                    .orElse(null);
            if (hookBearing != null) {
                throw new CompositionBootException("cap-manifest declares lifecycle hooks (cap '"
                        + hookBearing + "') but no initOrder — cannot conduct without the"
                        + " tooling-emitted order", null, "discover");
            }
            return List.of();
        }
        Map<String, CapManifest.Module> byName = new HashMap<>();
        for (CapManifest.Module module : modules) {
            byName.put(module.qualifiedName(), module);
        }
        List<Cap> caps = new ArrayList<>();
        for (String capName : initOrder) {
            CapManifest.Module module = byName.get(capName);
            if (module == null) {
                throw new CompositionBootException("initOrder entry '" + capName
                        + "' has no module in the manifest — a stale or hand-edited initOrder",
                        capName, "discover");
            }
            String owner = lifecycleOwnerOf(module);
            if (owner != null) {
                caps.add(new Cap(capName, instantiate(capName, owner, loader)));
            }
        }
        return List.copyOf(caps);
    }

    private static String lifecycleOwnerOf(CapManifest.Module module) {
        CapManifest.ModuleBody body = module.module();
        if (body == null) {
            return null;
        }
        String owner = body.lifecycleOwner();
        // The record's compact constructor already normalizes blank → null; the isBlank() check is
        // belt-and-braces for the documented "absent/null/blank = no hooks" identity.
        return (owner == null || owner.isBlank()) ? null : owner;
    }

    private static CapabilityLifecycleHooks instantiate(String capName, String ownerFqn, ClassLoader loader) {
        Class<?> ownerClass;
        try {
            ownerClass = Class.forName(ownerFqn, true, loader);
        } catch (ClassNotFoundException | LinkageError notLoadable) {
            throw new CompositionBootException("lifecycleOwner '" + ownerFqn + "' of cap '" + capName
                    + "' is not loadable from the composition class loader",
                    capName, "discover", notLoadable);
        }
        if (!CapabilityLifecycleHooks.class.isAssignableFrom(ownerClass)) {
            throw new CompositionBootException("lifecycleOwner '" + ownerFqn + "' of cap '" + capName
                    + "' does not implement CapabilityLifecycleHooks", capName, "discover");
        }
        try {
            return (CapabilityLifecycleHooks) ownerClass.getConstructor().newInstance();
        } catch (ReflectiveOperationException notInstantiable) {
            throw new CompositionBootException("lifecycleOwner '" + ownerFqn + "' of cap '" + capName
                    + "' has no public no-arg constructor or failed to instantiate",
                    capName, "discover", notInstantiable);
        }
    }

    // ---------------------------------------------------------------- builder

    /** Configures and starts a {@link CompositionConductor}; obtain via {@code from(...)}. */
    public static final class Builder {

        private final Path manifestPath;
        private final CapManifest manifest;
        private Duration drainDeadline = DEFAULT_DRAIN_DEADLINE;
        private ClassLoader classLoader;
        private DrainRunner drainRunner = DrainRunner.production();
        private LongSupplier nanoTime = System::nanoTime;

        private Builder(Path manifestPath, CapManifest manifest) {
            this.manifestPath = manifestPath;
            this.manifest = manifest;
        }

        /** The composition-wide drain budget (default {@link #DEFAULT_DRAIN_DEADLINE}); must be positive. */
        public Builder drainDeadline(Duration deadline) {
            Objects.requireNonNull(deadline, "deadline");
            if (deadline.isZero() || deadline.isNegative()) {
                throw new IllegalArgumentException("drainDeadline must be positive: " + deadline);
            }
            this.drainDeadline = deadline;
            return this;
        }

        /**
         * The class loader lifecycle owners are loaded from. Default: the thread context class
         * loader at {@code start()} time (falling back to this class's own loader if unset).
         */
        public Builder classLoader(ClassLoader loader) {
            this.classLoader = Objects.requireNonNull(loader, "loader");
            return this;
        }

        /** Package-private determinism seam — tests inject a synchronous fake. */
        Builder drainRunner(DrainRunner runner) {
            this.drainRunner = Objects.requireNonNull(runner, "runner");
            return this;
        }

        /** Package-private determinism seam — tests inject a fake nano-time source. */
        Builder nanoTime(LongSupplier nanos) {
            this.nanoTime = Objects.requireNonNull(nanos, "nanos");
            return this;
        }

        /**
         * Boot the composition: stamp assertion → discovery → {@code initialize*} →
         * {@code ready*} (see the {@link CompositionConductor} javadoc for the full semantics).
         *
         * @throws CompositionStampException if the manifest is unreadable, unparseable, or fails
         *         the ADR-024 stamp assertion (before any effect)
         * @throws CompositionBootException  if hook discovery or a cap's initialize/ready fails
         *         (touched caps are unwound first)
         */
        public CompositionConductor start() {
            CapManifest resolved = resolveManifest();
            CompositionStampAssertion.assertConsistent(resolved);
            List<Cap> caps = discover(resolved, effectiveClassLoader());
            int totalCaps = resolved.modules() != null ? resolved.modules().size() : 0;
            if (caps.isEmpty() && totalCaps > 0) {
                LOG.log(Level.INFO, "composition has " + totalCaps
                        + " cap(s) but none declares lifecycle hooks — nothing to conduct");
            }
            CompositionConductor conductor =
                    new CompositionConductor(caps, drainDeadline, drainRunner, nanoTime);
            conductor.boot(totalCaps);
            return conductor;
        }

        private CapManifest resolveManifest() {
            if (manifest != null) {
                return manifest;
            }
            String json;
            try {
                json = Files.readString(manifestPath);
            } catch (IOException unreadable) {
                throw new CompositionStampException(
                        "cannot read cap-manifest.json at " + manifestPath, unreadable);
            }
            CapManifest parsed;
            try {
                parsed = MAPPER.readValue(json, CapManifest.class);
            } catch (RuntimeException malformed) {
                // Jackson 3 throws unchecked; same failure class as the stamp assertion's parse.
                throw new CompositionStampException("cap-manifest at " + manifestPath
                        + " is not parseable: " + malformed.getMessage(), malformed);
            }
            if (parsed == null) {
                throw new CompositionStampException("cap-manifest at " + manifestPath + " is empty");
            }
            return parsed;
        }

        private ClassLoader effectiveClassLoader() {
            if (classLoader != null) {
                return classLoader;
            }
            ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
            return contextLoader != null ? contextLoader : CompositionConductor.class.getClassLoader();
        }
    }
}
