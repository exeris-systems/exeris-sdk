package eu.exeris.sdk.brief;

import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Holds the one-way dependency ADR-106 sets between the brief and the source model: the brief may
 * use enums from {@code eu.exeris.sdk.sourcemodel.ast}, and nothing under
 * {@code eu.exeris.sdk.sourcemodel} may use the brief.
 *
 * <p>Read from the compiled classes' constant pools rather than from import lines, so a
 * fully-qualified use is caught as surely as an import.
 */
@DisplayName("brief dependency direction: brief → source-model enums only, never the reverse")
class BriefDependencyDirectionTest {

    private static final Pattern BRIEF = Pattern.compile("eu/exeris/sdk/brief/");
    private static final Pattern SOURCE_MODEL = Pattern.compile("eu/exeris/sdk/sourcemodel/[A-Za-z0-9_/$]+");

    @Test
    @DisplayName("nothing under eu.exeris.sdk.sourcemodel references eu.exeris.sdk.brief")
    void sourceModelDoesNotUseTheBrief() throws Exception {
        Path classes = mainClasses();
        Set<String> offenders = new TreeSet<>();
        int scanned = 0;
        try (Stream<Path> files = Files.walk(classes.resolve("eu/exeris/sdk/sourcemodel"))) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".class"))::iterator) {
                scanned++;
                if (BRIEF.matcher(constantPool(file)).find()) {
                    offenders.add(classes.relativize(file).toString());
                }
            }
        }
        assertThat(scanned).as("an empty walk would make this vacuous").isGreaterThan(40);
        assertThat(offenders).isEmpty();
    }

    @Test
    @DisplayName("the brief uses only enums from the source model")
    void briefUsesOnlySourceModelEnums() throws Exception {
        Path classes = mainClasses();
        Set<String> referenced = new TreeSet<>();
        try (Stream<Path> files = Files.walk(classes.resolve("eu/exeris/sdk/brief"))) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".class"))::iterator) {
                Matcher matcher = SOURCE_MODEL.matcher(constantPool(file));
                while (matcher.find()) {
                    referenced.add(matcher.group().replace('/', '.'));
                }
            }
        }
        assertThat(referenced).as("the brief reuses DataScope by type").contains("eu.exeris.sdk.sourcemodel.ast.DataScope");
        for (String name : referenced) {
            assertThat(Class.forName(name, false, getClass().getClassLoader()).isEnum())
                    .as("%s is used by the brief and is not an enum", name).isTrue();
        }
    }

    /** The class file read as Latin-1, so every UTF-8 constant of ASCII text reads as itself. */
    private static String constantPool(Path file) throws Exception {
        return new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
    }

    private static Path mainClasses() throws URISyntaxException {
        return Path.of(DomainMetadata.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }
}
