package eu.exeris.sdk.catalog;

import com.sun.source.doctree.DeprecatedTree;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
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
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Emits {@value #RESOURCE_PATH} — the machine-readable description of the SDK annotation
 * surface — into the jar of whatever module it processes.
 *
 * <p><b>Why a processor and not reflection.</b> A catalog assembled by walking the
 * compiled classes recovers names, types, defaults, {@code @Target}, {@code @Retention}
 * and {@code @Deprecated}, and then stops short of the two things that make it worth
 * serving. It cannot recover the <em>prose</em>: a class file carries no javadoc, at any
 * retention — the annotations being {@code @Retention(SOURCE)} is a true fact standing
 * next to that reason rather than being it. And it cannot recover a deprecation's
 * canonical replacement, which lives in {@code @deprecated} prose and not in
 * {@code @Deprecated}, whose only elements are {@code since} and {@code forRemoval}.
 *
 * <p><b>Why it descends into nested types.</b> A classpath package walk sees top-level
 * types only, and roughly a quarter of this SDK's declaration sites are nested —
 * {@code @DomainEvent.Header}, {@code @SagaStep.InputMapping}, {@code @Saga.SagaTrigger}
 * and their kin are things an author writes. A catalog missing them is worse than no
 * catalog, because a consumer cannot tell which half it received.
 *
 * <p><b>No timestamp.</b> The output is a pure function of the sources and the version
 * option, so two builds of the same commit produce byte-identical bytes. A
 * {@code generatedAt} field would be the one thing making the jar unreproducible, in
 * exchange for a fact the release that carries it already states.
 */
@SupportedAnnotationTypes("*")
@SupportedOptions(AnnotationCatalogProcessor.OPT_SDK_VERSION)
public final class AnnotationCatalogProcessor extends AbstractProcessor {

    /** Where the catalog lands inside the processed module's jar. */
    public static final String RESOURCE_PATH = "META-INF/exeris/annotation-catalog.json";

    /** Set by the build to {@code ${project.version}}; a catalog with no version is refused. */
    public static final String OPT_SDK_VERSION = "exeris.catalog.sdkVersion";

    /**
     * The shape of this file. Bump when a consumer would have to change to keep reading
     * it — this is the catalog's own contract, independent of the SDK version beside it.
     */
    public static final int CATALOG_FORMAT = 1;

    private final JsonNodeFactory json = JsonNodeFactory.instance;
    private final Map<String, ObjectNode> annotationsByName = new TreeMap<>();
    private final Set<String> packageNames = new TreeSet<>();

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
            // Deliberately fatal rather than degrading to a prose-less catalog. The prose
            // is the reason this processor exists, and a silently reduced catalog would
            // be indistinguishable downstream from a complete one.
            throw new IllegalStateException(
                    "The annotation catalog needs javac's Compiler Tree API to read javadoc, and "
                            + "this processing environment does not provide it. Run the build on a JDK.", e);
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element root : roundEnv.getRootElements()) {
            collect(root);
        }
        if (roundEnv.processingOver()) {
            write();
        }
        return false;   // claim nothing; this processor only observes
    }

    private void collect(Element element) {
        if (element.getKind() == ElementKind.ANNOTATION_TYPE) {
            TypeElement type = (TypeElement) element;
            annotationsByName.put(type.getQualifiedName().toString(), describe(type));
            packageNames.add(processingEnv.getElementUtils().getPackageOf(type).getQualifiedName().toString());
        }
        for (Element enclosed : element.getEnclosedElements()) {
            if (enclosed instanceof TypeElement) {
                collect(enclosed);
            }
        }
    }

    // ---- one annotation --------------------------------------------------

    private ObjectNode describe(TypeElement type) {
        ObjectNode node = json.objectNode();
        node.put("name", type.getSimpleName().toString());
        node.put("qualifiedName", type.getQualifiedName().toString());
        node.put("package", processingEnv.getElementUtils().getPackageOf(type).getQualifiedName().toString());

        Element enclosing = type.getEnclosingElement();
        boolean nested = enclosing.getKind() != ElementKind.PACKAGE;
        node.put("nested", nested);
        if (nested) {
            node.put("declaredIn", ((TypeElement) enclosing).getQualifiedName().toString());
        }

        // An absent @Target and @Target({}) are different facts and must read differently:
        // the first is an annotation usable anywhere, the second one usable ONLY as a member
        // value of another annotation. Omitting the key for the first keeps them apart.
        Target target = type.getAnnotation(Target.class);
        if (target != null) {
            ArrayNode targets = node.putArray("targets");
            for (java.lang.annotation.ElementType each : target.value()) {
                targets.add(each.name());
            }
            node.put("memberValueOnly", target.value().length == 0);
        }

        Retention retention = type.getAnnotation(Retention.class);
        if (retention != null) {
            node.put("retention", retention.value().name());
        }

        String container = repeatableContainer(type);
        if (container != null) {
            node.put("repeatableContainer", container);
        }

        putDeprecation(node, type);
        putDoc(node, type);

        ArrayNode attributes = node.putArray("attributes");
        for (ExecutableElement attribute : ElementFilter.methodsIn(type.getEnclosedElements())) {
            attributes.add(describeAttribute(attribute));
        }
        return node;
    }

    /**
     * Read through the mirror rather than {@code getAnnotation(Repeatable.class).value()},
     * whose {@code Class}-valued element throws {@link javax.lang.model.type.MirroredTypeException}
     * during annotation processing — the class it names may not exist yet.
     */
    private String repeatableContainer(TypeElement type) {
        for (AnnotationMirror mirror : type.getAnnotationMirrors()) {
            TypeElement mirrorType = (TypeElement) mirror.getAnnotationType().asElement();
            if (!Repeatable.class.getName().contentEquals(mirrorType.getQualifiedName())) {
                continue;
            }
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                    : mirror.getElementValues().entrySet()) {
                if ("value".contentEquals(entry.getKey().getSimpleName())) {
                    return entry.getValue().getValue().toString();
                }
            }
        }
        return null;
    }

    // ---- one attribute ---------------------------------------------------

    private ObjectNode describeAttribute(ExecutableElement attribute) {
        ObjectNode node = json.objectNode();
        node.put("name", attribute.getSimpleName().toString());
        node.put("type", attribute.getReturnType().toString());

        AnnotationValue defaultValue = attribute.getDefaultValue();
        // An attribute with no default must be written at every use site — the single
        // most useful thing an authoring agent can be told about it.
        node.put("required", defaultValue == null);
        if (defaultValue != null) {
            node.put("default", defaultValue.toString());
        }

        List<String> constants = enumConstants(attribute.getReturnType());
        if (!constants.isEmpty()) {
            ArrayNode array = node.putArray("enumConstants");
            constants.forEach(array::add);
        }

        putDeprecation(node, attribute);
        putDoc(node, attribute);
        return node;
    }

    /** The admissible values, when the attribute's type (or its component type) is an enum. */
    private List<String> enumConstants(TypeMirror type) {
        TypeMirror element = type instanceof ArrayType array ? array.getComponentType() : type;
        if (!(element instanceof DeclaredType declared)
                || declared.asElement().getKind() != ElementKind.ENUM) {
            return List.of();
        }
        return ElementFilter.fieldsIn(declared.asElement().getEnclosedElements()).stream()
                .filter(field -> field.getKind() == ElementKind.ENUM_CONSTANT)
                .map(field -> field.getSimpleName().toString())
                .toList();
    }

    // ---- shared bits -----------------------------------------------------

    private void putDeprecation(ObjectNode node, Element element) {
        Deprecated deprecated = element.getAnnotation(Deprecated.class);
        if (deprecated == null) {
            return;
        }
        ObjectNode target = node.putObject("deprecated");
        target.put("forRemoval", deprecated.forRemoval());
        if (!deprecated.since().isEmpty()) {
            target.put("since", deprecated.since());
        }
        // The canonical replacement is prose and only prose — @Deprecated has nowhere to
        // put it. This is the single field a reflection-built catalog can never carry.
        String replacement = deprecatedProse(element);
        if (!replacement.isEmpty()) {
            target.put("replacement", replacement);
        }
    }

    private String deprecatedProse(Element element) {
        DocCommentTree doc = docTrees.getDocCommentTree(element);
        if (doc == null) {
            return "";
        }
        for (DocTree tag : doc.getBlockTags()) {
            if (tag instanceof DeprecatedTree deprecated) {
                return JavadocText.prose(deprecated.getBody());
            }
        }
        return "";
    }

    private void putDoc(ObjectNode node, Element element) {
        DocCommentTree doc = docTrees.getDocCommentTree(element);
        if (doc == null) {
            return;
        }
        String purpose = JavadocText.oneLine(doc.getFirstSentence());
        if (!purpose.isEmpty()) {
            node.put("purpose", purpose);
        }
        String description = JavadocText.prose(doc.getFullBody());
        if (!description.isEmpty()) {
            node.put("description", description);
        }
    }

    // ---- output ----------------------------------------------------------

    private void write() {
        String sdkVersion = processingEnv.getOptions().get(OPT_SDK_VERSION);
        if (sdkVersion == null || sdkVersion.isBlank()) {
            // Refused rather than stamped "unknown": a consumer pinning a different SDK
            // has to be able to say so, and it can only do that from this field.
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "-A" + OPT_SDK_VERSION + " is not set, so the catalog would carry no SDK version. "
                            + "Pass it from the build (${project.version}).");
            return;
        }

        ObjectNode root = json.objectNode();
        root.put("catalogFormat", CATALOG_FORMAT);
        root.put("sdkVersion", sdkVersion);
        root.put("annotationCount", annotationsByName.size());

        ArrayNode annotations = root.putArray("annotations");
        annotationsByName.values().forEach(annotations::add);   // sorted by name: reproducible

        ArrayNode packages = root.putArray("packages");
        packageNames.forEach(name -> packages.add(describePackage(name)));

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
     * Package-level prose, which is where this SDK states the rules that no single
     * annotation owns — the {@code @Field} / {@code @Validation} scoping split above all.
     */
    private ObjectNode describePackage(String name) {
        ObjectNode node = json.objectNode();
        node.put("name", name);
        PackageElement element = processingEnv.getElementUtils().getPackageElement(name);
        if (element != null) {
            putDoc(node, element);
        }
        return node;
    }
}
