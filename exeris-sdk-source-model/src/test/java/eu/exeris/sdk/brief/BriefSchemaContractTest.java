package eu.exeris.sdk.brief;

import eu.exeris.sdk.sourcemodel.ast.ActionMetadata;
import eu.exeris.sdk.sourcemodel.ast.DataScope;
import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Holds {@code META-INF/exeris/brief-schema.json} to the contract ADR-106 states for it.
 *
 * <p>The schema is written by {@code BriefSchemaProcessor} from this module's sources during its
 * own compilation. The expectations here are derived independently: the ADR-003 structure set
 * from {@link DomainMetadata}'s compiled record components, the concern-hint vocabulary from the
 * annotation catalog the annotations jar ships, and validity from a draft 2020-12 validator
 * running the fixtures, checked against {@link BriefReadiness} on the same documents.
 */
@DisplayName("brief schema contract: closed, catalog-bound, and no slot for a source structure")
class BriefSchemaContractTest {

    /**
     * One re-encoded structure per {@link DomainMetadata} component that carries one, as a brief
     * author might write it. The keys must cover {@link #structureComponents()}: a structure added
     * to the source model fails {@link #everySourceStructureIsRejected()} until it has a case here.
     */
    private static final Map<String, String> RE_ENCODED = new TreeMap<>(Map.ofEntries(
            Map.entry("fields", "[{\"name\":\"email\",\"type\":\"String\",\"min\":0,\"maxLength\":120}]"),
            Map.entry("actions", "[{\"name\":\"issue\",\"methodName\":\"issue\",\"resultType\":\"Invoice\","
                    + "\"params\":[{\"name\":\"date\",\"type\":\"LocalDate\"}]}]"),
            Map.entry("events", "[{\"name\":\"InvoiceIssued\",\"payloadFields\":[\"id\"]}]"),
            Map.entry("relationships", "[{\"name\":\"customer\",\"target\":\"Customer\",\"type\":\"MANY_TO_ONE\"}]"),
            Map.entry("projections", "[{\"name\":\"InvoiceSummary\",\"fields\":[\"id\",\"total\"]}]"),
            Map.entry("eventHandlers", "[{\"eventType\":\"OrderShipped\",\"methodName\":\"onShipped\"}]"),
            Map.entry("uiMetadata", "{\"views\":[{\"kind\":\"LIST\",\"layout\":\"table\",\"columns\":[\"id\"]}]}"),
            Map.entry("graphMetadata", "{\"label\":\"Invoice\",\"edges\":[{\"type\":\"BILLS\"}]}"),
            Map.entry("sagaMetadata", "{\"name\":\"settle\",\"steps\":[{\"name\":\"charge\",\"order\":1}]}"),
            Map.entry("eventSourced", "{\"snapshotEvery\":100}"),
            Map.entry("internalApi", "{\"hidden\":[\"total\"]}"),
            Map.entry("systemFields", "{\"primaryKey\":\"id\",\"tenantField\":\"tenantId\"}"),
            Map.entry("rules", "[{\"name\":\"positive\",\"expression\":\"total > 0\"}]"),
            Map.entry("channel", "{\"messageType\":\"InvoiceUpdate\"}")));

    /**
     * The nested structures ADR-106 names, written under the keys an author would use: a type, a
     * validation bound, an action signature, a saga step, a view layout.
     */
    private static final List<String> NESTED_KEYS = List.of(
            "type", "min", "max", "minLength", "maxLength", "pattern", "required",
            "params", "resultType", "methodName", "steps", "views", "layout", "columns");

    /** Every object position a brief author can write a property into. */
    private static final List<String> POSITIONS = List.of(
            "", "/actors/0", "/glossary/0", "/glossary/0/fulfilledBy/0", "/workflows/0",
            "/workflows/0/criteria/0", "/workflows/0/fulfilledBy/0", "/nonFunctional/0",
            "/integrations/0", "/openQuestions/0");

    // ---- shape -----------------------------------------------------------

    @Test
    @DisplayName("every object in the schema is additionalProperties: false")
    void everyObjectIsClosed() {
        List<String> open = new ArrayList<>();
        walk(BriefDocuments.schemaDocument(), "", (path, node) -> {
            if (node.isObject() && node.has("type") && "object".equals(node.get("type").asString())) {
                JsonNode additional = node.get("additionalProperties");
                if (additional == null || !additional.isBoolean() || additional.asBoolean()) {
                    open.add(path);
                }
            }
        });
        assertThat(BriefDocuments.schemaDocument().get("$defs").size())
                .as("an empty schema would make this vacuous").isGreaterThan(10);
        assertThat(open).as("objects that would admit a property the brief does not name").isEmpty();
    }

    @Test
    @DisplayName("no Java type reached the schema unmapped, and every $ref resolves")
    void everyTypeIsMappedAndResolves() {
        Set<String> defined = new TreeSet<>();
        BriefDocuments.schemaDocument().get("$defs").propertyNames().forEach(defined::add);
        List<String> problems = new ArrayList<>();
        walk(BriefDocuments.schemaDocument(), "", (path, node) -> {
            if (node.isObject() && node.has("x-exeris-unmapped")) {
                problems.add(path + " unmapped " + node.get("x-exeris-unmapped").asString());
            }
            if (node.isObject() && node.has("$ref")) {
                String ref = node.get("$ref").asString();
                if (!ref.startsWith("#/$defs/") || !defined.contains(ref.substring("#/$defs/".length()))) {
                    problems.add(path + " dangling " + ref);
                }
            }
        });
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("the document is reproducible: nothing dates it, and it carries its format integer")
    void isReproducibleAndVersioned() {
        JsonNode schema = BriefDocuments.schemaDocument();
        Set<String> rootKeys = new TreeSet<>();
        schema.propertyNames().forEach(rootKeys::add);
        // Pinned exactly: a generatedAt-shaped key is the one addition that would make the jar
        // unreproducible, and it would arrive as an addition rather than as a failure.
        assertThat(rootKeys).containsExactly(
                "$defs", "$id", "$ref", "$schema", "briefSchemaFormat", "definitionCount",
                "description", "sdkVersion", "title", "x-exeris-reader-requirements");
        int format = schema.get("briefSchemaFormat").asInt();
        assertThat(format).isPositive();
        assertThat(schema.get("$id").asString()).endsWith("/brief/" + format + ".json");
        assertThat(schema.get("$ref").asString()).isEqualTo("#/$defs/Brief");
        assertThat(schema.get("definitionCount").asInt()).isEqualTo(schema.get("$defs").size());
    }

    @Test
    @DisplayName("every definition and every property carries prose")
    void isFullyDocumented() {
        List<String> undocumented = new ArrayList<>();
        for (Map.Entry<String, JsonNode> def : BriefDocuments.schemaDocument().get("$defs").properties()) {
            if (!def.getValue().has("description")) {
                undocumented.add(def.getKey());
            }
            JsonNode properties = def.getValue().get("properties");
            if (properties != null) {
                for (Map.Entry<String, JsonNode> property : properties.properties()) {
                    if (!property.getValue().has("description")) {
                        undocumented.add(def.getKey() + "." + property.getKey());
                    }
                }
            }
        }
        assertThat(undocumented).as("a brief record or component without its javadoc or @param").isEmpty();
    }

    @Test
    @DisplayName("the definitions are exactly the types Brief reaches, DataScope reused by type")
    void definesWhatTheBriefReaches() {
        Set<String> defined = new TreeSet<>();
        BriefDocuments.schemaDocument().get("$defs").propertyNames().forEach(defined::add);
        Set<String> expected = new TreeSet<>(List.of(BriefSchemaNames.CONCERN_HINT));
        reachable(Brief.class, expected);
        assertThat(defined).isEqualTo(expected);

        List<String> scopes = new ArrayList<>();
        BriefDocuments.schemaDocument().get("$defs").get("DataScope").get("enum")
                .forEach(n -> scopes.add(n.asString()));
        assertThat(scopes).containsExactly(Arrays.stream(DataScope.values()).map(Enum::name).toArray(String[]::new));
    }

    // ---- the catalog -----------------------------------------------------

    @Test
    @DisplayName("every concern hint names an annotation in the same build's catalog")
    void concernHintsComeFromTheCatalog() {
        Set<String> hints = BriefDocuments.schemaVocabulary();
        JsonNode catalog = BriefDocuments.catalog();

        Set<String> topLevel = new TreeSet<>();
        Set<String> containers = new TreeSet<>();
        Set<String> leaving = new TreeSet<>();
        for (JsonNode annotation : catalog.get("annotations")) {
            if (annotation.has("repeatableContainer")) {
                containers.add(annotation.get("repeatableContainer").asString());
            }
            if (annotation.get("nested").asBoolean()) {
                continue;
            }
            topLevel.add(annotation.get("qualifiedName").asString());
            JsonNode deprecated = annotation.get("deprecated");
            if (deprecated != null && deprecated.get("forRemoval").asBoolean()) {
                leaving.add(annotation.get("qualifiedName").asString());
            }
        }
        Set<String> catalogNames = new TreeSet<>();
        for (String qualified : topLevel) {
            catalogNames.add(qualified.substring(qualified.lastIndexOf('.') + 1));
        }
        assertThat(catalogNames).as("top-level simple names must be unique for a hint to be unambiguous")
                .hasSameSizeAs(topLevel);
        assertThat(hints).as("the schema's hints must all be catalog annotations").isSubsetOf(catalogNames);

        Set<String> expected = new TreeSet<>();
        for (String qualified : topLevel) {
            if (!containers.contains(qualified) && !leaving.contains(qualified)) {
                expected.add(qualified.substring(qualified.lastIndexOf('.') + 1));
            }
        }
        assertThat(hints)
                .as("top-level annotations, less repeatable containers and those deprecated for removal")
                .isEqualTo(expected)
                .contains("UI", "Saga", "EventSourced");
    }

    // ---- ADR-003: no slot for a source structure -------------------------

    @Test
    @DisplayName("every structure DomainMetadata carries is rejected, at every position, one case each")
    void everySourceStructureIsRejected() {
        Set<String> structures = structureComponents();
        assertThat(structures).as("an empty derivation would make this vacuous").hasSizeGreaterThan(10);
        assertThat(RE_ENCODED.keySet())
                .as("a DomainMetadata structure without its rejection case; add one to RE_ENCODED")
                .containsAll(structures);

        Set<String> named = new TreeSet<>();
        walk(BriefDocuments.schemaDocument(), "", (path, node) -> {
            if (node.isObject() && node.get("properties") instanceof ObjectNode properties) {
                properties.propertyNames().forEach(named::add);
            }
        });
        assertThat(named).as("the schema must declare no property a source structure would occupy")
                .doesNotContainAnyElementsOf(structures);

        JsonNode base = BriefDocuments.fixture("ready.brief.yaml");
        List<String> admitted = new ArrayList<>();
        for (String structure : structures) {
            JsonNode value = BriefDocuments.JSON.readTree(RE_ENCODED.get(structure));
            for (String position : POSITIONS) {
                ObjectNode document = BriefDocuments.copy(base);
                ((ObjectNode) document.at(position)).set(structure, value);
                if (BriefDocuments.schemaErrors(document).isEmpty()) {
                    admitted.add(structure + " at '" + position + "'");
                }
                assertThat(BriefDocuments.bind(document))
                        .as("a strict reader must refuse %s at '%s'", structure, position).isNull();
            }
        }
        assertThat(admitted).as("re-encoded structures the schema admitted").isEmpty();
    }

    @Test
    @DisplayName("the nested source structures (type, bound, signature, step, layout) are rejected too")
    void nestedStructuresAreRejected() {
        JsonNode base = BriefDocuments.fixture("ready.brief.yaml");
        List<String> admitted = new ArrayList<>();
        for (String key : NESTED_KEYS) {
            for (String position : POSITIONS) {
                ObjectNode document = BriefDocuments.copy(base);
                ((ObjectNode) document.at(position)).put(key, "x");
                if (BriefDocuments.schemaErrors(document).isEmpty()) {
                    admitted.add(key + " at '" + position + "'");
                }
            }
        }
        assertThat(admitted).isEmpty();
    }

    // ---- EARS, by structure ----------------------------------------------

    @Test
    @DisplayName("an EARS criterion is valid when it has the fields its kind requires, and no others")
    void earsIsCheckedByStructure() {
        record Case(String kind, boolean trigger, boolean condition, boolean response, boolean valid) {
        }
        List<Case> cases = List.of(
                new Case("ubiquitous", false, false, true, true),
                new Case("ubiquitous", true, false, true, false),
                new Case("ubiquitous", false, true, true, false),
                new Case("when", true, false, true, true),
                new Case("when", true, true, true, true),
                new Case("when", false, true, true, false),
                new Case("if", true, false, true, true),
                new Case("if", true, true, true, true),
                new Case("if", false, false, true, false),
                new Case("while", false, true, true, true),
                new Case("while", true, true, true, false),
                new Case("while", false, false, true, false),
                new Case("when", true, false, false, false),
                new Case("shall", false, false, true, false));
        JsonNode base = BriefDocuments.fixture("minimal.brief.yaml");
        for (Case c : cases) {
            ObjectNode document = BriefDocuments.copy(base);
            ObjectNode criterion = (ObjectNode) document.at("/workflows/0/criteria/0");
            criterion.removeAll();
            criterion.put("kind", c.kind());
            if (c.trigger()) {
                criterion.put("trigger", "an event");
            }
            if (c.condition()) {
                criterion.put("condition", "a state");
            }
            if (c.response()) {
                criterion.put("response", "the system responds");
            }
            assertThat(BriefDocuments.schemaErrors(document).isEmpty()).as("schema on %s", c).isEqualTo(c.valid());
            Brief bound = BriefDocuments.bind(document);
            boolean javaValid = bound != null && BriefReadiness.evaluate(bound).valid();
            assertThat(javaValid).as("BriefReadiness on %s", c).isEqualTo(c.valid());
        }
    }

    @Test
    @DisplayName("a glossary term admits only entity references, a workflow only action, rule and saga")
    void referenceKindsArePositional() {
        JsonNode base = BriefDocuments.fixture("ready.brief.yaml");

        ObjectNode termWithAction = BriefDocuments.copy(base);
        ((ObjectNode) termWithAction.at("/glossary/0/fulfilledBy/0"))
                .put("kind", "action").put("ref", "com.acme.billing.Invoice#issue");
        assertRejectedByBoth(termWithAction);

        ObjectNode workflowWithEntity = BriefDocuments.copy(base);
        ((ObjectNode) workflowWithEntity.at("/workflows/0/fulfilledBy/0"))
                .put("kind", "entity").put("ref", "com.acme.billing.Invoice");
        assertRejectedByBoth(workflowWithEntity);

        ObjectNode memberWithoutHash = BriefDocuments.copy(base);
        ((ObjectNode) memberWithoutHash.at("/workflows/0/fulfilledBy/0")).put("ref", "com.acme.billing.Invoice");
        assertRejectedByBoth(memberWithoutHash);

        ObjectNode entityWithHash = BriefDocuments.copy(base);
        ((ObjectNode) entityWithHash.at("/glossary/0/fulfilledBy/0")).put("ref", "com.acme.billing.Invoice#issue");
        assertRejectedByBoth(entityWithHash);

        ObjectNode unknownHint = BriefDocuments.copy(base);
        ((ObjectNode) unknownHint.at("/glossary/0")).putArray("concernHints").add("Teleport");
        assertThat(BriefDocuments.schemaErrors(unknownHint)).isNotEmpty();
        assertThat(BriefReadiness.evaluate(BriefDocuments.bind(unknownHint),
                BriefDocuments.schemaVocabulary(), null).valid()).isFalse();
    }

    // ---- fixtures --------------------------------------------------------

    @Test
    @DisplayName("every fixture: the schema and BriefReadiness agree, and each planted gap is reported")
    void fixturesMeetTheirExpectations() throws Exception {
        record Expect(boolean schemaValid, boolean ready) {
        }
        Map<String, Expect> expected = new LinkedHashMap<>();
        expected.put("ready.brief.yaml", new Expect(true, true));
        expected.put("minimal.brief.yaml", new Expect(true, true));
        expected.put("gap-workflow-without-response.brief.yaml", new Expect(false, false));
        expected.put("gap-blocking-question-open.brief.yaml", new Expect(true, false));
        expected.put("gap-assumption-without-owner.brief.yaml", new Expect(false, false));
        expected.put("gap-re-encoded-structure.brief.yaml", new Expect(false, false));
        expected.put("gap-dangling-fulfilled-by.brief.yaml", new Expect(true, true));
        expected.put("empty.brief.yaml", new Expect(false, false));
        expected.put("empty-object.brief.yaml", new Expect(false, false));

        assertThat(fixtureNames()).as("a fixture without an expectation here is a gap nobody checks")
                .isEqualTo(new TreeSet<>(expected.keySet()));

        Set<String> vocabulary = BriefDocuments.schemaVocabulary();
        for (Map.Entry<String, Expect> e : expected.entrySet()) {
            JsonNode document = BriefDocuments.fixture(e.getKey());
            boolean schemaValid = BriefDocuments.schemaErrors(document).isEmpty();
            Brief brief = BriefDocuments.bind(document);
            BriefReadiness.Report report = BriefReadiness.evaluate(brief, vocabulary, null);

            assertThat(schemaValid).as("schema validity of %s", e.getKey()).isEqualTo(e.getValue().schemaValid());
            assertThat(report.valid()).as("BriefReadiness must agree with the schema on %s", e.getKey())
                    .isEqualTo(schemaValid);
            assertThat(report.ready()).as("readiness of %s", e.getKey()).isEqualTo(e.getValue().ready());
        }

        BriefReadiness.Report ready = BriefReadiness.evaluate(
                BriefDocuments.bind(BriefDocuments.fixture("ready.brief.yaml")));
        assertThat(ready.assumed()).containsExactly(
                new BriefReadiness.Assumption("Q1", "Head of Finance", "EU VAT only."));

        BriefReadiness.Report open = BriefReadiness.evaluate(
                BriefDocuments.bind(BriefDocuments.fixture("gap-blocking-question-open.brief.yaml")));
        assertThat(open.valid()).isTrue();
        assertThat(open.errors()).isEmpty();

        BriefReadiness.Report ownerless = BriefReadiness.evaluate(
                BriefDocuments.bind(BriefDocuments.fixture("gap-assumption-without-owner.brief.yaml")));
        assertThat(ownerless.errors()).extracting(BriefReadiness.Problem::path)
                .containsExactly("/openQuestions/0/owner");

        BriefReadiness.Report noResponse = BriefReadiness.evaluate(
                BriefDocuments.bind(BriefDocuments.fixture("gap-workflow-without-response.brief.yaml")));
        assertThat(noResponse.errors()).extracting(BriefReadiness.Problem::path)
                .containsExactly("/workflows/0/criteria/0/response");
    }

    @Test
    @DisplayName("a dangling fulfilledBy is reported unresolved against the source, unchecked without one")
    void danglingReferenceIsReported() {
        Brief brief = BriefDocuments.bind(BriefDocuments.fixture("gap-dangling-fulfilled-by.brief.yaml"));
        DomainMetadata customer = DomainMetadata.builder("Customer", "com.acme.crm")
                .actions(List.of(ActionMetadata.builder("enrol").build()))
                .build();

        BriefReadiness.Report checked = BriefReadiness.evaluate(brief, null, SourceReferences.of(List.of(customer)));
        assertThat(checked.unresolved()).containsExactly(
                new FulfilledBy(FulfilledBy.Kind.ACTION, "com.acme.crm.Customer#register"));
        assertThat(checked.unchecked()).isEmpty();

        BriefReadiness.Report unchecked = BriefReadiness.evaluate(brief);
        assertThat(unchecked.unchecked()).hasSize(2);
        assertThat(unchecked.unresolved()).isEmpty();
    }

    @Test
    @DisplayName("Jackson 3 ignores unknown properties by default, which is why the schema says to validate first")
    void readerRequirementIsReal() {
        assertThat(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES.enabledByDefault()).isFalse();
        JsonNode requirement = BriefDocuments.schemaDocument().get("x-exeris-reader-requirements").get(0);
        assertThat(requirement.get("id").asString()).isEqualTo("UNKNOWN_PROPERTIES_ARE_ERRORS");
    }

    // ---- helpers ---------------------------------------------------------

    /**
     * The components of {@link DomainMetadata} that carry a structure: those whose type, or whose
     * list element type, is a record. Scalars and enums ({@code dataScope}) are vocabulary a brief
     * may name, not structure it may copy.
     */
    static Set<String> structureComponents() {
        Set<String> out = new TreeSet<>();
        for (RecordComponent component : DomainMetadata.class.getRecordComponents()) {
            Type type = component.getGenericType();
            Class<?> carried = component.getType();
            if (type instanceof ParameterizedType parameterized
                    && parameterized.getActualTypeArguments()[0] instanceof Class<?> element) {
                carried = element;
            }
            if (carried.isRecord()) {
                out.add(component.getName());
            }
        }
        return out;
    }

    private static void reachable(Class<?> type, Set<String> into) {
        String name = type.getName().substring(type.getPackageName().length() + 1).replace('$', '.');
        if (!into.add(name) || !type.isRecord()) {
            return;
        }
        for (RecordComponent component : type.getRecordComponents()) {
            Class<?> carried = component.getType();
            if (component.getGenericType() instanceof ParameterizedType parameterized
                    && parameterized.getActualTypeArguments()[0] instanceof Class<?> element) {
                carried = element;
            }
            if (carried.isRecord() || carried.isEnum()) {
                reachable(carried, into);
            }
        }
    }

    private static Set<String> fixtureNames() throws Exception {
        URL root = BriefSchemaContractTest.class.getResource("/briefs");
        Path dir = Paths.get(URLDecoder.decode(root.getPath(), StandardCharsets.UTF_8));
        Set<String> out = new TreeSet<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.map(p -> p.getFileName().toString()).forEach(out::add);
        }
        return out;
    }

    private static void assertRejectedByBoth(JsonNode document) {
        assertThat(BriefDocuments.schemaErrors(document)).as("schema").isNotEmpty();
        Brief bound = BriefDocuments.bind(document);
        assertThat(bound).as("binds: the kind and ref are known properties").isNotNull();
        assertThat(BriefReadiness.evaluate(bound).valid()).as("BriefReadiness").isFalse();
    }

    private interface Visitor {
        void visit(String path, JsonNode node);
    }

    private static void walk(JsonNode node, String path, Visitor visitor) {
        visitor.visit(path, node);
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> child : node.properties()) {
                walk(child.getValue(), path + "/" + child.getKey(), visitor);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                walk(node.get(i), path + "/" + i, visitor);
            }
        }
    }

    /** Names the test shares with the emitter without depending on its module. */
    private static final class BriefSchemaNames {
        static final String CONCERN_HINT = "ConcernHint";

        private BriefSchemaNames() {
        }
    }
}
