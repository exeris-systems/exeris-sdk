/**
 * Domain metadata models for Exeris annotation processing.
 * <p>
 * Contains records representing metadata extracted from SDK annotations.
 * These models are serialized to JSON and consumed by code generators.
 *
 * <h2>JSON wire-format contract for consumers</h2>
 * <p>The processor writes these records to {@code *.json} files at build
 * time and downstream tooling (codegen, LSP, IDE plugins) reads them back.
 * For round-trip fidelity, any consumer constructing its own Jackson
 * {@code ObjectMapper} <strong>must</strong> configure:
 * <ul>
 *   <li>{@code FAIL_ON_NULL_FOR_PRIMITIVES = false} — Jackson 3 defaults this
 *       to {@code true}, but our records use primitive booleans heavily with
 *       {@code @JsonInclude(NON_DEFAULT)} / {@code NON_NULL}, so absent fields
 *       arrive as {@code null}. Without this flag, deserialization throws on
 *       any record that has a default-valued boolean.</li>
 * </ul>
 * The wire-format guard test {@code AstJsonRoundTripTest} configures the
 * mapper accordingly and is the canonical reference.
 *
 * <h2>FieldMetadata vs ValidationMetadata — canonical scoping (0.2.0)</h2>
 * <p>The annotation surface ({@code @Field} / {@code @Validation}) historically
 * exposed overlapping attributes. The AST mirrors the resolution chosen on the
 * annotation side:
 * <ul>
 *   <li><strong>{@link eu.exeris.sdk.sourcemodel.ast.FieldMetadata}</strong>
 *       owns {@code required}, {@code inCreate}, {@code inUpdate}. These are
 *       field-shape / lifecycle facts — populated from {@code @Field} only.
 *       {@code @Validation.required} (deprecated, removal in 1.0.0) is ignored
 *       by the processor.</li>
 *   <li><strong>{@link eu.exeris.sdk.sourcemodel.ast.ValidationMetadata}</strong>
 *       owns the constraint rules: {@code minLength}, {@code maxLength},
 *       {@code min}, {@code max}, {@code pattern}, {@code email}, {@code url},
 *       {@code future}, {@code past}. {@code notNull} / {@code notBlank} are
 *       <em>expected to be derived</em> from {@code FieldMetadata.required}
 *       at processor-build time (see {@code ExerisDomainProcessor} in the
 *       tooling repo) — they are not separately configurable on
 *       {@code @Validation} since 0.2.0.</li>
 * </ul>
 * <p>{@code FieldMetadata} also carries a few constraint-shaped fields
 * ({@code minLength}, {@code maxLength}, {@code min}, {@code max},
 * {@code pattern}) that overlap with {@code ValidationMetadata}. Those remain
 * for now — they describe basic shape hints used by the processor before
 * {@code @Validation} is consulted. A full deduplication is tracked in the
 * 0.6.0–0.9.0 cleanup phase once budgetHQ usage informs the right cut.
 *
 * <h2>{@code dataType} vs {@code format} on FieldMetadata (0.6.0)</h2>
 * <p>{@link eu.exeris.sdk.sourcemodel.ast.FieldMetadata} carries two distinct
 * display-related strings, split by responsibility:
 * <ul>
 *   <li><strong>{@code dataType}</strong> — the <em>semantic</em> kind of the
 *       value, sourced from {@code @Field.dataType} ({@code "currency"},
 *       {@code "percent"}, {@code "url"}, …). It tells a generator <em>what the
 *       value means</em> so it can pick the right renderer/editor; it does not
 *       prescribe the exact rendering.</li>
 *   <li><strong>{@code format}</strong> — an explicit <em>presentation</em>
 *       format string (e.g. a number/date pattern). It tells a generator
 *       <em>exactly how to render</em>, overriding the {@code dataType} default.
 *       There is no {@code @Field} attribute feeding it today; it is reserved for
 *       a processor-derived or future explicit-format source.</li>
 * </ul>
 * <p>{@code dataType} was added to the record in 0.6.0 so the AST can carry the
 * {@code @Field.dataType} the annotation already declared (it was previously
 * dropped at the annotation→AST boundary). <strong>Populating it is a coordinated
 * cross-repo change:</strong> the build-time processor and the {@code -io} reader
 * must begin extracting {@code @Field.dataType} <em>together</em>, or a reader
 * that reads it while the processor does not would diverge from the codegen
 * baseline and break conflict detection (ADR-042).
 *
 * <h2>i18n message keys + custom-component escape hatch (0.6.0)</h2>
 * <p>Two additive extensibility fields were added to the field-rendering AST:
 * <ul>
 *   <li><strong>i18n message keys</strong> — {@code FieldMetadata.displayNameKey}
 *       / {@code descriptionKey} and {@code UIMetadata.UIFieldMetadata.placeholderKey}
 *       / {@code helpTextKey}. Each is the optional bundle key paired with the
 *       literal text it sits next to ({@code displayName}, {@code description},
 *       {@code placeholder}, {@code helpText}); the literal is the design-time /
 *       missing-translation fallback. Sourced from {@code @Field.labelKey} /
 *       {@code descriptionKey} and {@code @UI.placeholderKey} / {@code helpTextKey}.
 *       Keys are added only where the AST already carries the corresponding
 *       string — entity-level {@code @UI} titles are not AST-carried, so they
 *       have no key here (carrying those titles is a separate prerequisite, and
 *       group/domain-description keys are a deferred follow-up using this same
 *       pattern).</li>
 *   <li><strong>Custom-component escape hatch</strong> — {@code ComponentType.CUSTOM}
 *       plus {@code UIFieldMetadata.customComponent}. {@code customComponent} is
 *       meaningful only when {@code componentType == CUSTOM} and names the
 *       application-supplied control the generator should render. {@code CUSTOM}
 *       without a {@code customComponent} is a generator-side error; the AST
 *       only carries the declared shape.</li>
 * </ul>
 * <p>Both are reader↔processor-parity-neutral: like {@code dataType}, the
 * {@code -io} reader and the build-time processor must begin populating them
 * <em>together</em> to keep ADR-042 conflict-detection baselines trustworthy.
 *
 * <h2>Event handlers — the reaction side (0.6.0)</h2>
 * <p>{@link eu.exeris.sdk.sourcemodel.ast.DomainEventMetadata} models event
 * <em>emission</em>; {@link eu.exeris.sdk.sourcemodel.ast.EventHandlerMetadata}
 * (a facet of {@code DomainMetadata}, like {@code events}) models the
 * <em>reaction</em> — the choreography backbone "when event X fires, do Y". The
 * {@code @EventHandler} annotation has shipped since 0.1.0, but had no AST
 * record to be extracted into; this record closes that gap. It captures the
 * behaviourally meaningful facets (identity, event selection, ordering,
 * execution semantics, saga-trigger surface); the annotation's operational
 * attributes ({@code emitMetrics}/{@code logLevel}/{@code alertOnFailure}), the
 * nested {@code @RetryPolicy}, and {@code versions} are deliberately deferred
 * (additive — by-name JSON). Enum-valued attributes ({@code priority},
 * {@code transactionMode}) are stored as their source-written {@code String}
 * form, the same discipline the capability records use, so {@code source-model}
 * never depends on the annotation module's enum types. As with {@code dataType}
 * and the i18n keys, the build-time processor extraction and the codegen/{@code -io}
 * consumer are coordinated {@code exeris-tooling} work — the SDK supplies only
 * the record the choreography serializes into.
 *
 * <h2>Capability surface (0.4.0)</h2>
 * <p>Capabilities are a top-level concept, parallel to entities — a
 * {@code @CapabilityModule} class is read into a
 * {@link eu.exeris.sdk.sourcemodel.ast.CapabilityModuleMetadata} the way an
 * {@code @ExerisDomain} class is read into a
 * {@link eu.exeris.sdk.sourcemodel.ast.DomainMetadata} (it is <em>not</em> a
 * facet of {@code DomainMetadata}). It holds the services the cap
 * {@link eu.exeris.sdk.sourcemodel.ast.ProvidesMetadata provides} and
 * {@link eu.exeris.sdk.sourcemodel.ast.RequiresMetadata requires}, plus the
 * {@code lifecycleOwner}. Service identity is stored as the
 * <strong>source-written</strong> name (FQN from the processor path, written
 * form from the {@code -io} reader); canonical FQN normalization is the
 * build-time tooling's responsibility, not the SDK's. Resolution, the
 * dependency DAG, version-range intersection, and the cap manifest are likewise
 * tooling concerns — the AST carries only the declared shape. See ADR-038.
 *
 * @since 0.1.0
 * @see eu.exeris.sdk.sourcemodel.ast.CapabilityModuleMetadata
 */
package eu.exeris.sdk.sourcemodel.ast;
