package eu.exeris.sdk.composition.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The published contract: all four phases default to no-ops so a cap implements only the subset it
 * needs. Calling every default on a bare anonymous implementation also exercises the interface's
 * default-method bytecode — the module's whole executable surface — for the JaCoCo gate.
 */
class CapabilityLifecycleHooksTest {

    @Test
    void allFourDefaultsAreCallableNoOpsOnABareImplementation() {
        CapabilityLifecycleHooks bare = new CapabilityLifecycleHooks() {
        };
        assertThatCode(() -> {
            bare.initialize();
            bare.ready();
            bare.drain(Duration.ofSeconds(30));
            bare.terminate();
        }).doesNotThrowAnyException();
    }

    @Test
    void subsetImplementationOverridesOnlyWhatItNeeds() throws Exception {
        List<String> phases = new ArrayList<>();
        // Implements initialize + drain only; ready + terminate stay the interface defaults.
        CapabilityLifecycleHooks subset = new CapabilityLifecycleHooks() {
            @Override
            public void initialize() {
                phases.add("initialize");
            }

            @Override
            public void drain(Duration remaining) {
                phases.add("drain:" + remaining.toSeconds());
            }
        };

        subset.initialize();
        subset.ready();
        subset.drain(Duration.ofSeconds(30));
        subset.terminate();

        assertThat(phases).containsExactly("initialize", "drain:30");
    }

    @Test
    void terminateDeclaresNoCheckedException() throws NoSuchMethodException {
        // Load-bearing signature detail: terminate() must not throw — the conductor relies on
        // catching only unchecked exceptions during the unconditional terminate unwind.
        assertThat(CapabilityLifecycleHooks.class.getMethod("terminate").getExceptionTypes()).isEmpty();
    }
}
