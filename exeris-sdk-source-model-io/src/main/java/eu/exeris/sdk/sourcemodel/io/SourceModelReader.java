package eu.exeris.sdk.sourcemodel.io;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.nodeTypes.NodeWithExtends;
import com.github.javaparser.ast.nodeTypes.NodeWithImplements;
import com.github.javaparser.ast.nodeTypes.NodeWithTypeParameters;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.WildcardType;
import eu.exeris.sdk.sourcemodel.ast.ActionMetadata;
import eu.exeris.sdk.sourcemodel.ast.ActionParamMetadata;
import eu.exeris.sdk.sourcemodel.ast.BlobMetadata;
import eu.exeris.sdk.sourcemodel.ast.CapabilityModuleMetadata;
import eu.exeris.sdk.sourcemodel.ast.DataScope;
import eu.exeris.sdk.sourcemodel.ast.DomainEventMetadata;
import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.EnumMetadata;
import eu.exeris.sdk.sourcemodel.ast.EventSourcedMetadata;
import eu.exeris.sdk.sourcemodel.ast.FieldMetadata;
import eu.exeris.sdk.sourcemodel.ast.GraphEdgeMetadata;
import eu.exeris.sdk.sourcemodel.ast.GraphMetadata;
import eu.exeris.sdk.sourcemodel.ast.GraphPropertyMetadata;
import eu.exeris.sdk.sourcemodel.ast.InternalApiMetadata;
import eu.exeris.sdk.sourcemodel.ast.NavMenuMetadata;
import eu.exeris.sdk.sourcemodel.ast.ParamSource;
import eu.exeris.sdk.sourcemodel.ast.ProvidesMetadata;
import eu.exeris.sdk.sourcemodel.ast.RelationshipMetadata;
import eu.exeris.sdk.sourcemodel.ast.RelationshipMetadata.RelationType;
import eu.exeris.sdk.sourcemodel.ast.RequiresMetadata;
import eu.exeris.sdk.sourcemodel.ast.RouteAccess;
import eu.exeris.sdk.sourcemodel.ast.SagaMetadata;
import eu.exeris.sdk.sourcemodel.ast.SagaStepMetadata;
import eu.exeris.sdk.sourcemodel.ast.SystemFieldsMetadata;
import eu.exeris.sdk.sourcemodel.ast.UIMetadata;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Reads Java source into a {@link DomainMetadata} without invoking {@code javac}
 * — the in-editor counterpart to the build-time annotation processor.
 *
 * <p>Locates the first {@code @ExerisDomain}-annotated type and extracts its entity name
 * (the class simple name — see {@code toDomain}), package, fields, and {@code @Relationship}s.
 * Fields mirror the processor's two-path contract: a field <b>with</b> {@code @Field} reads the full
 * attribute surface the processor reads (label, description, required, unique,
 * indexed, searchable, sortable, filterable, readOnly, inCreate, inUpdate,
 * computed, computedFrom) present-only on a {@link FieldMetadata.Builder}, plus
 * field-level {@code @Validation} (min/max/minLength/maxLength/pattern and the
 * deprecated {@code required}/{@code validateOn} fallbacks); a field <b>without</b>
 * {@code @Field} uses {@link FieldMetadata#simple} (search/sort/filter default
 * {@code true}) — the processor's deliberate default asymmetry, reproduced exactly.
 * For relationships, {@code targetEntity} (collection element type unwrapped),
 * {@code relationshipType}, and {@code mappedBy} are read; other attributes keep
 * builder defaults. {@code @Action} methods are read into {@link ActionMetadata}
 * (name, label, httpMethod, async, permissions, the return type as {@code resultType}, and
 * the parameters carrying {@code @ActionParam}, {@code @QueryParam} or both, a
 * {@code @QueryParam} one bound from the query string);
 * {@link #readEnums} extracts enum declarations separately, as the processor
 * does. Class-level {@code @UI} is read into {@link UIMetadata} (view flags,
 * matching the processor's default-true convention), field-level {@code @Tab} into
 * {@link UIMetadata#tabs()}, field-level {@code @UIGroup} into {@link UIMetadata#groups()},
 * field-level {@code @Blob} into {@link FieldMetadata#blob()}, and class-level {@code @NavMenu} into
 * {@link DomainMetadata#navMenu()}. The domain-level
 * {@code @ExerisDomain} string + boolean attributes the processor reads
 * (module, path, aggregate, description, the {@code *Api} flags,
 * tenantScoped, softDelete, audited, versioned, sensitive, cacheable,
 * cache/search config) are read present-only, as is the ADR-059
 * {@code dataScope} tier, and so is {@code tableName}, stored as written as
 * the processor stores it. One of those is
 * deprecated for removal at 1.0.0 and read until then: {@code tenantScoped}
 * (ADR-059). {@code apiVersion}, deprecated for removal too, is not read, as the
 * processor does not read it (see {@code MIGRATION.md}). Domain
 * {@code @DomainEvent}s are read
 * into {@link DomainMetadata#events} (direct/repeated, hand-written
 * {@code @DomainEvents} container, and nested-class legacy form — mirroring the
 * processor's three sources and its trigger-based name derivation). Class-level
 * {@code @Graph} (with its fields' {@code @GraphEdge}s and {@code @GraphProperty}s), {@code @EventSourced},
 * {@code @Saga} (with {@code @SagaStep} methods), and {@code @InternalApi} are read
 * into the matching {@link DomainMetadata} facets, each mirroring the processor's
 * extraction (including its {@code streamPrefix→aggregateType} translation and
 * presence-only {@code @InternalApi}). {@link DomainMetadata#systemFields} is read
 * from the {@code annotation.system} field markers, {@code @PrimaryKey} included, and the
 * {@code @ExerisDomain} {@code *Field} override attributes, resolved as the processor
 * resolves them.
 *
 * <p>Route access (exeris-tooling ADR-105): {@code @RouteAccess} on the type is read into
 * {@link DomainMetadata#routeAccess()}, and beside an {@code @Action} into
 * {@link ActionMetadata#routeAccess()}, {@code PUBLIC} included; {@code @ExerisDomain.permissions}
 * and {@code @Action.permissions} are read in declaration order. Each element carries only
 * what it declares: an action declaring neither keeps a {@code null} {@code routeAccess} and
 * empty {@code permissions}, because nearest-declaration resolution belongs to the generator
 * that compiles the route policy, not to the metadata.
 *
 * <p>Capability scope: {@link #readCapabilityModule} is the capability counterpart of {@link #read}:
 * it locates the first {@code @CapabilityModule}-annotated class and extracts its {@code @Provides}
 * (direct, repeated, or hand-written {@code @Provides.List} container) and
 * {@code @Requires} declarations into {@link CapabilityModuleMetadata}. Per
 * ADR-038, {@code service} is persisted <em>as written in source</em> (simple or
 * qualified — no symbol solving); FQN normalization is build-time tooling's job.
 * Blank {@code version} / {@code versionRange} map to {@code null}, mirroring the
 * AST records' "unversioned" convention. {@code lifecycleOwner} is the
 * fully-qualified name of the {@code @CapabilityLifecycle}-annotated class
 * <em>when it is declared in the same compilation unit</em>; a lifecycle owner in
 * a different file is invisible to a single-source reader and is left for
 * build-time tooling to link (the processor sees the whole round).
 *
 * <p><b>Round-trip safety.</b> Relative to the processor (the codegen baseline),
 * {@code read()} drops no annotation-level facet the processor emits, and the field facet is
 * attribute-complete against {@code extractFieldMetadata}. {@link #unmodeledFacets} reports
 * what {@code read()} cannot vouch for: the annotations whose carrier the processor fills, or
 * is scheduled to fill, in a shape this reader does not read ({@code @Channel}), the reserved
 * {@code @Schedule} whose carrier this reader leaves empty, a {@code @SagaStep.order} the reader
 * cannot resolve, a {@code permissions} element that is not a string literal, and a
 * {@code @RouteAccess} whose value is not a {@code Level} constant. A round-trip /
 * reattach caller consults it and refuses (or warns) when it is non-empty. (Facets neither side
 * reads and no carrier is scheduled for — {@code @Projection}, {@code @EventHandler},
 * {@code @GraphQuery}, the {@code security} annotations — are not divergences.) The guard is
 * annotation-level, with those attributes as the exception: attribute completeness
 * of the <em>other</em> read annotations (e.g. {@code @Relationship}, {@code @Action})
 * is verified per-slice, not by it.
 *
 * <p>The reverse divergence — a facet this reader carries and a processor does not extract
 * yet — is not reported by the guard. {@code @QueryParam}, {@code @NavMenu}, {@code @Tab},
 * {@code @UIGroup}, {@code @GraphProperty}, {@code @Blob}, the {@code @Saga} compensation section
 * and {@code @Saga.trigger} are read here ahead of a processor that does not extract them, in the
 * shape ADR-042's reader-shape amendment fixes for {@code @UIGroup}, {@code @GraphProperty} and
 * {@code @Blob}. {@link SourceModelConflictDetector} compares whole actions, so against such a
 * processor's baseline an action with a {@code @QueryParam} parameter reads as changed, and a
 * mutation of that action is reported as a conflict rather than applied; it compares no
 * navigation, tab or saga component.
 *
 * <p><b>Limitations.</b> Annotation matching is by <em>simple name</em>
 * ({@code ExerisDomain}, {@code Field}, {@code Relationship}) without import
 * resolution — activating JavaParser symbol-solving would pull heavy optional
 * deps and is out of scope. For the same reason attribute values are read
 * syntactically: a string or number attribute is read from its literal, so a value
 * supplied through a constant reference or a constant expression — which the
 * processor receives already folded by javac — is not resolved here. For
 * {@code @Saga.version} that means a constant-valued version reads as the default
 * {@code 1}, a different kernel plan identity than the one the processor records, so
 * declare it as a literal. {@code @SagaStep.order} is the exception: it decides the
 * saga's sequence, so the reader also folds a {@code final} field declared in the same
 * source file and initialised with a literal, and reports through
 * {@link #unmodeledFacets} an order it still cannot resolve. Instances are <b>not thread-safe</b> (they hold a single
 * {@code JavaParser}); use one per call-site or guard externally.
 *
 * @since 0.3
 */
public final class SourceModelReader {

    /**
     * Annotation simple names where {@link #read} <em>may diverge from the annotation
     * processor</em>: the processor populates, or is scheduled to populate, a
     * {@link DomainMetadata} component from them, and this reader does not read them.
     * These are the dangerous ones for reattach — the conflict-detection baseline is the
     * processor's codegen output, so a facet the processor keeps and the reader drops
     * would be misread as a user edit.
     *
     * <p>Each member has an AST carrier whose shape or extraction rule is not fixed:
     * {@code @Channel} ({@code DomainMetadata.channel}) encodes the kernel WebSocket SPI, which
     * stays at tier {@code preview}, and {@code @Schedule} ({@code ActionMetadata.schedule}) has
     * no extraction rule at all while the kernel issues no service principal: a declared job is
     * refused at build time (kernel ADR-057 Amendment A1), and a round-trip that dropped the
     * declaration would hide it. Reading one would produce a value the processor may not, which
     * is the drift this guard reports, so the reader leaves the carrier at its default and the
     * guard names the annotation instead. A member leaves the set in the change that fixes its
     * shape and reads it. Matched by simple name (no import resolution).
     */
    private static final Set<String> UNMODELED_FACET_ANNOTATIONS = Set.of("Channel", "Schedule");

    /** Attribute names several annotations share. */
    private static final String LABEL = "label";
    private static final String ORDER = "order";
    private static final String REQUIRED = "required";
    private static final String VALUE = "value";

    /**
     * How many constant references and wrappers {@link #intConstant} follows before giving up,
     * so a cyclic initialiser, which javac rejects, cannot loop the reader.
     */
    private static final int MAX_CONSTANT_DEPTH = 16;

    /** The route-authorization scope attribute on both {@code @ExerisDomain} and {@code @Action}. */
    private static final String PERMISSIONS = "permissions";
    private static final String TOPIC = "topic";
    private static final String TRIGGER = "trigger";
    /** The {@code CREATE} constant shared by {@code @DomainEvent.trigger} and the deprecated {@code validateOn}. */
    private static final String CREATE = "CREATE";

    private final JavaParser javaParser;

    /**
     * Creates a reader with the default JavaParser configuration.
     */
    public SourceModelReader() {
        this.javaParser = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.CURRENT));
    }

    /**
     * Parses {@code javaSource} and returns the domain model for its first
     * {@code @ExerisDomain} type, or {@link Optional#empty()} if the source
     * contains no such type.
     *
     * @param javaSource the entity's Java source text
     * @return the entity's metadata, or empty when the source declares no {@code @ExerisDomain}
     * @throws IllegalArgumentException if the source is not valid Java
     */
    public Optional<DomainMetadata> read(String javaSource) {
        CompilationUnit cu = parseOrThrow(javaSource);
        return cu.findFirst(ClassOrInterfaceDeclaration.class,
                        type -> type.isAnnotationPresent("ExerisDomain"))
                .map(type -> toDomain(cu, type));
    }

    /**
     * Extracts every enum declaration in {@code javaSource} as an
     * {@link EnumMetadata} (name, package, qualified name, constant values),
     * mirroring how the processor emits enum metadata separately from entities.
     *
     * @param javaSource the entity's Java source text
     * @return the enums the source declares, in declaration order
     * @throws IllegalArgumentException if the source is not valid Java
     */
    public List<EnumMetadata> readEnums(String javaSource) {
        CompilationUnit cu = parseOrThrow(javaSource);
        String packageName = packageName(cu);
        List<EnumMetadata> enums = new ArrayList<>();
        for (EnumDeclaration decl : cu.findAll(EnumDeclaration.class)) {
            String name = decl.getNameAsString();
            String qualifiedName = packageName.isEmpty() ? name : packageName + "." + name;
            List<EnumMetadata.EnumValueMetadata> values = new ArrayList<>();
            int ordinal = 0;
            for (EnumConstantDeclaration constant : decl.getEntries()) {
                values.add(new EnumMetadata.EnumValueMetadata(
                        constant.getNameAsString(), null, null, ordinal++));
            }
            enums.add(new EnumMetadata(name, qualifiedName, packageName, null, values));
        }
        return enums;
    }

    /**
     * Parses {@code javaSource} and returns the capability-module model for its
     * first {@code @CapabilityModule} class, or {@link Optional#empty()} if the
     * source contains no such class — the capability counterpart of {@link #read}.
     * See the class javadoc ("0.4.0 capability scope") for the written-form
     * service contract and the same-compilation-unit {@code lifecycleOwner}
     * limitation.
     *
     * @param javaSource the entity's Java source text
     * @return the capability module's metadata, or empty when the source declares none
     * @throws IllegalArgumentException if the source is not valid Java
     * @since 0.4
     */
    public Optional<CapabilityModuleMetadata> readCapabilityModule(String javaSource) {
        CompilationUnit cu = parseOrThrow(javaSource);
        return cu.findFirst(ClassOrInterfaceDeclaration.class,
                        type -> type.isAnnotationPresent("CapabilityModule"))
                .map(type -> CapabilityModuleMetadata.builder()
                        .provides(provides(type))
                        .requires(requires(type))
                        .lifecycleOwner(lifecycleOwner(cu))
                        .build());
    }

    /**
     * Reports the metadata facets where {@link #read} <b>diverges from the
     * annotation processor</b> for {@code javaSource}: facets the processor would
     * emit into {@link DomainMetadata} but the reader does not yet. A <b>non-empty</b>
     * result means {@code read()}'s {@code DomainMetadata} differs from the codegen
     * baseline — round-trip / reattach callers must treat it as unsafe for conflict
     * detection (the dropped facets would be misattributed as user edits). An
     * <b>empty</b> result means {@code read()} produces the same {@code DomainMetadata}
     * facets the processor would for this source.
     *
     * <p>Detected: presence of any annotation in {@link #UNMODELED_FACET_ANNOTATIONS} —
     * {@code @Channel} and {@code @Schedule}, whose carriers {@link #read} leaves at their
     * defaults. <b>Not</b> flagged: facets neither side reads and no carrier is scheduled for ({@code @Projection},
     * {@code @EventHandler}, {@code @GraphQuery}, the {@code security} annotations), and
     * facets this reader reads ahead of the processor ({@code @QueryParam}, {@code @NavMenu},
     * {@code @Tab}, {@code @UIGroup}, {@code @GraphProperty}, {@code @Blob}): the guard reports
     * what a round-trip would drop, and the reader drops none of those.
     *
     * <p>Also detected, as {@code @SagaStep}: a saga step on the {@code @ExerisDomain}
     * type whose {@code order} this reader cannot resolve — a constant from another file,
     * an inherited or statically imported one, or arithmetic. {@link #read} carries such a
     * step at order {@code 1}, while the processor receives the folded value, so the step
     * sequence of the two may differ. An order that is a literal, or a {@code final} field
     * of the same file initialised with one, is resolved and not reported.
     *
     * <p>Also detected, as {@code @ExerisDomain} or {@code @Action}: a {@code permissions}
     * element on the entity or on one of its actions that is not a string literal — a constant
     * reference, say. {@link #read} carries literals only, while the processor receives the
     * folded value, so the two may disagree on what the route requires.
     *
     * <p>Also detected, as {@code @RouteAccess}: one on the entity or on one of its actions whose
     * value is not a bare or qualified {@code Level} constant. {@link #read} carries nothing for
     * it, so the route would fall back to the level of a route that declares none, while the
     * processor receives the folded constant. A constant in parentheses is folded and not
     * reported.
     *
     * <p>Apart from those attributes, this guard is <em>annotation-level</em>: it does not
     * report attribute-level loss <em>within</em> a read annotation. {@code @Field}/{@code @Validation} are read
     * attribute-complete; completeness of the other read annotations
     * ({@code @Relationship}, {@code @Action}, {@code @UI}, {@code @ExerisDomain}) is
     * verified by their own tests rather than by this method. Matching is by simple name (no import resolution).
     * Re-parses {@code javaSource} independently of {@link #read}.
     *
     * @param javaSource the entity's Java source text
     * @return the names of annotations present in the source that this reader does not model — the honest report of what a round-trip would drop
     */
    public Set<String> unmodeledFacets(String javaSource) {
        CompilationUnit cu = parseOrThrow(javaSource);
        // The guard arms for both modelled roots: @ExerisDomain entities and
        // @CapabilityModule caps.
        Optional<ClassOrInterfaceDeclaration> root = cu.findFirst(ClassOrInterfaceDeclaration.class,
                type -> type.isAnnotationPresent("ExerisDomain")
                        || type.isAnnotationPresent("CapabilityModule"));
        if (root.isEmpty()) {
            return Set.of();
        }

        Set<String> facets = new TreeSet<>();
        for (AnnotationExpr annotation : cu.findAll(AnnotationExpr.class)) {
            String simpleName = simpleName(annotation.getNameAsString());
            if (UNMODELED_FACET_ANNOTATIONS.contains(simpleName)) {
                facets.add("@" + simpleName);
            }
        }
        // Attribute-level, the exceptions to the annotation-level rule: a step order read()
        // cannot resolve is a position the processor may hold differently, and a misplaced
        // step is a reordered saga; a permission it cannot fold changes what a route requires.
        // read() reads the first @ExerisDomain type, so this does too.
        cu.findFirst(ClassOrInterfaceDeclaration.class, type -> type.isAnnotationPresent("ExerisDomain"))
                .ifPresent(type -> {
                    if (stepOrderUnresolved(type)) {
                        facets.add("@SagaStep");
                    }
                    facets.addAll(unresolvedPermissionCarriers(type));
                    if (routeAccessUnresolved(type)) {
                        facets.add("@RouteAccess");
                    }
                });
        return Set.copyOf(facets); // immutable, consistent with the empty-path Set.of()
    }

    private String simpleName(String annotationName) {
        int dot = annotationName.lastIndexOf('.');
        return dot < 0 ? annotationName : annotationName.substring(dot + 1);
    }

    private CompilationUnit parseOrThrow(String javaSource) {
        ParseResult<CompilationUnit> result = javaParser.parse(javaSource);
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            throw new IllegalArgumentException("Source is not valid Java: " + result.getProblems());
        }
        return result.getResult().get();
    }

    private DomainMetadata toDomain(CompilationUnit cu, ClassOrInterfaceDeclaration type) {
        // The entity name IS the class simple name. @ExerisDomain declares no `name`
        // attribute, and the processor derives the name from the class element alone
        // (ExerisDomainProcessor: `element.getSimpleName()`), so there is nothing else
        // it could be without this reader disagreeing with the processor about the
        // identity of the same source — the one thing ADR-042 parity cannot tolerate.
        String entityName = type.getNameAsString();
        DomainMetadata.Builder builder = DomainMetadata.builder(entityName, packageName(cu))
                .fields(fields(type))
                .relationships(relationships(type))
                .actions(actions(type))
                .events(events(type));
        type.getAnnotationByName("ExerisDomain").ifPresent(ann -> {
            applyDomainAttributes(ann, builder);
            routeAccess(type).ifPresent(builder::routeAccess);
            SystemFieldsMetadata systemFields = systemFields(type, ann);
            if (systemFields != null) {
                builder.systemFields(systemFields);
            }
        });
        UIMetadata ui = uiMetadata(type);
        if (ui != null) {
            builder.uiMetadata(ui);
        }
        GraphMetadata graph = graphMetadata(type);
        if (graph != null) {
            builder.graphMetadata(graph);
        }
        EventSourcedMetadata eventSourced = eventSourced(type);
        if (eventSourced != null) {
            builder.eventSourced(eventSourced);
        }
        SagaMetadata saga = sagaMetadata(type);
        if (saga != null) {
            builder.sagaMetadata(saga);
        }
        InternalApiMetadata internalApi = internalApi(type);
        if (internalApi != null) {
            builder.internalApi(internalApi);
        }
        NavMenuMetadata navMenu = navMenu(type);
        if (navMenu != null) {
            builder.navMenu(navMenu);
        }
        return builder.build();
    }

    /**
     * Reads the domain-level {@code @ExerisDomain} attributes the processor reads
     * (string + boolean) into the builder, present-only — an absent attribute keeps
     * the builder default, exactly matching {@code ExerisDomainProcessor}'s
     * {@code if (values.containsKey(...)) builder.set(...)} pattern. {@code permissions} is
     * read in declaration order, the route-authorization input of exeris-tooling ADR-105,
     * together with the entity's {@code @RouteAccess}. The other array attributes
     * ({@code tags}/{@code roles}) and the {@code validationMode}/{@code ui} attributes are
     * not read — the processor doesn't read them either, so they constitute no
     * reader-vs-processor divergence and {@link #unmodeledFacets} does not flag them. The {@code *Field} attributes
     * are read by {@link #systemFields}, together with the field markers they override.
     *
     * <p>{@code tableName} is stored as written, exactly as the processor stores it: a blank
     * value means "derive it", and {@code DomainMetadata.effectiveTableName()} is the one place
     * that applies that rule, so neither side derives a name the other does not.
     */
    private void applyDomainAttributes(AnnotationExpr ann, DomainMetadata.Builder builder) {
        stringAttr(ann, "module").ifPresent(builder::module);
        stringAttr(ann, "path").ifPresent(builder::path);
        stringAttr(ann, "aggregate").ifPresent(builder::aggregate);
        stringAttr(ann, "description").ifPresent(builder::description);
        stringAttr(ann, "cacheTtl").ifPresent(builder::cacheTtl);
        stringAttr(ann, "cacheRegion").ifPresent(builder::cacheRegion);
        stringAttr(ann, "searchConfig").ifPresent(builder::searchConfig);
        stringAttr(ann, "tableName").ifPresent(builder::tableName);
        stringArrayAttr(ann, PERMISSIONS).ifPresent(builder::permissions);
        boolAttr(ann, "restApi").ifPresent(builder::restApi);
        boolAttr(ann, "graphqlApi").ifPresent(builder::graphqlApi);
        boolAttr(ann, "realTimeApi").ifPresent(builder::realTimeApi);
        boolAttr(ann, "internalClient").ifPresent(builder::internalClient);
        boolAttr(ann, "tenantScoped").ifPresent(builder::tenantScoped);
        dataScope(ann).ifPresent(builder::dataScope);
        boolAttr(ann, "softDelete").ifPresent(builder::softDelete);
        boolAttr(ann, "audited").ifPresent(builder::audited);
        boolAttr(ann, "versioned").ifPresent(builder::versioned);
        boolAttr(ann, "sensitive").ifPresent(builder::sensitive);
        boolAttr(ann, "cacheable").ifPresent(builder::cacheable);
        boolAttr(ann, "fullTextSearch").ifPresent(builder::fullTextSearch);
    }

    /** Present-only boolean attribute (no default — absent leaves the builder default). */
    private Optional<Boolean> boolAttr(AnnotationExpr ann, String attribute) {
        // exact "true" match (JavaParser renders boolean literals lowercase),
        // consistent with isRequired's style.
        return value(ann, attribute).map(Expression::toString).map("true"::equals);
    }

    /**
     * One system-field role: the {@code annotation.system} marker that declares it on a field,
     * and the {@code @ExerisDomain} attribute that names the same field remotely.
     * {@code @SharedScope} has no override attribute; its key is the component name, which no
     * {@code @ExerisDomain} attribute carries, so its override lookup is always empty.
     */
    private record SystemFieldRole(String annotation, String overrideAttribute) {
    }

    /** The {@code @ExerisDomain} attribute, and {@link SystemFieldsMetadata} component, naming the key. */
    private static final String PRIMARY_KEY_FIELD = "primaryKeyField";

    /**
     * The {@code @PrimaryKey.strategy} values the processor refuses (ADR-104): {@code SEQUENCE} and
     * {@code IDENTITY} need a key that is not a {@code UUID}, and {@code NONE} a create path that
     * never assigns one. A marker declaring one of them is not read.
     */
    private static final Set<String> REFUSED_PRIMARY_KEY_STRATEGIES = Set.of("SEQUENCE", "IDENTITY", "NONE");

    /**
     * The roles {@link #systemFields} reads: the processor's ten ({@code SYSTEM_FIELD_ROLES}), in its
     * order, and {@code @PrimaryKey}, which fills {@code primaryKeyField} under the same rules
     * (ADR-104).
     */
    private static final List<SystemFieldRole> SYSTEM_FIELD_ROLES = List.of(
            new SystemFieldRole("PrimaryKey", PRIMARY_KEY_FIELD),
            new SystemFieldRole("TenantId", "tenantIdField"),
            new SystemFieldRole("Version", "versionField"),
            new SystemFieldRole("SoftDelete", "softDeleteField"),
            new SystemFieldRole("SoftDeleteTimestamp", "softDeleteTimestampField"),
            new SystemFieldRole("SoftDeletedBy", "softDeletedByField"),
            new SystemFieldRole("AuditCreatedAt", "createdAtField"),
            new SystemFieldRole("AuditCreatedBy", "createdByField"),
            new SystemFieldRole("AuditUpdatedAt", "updatedAtField"),
            new SystemFieldRole("AuditUpdatedBy", "updatedByField"),
            new SystemFieldRole("SharedScope", "sharedScopeField"));

    /**
     * {@link SystemFieldsMetadata} from its two sources, mirroring the processor's
     * {@code resolveSystemFields}: an {@code annotation.system} marker on a field, and the
     * matching {@code @ExerisDomain} {@code *Field} override attribute. Each role resolves to the
     * marker's field, else the override, else the {@link SystemFieldsMetadata#defaults()} value.
     *
     * <p>Returns {@code null} when neither source declared anything, so an entity that declares
     * nothing reads with no record, as it is emitted.
     *
     * <p>A role the processor refuses is left to the override here, as the refusal leaves it
     * there: a marker on more than one field, a marker whose field differs from a non-blank
     * override, and a {@code @PrimaryKey} whose {@code strategy} is one the processor refuses.
     * The refusals ADR-104 places on the key itself — a key that is not a {@code UUID}, a missing
     * key field, a renamed key beside a field {@code id} — are not mirrored, for the marker as for
     * the override: the processor emits nothing for such an entity. An explicit
     * {@code primaryKeyField = "id"} declares nothing on its own, since {@code "id"} is the
     * attribute's default. {@code sharedScopeField} is recorded under an explicit
     * {@code dataScope = UNIVERSE} only; on any other tier the processor drops the marker with a
     * warning. Markers are matched by simple name, as every annotation here is, so another
     * library's {@code @Version} on an entity field reads as the system marker.
     */
    private SystemFieldsMetadata systemFields(ClassOrInterfaceDeclaration type, AnnotationExpr domain) {
        Map<String, String> declared = new LinkedHashMap<>();
        for (SystemFieldRole role : SYSTEM_FIELD_ROLES) {
            List<String> carriers = fieldsAnnotated(type, role.annotation());
            if (carriers.size() != 1) {
                continue;
            }
            String annotated = carriers.getFirst();
            if (PRIMARY_KEY_FIELD.equals(role.overrideAttribute()) && refusedPrimaryKeyStrategy(type)) {
                continue;
            }
            Optional<String> override = nonBlankStringAttr(domain, role.overrideAttribute());
            if (override.isPresent() && !override.get().equals(annotated)) {
                continue;
            }
            declared.put(role.overrideAttribute(), annotated);
        }
        if (dataScope(domain).orElse(null) != DataScope.UNIVERSE) {
            declared.remove("sharedScopeField");
        }

        boolean anyOverride = SYSTEM_FIELD_ROLES.stream()
                .filter(role -> !PRIMARY_KEY_FIELD.equals(role.overrideAttribute()))
                .anyMatch(role -> nonBlankStringAttr(domain, role.overrideAttribute()).isPresent())
                || nonBlankStringAttr(domain, PRIMARY_KEY_FIELD).filter(name -> !"id".equals(name)).isPresent();
        if (!anyOverride && declared.isEmpty()) {
            return null;
        }

        SystemFieldsMetadata d = SystemFieldsMetadata.defaults();
        return new SystemFieldsMetadata(
                resolvedSystemField(declared, domain, PRIMARY_KEY_FIELD, d.primaryKeyField()),
                resolvedSystemField(declared, domain, "createdAtField", d.createdAtField()),
                resolvedSystemField(declared, domain, "createdByField", d.createdByField()),
                resolvedSystemField(declared, domain, "updatedAtField", d.updatedAtField()),
                resolvedSystemField(declared, domain, "updatedByField", d.updatedByField()),
                resolvedSystemField(declared, domain, "tenantIdField", d.tenantIdField()),
                resolvedSystemField(declared, domain, "versionField", d.versionField()),
                resolvedSystemField(declared, domain, "softDeleteField", d.softDeleteField()),
                resolvedSystemField(declared, domain, "softDeleteTimestampField", d.softDeleteTimestampField()),
                resolvedSystemField(declared, domain, "softDeletedByField", d.softDeletedByField()),
                declared.get("sharedScopeField"));
    }

    /**
     * Whether the {@code @PrimaryKey} on {@code type}'s fields declares a {@code strategy} the
     * processor refuses. Called with exactly one carrier, so at most one marker is inspected.
     */
    private boolean refusedPrimaryKeyStrategy(ClassOrInterfaceDeclaration type) {
        return type.getFields().stream()
                .flatMap(field -> field.getAnnotations().stream())
                .filter(annotation -> "PrimaryKey".equals(simpleName(annotation.getNameAsString())))
                .anyMatch(marker -> enumAttr(marker, "strategy")
                        .filter(REFUSED_PRIMARY_KEY_STRATEGIES::contains)
                        .isPresent());
    }

    /** One role's field: the marker's, else the override, else {@code fallback}. */
    private String resolvedSystemField(Map<String, String> declared, AnnotationExpr domain,
                                       String attribute, String fallback) {
        String fromMarker = declared.get(attribute);
        return fromMarker != null ? fromMarker : nonBlankStringAttr(domain, attribute).orElse(fallback);
    }

    /** A string attribute that is present and not blank; a blank one means "not overridden". */
    private Optional<String> nonBlankStringAttr(AnnotationExpr annotation, String attribute) {
        return stringAttr(annotation, attribute).filter(value -> !value.isBlank());
    }

    /**
     * The names of {@code type}'s fields carrying {@code annotationSimpleName}, in declaration
     * order — one per variable, since javac gives each variable of a multi-variable declaration
     * its own copy of the annotation.
     */
    private List<String> fieldsAnnotated(ClassOrInterfaceDeclaration type, String annotationSimpleName) {
        List<String> names = new ArrayList<>();
        for (FieldDeclaration field : type.getFields()) {
            boolean carries = field.getAnnotations().stream()
                    .anyMatch(annotation -> annotationSimpleName.equals(simpleName(annotation.getNameAsString())));
            if (carries) {
                field.getVariables().forEach(variable -> names.add(variable.getNameAsString()));
            }
        }
        return names;
    }

    /**
     * Class-level standalone {@code @UI}, field-level {@code @Tab} and field-level
     * {@code @UIGroup} → {@link UIMetadata}, or {@code null} when the type carries none of them.
     * A type-level {@code @UI} is read as {@code ExerisDomainProcessor.extractUIMetadata} reads
     * it, through a directly-present annotation. Tabs are read by {@link #tabs} and groups by
     * {@link #groups}; a type with tabs or groups and no {@code @UI} gets a {@link UIMetadata}
     * whose other components are the builder defaults, which are the values an {@code @UI} that
     * declares nothing produces. A type with no group keeps the builder's empty {@code groups}.
     *
     * <p>Parity note: like the processor, the view flags default to {@code true}
     * when their {@code @UI} attribute is absent (and {@code exportable} to
     * {@code false}) — the "you added @UI, so the views are on" convention,
     * intentionally stronger than the {@code @UI} annotation's own
     * {@code default false}.
     *
     * <p>Deliberately <b>not</b> mapped, all matching the processor so reader- and
     * processor-produced {@code DomainMetadata} stay identical:
     * <ul>
     *   <li>The nested {@code @ExerisDomain(ui = @UI(...))} form — the processor's
     *       {@code findAnnotation} only sees directly-present annotations, so it
     *       reads neither; the {@code @ExerisDomain.ui()} attribute is effectively
     *       unconsumed SDK-wide. Changing this must move processor + reader together.</li>
     *   <li>{@code icon}/{@code color}/{@code label} — present on {@code @UI} but
     *       not read by the processor.</li>
     *   <li>{@code bulkActions}/{@code columns}/{@code defaultLayout}/
     *       {@code fieldOverrides} — no {@code @UI} attribute exists for them; they
     *       stay at {@link UIMetadata} builder defaults.</li>
     * </ul>
     */
    private UIMetadata uiMetadata(ClassOrInterfaceDeclaration type) {
        Optional<AnnotationExpr> ui = type.getAnnotationByName("UI");
        List<UIMetadata.UITabMetadata> tabs = tabs(type);
        List<UIMetadata.UIGroupMetadata> groups = groups(type);
        if (ui.isEmpty() && tabs.isEmpty() && groups.isEmpty()) {
            return null;
        }
        UIMetadata.Builder builder = UIMetadata.builder();
        ui.ifPresent(annotation -> builder
                .listView(boolAttr(annotation, "listView", true))
                .detailView(boolAttr(annotation, "detailView", true))
                .createForm(boolAttr(annotation, "createForm", true))
                .editForm(boolAttr(annotation, "editForm", true))
                .searchable(boolAttr(annotation, "searchable", true))
                .filterable(boolAttr(annotation, "filterable", true))
                .exportable(boolAttr(annotation, "exportable", false)));
        if (!tabs.isEmpty()) {
            builder.tabs(tabs);
        }
        builder.groups(groups);
        return builder.build();
    }

    /**
     * Field-level {@code @UIGroup} → {@link UIMetadata.UIGroupMetadata}, one per distinct
     * {@code name}, in the order each name is first declared — the rule {@link #tabs} applies,
     * for the same reasons. Every field carrying the annotation is a member, a
     * {@code @Relationship} field included, and each variable of a multi-variable declaration
     * is one member.
     *
     * <p>Each attribute takes the value of the first member field, in declaration order, that
     * declares it; a blank string counts as undeclared. An attribute no member declares takes
     * the annotation default, written out because the record's components are primitive:
     * {@code order} {@value UIMetadata.UIGroupMetadata#DEFAULT_ORDER}, {@code collapsible}
     * {@code true}, {@code collapsed} {@code false}; an undeclared string is {@code null}.
     * {@code gridSpan} has no attribute and is
     * {@value UIMetadata.UIGroupMetadata#DEFAULT_GRID_SPAN}. {@code columns}, {@code cssClass}
     * and {@code visibleWhen} have no component and are not read. A {@code @UIGroup} whose
     * {@code name} is not a non-blank string literal is skipped.
     */
    private List<UIMetadata.UIGroupMetadata> groups(ClassOrInterfaceDeclaration type) {
        Map<String, GroupDeclarations> byName = new LinkedHashMap<>();
        for (FieldDeclaration field : type.getFields()) {
            field.getAnnotationByName("UIGroup").ifPresent(group -> nonBlankStringAttr(group, "name")
                    .ifPresent(name -> {
                        GroupDeclarations declarations = byName.computeIfAbsent(name, GroupDeclarations::new);
                        declarations.absorb(group);
                        for (VariableDeclarator variable : field.getVariables()) {
                            declarations.fields.add(variable.getNameAsString());
                        }
                    }));
        }
        return byName.values().stream().map(GroupDeclarations::build).toList();
    }

    /**
     * The {@code @UIGroup} declarations sharing one name, folded attribute by attribute: the
     * first declaration of each attribute is kept, and a later one is ignored.
     */
    private final class GroupDeclarations {
        private final String name;
        private final List<String> fields = new ArrayList<>();
        private String label;
        private String description;
        private Integer order;
        private Boolean collapsible;
        private Boolean collapsed;
        private String icon;

        private GroupDeclarations(String name) {
            this.name = name;
        }

        private void absorb(AnnotationExpr group) {
            label = TabDeclarations.first(label, () -> nonBlankStringAttr(group, LABEL));
            description = TabDeclarations.first(description, () -> nonBlankStringAttr(group, "description"));
            order = TabDeclarations.first(order, () -> signedIntAttr(group, ORDER));
            collapsible = TabDeclarations.first(collapsible, () -> boolAttr(group, "collapsible"));
            collapsed = TabDeclarations.first(collapsed, () -> boolAttr(group, "collapsed"));
            icon = TabDeclarations.first(icon, () -> nonBlankStringAttr(group, "icon"));
        }

        private UIMetadata.UIGroupMetadata build() {
            return new UIMetadata.UIGroupMetadata(name, label, description,
                    order != null ? order : UIMetadata.UIGroupMetadata.DEFAULT_ORDER,
                    !Boolean.FALSE.equals(collapsible), Boolean.TRUE.equals(collapsed), icon,
                    UIMetadata.UIGroupMetadata.DEFAULT_GRID_SPAN, List.copyOf(fields));
        }
    }

    /**
     * Field-level {@code @Tab} → {@link UIMetadata.UITabMetadata}, one per distinct
     * {@code name}, in the order each name is first declared. Every field carrying the
     * annotation is a member, whatever else it carries — a {@code @Relationship} field
     * included — and each variable of a multi-variable declaration is one member.
     *
     * <p>Each attribute takes the value of the first member field, in declaration order, that
     * declares it; a blank string counts as undeclared. So the attributes may be written on any
     * one member, the first being the convention the annotation documents, and a later field
     * that restates one with another value does not change it. An attribute no member declares
     * is left unset and the record's {@code effective*} accessor applies the annotation default.
     * A {@code @Tab} whose {@code name} is not a non-blank string literal is skipped.
     */
    private List<UIMetadata.UITabMetadata> tabs(ClassOrInterfaceDeclaration type) {
        Map<String, TabDeclarations> byName = new LinkedHashMap<>();
        for (FieldDeclaration field : type.getFields()) {
            field.getAnnotationByName("Tab").ifPresent(tab -> nonBlankStringAttr(tab, "name")
                    .ifPresent(name -> {
                        TabDeclarations declarations = byName.computeIfAbsent(name, TabDeclarations::new);
                        declarations.absorb(tab);
                        for (VariableDeclarator variable : field.getVariables()) {
                            declarations.fields.add(variable.getNameAsString());
                        }
                    }));
        }
        return byName.values().stream().map(TabDeclarations::build).toList();
    }

    /**
     * The {@code @Tab} declarations sharing one name, folded attribute by attribute: the first
     * declaration of each attribute is kept, and a later one is ignored.
     */
    private final class TabDeclarations {
        private final String name;
        private final List<String> fields = new ArrayList<>();
        private String label;
        private Integer order;
        private String icon;
        private Boolean lazy;
        private String badgeField;
        private String badgeColor;
        private Boolean disabled;
        private String disabledWhen;
        private String visibleWhen;
        private String cssClass;

        private TabDeclarations(String name) {
            this.name = name;
        }

        private void absorb(AnnotationExpr tab) {
            label = first(label, () -> nonBlankStringAttr(tab, LABEL));
            order = first(order, () -> signedIntAttr(tab, ORDER));
            icon = first(icon, () -> nonBlankStringAttr(tab, "icon"));
            lazy = first(lazy, () -> boolAttr(tab, "lazy"));
            badgeField = first(badgeField, () -> nonBlankStringAttr(tab, "badgeField"));
            badgeColor = first(badgeColor, () -> nonBlankStringAttr(tab, "badgeColor"));
            disabled = first(disabled, () -> boolAttr(tab, "disabled"));
            disabledWhen = first(disabledWhen, () -> nonBlankStringAttr(tab, "disabledWhen"));
            visibleWhen = first(visibleWhen, () -> nonBlankStringAttr(tab, "visibleWhen"));
            cssClass = first(cssClass, () -> nonBlankStringAttr(tab, "cssClass"));
        }

        private static <T> T first(T kept, Supplier<Optional<T>> declared) {
            return kept != null ? kept : declared.get().orElse(null);
        }

        private UIMetadata.UITabMetadata build() {
            return new UIMetadata.UITabMetadata(name, label, order, icon, Boolean.TRUE.equals(lazy),
                    badgeField, badgeColor, Boolean.TRUE.equals(disabled), disabledWhen, visibleWhen,
                    cssClass, fields);
        }
    }

    /**
     * Type-level {@code @NavMenu} → {@link NavMenuMetadata}, or {@code null} when absent. Every
     * attribute is read present-only: an undeclared one is left unset, and the record's
     * {@code effective*} accessors apply the annotation default. A blank string is stored as
     * unset by the record. {@code order} and {@code groupOrder} are read signed, as javac folds
     * them; {@code divider} is read by constant name, and a name the AST enum does not declare is
     * left unset. A {@code @NavMenu} that declares nothing is an entry with every component unset,
     * not an absent one.
     */
    private NavMenuMetadata navMenu(ClassOrInterfaceDeclaration type) {
        Optional<AnnotationExpr> found = type.getAnnotationByName("NavMenu");
        if (found.isEmpty()) {
            return null;
        }
        AnnotationExpr nav = found.get();
        NavMenuMetadata.Builder builder = NavMenuMetadata.builder();
        stringAttr(nav, "group").ifPresent(builder::group);
        stringAttr(nav, "parent").ifPresent(builder::parent);
        stringAttr(nav, LABEL).ifPresent(builder::label);
        stringAttr(nav, "icon").ifPresent(builder::icon);
        signedIntAttr(nav, ORDER).ifPresent(builder::order);
        signedIntAttr(nav, "groupOrder").ifPresent(builder::groupOrder);
        stringAttr(nav, "groupIcon").ifPresent(builder::groupIcon);
        builder.hidden(boolAttr(nav, "hidden", false));
        stringArrayAttr(nav, "roles").ifPresent(builder::roles);
        stringArrayAttr(nav, PERMISSIONS).ifPresent(builder::permissions);
        stringAttr(nav, "badge").ifPresent(builder::badge);
        stringAttr(nav, "badgeColor").ifPresent(builder::badgeColor);
        stringAttr(nav, "tooltip").ifPresent(builder::tooltip);
        builder.openInNewTab(boolAttr(nav, "openInNewTab", false));
        stringAttr(nav, "routePath").ifPresent(builder::routePath);
        stringAttr(nav, "queryParams").ifPresent(builder::queryParams);
        builder.expanded(boolAttr(nav, "expanded", false));
        enumAttr(nav, "divider").flatMap(SourceModelReader::divider).ifPresent(builder::divider);
        return builder.build();
    }

    private static Optional<NavMenuMetadata.Divider> divider(String constant) {
        for (NavMenuMetadata.Divider divider : NavMenuMetadata.Divider.values()) {
            if (divider.name().equals(constant)) {
                return Optional.of(divider);
            }
        }
        return Optional.empty();
    }

    private List<ActionMetadata> actions(ClassOrInterfaceDeclaration type) {
        List<ActionMetadata> actions = new ArrayList<>();
        for (MethodDeclaration method : type.getMethods()) {
            Optional<AnnotationExpr> action = method.getAnnotationByName("Action");
            if (action.isEmpty()) {
                continue;
            }
            // @Action.name() is required; fall back to the method name if absent.
            String name = stringAttr(action.get(), "name")
                    .filter(s -> !s.isBlank())
                    .orElse(method.getNameAsString());
            ActionMetadata.Builder builder = ActionMetadata.builder(name)
                    .methodName(method.getNameAsString())
                    .resultType(typeName(method.getType(), method, type))
                    .async(boolAttr(action.get(), "async", false));
            stringAttr(action.get(), LABEL).ifPresent(builder::displayName);
            stringAttr(action.get(), "description")
                    .filter(s -> !s.isBlank())
                    .ifPresent(builder::description);
            stringAttr(action.get(), "httpMethod").ifPresent(builder::httpMethod);
            // Per-action streaming driver (ADR-043).
            // The reader mirrors the processor, which extracts the streaming
            // flag and the stream event type but deliberately skips
            // realTimeUpdates until that attribute has a generator consumer.
            builder.streaming(boolAttr(action.get(), "streaming", false));
            stringAttr(action.get(), "streamEventType").ifPresent(builder::streamEventType);
            // The action's own declaration only. An action that declares neither keeps a null
            // routeAccess and empty permissions, and inherits the entity's at generation time:
            // the AST carries what each element declares, and nearest-declaration resolution is
            // the generator's (exeris-tooling ADR-105 obligation 12).
            stringArrayAttr(action.get(), PERMISSIONS).ifPresent(builder::permissions);
            routeAccess(method).ifPresent(builder::routeAccess);
            for (Parameter parameter : method.getParameters()) {
                AnnotationExpr actionParam = parameter.getAnnotationByName("ActionParam").orElse(null);
                AnnotationExpr queryParam = parameter.getAnnotationByName("QueryParam").orElse(null);
                if (actionParam != null || queryParam != null) {
                    builder.addParam(actionParam(parameter, actionParam, queryParam));
                }
            }
            actions.add(builder.build());
        }
        return actions;
    }

    /**
     * {@code type} in the form {@code TypeMirror.toString()} gives it, without type-use
     * annotations: {@code void} and primitives as keywords, a class or interface type
     * fully qualified with {@code .} between a nested type and its enclosing one, type
     * arguments kept and joined by {@code ,} with no space, wildcards as {@code ?},
     * {@code ? extends …} and {@code ? super …}, arrays with {@code []} per dimension, and a
     * type variable by its simple name. Names are resolved syntactically from inside
     * {@code method}'s body — see {@link #qualifyInBody}.
     *
     * @param type the type as written
     * @param method the method whose signature carries it
     * @param declaring the type declaring {@code method}
     * @return the rendered type
     */
    private String typeName(Type type, MethodDeclaration method, ClassOrInterfaceDeclaration declaring) {
        if (type.isVoidType()) {
            return "void";
        }
        if (type.isPrimitiveType()) {
            return type.asPrimitiveType().getType().asString();
        }
        if (type.isArrayType()) {
            return typeName(type.asArrayType().getComponentType(), method, declaring) + "[]";
        }
        if (type.isWildcardType()) {
            WildcardType wildcard = type.asWildcardType();
            return wildcard.getExtendedType()
                    .map(bound -> "? extends " + typeName(bound, method, declaring))
                    .or(() -> wildcard.getSuperType().map(bound -> "? super " + typeName(bound, method, declaring)))
                    .orElse("?");
        }
        if (type.isClassOrInterfaceType()) {
            return classTypeName(type.asClassOrInterfaceType(), method, declaring);
        }
        // Union, intersection and var types cannot be a method's return type.
        return type.asString();
    }

    /** A class or interface type per {@link #typeName}: its first segment resolved, the rest kept. */
    private String classTypeName(ClassOrInterfaceType type, MethodDeclaration method,
                                 ClassOrInterfaceDeclaration declaring) {
        List<ClassOrInterfaceType> segments = new ArrayList<>();
        for (ClassOrInterfaceType segment = type; segment != null; segment = segment.getScope().orElse(null)) {
            segments.add(0, segment);
        }
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < segments.size(); i++) {
            ClassOrInterfaceType segment = segments.get(i);
            if (i == 0) {
                name.append(qualifyInBody(segment.getNameAsString(), segments.size() > 1, method, declaring));
            } else {
                name.append('.').append(segment.getNameAsString());
            }
            segment.getTypeArguments().ifPresent(arguments -> name.append(arguments.stream()
                    .map(argument -> typeName(argument, method, declaring))
                    .collect(Collectors.joining(",", "<", ">"))));
        }
        return name.toString();
    }

    /**
     * The first segment of a type named in {@code method}'s signature, resolved syntactically in
     * the order javac's scoping gives it: a type variable of {@code method} or of a type
     * enclosing it, kept as written; then a member type of {@code declaring} or {@code declaring}
     * itself, then a type enclosing it or a member type of one, innermost first; then a
     * top-level type of the compilation unit; then a single-type import; then a public
     * top-level type of {@code java.lang} the running JDK declares. A first segment of a
     * qualified name that matches none of those is a package and is kept as written. A simple
     * name that matches none of them is taken to be in the unit's package, unless the unit has
     * an on-demand import, in which case it is kept as written.
     *
     * <p>A type declared in another file of the same package under a {@code java.lang} name, and
     * a member type inherited from a supertype, are not visible to a single-file reader; both
     * resolve differently than javac resolves them.
     *
     * @param head the segment's simple name
     * @param qualified whether further segments follow it
     * @param method the method whose signature names it
     * @param declaring the type declaring {@code method}
     * @return the resolved prefix
     */
    private String qualifyInBody(String head, boolean qualified, MethodDeclaration method,
                                 ClassOrInterfaceDeclaration declaring) {
        if (!qualified && isTypeVariable(head, method)) {
            return head;
        }
        Optional<CompilationUnit> unit = declaring.findCompilationUnit();
        Optional<String> resolved = memberType(head, declaring)
                .or(() -> typeInScope(head, declaring))
                .or(() -> unit.flatMap(cu -> topLevelType(head, cu)))
                .or(() -> unit.flatMap(cu -> singleTypeImport(head, cu)));
        if (resolved.isPresent()) {
            return resolved.get();
        }
        if (isJavaLangType(head)) {
            return "java.lang." + head;
        }
        if (qualified || unit.isEmpty() || hasOnDemandImport(unit.get())) {
            return head;
        }
        String pkg = unit.get().getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
        return pkg.isEmpty() ? head : pkg + "." + head;
    }

    /** Whether {@code name} is a type parameter of {@code method} or of a type enclosing it. */
    private boolean isTypeVariable(String name, Node method) {
        for (Node node = method; node != null; node = node.getParentNode().orElse(null)) {
            if (node instanceof NodeWithTypeParameters<?> generic
                    && generic.getTypeParameters().stream().anyMatch(p -> p.getNameAsString().equals(name))) {
                return true;
            }
        }
        return false;
    }

    /** A member type of {@code type} named {@code name}, fully qualified. */
    private Optional<String> memberType(String name, TypeDeclaration<?> type) {
        return type.getMembers().stream()
                .filter(BodyDeclaration::isTypeDeclaration)
                .map(BodyDeclaration::asTypeDeclaration)
                .filter(nested -> nested.getNameAsString().equals(name))
                .findFirst()
                .flatMap(TypeDeclaration::getFullyQualifiedName);
    }

    /** Whether the running JDK's {@code java.base} declares a public top-level {@code java.lang.name}. */
    private static boolean isJavaLangType(String name) {
        Class<?> candidate = Class.forName(Object.class.getModule(), "java.lang." + name);
        return candidate != null
                && candidate.getEnclosingClass() == null
                && java.lang.reflect.Modifier.isPublic(candidate.getModifiers());
    }

    /**
     * An action parameter, from {@code @ActionParam}, {@code @QueryParam} or both. Either
     * annotation makes a parameter an action parameter.
     *
     * <p>{@code @QueryParam} binds the parameter from the query string:
     * {@link ParamSource#QUERY}, with its {@code value} as the query key. {@code @ActionParam}
     * owns the parameter's identity and label, and is the nearer declaration for the three
     * attributes both annotations declare — {@code required}, {@code description} and
     * {@code defaultValue}: each is taken from {@code @ActionParam} when it declares it, else
     * from {@code @QueryParam}, else the shared default ({@code true}, none, none). A blank
     * string counts as undeclared.
     *
     * @param actionParam the parameter's {@code @ActionParam}, or {@code null}
     * @param queryParam the parameter's {@code @QueryParam}, or {@code null}
     */
    private ActionParamMetadata actionParam(Parameter parameter, AnnotationExpr actionParam,
                                            AnnotationExpr queryParam) {
        Optional<AnnotationExpr> nearer = Optional.ofNullable(actionParam);
        Optional<AnnotationExpr> farther = Optional.ofNullable(queryParam);
        String name = nearer.flatMap(ann -> nonBlankStringAttr(ann, "name"))
                .orElse(parameter.getNameAsString());
        // required defaults to true on both annotations.
        ActionParamMetadata.Builder builder = ActionParamMetadata.builder(name, parameter.getTypeAsString())
                .required(nearer.flatMap(ann -> boolAttr(ann, REQUIRED))
                        .or(() -> farther.flatMap(ann -> boolAttr(ann, REQUIRED)))
                        .orElse(true));
        nearer.flatMap(ann -> stringAttr(ann, LABEL)).ifPresent(builder::displayName);
        nearer.flatMap(ann -> nonBlankStringAttr(ann, "description"))
                .or(() -> farther.flatMap(ann -> nonBlankStringAttr(ann, "description")))
                .ifPresent(builder::description);
        nearer.flatMap(ann -> nonBlankStringAttr(ann, "defaultValue"))
                .or(() -> farther.flatMap(ann -> nonBlankStringAttr(ann, "defaultValue")))
                .ifPresent(builder::defaultValue);
        if (queryParam != null) {
            builder.bindingSource(ParamSource.QUERY);
            queryKey(queryParam).ifPresent(builder::queryKey);
        }
        return builder.build();
    }

    /**
     * {@code @QueryParam}'s {@code value}, in the single-member form {@code @QueryParam("q")} or
     * the named form {@code @QueryParam(value = "q")}. The record stores a key equal to the
     * parameter name, or a blank one, as absent.
     */
    private Optional<String> queryKey(AnnotationExpr queryParam) {
        if (queryParam.isSingleMemberAnnotationExpr()) {
            Expression member = queryParam.asSingleMemberAnnotationExpr().getMemberValue();
            return member.isStringLiteralExpr()
                    ? Optional.of(member.asStringLiteralExpr().asString())
                    : Optional.empty();
        }
        return stringAttr(queryParam, VALUE);
    }

    /**
     * Domain events declared on the entity, mirroring
     * {@code ExerisDomainProcessor.extractEventsMetadata}. Reads three source
     * shapes, all keyed off the entity's <em>class</em> simple name, exactly as
     * the processor uses {@code element.getSimpleName()}:
     * <ul>
     *   <li>repeated/single {@code @DomainEvent} directly on the type — the natural
     *       source form of the {@code @Repeatable} annotation (javac folds these into
     *       a {@code @DomainEvents} container only at the processor's JSR-269 level);</li>
     *   <li>a hand-written {@code @DomainEvents} container (its {@code value} array
     *       unwrapped);</li>
     *   <li>nested classes annotated {@code @DomainEvent} (the processor's "legacy
     *       support" path) — name = nested class name, topic only.</li>
     * </ul>
     */
    private List<DomainEventMetadata> events(ClassOrInterfaceDeclaration type) {
        List<DomainEventMetadata> events = new ArrayList<>();
        String entitySimpleName = type.getNameAsString();
        // EV1: the entity's @Field-annotated field names in declaration order —
        // the universe payload resolution selects from, computed once. Shared,
        // lock-step with ExerisDomainProcessor.entityFieldNames (ADR-042).
        List<String> entityFieldNames = entityFieldNames(type);
        for (AnnotationExpr annotation : type.getAnnotations()) {
            String simpleName = simpleName(annotation.getNameAsString());
            if ("DomainEvent".equals(simpleName)) {
                events.add(singleEvent(annotation, entitySimpleName, entityFieldNames));
            } else if ("DomainEvents".equals(simpleName)) {
                for (AnnotationExpr inner : containedAnnotations(annotation)) {
                    events.add(singleEvent(inner, entitySimpleName, entityFieldNames));
                }
            }
        }
        for (BodyDeclaration<?> member : type.getMembers()) {
            if (member instanceof ClassOrInterfaceDeclaration nested && !nested.isInterface()) {
                // Legacy nested-class form: name + topic only, mirroring the
                // processor (which reads no payload attributes on this path).
                nested.getAnnotationByName("DomainEvent").ifPresent(annotation ->
                        events.add(DomainEventMetadata.withTopic(
                                nested.getNameAsString(), stringAttr(annotation, TOPIC).orElse(null))));
            }
        }
        return events;
    }

    /**
     * One {@code @DomainEvent} → {@link DomainEventMetadata}, mirroring
     * {@code extractSingleEventMetadata}: an absent/blank {@code name} is derived as
     * {@code entityName + suffix(trigger)} with {@code trigger} defaulting to
     * {@code CREATE}; {@code aggregateType} is always the entity class name.
     *
     * <p>EV1: resolves the payload field subset (ADR-042 lock-step with the
     * processor) — see {@link #resolvePayloadFields} — and reads
     * {@code sensitiveFields} verbatim.
     */
    private DomainEventMetadata singleEvent(AnnotationExpr annotation, String entitySimpleName,
                                            List<String> entityFieldNames) {
        String name = stringAttr(annotation, "name")
                .filter(s -> !s.isBlank())
                .orElseGet(() -> entitySimpleName
                        + triggerToEventSuffix(enumAttr(annotation, TRIGGER).orElse(CREATE)));
        String topic = stringAttr(annotation, TOPIC).orElse(null);
        String description = stringAttr(annotation, "description").orElse(null);
        // EV2: the trigger triple. Note the asymmetry with the name derivation above — that
        // one defaults an ABSENT trigger to CREATE because it only needs a suffix, whereas
        // the component stays unset, since "not declared" and "fires on create" are different
        // claims downstream. @DomainEvent.trigger has no annotation default, so an absent
        // value here means unparseable or a source that does not compile.
        return DomainEventMetadata.builder(name)
                .topic(topic)
                .description(description)
                .aggregateType(entitySimpleName)
                .payloadFields(resolvePayloadFields(annotation, entityFieldNames))
                .sensitiveFields(stringArrayAttr(annotation, "sensitiveFields").orElse(List.of()))
                .trigger(eventTrigger(annotation).orElse(null))
                .actionName(stringAttr(annotation, "action").filter(v -> !v.isBlank()).orElse(null))
                .fieldName(stringAttr(annotation, "field").filter(v -> !v.isBlank()).orElse(null))
                .build();
    }

    /**
     * The entity's {@code @Field}-annotated field names in declaration order. This
     * is the universe EV1 payload resolution selects from; it must agree byte-for-byte
     * with {@code ExerisDomainProcessor.entityFieldNames} (ADR-042 lock-step).
     * {@code @Relationship} fields are excluded (they are modelled separately and the
     * processor's @Field scan does not pick them up as payload fields).
     */
    private List<String> entityFieldNames(ClassOrInterfaceDeclaration type) {
        List<String> names = new ArrayList<>();
        for (FieldDeclaration field : type.getFields()) {
            if (field.getAnnotationByName("Field").isEmpty()) {
                continue;
            }
            for (VariableDeclarator var : field.getVariables()) {
                names.add(var.getNameAsString());
            }
        }
        return names;
    }

    /**
     * EV1 payload-field resolution, the deterministic mirror of the processor's:
     * ({@code @DomainEvent.includeFields} if non-empty, else ALL of the entity's
     * {@code @Field} names) minus {@code @DomainEvent.excludeFields}, preserving
     * entity-declaration order. {@code includeComputed}/{@code includePreviousValues}
     * are intentionally OUT of EV1 — there is no computed-field source in the
     * persisted field list yet.
     */
    private List<String> resolvePayloadFields(AnnotationExpr annotation, List<String> entityFieldNames) {
        List<String> include = stringArrayAttr(annotation, "includeFields").orElse(List.of());
        List<String> base = include.isEmpty() ? entityFieldNames : include;
        Set<String> exclude = Set.copyOf(stringArrayAttr(annotation, "excludeFields").orElse(List.of()));
        List<String> resolved = new ArrayList<>(base.size());
        for (String fieldName : base) {
            if (!exclude.contains(fieldName)) {
                resolved.add(fieldName);
            }
        }
        return resolved;
    }

    /**
     * The inner annotations of a hand-written container annotation — either
     * {@code @Container({...})} (single-member) or {@code @Container(value = {...})}
     * (normal); a lone non-array element is also tolerated. Used for
     * {@code @DomainEvents}, {@code @GraphEdges} and the capability
     * {@code @Provides.List} / {@code @Requires.List} containers.
     */
    private List<AnnotationExpr> containedAnnotations(AnnotationExpr container) {
        Optional<Expression> value = container.isSingleMemberAnnotationExpr()
                ? Optional.of(container.asSingleMemberAnnotationExpr().getMemberValue())
                : value(container, VALUE);
        List<AnnotationExpr> inner = new ArrayList<>();
        value.ifPresent(expression -> {
            if (expression.isArrayInitializerExpr()) {
                for (Expression element : expression.asArrayInitializerExpr().getValues()) {
                    if (element.isAnnotationExpr()) {
                        inner.add(element.asAnnotationExpr());
                    }
                }
            } else if (expression.isAnnotationExpr()) {
                inner.add(expression.asAnnotationExpr());
            }
        });
        return inner;
    }

    /**
     * Entity-name suffix for an unnamed event, by {@code @DomainEvent.Trigger}
     * constant name — a verbatim mirror of {@code ExerisDomainProcessor}'s
     * {@code triggerToEventSuffix}. Exact-string matching: an unmapped or future
     * trigger falls through to the generic {@code "Event"} suffix rather than
     * silently matching a prefix.
     */
    private String triggerToEventSuffix(String trigger) {
        // Unreachable from singleEvent (which passes enumAttr(...).orElse(CREATE)),
        // but kept to mirror the processor verbatim and defend any future direct caller.
        if (trigger == null) {
            return "Event";
        }
        return switch (trigger) {
            case CREATE -> "CreatedEvent";
            case "UPDATE" -> "UpdatedEvent";
            case "DELETE" -> "DeletedEvent";
            case "FIELD_CHANGED" -> "ChangedEvent";
            case "ACTION" -> "ActionEvent";
            default -> "Event";
        };
    }

    /**
     * The {@code @Provides} declarations of a {@code @CapabilityModule} class →
     * {@link ProvidesMetadata}. {@code service} is the class literal's type as
     * written (a declaration whose {@code service} is missing or not a class
     * literal is skipped — invalid source the compiler rejects anyway); a blank
     * {@code version} maps to {@code null} (unversioned).
     */
    private List<ProvidesMetadata> provides(ClassOrInterfaceDeclaration type) {
        List<ProvidesMetadata> provides = new ArrayList<>();
        for (AnnotationExpr annotation : repeatedDeclarations(type, "Provides")) {
            classAttr(annotation, "service").ifPresent(service ->
                    provides.add(new ProvidesMetadata(service,
                            stringAttr(annotation, "version")
                                    .filter(version -> !version.isBlank())
                                    .orElse(null))));
        }
        return provides;
    }

    /**
     * The {@code @Requires} declarations of a {@code @CapabilityModule} class →
     * {@link RequiresMetadata}. Same written-form {@code service} contract as
     * {@link #provides}; a blank {@code versionRange} maps to {@code null} (any
     * version), {@code optional} defaults {@code false}.
     */
    private List<RequiresMetadata> requires(ClassOrInterfaceDeclaration type) {
        List<RequiresMetadata> requires = new ArrayList<>();
        for (AnnotationExpr annotation : repeatedDeclarations(type, "Requires")) {
            classAttr(annotation, "service").ifPresent(service ->
                    requires.add(new RequiresMetadata(service,
                            stringAttr(annotation, "versionRange")
                                    .filter(range -> !range.isBlank())
                                    .orElse(null),
                            boolAttr(annotation, "optional", false))));
        }
        return requires;
    }

    /**
     * All declarations of a {@code @Repeatable} annotation on {@code type}, in
     * declaration order: direct or compiler-implicit repeated occurrences matched
     * by simple name, plus the hand-written {@code @<Simple>.List({...})} container
     * (matched by its {@code <Simple>.List} written suffix — matching the bare
     * {@code List} simple name would be ambiguous).
     */
    private List<AnnotationExpr> repeatedDeclarations(ClassOrInterfaceDeclaration type, String annotationSimpleName) {
        String containerName = annotationSimpleName + ".List";
        List<AnnotationExpr> declarations = new ArrayList<>();
        for (AnnotationExpr annotation : type.getAnnotations()) {
            String name = annotation.getNameAsString();
            if (annotationSimpleName.equals(simpleName(name))) {
                declarations.add(annotation);
            } else if (name.equals(containerName) || name.endsWith("." + containerName)) {
                declarations.addAll(containedAnnotations(annotation));
            }
        }
        return declarations;
    }

    /**
     * A {@code Class<?>} attribute's type <em>as written in source</em> (simple or
     * qualified — no symbol solving, per the reader's documented limitation and
     * ADR-038's written-form contract), or empty when absent or not a class literal.
     */
    private Optional<String> classAttr(AnnotationExpr annotation, String attribute) {
        return value(annotation, attribute)
                .filter(Expression::isClassExpr)
                .map(value -> value.asClassExpr().getTypeAsString());
    }

    /**
     * A {@code Class<?>} attribute as the fully-qualified name javac reports for it, or empty
     * when absent, not a class literal, or {@code void.class}.
     *
     * <p>Resolution is syntactic, in the order javac's scoping gives an annotation on
     * {@code declaring}: {@code declaring} itself, then a type enclosing it or a member type of
     * one, innermost first, then a top-level type of the compilation unit, then a single-type
     * import. {@code declaring}'s own member types are not in scope there, since the
     * annotation is outside its body. A simple name none of
     * those match is taken to be in the unit's package, unless the unit has an on-demand
     * import, in which case the type could come from either and the name is kept as written.
     * A qualified name is resolved through its first segment the same way, and kept as written
     * when that segment matches nothing, since it is then a package. A nested type's name uses
     * {@code .} throughout, as {@code TypeMirror.toString()} does.
     *
     * @param annotation the annotation carrying the attribute
     * @param attribute the attribute's name
     * @param declaring the type the annotation is declared on
     * @return the resolved name, or the written one when it cannot be resolved without
     *         symbol solving
     */
    private Optional<String> qualifiedClassAttr(AnnotationExpr annotation, String attribute,
                                                ClassOrInterfaceDeclaration declaring) {
        return classAttr(annotation, attribute)
                .filter(written -> !"void".equals(written))
                .map(written -> qualify(written, declaring));
    }

    /** {@code written} resolved per {@link #qualifiedClassAttr}, or itself when unresolvable. */
    private String qualify(String written, ClassOrInterfaceDeclaration declaring) {
        int dot = written.indexOf('.');
        String head = dot < 0 ? written : written.substring(0, dot);
        String tail = dot < 0 ? "" : written.substring(dot);
        Optional<CompilationUnit> unit = declaring.findCompilationUnit();
        Optional<String> resolved = typeInScope(head, declaring)
                .or(() -> unit.flatMap(cu -> topLevelType(head, cu)))
                .or(() -> unit.flatMap(cu -> singleTypeImport(head, cu)));
        if (resolved.isPresent()) {
            return resolved.get() + tail;
        }
        if (dot >= 0 || unit.isEmpty() || hasOnDemandImport(unit.get())) {
            return written;
        }
        String pkg = unit.get().getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
        return pkg.isEmpty() ? written : pkg + "." + written;
    }

    /** {@code name} as {@code from}, a type enclosing it, or a member type of an enclosing one. */
    private Optional<String> typeInScope(String name, TypeDeclaration<?> from) {
        if (from.getNameAsString().equals(name)) {
            return from.getFullyQualifiedName();
        }
        Node node = from.getParentNode().orElse(null);
        while (node instanceof TypeDeclaration<?> type) {
            if (type.getNameAsString().equals(name)) {
                return type.getFullyQualifiedName();
            }
            Optional<String> member = memberType(name, type);
            if (member.isPresent()) {
                return member;
            }
            node = type.getParentNode().orElse(null);
        }
        return Optional.empty();
    }

    /** A top-level type of {@code cu} named {@code name}, fully qualified. */
    private Optional<String> topLevelType(String name, CompilationUnit cu) {
        return cu.getTypes().stream()
                .filter(type -> type.getNameAsString().equals(name))
                .findFirst()
                .flatMap(TypeDeclaration::getFullyQualifiedName);
    }

    /** The single-type import of {@code cu} whose simple name is {@code name}. */
    private Optional<String> singleTypeImport(String name, CompilationUnit cu) {
        return cu.getImports().stream()
                .filter(imp -> !imp.isStatic() && !imp.isAsterisk())
                .filter(imp -> imp.getName().getIdentifier().equals(name))
                .map(ImportDeclaration::getNameAsString)
                .findFirst();
    }

    /** Whether {@code cu} has a non-static on-demand ({@code .*}) import. */
    private boolean hasOnDemandImport(CompilationUnit cu) {
        return cu.getImports().stream().anyMatch(imp -> !imp.isStatic() && imp.isAsterisk());
    }

    /**
     * The fully-qualified name of the {@code @CapabilityLifecycle}-annotated class
     * in this compilation unit, or {@code null} when none — the same-unit
     * limitation documented on {@link #readCapabilityModule}. The declaring class
     * is FQN-resolvable without symbol solving (package declaration + type-nesting
     * path), unlike a referenced {@code service} type. {@code @CapabilityLifecycle}
     * targets {@code TYPE} so an interface could carry it, but a lifecycle owner is
     * a class — an annotated interface is skipped (not treated as the owner).
     */
    private String lifecycleOwner(CompilationUnit cu) {
        return cu.findFirst(ClassOrInterfaceDeclaration.class,
                        type -> !type.isInterface() && type.isAnnotationPresent("CapabilityLifecycle"))
                .flatMap(ClassOrInterfaceDeclaration::getFullyQualifiedName)
                .orElse(null);
    }

    private boolean boolAttr(AnnotationExpr annotation, String attribute, boolean defaultValue) {
        return value(annotation, attribute)
                .map(Expression::toString)
                .map("true"::equals)
                .orElse(defaultValue);
    }

    /**
     * Class-level {@code @Graph} → {@link GraphMetadata}, or {@code null} when
     * absent. Mirrors {@code extractGraphMetadata}: the label is
     * {@code @Graph(nodeClass)} falling back to the class simple name, the edges are
     * the fields' {@code @GraphEdge}s ({@link #graphEdges}), and — as in the processor —
     * queries {@code null}, which reads as "not carried" and keeps them off the wire.
     * {@code @GraphQuery} is read by neither side. The properties are the fields'
     * {@code @GraphProperty}s ({@link #graphProperties}), an empty list when none is declared; a
     * {@code @GraphProperty} on a type without {@code @Graph} is not read, as a {@code @GraphEdge}
     * there is not.
     */
    private GraphMetadata graphMetadata(ClassOrInterfaceDeclaration type) {
        Optional<AnnotationExpr> graph = type.getAnnotationByName("Graph");
        if (graph.isEmpty()) {
            return null;
        }
        String label = stringAttr(graph.get(), "nodeClass").orElse(type.getNameAsString());
        return new GraphMetadata(label, graphProperties(type), graphEdges(type), null);
    }

    /**
     * The fields' {@code @GraphProperty}s → {@link GraphPropertyMetadata}, one per annotated
     * field variable, in field declaration order. {@code name} is a non-blank
     * {@code @GraphProperty.name}, else the field name, and {@code field} is the field name, which
     * the record drops when it equals {@code name}. {@code type} is the {@code graphType}
     * constant name, and {@code null} for {@code AUTO} or when undeclared: the store type then
     * follows the field's Java type, which {@code FieldMetadata.type} carries, so no graph type
     * is derived here. {@code indexed} is the declared value, else {@code false}. The other
     * attributes have no component and are not read.
     */
    private List<GraphPropertyMetadata> graphProperties(ClassOrInterfaceDeclaration type) {
        List<GraphPropertyMetadata> properties = new ArrayList<>();
        for (FieldDeclaration field : type.getFields()) {
            field.getAnnotationByName("GraphProperty").ifPresent(property -> {
                String graphType = enumAttr(property, "graphType")
                        .filter(constant -> !"AUTO".equals(constant))
                        .orElse(null);
                boolean indexed = boolAttr(property, "indexed").orElse(false);
                Optional<String> declaredName = nonBlankStringAttr(property, "name");
                for (VariableDeclarator variable : field.getVariables()) {
                    String fieldName = variable.getNameAsString();
                    properties.add(new GraphPropertyMetadata(
                            declaredName.orElse(fieldName), graphType, indexed, fieldName));
                }
            });
        }
        return List.copyOf(properties);
    }

    /**
     * The fields' {@code @GraphEdge}s → {@link GraphEdgeMetadata}, in field declaration order,
     * mirroring the processor's {@code graphEdges}. A field carries at most one edge, counted
     * across the direct annotation and a {@code @GraphEdges} container together, whether javac
     * wrote the container or the author did. A field declaring more than one is dropped, as the
     * processor refuses it. {@code GraphEdgeMetadata} can carry such a field, with one identity
     * per edge and a shared {@code field}; this reader reads it when the processor does, so the
     * two keep writing the same edges for the same source (ADR-042).
     */
    private List<GraphEdgeMetadata> graphEdges(ClassOrInterfaceDeclaration type) {
        List<GraphEdgeMetadata> edges = new ArrayList<>();
        for (FieldDeclaration field : type.getFields()) {
            List<AnnotationExpr> declared = new ArrayList<>();
            for (AnnotationExpr annotation : field.getAnnotations()) {
                String name = simpleName(annotation.getNameAsString());
                if ("GraphEdge".equals(name)) {
                    declared.add(annotation);
                } else if ("GraphEdges".equals(name)) {
                    declared.addAll(containedAnnotations(annotation));
                }
            }
            if (declared.size() != 1) {
                continue;
            }
            for (VariableDeclarator variable : field.getVariables()) {
                edges.add(graphEdge(declared.getFirst(), variable.getNameAsString()));
            }
        }
        return List.copyOf(edges);
    }

    /**
     * One {@code @GraphEdge} → {@link GraphEdgeMetadata}, mirroring the processor's
     * {@code graphEdge}: the identity is the field name, the relation type is {@code type}, and
     * the target label is the first of a non-blank {@code targetLabel}, the simple name of
     * {@code target} (not {@code void.class}), and a non-blank {@code targetName}.
     *
     * <p>The field is the identity, so {@code field} stays {@code null}. The other carried
     * attributes are read present-only: an attribute the source does not write is
     * {@code null}, so the annotation default applies, and a declared {@code false} or
     * {@code 0.0} is carried. {@code direction} maps onto the AST's
     * {@link GraphEdgeMetadata.Direction} by constant name, an unrecognised name reading as
     * absent. {@code staticWeight} is read from a numeric literal, signed; a value written as a
     * constant reference is not resolved and reads as absent.
     */
    private GraphEdgeMetadata graphEdge(AnnotationExpr annotation, String fieldName) {
        String label = nonBlankStringAttr(annotation, "targetLabel")
                .or(() -> classAttr(annotation, "target")
                        .filter(target -> !"void".equals(target))
                        .map(this::simpleName))
                .or(() -> nonBlankStringAttr(annotation, "targetName"))
                .orElse(null);
        return new GraphEdgeMetadata(
                fieldName,
                label,
                nonBlankStringAttr(annotation, "type").orElse(null),
                null,
                edgeDirection(annotation).orElse(null),
                boolAttr(annotation, "bidirectional").orElse(null),
                nonBlankStringAttr(annotation, "inverseType").orElse(null),
                nonBlankStringAttr(annotation, "description").orElse(null),
                boolAttr(annotation, "weighted").orElse(null),
                nonBlankStringAttr(annotation, "weightField").orElse(null),
                doubleAttr(annotation, "staticWeight").orElse(null));
    }

    /**
     * {@code @GraphEdge.direction} → the AST-owned {@link GraphEdgeMetadata.Direction}, bridged
     * by constant-name identity like {@link #dataScope}. An unrecognised constant reads as absent.
     */
    private Optional<GraphEdgeMetadata.Direction> edgeDirection(AnnotationExpr annotation) {
        return enumAttr(annotation, "direction")
                .flatMap(name -> {
                    try {
                        return Optional.of(GraphEdgeMetadata.Direction.valueOf(name));
                    } catch (IllegalArgumentException unknownConstant) {
                        return Optional.empty();
                    }
                });
    }

    /**
     * Present-only {@code double} attribute from a numeric literal ({@code 2}, {@code 0.5},
     * {@code 1.5d}), unwrapping a leading unary minus. Any other expression reads as absent.
     */
    private Optional<Double> doubleAttr(AnnotationExpr annotation, String attribute) {
        return value(annotation, attribute).flatMap(this::asDouble);
    }

    private Optional<Double> asDouble(Expression expression) {
        if (expression.isUnaryExpr()) {
            UnaryExpr unary = expression.asUnaryExpr();
            if (unary.getOperator() == UnaryExpr.Operator.MINUS) {
                return asDouble(unary.getExpression()).map(value -> -value);
            }
            return Optional.empty();
        }
        if (expression.isDoubleLiteralExpr()) {
            return Optional.of(expression.asDoubleLiteralExpr().asDouble());
        }
        return asLong(expression).map(Long::doubleValue);
    }

    /**
     * Class-level {@code @EventSourced} → {@link EventSourcedMetadata}, or
     * {@code null} when absent. Mirrors {@code extractEventSourcedMetadata}'s
     * deliberate attribute translation: {@code streamPrefix} (empty → class simple
     * name) becomes {@code aggregateType}, and {@code snapshotThreshold} (default
     * 50) becomes {@code snapshotEvery}; all other event-sourcing fields keep
     * {@link EventSourcedMetadata.Builder} defaults.
     */
    private EventSourcedMetadata eventSourced(ClassOrInterfaceDeclaration type) {
        Optional<AnnotationExpr> es = type.getAnnotationByName("EventSourced");
        if (es.isEmpty()) {
            return null;
        }
        String streamPrefix = stringAttr(es.get(), "streamPrefix").orElse("");
        String aggregateType = streamPrefix.isEmpty() ? type.getNameAsString() : streamPrefix;
        // Fallback is the @EventSourced.snapshotThreshold annotation default (50),
        // which the processor preserves — deliberately NOT the builder's own
        // snapshotEvery default (100), so absent == the annotation's documented value.
        return EventSourcedMetadata.builder(aggregateType)
                .snapshotEvery(intAttr(es.get(), "snapshotThreshold").orElse(50))
                .build();
    }

    /**
     * Class-level {@code @Saga} → {@link SagaMetadata}, or {@code null} when absent.
     * Mirrors {@code extractSagaMetadata}: {@code name} falls back to the class
     * simple name; {@code description}/{@code timeout}/{@code maxRetries}/{@code version}
     * are present-only; steps come from {@code @SagaStep} methods. {@code trigger} is
     * present-only too: a saga that does not write {@code trigger = …} reads {@code null},
     * not the annotation default {@code MANUAL}, so {@code hasTrigger()} says whether the
     * source declared one; see {@link #sagaTrigger}.
     *
     * <p>{@code version} is the other half of the {@code (name, version)} key kernel
     * ADR-064 addresses a saga plan by, and the processor reads it, so skipping it here
     * would read {@code version = 3} back as {@code 1} against a processor baseline of
     * {@code 3} — an ADR-042 parity break with no diagnostic on either side. Absent, it
     * keeps the builder default {@code 1}, which is also the annotation default, exactly
     * as the processor does. It is read <em>signed</em>, unlike the count-style
     * attributes: {@code 0} or a negative literal is carried as written, because javac
     * folds it into the processor's value just the same, and the kernel refuses a version
     * below {@code 1} at {@code FlowDefinitionBuilder.version(int)} — a refusal that
     * repairing the value to {@code 1} here would hide. One difference remains and is
     * structural: a constant reference or expression ({@code version = Versions.CURRENT})
     * is resolved by javac on the processor path and is not a literal to this reader, so it
     * reads as absent (see the class-level limitations).
     *
     * <p>The compensation section is read here ahead of the processor, which does not
     * extract it yet: {@code compensationTimeout} and the six boxed components are
     * present-only, so an absent attribute leaves {@code compensationTimeout} at its builder
     * default and the six at {@code null}, and a declared {@code 0} or {@code false} is
     * carried. {@code compensationMaxRetries} is read signed, as javac would fold it, so a
     * declared value is carried rather than repaired. A blank {@code compensationDlq} and
     * {@code void.class} mean "none" and read as {@code null}. The handler is carried as the
     * fully-qualified name javac would report, resolved syntactically — see
     * {@link #qualifiedClassAttr}. {@code compensationStrategy} and {@code compensationOrder}
     * are not read: their annotation enums declare constants the AST enums do not, so a
     * read by name would turn an uncarriable constant into the builder default.
     */
    private SagaMetadata sagaMetadata(ClassOrInterfaceDeclaration type) {
        Optional<AnnotationExpr> saga = type.getAnnotationByName("Saga");
        if (saga.isEmpty()) {
            return null;
        }
        String name = stringAttr(saga.get(), "name").orElse(type.getNameAsString());
        SagaMetadata.Builder builder = SagaMetadata.builder(name);
        stringAttr(saga.get(), "description").ifPresent(builder::description);
        stringAttr(saga.get(), "timeout").ifPresent(builder::timeout);
        intAttr(saga.get(), "maxRetries").ifPresent(builder::maxRetries);
        signedIntAttr(saga.get(), "version").ifPresent(builder::version);
        stringAttr(saga.get(), "compensationTimeout").ifPresent(builder::compensationTimeout);
        signedIntAttr(saga.get(), "compensationMaxRetries").ifPresent(builder::compensationMaxRetries);
        stringAttr(saga.get(), "compensationRetryDelay").ifPresent(builder::compensationRetryDelay);
        boolAttr(saga.get(), "continueCompensationOnFailure").ifPresent(builder::continueCompensationOnFailure);
        nonBlankStringAttr(saga.get(), "compensationDlq").ifPresent(builder::compensationDlq);
        qualifiedClassAttr(saga.get(), "compensationFailureHandler", type)
                .ifPresent(builder::compensationFailureHandler);
        boolAttr(saga.get(), "manualInterventionOnCompensationFailure")
                .ifPresent(builder::manualInterventionOnCompensationFailure);
        value(saga.get(), TRIGGER).filter(Expression::isAnnotationExpr)
                .map(expression -> sagaTrigger(expression.asAnnotationExpr(), type))
                .ifPresent(builder::trigger);
        builder.steps(sagaSteps(type));
        return builder.build();
    }

    /**
     * {@code @Saga.trigger} → {@link SagaMetadata.SagaTrigger}, mapped as the processor maps it.
     * {@code type} is read by constant name, and the constants deprecated for removal read as
     * the ones they mean: {@code SCHEDULE} as {@code SCHEDULED}, {@code HTTP} as {@code API},
     * {@code COMMAND} as {@code MANUAL}. A {@code @SagaTrigger} that omits {@code type} is
     * {@code MANUAL}, the annotation default; a constant the AST does not know reads as
     * {@code null}, not as a default that would claim a kind the source did not declare.
     * {@code schedule} is {@code cronExpression}, {@code topic} is {@code topic}, and
     * {@code source} is the class when one is named (fully qualified, see
     * {@link #qualifiedClassAttr}), else {@code sourceName}. A blank string and {@code void.class}
     * mean "none" and read as {@code null}. {@code condition} and the remaining attributes
     * ({@code sourcePattern}, {@code consumerGroup}, {@code filter}, {@code startPosition},
     * {@code httpPath}, {@code httpMethod}) are not carried.
     */
    private SagaMetadata.SagaTrigger sagaTrigger(AnnotationExpr trigger, ClassOrInterfaceDeclaration type) {
        Optional<String> declared = enumAttr(trigger, "type");
        SagaMetadata.TriggerType kind = declared.isPresent()
                ? triggerType(declared.get())
                : SagaMetadata.TriggerType.MANUAL;
        String source = qualifiedClassAttr(trigger, "source", type)
                .or(() -> nonBlankStringAttr(trigger, "sourceName"))
                .orElse(null);
        return new SagaMetadata.SagaTrigger(kind, source,
                nonBlankStringAttr(trigger, TOPIC).orElse(null),
                nonBlankStringAttr(trigger, "schedule").orElse(null), null);
    }

    /** The AST kind a {@code @SagaTrigger.TriggerType} constant name maps to, or {@code null}. */
    private static SagaMetadata.TriggerType triggerType(String constant) {
        String name = switch (constant) {
            case "SCHEDULE" -> "SCHEDULED";
            case "HTTP" -> "API";
            case "COMMAND" -> "MANUAL";
            default -> constant;
        };
        try {
            return SagaMetadata.TriggerType.valueOf(name);
        } catch (IllegalArgumentException _) {
            return null;
        }
    }

    /**
     * {@code @SagaStep} methods → {@link SagaStepMetadata}, sorted by {@code order},
     * mirroring {@code extractSagaSteps}: {@code name} falls back to the method
     * name, and {@code description}/{@code service}/{@code command}/{@code compensation}/
     * {@code timeout}/{@code parallel} are present-only.
     *
     * <p>{@code order} is read through {@link #stepOrder}, which resolves a constant declared
     * in this source file as well as a literal: the order is the attribute that decides the
     * sequence, and a value read wrongly here reorders the saga against the processor's
     * baseline. An order this reader cannot resolve is carried as {@code 1} and reported by
     * {@link #unmodeledFacets} as {@code @SagaStep}, so a reattach caller refuses the read
     * rather than trusting the position. A duplicate, a gap or a sequence not starting at
     * {@code 1} is carried as written, in stable-sort order: that contract is the processor's to
     * check, and this reader has no diagnostic channel to report it through.
     */
    private List<SagaStepMetadata> sagaSteps(ClassOrInterfaceDeclaration type) {
        List<SagaStepMetadata> steps = new ArrayList<>();
        for (MethodDeclaration method : type.getMethods()) {
            Optional<AnnotationExpr> step = method.getAnnotationByName("SagaStep");
            if (step.isEmpty()) {
                continue;
            }
            String name = stringAttr(step.get(), "name").orElse(method.getNameAsString());
            int order = stepOrder(method, step.get()).orElse(1);
            SagaStepMetadata.Builder builder = SagaStepMetadata.builder(name, order);
            stringAttr(step.get(), "description").ifPresent(builder::description);
            stringAttr(step.get(), "service").ifPresent(builder::service);
            stringAttr(step.get(), "command").ifPresent(builder::command);
            stringAttr(step.get(), "compensation").ifPresent(builder::compensation);
            stringAttr(step.get(), "timeout").ifPresent(builder::timeout);
            boolAttr(step.get(), "parallel").ifPresent(builder::parallel);
            steps.add(builder.build());
        }
        steps.sort(Comparator.comparingInt(SagaStepMetadata::order));
        return steps;
    }

    /**
     * The {@code @SagaStep.order} of {@code method}, or empty when the attribute is absent or
     * its value cannot be resolved without a compiler; {@link #stepOrderUnresolved} tells the
     * two apart.
     */
    private Optional<Integer> stepOrder(MethodDeclaration method, AnnotationExpr step) {
        return value(step, ORDER).flatMap(expression -> intConstant(expression, method));
    }

    /**
     * Whether a {@code @SagaStep} on a method of {@code type} declares an {@code order} this
     * reader cannot resolve — the case where {@link #sagaSteps} carries a position the
     * processor, which receives the value folded by javac, may not.
     */
    private boolean stepOrderUnresolved(ClassOrInterfaceDeclaration type) {
        if (type.getAnnotationByName("Saga").isEmpty()) {
            return false;
        }
        for (MethodDeclaration method : type.getMethods()) {
            Optional<AnnotationExpr> step = method.getAnnotationByName("SagaStep");
            if (step.isPresent() && value(step.get(), ORDER).isPresent()
                    && stepOrder(method, step.get()).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The {@code int} value of a constant expression this reader can fold without symbol
     * solving: an integer literal, optionally parenthesised or negated, or a reference to a
     * {@code final} field initialised with one. A simple name ({@code RESERVE}) is looked up in
     * the type enclosing {@code context} and then its enclosing types. javac searches a type's
     * inherited members before its enclosing scope, so the lookup stops, unresolved, at the first
     * type that does not declare the name but extends or implements another type: the name may
     * be inherited, and a same-named field of an enclosing type would then be a different
     * constant. A qualified name ({@code Steps.RESERVE}) resolves only when exactly one type of
     * that simple name is declared in the compilation unit and declares the field itself. An
     * inherited, statically imported or other-file constant, and any arithmetic, is not
     * resolved. Narrowed as javac narrows a folded {@code int}.
     */
    private Optional<Integer> intConstant(Expression expression, Node context) {
        return longConstant(expression, context, 0).map(Long::intValue);
    }

    private Optional<Long> longConstant(Expression expression, Node context, int depth) {
        if (depth > MAX_CONSTANT_DEPTH) {
            return Optional.empty();
        }
        if (expression.isEnclosedExpr()) {
            return longConstant(expression.asEnclosedExpr().getInner(), context, depth + 1);
        }
        if (expression.isUnaryExpr()) {
            UnaryExpr unary = expression.asUnaryExpr();
            if (unary.getOperator() == UnaryExpr.Operator.MINUS) {
                return longConstant(unary.getExpression(), context, depth + 1).map(value -> -value);
            }
            return Optional.empty();
        }
        if (expression.isIntegerLiteralExpr()) {
            return Optional.of(expression.asIntegerLiteralExpr().asNumber().longValue());
        }
        Optional<VariableDeclarator> constant = Optional.empty();
        if (expression.isNameExpr()) {
            constant = lexicalConstant(expression.asNameExpr().getNameAsString(), context);
        } else if (expression.isFieldAccessExpr() && expression.asFieldAccessExpr().getScope().isNameExpr()) {
            FieldAccessExpr access = expression.asFieldAccessExpr();
            constant = qualifiedConstant(access.getScope().asNameExpr().getNameAsString(),
                    access.getNameAsString(), context);
        }
        return constant.flatMap(variable -> variable.getInitializer()
                .flatMap(initializer -> longConstant(initializer, variable, depth + 1)));
    }

    private Optional<VariableDeclarator> lexicalConstant(String name, Node context) {
        Optional<Node> scope = context.getParentNode();
        while (scope.isPresent()) {
            if (scope.get() instanceof TypeDeclaration<?> declaring) {
                Optional<VariableDeclarator> found = constantIn(declaring, name);
                if (found.isPresent() || hasSupertypes(declaring)) {
                    return found;
                }
            }
            scope = scope.get().getParentNode();
        }
        return Optional.empty();
    }

    /**
     * Whether {@code type} names a supertype in an {@code extends} or {@code implements} clause,
     * so its members may include an inherited field this reader cannot see.
     */
    private static boolean hasSupertypes(TypeDeclaration<?> type) {
        boolean extendsAny = type instanceof NodeWithExtends<?> extending
                && !extending.getExtendedTypes().isEmpty();
        boolean implementsAny = type instanceof NodeWithImplements<?> implementing
                && !implementing.getImplementedTypes().isEmpty();
        return extendsAny || implementsAny;
    }

    private Optional<VariableDeclarator> qualifiedConstant(String typeName, String name, Node context) {
        Optional<CompilationUnit> unit = context.findCompilationUnit();
        if (unit.isEmpty()) {
            return Optional.empty();
        }
        List<TypeDeclaration<?>> candidates = new ArrayList<>();
        for (TypeDeclaration<?> declared : unit.get().findAll(TypeDeclaration.class)) {
            if (declared.getNameAsString().equals(typeName)) {
                candidates.add(declared);
            }
        }
        return candidates.size() == 1 ? constantIn(candidates.get(0), name) : Optional.empty();
    }

    /**
     * A constant field of {@code type}: {@code final}, or declared in an interface, where every
     * field is implicitly {@code static final}.
     */
    private Optional<VariableDeclarator> constantIn(TypeDeclaration<?> type, String name) {
        boolean implicitlyFinal = type instanceof ClassOrInterfaceDeclaration declaration
                && declaration.isInterface();
        return type.getFieldByName(name)
                .filter(field -> implicitlyFinal || field.isFinal())
                .flatMap(field -> field.getVariables().stream()
                        .filter(variable -> variable.getNameAsString().equals(name))
                        .findFirst());
    }

    /**
     * Class-level {@code @InternalApi} → {@link InternalApiMetadata}, or
     * {@code null} when absent. Mirrors {@code extractInternalApiMetadata}: the
     * SDK annotation and the AST record describe different concepts (a known,
     * documented drift), so the only signal extracted is presence → {@code
     * internal = true}; every other field stays at its default.
     */
    private InternalApiMetadata internalApi(ClassOrInterfaceDeclaration type) {
        if (type.getAnnotationByName("InternalApi").isEmpty()) {
            return null;
        }
        return InternalApiMetadata.builder().internal(true).build();
    }

    /**
     * Present-only int attribute from an integer literal (no default — absent
     * leaves it to the caller). A negative literal is a {@code UnaryExpr} (not an
     * {@code IntegerLiteralExpr}) and so falls through to the caller default;
     * harmless for the count-style attributes read here ({@code snapshotThreshold},
     * {@code maxRetries}, {@code minLength}, {@code maxLength}), where a negative
     * value is meaningless. {@code @SagaStep.order} is read by {@link #stepOrder}
     * instead.
     */
    private Optional<Integer> intAttr(AnnotationExpr annotation, String attribute) {
        return value(annotation, attribute)
                .filter(Expression::isIntegerLiteralExpr)
                .map(value -> value.asIntegerLiteralExpr().asNumber().intValue());
    }

    /**
     * Present-only signed int attribute: an integer literal, optionally negated. For
     * an attribute where a sign is not meaningless and dropping it would change what
     * the source says ({@code @Saga.version}); see {@link #sagaMetadata}. Read through
     * {@link #longAttr} and narrowed, which is how javac folds a negated {@code int}
     * constant — so {@code -2147483648} is {@link Integer#MIN_VALUE} on both paths.
     */
    private Optional<Integer> signedIntAttr(AnnotationExpr annotation, String attribute) {
        return longAttr(annotation, attribute).map(Long::intValue);
    }

    private List<RelationshipMetadata> relationships(ClassOrInterfaceDeclaration type) {
        List<RelationshipMetadata> relationships = new ArrayList<>();
        for (FieldDeclaration field : type.getFields()) {
            Optional<AnnotationExpr> rel = field.getAnnotationByName("Relationship");
            if (rel.isEmpty()) {
                continue;
            }
            for (VariableDeclarator var : field.getVariables()) {
                String fieldName = var.getNameAsString();
                // builder(name, target) already sets fieldName = name.
                RelationshipMetadata.Builder builder =
                        RelationshipMetadata.builder(fieldName, targetEntity(var.getType()));
                relationType(rel.get()).ifPresent(builder::type);
                stringAttr(rel.get(), "mappedBy")
                        .filter(s -> !s.isBlank())
                        .ifPresent(builder::mappedBy);
                relationships.add(builder.build());
            }
        }
        return relationships;
    }

    /**
     * The related entity's simple type name. For a collection-typed field
     * ({@code List<OrderLine>}, {@code Set<Tag>}) the element type is unwrapped;
     * otherwise the declared type name is used.
     */
    private String targetEntity(Type type) {
        if (type.isClassOrInterfaceType()) {
            ClassOrInterfaceType declared = type.asClassOrInterfaceType();
            Optional<NodeList<Type>> args = declared.getTypeArguments();
            if (args.isPresent() && !args.get().isEmpty()) {
                return args.get().get(0).asString();
            }
            return declared.getNameAsString();
        }
        return type.asString();
    }

    private Optional<RelationType> relationType(AnnotationExpr annotation) {
        // The annotation's RelationshipType and the AST's RelationType are
        // distinct types bridged purely by constant-name identity (ONE_TO_MANY,
        // MANY_TO_ONE, ...). They must evolve together; a rename on one side
        // without the other yields Optional.empty() here, not a compile error.
        return enumAttr(annotation, "relationshipType").flatMap(name -> {
            try {
                return Optional.of(RelationType.valueOf(name));
            } catch (IllegalArgumentException unknownConstant) {
                return Optional.empty();
            }
        });
    }

    /**
     * ADR-059 data-scope tier, in parity with {@code ExerisDomainProcessor}'s own
     * {@code dataScope} helper.
     *
     * <p>Two constants read as absent rather than as a value. {@code UNSPECIFIED}
     * exists only because an annotation attribute cannot default to {@code null};
     * it means "the author declared no tier", so carrying it onto the wire would
     * invent a fourth tier the AST enum does not have. An unrecognised name reads
     * as absent for the same reason {@link #relationType} does — the annotation's
     * {@code ExerisDomain.DataScope} and the AST's {@link DataScope} are distinct
     * types bridged by constant-name identity, and a rename on one side alone must
     * not become a parse failure here.
     *
     * <p>Both cases leave the component unset, which is exactly what the processor
     * emits for them, so {@code effectiveDataScope()} resolves through the
     * {@code tenantScoped} fallback on either path.
     */
    private Optional<DataScope> dataScope(AnnotationExpr annotation) {
        return enumAttr(annotation, "dataScope")
                .filter(name -> !"UNSPECIFIED".equals(name))
                .flatMap(name -> {
                    try {
                        return Optional.of(DataScope.valueOf(name));
                    } catch (IllegalArgumentException unknownConstant) {
                        return Optional.empty();
                    }
                });
    }

    /**
     * {@code @DomainEvent.trigger} → the AST-owned {@link DomainEventMetadata.Trigger},
     * bridged by constant-name identity like {@link #dataScope} and {@link #relationType}.
     * An unrecognised constant leaves the component unset rather than failing the parse: the
     * two enums are independent types (ADR-059), so a value added on the annotation side
     * alone must degrade to "not extracted", not to a broken read.
     *
     * @since 0.11
     */
    private Optional<DomainEventMetadata.Trigger> eventTrigger(AnnotationExpr annotation) {
        return enumAttr(annotation, TRIGGER)
                .flatMap(name -> {
                    try {
                        return Optional.of(DomainEventMetadata.Trigger.valueOf(name));
                    } catch (IllegalArgumentException unknownConstant) {
                        return Optional.empty();
                    }
                });
    }

    /** Constant name of an enum-valued annotation attribute, e.g. {@code ONE_TO_MANY}. */
    private Optional<String> enumAttr(AnnotationExpr annotation, String attribute) {
        return value(annotation, attribute).map(this::constantName);
    }

    /**
     * The constant an enum-valued expression names: {@code Level.PUBLIC},
     * {@code RouteAccess.Level.PUBLIC} and a statically imported {@code PUBLIC} all
     * name {@code PUBLIC}.
     */
    private String constantName(Expression value) {
        if (value.isFieldAccessExpr()) {
            return value.asFieldAccessExpr().getNameAsString();
        }
        if (value.isNameExpr()) {
            return value.asNameExpr().getNameAsString();
        }
        // Unsupported value shape (e.g. a parenthesised expression): fall back
        // to the raw text. Callers that map this to an enum will treat an
        // unrecognised name as "absent" rather than failing.
        return value.toString();
    }

    /**
     * The {@code @RouteAccess} an element declares, bridged onto the AST-owned
     * {@link RouteAccess} by constant name. Both constants are carried, {@code PUBLIC}
     * included: absence is the "declared nothing" state, so a {@code PUBLIC} that were
     * skipped would read as a route nobody spoke about. Read in the single-member form
     * {@code @RouteAccess(Level.PUBLIC)} and the named form {@code @RouteAccess(value = …)}, with
     * the constant optionally in parentheses; a value that names no constant is empty and is
     * reported by {@link #unmodeledFacets}.
     */
    private Optional<RouteAccess> routeAccess(NodeWithAnnotations<?> element) {
        return element.getAnnotationByName("RouteAccess").flatMap(this::routeAccessLevel);
    }

    /**
     * The level one {@code @RouteAccess} annotation names. Parentheses around the constant are
     * folded, as javac folds them for the processor; any other value that is not a bare or
     * qualified {@code Level} constant is empty.
     */
    private Optional<RouteAccess> routeAccessLevel(AnnotationExpr annotation) {
        Optional<Expression> member = annotation.isSingleMemberAnnotationExpr()
                ? Optional.of(annotation.asSingleMemberAnnotationExpr().getMemberValue())
                : value(annotation, VALUE);
        return member.map(expression -> {
            Expression unwrapped = expression;
            while (unwrapped.isEnclosedExpr()) {
                unwrapped = unwrapped.asEnclosedExpr().getInner();
            }
            return unwrapped;
        }).map(this::constantName).flatMap(name -> {
            try {
                return Optional.of(RouteAccess.valueOf(name));
            } catch (IllegalArgumentException _) {
                return Optional.empty();
            }
        });
    }

    /**
     * Whether {@code type}, or one of its {@code @Action} methods, carries a
     * {@code @RouteAccess} whose value does not resolve to a {@code Level} constant.
     * {@link #routeAccess} carries nothing for it, while the processor receives the constant
     * folded by javac. A dropped {@code AUTHENTICATED} leaves the route on the fail-open
     * fallback for routes that declare nothing.
     */
    private boolean routeAccessUnresolved(ClassOrInterfaceDeclaration type) {
        if (type.getAnnotationByName("RouteAccess").map(a -> routeAccessLevel(a).isEmpty()).orElse(false)) {
            return true;
        }
        for (MethodDeclaration method : type.getMethods()) {
            if (method.isAnnotationPresent("Action")
                    && method.getAnnotationByName("RouteAccess").map(a -> routeAccessLevel(a).isEmpty()).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The annotations on {@code type} whose {@code permissions} hold an element that is not a
     * string literal: {@code @ExerisDomain} for the entity's, {@code @Action} for an action's.
     * {@link #stringArrayAttr} reads literals only, so such an element is missing from what
     * {@link #read} carries, while the processor receives it folded by javac. A permission
     * dropped here widens the route it guards, or turns an action's own declaration into an
     * inherited one.
     */
    private Set<String> unresolvedPermissionCarriers(ClassOrInterfaceDeclaration type) {
        Set<String> carriers = new TreeSet<>();
        if (type.getAnnotationByName("ExerisDomain").map(this::hasNonLiteralPermission).orElse(false)) {
            carriers.add("@ExerisDomain");
        }
        for (MethodDeclaration method : type.getMethods()) {
            if (method.getAnnotationByName("Action").map(this::hasNonLiteralPermission).orElse(false)) {
                carriers.add("@Action");
            }
        }
        return carriers;
    }

    private boolean hasNonLiteralPermission(AnnotationExpr annotation) {
        return value(annotation, PERMISSIONS).map(expression -> {
            if (expression.isArrayInitializerExpr()) {
                return expression.asArrayInitializerExpr().getValues().stream()
                        .anyMatch(element -> !element.isStringLiteralExpr());
            }
            return !expression.isStringLiteralExpr();
        }).orElse(false);
    }

    private Optional<String> stringAttr(AnnotationExpr annotation, String attribute) {
        return value(annotation, attribute)
                .filter(Expression::isStringLiteralExpr)
                .map(value -> value.asStringLiteralExpr().asString());
    }

    private Optional<Expression> value(AnnotationExpr annotation, String attribute) {
        if (!annotation.isNormalAnnotationExpr()) {
            return Optional.empty();
        }
        return annotation.asNormalAnnotationExpr().getPairs().stream()
                .filter(pair -> pair.getNameAsString().equals(attribute))
                .map(MemberValuePair::getValue)
                .findFirst();
    }

    /**
     * Entity fields, mirroring {@code ExerisDomainProcessor.extractFieldsMetadata}'s
     * two-path contract:
     * <ul>
     *   <li>a field <b>with</b> {@code @Field} → {@link #fieldMetadata} (full
     *       attribute surface, present-only on a {@link FieldMetadata.Builder});</li>
     *   <li>a field <b>without</b> {@code @Field} → {@link FieldMetadata#simple}
     *       (which, unlike the builder default, sets
     *       {@code searchable}/{@code sortable}/{@code filterable} to {@code true}).</li>
     * </ul>
     * The two paths intentionally differ on the search/sort/filter defaults — that
     * asymmetry <em>is</em> the processor's behaviour, so the reader reproduces it
     * exactly to stay in lock-step. {@code @Relationship} fields are skipped (they
     * are modelled separately). On either path a {@code @Blob} adds {@link FieldMetadata#blob()}
     * ({@link #blob}).
     */
    private List<FieldMetadata> fields(ClassOrInterfaceDeclaration type) {
        List<FieldMetadata> fields = new ArrayList<>();
        for (FieldDeclaration field : type.getFields()) {
            if (field.getAnnotationByName("Relationship").isPresent()) {
                continue;
            }
            Optional<AnnotationExpr> fieldAnn = field.getAnnotationByName("Field");
            Optional<AnnotationExpr> validationAnn = field.getAnnotationByName("Validation");
            Optional<BlobMetadata> blob = field.getAnnotationByName("Blob").map(this::blob);
            for (VariableDeclarator var : field.getVariables()) {
                String name = var.getNameAsString();
                String fieldType = var.getTypeAsString();
                FieldMetadata.Builder builder = fieldAnn.isPresent()
                        ? fieldMetadata(name, fieldType, fieldAnn.get(), validationAnn.orElse(null))
                        : FieldMetadata.builder(name, fieldType).searchable(true).sortable(true).filterable(true);
                blob.ifPresent(builder::blob);
                fields.add(builder.build());
            }
        }
        return fields;
    }

    /**
     * A single {@code @Field}-annotated field → {@link FieldMetadata}, mirroring
     * {@code extractFieldMetadata}: the {@code @Field} attribute surface the
     * processor reads (label→displayName, description, required, unique, indexed,
     * searchable, sortable, filterable, readOnly, inCreate, inUpdate, computed,
     * computedFrom) is applied present-only; {@code @Validation} (only consulted
     * when {@code @Field} is present, as in the processor) contributes
     * {@code min}/{@code max}/{@code pattern} plus the two deprecated fallbacks.
     *
     * <p>"Attribute-complete" means complete <em>against what the processor reads</em>,
     * not against every {@code @Field} attribute with a {@link FieldMetadata}
     * counterpart. Notably {@code @Field.defaultValue} is <b>not</b> read: although
     * {@code FieldMetadata.defaultValue} exists, {@code extractFieldMetadata} never
     * populates it, so reading it here would be a divergence, not a fix.
     * {@code @Field.dataType} <b>is</b> read: the processor's {@code extractFieldMetadata}
     * populates {@link FieldMetadata#dataType()} and this reader mirrors it, so the
     * two stay in lock-step on the codegen baseline (ADR-042). (The remaining unread
     * {@code @Field} attributes —
     * {@code inList}, {@code inDetail}, {@code order}, {@code ui}, {@code cssClass},
     * {@code group}, {@code sensitive}, {@code encrypted}, {@code maskPattern},
     * {@code writeOnly}, {@code compositeUnique} — have no {@code FieldMetadata}
     * counterpart at all.)
     */
    private FieldMetadata.Builder fieldMetadata(String name, String type, AnnotationExpr field, AnnotationExpr validation) {
        FieldMetadata.Builder builder = FieldMetadata.builder(name, type);
        stringAttr(field, LABEL).ifPresent(builder::displayName);
        stringAttr(field, "description").ifPresent(builder::description);
        boolAttr(field, REQUIRED).ifPresent(builder::required);
        boolAttr(field, "unique").ifPresent(builder::unique);
        boolAttr(field, "indexed").ifPresent(builder::indexed);
        boolAttr(field, "searchable").ifPresent(builder::searchable);
        boolAttr(field, "sortable").ifPresent(builder::sortable);
        boolAttr(field, "filterable").ifPresent(builder::filterable);
        boolAttr(field, "readOnly").ifPresent(builder::readOnly);
        boolAttr(field, "inCreate").ifPresent(builder::inCreate);
        boolAttr(field, "inUpdate").ifPresent(builder::inUpdate);
        boolAttr(field, "computed").ifPresent(builder::computed);
        stringArrayAttr(field, "computedFrom").ifPresent(builder::computedFrom);
        stringAttr(field, "dataType").ifPresent(builder::dataType);
        if (validation != null) {
            applyValidation(field, validation, builder);
        }
        return builder;
    }

    /**
     * {@code @Blob} → {@link BlobMetadata}, as written: {@code container} is a non-blank string
     * literal, else {@code null}, and {@code contentTypes} the declared literals in declaration
     * order, empty when none are declared. A {@code @Blob} declaring nothing is
     * {@link BlobMetadata#unrestricted()}. The entity's data scope is not consulted: the
     * {@code GLOBAL}-scoped combination is refused at build time by {@code exeris-tooling}, and
     * the reader reads the declaration the source makes.
     */
    private BlobMetadata blob(AnnotationExpr blob) {
        return new BlobMetadata(
                nonBlankStringAttr(blob, "container").orElse(null),
                stringArrayAttr(blob, "contentTypes").orElse(List.of()));
    }

    /**
     * {@code @Validation} contributions to {@link FieldMetadata}, mirroring the
     * validation block of {@code extractFieldMetadata} plus
     * {@code applyDeprecatedValidationFallbacks}:
     * <ul>
     *   <li>{@code min}/{@code max} (long), {@code minLength}/{@code maxLength}
     *       (int) and {@code pattern} present-only;</li>
     *   <li>deprecated {@code required = true} → {@code @Field.required} only when
     *       {@code @Field} did not set it (canonical wins);</li>
     *   <li>deprecated {@code validateOn} = {@code "CREATE"}/{@code "UPDATE"} →
     *       {@code inUpdate}/{@code inCreate} = false, again only when {@code @Field}
     *       did not set the corresponding attribute.</li>
     * </ul>
     * The processor also emits build warnings for the deprecated attributes; the
     * reader has no diagnostics channel and produces the same {@code DomainMetadata}
     * value without them, which is what reattach compares.
     */
    private void applyValidation(AnnotationExpr field, AnnotationExpr validation, FieldMetadata.Builder builder) {
        // min/max/minLength/maxLength/pattern are read present-only, mirroring the
        // processor's @Validation block exactly: ExerisDomainProcessor extracts all
        // five into FieldMetadata, guarded on explicit presence in source, so an
        // absent attribute never floods in the annotation default and stays null on
        // FieldMetadata here too. Note: min == 0 is not wire-safe under
        // @JsonInclude(NON_DEFAULT) (Jackson 3 drops boxed Long(0)); that belongs
        // to the documented Field/Validation overlap, not to this reader.
        longAttr(validation, "min").ifPresent(builder::min);
        longAttr(validation, "max").ifPresent(builder::max);
        intAttr(validation, "minLength").ifPresent(builder::minLength);
        intAttr(validation, "maxLength").ifPresent(builder::maxLength);
        stringAttr(validation, "pattern").ifPresent(builder::pattern);

        if (boolAttr(validation, REQUIRED).orElse(false) && boolAttr(field, REQUIRED).isEmpty()) {
            builder.required(true);
        }
        stringAttr(validation, "validateOn").filter(s -> !s.isEmpty()).ifPresent(validateOn -> {
            if (CREATE.equals(validateOn) && boolAttr(field, "inUpdate").isEmpty()) {
                builder.inUpdate(false);
            } else if ("UPDATE".equals(validateOn) && boolAttr(field, "inCreate").isEmpty()) {
                builder.inCreate(false);
            }
        });
    }

    /**
     * Present-only {@code String[]} attribute as a {@code List<String>} of literal
     * values — both the {@code {"a","b"}} array form and the single-element
     * {@code "a"} (brace-elided) form, mirroring how the processor unwraps the
     * javac array. Empty when the attribute is absent.
     */
    private Optional<List<String>> stringArrayAttr(AnnotationExpr annotation, String attribute) {
        return value(annotation, attribute).map(expression -> {
            List<String> values = new ArrayList<>();
            if (expression.isArrayInitializerExpr()) {
                for (Expression element : expression.asArrayInitializerExpr().getValues()) {
                    if (element.isStringLiteralExpr()) {
                        values.add(element.asStringLiteralExpr().asString());
                    }
                }
            } else if (expression.isStringLiteralExpr()) {
                values.add(expression.asStringLiteralExpr().asString());
            }
            return values;
        });
    }

    /**
     * Present-only signed-long attribute from an integer/long literal, unwrapping a
     * leading unary minus (a {@code UnaryExpr}). Unlike the count-style
     * {@link #intAttr}, negatives are meaningful here ({@code @Validation.min}/
     * {@code max} are signed bounds), so they are read rather than dropped.
     */
    private Optional<Long> longAttr(AnnotationExpr annotation, String attribute) {
        return value(annotation, attribute).flatMap(this::asLong);
    }

    private Optional<Long> asLong(Expression expression) {
        if (expression.isUnaryExpr()) {
            UnaryExpr unary = expression.asUnaryExpr();
            if (unary.getOperator() == UnaryExpr.Operator.MINUS) {
                return asLong(unary.getExpression()).map(value -> -value);
            }
            return Optional.empty();
        }
        if (expression.isIntegerLiteralExpr()) {
            return Optional.of(expression.asIntegerLiteralExpr().asNumber().longValue());
        }
        if (expression.isLongLiteralExpr()) {
            return Optional.of(expression.asLongLiteralExpr().asNumber().longValue());
        }
        return Optional.empty();
    }

    private String packageName(CompilationUnit cu) {
        return cu.getPackageDeclaration().map(pkg -> pkg.getNameAsString()).orElse("");
    }
}
