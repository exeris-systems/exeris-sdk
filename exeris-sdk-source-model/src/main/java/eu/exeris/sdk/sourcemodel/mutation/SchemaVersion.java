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
     *   <li>{@code "0.9.0"} — bumped for the {@code FieldMetadata} bounds
     *       inclusion fix: {@code min} / {@code max} / {@code minLength} /
     *       {@code maxLength} moved from the class-level
     *       {@code @JsonInclude(NON_DEFAULT)} to per-component
     *       {@code NON_NULL}, so zero-valued bounds (e.g. {@code min = 0} as a
     *       non-negativity floor) survive serialization instead of being
     *       dropped as boxed-zero "empty". No component changed name or type,
     *       but the wire can now carry keys it previously dropped — the schema
     *       names the shape, so a {@code "0.8.0"} baseline reads as
     *       {@code SCHEMA_VERSION_SKEW}.</li>
     *   <li>{@code "0.10.0"} — bumped for the data-scope tier:
     *       {@code DomainMetadata} gained {@code dataScope}
     *       ({@link eu.exeris.sdk.sourcemodel.ast.DataScope}), the
     *       mutually-exclusive successor of the deprecated {@code tenantScoped}
     *       boolean (RFC-2026-06-24 / ADR-059). Additive and by-name, and an
     *       absent {@code dataScope} still reads correctly through
     *       {@code DomainMetadata.effectiveDataScope()}'s {@code tenantScoped}
     *       fallback — but the schema names the shape, so a {@code "0.9.0"}
     *       baseline reads as {@code SCHEMA_VERSION_SKEW}.</li>
     *   <li>{@code "0.11.0"} — bumped for the kernel-0.11 design-time facets:
     *       {@code FieldMetadata} gained {@code blob}
     *       ({@link eu.exeris.sdk.sourcemodel.ast.BlobMetadata}) and
     *       {@code ActionMetadata} gained {@code schedule}
     *       ({@link eu.exeris.sdk.sourcemodel.ast.ScheduleMetadata}), the AST
     *       twins of {@code @Blob} and {@code @Schedule} (ADR-072). Both are
     *       trailing, additive and by-name, and both are <em>reserved</em> —
     *       nothing populates them yet — so a {@code "0.10.0"} baseline loses
     *       no information in practice. The bump is taken anyway, because the
     *       schema names the shape rather than its population, and the posture
     *       is to refuse a cross-shape baseline rather than assume
     *       compatibility: a {@code "0.10.0"} baseline reads as
     *       {@code SCHEMA_VERSION_SKEW}.</li>
     *   <li>{@code "0.12.0"} — bumped for the route-authorization facet:
     *       {@code DomainMetadata} and {@code ActionMetadata} each gained
     *       {@code routeAccess}
     *       ({@link eu.exeris.sdk.sourcemodel.ast.RouteAccess}), the AST twin of
     *       {@code @RouteAccess} (kernel ADR-061; ADR-072 as amended). Trailing,
     *       additive, by-name and <em>reserved</em> on the same terms as the
     *       0.11.0 pair, so a {@code "0.11.0"} baseline loses no information in
     *       practice — and the bump is taken anyway, for the same reason it was
     *       taken then. A {@code "0.11.0"} baseline reads as
     *       {@code SCHEMA_VERSION_SKEW}. The 0.12.0 schema likewise also covers
     *       the shared-scope key that landed within the same 0.12.0 release:
     *       {@code SystemFieldsMetadata.sharedScopeField}, the AST twin of
     *       {@code @SharedScope} (RFC-2026-06-24 / ADR-059) — trailing, additive
     *       and by-name, so no further bump within 0.12.0. It is reserved on the
     *       same terms as the facets above, with one difference worth knowing:
     *       the kernel half it names is not pending. A {@code DataScope.UNIVERSE}
     *       policy compares this column against the session variable kernel
     *       v0.12.0 publishes as {@code SESSION_KEY_SHARED_SCOPE}; what is still
     *       missing is only the tooling emitter that reads the component.</li>
     * </ul>
     */
    public static final String CURRENT = currentVersion();

    /**
     * Holds the literal so that {@link #CURRENT} is <strong>not a constant
     * variable</strong> (JLS 4.12.4) and therefore carries no {@code
     * ConstantValue} attribute for {@code javac} to inline at a consumer's
     * compile sites (JLS 13.1).
     *
     * <p>This is not style. With a literal initializer, every downstream class
     * that mentions {@code SchemaVersion.CURRENT} bakes the value it saw at
     * <em>its own</em> compile time into its own class file, and swapping the
     * {@code source-model} jar underneath it does not change what that class
     * compares against. The two halves of the same build then disagree:
     * {@link #isCurrent(String)} answers from the new jar while a caller's
     * inlined {@code CURRENT.equals(stamp)} answers from the old one, and a
     * baseline the build has just stamped reads back as
     * {@link MutationResult.NoBaselineCause#SCHEMA_VERSION_SKEW}.
     *
     * <p>Found downstream in {@code exeris-platform}, where an SDK bump was
     * green under {@code mvn clean test} and dropped three tests to
     * {@code NO_BASELINE} without {@code clean} — stale test classes still
     * carrying the inlined {@code "0.10.0"}. That is a build-hygiene trap with
     * a confusing name on it, and it is this constant's to remove rather than
     * every consumer's to know about. It is separate from, and was easy to
     * mistake for, the deliberate cross-shape refusal documented on
     * {@link #CURRENT} — that one is a real skew and is meant to be reported.
     */
    // java:S3400 ("methods should not return constants") asks for exactly the shape this
    // method exists to prevent: folding the literal back into CURRENT's initializer makes it
    // a constant variable again (JLS 4.12.4) and re-inlines it at every downstream compile
    // site (JLS 13.1), which is the bug above. The indirection is the fix, not an oversight —
    // BaselineTrustContractTest.currentIsNotAConstantVariable pins it by compiling a source
    // that uses CURRENT where only a constant expression is legal, and asserting it fails.
    @SuppressWarnings("java:S3400")
    private static String currentVersion() {
        return "0.12.0";
    }

    /**
     * Whether a baseline's stamped schema version is the one this build reads.
     * A {@code null} or absent stamp (e.g. a pre-0.5.0 baseline) is <em>not</em>
     * current — the safe posture is to refuse, not to assume compatibility.
          *
     * @param schemaVersion the version stamped on a baseline
     * @return whether it is the version this build reads
     */
    public static boolean isCurrent(String schemaVersion) {
        return CURRENT.equals(schemaVersion);
    }
}
