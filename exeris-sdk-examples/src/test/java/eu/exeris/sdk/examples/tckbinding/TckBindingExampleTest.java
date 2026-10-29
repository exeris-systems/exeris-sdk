/*
 * Copyright (C) 2026 Exeris Systems.
 * SPDX-License-Identifier: Apache-2.0
 */
package eu.exeris.sdk.examples.tckbinding;

import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.io.SourceModelReader;
import eu.exeris.sdk.tck.AbstractMetadataReaderTck;
import eu.exeris.sdk.tck.Facet;
import org.junit.jupiter.api.DisplayName;

import java.util.Set;

/**
 * Binds a reader to the SDK's reader conformance kit: extend {@link AbstractMetadataReaderTck},
 * implement {@code read}, and the kit's cases run against your reader.
 *
 * <p>The set returned by {@link #unsupportedFacets()} is the binding's honest statement of what
 * the reader does not read. Every facet named there turns the kit's cases for it into skips, so a
 * facet is listed only when the reader genuinely lacks it. {@link SourceModelReader} reads every
 * facet the kit asks about, so the set is empty.
 */
@DisplayName("examples: binding a reader to the reader TCK")
class TckBindingExampleTest extends AbstractMetadataReaderTck {

    private final SourceModelReader reader = new SourceModelReader();

    @Override
    protected DomainMetadata read(String entitySource) {
        return reader.read(entitySource).orElseThrow(() -> new AssertionError(
                "no @ExerisDomain type in a TCK corpus source"));
    }

    @Override
    protected Set<Facet> unsupportedFacets() {
        return Set.of();
    }
}
