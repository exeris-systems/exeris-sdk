/*
 * Copyright (C) 2026 Exeris Systems.
 * SPDX-License-Identifier: Apache-2.0
 */
package eu.exeris.sdk.examples.studioedit;

import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.FieldMetadata;
import eu.exeris.sdk.sourcemodel.io.ApplyResult;
import eu.exeris.sdk.sourcemodel.io.SourceModelMutationApplier;
import eu.exeris.sdk.sourcemodel.io.SourceModelReader;
import eu.exeris.sdk.sourcemodel.io.SourceModelWriter;
import eu.exeris.sdk.sourcemodel.mutation.MutationOp;
import eu.exeris.sdk.sourcemodel.mutation.MutationPath;
import eu.exeris.sdk.sourcemodel.mutation.MutationResult;
import eu.exeris.sdk.sourcemodel.mutation.SchemaVersion;
import eu.exeris.sdk.sourcemodel.mutation.SourceDigest;
import eu.exeris.sdk.tck.TckMappers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The design-time edit loop over the invoicing {@code Invoice} source: the writer edits source
 * text, and the applier edits it only when the source still matches the baseline the last code
 * generation recorded.
 */
@DisplayName("examples: studio edit loop")
class StudioEditExampleTest {

    private static final Path INVOICE =
            Path.of("src/main/java/eu/exeris/sdk/examples/invoicing/Invoice.java");

    private final SourceModelReader reader = new SourceModelReader();
    private final SourceModelWriter writer = new SourceModelWriter();
    private final SourceModelMutationApplier applier = new SourceModelMutationApplier();
    private final ObjectMapper mapper = TckMappers.canonical();

    private static String invoiceSource() throws IOException {
        return Files.readString(INVOICE);
    }

    /** The baseline: the metadata read from {@code source}, stamped with that source's digest. */
    private String baselineOf(String source) {
        DomainMetadata metadata = reader.read(source).orElseThrow();
        ObjectNode baseline = (ObjectNode) mapper.valueToTree(metadata);
        baseline.put("sourceDigest", SourceDigest.of(source));
        baseline.put("schemaVersion", SchemaVersion.CURRENT);
        return baseline.toString();
    }

    private static MutationOp renameNumberToReference() {
        return new MutationOp.RenameField(MutationPath.field("Invoice", "number").toString(), "reference");
    }

    @Test
    @DisplayName("the writer adds and renames a field in the source text")
    void writerEditsSource() throws IOException {
        String source = invoiceSource();

        String withNote = writer.addField(source, "String", "note");
        assertThat(reader.read(withNote).orElseThrow().fields())
                .extracting(FieldMetadata::name).contains("note");

        String renamed = writer.renameField(withNote, "note", "memo");
        assertThat(reader.read(renamed).orElseThrow().fields())
                .extracting(FieldMetadata::name).contains("memo").doesNotContain("note");
    }

    @Test
    @DisplayName("an op over an unchanged source applies and returns the edited source")
    void appliesOverMatchingBaseline() throws IOException {
        String source = invoiceSource();

        ApplyResult result = applier.apply(renameNumberToReference(), baselineOf(source), source);

        assertThat(result.outcome()).isInstanceOf(MutationResult.Success.class);
        assertThat(result.applied()).isTrue();
        assertThat(reader.read(result.source()).orElseThrow().fields())
                .extracting(FieldMetadata::name).contains("reference").doesNotContain("number");
    }

    @Test
    @DisplayName("a source the user changed since the baseline is a Conflict and stays untouched")
    void conflictOnDriftedSource() throws IOException {
        String baseline = baselineOf(invoiceSource());
        // The user renamed the field by hand after the baseline was recorded.
        String drifted = writer.renameField(invoiceSource(), "number", "code");
        MutationOp op = renameNumberToReference();

        ApplyResult result = applier.apply(op, baseline, drifted);

        assertThat(result.outcome()).isInstanceOf(MutationResult.Conflict.class);
        assertThat(result.applied()).isFalse();
        assertThat(result.source()).isEqualTo(drifted);
    }

    @Test
    @DisplayName("a missing baseline is NoBaseline with cause MISSING_BASELINE")
    void noBaselineWhenMissing() throws IOException {
        String source = invoiceSource();

        ApplyResult result = applier.apply(renameNumberToReference(), null, source);

        assertThat(result.outcome()).isInstanceOf(MutationResult.NoBaseline.class);
        assertThat(((MutationResult.NoBaseline) result.outcome()).cause())
                .isEqualTo(MutationResult.NoBaselineCause.MISSING_BASELINE);
        assertThat(result.source()).isEqualTo(source);
    }

    @Test
    @DisplayName("a baseline from another schema version is NoBaseline with cause SCHEMA_VERSION_SKEW")
    void noBaselineWhenSkewed() throws IOException {
        String source = invoiceSource();
        ObjectNode skewed = (ObjectNode) mapper.readTree(baselineOf(source));
        skewed.put("schemaVersion", SchemaVersion.CURRENT + 1);

        ApplyResult result = applier.apply(renameNumberToReference(), skewed.toString(), source);

        assertThat(result.outcome()).isInstanceOf(MutationResult.NoBaseline.class);
        assertThat(((MutationResult.NoBaseline) result.outcome()).cause())
                .isEqualTo(MutationResult.NoBaselineCause.SCHEMA_VERSION_SKEW);
        assertThat(result.source()).isEqualTo(source);
    }
}
