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
     * <p><strong>Open-Core status — consumed today, but narrower than
     * declared:</strong> this tier is not inert, and reading it as "declared
     * and does nothing" is the dangerous misreading. Every emitter asks one
     * predicate — {@code DataScopeSupport.isTenantPartitioned}, which answers
     * {@code effectiveDataScope() != GLOBAL} and is therefore {@code true} for
     * {@code UNIVERSE} ({@code exeris-tooling}
     * {@code exeris-codegen-java/.../support/DataScopeSupport.java:48-50}) — so
     * a {@code UNIVERSE} entity generates the full {@link #TENANT} shape:
     * owner column, owner-pinned RLS policy, owner index, tenant migration
     * tier. The untranscribed half is only the cross-tenant read-widening, i.e.
     * the mapping onto the kernel {@code sharedScopeKey} /
     * {@code SHARED_WORLD} carrier. Output is thus strictly narrower than
     * declared and never wider, which is the deliberate choice: an
     * "is {@code TENANT}" test would have routed {@code UNIVERSE} down the
     * {@link #GLOBAL} path and published rows the author scoped to an owner.
     * The processor emits a build warning naming exactly this on every entity
     * that declares the tier ({@code ExerisDomainProcessor.java:817-826}).
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
