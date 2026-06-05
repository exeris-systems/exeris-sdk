package eu.exeris.sdk.sourcemodel.io;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import eu.exeris.sdk.sourcemodel.ast.ActionMetadata;
import eu.exeris.sdk.sourcemodel.ast.ActionParamMetadata;
import eu.exeris.sdk.sourcemodel.ast.DomainEventMetadata;
import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.EnumMetadata;
import eu.exeris.sdk.sourcemodel.ast.FieldMetadata;
import eu.exeris.sdk.sourcemodel.ast.RelationshipMetadata;
import eu.exeris.sdk.sourcemodel.ast.RelationshipMetadata.RelationType;
import eu.exeris.sdk.sourcemodel.ast.UIMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Reads Java source into a {@link DomainMetadata} without invoking {@code javac}
 * — the in-editor counterpart to the build-time annotation processor.
 *
 * <p>0.3.0 scope: locates the first {@code @ExerisDomain}-annotated type and
 * extracts its entity name ({@code @ExerisDomain(name=...)}, falling back to the
 * class name), package, fields, and {@code @Relationship}s. Of the {@code @Field}
 * attributes only {@code required} is read; all others are left at the
 * {@link FieldMetadata} factory defaults (e.g. {@code searchable}/{@code sortable}/
 * {@code filterable} default to {@code true} — see {@link FieldMetadata#simple}).
 * For relationships, {@code targetEntity} (collection element type unwrapped),
 * {@code relationshipType}, and {@code mappedBy} are read; other attributes keep
 * builder defaults. {@code @Action} methods are read into {@link ActionMetadata}
 * (name, label, httpMethod, async, and {@code @ActionParam} parameters);
 * {@link #readEnums} extracts enum declarations separately, as the processor
 * does. Class-level {@code @UI} is read into {@link UIMetadata} (view flags,
 * matching the processor's default-true convention). The domain-level
 * {@code @ExerisDomain} string + boolean attributes the processor reads
 * (module, path, aggregate, description, apiVersion, the {@code *Api} flags,
 * tenantScoped, softDelete, audited, versioned, sensitive, cacheable,
 * cache/search config) are read present-only. Domain {@code @DomainEvent}s are read
 * into {@link DomainMetadata#events} (direct/repeated, hand-written
 * {@code @DomainEvents} container, and nested-class legacy form — mirroring the
 * processor's three sources and its trigger-based name derivation).
 *
 * <p><b>Round-trip safety.</b> Relative to the processor (the codegen baseline),
 * {@code read()} still diverges on the facets the processor emits but the reader
 * does not yet: graph, saga, event-sourcing, internal-API, and field-level
 * {@code @Validation}. A round-trip / reattach caller MUST consult
 * {@link #unmodeledFacets} first and refuse (or warn) when it is non-empty,
 * otherwise the dropped metadata is silently misattributed as a user edit during
 * conflict detection. (Facets the processor doesn't read either — {@code @Projection},
 * {@code @NavMenu}, {@code @EventHandler}, {@code system}/{@code security} annotations
 * — are not divergences.)
 *
 * <p><b>Limitations.</b> Annotation matching is by <em>simple name</em>
 * ({@code ExerisDomain}, {@code Field}, {@code Relationship}) without import
 * resolution — activating JavaParser symbol-solving would pull heavy optional
 * deps and is out of scope. Instances are <b>not thread-safe</b> (they hold a
 * single {@code JavaParser}); use one per call-site or guard externally.
 *
 * @since 0.3.0
 */
public final class SourceModelReader {

    /**
     * Annotation simple names where {@link #read} <em>diverges from the annotation
     * processor</em>: the processor populates a distinct {@link DomainMetadata}
     * facet from them, but the reader does not yet. These are the dangerous ones
     * for reattach — the conflict-detection baseline is the processor's codegen
     * output, so a facet the processor keeps and the reader drops would be
     * misread as a user edit. (Facets neither side reads — e.g. {@code @Projection},
     * {@code @NavMenu}, {@code @EventHandler}, the {@code system}/{@code security}
     * annotations — are <b>not</b> divergences and are not flagged: the reader's
     * {@code DomainMetadata} matches the processor's for those sources.)
     *
     * <p>Matched by simple name (no import resolution). As reader slices land, a
     * name graduates out of this set.
     */
    private static final Set<String> UNMODELED_FACET_ANNOTATIONS = Set.of(
            "Graph", "GraphEdge", "GraphProperty", "GraphQuery",    // -> graphMetadata
            "EventSourced",                                         // -> eventSourced
            "Saga", "SagaStep",                                     // -> sagaMetadata
            "InternalApi",                                          // -> internalApi
            "Validation");                                          // -> FieldMetadata validation

    private final JavaParser javaParser;

    public SourceModelReader() {
        this.javaParser = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.CURRENT));
    }

    /**
     * Parses {@code javaSource} and returns the domain model for its first
     * {@code @ExerisDomain} type, or {@link Optional#empty()} if the source
     * contains no such type.
     *
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
     * Reports the metadata facets where {@link #read} <b>diverges from the
     * annotation processor</b> for {@code javaSource}: facets the processor would
     * emit into {@link DomainMetadata} but the reader does not yet. A <b>non-empty</b>
     * result means {@code read()}'s {@code DomainMetadata} differs from the codegen
     * baseline — round-trip / reattach callers must treat it as unsafe for conflict
     * detection (the dropped facets would be misattributed as user edits). An
     * <b>empty</b> result means {@code read()} produces the same {@code DomainMetadata}
     * facets the processor would for this source.
     *
     * <p>Detected: presence of any annotation in {@link #UNMODELED_FACET_ANNOTATIONS}
     * (graph, saga, event-sourcing, internal-API, and field-level
     * {@code @Validation}). <b>Not</b> flagged: facets neither side reads
     * ({@code @Projection}, {@code @NavMenu}, {@code @EventHandler},
     * {@code @Tab}/{@code @UIGroup}, the {@code system}/{@code security} annotations),
     * {@code @DomainEvent}s (now read into {@code events}), and {@code @ExerisDomain}
     * attributes (the reader reads exactly the set the processor does) — those don't
     * change the {@code DomainMetadata} relative to the baseline, so flagging them
     * would needlessly refuse safe entities.
     *
     * <p>The one gap <em>not</em> reported is attribute-level loss <em>within</em> a
     * read annotation (e.g. an unread {@code @Field(searchable=false)} left at a
     * default) — a separate, narrower concern. Matching is by simple name (no import
     * resolution). Re-parses {@code javaSource} independently of {@link #read}.
     */
    public Set<String> unmodeledFacets(String javaSource) {
        CompilationUnit cu = parseOrThrow(javaSource);
        Optional<ClassOrInterfaceDeclaration> domain = cu.findFirst(ClassOrInterfaceDeclaration.class,
                type -> type.isAnnotationPresent("ExerisDomain"));
        if (domain.isEmpty()) {
            return Set.of();
        }

        Set<String> facets = new TreeSet<>();
        for (AnnotationExpr annotation : cu.findAll(AnnotationExpr.class)) {
            String simpleName = simpleName(annotation.getNameAsString());
            if (UNMODELED_FACET_ANNOTATIONS.contains(simpleName)) {
                facets.add("@" + simpleName);
            }
        }
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
        List<FieldMetadata> fields = new ArrayList<>();
        for (FieldDeclaration field : type.getFields()) {
            if (field.getAnnotationByName("Relationship").isPresent()) {
                continue; // relationships are modelled separately, not as plain fields
            }
            boolean required = isRequired(field);
            for (VariableDeclarator var : field.getVariables()) {
                String name = var.getNameAsString();
                String fieldType = var.getTypeAsString();
                fields.add(required
                        ? FieldMetadata.required(name, fieldType)
                        : FieldMetadata.simple(name, fieldType));
            }
        }

        String entityName = exerisDomainName(type).orElse(type.getNameAsString());
        DomainMetadata.Builder builder = DomainMetadata.builder(entityName, packageName(cu))
                .fields(fields)
                .relationships(relationships(type))
                .actions(actions(type))
                .events(events(type));
        type.getAnnotationByName("ExerisDomain").ifPresent(ann -> applyDomainAttributes(ann, builder));
        UIMetadata ui = uiMetadata(type);
        if (ui != null) {
            builder.uiMetadata(ui);
        }
        return builder.build();
    }

    /**
     * Reads the domain-level {@code @ExerisDomain} attributes the processor reads
     * (string + boolean) into the builder, present-only — an absent attribute keeps
     * the builder default, exactly matching {@code ExerisDomainProcessor}'s
     * {@code if (values.containsKey(...)) builder.set(...)} pattern. Array attributes
     * ({@code tags}/{@code roles}/{@code permissions}) and the {@code *Field}/
     * {@code validationMode}/{@code ui} attributes are not read here — the processor
     * doesn't read them either, so they constitute no reader-vs-processor divergence
     * and {@link #unmodeledFacets} does not flag them.
     */
    private void applyDomainAttributes(AnnotationExpr ann, DomainMetadata.Builder builder) {
        stringAttr(ann, "module").ifPresent(builder::module);
        stringAttr(ann, "path").ifPresent(builder::path);
        stringAttr(ann, "aggregate").ifPresent(builder::aggregate);
        stringAttr(ann, "description").ifPresent(builder::description);
        stringAttr(ann, "apiVersion").ifPresent(builder::apiVersion);
        stringAttr(ann, "cacheTtl").ifPresent(builder::cacheTtl);
        stringAttr(ann, "cacheRegion").ifPresent(builder::cacheRegion);
        stringAttr(ann, "searchConfig").ifPresent(builder::searchConfig);
        boolAttr(ann, "restApi").ifPresent(builder::restApi);
        boolAttr(ann, "graphqlApi").ifPresent(builder::graphqlApi);
        boolAttr(ann, "realTimeApi").ifPresent(builder::realTimeApi);
        boolAttr(ann, "internalClient").ifPresent(builder::internalClient);
        boolAttr(ann, "tenantScoped").ifPresent(builder::tenantScoped);
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
     * Class-level standalone {@code @UI} → {@link UIMetadata}, or {@code null} when
     * absent (matching {@code ExerisDomainProcessor.extractUIMetadata}, which reads
     * a directly-present {@code @UI} via {@code findAnnotation}).
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
     *   <li>{@code bulkActions}/{@code columns}/{@code defaultLayout}/{@code groups}/
     *       {@code fieldOverrides} — no {@code @UI} attribute exists for them; they
     *       stay at {@link UIMetadata} builder defaults.</li>
     * </ul>
     */
    private UIMetadata uiMetadata(ClassOrInterfaceDeclaration type) {
        Optional<AnnotationExpr> ui = type.getAnnotationByName("UI");
        if (ui.isEmpty()) {
            return null;
        }
        return UIMetadata.builder()
                .listView(boolAttr(ui.get(), "listView", true))
                .detailView(boolAttr(ui.get(), "detailView", true))
                .createForm(boolAttr(ui.get(), "createForm", true))
                .editForm(boolAttr(ui.get(), "editForm", true))
                .searchable(boolAttr(ui.get(), "searchable", true))
                .filterable(boolAttr(ui.get(), "filterable", true))
                .exportable(boolAttr(ui.get(), "exportable", false))
                .build();
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
                    .async(boolAttr(action.get(), "async", false));
            stringAttr(action.get(), "label").ifPresent(builder::displayName);
            stringAttr(action.get(), "description")
                    .filter(s -> !s.isBlank())
                    .ifPresent(builder::description);
            stringAttr(action.get(), "httpMethod").ifPresent(builder::httpMethod);
            for (Parameter parameter : method.getParameters()) {
                parameter.getAnnotationByName("ActionParam")
                        .ifPresent(ann -> builder.addParam(actionParam(parameter, ann)));
            }
            actions.add(builder.build());
        }
        return actions;
    }

    private ActionParamMetadata actionParam(Parameter parameter, AnnotationExpr annotation) {
        String name = stringAttr(annotation, "name")
                .filter(s -> !s.isBlank())
                .orElse(parameter.getNameAsString());
        // @ActionParam.required defaults to true (mirrors the annotation default).
        ActionParamMetadata.Builder builder = ActionParamMetadata.builder(name, parameter.getTypeAsString())
                .required(boolAttr(annotation, "required", true));
        stringAttr(annotation, "label").ifPresent(builder::displayName);
        stringAttr(annotation, "description")
                .filter(s -> !s.isBlank())
                .ifPresent(builder::description);
        stringAttr(annotation, "defaultValue")
                .filter(s -> !s.isBlank())
                .ifPresent(builder::defaultValue);
        return builder.build();
    }

    /**
     * Domain events declared on the entity, mirroring
     * {@code ExerisDomainProcessor.extractEventsMetadata}. Reads three source
     * shapes, all keyed off the entity's <em>class</em> simple name (not the
     * {@code @ExerisDomain(name)}), exactly as the processor uses
     * {@code element.getSimpleName()}:
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
        for (AnnotationExpr annotation : type.getAnnotations()) {
            String simpleName = simpleName(annotation.getNameAsString());
            if ("DomainEvent".equals(simpleName)) {
                events.add(singleEvent(annotation, entitySimpleName));
            } else if ("DomainEvents".equals(simpleName)) {
                for (AnnotationExpr inner : containerEvents(annotation)) {
                    events.add(singleEvent(inner, entitySimpleName));
                }
            }
        }
        for (BodyDeclaration<?> member : type.getMembers()) {
            if (member instanceof ClassOrInterfaceDeclaration nested && !nested.isInterface()) {
                nested.getAnnotationByName("DomainEvent").ifPresent(annotation ->
                        events.add(DomainEventMetadata.withTopic(
                                nested.getNameAsString(), stringAttr(annotation, "topic").orElse(null))));
            }
        }
        return events;
    }

    /**
     * One {@code @DomainEvent} → {@link DomainEventMetadata}, mirroring
     * {@code extractSingleEventMetadata}: an absent/blank {@code name} is derived as
     * {@code entityName + suffix(trigger)} with {@code trigger} defaulting to
     * {@code CREATE}; {@code aggregateType} is always the entity class name.
     */
    private DomainEventMetadata singleEvent(AnnotationExpr annotation, String entitySimpleName) {
        String name = stringAttr(annotation, "name")
                .filter(s -> !s.isBlank())
                .orElseGet(() -> entitySimpleName
                        + triggerToEventSuffix(enumAttr(annotation, "trigger").orElse("CREATE")));
        String topic = stringAttr(annotation, "topic").orElse(null);
        String description = stringAttr(annotation, "description").orElse(null);
        return new DomainEventMetadata(name, topic, description, entitySimpleName);
    }

    /**
     * The inner {@code @DomainEvent} annotations of a hand-written
     * {@code @DomainEvents} container — either {@code @DomainEvents({...})}
     * (single-member) or {@code @DomainEvents(value = {...})} (normal); a lone
     * non-array element is also tolerated.
     */
    private List<AnnotationExpr> containerEvents(AnnotationExpr container) {
        Optional<Expression> value = container.isSingleMemberAnnotationExpr()
                ? Optional.of(container.asSingleMemberAnnotationExpr().getMemberValue())
                : value(container, "value");
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
        if (trigger == null) {
            return "Event";
        }
        return switch (trigger) {
            case "CREATE" -> "CreatedEvent";
            case "UPDATE" -> "UpdatedEvent";
            case "DELETE" -> "DeletedEvent";
            case "FIELD_CHANGED" -> "ChangedEvent";
            case "ACTION" -> "ActionEvent";
            default -> "Event";
        };
    }

    private boolean boolAttr(AnnotationExpr annotation, String attribute, boolean defaultValue) {
        return value(annotation, attribute)
                .map(Expression::toString)
                .map("true"::equals)
                .orElse(defaultValue);
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

    /** Constant name of an enum-valued annotation attribute, e.g. {@code ONE_TO_MANY}. */
    private Optional<String> enumAttr(AnnotationExpr annotation, String attribute) {
        return value(annotation, attribute).map(value -> {
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
        });
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
     * The {@code name} attribute of {@code @ExerisDomain(name = "...")} when
     * present and non-blank — the canonical entity name the processor uses, which
     * may differ from the Java class name. Empty otherwise (caller falls back to
     * the class name).
     */
    private Optional<String> exerisDomainName(ClassOrInterfaceDeclaration type) {
        return type.getAnnotationByName("ExerisDomain")
                .flatMap(ann -> stringAttr(ann, "name"))
                .filter(name -> !name.isBlank());
    }

    /**
     * {@code @Field}-shape ownership of {@code required} (canonical per the
     * annotations package-info): true only when {@code @Field(required = true)}
     * is present. A bare {@code @Field} marker or no annotation means not-required.
     */
    private boolean isRequired(FieldDeclaration field) {
        Optional<AnnotationExpr> ann = field.getAnnotationByName("Field");
        return ann.isPresent()
                && "true".equals(value(ann.get(), "required").map(Expression::toString).orElse(null));
    }

    private String packageName(CompilationUnit cu) {
        return cu.getPackageDeclaration().map(pkg -> pkg.getNameAsString()).orElse("");
    }
}
