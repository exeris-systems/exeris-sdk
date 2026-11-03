package eu.exeris.sdk.tck;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shipped corpus must compile — cleanly, and against no attribute scheduled for removal.
 *
 * <p>Not a formality. A producer binding drives javac over these sources, so the corpus is
 * <em>compiled</em> code that happens to travel as a resource, and the compiler never sees it in
 * this repository. The first draft declared its tenancy tier through
 * {@code @ExerisDomain.tenantScoped} — an attribute removed at 1.0.0, which would have left the kit
 * failing to compile at exactly the release it exists to guard, and left every binder to discover
 * it.
 *
 * <p>{@code -Werror -Xlint:deprecation} is what makes the rule enforceable rather than remembered.
 * Fixing that symptom without this guard is what a previous fix in this repo did, and is why the
 * class of defect recurred.
 */
@DisplayName("the shipped corpus compiles against the real annotations")
class CorpusCompilesTest {

    @Test
    @DisplayName("compiles with no error and no deprecation warning")
    void corpusCompilesCleanly(@TempDir Path classes) throws IOException {
        List<String> reported = compile(classes, sourcesOf(TckCorpus.all()));
        assertThat(reported)
                .withFailMessage(
                        "The shipped corpus did not compile cleanly:%n  %s%n%nA producer binding "
                                + "compiles these sources, so anything javac objects to here is "
                                + "something every binder inherits. A deprecation warning is an error "
                                + "on purpose: an attribute deprecated for removal compiles today and "
                                + "stops compiling at the release this kit exists to guard.",
                        String.join("\n  ", reported))
                .isEmpty();
    }

    @Test
    @DisplayName("an attribute deprecated for removal would be caught")
    void theDeprecationRuleIsNotVacuous(@TempDir Path classes) throws IOException {
        // The corpus as it was first written — the tier declared through the boolean that goes away
        // at 1.0.0. Committed rather than done once and described, so the proof survives a refactor
        // of the guard it guards.
        String asFirstWritten = """
                package eu.exeris.sdk.tck.corpus;

                import eu.exeris.sdk.annotation.ExerisDomain;

                @ExerisDomain(module = "sales", path = "/probes", tenantScoped = true)
                public class Probe {
                }
                """;
        List<String> reported = compile(classes, List.of(new InMemorySource("Probe", asFirstWritten)));
        assertThat(reported)
                .withFailMessage("A deprecated-for-removal attribute compiled without complaint — "
                        + "the -Werror -Xlint:deprecation pairing above is not doing anything.")
                .isNotEmpty();
        assertThat(String.join(" ", reported)).contains("tenantScoped");
    }

    @Test
    @DisplayName("every corpus entity declares its primary-key field")
    void everyEntityDeclaresItsPrimaryKey(@TempDir Path classes) throws IOException {
        PrimaryKeyCheck check = new PrimaryKeyCheck();
        compile(classes, sourcesOf(TckCorpus.all()), check);
        assertThat(check.entities)
                .withFailMessage("The check saw no @ExerisDomain type; it is not looking at the corpus.")
                .containsExactlyInAnyOrder(TckCorpus.ORDER, TckCorpus.CUSTOMER, TckCorpus.PRODUCT);
        assertThat(check.missing)
                .withFailMessage(
                        "Corpus entities without their primary-key field: %s. The exeris-tooling "
                                + "processor refuses such an entity (EXT-PROC-1015), so a producer "
                                + "binding fails at produce() before any case runs.",
                        check.missing)
                .isEmpty();
    }

    @Test
    @DisplayName("an entity without its primary-key field would be caught")
    void thePrimaryKeyRuleIsNotVacuous(@TempDir Path classes) throws IOException {
        String keyless = """
                package eu.exeris.sdk.tck.corpus;

                import eu.exeris.sdk.annotation.ExerisDomain;

                @ExerisDomain(module = "sales", path = "/probes", primaryKeyField = "probeId")
                public class Probe {
                    private java.util.UUID id;
                }
                """;
        PrimaryKeyCheck check = new PrimaryKeyCheck();
        compile(classes, List.of(new InMemorySource("Probe", keyless)), check);
        assertThat(check.missing).containsExactly("Probe.probeId");
    }

    @Test
    @DisplayName("every corpus @Blob field is typed BlobRef")
    void everyBlobFieldIsABlobRef(@TempDir Path classes) throws IOException {
        BlobTypeCheck check = new BlobTypeCheck();
        compile(classes, sourcesOf(TckCorpus.all()), check);
        assertThat(check.seen)
                .withFailMessage("The check saw no @Blob field; it is not looking at the corpus.")
                .containsExactlyInAnyOrder("Customer.avatar", "Customer.contract");
        assertThat(check.mistyped)
                .withFailMessage(
                        "Corpus @Blob fields not typed %s: %s. The exeris-tooling processor refuses "
                                + "@Blob on any other type (EXT-PROC-1032), so a producer binding fails "
                                + "at produce() or rewrites the corpus first.",
                        BlobTypeCheck.BLOB_REF, check.mistyped)
                .isEmpty();
    }

    @Test
    @DisplayName("a @Blob field of another type would be caught")
    void theBlobTypeRuleIsNotVacuous(@TempDir Path classes) throws IOException {
        String stringBlob = """
                package eu.exeris.sdk.tck.corpus;

                import eu.exeris.sdk.annotation.Blob;
                import eu.exeris.sdk.annotation.ExerisDomain;

                @ExerisDomain(module = "sales", path = "/probes")
                public class Probe {
                    private java.util.UUID id;

                    @Blob
                    private String scan;
                }
                """;
        BlobTypeCheck check = new BlobTypeCheck();
        compile(classes, List.of(new InMemorySource("Probe", stringBlob)), check);
        assertThat(check.mistyped).containsExactly("Probe.scan");
    }

    /**
     * The rule the exeris-tooling processor applies to {@code @Blob}: the field's type is the
     * kernel's {@code BlobRef}, the handle the generated application serves the bytes through.
     */
    @SupportedAnnotationTypes("eu.exeris.sdk.annotation.Blob")
    private static final class BlobTypeCheck extends AbstractProcessor {

        static final String BLOB_REF = "eu.exeris.kernel.spi.storage.blob.BlobRef";

        private final List<String> seen = new ArrayList<>();
        private final List<String> mistyped = new ArrayList<>();

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(java.util.Set<? extends TypeElement> annotations, RoundEnvironment round) {
            for (TypeElement annotation : annotations) {
                for (Element field : round.getElementsAnnotatedWith(annotation)) {
                    String name = field.getEnclosingElement().getSimpleName() + "." + field.getSimpleName();
                    seen.add(name);
                    if (!field.asType().toString().equals(BLOB_REF)) {
                        mistyped.add(name);
                    }
                }
            }
            return false;
        }
    }

    /**
     * The rule the exeris-tooling processor applies: an {@code @ExerisDomain} type declares a field
     * named after its primary key, which is {@code primaryKeyField} when that is non-blank and
     * {@code id} otherwise.
     */
    @SupportedAnnotationTypes("eu.exeris.sdk.annotation.ExerisDomain")
    private static final class PrimaryKeyCheck extends AbstractProcessor {

        private final List<String> entities = new ArrayList<>();
        private final List<String> missing = new ArrayList<>();

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(java.util.Set<? extends TypeElement> annotations, RoundEnvironment round) {
            for (TypeElement annotation : annotations) {
                for (Element entity : round.getElementsAnnotatedWith(annotation)) {
                    String key = primaryKeyField(entity);
                    String name = entity.getSimpleName().toString();
                    entities.add(name);
                    boolean declared = entity.getEnclosedElements().stream()
                            .anyMatch(e -> e.getKind() == ElementKind.FIELD
                                    && e.getSimpleName().contentEquals(key));
                    if (!declared) {
                        missing.add(name + "." + key);
                    }
                }
            }
            return false;
        }

        private static String primaryKeyField(Element entity) {
            for (AnnotationMirror mirror : entity.getAnnotationMirrors()) {
                if (!mirror.getAnnotationType().toString().equals("eu.exeris.sdk.annotation.ExerisDomain")) {
                    continue;
                }
                return mirror.getElementValues().entrySet().stream()
                        .filter(e -> e.getKey().getSimpleName().contentEquals("primaryKeyField"))
                        .map(e -> String.valueOf(e.getValue().getValue()))
                        .filter(value -> !value.isBlank())
                        .findFirst()
                        .orElse("id");
            }
            return "id";
        }
    }

    /**
     * The corpus, with a stand-in for the kernel's {@code BlobRef}. The corpus types its
     * {@code @Blob} fields with it, and a producer compiles them against {@code exeris-kernel-spi};
     * the SDK takes no kernel dependency, so this compile declares the record with the kernel's
     * shape instead. The stand-in is never shipped with the corpus.
     */
    private static List<JavaFileObject> sourcesOf(java.util.Map<String, String> corpus) {
        List<JavaFileObject> sources = new ArrayList<>();
        corpus.forEach((name, source) -> sources.add(new InMemorySource(name, source)));
        sources.add(new InMemorySource("BlobRef", BLOB_REF_STAND_IN));
        return sources;
    }

    private static final String BLOB_REF_STAND_IN = """
            package eu.exeris.kernel.spi.storage.blob;

            public record BlobRef(String container, String key) {
            }
            """;

    /**
     * @param classes where to put the class files
     * @param sources what to compile
     * @return every error and warning javac reported, empty when it was happy
     */
    private static List<String> compile(Path classes, List<JavaFileObject> sources) throws IOException {
        return compile(classes, sources, null);
    }

    /**
     * @param classes where to put the class files
     * @param sources what to compile
     * @param processor an annotation processor to run over them, or {@code null} for none
     * @return every error and warning javac reported, empty when it was happy
     */
    private static List<String> compile(Path classes, List<JavaFileObject> sources,
            AbstractProcessor processor) throws IOException {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertThat(javac)
                .withFailMessage("No system Java compiler; run this on a JDK, not a JRE.")
                .isNotNull();

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = javac.getStandardFileManager(diagnostics, null, null)) {
            JavaCompiler.CompilationTask task = javac.getTask(
                    null,
                    files,
                    diagnostics,
                    List.of("-Werror", "-Xlint:deprecation", "-d", classes.toString()),
                    null,
                    sources);
            if (processor != null) {
                task.setProcessors(List.of(processor));
            }
            task.call();
        }
        return diagnostics.getDiagnostics().stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR
                        || d.getKind() == Diagnostic.Kind.WARNING
                        || d.getKind() == Diagnostic.Kind.MANDATORY_WARNING)
                .map(d -> d.getKind() + " " + d.getMessage(null))
                .toList();
    }

    /** A corpus source handed to javac without touching the filesystem. */
    private static final class InMemorySource extends SimpleJavaFileObject {

        private final String code;

        InMemorySource(String simpleName, String code) {
            super(URI.create("string:///" + TckCorpus.packageName().replace('.', '/')
                    + "/" + simpleName + ".java"), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
}
