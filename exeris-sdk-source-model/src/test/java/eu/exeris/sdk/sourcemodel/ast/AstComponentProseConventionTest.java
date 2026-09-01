package eu.exeris.sdk.sourcemodel.ast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A record component is documented with {@code @param} on the record, never with a comment
 * written above it inside the record header.
 *
 * <p>Both forms render in javadoc, so the difference is invisible to the author — and that is
 * exactly why it needs a guard. Only {@code @param} is reachable from annotation processing,
 * so only {@code @param} reaches {@code META-INF/exeris/ast-schema.json}. A header comment
 * looks documented, reads as documented in an IDE, and silently contributes nothing to the
 * published contract. Fifteen of them existed in this package and were converted; this test
 * exists so the sixteenth is caught at the point it is written.
 *
 * <p>This reads sources rather than the compiled model on purpose: the fact under test is a
 * property of how the source is written, and the compiled model is precisely where the
 * distinction has already been lost.
 */
@DisplayName("AST prose convention: component documentation lives in @param, not in the header")
class AstComponentProseConventionTest {

    private static final Path AST_SOURCES =
            Paths.get("src/main/java/eu/exeris/sdk/sourcemodel/ast");

    private static final Pattern RECORD_HEADER = Pattern.compile("\\brecord\\s+(\\w+)\\s*\\(");

    @Test
    @DisplayName("no record header carries a component doc comment")
    void componentProseIsNotWrittenInTheHeader() throws IOException {
        assertThat(AST_SOURCES)
                .as("the test runs from the module directory; an unresolvable source path would "
                        + "make this pass vacuously")
                .isDirectory();

        List<String> offenders = new ArrayList<>();
        int recordsScanned = 0;
        try (Stream<Path> files = Files.list(AST_SOURCES)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String source = Files.readString(file);
                Matcher records = RECORD_HEADER.matcher(source);
                while (records.find()) {
                    int open = source.indexOf('(', records.start());
                    int close = matchingParen(source, open);
                    if (close < 0) {
                        continue;
                    }
                    recordsScanned++;
                    String header = source.substring(open, close);
                    if (header.contains("/**")) {
                        offenders.add("%s: record %s".formatted(file.getFileName(), records.group(1)));
                    }
                }
            }
        }

        assertThat(recordsScanned)
                .as("an empty scan would make this test vacuous")
                .isGreaterThan(30);
        assertThat(offenders)
                .as("Move the prose to an @param tag on the record. A comment inside the record "
                        + "header renders in javadoc but is unreachable from annotation processing, so "
                        + "it never reaches META-INF/exeris/ast-schema.json — the component ends up "
                        + "looking documented while contributing nothing to the published contract.")
                .isEmpty();
    }

    private static int matchingParen(String source, int open) {
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
}
