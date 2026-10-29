/*
 * Copyright (C) 2026 Exeris Systems.
 * SPDX-License-Identifier: Apache-2.0
 */
package eu.exeris.sdk.examples.capability;

import eu.exeris.sdk.composition.runtime.CompositionConductor;
import eu.exeris.sdk.composition.runtime.CompositionStampAssertion;
import eu.exeris.sdk.composition.runtime.CompositionStampException;
import eu.exeris.sdk.sourcemodel.ast.CapabilityModuleMetadata;
import eu.exeris.sdk.sourcemodel.ast.ProvidesMetadata;
import eu.exeris.sdk.sourcemodel.ast.RequiresMetadata;
import eu.exeris.sdk.sourcemodel.io.SourceModelReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reads the capability example source exactly as the documentation quotes it, asserts the
 * metadata it declares, and drives the same capability through the boot conductor from a
 * {@code cap-manifest.json} whose stamp is consistent with it. Files are resolved against the
 * module base directory, which is surefire's working directory.
 */
@DisplayName("examples: capability")
class CapabilityExampleTest {

    private static final Path SOURCE =
            Path.of("src/main/java/eu/exeris/sdk/examples/capability/LedgerCapLifecycle.java");
    private static final Path MANIFEST = Path.of("src/test/resources/capability/cap-manifest.json");

    @BeforeEach
    void forgetEarlierPhases() {
        LedgerCapLifecycle.reset();
    }

    @Test
    @DisplayName("@Provides and @Requires are read in the form they are written")
    void declaredSurface() throws IOException {
        CapabilityModuleMetadata cap = new SourceModelReader()
                .readCapabilityModule(Files.readString(SOURCE)).orElseThrow();

        assertThat(cap.provides()).containsExactly(ProvidesMetadata.of("LedgerService", "1.0.0"));
        assertThat(cap.requires()).containsExactly(
                RequiresMetadata.of("AuditTrail", "[1.0.0,2.0.0)"),
                RequiresMetadata.optional("ExchangeRates"));
        assertThat(cap.lifecycleOwner()).isEqualTo("eu.exeris.sdk.examples.capability.LedgerCapLifecycle");
    }

    @Test
    @DisplayName("the manifest stamp is consistent with the capabilities it lists")
    void manifestIsConsistent() {
        assertThatCode(() -> CompositionStampAssertion.assertConsistent(MANIFEST))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the conductor runs initialize and ready on start, then drain and terminate on shutdown")
    void conductorDrivesTheFourPhases() {
        CompositionConductor conductor = CompositionConductor.from(MANIFEST).start();

        assertThat(LedgerCapLifecycle.phases()).containsExactly("initialize", "ready");

        conductor.shutdown();

        assertThat(LedgerCapLifecycle.phases())
                .containsExactly("initialize", "ready", "drain", "terminate");
    }

    @Test
    @DisplayName("a manifest whose content no longer matches its stamp is refused before any hook runs")
    void tamperedManifestIsRefused() throws IOException {
        String json = Files.readString(MANIFEST);
        String tampered = json.replace("\"version\": \"1.0.0\"", "\"version\": \"9.9.9\"");
        assertThat(tampered).isNotEqualTo(json);

        assertThatThrownBy(() -> CompositionStampAssertion.assertConsistent(tampered))
                .isInstanceOf(CompositionStampException.class)
                .hasMessageContaining("binding mismatch");

        Path tamperedFile = Files.createTempFile("cap-manifest", ".json");
        try {
            Files.writeString(tamperedFile, tampered);
            assertThatThrownBy(() -> CompositionConductor.from(tamperedFile).start())
                    .isInstanceOf(CompositionStampException.class);
            assertThat(LedgerCapLifecycle.phases()).isEmpty();
        } finally {
            Files.deleteIfExists(tamperedFile);
        }
    }
}
