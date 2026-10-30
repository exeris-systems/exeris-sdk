package eu.exeris.sdk.brief;

import eu.exeris.sdk.sourcemodel.ast.DataScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BriefReadiness: the readiness gate as a function")
class BriefReadinessTest {

    private static final EarsCriterion ALWAYS =
            new EarsCriterion(EarsCriterion.Kind.UBIQUITOUS, null, null, "the system keeps records");

    private static Brief brief(List<OpenQuestion> questions) {
        return new Brief("0.13.0", "Keep customers.", List.of("Fast lookup"), List.of("Marketing"),
                List.of(new Actor("Clerk", null, List.of("sales"))),
                List.of(new GlossaryTerm("Customer", "Someone who buys.", DataScope.TENANT, List.of("UI"),
                        List.of(new FulfilledBy(FulfilledBy.Kind.ENTITY, "com.acme.Customer")))),
                List.of(new Workflow("Register", List.of("Clerk"), List.of(ALWAYS),
                        List.of(new FulfilledBy(FulfilledBy.Kind.ACTION, "com.acme.Customer#register")))),
                List.of(new NonFunctionalRequirement(NonFunctionalRequirement.Category.LOAD, "100 users")),
                List.of(new Integration("CRM", Integration.Direction.OUTBOUND, "Sync customers")),
                List.of("crm"), questions);
    }

    private static OpenQuestion question(String id, boolean blocking, OpenQuestion.Status status,
                                         String assumption, String owner) {
        return new OpenQuestion(id, "What?", blocking, status, assumption, owner, null);
    }

    private static List<String> paths(BriefReadiness.Report report) {
        return report.errors().stream().map(BriefReadiness.Problem::path).toList();
    }

    @Test
    @DisplayName("an absent brief is not valid and never ready")
    void absentIsNeverReady() {
        BriefReadiness.Report report = BriefReadiness.evaluate(null);
        assertThat(report.valid()).isFalse();
        assertThat(report.ready()).isFalse();
        assertThat(report.errors()).hasSize(1);
    }

    @Test
    @DisplayName("an empty brief is invalid on every minimum the contract sets")
    void emptyIsInvalid() {
        BriefReadiness.Report report = BriefReadiness.evaluate(
                new Brief(null, null, null, null, null, null, null, null, null, null, null));
        assertThat(report.ready()).isFalse();
        assertThat(paths(report)).containsExactly(
                "/briefSchema", "/purpose", "/nonGoals", "/actors", "/workflows");
    }

    @Test
    @DisplayName("a blocking open question keeps a valid brief from being ready; a non-blocking one does not")
    void blockingOpenQuestion() {
        BriefReadiness.Report blocked = BriefReadiness.evaluate(
                brief(List.of(question("Q1", true, OpenQuestion.Status.OPEN, null, null))));
        assertThat(blocked.valid()).isTrue();
        assertThat(blocked.ready()).isFalse();

        BriefReadiness.Report free = BriefReadiness.evaluate(
                brief(List.of(question("Q1", false, OpenQuestion.Status.OPEN, null, null))));
        assertThat(free.ready()).isTrue();
    }

    @Test
    @DisplayName("assumed needs its assumption and owner, or the brief is invalid")
    void assumedNeedsAssumptionAndOwner() {
        BriefReadiness.Report report = BriefReadiness.evaluate(brief(List.of(
                question("Q1", true, OpenQuestion.Status.ASSUMED, null, "CFO"),
                question("Q2", true, OpenQuestion.Status.ASSUMED, "EU only", null))));
        assertThat(report.valid()).isFalse();
        assertThat(report.ready()).isFalse();
        assertThat(paths(report)).containsExactly("/openQuestions/0/assumption", "/openQuestions/1/owner");
    }

    @Test
    @DisplayName("all blocking questions assumed or resolved: ready, with the assumptions listed")
    void readyListsAssumptions() {
        BriefReadiness.Report report = BriefReadiness.evaluate(brief(List.of(
                question("Q1", true, OpenQuestion.Status.ASSUMED, "EU only", "CFO"),
                question("Q2", true, OpenQuestion.Status.RESOLVED, null, null),
                question("Q3", false, OpenQuestion.Status.ASSUMED, "No reminders", "PM"))));
        assertThat(report.valid()).isTrue();
        assertThat(report.ready()).isTrue();
        assertThat(report.errors()).isEmpty();
        assertThat(report.assumed()).containsExactly(
                new BriefReadiness.Assumption("Q1", "CFO", "EU only"),
                new BriefReadiness.Assumption("Q3", "PM", "No reminders"));
    }

    @Test
    @DisplayName("a question missing its required parts is invalid")
    void questionParts() {
        BriefReadiness.Report report = BriefReadiness.evaluate(brief(List.of(
                new OpenQuestion(null, " ", null, null, " ", null, " "))));
        assertThat(paths(report)).containsExactly(
                "/openQuestions/0/id", "/openQuestions/0/question", "/openQuestions/0/assumption",
                "/openQuestions/0/answer", "/openQuestions/0/blocking", "/openQuestions/0/status");
    }

    @Test
    @DisplayName("blank text, a malformed version and missing parts are reported at their pointers")
    void textAndParts() {
        Brief brief = new Brief("next", " ", List.of(" "), List.of(" "),
                List.of(new Actor(" ", null, List.of(" "))),
                List.of(new GlossaryTerm(null, null, null, null, null)),
                List.of(new Workflow(null, List.of(" "), null, null)),
                List.of(new NonFunctionalRequirement(null, null)),
                List.of(new Integration(null, null, " ")),
                List.of(" "), null);
        assertThat(paths(BriefReadiness.evaluate(brief))).containsExactly(
                "/briefSchema", "/purpose", "/goals/0", "/nonGoals/0", "/candidateCapabilities/0",
                "/actors/0/name", "/actors/0/roles/0",
                "/glossary/0/term", "/glossary/0/meaning",
                "/workflows/0/name", "/workflows/0/actors/0", "/workflows/0/criteria",
                "/nonFunctional/0/category", "/nonFunctional/0/requirement",
                "/integrations/0/name", "/integrations/0/purpose");
    }

    @Test
    @DisplayName("question ids: a repeat is reported at the later question, a blank id only as blank")
    void questionIdsAreUnique() {
        Brief brief = brief(List.of(
                question("Q1", false, OpenQuestion.Status.OPEN, null, null),
                question(" ", false, OpenQuestion.Status.OPEN, null, null),
                question(" ", false, OpenQuestion.Status.OPEN, null, null),
                question("Q1", false, OpenQuestion.Status.OPEN, null, null),
                question("q1", false, OpenQuestion.Status.OPEN, null, null)));
        assertThat(BriefReadiness.evaluate(brief).errors()).containsExactly(
                new BriefReadiness.Problem("/openQuestions/1/id", "must not be blank"),
                new BriefReadiness.Problem("/openQuestions/2/id", "must not be blank"),
                new BriefReadiness.Problem("/openQuestions/3/id", "repeats the id of /openQuestions/0"));
    }

    @Test
    @DisplayName("workflow actors: each names a declared actor exactly; a blank one is reported only as blank")
    void workflowActorsAreDeclared() {
        Brief brief = withWorkflow(new Workflow("W", List.of("A", "a", " "), List.of(ALWAYS), null));
        assertThat(BriefReadiness.evaluate(brief).errors()).containsExactly(
                new BriefReadiness.Problem("/workflows/0/actors/2", "must not be blank"),
                new BriefReadiness.Problem("/workflows/0/actors/1", "names no actor the brief declares"));
    }

    @Test
    @DisplayName("concern hints: the one-argument form checks the shipped vocabulary, a null vocabulary skips them")
    void concernHintsUseTheShippedVocabulary() {
        Brief brief = new Brief("0.13.0", "P", null, List.of("N"), List.of(new Actor("A", null, null)),
                List.of(new GlossaryTerm("T", "M", null, List.of("Saga", "NoSuchAnnotation"), null)),
                List.of(new Workflow("W", null, List.of(ALWAYS), null)), null, null, null, null);
        assertThat(BriefReadiness.evaluate(brief).errors()).extracting(BriefReadiness.Problem::path)
                .containsExactly("/glossary/0/concernHints/1");
        assertThat(BriefReadiness.evaluate(brief, null, null).errors()).isEmpty();
    }

    @Test
    @DisplayName("blank is ECMA-262 whitespace, as the schema's pattern means it, not Java's ASCII \\s")
    void blankIsEcmaWhitespace() {
        // ECMA-262 WhiteSpace and LineTerminator beyond Java's [ \t\n\x0B\f\r]: U+00A0, U+FEFF,
        // the Zs spaces, U+2028 and U+2029.
        int[] ecmaOnly = {0x00A0, 0xFEFF, 0x1680, 0x2000, 0x2005, 0x200A, 0x202F, 0x205F, 0x3000,
                0x2028, 0x2029};
        for (int c : ecmaOnly) {
            String blank = Character.toString(c).repeat(2);
            assertThat(paths(BriefReadiness.evaluate(withPurpose(blank))))
                    .as("U+%04X", c).containsExactly("/purpose");
        }
        // Outside ECMA-262's \s: a zero-width space, NEL and U+180E (format, not Zs) are text.
        for (int c : new int[] {0x200B, 0x0085, 0x180E}) {
            assertThat(BriefReadiness.evaluate(withPurpose(Character.toString(c))).errors())
                    .as("U+%04X", c).isEmpty();
        }
        assertThat(BriefReadiness.evaluate(withPurpose("\u00A0Keep customers.\u00A0")).errors()).isEmpty();
    }

    @Test
    @DisplayName("EARS: each kind's fields, checked by structure")
    void ears() {
        List<EarsCriterion> criteria = List.of(
                new EarsCriterion(null, null, null, "r"),
                new EarsCriterion(EarsCriterion.Kind.UBIQUITOUS, "t", "c", "r"),
                new EarsCriterion(EarsCriterion.Kind.WHEN, null, null, "r"),
                new EarsCriterion(EarsCriterion.Kind.IF, null, "c", "r"),
                new EarsCriterion(EarsCriterion.Kind.WHILE, "t", null, "r"),
                new EarsCriterion(EarsCriterion.Kind.WHEN, "t", "c", null),
                new EarsCriterion(EarsCriterion.Kind.WHEN, " ", null, "r"));
        Brief brief = withWorkflow(new Workflow("W", null, criteria, null));
        assertThat(paths(BriefReadiness.evaluate(brief))).containsExactly(
                "/workflows/0/criteria/0/kind",
                "/workflows/0/criteria/1/trigger", "/workflows/0/criteria/1/condition",
                "/workflows/0/criteria/2/trigger",
                "/workflows/0/criteria/3/trigger",
                "/workflows/0/criteria/4/condition", "/workflows/0/criteria/4/trigger",
                "/workflows/0/criteria/5/response",
                "/workflows/0/criteria/6/trigger");
    }

    @Test
    @DisplayName("references: kind admitted by position, ref shaped by kind, both present")
    void referenceShapes() {
        Workflow workflow = new Workflow("W", null, List.of(ALWAYS), List.of(
                new FulfilledBy(FulfilledBy.Kind.ENTITY, "com.acme.Customer"),
                new FulfilledBy(FulfilledBy.Kind.SAGA, "com.acme.Customer"),
                new FulfilledBy(null, null),
                new FulfilledBy(FulfilledBy.Kind.RULE, "com.acme.Customer#unique")));
        GlossaryTerm term = new GlossaryTerm("T", "M", null, null, List.of(
                new FulfilledBy(FulfilledBy.Kind.ENTITY, "com.acme.Customer#x")));
        Brief brief = new Brief("0.13.0", "P", null, List.of("N"), List.of(new Actor("A", null, null)),
                List.of(term), List.of(workflow), null, null, null, null);
        BriefReadiness.Report report = BriefReadiness.evaluate(brief);
        assertThat(paths(report)).containsExactly(
                "/glossary/0/fulfilledBy/0/ref",
                "/workflows/0/fulfilledBy/0/kind",
                "/workflows/0/fulfilledBy/1/ref",
                "/workflows/0/fulfilledBy/2/kind", "/workflows/0/fulfilledBy/2/ref");
        assertThat(report.unchecked()).containsExactly(
                new FulfilledBy(FulfilledBy.Kind.RULE, "com.acme.Customer#unique"));
    }

    @Test
    @DisplayName("references resolve against a supplied source, and an unresolved one does not change readiness")
    void resolution() {
        Brief brief = brief(List.of());
        Set<FulfilledBy> source = Set.of(new FulfilledBy(FulfilledBy.Kind.ENTITY, "com.acme.Customer"));
        BriefReadiness.Report report = BriefReadiness.evaluate(brief, Set.of("UI"), source);
        assertThat(report.ready()).isTrue();
        assertThat(report.unresolved()).containsExactly(
                new FulfilledBy(FulfilledBy.Kind.ACTION, "com.acme.Customer#register"));
        assertThat(report.unchecked()).isEmpty();
    }

    @Test
    @DisplayName("a concern hint outside the supplied vocabulary is invalid")
    void concernVocabulary() {
        BriefReadiness.Report report = BriefReadiness.evaluate(brief(List.of()), Set.of("Saga"), null);
        assertThat(paths(report)).containsExactly("/glossary/0/concernHints/0");
    }

    @Test
    @DisplayName("lists are never null on a constructed record, and a null element is refused by name")
    void lists() {
        Brief empty = new Brief(null, null, null, null, null, null, null, null, null, null, null);
        List<Object> listComponents = Arrays.stream(Brief.class.getRecordComponents())
                .filter(c -> c.getType() == List.class)
                .map(c -> {
                    try {
                        return c.getAccessor().invoke(empty);
                    } catch (ReflectiveOperationException e) {
                        throw new AssertionError(e);
                    }
                })
                .toList();
        assertThat(listComponents).isNotEmpty().allMatch(List.of()::equals);

        List<String> withNull = new ArrayList<>();
        withNull.add(null);
        assertThatThrownBy(() -> new Actor("A", null, withNull))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("roles[0]");
        assertThat(new BriefReadiness.Report(true, true, null, null, null, null).errors()).isEmpty();
    }

    private static Brief withWorkflow(Workflow workflow) {
        return new Brief("0.13.0", "P", null, List.of("N"), List.of(new Actor("A", null, null)),
                null, List.of(workflow), null, null, null, null);
    }

    private static Brief withPurpose(String purpose) {
        return new Brief("0.13.0", purpose, null, List.of("N"), List.of(new Actor("A", null, null)),
                null, List.of(new Workflow("W", null, List.of(ALWAYS), null)), null, null, null, null);
    }
}
