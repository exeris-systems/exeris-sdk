package eu.exeris.sdk.tck;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Facet declaration shared by every suite in the kit.
 *
 * <p>Binders extend one of the concrete suites, not this class.
 */
public abstract class AbstractExerisTck {

    /**
     * Facets this binding does not implement yet. Their cases skip rather than fail.
     *
     * <p>Override only for surface that is genuinely unbuilt. A facet listed here is a claim that
     * the binding emits nothing for it at all — not that it emits something approximate.
     *
     * @return the unimplemented facets; empty by default
     */
    protected Set<Facet> unsupportedFacets() {
        return Set.of();
    }

    /**
     * Skips the calling case when its facet is undeclared, with the reason attached.
     *
     * @param facet the facet the calling case needs
     */
    protected final void requireSupported(Facet facet) {
        Assumptions.assumeTrue(
                !unsupportedFacets().contains(facet),
                () -> "Skipped: this binding declares " + facet + " unsupported via unsupportedFacets().");
    }

    /**
     * The one case that cannot be skipped.
     *
     * <p>Without it the kit has an open trapdoor: a binding declaring every facet unsupported runs
     * a suite of skips and reports green, which reads as conformance and is worse than a failure.
     * Identity and fields are the hand-off itself — a producer emitting neither has produced
     * nothing for anyone to be compatible with.
     */
    @Test
    @DisplayName("the binding does not declare a mandatory facet unsupported")
    final void mandatoryFacetsAreNotDeclaredUnsupported() {
        Set<Facet> declared = unsupportedFacets();
        Set<Facet> offending = EnumSet.noneOf(Facet.class);
        for (Facet mandatory : Facet.MANDATORY) {
            if (declared.contains(mandatory)) {
                offending.add(mandatory);
            }
        }
        assertThat(offending)
                .withFailMessage(
                        "unsupportedFacets() declares %s, which cannot be opted out of. %s are the "
                                + "metadata hand-off itself; a binding that emits neither has nothing "
                                + "to be compatible with, and skipping their cases would report that "
                                + "state as conformance.",
                        offending, Facet.MANDATORY)
                .isEmpty();
    }
}
