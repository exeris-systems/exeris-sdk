package eu.exeris.sdk.sourcemodel.io;

import eu.exeris.sdk.sourcemodel.ast.ActionMetadata;
import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.EnumMetadata;
import eu.exeris.sdk.sourcemodel.ast.FieldMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * Round-trip property tests across the budgetHQ corpus — the 0.3.0 roadmap
 * line. The corpus under {@code src/test/resources/corpus/budgethq} is ported
 * from real budgetHQ entities (each file's header cites its JPA original);
 * budgetHQ does not author {@code @ExerisDomain} sources yet, so a port of the
 * real field surface is the closest available approximation until the 0.6.x
 * dogfood replaces it with sources read straight from the budgetHQ tree.
 *
 * <p>Every {@code .java} dropped into the corpus directory automatically joins
 * the property tests; per-entity fidelity spot-checks live alongside them.
 * The properties are the ADR-037 contract, applied per corpus member:
 * <ul>
 *   <li><b>read-completeness</b> — {@code unmodeledFacets()} is empty, so a
 *       reattach over a corpus entity never misattributes a processor facet
 *       as a user edit;</li>
 *   <li><b>mutate–invert restores the source</b> — byte-for-byte for in-place
 *       mutations (rename), and modulo blank-line artifacts for removals:
 *       LexicalPreservingPrinter leaves an indentation-only line where a
 *       removed member stood, so removal inverses are content-equal and
 *       AST-equal but not byte-equal. Found by this corpus; feeds the
 *       conflict-resolution RFC (conflict detection must compare ASTs, not
 *       text);</li>
 *   <li><b>idempotence</b> — re-applying a mutation is a no-op;</li>
 *   <li><b>reader sees writer output</b> — a written mutation is visible to
 *       a subsequent {@code read()}.</li>
 * </ul>
 */
@DisplayName("budgetHQ corpus: round-trip properties over ported real entities")
class BudgetHqCorpusRoundTripTest {

    private static final Path CORPUS_DIR = Path.of("src", "test", "resources", "corpus", "budgethq");

    /** Probe field name guaranteed absent from every corpus member. */
    private static final String PROBE = "corpusProbeField";

    private final SourceModelReader reader = new SourceModelReader();
    private final SourceModelWriter writer = new SourceModelWriter();

    record CorpusFile(String name, String source) {
        @Override
        public String toString() {
            return name; // parameterized-test display name
        }
    }

    static Stream<CorpusFile> corpus() {
        try (Stream<Path> files = Files.list(CORPUS_DIR)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .map(p -> {
                        try {
                            return new CorpusFile(p.getFileName().toString(), Files.readString(p));
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .toList()
                    .stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Stream<CorpusFile> entityCorpus() {
        return corpus().filter(f -> f.source().contains("@ExerisDomain"));
    }

    @Test
    void corpusIsPresentAndDiverse() {
        List<CorpusFile> all = corpus().toList();
        // a silently empty/shrunken corpus must fail loudly, not vacuously pass
        assertThat(all).hasSizeGreaterThanOrEqualTo(6);
        assertThat(entityCorpus()).hasSizeGreaterThanOrEqualTo(5);
        // at least one non-entity supporting type (enum) keeps readEnums exercised
        assertThat(all.stream().filter(f -> f.source().contains("public enum"))).isNotEmpty();
        // the mutation properties below inject PROBE as a fresh field — no corpus
        // member may already declare it, or addField/renameField would no-op and the
        // property's "mutation changed the source" precondition would fail obscurely
        assertThat(entityCorpus()
                .flatMap(f -> reader.read(f.source()).stream())
                .flatMap(d -> d.fields().stream())
                .map(FieldMetadata::name))
                .doesNotContain(PROBE);
    }

    @ParameterizedTest
    @MethodSource("entityCorpus")
    void everyEntityReadsWithNameAndFields(CorpusFile file) {
        DomainMetadata domain = reader.read(file.source()).orElseThrow();

        assertThat(domain.entityName()).isNotBlank();
        assertThat(domain.packageName()).startsWith("app.budgethq.");
        assertThat(domain.fields()).isNotEmpty();
    }

    @ParameterizedTest
    @MethodSource("corpus")
    void noCorpusMemberHasUnmodeledFacets(CorpusFile file) {
        // the reattach-safety precondition: reader and processor agree on every facet
        assertThat(reader.unmodeledFacets(file.source())).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("entityCorpus")
    void addThenRemoveProbeFieldRestoresContentAndAst(CorpusFile file) {
        String mutated = writer.addField(file.source(), "String", PROBE);
        assertThat(mutated).isNotEqualTo(file.source());

        String restored = writer.removeField(mutated, PROBE);
        // not byte-equal: LexicalPreservingPrinter leaves an indentation-only
        // line where the removed member stood (documented writer limitation) —
        // so the guarantee characterized here is content + AST equality
        assertThat(contentLines(restored)).isEqualTo(contentLines(file.source()));
        assertThat(reader.read(restored)).isEqualTo(reader.read(file.source()));
    }

    /** Non-blank lines, verbatim — blank-line structure is the LPP artifact zone. */
    private static List<String> contentLines(String source) {
        return source.lines().filter(line -> !line.isBlank()).toList();
    }

    @ParameterizedTest
    @MethodSource("entityCorpus")
    void addFieldIsIdempotent(CorpusFile file) {
        String once = writer.addField(file.source(), "String", PROBE);

        assertThat(writer.addField(once, "String", PROBE)).isEqualTo(once);
    }

    @ParameterizedTest
    @MethodSource("entityCorpus")
    void renameThereAndBackRestoresSourceExactly(CorpusFile file) {
        String firstField = reader.read(file.source()).orElseThrow()
                .fields().getFirst().name();
        String renamed = writer.renameField(file.source(), firstField, PROBE);
        assertThat(renamed).isNotEqualTo(file.source());

        assertThat(writer.renameField(renamed, PROBE, firstField)).isEqualTo(file.source());
    }

    @ParameterizedTest
    @MethodSource("entityCorpus")
    void readerSeesWriterMutation(CorpusFile file) {
        String mutated = writer.addField(file.source(), "String", PROBE);

        assertThat(reader.read(mutated).orElseThrow().findField(PROBE)).isPresent();
    }

    @ParameterizedTest
    @MethodSource("entityCorpus")
    void addThenRemoveRelationshipRestoresContentAndAst(CorpusFile file) {
        String mutated = writer.addRelationship(file.source(), PROBE, "Probe", "MANY_TO_ONE");
        assertThat(reader.read(mutated).orElseThrow().relationships())
                .anySatisfy(r -> assertThat(r.fieldName()).isEqualTo(PROBE));

        String restored = writer.removeRelationship(mutated, PROBE);
        // same LPP blank-line artifact as field removal — content + AST equality
        assertThat(contentLines(restored)).isEqualTo(contentLines(file.source()));
        assertThat(reader.read(restored)).isEqualTo(reader.read(file.source()));
    }

    @ParameterizedTest
    @MethodSource("entityCorpus")
    void changeFieldTypeThereAndBackRestoresSourceExactly(CorpusFile file) {
        DomainMetadata domain = reader.read(file.source()).orElseThrow();
        FieldMetadata first = domain.fields().getFirst();
        String originalType = first.type();
        // a sentinel type guaranteed different from any corpus field's declared type
        String sentinel = "ProbeType";
        assertThat(originalType).isNotEqualTo(sentinel);

        String changed = writer.changeFieldType(file.source(), first.name(), sentinel);
        assertThat(changed).isNotEqualTo(file.source());
        // changeFieldType is an in-place edit (no member removal) -> byte-exact inverse
        assertThat(writer.changeFieldType(changed, first.name(), originalType))
                .isEqualTo(file.source());
    }

    // --- per-entity fidelity spot-checks (ground the ports in the real shapes) ---

    private String corpusSource(String fileName) {
        return corpus().filter(f -> f.name().equals(fileName)).findFirst().orElseThrow().source();
    }

    @Test
    void bankAccountModelsConnectionRelationshipAndDomainAttributes() {
        DomainMetadata account = reader.read(corpusSource("BankAccount.java")).orElseThrow();

        assertThat(account.tenantScoped()).isTrue();
        assertThat(account.module()).isEqualTo("sync");
        assertThat(account.relationships()).extracting("fieldName", "targetEntity")
                .containsExactly(tuple("connection", "BankConnection"));
        // FK fields stay plain fields; the relationship is modelled separately
        assertThat(account.findField("connection")).isEmpty();
    }

    @Test
    void bankTransactionCarriesCreateTriggeredEvent() {
        DomainMetadata tx = reader.read(corpusSource("BankTransaction.java")).orElseThrow();

        // unnamed @DomainEvent(trigger = CREATE) -> processor-derived name
        assertThat(tx.events()).extracting("name", "topic")
                .containsExactly(tuple("BankTransactionCreatedEvent", "sync.bank-transactions"));
        assertThat(tx.relationships()).extracting("targetEntity").containsExactly("BankAccount");
    }

    @Test
    void userExposesLockoutActionsWithParams() {
        DomainMetadata user = reader.read(corpusSource("User.java")).orElseThrow();

        assertThat(user.audited()).isTrue();
        assertThat(user.sensitive()).isTrue();
        assertThat(user.actions()).extracting(ActionMetadata::name)
                .containsExactly("lock", "forceLogout");
        assertThat(user.actions().getFirst().params()).extracting("name")
                .containsExactly("reason", "until");
        assertThat(user.actions().getLast().async()).isTrue();
    }

    @Test
    void investmentReadsUiViewFlags() {
        DomainMetadata investment = reader.read(corpusSource("Investment.java")).orElseThrow();

        assertThat(investment.uiMetadata().listView()).isTrue();
        assertThat(investment.uiMetadata().detailView()).isTrue();
        assertThat(investment.uiMetadata().createForm()).isTrue();
        assertThat(investment.uiMetadata().editForm()).isFalse();
    }

    @Test
    void identityProviderEnumIsExtracted() {
        List<EnumMetadata> enums = reader.readEnums(corpusSource("IdentityProvider.java"));

        assertThat(enums).hasSize(1);
        assertThat(enums.getFirst().qualifiedName())
                .isEqualTo("app.budgethq.identity.domain.IdentityProvider");
        assertThat(enums.getFirst().values()).extracting("name")
                .containsExactly("LOCAL", "GOOGLE", "MICROSOFT");
    }
}
