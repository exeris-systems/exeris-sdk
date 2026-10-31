package eu.exeris.sdk.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.ParamTree;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.DocTrees;
import com.sun.source.util.JavacTask;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Emits {@value #RESOURCE_PATH} — the JSON Schema of the pre-construction brief (ADR-106) — into
 * the jar of the module that declares the brief records.
 *
 * <p>The records in {@code eu.exeris.sdk.brief} are the contract; this schema is how a consumer
 * that does not compile against them (an agent bridge, a validator in an application's CI) reads
 * it. It differs from {@link AstSchemaProcessor}'s output on purpose:
 * <ul>
 *   <li><b>Closed objects.</b> Every object is {@code additionalProperties: false}. The AST is
 *       open for forward compatibility; the brief is closed because the property it does not
 *       name is how a re-encoded source structure (a field list, an action signature) would get
 *       in, and ADR-003 keeps the annotated class the only place those live.</li>
 *   <li><b>Constraints.</b> {@code required}, {@code minItems}, patterns and the per-kind EARS and
 *       open-question rules come from the package-private {@code SchemaRules} annotations on the
 *       records, read here by name. The readiness gate needs them; the AST has no such gate.</li>
 *   <li><b>Only what {@code Brief} reaches.</b> Definitions are the types reachable from the
 *       root through record components, so a helper type in the package (the readiness verdict)
 *       never becomes wire format by being there, and an enum reused from the AST
 *       ({@code DataScope}) is described where the brief uses it.</li>
 * </ul>
 *
 * <p><b>The concern-hint vocabulary</b> is the set of annotation names a glossary term may hint
 * at. It is read from the annotation <em>sources</em> — the input
 * {@link AnnotationCatalogProcessor} reads, not its output — so the two processors do not depend
 * on each other or on the order the modules build in. The selection: top-level annotation types,
 * less those deprecated for removal (a hint should not name what is on its way out) and less
 * repeatable containers (an author writes the repeated annotation, not its container).
 *
 * <p><b>No timestamp, no path</b>: the output is a pure function of the sources and the options,
 * so two builds of one commit produce identical bytes.
 */
@SupportedAnnotationTypes("*")
@SupportedOptions({
        BriefSchemaProcessor.OPT_SDK_VERSION,
        BriefSchemaProcessor.OPT_FORMAT,
        BriefSchemaProcessor.OPT_ANNOTATION_SOURCES})
public final class BriefSchemaProcessor extends AbstractProcessor {

    /** Where the schema lands inside the processed module's jar. */
    public static final String RESOURCE_PATH = "META-INF/exeris/brief-schema.json";

    /** Set by the build to {@code ${project.version}}; shared with {@link AstSchemaProcessor}. */
    public static final String OPT_SDK_VERSION = AstSchemaProcessor.OPT_SDK_VERSION;

    /**
     * Set by the build to the brief wire-format integer. A consumer pins to this number rather
     * than to the SDK version.
     */
    public static final String OPT_FORMAT = "exeris.brief.schemaFormat";

    /** Set by the build to the source root of {@code exeris-sdk-annotations}. */
    public static final String OPT_ANNOTATION_SOURCES = "exeris.brief.annotationSources";

    /** The package whose records are the brief. */
    public static final String BRIEF_PACKAGE = "eu.exeris.sdk.brief";

    /** The document's root type. */
    public static final String ROOT_TYPE = BRIEF_PACKAGE + ".Brief";

    /**
     * Where the concern-hint vocabulary lands as plain text, one name per line, sorted: the same
     * names as the {@value #CONCERN_HINT_DEFINITION} enum, for a reader with no JSON parser.
     */
    static final String HINTS_RESOURCE_PATH = "META-INF/exeris/concern-hints.txt";

    /** The definition the concern-hint vocabulary is published under. */
    public static final String CONCERN_HINT_DEFINITION = "ConcernHint";

    private static final String RULES = BRIEF_PACKAGE + ".SchemaRules.";
    private static final String KEY_JAVA_TYPE = "x-exeris-java-type";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_PROPERTIES = "properties";
    private static final String DEFS = "#/$defs/";
    private static final String KEY_REQUIRED = "required";
    private static final String KEY_PATTERN = "pattern";
    private static final String KEY_TYPE = "type";
    private static final String TYPE_STRING = "string";
    private static final String TYPE_BOOLEAN = "boolean";
    private static final String ELEMENT_VALUE = "value";

    private static final JsonNodeFactory json = JsonNodeFactory.instance;
    private final Map<String, ObjectNode> defsByName = new TreeMap<>();

    private DocTrees docTrees;
    private boolean briefSeen;

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
                    "The brief schema needs javac's Compiler Tree API to read javadoc, and this "
                            + "processing environment does not provide it. Run the build on a JDK.", e);
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element root : roundEnv.getRootElements()) {
            if (root instanceof TypeElement type && ROOT_TYPE.contentEquals(type.getQualifiedName())) {
                briefSeen = true;
            }
        }
        if (roundEnv.processingOver() && briefSeen) {
            write();
        }
        return false;   // claim nothing; this processor only observes
    }

    // ---- the vocabulary --------------------------------------------------

    /**
     * The concern-hint names, read by parsing the annotation sources. Parsing only: nothing is
     * attributed or compiled, so the result depends on the files and nothing else.
     */
    private Set<String> concernVocabulary(Path sources) throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(sources)) {
            files = walk.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().equals("package-info.java"))
                    .sorted()
                    .toList();
        }
        if (files.isEmpty()) {
            return Set.of();   // javac refuses a task with no sources; the caller reports the empty set
        }
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Set<String> annotations = new TreeSet<>();
        Set<String> containers = new TreeSet<>();
        try (StandardJavaFileManager fileManager = javac.getStandardFileManager(diagnostics, null, null)) {
            JavacTask task = (JavacTask) javac.getTask(null, fileManager, diagnostics,
                    List.of("-proc:none"), null, fileManager.getJavaFileObjectsFromPaths(files));
            for (CompilationUnitTree unit : task.parse()) {
                for (Tree declaration : unit.getTypeDecls()) {
                    if (declaration instanceof ClassTree type
                            && declaration.getKind() == Tree.Kind.ANNOTATION_TYPE) {
                        readAnnotationType(type, annotations, containers);
                    }
                }
            }
        }
        annotations.removeAll(containers);
        return annotations;
    }

    private static void readAnnotationType(ClassTree type, Set<String> annotations, Set<String> containers) {
        boolean leaving = false;
        for (AnnotationTree annotation : type.getModifiers().getAnnotations()) {
            String name = lastSegment(annotation.getAnnotationType().toString());
            if (name.equals("Deprecated")) {
                leaving = forRemoval(annotation);
            } else if (name.equals("Repeatable") && !annotation.getArguments().isEmpty()) {
                ExpressionTree value = annotation.getArguments().getFirst();
                if (value instanceof AssignmentTree assignment) {
                    value = assignment.getExpression();
                }
                if (value instanceof MemberSelectTree classLiteral) {
                    containers.add(lastSegment(classLiteral.getExpression().toString()));
                }
            }
        }
        if (!leaving) {
            annotations.add(type.getSimpleName().toString());
        }
    }

    private static boolean forRemoval(AnnotationTree deprecated) {
        for (ExpressionTree argument : deprecated.getArguments()) {
            if (argument instanceof AssignmentTree assignment
                    && assignment.getVariable().toString().equals("forRemoval")
                    && assignment.getExpression().toString().equals("true")) {
                return true;
            }
        }
        return false;
    }

    private static String lastSegment(String name) {
        return name.substring(name.lastIndexOf('.') + 1);
    }

    // ---- definitions -----------------------------------------------------

    /** Adds the definition of {@code type}, and of what it reaches, unless it is already there. */
    private void define(TypeElement type) {
        String name = definitionName(type);
        if (defsByName.containsKey(name)) {
            return;
        }
        if (type.getKind() == ElementKind.ENUM) {
            defsByName.put(name, describeEnum(type));
        } else {
            ObjectNode node = json.objectNode();
            defsByName.put(name, node);   // placed first, so a cycle terminates
            describeRecord(type, node);
        }
    }

    private static String definitionName(TypeElement type) {
        StringBuilder name = new StringBuilder(type.getSimpleName());
        for (Element e = type.getEnclosingElement(); e instanceof TypeElement outer; e = e.getEnclosingElement()) {
            name.insert(0, outer.getSimpleName() + ".");
        }
        return name.toString();
    }

    private void describeRecord(TypeElement type, ObjectNode node) {
        node.put(KEY_TYPE, "object");
        putDoc(node, type);
        node.put(KEY_JAVA_TYPE, type.getQualifiedName().toString());

        ObjectNode properties = node.putObject(KEY_PROPERTIES);
        ArrayNode required = json.arrayNode();
        DocCommentTree recordDoc = docTrees.getDocCommentTree(type);
        for (RecordComponentElement component : ElementFilter.recordComponentsIn(type.getEnclosedElements())) {
            String name = propertyName(component);
            properties.set(name, describeComponent(component, recordDoc));
            if (rule(component, "Required") != null || rule(component, "MinItems") != null) {
                required.add(name);
            }
        }
        if (!required.isEmpty()) {
            node.set(KEY_REQUIRED, required);
        }
        node.put("additionalProperties", false);

        ArrayNode conditions = json.arrayNode();
        for (AnnotationMirror when : whenRules(type)) {
            conditions.add(condition(when));
        }
        if (!conditions.isEmpty()) {
            node.set("allOf", conditions);
        }
    }

    private ObjectNode describeEnum(TypeElement type) {
        ObjectNode node = json.objectNode();
        node.put(KEY_TYPE, TYPE_STRING);
        putDoc(node, type);
        node.put(KEY_JAVA_TYPE, type.getQualifiedName().toString());
        ArrayNode constants = node.putArray("enum");
        for (VariableElement constant : ElementFilter.fieldsIn(type.getEnclosedElements())) {
            if (constant.getKind() == ElementKind.ENUM_CONSTANT) {
                JsonProperty named = constant.getAnnotation(JsonProperty.class);
                constants.add(named != null && !named.value().isEmpty()
                        ? named.value() : constant.getSimpleName().toString());
            }
        }
        return node;
    }

    private ObjectNode describeComponent(RecordComponentElement component, DocCommentTree recordDoc) {
        TypeMirror type = component.asType();
        ObjectNode node;
        if (isList(type)) {
            node = json.objectNode();
            node.put(KEY_TYPE, "array");
            node.set("items", itemsNode(component, ((DeclaredType) type).getTypeArguments().getFirst()));
            AnnotationMirror minItems = rule(component, "MinItems");
            if (minItems != null) {
                node.put("minItems", (Integer) value(minItems, ELEMENT_VALUE));
            }
            node.put(KEY_JAVA_TYPE, "java.util.List");
        } else {
            node = typeNode(type);
            applyStringRules(node, component);
        }
        putComponentDoc(node, component, recordDoc);
        return node;
    }

    private ObjectNode itemsNode(RecordComponentElement component, TypeMirror element) {
        // The vocabulary's definition is named after the rule annotation that requests it.
        if (rule(component, CONCERN_HINT_DEFINITION) != null) {
            ObjectNode ref = json.objectNode();
            ref.put("$ref", DEFS + CONCERN_HINT_DEFINITION);
            return ref;
        }
        ObjectNode items = typeNode(element);
        applyStringRules(items, component);
        AnnotationMirror kinds = rule(component, "Kinds");
        if (kinds != null) {
            // The item keeps its own definition and is narrowed beside it. The narrowing schema
            // has no KEY_TYPE, so it does not restate the object and carries no
            // additionalProperties of its own: the referenced definition's closure governs.
            ObjectNode narrowed = json.objectNode();
            ArrayNode allOf = narrowed.putArray("allOf");
            allOf.add(items);
            ObjectNode kindOnly = allOf.addObject();
            ArrayNode admitted = kindOnly.putObject(KEY_PROPERTIES).putObject("kind").putArray("enum");
            strings(kinds, ELEMENT_VALUE).forEach(admitted::add);
            return narrowed;
        }
        return items;
    }

    private void applyStringRules(ObjectNode node, RecordComponentElement component) {
        if (rule(component, "NonBlank") != null) {
            node.put("minLength", 1);
            node.put(KEY_PATTERN, "\\S");
        }
        AnnotationMirror pattern = rule(component, "Pattern");
        if (pattern != null) {
            node.put(KEY_PATTERN, (String) value(pattern, ELEMENT_VALUE));
        }
    }

    private ObjectNode condition(AnnotationMirror when) {
        ObjectNode entry = json.objectNode();
        String property = (String) value(when, "property");
        List<String> values = strings(when, "values");

        ObjectNode ifNode = entry.putObject("if");
        ObjectNode discriminator = ifNode.putObject(KEY_PROPERTIES).putObject(property);
        if (values.size() == 1) {
            discriminator.put("const", values.getFirst());
        } else {
            ArrayNode admitted = discriminator.putArray("enum");
            values.forEach(admitted::add);
        }
        ifNode.putArray(KEY_REQUIRED).add(property);

        ObjectNode then = entry.putObject("then");
        List<String> require = strings(when, "require");
        if (!require.isEmpty()) {
            ArrayNode required = then.putArray(KEY_REQUIRED);
            require.forEach(required::add);
        }
        List<String> forbid = strings(when, "forbid");
        String patternProperty = (String) value(when, "patternProperty");
        if (!forbid.isEmpty() || !patternProperty.isEmpty()) {
            ObjectNode properties = then.putObject(KEY_PROPERTIES);
            forbid.forEach(name -> properties.put(name, false));
            if (!patternProperty.isEmpty()) {
                properties.putObject(patternProperty).put(KEY_PATTERN, (String) value(when, KEY_PATTERN));
            }
        }
        return entry;
    }

    // ---- types -----------------------------------------------------------

    private ObjectNode typeNode(TypeMirror type) {
        ObjectNode node = json.objectNode();
        if (type.getKind() == TypeKind.BOOLEAN) {
            node.put(KEY_TYPE, TYPE_BOOLEAN);
            node.put(KEY_JAVA_TYPE, TYPE_BOOLEAN);
            return node;
        }
        if (type.getKind() != TypeKind.DECLARED) {
            node.put("x-exeris-unmapped", type.toString());
            return node;
        }
        TypeElement element = (TypeElement) ((DeclaredType) type).asElement();
        String qualified = element.getQualifiedName().toString();
        if (element.getKind() == ElementKind.RECORD || element.getKind() == ElementKind.ENUM) {
            define(element);
            node.put("$ref", DEFS + definitionName(element));
            return node;
        }
        switch (qualified) {
            case "java.lang.String" -> node.put(KEY_TYPE, TYPE_STRING);
            case "java.lang.Boolean" -> node.put(KEY_TYPE, TYPE_BOOLEAN);
            // Loud rather than silently typed: BriefSchemaContractTest fails on the key.
            default -> node.put("x-exeris-unmapped", qualified);
        }
        node.put(KEY_JAVA_TYPE, qualified);
        return node;
    }

    private static boolean isList(TypeMirror type) {
        return type instanceof DeclaredType declared
                && ((TypeElement) declared.asElement()).getQualifiedName().contentEquals("java.util.List")
                && declared.getTypeArguments().size() == 1;
    }

    // ---- reading the rules -----------------------------------------------

    private static AnnotationMirror rule(Element element, String simpleName) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            TypeElement type = (TypeElement) mirror.getAnnotationType().asElement();
            if (type.getQualifiedName().contentEquals(RULES + simpleName)) {
                return mirror;
            }
        }
        return null;
    }

    private List<AnnotationMirror> whenRules(TypeElement type) {
        List<AnnotationMirror> out = new ArrayList<>();
        AnnotationMirror single = rule(type, "When");
        if (single != null) {
            out.add(single);
        }
        AnnotationMirror container = rule(type, "WhenAll");
        if (container != null) {
            for (AnnotationValue each : list(value(container, ELEMENT_VALUE))) {
                out.add((AnnotationMirror) each.getValue());
            }
        }
        return out;
    }

    private Object value(AnnotationMirror mirror, String name) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                : processingEnv.getElementUtils().getElementValuesWithDefaults(mirror).entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals(name)) {
                return entry.getValue().getValue();
            }
        }
        throw new IllegalStateException(mirror + " has no element " + name);
    }

    private List<String> strings(AnnotationMirror mirror, String name) {
        List<String> out = new ArrayList<>();
        for (AnnotationValue each : list(value(mirror, name))) {
            out.add((String) each.getValue());
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<? extends AnnotationValue> list(Object arrayValue) {
        return (List<? extends AnnotationValue>) arrayValue;
    }

    private static String propertyName(RecordComponentElement component) {
        ExecutableElement accessor = component.getAccessor();
        JsonProperty named = accessor == null ? null : accessor.getAnnotation(JsonProperty.class);
        if (named == null) {
            named = component.getAnnotation(JsonProperty.class);
        }
        return named != null && !named.value().isEmpty() ? named.value() : component.getSimpleName().toString();
    }

    // ---- prose -----------------------------------------------------------

    private void putDoc(ObjectNode node, Element element) {
        DocCommentTree doc = docTrees.getDocCommentTree(element);
        if (doc == null) {
            return;
        }
        String summary = JavadocText.oneLine(doc.getFirstSentence());
        if (!summary.isEmpty()) {
            node.put(KEY_DESCRIPTION, summary);
        }
        String full = JavadocText.prose(doc.getFullBody());
        if (!full.isEmpty() && !full.equals(summary)) {
            node.put("x-exeris-notes", full);
        }
    }

    /** Component prose, from the record's {@code @param} tags, as {@link AstSchemaProcessor} reads it. */
    private static void putComponentDoc(ObjectNode node, RecordComponentElement component, DocCommentTree recordDoc) {
        if (recordDoc == null) {
            return;
        }
        for (DocTree tag : recordDoc.getBlockTags()) {
            if (tag instanceof ParamTree param
                    && param.getName().getName().contentEquals(component.getSimpleName())) {
                String prose = JavadocText.prose(param.getDescription());
                if (!prose.isEmpty()) {
                    node.put(KEY_DESCRIPTION, prose);
                }
                return;
            }
        }
    }

    // ---- output ----------------------------------------------------------

    private void write() {
        Map<String, String> options = processingEnv.getOptions();
        String sdkVersion = options.get(OPT_SDK_VERSION);
        String format = options.get(OPT_FORMAT);
        String sources = options.get(OPT_ANNOTATION_SOURCES);
        if (blank(sdkVersion) || blank(format) || blank(sources)) {
            error("-A" + OPT_SDK_VERSION + ", -A" + OPT_FORMAT + " and -A" + OPT_ANNOTATION_SOURCES
                    + " must all be set, so the brief schema can say which SDK it came from, which "
                    + "wire format it describes, and which annotations a concern hint may name. "
                    + "Pass them from the build.");
            return;
        }
        int formatNumber;
        try {
            formatNumber = Integer.parseInt(format.strip());
        } catch (NumberFormatException _) {
            error("-A" + OPT_FORMAT + " must be an integer, was '" + format + "'");
            return;
        }
        Path sourceRoot = Path.of(sources);
        if (!Files.isDirectory(sourceRoot)) {
            error("-A" + OPT_ANNOTATION_SOURCES + " names no directory: " + sources);
            return;
        }
        Set<String> vocabulary;
        try {
            vocabulary = concernVocabulary(sourceRoot);
        } catch (IOException e) {
            error("Could not read the annotation sources under " + sources + ": " + e);
            return;
        }
        if (vocabulary.isEmpty()) {
            error("No annotation type was found under " + sources + "; the concern-hint vocabulary "
                    + "would be empty, so no glossary term could carry a hint.");
            return;
        }

        TypeElement root = processingEnv.getElementUtils().getTypeElement(ROOT_TYPE);
        define(root);
        String rootName = definitionName(root);
        defsByName.put(CONCERN_HINT_DEFINITION, concernHintDefinition(vocabulary));

        ObjectNode document = json.objectNode();
        document.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        document.put("$id", "https://exeris.eu/schema/sdk/brief/" + formatNumber + ".json");
        document.put("title", "Exeris SDK pre-construction brief (ADR-106)");
        document.put(KEY_DESCRIPTION,
                "The shape of docs/briefs/<area>.brief.yaml in an application project: what one bounded "
                        + "area is for, before or beside its @ExerisDomain source. It references the source "
                        + "model and never re-encodes it, so every object is closed. Generated from the "
                        + BRIEF_PACKAGE + " sources.");
        document.put("briefSchemaFormat", formatNumber);
        document.put("sdkVersion", sdkVersion);
        document.put("definitionCount", defsByName.size());
        document.set("x-exeris-reader-requirements", readerRequirements());
        document.put("$ref", DEFS + rootName);
        ObjectNode defs = document.putObject("$defs");
        defsByName.forEach(defs::set);   // sorted by name: reproducible

        try (Writer out = processingEnv.getFiler()
                .createResource(StandardLocation.CLASS_OUTPUT, "", RESOURCE_PATH)
                .openWriter()) {
            out.write(document.toPrettyString());
            out.write('\n');
        } catch (IOException e) {
            error("Could not write " + RESOURCE_PATH + ": " + e);
        }
        try (Writer out = processingEnv.getFiler()
                .createResource(StandardLocation.CLASS_OUTPUT, "", HINTS_RESOURCE_PATH)
                .openWriter()) {
            for (String name : new TreeSet<>(vocabulary)) {
                out.write(name);
                out.write('\n');
            }
        } catch (IOException e) {
            error("Could not write " + HINTS_RESOURCE_PATH + ": " + e);
        }
    }

    private static ObjectNode concernHintDefinition(Set<String> vocabulary) {
        ObjectNode node = json.objectNode();
        node.put(KEY_TYPE, TYPE_STRING);
        node.put(KEY_DESCRIPTION, "The simple name of an annotation in the SDK catalog of the same release.");
        node.put("x-exeris-notes",
                "Top-level annotation types of exeris-sdk-annotations, less those deprecated for removal and "
                        + "less repeatable containers. A hint names the annotation and never its attributes.");
        ArrayNode names = node.putArray("enum");
        vocabulary.forEach(names::add);
        return node;
    }

    private static ArrayNode readerRequirements() {
        ArrayNode requirements = json.arrayNode();
        ObjectNode unknown = requirements.addObject();
        unknown.put("id", "UNKNOWN_PROPERTIES_ARE_ERRORS");
        unknown.put("statement",
                "Validate a brief against this schema, or bind it with unknown properties failing, before "
                        + "acting on it.");
        unknown.put("why",
                "A property this schema does not name is how a re-encoded source structure (a field list, "
                        + "an action signature, a saga step) would enter a brief. A Jackson 3 reader ignores "
                        + "unknown properties by default, so binding alone would drop such a property silently "
                        + "and the brief would look valid.");
        ObjectNode questionIds = requirements.addObject();
        questionIds.put("id", "QUESTION_IDS_ARE_UNIQUE");
        questionIds.put("statement",
                "Treat a brief in which two open questions share an id as invalid, at the later one's id.");
        questionIds.put("why",
                "An answer, an assumption and its owner are recorded against a question by its id, so a "
                        + "repeated id makes it ambiguous which question they settle. JSON Schema cannot compare "
                        + "values across array items.");
        ObjectNode actors = requirements.addObject();
        actors.put("id", "WORKFLOW_ACTORS_ARE_DECLARED");
        actors.put("statement",
                "Treat a brief in which a workflow names an actor that no entry of actors declares by name "
                        + "as invalid, at that workflow actor.");
        actors.put("why",
                "A workflow names its actors by Actor name; a name with no declaration is a participant "
                        + "the brief never describes. JSON Schema cannot resolve a value against another array.");
        return requirements;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private void error(String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message);
    }
}
