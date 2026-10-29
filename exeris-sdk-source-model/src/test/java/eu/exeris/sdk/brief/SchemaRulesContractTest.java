package eu.exeris.sdk.brief;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Holds the {@code SchemaRules} annotations to what their javadoc promises: the processor reads
 * them during compilation and no record's class file carries them.
 *
 * <p>Two independent checks. {@code @Retention} is itself a runtime-retained meta-annotation, so
 * reflection reads each rule type's retention and target directly. The compiled output is checked
 * as well: a record annotated with a {@code CLASS}- or {@code RUNTIME}-retained rule would name
 * {@code SchemaRules} in its constant pool, and none may. The annotation types themselves do
 * compile to class files, as every type does; what must not happen is a record carrying one.
 */
@DisplayName("SchemaRules: source-retained, targeted, and absent from every record's class file")
class SchemaRulesContractTest {

    private static final Set<String> RULES = Set.of(
            "Required", "NonBlank", "Pattern", "MinItems", "Kinds", "ConcernHint", "When", "WhenAll");

    @Test
    @DisplayName("every rule type is @Retention(SOURCE) and declares a non-empty @Target")
    void everyRuleIsSourceRetainedAndTargeted() {
        Class<?>[] nested = SchemaRules.class.getDeclaredClasses();
        assertThat(Arrays.stream(nested).map(Class::getSimpleName))
                .as("a rule added to SchemaRules joins this check by being listed here")
                .containsExactlyInAnyOrderElementsOf(RULES);
        for (Class<?> rule : nested) {
            assertThat(rule.isAnnotation()).as("%s is an annotation type", rule.getSimpleName()).isTrue();
            Retention retention = rule.getAnnotation(Retention.class);
            assertThat(retention).as("%s declares @Retention", rule.getSimpleName()).isNotNull();
            assertThat(retention.value()).as("%s retention", rule.getSimpleName())
                    .isEqualTo(RetentionPolicy.SOURCE);
            Target target = rule.getAnnotation(Target.class);
            assertThat(target).as("%s declares @Target", rule.getSimpleName()).isNotNull();
            assertThat(target.value()).as("%s targets", rule.getSimpleName()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("no compiled brief type outside SchemaRules refers to SchemaRules")
    void noRecordCarriesARule() throws Exception {
        Path classes = Path.of(Brief.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .resolve("eu/exeris/sdk/brief");
        Set<String> carriers = new TreeSet<>();
        int scanned = 0;
        try (Stream<Path> files = Files.list(classes)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                String name = file.getFileName().toString();
                if (!name.endsWith(".class") || name.startsWith("SchemaRules")) {
                    continue;
                }
                scanned++;
                String pool = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
                if (pool.contains("eu/exeris/sdk/brief/SchemaRules")) {
                    carriers.add(name);
                }
            }
        }
        assertThat(scanned).as("an empty walk would make this vacuous").isGreaterThan(10);
        assertThat(carriers).as("class files that carry a SchemaRules annotation").isEmpty();
    }
}
