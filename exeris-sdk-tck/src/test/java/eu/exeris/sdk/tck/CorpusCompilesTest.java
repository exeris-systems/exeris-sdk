package eu.exeris.sdk.tck;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    private static List<JavaFileObject> sourcesOf(java.util.Map<String, String> corpus) {
        List<JavaFileObject> sources = new ArrayList<>();
        corpus.forEach((name, source) -> sources.add(new InMemorySource(name, source)));
        return sources;
    }

    /**
     * @param classes where to put the class files
     * @param sources what to compile
     * @return every error and warning javac reported, empty when it was happy
     */
    private static List<String> compile(Path classes, List<JavaFileObject> sources) throws IOException {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertThat(javac)
                .withFailMessage("No system Java compiler; run this on a JDK, not a JRE.")
                .isNotNull();

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = javac.getStandardFileManager(diagnostics, null, null)) {
            javac.getTask(
                    null,
                    files,
                    diagnostics,
                    List.of("-Werror", "-Xlint:deprecation", "-d", classes.toString()),
                    null,
                    sources).call();
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
