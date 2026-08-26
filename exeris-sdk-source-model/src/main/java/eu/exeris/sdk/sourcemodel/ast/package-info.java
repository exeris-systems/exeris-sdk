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
 *   <li>{@code FAIL_ON_NULL_FOR_PRIMITIVES = false} — our records use primitive
 *       booleans heavily, and without this flag deserialization throws on any
 *       explicit {@code null} standing where one of them is declared.
 *       <strong>Corrected 2026-08-26:</strong> this text used to say that
 *       {@code @JsonInclude(NON_DEFAULT)} / {@code NON_NULL} makes "absent
 *       fields arrive as {@code null}", and that the throw follows from a
 *       default-valued boolean being omitted. Measured, it does not: an
 *       <em>absent</em> property binds the primitive's own default and raises
 *       nothing, on a stock Jackson 3 mapper. What throws is an
 *       <em>explicit</em> {@code null} on the wire. The obligation is real and
 *       unchanged — a baseline is a file the reader did not necessarily write,
 *       and a third-party producer, a hand edit, or a re-serialization under
 *       {@code ALWAYS} inclusion each put explicit nulls in it — but its
 *       trigger is not the inclusion posture of our own writer. The case is
 *       pinned in {@code exeris-sdk-tck}
 *       ({@code AbstractMapperPostureTck}), whose self-test is what measured
 *       it: the suite's other three candidate cases could not be made to fail
 *       against a deliberately misconfigured mapper and were dropped. <strong>Which Jackson generation this bites:</strong> Jackson 3
 *       ({@code tools.jackson}) defaults the feature to {@code true}, so a
 *       Jackson-3 consumer <em>must</em> turn it off explicitly. Jackson 2
 *       ({@code com.fasterxml.jackson}) already defaults it to {@code false},
 *       so a Jackson-2 consumer gets the correct behaviour without configuring
 *       anything — which is why today's real producer/consumer pair in
 *       {@code exeris-tooling} ({@code ExerisDomainProcessor} writing,
 *       {@code MetadataLoader} reading, both on the 2.x line) never mentions
 *       the feature. Setting it explicitly is correct and harmless on both
 *       lines; state it, do not assume the default.</li>
 * </ul>
 * The wire-format guard test {@code AstJsonRoundTripTest} configures the
 * mapper accordingly and is the canonical reference. It runs on Jackson 3,
 * which this module pulls at <em>test</em> scope only — the published
 * {@code source-model} jar ships no databind at all, so the mapper is always
 * the consumer's own and the rule above is a consumer obligation, not
 * something the SDK can enforce.
 *
 * <h2>FieldMetadata vs ValidationMetadata — canonical scoping (0.2.0, finalized 0.9.0)</h2>
 * <p>The annotation surface splits field-shape ({@code @Field}) from constraint
 * declarations ({@code @Validation}); the AST carries both on a single record:
 * <ul>
 *   <li><strong>{@link eu.exeris.sdk.sourcemodel.ast.FieldMetadata}</strong>
 *       owns {@code required}, {@code inCreate}, {@code inUpdate} — field-shape /
 *       lifecycle facts populated from {@code @Field} only
 *       ({@code @Validation.required}, deprecated, removal in 1.0.0, is ignored
 *       by the processor) — and is the <em>sole AST carrier</em> of the
 *       constraint values {@code minLength}, {@code maxLength}, {@code min},
 *       {@code max}, {@code pattern}, populated <em>from {@code @Validation}</em>
 *       by the build-time processor and the {@code -io} reader.
 *       ({@code @Field} never declared these attributes — the historically
 *       documented "basic shape hints on {@code @Field}" overlap was fictional
 *       at the annotation level; ADR-054.) DB NOT NULL / not-blank semantics
 *       derive from {@code FieldMetadata.required} at generator level. Since
 *       0.9.0 the four numeric bounds use per-component
 *       {@code @JsonInclude(NON_NULL)} so a zero-valued bound (e.g.
 *       {@code min = 0} non-negativity) survives the wire; the class-level
 *       {@code NON_DEFAULT} treated boxed zero as "empty" and dropped it.
 *       (Same rationale as {@code ViewMetadata}'s deliberate {@code NON_NULL}
 *       choice — see the presentation-IR section below.)</li>
 *   <li><strong>{@code ValidationMetadata} was removed in 0.9.0</strong>
 *       (ADR-054): it was never populated by any processor or reader, never
 *       referenced by {@code DomainMetadata}, and never consumed by any
 *       generator — it never appeared on the domain wire, and no artifact had
 *       ever been published to a registry, so no external dependent could
 *       exist and the deprecation window would have been vacuous (0.x permits
 *       the break). Its {@code notNull} / {@code notBlank} "derived from
 *       {@code required}" design never happened (the derivation lives at
 *       generator level, from {@code FieldMetadata.required});
 *       {@code patternMessage} had no {@code @Validation} source. Do not add a
 *       parallel AST validation carrier — constraint values live on
 *       {@code FieldMetadata}.</li>
 * </ul>
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
 * <h2>Projection source + read-model framing (0.7.0)</h2>
 * <p>{@link eu.exeris.sdk.sourcemodel.ast.ProjectionMetadata} originally carried
 * only {@code name} / {@code description} / {@code fields} / {@code cacheable} —
 * it could say what a view shows but not what it is a view <em>of</em>. 0.7.0
 * adds the source and read-model framing: {@code aggregateTypes} (the source
 * aggregate(s) the projection reads — the "of <em>this</em> aggregate" link the
 * exposed {@code fields} subset was missing), the event subscription that drives
 * it ({@code events} / {@code eventClassNames} / {@code topicPattern}), and the
 * read-model identity ({@code model} / {@code schema}). {@code @Projection}'s
 * large operational surface (partitioning, error handling, rebuild, consistency,
 * monitoring, lifecycle, query API) is deliberately deferred — additive, by-name
 * JSON. Class-valued attributes ({@code model}, {@code eventClasses}) are stored
 * as source-written {@code String} names, the same zero-coupling discipline as
 * the capability and event-handler records.
 *
 * <h2>Saga step kind + typed transitions (0.7.0)</h2>
 * <p>{@link eu.exeris.sdk.sourcemodel.ast.SagaStepMetadata} was already a rich
 * step descriptor (order, service/command, compensation, timeout/retry,
 * parallel, condition, {@code dependsOn}, mappings), but two behavioural facts
 * were missing. 0.7.0 adds them:
 * <ul>
 *   <li><strong>{@code SagaStepMetadata.kind}</strong> — the step's behavioural
 *       {@code StepKind} ({@code INVOKE} / {@code COMPENSATE} / {@code AWAIT_EVENT}
 *       / {@code AWAIT_TIMER}). Today the kind is only <em>implied</em> by which
 *       of {@code service}/{@code command}/{@code compensation} is set;
 *       {@code effectiveKind()} infers {@code INVOKE}/{@code COMPENSATE} from that
 *       structure but deliberately cannot infer the await kinds — which is exactly
 *       why the explicit field exists.</li>
 *   <li><strong>{@code SagaMetadata.transitions}</strong> — typed, outcome-edged
 *       {@code SagaTransition} edges ({@code from} → {@code to} {@code on} a
 *       {@code TransitionOutcome} of {@code SUCCESS}/{@code FAILURE}/{@code TIMEOUT}/
 *       {@code COMPENSATED}, with an optional SpEL {@code guard}). {@code order}
 *       and {@code dependsOn} sequence steps but never carry the <em>outcome</em>
 *       a branch fires on, so a success→next / failure→compensate / timeout→retry
 *       branch (or a loop) couldn't be drawn or generated. This promotes the step
 *       list + {@code dependsOn} DAG into an outcome-edged state-machine graph.</li>
 * </ul>
 * <p>Both use AST-owned enums (consistent with {@code SagaMetadata}'s existing
 * {@code CompensationStrategy} / {@code CompensationOrder} / {@code TriggerType}),
 * not source-written strings — there is no annotation-side {@code kind} or
 * {@code @SagaTransition} to mirror, so the enums are defined here and stay
 * zero-coupling. As with {@code dataType}, the i18n keys, event handlers, and
 * the projection growth, this is reader↔processor-parity-neutral: the
 * {@code -io} reader does <em>not</em> populate {@code kind} / {@code transitions}
 * yet, and the matching {@code @SagaTransition} annotation + extraction is
 * coordinated {@code exeris-tooling} work (RFC-worthy) — the SDK supplies only
 * the record the state machine serializes into.
 *
 * <h2>Declarative behaviour — derived fields + rules (0.7.0)</h2>
 * <p>The declarative-behaviour layer lets a domain declare the <em>mechanical</em>
 * slice of behaviour the AST otherwise can't describe:
 * <ul>
 *   <li><strong>{@link eu.exeris.sdk.sourcemodel.ast.DerivedMetadata}</strong> — a
 *       {@code FieldMetadata} facet ({@link eu.exeris.sdk.sourcemodel.ast.FieldMetadata#derived()},
 *       like {@code dataType}) for a {@code @Derived} field/value computed from
 *       others (roll-up / formula). Carries the {@code expression}, a
 *       {@code language} tag, and {@code dependsOn} hints (sibling field names or
 *       related-entity paths).</li>
 *   <li><strong>{@link eu.exeris.sdk.sourcemodel.ast.RuleMetadata}</strong> — a
 *       {@code DomainMetadata.rules} list entry for a {@code @Rule} named invariant
 *       ({@code name}, {@code expression}, {@code message}, {@code severity}).</li>
 * </ul>
 * <p>Expressions are stored verbatim and tagged by {@code language}; the AST
 * interprets neither (zero runtime coupling). Defaulted attributes follow the
 * blank → {@code null} discipline under {@code @JsonInclude} and the consumer
 * applies the semantic default ({@code effectiveLanguage()} ⇒ {@code "spel"},
 * {@code RuleMetadata.effectiveSeverity()} ⇒ {@code "ERROR"}), so the common case
 * carries neither on the wire. As with the other behaviour facets, this is
 * reader↔processor-parity-neutral: the {@code -io} reader does <em>not</em>
 * populate {@code derived} / {@code rules} yet, and the {@code @Derived} /
 * {@code @Rule} processor extraction + codegen are coordinated
 * {@code exeris-tooling} work. See {@code RFC-2026-06-18}.
 *
 * <h2>Presentation IR — the front facet (0.8.0, reserved)</h2>
 * <p>{@link eu.exeris.sdk.sourcemodel.ast.ViewMetadata} is a net-new,
 * <strong>entity-optional</strong> record family modelling the <em>front facet</em>
 * of a unit — a page / section / component / fragment
 * ({@link eu.exeris.sdk.sourcemodel.ast.ViewKind}) composed of
 * {@link eu.exeris.sdk.sourcemodel.ast.RegionMetadata} regions, each holding a
 * recursive tree of {@link eu.exeris.sdk.sourcemodel.ast.ComponentNodeMetadata}
 * nodes whose leaves carry {@link eu.exeris.sdk.sourcemodel.ast.BindingMetadata}
 * bindings ({@link eu.exeris.sdk.sourcemodel.ast.BlockType} /
 * {@link eu.exeris.sdk.sourcemodel.ast.BindSource} are AST-owned enums,
 * null-tolerated with {@code effective*()} defaults like
 * {@code SagaStepMetadata.kind}). It is orthogonal to the backend (entity /
 * action / capability) facet, references the data source of truth only by name,
 * and never generates persistence.
 * <p>The IR is the <strong>shared</strong> front-facet model authored through
 * <em>two front-doors</em>: the backend-anchored Java annotations
 * ({@code @View} / {@code @Region} / {@code @Block} / {@code @Bind} in
 * {@code exeris-sdk-annotations}, which attach presentation to a real backend
 * type) and a frontend-only path (Studio / hand-authored presentation-IR JSON,
 * owned by {@code exeris-platform} / {@code exeris-tooling}). Both converge on
 * {@code ViewMetadata}; the AST itself is front-door-agnostic.
 * <p>These records deliberately use {@code @JsonInclude(NON_NULL)} (not the
 * prevailing {@code NON_DEFAULT}), per RFC-2026-06-25's "Storage &amp; defaults on
 * the wire": the planned numeric component / binding fields would hit the
 * boxed-zero trap {@code NON_DEFAULT} carries, and {@code NON_NULL} +
 * blank/null-list normalization in the compact constructors avoids it by
 * construction. The surface is <strong>structurally live</strong> since the
 * tooling caught up: the {@code exeris-tooling} processor extracts the
 * {@code @View} family into these records and the codegen-ts Angular view
 * generator emits the component tree (RFC-2026-06-28, tooling). The remaining
 * piece is the ADR-047 leaf-field facet ({@code ComponentNodeMetadata.field} —
 * seeded but not yet populated by the processor), so field-level render detail
 * stays on {@code @UI}. The {@code -io} reader does not read the {@code @View}
 * family; because these records are standalone types not referenced by
 * {@code DomainMetadata}, that gap creates no ADR-042 conflict-detection drift
 * surface, and {@code SchemaVersion} is unchanged. See {@code RFC-2026-06-25}.
 *
 * <h2>Blob + schedule facets (0.11.0, reserved)</h2>
 * <p>{@link eu.exeris.sdk.sourcemodel.ast.FieldMetadata#blob()}
 * ({@link eu.exeris.sdk.sourcemodel.ast.BlobMetadata}) and
 * {@link eu.exeris.sdk.sourcemodel.ast.ActionMetadata#schedule()}
 * ({@link eu.exeris.sdk.sourcemodel.ast.ScheduleMetadata}) are the AST twins of
 * {@code @Blob} and {@code @Schedule} — the design-time expression of the kernel
 * v0.11 blob-storage and job-scheduling seams (ADR-072). Both are trailing,
 * nullable, and absent from the wire unless declared.
 *
 * <p>{@code ScheduleMetadata} collapses the annotation's three mutually exclusive
 * attributes ({@code cron} / {@code every} / {@code at}) into one
 * {@code TriggerKind} discriminator plus the verbatim expression, so a consumer
 * cannot read a cron <em>and</em> an interval — a combination the annotation only
 * forbids in prose. Both new records are class-level {@code NON_NULL} — the
 * posture every small facet record here already uses, with the
 * {@code NON_DEFAULT} on the larger records being the exception.
 *
 * <p>While pinning that, the round-trip suite also pins <em>why</em>, because one
 * standing justification for these choices turned out to be folklore. The
 * repo-wide caveat is correct as stated: {@code NON_DEFAULT} treats a boxed
 * numeric zero as "empty" and drops it, which is what cost the
 * {@link eu.exeris.sdk.sourcemodel.ast.FieldMetadata} bounds a fix in 0.9.0. The
 * extension of that to enums is not — an ordinal-0 constant is not "empty" to
 * Jackson and survives {@code NON_DEFAULT} untouched, contrary to what ADR-059
 * obligation 3 asserted about {@code DataScope.GLOBAL}. Measured in
 * {@code AstJsonRoundTripTest}, so the next inclusion decision reasons from the
 * behaviour rather than from the claim.
 *
 * <p>Both are <strong>reserved</strong>: no processor populates them and no
 * generator consumes them, the kernel holds both SPI packages at tier
 * {@code preview}, and neither enters the 1.0.0 freeze. {@code SchemaVersion}
 * moves to {@code "0.11.0"} regardless — the schema names the shape, not its
 * population.
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
