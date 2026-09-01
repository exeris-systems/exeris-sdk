package eu.exeris.sdk.catalog;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.ParamTree;
import com.sun.source.util.DocTrees;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Emits {@value #RESOURCE_PATH} — a JSON Schema for the build-time AST hand-off format —
 * into the jar of whatever module it processes.
 *
 * <p>The AST records in {@code eu.exeris.sdk.sourcemodel.ast} are the format the processor
 * writes and every generator reads ({@code exeris-metadata/<entity>.json}). Until now the
 * only description of that format was the Java sources themselves, which is fine for a
 * consumer that compiles against the jar and useless for one that does not — an agent
 * reading the AST, a non-JVM generator, a validator in CI.
 *
 * <p><b>Why a processor rather than a schema library.</b> A generator built on Jackson's
 * own schema module recovers the property names and types and stops there. It cannot
 * recover the <em>prose</em> (a class file carries no javadoc), and — the reason this
 * schema exists at all — it cannot state the two Jackson 3 postures a reader must adopt to
 * read the format correctly, because those are facts about how this codebase configures
 * Jackson rather than facts about the types. They are carried here as
 * {@code x-exeris-reader-requirements} and as per-property flags, so a consumer inherits
 * them with the schema instead of learning them from a wiki page.
 *
 * <h2>The two requirements, and how the schema carries them</h2>
 * <ol>
 *   <li><b>{@code FAIL_ON_NULL_FOR_PRIMITIVES = false}.</b> Jackson 3 defaults it to
 *       {@code true}. The AST uses primitive {@code boolean} / {@code int} components under
 *       {@code NON_DEFAULT}, so a default-valued one is <em>absent</em> from the document
 *       and arrives at the constructor as {@code null}. Every property whose Java type is
 *       primitive is flagged {@code x-exeris-primitive}, which is the set a reader has to
 *       be able to tolerate absent.</li>
 *   <li><b>Per-component {@code NON_NULL} is what lets a zero survive.</b> Class-level
 *       {@code NON_DEFAULT} treats a boxed {@code 0} as empty and drops it, which is why
 *       {@code FieldMetadata}'s bounds carry their own {@code NON_NULL}. Each definition
 *       records its class-level posture as {@code x-exeris-json-include}, and any property
 *       that overrides it records its own — so "is a zero here meaningful?" is answerable
 *       from the schema rather than from the source.</li>
 * </ol>
 *
 * <h2>No {@code required}, deliberately</h2>
 * <p>Every property may legitimately be absent: all three postures this package uses
 * ({@code NON_NULL}, {@code NON_DEFAULT}, {@code NON_EMPTY}) omit rather than write, and
 * the record's own constructor restores the default. A {@code required} list would
 * therefore reject documents this SDK itself produces. Which components a compact
 * constructor rejects as {@code null} is a construction-time invariant and not a property
 * of the wire format; it is not derivable from the declaration model and is not guessed at
 * here.
 *
 * <p><b>No timestamp</b>, for the reason {@link AnnotationCatalogProcessor} states: the
 * output is a pure function of the sources and the version options, so two builds of one
 * commit produce identical bytes.
 */
@SupportedAnnotationTypes("*")
@SupportedOptions({AstSchemaProcessor.OPT_SDK_VERSION, AstSchemaProcessor.OPT_AST_VERSION})
public final class AstSchemaProcessor extends AbstractProcessor {

    /** Where the schema lands inside the processed module's jar. */
    public static final String RESOURCE_PATH = "META-INF/exeris/ast-schema.json";

    /** Set by the build to {@code ${project.version}}. */
    public static final String OPT_SDK_VERSION = "exeris.schema.sdkVersion";

    /**
     * Set by the build to the AST wire-format version — {@code SchemaVersion.CURRENT}.
     * Distinct from the SDK version: the wire shape changes on its own schedule, and a
     * consumer pairs a baseline stamp against <em>this</em> number. It is passed in rather
     * than read from {@code SchemaVersion} because that constant is deliberately no longer
     * a compile-time constant (it is initialized from a method, so it does not inline into
     * consumers), which also puts it out of reach of the declaration model. The duplication
     * is guarded: {@code AstSchemaContractTest} fails if the two disagree.
     */
    public static final String OPT_AST_VERSION = "exeris.schema.astVersion";

    /** The package whose records are the hand-off format. */
    public static final String AST_PACKAGE = "eu.exeris.sdk.sourcemodel.ast";

    /** The document a generator is handed, and therefore the schema's root. */
    public static final String ROOT_DEFINITION = "DomainMetadata";

    /**
     * The shape of this file. Bump when a consumer would have to change to keep reading it —
     * this is the schema document's own contract, independent of both versions beside it.
     */
    public static final int SCHEMA_FORMAT = 1;

    private final JsonNodeFactory json = JsonNodeFactory.instance;
    private final Map<String, ObjectNode> defsByName = new TreeMap<>();

    private DocTrees docTrees;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        try {
            this.docTrees = DocTrees.instance(env);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "The AST schema needs javac's Compiler Tree API to read javadoc, and this "
                            + "processing environment does not provide it. Run the build on a JDK.", e);
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element root : roundEnv.getRootElements()) {
            collect(root);
        }
        if (roundEnv.processingOver() && !defsByName.isEmpty()) {
            write();
        }
        return false;   // claim nothing; this processor only observes
    }

    private void collect(Element element) {
        if (element instanceof TypeElement type && inAstPackage(type)) {
            if (type.getKind() == ElementKind.RECORD) {
                defsByName.put(definitionName(type), describeRecord(type));
            } else if (type.getKind() == ElementKind.ENUM) {
                defsByName.put(definitionName(type), describeEnum(type));
            }
        }
        for (Element enclosed : element.getEnclosedElements()) {
            if (enclosed instanceof TypeElement) {
                collect(enclosed);
            }
        }
    }

    private boolean inAstPackage(TypeElement type) {
        return AST_PACKAGE.contentEquals(
                processingEnv.getElementUtils().getPackageOf(type).getQualifiedName());
    }

    /**
     * {@code FieldMetadata}, or {@code SagaMetadata.SagaTrigger} for a nested type — the
     * simple name alone collides ({@code SagaStepMetadata.FieldMapping} and its kin), and a
     * fully-qualified name would put the package in every {@code $ref}.
     */
    private static String definitionName(TypeElement type) {
        StringBuilder name = new StringBuilder(type.getSimpleName());
        for (Element e = type.getEnclosingElement(); e instanceof TypeElement outer; e = e.getEnclosingElement()) {
            name.insert(0, outer.getSimpleName() + ".");
        }
        return name.toString();
    }

    // ---- one definition --------------------------------------------------

    private ObjectNode describeRecord(TypeElement type) {
        ObjectNode node = json.objectNode();
        node.put("type", "object");
        putDoc(node, type);
        node.put("x-exeris-java-type", type.getQualifiedName().toString());

        String include = jsonInclude(type);
        if (include != null) {
            node.put("x-exeris-json-include", include);
        }

        ObjectNode properties = node.putObject("properties");
        DocCommentTree recordDoc = docTrees.getDocCommentTree(type);
        for (RecordComponentElement component : ElementFilter.recordComponentsIn(type.getEnclosedElements())) {
            if (onComponent(component, JsonIgnore.class) != null) {
                continue;
            }
            properties.set(propertyName(component), describeComponent(component, recordDoc));
        }
        // Jackson is configured to ignore unknown keys on read (@JsonIgnoreProperties), so a
        // document carrying a newer SDK's components is readable by an older one. Saying so
        // here keeps a validator from rejecting exactly the forward-compatibility this
        // format is built for.
        node.put("additionalProperties", true);
        return node;
    }

    private ObjectNode describeEnum(TypeElement type) {
        ObjectNode node = json.objectNode();
        node.put("type", "string");
        putDoc(node, type);
        node.put("x-exeris-java-type", type.getQualifiedName().toString());
        ArrayNode constants = node.putArray("enum");
        for (VariableElement constant : ElementFilter.fieldsIn(type.getEnclosedElements())) {
            if (constant.getKind() == ElementKind.ENUM_CONSTANT) {
                constants.add(constant.getSimpleName().toString());
            }
        }
        return node;
    }

    private ObjectNode describeComponent(RecordComponentElement component, DocCommentTree recordDoc) {
        ObjectNode node = typeNode(component.asType());
        putComponentDoc(node, component, recordDoc);

        String include = jsonInclude(component);
        if (include != null) {
            // Present only when the component overrides its record's posture — which is the
            // question worth answering: an overriding NON_NULL is what makes a zero survive.
            node.put("x-exeris-json-include", include);
        }
        if (component.asType().getKind().isPrimitive()) {
            node.put("x-exeris-primitive", true);
        }
        return node;
    }

    private String propertyName(RecordComponentElement component) {
        JsonProperty named = onComponent(component, JsonProperty.class);
        if (named != null && !named.value().isEmpty()) {
            return named.value();
        }
        return component.getSimpleName().toString();
    }

    private String jsonInclude(Element element) {
        JsonInclude include = element.getAnnotation(JsonInclude.class);
        return include == null ? null : include.value().name();
    }

    private String jsonInclude(RecordComponentElement component) {
        JsonInclude include = onComponent(component, JsonInclude.class);
        return include == null ? null : include.value().name();
    }

    /**
     * Reads an annotation written on a record component, wherever javac decided to put it.
     *
     * <p>This indirection is load-bearing and the reason is easy to miss. An annotation on a
     * record component is propagated to the field, the accessor and the constructor
     * parameter, and it stays on the {@link RecordComponentElement} itself <em>only</em> if
     * its {@code @Target} includes {@code RECORD_COMPONENT}. Jackson's do not — both
     * {@code @JsonInclude} and {@code @JsonProperty} target
     * {@code ANNOTATION_TYPE, METHOD, FIELD, TYPE, PARAMETER} and nothing else. So asking
     * the component directly returns {@code null} for every one of them, silently, and the
     * schema would have reported {@code FieldMetadata.min} as an ordinary {@code NON_DEFAULT}
     * property — that is, it would have described the exact bug those per-component
     * {@code NON_NULL}s exist to prevent as if it were still present.
     */
    private <A extends java.lang.annotation.Annotation> A onComponent(
            RecordComponentElement component, Class<A> type) {
        A own = component.getAnnotation(type);
        if (own != null) {
            return own;
        }
        ExecutableElement accessor = component.getAccessor();
        if (accessor != null) {
            A viaAccessor = accessor.getAnnotation(type);
            if (viaAccessor != null) {
                return viaAccessor;
            }
        }
        for (VariableElement field : ElementFilter.fieldsIn(
                component.getEnclosingElement().getEnclosedElements())) {
            if (field.getSimpleName().contentEquals(component.getSimpleName())) {
                return field.getAnnotation(type);
            }
        }
        return null;
    }

    // ---- types -----------------------------------------------------------

    private ObjectNode typeNode(TypeMirror type) {
        ObjectNode node = json.objectNode();
        switch (type.getKind()) {
            case BOOLEAN -> node.put("type", "boolean");
            case INT, LONG, SHORT, BYTE, CHAR -> node.put("type", "integer");
            case FLOAT, DOUBLE -> node.put("type", "number");
            case DECLARED -> declaredTypeNode(node, (DeclaredType) type);
            default -> node.put("x-exeris-unmapped", type.toString());
        }
        if (type.getKind() != TypeKind.DECLARED) {
            node.put("x-exeris-java-type", type.toString());
        }
        return node;
    }

    private void declaredTypeNode(ObjectNode node, DeclaredType declared) {
        TypeElement element = (TypeElement) declared.asElement();
        String qualified = element.getQualifiedName().toString();

        if (inAstPackage(element)) {
            node.put("$ref", "#/$defs/" + definitionName(element));
            return;
        }
        switch (qualified) {
            case "java.lang.String" -> node.put("type", "string");
            case "java.lang.Boolean" -> node.put("type", "boolean");
            case "java.lang.Integer", "java.lang.Long", "java.lang.Short", "java.lang.Byte" ->
                    node.put("type", "integer");
            case "java.lang.Double", "java.lang.Float" -> node.put("type", "number");
            case "java.util.List", "java.util.Set", "java.util.Collection" -> {
                node.put("type", "array");
                List<? extends TypeMirror> args = declared.getTypeArguments();
                node.set("items", args.isEmpty() ? json.objectNode() : typeNode(args.get(0)));
            }
            case "java.util.Map" -> {
                node.put("type", "object");
                List<? extends TypeMirror> args = declared.getTypeArguments();
                node.set("additionalProperties", args.size() < 2 ? json.booleanNode(true) : typeNode(args.get(1)));
            }
            // Loud rather than silently typed as a string: an unmapped type reaching the
            // schema is a gap in this switch, and AstSchemaContractTest fails on the key.
            default -> node.put("x-exeris-unmapped", qualified);
        }
        node.put("x-exeris-java-type", qualified);
    }

    // ---- prose -----------------------------------------------------------

    private void putDoc(ObjectNode node, Element element) {
        DocCommentTree doc = docTrees.getDocCommentTree(element);
        if (doc == null) {
            return;
        }
        String summary = JavadocText.oneLine(doc.getFirstSentence());
        if (!summary.isEmpty()) {
            node.put("description", summary);
        }
        String full = JavadocText.prose(doc.getFullBody());
        if (!full.isEmpty() && !full.equals(summary)) {
            node.put("x-exeris-notes", full);
        }
    }

    /**
     * Component prose, from the record's {@code @param} tags — the only route that works.
     *
     * <p><b>Measured, because four plausible routes fail silently.</b> Most components in
     * this package are documented with a comment written above them inside the record
     * header. Javadoc-the-tool renders those; annotation processing cannot reach them.
     * {@code Elements.getDocComment(RecordComponentElement)} returns {@code null};
     * {@code DocTrees.getDocCommentTree(Element)} returns {@code null}; {@code
     * DocTrees.getPath} on the component returns {@code null}, so there is no path for the
     * tree-based overload either; and building a path to the {@link
     * javax.lang.model.element.RecordComponentElement}'s {@code VariableTree} member off the
     * record's own (resolvable) path returns {@code null} as well. The accessor and backing
     * field are both javac-derived and carry no source position, so neither helps.
     *
     * <p>{@code @param} on the record does resolve, and is the canonical Java way to
     * document a record component. It is also, today, used by <em>no</em> record in this
     * package — so property-level {@code description} is near-empty and the document says so
     * at its root rather than letting a consumer infer that the properties are meaningless.
     * Converting the existing header prose is a separate decision: much of it is
     * multi-paragraph and a {@code @param} tag is a phrase.
     */
    private void putComponentDoc(ObjectNode node, RecordComponentElement component, DocCommentTree recordDoc) {
        if (recordDoc == null) {
            return;
        }
        for (DocTree tag : recordDoc.getBlockTags()) {
            if (tag instanceof ParamTree param
                    && param.getName().getName().contentEquals(component.getSimpleName())) {
                String prose = JavadocText.prose(param.getDescription());
                if (!prose.isEmpty()) {
                    node.put("description", prose);
                }
                return;
            }
        }
    }

    // ---- output ----------------------------------------------------------

    private void write() {
        String sdkVersion = processingEnv.getOptions().get(OPT_SDK_VERSION);
        String astVersion = processingEnv.getOptions().get(OPT_AST_VERSION);
        if (sdkVersion == null || sdkVersion.isBlank() || astVersion == null || astVersion.isBlank()) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "-A" + OPT_SDK_VERSION + " and -A" + OPT_AST_VERSION + " must both be set, so the "
                            + "schema can say which SDK it came from and which wire shape it describes. "
                            + "Pass them from the build.");
            return;
        }

        ObjectNode root = json.objectNode();
        root.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        root.put("$id", "https://exeris.eu/schema/sdk/ast/" + astVersion + ".json");
        root.put("title", "Exeris SDK AST — the build-time metadata hand-off format");
        root.put("description",
                "The shape of exeris-metadata/<entity>.json, the document exeris-tooling's processor "
                        + "writes and every generator reads. Generated from the "
                        + AST_PACKAGE + " sources; see x-exeris-reader-requirements before reading one.");
        root.put("schemaFormat", SCHEMA_FORMAT);
        root.put("sdkVersion", sdkVersion);
        root.put("astSchemaVersion", astVersion);
        root.put("definitionCount", defsByName.size());
        root.set("x-exeris-prose-coverage", proseCoverage());
        root.set("x-exeris-reader-requirements", readerRequirements());
        root.put("$ref", "#/$defs/" + ROOT_DEFINITION);

        ObjectNode defs = root.putObject("$defs");
        defsByName.forEach(defs::set);   // sorted by name: reproducible

        try (Writer out = processingEnv.getFiler()
                .createResource(StandardLocation.CLASS_OUTPUT, "", RESOURCE_PATH)
                .openWriter()) {
            out.write(root.toPrettyString());
            out.write('\n');
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Could not write " + RESOURCE_PATH + ": " + e);
        }
    }

    /**
     * How much of this document carries prose, stated rather than left to be discovered.
     *
     * <p>A consumer that finds most {@code description} keys missing needs to know whether
     * it received a truncated schema or an accurate one. Definitions are well documented;
     * properties are not, for the reason {@link #putComponentDoc} records. Counting it here
     * makes the gap a measured fact with a number attached, and makes any improvement
     * visible in a diff.
     */
    private ObjectNode proseCoverage() {
        int definitions = defsByName.size();
        int describedDefinitions = 0;
        int properties = 0;
        int describedProperties = 0;
        for (ObjectNode def : defsByName.values()) {
            if (def.has("description")) {
                describedDefinitions++;
            }
            if (def.get("properties") instanceof ObjectNode props) {
                for (var entry : props.properties()) {
                    properties++;
                    if (entry.getValue().has("description")) {
                        describedProperties++;
                    }
                }
            }
        }
        ObjectNode node = json.objectNode();
        node.put("definitions", definitions);
        node.put("describedDefinitions", describedDefinitions);
        node.put("properties", properties);
        node.put("describedProperties", describedProperties);
        node.put("note",
                "Property prose comes from @param tags on the record. Comments written above a "
                        + "component inside the record header render in javadoc but are unreachable from "
                        + "annotation processing, so they do not appear here. A low describedProperties "
                        + "count is an accurate schema, not a truncated one.");
        return node;
    }

    /**
     * The two postures a reader must adopt, carried in the document rather than left as
     * folklore. Both have cost real debugging time in this ecosystem, and neither is
     * inferable from the property types.
     */
    private ArrayNode readerRequirements() {
        ArrayNode requirements = json.arrayNode();

        ObjectNode primitives = requirements.addObject();
        primitives.put("id", "FAIL_ON_NULL_FOR_PRIMITIVES");
        primitives.put("statement",
                "A Jackson reader of this format must set FAIL_ON_NULL_FOR_PRIMITIVES to false.");
        primitives.put("why",
                "Jackson 3 defaults it to true. Records here use primitive boolean/int components "
                        + "under class-level NON_DEFAULT, so a default-valued component is absent from the "
                        + "document and reaches the constructor as null. The properties this applies to are "
                        + "flagged x-exeris-primitive.");

        ObjectNode zeroes = requirements.addObject();
        zeroes.put("id", "NON_DEFAULT_DROPS_BOXED_ZERO");
        zeroes.put("statement",
                "Under x-exeris-json-include NON_DEFAULT a boxed zero is treated as empty and omitted, "
                        + "so a meaningful zero is carried only where a property declares its own NON_NULL.");
        zeroes.put("why",
                "This is why FieldMetadata's bounds (min/max/minLength/maxLength) carry per-property "
                        + "NON_NULL: without it a min = 0 non-negativity floor vanished on the wire. Read a "
                        + "property's own x-exeris-json-include, where present, in preference to its "
                        + "definition's. Measured: an ordinal-0 enum constant is NOT dropped by NON_DEFAULT — "
                        + "only boxed numeric zero is.");

        return requirements;
    }
}
