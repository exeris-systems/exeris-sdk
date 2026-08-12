/**
 * Exeris SDK — the annotation surface for Entity-First development.
 *
 * <h2>What this package is</h2>
 * <p>These annotations are <strong>descriptive metadata</strong>, not runtime
 * behaviour. Every annotation here is {@link java.lang.annotation.RetentionPolicy#SOURCE}
 * — nothing survives into the class file, nothing is on your runtime classpath,
 * and the SDK has zero dependencies. Annotating a class changes what
 * {@code exeris-tooling} <em>emits at build time</em>; it changes nothing about
 * how your program runs.
 *
 * <p>The chain is one-way:
 * <pre>
 *   your annotated .java
 *        │  exeris-tooling / ExerisDomainProcessor   (or the SDK's own -io reader)
 *        ▼
 *   exeris-metadata/&lt;entity&gt;.json                  (the AST — eu.exeris.sdk.sourcemodel.ast)
 *        │  exeris-tooling / codegen-java + codegen-ts
 *        ▼
 *   emitted Java, SQL, OpenAPI, Angular
 * </pre>
 *
 * <p>An annotation only has an effect if <em>both</em> hops carry it. Much of
 * this package is declared but not yet carried, so each annotation's javadoc
 * states its status explicitly.
 *
 * <h2>Status vocabulary</h2>
 * <p>Every annotation — and, where they differ, every attribute — is labelled
 * with one of four words. They are used consistently across this package, the
 * AST package, and {@code docs/guide/}:
 * <dl>
 *   <dt><strong>LIVE</strong></dt>
 *   <dd>Extracted into the AST <em>and</em> read by a generator that emits
 *       something. Writing it changes emitted output.</dd>
 *
 *   <dt><strong>PARTIAL</strong></dt>
 *   <dd>Extracted into the AST, but no generator reads it — or only one of the
 *       two readers (processor / {@code -io}) handles it. It survives the wire
 *       and stops there. Safe to write; produces nothing today.</dd>
 *
 *   <dt><strong>RESERVED</strong></dt>
 *   <dd>Declared here and read by nobody. It does not reach the AST at all.
 *       The javadoc says what it is waiting on. Writing it is a no-op — it
 *       compiles, and that is all it does.</dd>
 *
 *   <dt><strong>DEPRECATED</strong></dt>
 *   <dd>{@code @Deprecated(forRemoval = true)} with a named replacement and a
 *       named removal release. See {@code MIGRATION.md}.</dd>
 * </dl>
 *
 * <p>The honest summary: of the annotations in this package, roughly twenty are
 * referenced by the build-time processor at all. The rest are RESERVED. That is
 * deliberate — the annotation surface is designed ahead of the generators — but
 * it means <strong>you cannot infer from an attribute's existence that it does
 * anything</strong>. Read the status label.
 *
 * <h2>What is actually generated today</h2>
 * <p>From a {@code @ExerisDomain}-annotated class, {@code exeris-tooling} emits:
 * <ul>
 *   <li><strong>Java (kernel tier)</strong> — request handler, service, repository,
 *       domain event + event handler, saga driver, graph-sync writer, streaming
 *       handlers, internal client, and the application bootstrap; plus generated
 *       tests for the handler / service / repository / saga.</li>
 *   <li><strong>SQL</strong> — a Flyway migration for the entity table, including
 *       the tenant column and RLS policy when the entity is tenant-partitioned,
 *       and the audit / soft-delete / version columns when those flags are set.</li>
 *   <li><strong>OpenAPI</strong> — the specification document for the emitted REST
 *       surface.</li>
 *   <li><strong>TypeScript / Angular</strong> — request/response types, enums, an
 *       HTTP service, a form, a list, and any {@code @View}-declared page, wired
 *       into a generated app structure.</li>
 * </ul>
 *
 * <p><strong>Not generated, despite what older javadoc in this package used to
 * claim:</strong> there is no GraphQL emitter anywhere in the toolchain.
 * {@code @ExerisDomain(graphqlApi = true)} reaches the AST and is read by no
 * generator — it is PARTIAL, not LIVE. The {@code @Graph*} family is about
 * graph-<em>database</em> projection, not GraphQL.
 *
 * <h2>Quick start</h2>
 *
 * <h3>1. Resolve the artifacts</h3>
 * <p>There are no {@code eu.exeris} artifacts on Maven Central yet. The Central
 * Portal endpoints are wired in the root POM, but publishing is deliberately not
 * switched on before the kernel moves — an ecosystem sequencing decision.
 * Releases ship as a git tag plus a GitHub Release. Until that changes, build the
 * SDK from source and consume the local install:
 * <pre>{@code
 * git clone https://github.com/exeris-systems/exeris-sdk && cd exeris-sdk && mvn -q install
 * }</pre>
 * <p>Then depend on it:
 * <pre>{@code
 * <dependency>
 *     <groupId>eu.exeris</groupId>
 *     <artifactId>exeris-sdk-annotations</artifactId>
 *     <version>0.10.0</version>
 *     <scope>provided</scope>
 * </dependency>
 * }</pre>
 * <p>{@code provided} is the honest scope: the annotations are
 * {@code RetentionPolicy.SOURCE}, so they are needed to compile and never at
 * runtime.
 *
 * <h3>2. Annotate a class</h3>
 * <p>Note the shape carefully — <strong>every annotation is applied as a sibling
 * of the one it relates to</strong>, never nested inside it. See "The nested-form
 * trap" below; it is the single most common way to write Exeris metadata that
 * silently does nothing.
 * <pre>{@code
 * @ExerisDomain(
 *     module = "sales",
 *     aggregate = "Order",
 *     path = "/orders",
 *     dataScope = ExerisDomain.DataScope.TENANT,
 *     softDelete = true,
 *     audited = true
 * )
 * @UI(listView = true, detailView = true, createForm = true)   // sibling of @ExerisDomain
 * public class Order {
 *
 *     @Field(label = "Order Number", required = true, unique = true, indexed = true)
 *     @Validation(minLength = 3, maxLength = 32)                // sibling of @Field
 *     private String orderNumber;
 *
 *     @Field(label = "Customer", required = true)
 *     @Relationship(targetEntity = Customer.class, displayField = "name")
 *     private UUID customerId;
 *
 *     @Field(label = "Total", readOnly = true, computed = true,
 *            computedFrom = {"unitPrice", "quantity"})
 *     private BigDecimal total;
 * }
 * }</pre>
 * <p>The class needs no superclass. There is no {@code BaseTenantEntity} or any
 * other SDK base type — the SDK ships annotations and records only, and tenant /
 * audit / soft-delete columns are derived by the generator from the
 * {@code @ExerisDomain} flags above, not inherited from a parent class.
 *
 * <h3>3. Generate</h3>
 * <p>Codegen runs from {@code exeris-tooling}'s Maven plugin
 * ({@code exeris-codegen-maven-plugin}, goal prefix {@code exeris}):
 * <pre>{@code
 * mvn exeris:generate
 * }</pre>
 * <p>The plugin's own documentation in {@code exeris-tooling} is authoritative
 * for its configuration, the annotation-processor wiring, and the JDK it runs
 * under.
 *
 * <h2>The nested-form trap</h2>
 * <p>Several annotations here declare a nested member typed as another
 * annotation — {@code @Field(validation = @Validation(...))},
 * {@code @Field(ui = @UI(...))}, {@code @ExerisDomain(ui = @UI(...))}. These
 * members exist, they compile, and <strong>no reader looks at them</strong>.
 * Both the {@code exeris-tooling} processor and the SDK's own {@code -io} reader
 * find related annotations by walking the element's <em>directly present</em>
 * annotations, so only the sibling form is seen:
 * <pre>{@code
 * // NO-OP — compiles clean, reaches no AST, produces nothing:
 * @Field(label = "Email", validation = @Validation(email = true))
 * private String email;
 *
 * // READ — the constraint reaches FieldMetadata:
 * @Field(label = "Email")
 * @Validation(email = true)
 * private String email;
 * }</pre>
 * <p>The nested members are RESERVED surface kept for a possible future reader.
 * Until one exists, treat them as traps and always write siblings.
 *
 * <h2>Annotation index by status</h2>
 * <p>Annotation-level status. Attribute-level status is on each annotation's own
 * javadoc, and the full per-attribute matrix is in {@code docs/guide/}.
 * <dl>
 *   <dt><strong>LIVE</strong> — reach the AST and drive emitted output</dt>
 *   <dd>{@link eu.exeris.sdk.annotation.ExerisDomain @ExerisDomain},
 *       {@link eu.exeris.sdk.annotation.Field @Field},
 *       {@link eu.exeris.sdk.annotation.Validation @Validation},
 *       {@link eu.exeris.sdk.annotation.Relationship @Relationship},
 *       {@link eu.exeris.sdk.annotation.Action @Action},
 *       {@link eu.exeris.sdk.annotation.ActionParam @ActionParam},
 *       {@link eu.exeris.sdk.annotation.DomainEvent @DomainEvent},
 *       {@link eu.exeris.sdk.annotation.Saga @Saga},
 *       {@link eu.exeris.sdk.annotation.SagaStep @SagaStep},
 *       {@link eu.exeris.sdk.annotation.Graph @Graph},
 *       {@link eu.exeris.sdk.annotation.InternalApi @InternalApi},
 *       {@link eu.exeris.sdk.annotation.View @View} with
 *       {@link eu.exeris.sdk.annotation.Region @Region} /
 *       {@link eu.exeris.sdk.annotation.Block @Block} /
 *       {@link eu.exeris.sdk.annotation.Bind @Bind},
 *       and the {@code capability} subpackage
 *       ({@code @CapabilityModule}, {@code @Provides}, {@code @Requires},
 *       {@code @CapabilityLifecycle}), which drives {@code cap-manifest.json}.
 *       Not every attribute of a LIVE annotation is itself LIVE.</dd>
 *
 *   <dt><strong>PARTIAL</strong> — reach the AST, read by no generator</dt>
 *   <dd>{@link eu.exeris.sdk.annotation.UI @UI}. Applied as a sibling of the
 *       <em>type</em> its view flags land in {@code UIMetadata}, but no emitter
 *       gates on them — which views are generated is a codegen CLI setting, not
 *       an annotation. Applied to a <em>field</em> (in either form) it is read
 *       by nobody: there is currently no field-level presentation path, and
 *       {@code @UI}'s own javadoc claim to the contrary is being corrected.</dd>
 *
 *   <dt><strong>RESERVED</strong> — declared, extracted by nobody, no effect</dt>
 *   <dd>The declarative-behaviour pair
 *       {@link eu.exeris.sdk.annotation.Derived @Derived} /
 *       {@link eu.exeris.sdk.annotation.Rule @Rule}
 *       ({@link eu.exeris.sdk.annotation.Rules @Rules});
 *       the graph detail annotations
 *       {@link eu.exeris.sdk.annotation.GraphEdge @GraphEdge},
 *       {@link eu.exeris.sdk.annotation.GraphProperty @GraphProperty},
 *       {@link eu.exeris.sdk.annotation.GraphQuery @GraphQuery};
 *       the saga state-machine half
 *       {@link eu.exeris.sdk.annotation.SagaTransition @SagaTransition};
 *       {@link eu.exeris.sdk.annotation.EventHandler @EventHandler},
 *       {@link eu.exeris.sdk.annotation.EventSourced @EventSourced},
 *       {@link eu.exeris.sdk.annotation.Projection @Projection},
 *       {@link eu.exeris.sdk.annotation.QueryParam @QueryParam};
 *       the layout annotations
 *       {@link eu.exeris.sdk.annotation.Tab @Tab},
 *       {@link eu.exeris.sdk.annotation.UIGroup @UIGroup},
 *       {@link eu.exeris.sdk.annotation.NavMenu @NavMenu};
 *       and <strong>the whole {@code system} and {@code security} subpackages</strong>
 *       — {@code @PrimaryKey}, {@code @TenantId}, {@code @Version},
 *       {@code @SoftDelete}, the {@code @Audit*} family, {@code @Encrypted},
 *       {@code @RowLevelSecurity}. See the note below.</dd>
 * </dl>
 *
 * <h2>System fields are driven by flags, not by field annotations</h2>
 * <p>This is the most surprising consequence of the index above, so it is worth
 * stating plainly: the {@code system} subpackage annotations are <strong>not
 * read by the processor</strong>. Marking a field {@code @TenantId} or
 * {@code @SoftDelete} does not create a column, a filter, or a policy.
 *
 * <p>What generates those columns is the entity-level flag set on
 * {@code @ExerisDomain}: {@link eu.exeris.sdk.annotation.ExerisDomain#dataScope()}
 * for tenant partitioning, {@link eu.exeris.sdk.annotation.ExerisDomain#softDelete()},
 * {@link eu.exeris.sdk.annotation.ExerisDomain#audited()},
 * {@link eu.exeris.sdk.annotation.ExerisDomain#versioned()}. The generator adds
 * the column, the migration, the repository filter and the RLS policy from the
 * flag alone. The {@code system} annotations exist to let a future reader
 * override the derived names; today they are documentation for humans.
 *
 * <h2>Data scope (formerly {@code tenantScoped})</h2>
 * <p>{@link eu.exeris.sdk.annotation.ExerisDomain#tenantScoped()} is
 * {@code @Deprecated(forRemoval = true)} as of 0.10.0 (ADR-059). Its successor is
 * the three-tier {@link eu.exeris.sdk.annotation.ExerisDomain#dataScope()}:
 * <ul>
 *   <li>{@code GLOBAL} — one row set shared by every tenant. Today's
 *       {@code tenantScoped = false}.</li>
 *   <li>{@code TENANT} — partitioned; no tenant can read another's rows. Today's
 *       {@code tenantScoped = true}. Emits the tenant column, the RLS policy and
 *       the query filter.</li>
 *   <li>{@code UNIVERSE} — the cross-tenant shared-world tier. <strong>Read the
 *       status note on {@code dataScope()} before using it:</strong> the generator
 *       currently emits the full {@code TENANT} shape for {@code UNIVERSE}, which
 *       is strictly narrower than what the tier declares. It is fail-closed, not
 *       complete.</li>
 * </ul>
 * <p>{@code UNSPECIFIED} defers to {@code tenantScoped} for the remainder of the
 * deprecation window, which closes at 1.0.0. Declaring both with contradicting
 * values is a build-time error.
 *
 * <h2>Field vs Validation — canonical scoping</h2>
 * <p>The one rule that decides where an attribute belongs:
 * <ul>
 *   <li><strong>{@code @Field}</strong> describes <em>what the field is</em> —
 *       data shape, persistence, lifecycle. {@code required}, {@code inCreate},
 *       {@code inUpdate}, {@code unique}, {@code indexed} live here. It declares
 *       no constraint attributes at all.</li>
 *   <li><strong>{@code @Validation}</strong> is the sole declaration site of the
 *       constraint rules — {@code min}, {@code max}, {@code minLength},
 *       {@code maxLength}, {@code pattern}, and the flag constraints.</li>
 * </ul>
 * <p>The AST carrier for both is a single record, {@code FieldMetadata}; there is
 * no parallel validation record (ADR-054 removed it in 0.9.0). Database
 * NOT NULL / not-blank semantics are <em>derived</em> from
 * {@code FieldMetadata.required} at generator level rather than separately
 * declared.
 *
 * <p>Two {@code @Validation} attributes are DEPRECATED and removed at 1.0.0:
 * <ul>
 *   <li>{@code @Validation.required} → {@link eu.exeris.sdk.annotation.Field#required()}</li>
 *   <li>{@code @Validation.validateOn} → {@link eu.exeris.sdk.annotation.Field#inCreate()} /
 *       {@link eu.exeris.sdk.annotation.Field#inUpdate()} — form-lifecycle scope is a
 *       field property, not a validation rule.</li>
 * </ul>
 * <p>The processor reads both as a fallback with a build warning until 1.0.0.
 * Note that only {@code min}, {@code max}, {@code minLength}, {@code maxLength}
 * and {@code pattern} are carried into the AST; the flag constraints
 * ({@code email}, {@code url}, {@code future}, {@code past}, …) are declared here
 * but not yet extracted — PARTIAL at best. Check
 * {@link eu.exeris.sdk.annotation.Validation @Validation}'s own javadoc per
 * attribute.
 *
 * <h2>i18n message keys and the CUSTOM component escape hatch — RESERVED</h2>
 * <p>{@link eu.exeris.sdk.annotation.Field#labelKey()} /
 * {@link eu.exeris.sdk.annotation.Field#descriptionKey()} and
 * {@link eu.exeris.sdk.annotation.UI#placeholderKey()} /
 * {@link eu.exeris.sdk.annotation.UI#helpTextKey()} declare the intended
 * "key wins, literal is the fallback" resolution, and
 * {@link eu.exeris.sdk.annotation.UI.ComponentType#CUSTOM} with
 * {@link eu.exeris.sdk.annotation.UI#customComponent()} declares the escape hatch
 * out of the closed component enum. Neither is extracted by any reader today, so
 * neither has a generated effect — they are RESERVED, not the live i18n path the
 * 0.6.0 notes described.
 *
 * <h2>Declarative-behaviour layer — RESERVED</h2>
 * <p>{@link eu.exeris.sdk.annotation.Derived} and {@link eu.exeris.sdk.annotation.Rule}
 * (repeatable via {@link eu.exeris.sdk.annotation.Rules}) let a domain declare the
 * <em>mechanical</em> slice of behaviour the AST cannot otherwise describe —
 * derived fields / roll-ups and named invariants / guards — so a design-time tool
 * can draw it and tooling can eventually generate it, leaving complex logic as
 * hand-written escape-hatch code. The expression is stored verbatim and tagged by
 * a {@code language} attribute (empty ⇒ {@code "spel"}); the SDK interprets
 * nothing, preserving zero runtime coupling. No processor extraction and no
 * generator consumes them, so declaring them today has no generated effect.
 * Direction in RFC-2026-06-18 (ACCEPTED).
 *
 * <h2>Architecture principles</h2>
 * <ul>
 *   <li><strong>Entity-First (ADR-003)</strong> — the annotated class is the single
 *       source of truth; SQL, endpoints, DTOs and UI are generated artifacts.
 *       There are no hand-written {@code .proto} or OpenAPI files.</li>
 *   <li><strong>Zero runtime coupling</strong> — {@code RetentionPolicy.SOURCE},
 *       no dependencies, nothing on your runtime classpath.</li>
 *   <li><strong>JDK 25 LTS baseline</strong> — these jars sit on a consumer's
 *       compile classpath, so the SDK follows the kernel's GA line rather than
 *       leading it (ADR-069).</li>
 *   <li><strong>Additive after 1.0.0</strong> — 0.x may break in any release and
 *       downstream consumers pin exact versions; 1.0.0 freezes this surface to
 *       additive minors and bug-fix patches.</li>
 * </ul>
 *
 * <h2>Where to go next</h2>
 * <ul>
 *   <li>{@code docs/guide/} — the user guide and the full per-attribute status matrix.</li>
 *   <li>{@code MIGRATION.md} — every deprecation, its replacement and its removal release.</li>
 *   <li>{@code ROADMAP.md} — what each release added and what 1.0.0 freezes.</li>
 *   <li>{@code exeris-tooling} — the processor and generators; authoritative for
 *       what is emitted and how to run it.</li>
 * </ul>
 *
 * @since 0.1.0
 */
package eu.exeris.sdk.annotation;

