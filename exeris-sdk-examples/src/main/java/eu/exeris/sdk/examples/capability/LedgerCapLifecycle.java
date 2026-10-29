/*
 * Copyright (C) 2026 Exeris Systems.
 * SPDX-License-Identifier: Apache-2.0
 */
package eu.exeris.sdk.examples.capability;

import eu.exeris.sdk.annotation.capability.CapabilityLifecycle;
import eu.exeris.sdk.annotation.capability.CapabilityModule;
import eu.exeris.sdk.annotation.capability.Provides;
import eu.exeris.sdk.annotation.capability.Requires;
import eu.exeris.sdk.composition.lifecycle.CapabilityLifecycleHooks;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The lifecycle owner of the {@link LedgerCap} capability: a class that implements
 * {@link CapabilityLifecycleHooks} and records each phase the boot conductor drives.
 *
 * <p>The module declaration and its lifecycle owner sit in this one file because
 * {@code SourceModelReader.readCapabilityModule} resolves {@code lifecycleOwner} from the same
 * compilation unit. The owner is the public top-level type because the conductor instantiates it
 * reflectively from another package through a public no-arg constructor.
 *
 * <p>The reader carries {@code @Provides} and {@code @Requires} in their written form, so a
 * service is the simple name in the source; {@code cap-manifest.json} names it fully qualified.
 */
@CapabilityLifecycle
public class LedgerCapLifecycle implements CapabilityLifecycleHooks {

    private static final List<String> PHASES = new CopyOnWriteArrayList<>();

    /** Creates the hooks; the conductor calls this through reflection. */
    public LedgerCapLifecycle() {
    }

    /**
     * The phases seen so far across every instance, in the order they ran.
     *
     * @return an immutable snapshot of the recorded phase names
     */
    public static List<String> phases() {
        return List.copyOf(PHASES);
    }

    /** Forgets every recorded phase. */
    public static void reset() {
        PHASES.clear();
    }

    @Override
    public void initialize() {
        PHASES.add("initialize");
    }

    @Override
    public void ready() {
        PHASES.add("ready");
    }

    @Override
    public void drain(Duration remaining) {
        PHASES.add("drain");
    }

    @Override
    public void terminate() {
        PHASES.add("terminate");
    }
}

/** The service the ledger capability exposes to other capabilities. */
interface LedgerService {
}

/** A service the ledger capability depends on, supplied by another capability. */
interface AuditTrail {
}

/** A service the ledger capability uses when present and runs without otherwise. */
interface ExchangeRates {
}

/** The composition surface of the ledger capability: what it provides and what it requires. */
@CapabilityModule
@Provides(service = LedgerService.class, version = "1.0.0")
@Requires(service = AuditTrail.class, versionRange = "[1.0.0,2.0.0)")
@Requires(service = ExchangeRates.class, optional = true)
class LedgerCap {
}
