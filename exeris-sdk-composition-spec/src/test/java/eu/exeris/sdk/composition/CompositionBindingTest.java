package eu.exeris.sdk.composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Cross-module conformance pin for the ADR-024 content binding. The golden vectors are computed
 * independently of this code (shell {@code printf … | sha256sum} over the canonical form), so a
 * drift in {@link CompositionBinding} — sort order, separators, the {@code "  provides "} prefix,
 * the trailing newline, or the unversioned-provide normalization — fails here rather than silently
 * false-failing a deploy. The versioned vector is the same value the tooling emitter and the
 * (now-superseded) platform port both produced.
 */
class CompositionBindingTest {

    /**
     * Audit[AuditLog@1.0.0] + Billing[Invoice@2.0.0, PaymentApi@1.2.0].
     * <pre>sha256( "com.app.Audit\n  provides com.api.AuditLog@1.0.0\n"
     *           + "com.app.Billing\n  provides com.api.Invoice@2.0.0\n  provides com.api.PaymentApi@1.2.0\n" )</pre>
     */
    private static final String GOLDEN_VERSIONED =
            "sha256:83aae84863de8480b0c1ec943f7d350900a1ff2aab78b4c311684ca2ecc79e96";

    /**
     * Solo[Thing@(unversioned)] — a null version normalizes to the empty string, so the canonical
     * line is {@code "  provides com.api.Thing@\n"} (NOT {@code "…@null\n"}).
     * <pre>sha256( "com.app.Solo\n  provides com.api.Thing@\n" )</pre>
     */
    private static final String GOLDEN_UNVERSIONED =
            "sha256:75f3616837c895cafb566e04f14f3349dbc32561256a41e7d6ae5c912061262c";

    private static List<CapManifest.Module> versionedFixture() {
        return List.of(
                new CapManifest.Module("com.app.Audit", new CapManifest.ModuleBody(List.of(
                        new CapManifest.Provided("com.api.AuditLog", "1.0.0")), null)),
                new CapManifest.Module("com.app.Billing", new CapManifest.ModuleBody(List.of(
                        new CapManifest.Provided("com.api.Invoice", "2.0.0"),
                        new CapManifest.Provided("com.api.PaymentApi", "1.2.0")), null)));
    }

    @Test
    void matchesGoldenVectorForVersionedProvides() {
        assertThat(CompositionBinding.compute(versionedFixture())).isEqualTo(GOLDEN_VERSIONED);
    }

    @Test
    void manifestOverloadEqualsModuleListOverload() {
        CapManifest manifest = new CapManifest(2,
                new CapManifest.Stamp(true, "0.0.0", GOLDEN_VERSIONED),
                versionedFixture(), List.of("com.app.Audit", "com.app.Billing"));
        assertThat(CompositionBinding.compute(manifest)).isEqualTo(GOLDEN_VERSIONED);
    }

    @Test
    void isRobustToModuleAndProvideOrder() {
        // Reverse module order AND reverse provide order within Billing — canonical form sorts both.
        List<CapManifest.Module> shuffled = List.of(
                new CapManifest.Module("com.app.Billing", new CapManifest.ModuleBody(List.of(
                        new CapManifest.Provided("com.api.PaymentApi", "1.2.0"),
                        new CapManifest.Provided("com.api.Invoice", "2.0.0")), null)),
                new CapManifest.Module("com.app.Audit", new CapManifest.ModuleBody(List.of(
                        new CapManifest.Provided("com.api.AuditLog", "1.0.0")), null)));
        assertThat(CompositionBinding.compute(shuffled)).isEqualTo(GOLDEN_VERSIONED);
    }

    @Test
    void isSensitiveToAVersionChange() {
        List<CapManifest.Module> bumped = List.of(
                new CapManifest.Module("com.app.Audit", new CapManifest.ModuleBody(List.of(
                        new CapManifest.Provided("com.api.AuditLog", "9.9.9")), null)),
                new CapManifest.Module("com.app.Billing", new CapManifest.ModuleBody(List.of(
                        new CapManifest.Provided("com.api.Invoice", "2.0.0"),
                        new CapManifest.Provided("com.api.PaymentApi", "1.2.0")), null)));
        assertThat(CompositionBinding.compute(bumped)).isNotEqualTo(GOLDEN_VERSIONED);
    }

    @Test
    void normalizesUnversionedProvideToEmptyString() {
        List<CapManifest.Module> unversioned = List.of(
                new CapManifest.Module("com.app.Solo", new CapManifest.ModuleBody(
                        Collections.singletonList(new CapManifest.Provided("com.api.Thing", null)), null)));
        assertThat(CompositionBinding.compute(unversioned)).isEqualTo(GOLDEN_UNVERSIONED);
    }

    @Test
    void unversionedDiffersFromLiteralNullVersion() {
        // Guards the exact bug the old platform port had: "service@" must NOT equal "service@null".
        String unversioned = CompositionBinding.compute(List.of(
                new CapManifest.Module("com.app.Solo", new CapManifest.ModuleBody(
                        Collections.singletonList(new CapManifest.Provided("com.api.Thing", null)), null))));
        String literalNull = CompositionBinding.compute(List.of(
                new CapManifest.Module("com.app.Solo", new CapManifest.ModuleBody(
                        List.of(new CapManifest.Provided("com.api.Thing", "null")), null))));
        assertThat(unversioned).isNotEqualTo(literalNull);
    }

    @Test
    void toleratesNullBodyAndNullProvidesAsNoProvides() {
        String nullBody = CompositionBinding.compute(List.of(
                new CapManifest.Module("com.app.Empty", null)));
        String nullProvides = CompositionBinding.compute(List.of(
                new CapManifest.Module("com.app.Empty", new CapManifest.ModuleBody(null, null))));
        String emptyProvides = CompositionBinding.compute(List.of(
                new CapManifest.Module("com.app.Empty", new CapManifest.ModuleBody(List.of(), null))));
        // All three are "a module named com.app.Empty that provides nothing" → one canonical form.
        assertThat(nullBody).isEqualTo(nullProvides).isEqualTo(emptyProvides);
    }

    @Test
    void lifecycleOwnerIsGoldenInvariant() {
        // 0.9.0: the module body grew lifecycleOwner, which is deliberately NOT binding-covered
        // (like initOrder — trusted post-assertion). The same cap set with and without owners must
        // bind byte-identically, and both must still equal the pre-0.9.0 golden vector: adding
        // lifecycle hooks to a cap never invalidates a deployed composition's stamp.
        List<CapManifest.Module> withOwners = List.of(
                new CapManifest.Module("com.app.Audit", new CapManifest.ModuleBody(List.of(
                        new CapManifest.Provided("com.api.AuditLog", "1.0.0")), "com.app.AuditLifecycle")),
                new CapManifest.Module("com.app.Billing", new CapManifest.ModuleBody(List.of(
                        new CapManifest.Provided("com.api.Invoice", "2.0.0"),
                        new CapManifest.Provided("com.api.PaymentApi", "1.2.0")), "com.app.BillingLifecycle")));
        assertThat(CompositionBinding.compute(withOwners))
                .isEqualTo(CompositionBinding.compute(versionedFixture()))
                .isEqualTo(GOLDEN_VERSIONED);
    }

    @Test
    void computeManifestOverloadToleratesNullModules() {
        // "modules" absent from the JSON leaves the component null under the consumer mapper config;
        // the convenience overload must bind it as an empty composition, never NPE.
        CapManifest noModules = new CapManifest(2,
                new CapManifest.Stamp(true, "0.0.0", GOLDEN_VERSIONED), null, null);
        String emptyComposition = CompositionBinding.compute(List.<CapManifest.Module>of());
        assertThatCode(() -> CompositionBinding.compute(noModules)).doesNotThrowAnyException();
        assertThat(CompositionBinding.compute(noModules)).isEqualTo(emptyComposition);
    }

    @Test
    void isDeterministicAcrossCalls() {
        assertThat(CompositionBinding.compute(versionedFixture()))
                .isEqualTo(CompositionBinding.compute(versionedFixture()));
    }
}
