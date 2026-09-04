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
     * <p><strong>Open-Core status — declarable, and refused:</strong> declaring
     * this tier fails the build at the declaration site. {@code exeris-tooling}'s
     * {@code ExerisDomainProcessor.errorReservedUniverseTier} raises an error
     * naming the tier and the reason, instead of emitting for it. The refusal is
     * unconditional on the tier, suppressed only when the declaration is already
     * contradicted by {@code tenantScoped} — one line does not raise two errors.
     *
     * <p>It replaced a warning at {@code exeris-tooling} 0.8.0, and what the
     * warning missed is worth carrying, because it is also why the fail-closed
     * predicate below stays. Every emitter asks one question —
     * {@code DataScopeSupport.isTenantPartitioned}, which answers
     * {@code effectiveDataScope() != GLOBAL} and is therefore {@code true} for
     * {@code UNIVERSE} — so the shape this tier would emit is the full
     * {@link #TENANT} one: owner column, owner-pinned RLS policy, owner index,
     * tenant migration tier. That predicate is correct and stays: an
     * "is {@code TENANT}" test would have routed the tier down the
     * {@link #GLOBAL} path and published rows the author scoped to an owner. But
     * an output strictly narrower than declared is not thereby a usable one. A
     * shared-world row is precisely one with no tenant property, and the
     * {@code TENANT} shape binds {@code getTenantId()} in the emitted repository,
     * so the archetypal entity for this tier did not merely under-deliver — it
     * failed to compile, with {@code cannot find symbol} inside generated code
     * its author is told not to edit. A diagnostic at the declaration is strictly
     * better than a compile error two artefacts downstream.
     *
     * <p>What the untranscribed half waits on is a kernel contract distinct from
     * the carrier: an emitted RLS policy writes a PostgreSQL session-variable
     * name into SQL, and that name is published as
     * {@code ConnectionInterceptor.SESSION_KEY_SHARED_SCOPE} from kernel v0.12.0.
     * {@code StorageContext.sharedScopeKey()} — the accessor an application reads
     * — has existed since 0.11 and is a different contract. See the Open-Core
     * status note on {@code @ExerisDomain.dataScope()}, and ADR-059.
     */
    UNIVERSE
}
