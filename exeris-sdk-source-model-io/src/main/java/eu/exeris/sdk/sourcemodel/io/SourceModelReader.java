package eu.exeris.sdk.sourcemodel.io;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
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
import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.EnumMetadata;
import eu.exeris.sdk.sourcemodel.ast.FieldMetadata;
import eu.exeris.sdk.sourcemodel.ast.RelationshipMetadata;
import eu.exeris.sdk.sourcemodel.ast.RelationshipMetadata.RelationType;
import eu.exeris.sdk.sourcemodel.ast.UIMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
 * matching the processor's default-true convention).
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
                .actions(actions(type));
        UIMetadata ui = uiMetadata(type);
        if (ui != null) {
            builder.uiMetadata(ui);
        }
        return builder.build();
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
