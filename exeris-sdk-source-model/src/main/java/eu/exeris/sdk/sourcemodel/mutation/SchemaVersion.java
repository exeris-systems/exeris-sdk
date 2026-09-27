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
 * @since 0.5
 */
public final class SchemaVersion {

    private SchemaVersion() {
    }

    /**
     * The wire-format schema version this build of {@code source-model} writes
     * and understands. Bump only on an AST/JSON shape change, with a note in
     * {@code MIGRATION.md} — not on routine artifact-version bumps.
     *
     * <p>The value names an AST shape, not its population: a trailing, additive,
     * by-name component still takes a bump, and a baseline stamped with any other
     * value reads as {@link MutationResult.NoBaselineCause#SCHEMA_VERSION_SKEW}
     * rather than being assumed compatible (ADR-042). The shape each value names
     * is recorded against its bump in {@code MIGRATION.md}.
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
     * compares against. The two halves of the same build would then disagree:
     * {@link #isCurrent(String)} answers from the new jar while a caller's
     * inlined {@code CURRENT.equals(stamp)} answers from the old one, and a
     * baseline the build has just stamped would read back as
     * {@link MutationResult.NoBaselineCause#SCHEMA_VERSION_SKEW}. Computing the
     * value in a method keeps it out of every caller's constant pool.
     *
     * <p>This is separate from the deliberate cross-shape refusal documented on
     * {@link #CURRENT}: that one is a real skew and is meant to be reported.
     * {@code MIGRATION.md} records the downstream case that surfaced the trap.
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
