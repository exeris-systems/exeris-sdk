package eu.exeris.sdk.sourcemodel.io;

import eu.exeris.sdk.sourcemodel.ast.ActionMetadata;
import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ActionMetadata#resultType()} as the reader writes it, against what javac reports for
 * the same method: {@code ExecutableElement.getReturnType().toString()}, the form the
 * {@code exeris-tooling} processor writes (ADR-042).
 *
 * <p>The oracle is javac itself, run in-process over the fixture with a processor that records
 * the return type of every {@code @Action} method. The fixture uses no type-use annotations,
 * whose rendering is the one place the AST form departs from {@code toString()}; that case is
 * pinned separately against the reader alone.
 */
@DisplayName("reader↔javac parity: ActionMetadata.resultType")
class SourceModelReaderResultTypeParityTest {

    private static final String ORDER = """
            package shop;

            import eu.exeris.sdk.annotation.Action;
            import eu.exeris.sdk.annotation.ExerisDomain;
            import java.util.List;
            import java.util.Map;

            @ExerisDomain(module = "shop", path = "/orders")
            public class Order<K> {

                public record Receipt(String reference) {
                }

                @Action(name = "submit", label = "Submit")
                public void submit() {
                }

                @Action(name = "customer", label = "Customer")
                public Customer customer() {
                    return null;
                }

                @Action(name = "receipt", label = "Receipt")
                public Receipt receipt() {
                    return null;
                }

                @Action(name = "receipts", label = "Receipts")
                public List<Receipt> receipts() {
                    return null;
                }

                @Action(name = "total", label = "Total")
                public long total() {
                    return 0;
                }

                @Action(name = "totals", label = "Totals")
                public Map<String, List<? extends Number>> totals() {
                    return null;
                }

                @Action(name = "sinks", label = "Sinks")
                public List<? super Integer> sinks() {
                    return null;
                }

                @Action(name = "anything", label = "Anything")
                public List<?> anything() {
                    return null;
                }

                @Action(name = "blob", label = "Blob")
                public byte[][] blob() {
                    return null;
                }

                @Action(name = "labels", label = "Labels")
                public String[] labels() {
                    return null;
                }

                @Action(name = "entry", label = "Entry")
                public Map.Entry<String, Integer> entry() {
                    return null;
                }

                @Action(name = "max", label = "Max")
                public <T extends Comparable<T>> T max() {
                    return null;
                }

                @Action(name = "key", label = "Key")
                public K key() {
                    return null;
                }

                @Action(name = "self", label = "Self")
                public java.util.Optional<Order<K>> self() {
                    return null;
                }

                @Action(name = "state", label = "State")
                public Thread.State state() {
                    return null;
                }

                @Action(name = "boxed", label = "Boxed")
                public Integer boxed() {
                    return null;
                }

                @Action(name = "qualified", label = "Qualified")
                public shop.Customer qualified() {
                    return null;
                }

                @SuppressWarnings("rawtypes")
                @Action(name = "raw", label = "Raw")
                public List raw() {
                    return null;
                }
            }
            """;

    private static final String CUSTOMER = """
            package shop;

            public class Customer {
            }
            """;

    private final SourceModelReader reader = new SourceModelReader();

    @Test
    @DisplayName("every @Action method's resultType is what javac reports for its return type")
    void readerWritesWhatJavacReports(@TempDir Path classes) throws IOException {
        Map<String, String> javac = javacReturnTypes(classes, Map.of("Order", ORDER, "Customer", CUSTOMER));
        Map<String, String> read = reader.read(ORDER).orElseThrow().actions().stream()
                .collect(Collectors.toMap(ActionMetadata::methodName, ActionMetadata::resultType,
                        (a, b) -> a, LinkedHashMap::new));

        assertThat(javac).hasSize(18);
        assertThat(read).containsExactlyInAnyOrderEntriesOf(javac);
    }

    @Test
    @DisplayName("the five cases the generators rely on render as expected")
    void theGeneratorCasesRenderAsExpected() {
        Map<String, String> read = reader.read(ORDER).orElseThrow().actions().stream()
                .collect(Collectors.toMap(ActionMetadata::methodName, ActionMetadata::resultType));

        assertThat(read)
                .containsEntry("submit", "void")
                .containsEntry("customer", "shop.Customer")
                .containsEntry("receipt", "shop.Order.Receipt")
                .containsEntry("receipts", "java.util.List<shop.Order.Receipt>")
                .containsEntry("total", "long");
    }

    @Nested
    @DisplayName("what a single-file reader cannot see")
    class Limits {

        @Test
        @DisplayName("a type-use annotation is not part of the name")
        void typeUseAnnotationsAreDropped() {
            DomainMetadata domain = reader.read("""
                    package shop;
                    import eu.exeris.sdk.annotation.Action;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import java.util.List;
                    @ExerisDomain
                    public class Basket {
                        @Action(name = "names", label = "Names")
                        public List<@Marked String> names() { return null; }
                    }
                    """).orElseThrow();
            assertThat(domain.actions()).singleElement()
                    .extracting(ActionMetadata::resultType)
                    .isEqualTo("java.util.List<java.lang.String>");
        }

        @Test
        @DisplayName("a simple name under an on-demand import is kept as written")
        void onDemandImportKeepsTheWrittenName() {
            DomainMetadata domain = reader.read("""
                    package shop;
                    import eu.exeris.sdk.annotation.*;
                    import java.util.*;
                    @ExerisDomain
                    public class Basket {
                        @Action(name = "items", label = "Items")
                        public List<Item> items() { return null; }

                        @Action(name = "count", label = "Count")
                        public Long count() { return null; }
                    }
                    """).orElseThrow();
            assertThat(domain.actions())
                    .extracting(ActionMetadata::resultType)
                    .containsExactly("List<Item>", "java.lang.Long");
        }

        @Test
        @DisplayName("a type in the default package is its simple name")
        void defaultPackageKeepsTheSimpleName() {
            DomainMetadata domain = reader.read("""
                    import eu.exeris.sdk.annotation.Action;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    @ExerisDomain
                    public class Basket {
                        @Action(name = "item", label = "Item")
                        public Item item() { return null; }
                    }
                    """).orElseThrow();
            assertThat(domain.actions()).singleElement()
                    .extracting(ActionMetadata::resultType)
                    .isEqualTo("Item");
        }
    }

    /**
     * Compiles {@code sources} with annotation processing only, and records what javac reports as
     * the return type of each {@code @Action} method.
     *
     * @return method name → {@code getReturnType().toString()}
     */
    private static Map<String, String> javacReturnTypes(Path classes, Map<String, String> sources)
            throws IOException {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertThat(javac)
                .withFailMessage("No system Java compiler; run this on a JDK, not a JRE.")
                .isNotNull();

        ReturnTypes recorder = new ReturnTypes();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = javac.getStandardFileManager(diagnostics, null, null)) {
            JavaCompiler.CompilationTask task = javac.getTask(null, files, diagnostics,
                    List.of("-proc:only", "-d", classes.toString()), null,
                    sources.entrySet().stream()
                            .map(source -> (JavaFileObject) new InMemorySource(source.getKey(), source.getValue()))
                            .toList());
            task.setProcessors(List.of(recorder));
            task.call();
        }
        assertThat(diagnostics.getDiagnostics().stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .map(d -> d.getMessage(null))
                .toList())
                .isEmpty();
        return recorder.returnTypes;
    }

    /** Records the return type of every method annotated {@code eu.exeris.sdk.annotation.Action}. */
    private static final class ReturnTypes extends AbstractProcessor {

        private final Map<String, String> returnTypes = new LinkedHashMap<>();

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of("*");
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
            for (Element root : round.getRootElements()) {
                for (Element member : root.getEnclosedElements()) {
                    if (member.getKind() == ElementKind.METHOD && isAction(member)) {
                        returnTypes.put(member.getSimpleName().toString(),
                                ((ExecutableElement) member).getReturnType().toString());
                    }
                }
            }
            return false;
        }

        private static boolean isAction(Element method) {
            return method.getAnnotationMirrors().stream()
                    .anyMatch(mirror -> mirror.getAnnotationType().toString()
                            .equals("eu.exeris.sdk.annotation.Action"));
        }
    }

    /** A fixture source handed to javac without touching the filesystem. */
    private static final class InMemorySource extends SimpleJavaFileObject {

        private final String code;

        InMemorySource(String simpleName, String code) {
            super(URI.create("string:///shop/" + simpleName + ".java"), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
}
