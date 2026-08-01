package eu.exeris.sdk.sourcemodel.ast;

/**
 * The data-scope tier of an entity — the single mutually-exclusive discriminator
 * for how its rows are partitioned and made visible.
 *
 * <p>AST-owned (mirroring {@code @ExerisDomain.DataScope} on the annotation side
 * without depending on it), null-tolerated on the wire — an absent value means
 * the author declared no tier, and {@link DomainMetadata#effectiveDataScope()}
 * then falls back to the deprecated {@link DomainMetadata#tenantScoped()} boolean
 * ({@code true} → {@link #TENANT}, {@code false} → {@link #GLOBAL}).
 *
 * <p>There is deliberately no {@code UNSPECIFIED} constant here: the annotation
 * side needs one only because annotation attributes cannot default to
 * {@code null}, and the AST expresses the same state as an absent field. This is
 * the {@code SagaStepMetadata.StepKind} precedent.
 *
 * <p>The SDK encodes <em>author intent</em> only. The kernel mechanism behind
 * {@link #UNIVERSE} — a {@code sharedScopeKey} carrier plus a
 * {@code SHARED_WORLD} row-visibility mode composing with the physical isolation
 * strategy — is never represented here; mapping the tier onto that carrier is
 * {@code exeris-tooling}'s transcription. See RFC-2026-06-24 and ADR-059.
 *
 * @since 0.10.0
 */
public enum DataScope {
    /** Not tenant-partitioned — a global singleton or reference table. */
    GLOBAL,
    /** Tenant-private rows — one tenant never sees another's rows. */
    TENANT,
    /**
     * Shared world — rows owned by a tenant but readable across tenants; reads
     * widen beyond the owning tenant, writes stay pinned to it.
     *
     * <p><strong>Reserved:</strong> no generator consumes this tier yet, so it
     * carries author intent without generated effect. See the Open-Core status
     * note on {@code @ExerisDomain.dataScope()}.
     */
    UNIVERSE
}
