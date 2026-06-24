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
     *   <li>{@code "0.7.0"} — bumped for the next JSON-affecting AST shape
     *       growth: {@code ProjectionMetadata} gained the source / subscription /
     *       read-model components ({@code aggregateTypes}, {@code events},
     *       {@code eventClassNames}, {@code topicPattern}, {@code model},
     *       {@code schema}). Same posture as the 0.6.0 bump — the additions are
     *       by-name and back-compatible to read, but the schema names the shape,
     *       so a {@code "0.6.0"} baseline reads as {@code SCHEMA_VERSION_SKEW}.
     *       (The 0.6.0 schema already covered {@code DomainMetadata.eventHandlers},
     *       which landed within the 0.6.0 release.) The 0.7.0 schema likewise
     *       also covers the saga state-machine growth that landed within the same
     *       0.7.0 release: {@code SagaMetadata.transitions} (typed, outcome-edged
     *       transitions) and {@code SagaStepMetadata.kind}, and the
     *       declarative-behaviour growth — {@code FieldMetadata.derived}
     *       ({@code DerivedMetadata}) and {@code DomainMetadata.rules}
     *       ({@code RuleMetadata}) — all additive, by-name, so no further bump
     *       within 0.7.0.</li>
     *   <li>{@code "0.8.0"} — bumped for the streaming AST growth: {@code
     *       ActionMetadata} gained {@code streaming} / {@code streamEventType} /
     *       {@code realTimeUpdates}, the per-action twin of the {@code @Action}
     *       streaming attributes, unblocking the tooling per-action SSE stream
     *       emitter (ADR-043 / RFC-2026-06-22). Same posture as the prior bumps —
     *       the additions are by-name and back-compatible to read, but the schema
     *       names the shape, so a {@code "0.7.0"} baseline reads as
     *       {@code SCHEMA_VERSION_SKEW}.</li>
     * </ul>
     */
    public static final String CURRENT = "0.8.0";

    /**
     * Whether a baseline's stamped schema version is the one this build reads.
     * A {@code null} or absent stamp (e.g. a pre-0.5.0 baseline) is <em>not</em>
     * current — the safe posture is to refuse, not to assume compatibility.
     */
    public static boolean isCurrent(String schemaVersion) {
        return CURRENT.equals(schemaVersion);
    }
}
