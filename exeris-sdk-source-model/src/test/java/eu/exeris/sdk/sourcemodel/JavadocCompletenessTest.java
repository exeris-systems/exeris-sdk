package eu.exeris.sdk.sourcemodel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Every public member of this module carries javadoc — except a builder's setters, which are
 * excluded on purpose.
 *
 * <h2>Why a test rather than {@code failOnWarnings}</h2>
 * <p>The other publishable modules gate this in their POM: {@code maven-javadoc-plugin} with
 * {@code failOnWarnings}, which is cheaper and stricter. This module cannot, for one reason.
 * Its records expose builders, and a builder's setters are deliberately undocumented: each sets
 * the record component of the same name, that component is documented by the record's own
 * {@code @param} tag, and 215 one-line restatements of those tags would be filler. Javadoc has
 * no way to exempt them, so the exemption is expressed here, in a rule a reader can check —
 * "declared inside a {@code Builder}, returns {@code Builder}" — rather than by turning the
 * gate off.
 *
 * <h2>Why this matters enough to gate</h2>
 * <p>These artifacts go to Maven Central, where they are permanent, and their javadoc is what a
 * consumer reads. Nothing in the publish path checks completeness; this is that check.
 */
@DisplayName("javadoc completeness: every public member is documented, builder setters aside")
class JavadocCompletenessTest {

    private static final Path SOURCES = Paths.get("src/main/java");

    /** {@code path:line: warning: message}, with the offending declaration on the next line. */
    private static final Pattern WARNING = Pattern.compile("^(\\S+?\\.java):(\\d+): warning: (.+)$");

    /** A builder setter: declared in a {@code Builder}, named for its component, returning the builder. */
    private static final Pattern BUILDER_SETTER = Pattern.compile("^public Builder \\w+\\(");

    @Test
    @DisplayName("javadoc reports nothing outside the documented exemption")
    void everyPublicMemberIsDocumented(@TempDir Path out) throws IOException {
        ToolProvider javadoc = ToolProvider.findFirst("javadoc")
                .orElseGet(() -> fail("no javadoc tool on this JDK"));
        assertThat(SOURCES).as("run from the module directory").isDirectory();

        List<String> args = new ArrayList<>(List.of(
                "-Xmaxwarns", "100000", "-quiet", "-protected",
                "-d", out.toString(),
                "-classpath", System.getProperty("java.class.path")));
        try (Stream<Path> files = Files.walk(SOURCES)) {
            files.filter(p -> p.toString().endsWith(".java")).map(Path::toString).forEach(args::add);
        }
        assertThat(args).as("an empty source list would make this pass vacuously").hasSizeGreaterThan(40);

        StringWriter sink = new StringWriter();
        try (PrintWriter writer = new PrintWriter(sink)) {
            javadoc.run(writer, writer, args.toArray(new String[0]));
        }

        String[] lines = sink.toString().split("\\R");
        List<String> offenders = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            Matcher m = WARNING.matcher(lines[i].trim());
            if (!m.matches()) {
                continue;
            }
            String declaration = i + 1 < lines.length ? lines[i + 1].trim() : "";
            if (BUILDER_SETTER.matcher(declaration).find()) {
                continue;
            }
            offenders.add("%s:%s %s — %s".formatted(
                    Paths.get(m.group(1)).getFileName(), m.group(2), m.group(3), declaration));
        }

        assertThat(offenders)
                .as("Document the member, or — if it is a builder setter — make it look like one. "
                        + "These jars publish to Maven Central and their javadoc is what a consumer "
                        + "reads instead of the sources.")
                .isEmpty();
    }
}
