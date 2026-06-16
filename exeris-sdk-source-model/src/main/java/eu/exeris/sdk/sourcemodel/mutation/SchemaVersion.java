package eu.exeris.sdk.sourcemodel.mutation;

/**
 * The version of the {@code source-model} <em>wire format</em> — the schema of
 * the {@code exeris-metadata/<entity>.json} hand-off the processor/codegen write
 * and {@code -io} reads (ADR-042, obligation 5).
 *
 * <p>This is a <strong>dedicated constant, deliberately decoupled from the
 * Maven artifact version</strong>: it is bumped only when the AST record shapes
 * change in a way that affects the JSON, not on every release. A patch release
 * that touches no AST shape leaves {@link #CURRENT} unchanged, so an unchanged
 * baseline does not look like a schema skew (which would otherwise produce a
 * spurious {@link MutationResult.NoBaseline}). Codegen stamps this value into
 * each baseline JSON; the {@code -io} reader compares the baseline's stamp
 * against {@link #CURRENT} and refuses a mismatch
 * ({@link MutationResult.NoBaselineCause#SCHEMA_VERSION_SKEW}).
 *
 * @since 0.5.0
 */
public final class SchemaVersion {

    private SchemaVersion() {
    }

    /**
     * The wire-format schema version this build of {@code source-model} writes
     * and understands. Bump only on an AST/JSON shape change, with a note in
     * {@code MIGRATION.md} — not on routine artifact-version bumps.
     *
     * <p>History:
     * <ul>
     *   <li>{@code "0.5.0"} — initial baseline-trust schema (ADR-042).</li>
     *   <li>{@code "0.6.0"} — bumped for the 0.6.0 AST shape growth that added
     *       JSON-affecting record components: {@code FieldMetadata.dataType}
     *       (B5), {@code FieldMetadata.displayNameKey} / {@code descriptionKey},
     *       {@code UIFieldMetadata.customComponent} / {@code placeholderKey} /
     *       {@code helpTextKey}, and {@code ComponentType.CUSTOM} (B4). The
     *       additions are by-name and back-compatible to read, but the schema
     *       names the shape, and the ADR posture is to refuse cross-shape
     *       baselines rather than assume compatibility — so a {@code "0.5.0"}
     *       baseline reads as {@code SCHEMA_VERSION_SKEW}.</li>
     * </ul>
     */
    public static final String CURRENT = "0.6.0";

    /**
     * Whether a baseline's stamped schema version is the one this build reads.
     * A {@code null} or absent stamp (e.g. a pre-0.5.0 baseline) is <em>not</em>
     * current — the safe posture is to refuse, not to assume compatibility.
     */
    public static boolean isCurrent(String schemaVersion) {
        return CURRENT.equals(schemaVersion);
    }
}
