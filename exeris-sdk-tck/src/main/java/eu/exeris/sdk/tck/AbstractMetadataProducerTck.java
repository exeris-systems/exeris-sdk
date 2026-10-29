package eu.exeris.sdk.tck;

import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.FieldMetadata;
import eu.exeris.sdk.sourcemodel.ast.SagaMetadata;
import eu.exeris.sdk.sourcemodel.ast.UIMetadata;
import eu.exeris.sdk.sourcemodel.mutation.SchemaVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract every producer of {@code exeris-metadata/<entity>.json} must satisfy.
 *
 * <p>Extend it and implement {@link #produce(String)} to return the JSON your build emits for the
 * given entity source. What that costs a binder is a compile harness, not a rewrite — the
 * annotation processor in {@code exeris-tooling} already runs javac in its own tests.
 *
 * <p>Where {@link AbstractMetadataReaderTck} asks whether a reader understands source, this asks
 * whether a producer's output is something anyone else can trust: readable by a correctly
 * configured mapper, stamped so its age can be judged, and carrying the values the source declared
 * rather than the defaults a lost attribute leaves behind.
 */
/*
 * S5960 suppressed: in a TCK the assertions are the shipped artifact, not residue. Full rationale
 * on {@code AbstractExerisTck}.
 */
@SuppressWarnings("java:S5960")
public abstract class AbstractMetadataProducerTck extends AbstractExerisTck {

    /**
     * For subclasses; this type is extended, never instantiated directly.
     *
     * <p>Declared so the metadata-producer suite carries a documented constructor rather than an implicit
     * one. It stays {@code public} rather than becoming {@code protected}: the implicit
     * constructor of a public class is public, so narrowing it here would be a binary
     * break on a published artifact — which the semver gate reports as
     * {@code CONSTRUCTOR_LESS_ACCESSIBLE}, and did.
     */
    // java:S5993 asks for protected here, and is right in general — an abstract class's
    // constructor exists only for subclasses. It is wrong for this one: the constructor it
    // replaces was implicit and therefore public, so narrowing it breaks binary
    // compatibility on a published artifact. japicmp reported exactly that
    // (CONSTRUCTOR_LESS_ACCESSIBLE) when it was first written as protected.
    @SuppressWarnings("java:S5993")
    public AbstractMetadataProducerTck() {
        // Nothing to initialize; declared so it carries a comment rather than being implicit.
    }

    private static final String SCHEMA_VERSION = "schemaVersion";

    /**
     * Produces the metadata JSON for one entity.
     *
     * @param entitySource Java source text of a single {@code @ExerisDomain} class
     * @return the exact content your build writes to {@code exeris-metadata/<entity>.json}
     */
    protected abstract String produce(String entitySource);

    @Test
    @DisplayName("output is readable by a correctly configured mapper")
    void outputIsReadableByACorrectlyConfiguredMapper() {
        requireSupported(Facet.IDENTITY);
        for (String name : TckCorpus.entityNames()) {
            DomainMetadata metadata = read(name);
            assertThat(metadata.entityName())
                    .withFailMessage(
                            "Produced JSON for %s deserialized to entityName '%s'. Under Entity-First "
                                    + "(ADR-003) the class simple name is the identity, and a producer "
                                    + "disagreeing with a reader about it is the one divergence "
                                    + "three-way conflict detection cannot tolerate (ADR-042).",
                            name, metadata.entityName())
                    .isEqualTo(name);
        }
    }

    @Test
    @DisplayName("the schemaVersion baseline-trust field is stamped")
    void schemaVersionIsStamped() {
        requireSupported(Facet.BASELINE_TRUST);
        JsonNode root = tree(TckCorpus.ORDER);
        assertThat(root.has(SCHEMA_VERSION))
                .withFailMessage(
                        "No schemaVersion in the produced JSON. Without it a reader cannot tell an "
                                + "older baseline from a current one and must refuse it as untrusted "
                                + "(NO_BASELINE, ADR-042 obligation 5) — every applyMutation against "
                                + "this producer's output would fail closed.")
                .isTrue();
        assertThat(root.get(SCHEMA_VERSION).asString())
                .withFailMessage(
                        "schemaVersion is '%s' but this kit was built against '%s'. Note the value is "
                                + "SchemaVersion.CURRENT, deliberately decoupled from the Maven "
                                + "artifact version — a producer that stamps its own artifact version "
                                + "will drift the moment a release ships with no AST change.",
                        root.get(SCHEMA_VERSION).asString(), SchemaVersion.CURRENT)
                .isEqualTo(SchemaVersion.CURRENT);
    }

    @Test
    @DisplayName("baseline-trust fields are siblings of the metadata, not a wrapper around it")
    void baselineTrustFieldsAreSiblingsNotAWrapper() {
        requireSupported(Facet.BASELINE_TRUST);
        JsonNode root = tree(TckCorpus.ORDER);
        assertThat(root.has("entityName"))
                .withFailMessage(
                        "The produced JSON has no top-level entityName, so the trust fields were "
                                + "wrapped around the metadata rather than placed beside it. The "
                                + "shape matters to two readers at once: a plain DomainMetadata read "
                                + "must still work on this file (it ignores the two extra fields), "
                                + "and a BaselineTrust read of the same file must pick up just those "
                                + "two. A wrapper breaks the first.")
                .isTrue();
        if (root.has("sourceDigest")) {
            assertThat(root.get("sourceDigest").asString())
                    .withFailMessage("sourceDigest is present but blank — omit it instead; absent and "
                            + "empty mean different things to the staleness check.")
                    .isNotBlank();
        }
    }

    @Test
    @DisplayName("@Validation constraints reach the produced FieldMetadata")
    void validationConstraintsReachTheProducedField() {
        requireSupported(Facet.VALIDATION_BOUNDS);
        FieldMetadata reference = field(read(TckCorpus.ORDER), "reference");
        assertThat(reference.minLength()).isEqualTo(3);
        assertThat(reference.maxLength()).isEqualTo(32);
        assertThat(reference.pattern()).isEqualTo("^ORD-[0-9]+$");
    }

    @Test
    @DisplayName("a zero-valued bound survives serialization")
    void aZeroValuedBoundSurvivesSerialization() {
        requireSupported(Facet.VALIDATION_BOUNDS);
        JsonNode fields = tree(TckCorpus.ORDER).get("fields");
        assertThat(fields)
                .withFailMessage("The produced JSON for Order carries no fields array.")
                .isNotNull();
        JsonNode total = null;
        for (JsonNode f : fields) {
            if (f.has("name") && "totalCents".equals(f.get("name").asString())) {
                total = f;
            }
        }
        assertThat(total)
                .withFailMessage("No 'totalCents' entry in the produced fields array.")
                .isNotNull();
        assertThat(total.has("min"))
                .withFailMessage(
                        "@Validation(min = 0) did not survive onto the wire for Order.totalCents. This "
                                + "is the boxed-zero drop: under a class-level "
                                + "@JsonInclude(NON_DEFAULT), Jackson 3 treats Long(0) as empty and "
                                + "omits it, so a non-negativity floor reads back as no floor at all. "
                                + "The fix is a per-component @JsonInclude(NON_NULL) on the bound, "
                                + "which is what FieldMetadata carries.")
                .isTrue();
        assertThat(total.get("min").asLong()).isEqualTo(0L);
    }

    @Test
    @DisplayName("the saga version the source declares reaches the produced metadata")
    void sagaVersionReachesTheProducedMetadata() {
        requireSupported(Facet.SAGA);
        SagaMetadata saga = read(TckCorpus.ORDER).sagaMetadata();
        assertThat(saga)
                .withFailMessage("Order declares @Saga; the produced JSON carries no sagaMetadata.")
                .isNotNull();
        assertThat(saga.version())
                .withFailMessage(
                        "Order declares @Saga(version = 3) and the produced baseline says %d. The "
                                + "kernel addresses a saga plan by name and version (ADR-064), and "
                                + "generation takes the version from this metadata — so a producer "
                                + "that never reads the attribute, and reports the default 1, turns "
                                + "a bump meant to protect in-flight sagas into no bump at all, "
                                + "with nothing failing anywhere to say so.",
                        saga.version())
                .isEqualTo(3);
    }

    /**
     * Read from the tree rather than through the record, because what is asserted is the key: a
     * declared {@code 0} or {@code false} must be written, and a component that is {@code null}
     * when undeclared reads back the same whether the producer dropped the value or never had it.
     */
    @Test
    @DisplayName("the saga's compensation section reaches the produced JSON, zero and false included")
    void sagaCompensationReachesTheProducedJson() {
        requireSupported(Facet.SAGA_COMPENSATION);
        JsonNode saga = tree(TckCorpus.ORDER).path("sagaMetadata");
        assertThat(saga.isObject())
                .withFailMessage("Order declares @Saga; the produced JSON carries no sagaMetadata.")
                .isTrue();
        assertThat(saga.path("compensationMaxRetries").isNumber() && saga.path("compensationMaxRetries").asInt() == 0)
                .withFailMessage(
                        "Order declares @Saga(compensationMaxRetries = 0) and the produced "
                                + "sagaMetadata has compensationMaxRetries = %s. A missing key means "
                                + "'not declared' to every reader, and the annotation default, 5, "
                                + "then applies.",
                        saga.path("compensationMaxRetries"))
                .isTrue();
        for (String key : List.of("continueCompensationOnFailure", "manualInterventionOnCompensationFailure")) {
            assertThat(saga.path(key).isBoolean() && !saga.path(key).asBoolean())
                    .withFailMessage(
                            "Order declares @Saga(%s = false) and the produced sagaMetadata has "
                                    + "%s = %s. A missing key means 'not declared' to every "
                                    + "reader, and the annotation default, true, is the opposite.",
                            key, key, saga.path(key))
                    .isTrue();
        }
        String handler = TckCorpus.packageName() + ".Order.CompensationEscalation";
        assertThat(saga.path("compensationFailureHandler").asString(""))
                .withFailMessage(
                        "Order declares compensationFailureHandler = Order.CompensationEscalation.class "
                                + "and the produced sagaMetadata names '%s'. The component carries "
                                + "the fully-qualified name, %s.",
                        saga.path("compensationFailureHandler"), handler)
                .isEqualTo(handler);
        assertThat(saga.path("compensationTimeout").asString("")).isEqualTo("PT2M");
        assertThat(saga.path("compensationRetryDelay").asString("")).isEqualTo("PT30S");
        assertThat(saga.path("compensationDlq").asString("")).isEqualTo("sales.order-compensation.dlq");
    }

    /**
     * Every route-access declaration reaches the produced metadata, and {@code PUBLIC} is written
     * as a key. Read from the tree for that second half: a missing {@code routeAccess} means
     * "declared nothing" to every reader, so a {@code PUBLIC} the serializer dropped as ordinal 0
     * turns a route the author opened into one nobody spoke about.
     */
    @Test
    @DisplayName("route access and permissions reach the produced JSON, PUBLIC included")
    void routeAccessReachesTheProducedJson() {
        requireSupported(Facet.ROUTE_ACCESS);
        List<String> gaps = new ArrayList<>();
        for (String name : TckCorpus.entityNames()) {
            gaps.addAll(CorpusRouteAccess.gaps(read(name)));
        }
        JsonNode product = tree(TckCorpus.PRODUCT);
        if (!"PUBLIC".equals(product.path("routeAccess").asString(""))) {
            gaps.add(TckCorpus.PRODUCT + "/routeAccess — no PUBLIC key in the produced JSON");
        }
        boolean trackIsPublic = false;
        for (JsonNode action : tree(TckCorpus.ORDER).path("actions")) {
            if ("track".equals(action.path("name").asString(""))) {
                trackIsPublic = "PUBLIC".equals(action.path("routeAccess").asString(""));
            }
        }
        if (!trackIsPublic) {
            gaps.add(TckCorpus.ORDER + "/actions/track/routeAccess — no PUBLIC key in the produced JSON");
        }
        assertThat(gaps)
                .withFailMessage(
                        "The produced route access disagrees with the corpus on %d path(s):%n  %s%n%n"
                                + "Each element carries only what it declares, and PUBLIC is written: "
                                + "absence is how the metadata says 'declared nothing'.",
                        gaps.size(), String.join("\n  ", gaps))
                .isEmpty();
    }

    /**
     * Read from the tree, so a producer that writes no {@code resultType} key fails on the key
     * rather than on a {@code null} that also stands for "not extracted".
     */
    @Test
    @DisplayName("each action's return type reaches the produced JSON, fully qualified, void as \"void\"")
    void actionResultTypeReachesTheProducedJson() {
        requireSupported(Facet.ACTIONS);
        requireSupported(Facet.ACTION_RESULT_TYPE);
        JsonNode actions = tree(TckCorpus.ORDER).path("actions");
        for (Map.Entry<String, String> expected : TckCorpus.orderResultTypes().entrySet()) {
            JsonNode action = actions.valueStream()
                    .filter(candidate -> expected.getKey().equals(candidate.path("name").asString("")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Order declares @Action(name = \""
                            + expected.getKey() + "\"); the produced JSON carries no such action."));
            assertThat(action.path("resultType").asString(""))
                    .withFailMessage(
                            "Order's action '%s' was produced with resultType %s. The component "
                                    + "carries the return type as javac reports it, %s, and \"void\" for "
                                    + "an action that returns nothing; a missing key means the producer "
                                    + "did not extract it.",
                            expected.getKey(), action.path("resultType"), expected.getValue())
                    .isEqualTo(expected.getValue());
        }
    }

    /**
     * Read from the tree, so that what is asserted is the key a declared {@code false} must be
     * written under, not the record default it reads back as when dropped.
     */
    @Test
    @DisplayName("@QueryParam's binding source, key and declared false reach the produced JSON")
    void queryParamsReachTheProducedJson() {
        requireSupported(Facet.QUERY_PARAMS);
        JsonNode search = element(tree(TckCorpus.ORDER).path("actions"), "search",
                "Order declares @Action(name = \"search\"); the produced JSON carries no such action.");
        JsonNode params = search.path("params");
        String admitted = "A parameter carrying @QueryParam alone is an action parameter.";
        JsonNode term = element(params, "term", "The produced 'search' action carries no parameter 'term'. "
                + admitted);
        assertThat(term.path("bindingSource").asString(""))
                .withFailMessage(
                        "Order.search(term) carries @QueryParam(\"q\") and the produced parameter "
                                + "has bindingSource = %s. Absent means BODY, so a generated handler "
                                + "reads it from the request document, where no client sends it.",
                        term.path("bindingSource"))
                .isEqualTo("QUERY");
        assertThat(term.path("queryKey").asString(""))
                .withFailMessage(
                        "Order.search(term) is read under the query key 'q' and the produced "
                                + "parameter has queryKey = %s.",
                        term.path("queryKey"))
                .isEqualTo("q");
        JsonNode limit = element(params, "limit", "The produced 'search' action carries no parameter "
                + "'limit'. " + admitted);
        assertThat(limit.path("bindingSource").asString("")).isEqualTo("QUERY");
        assertThat(limit.path("required").isBoolean() && !limit.path("required").asBoolean())
                .withFailMessage(
                        "Order.search(limit) declares @QueryParam(required = false) and the produced "
                                + "parameter has required = %s.",
                        limit.path("required"))
                .isTrue();
    }

    @Test
    @DisplayName("@NavMenu reaches the produced JSON, a zero order included")
    void navMenuReachesTheProducedJson() {
        requireSupported(Facet.NAV_MENU);
        JsonNode nav = tree(TckCorpus.ORDER).path("navMenu");
        assertThat(nav.isObject())
                .withFailMessage("Order declares @NavMenu; the produced JSON carries no navMenu.")
                .isTrue();
        assertThat(nav.path("order").isNumber() && nav.path("order").asInt() == 0)
                .withFailMessage(
                        "Order declares @NavMenu(order = 0) and the produced navMenu has order = %s. "
                                + "A missing key means 'not declared' to every reader, and the "
                                + "default 999 then applies.",
                        nav.path("order"))
                .isTrue();
        assertThat(nav.path("group").asString("")).isEqualTo("Sales");
        assertThat(nav.path("label").asString("")).isEqualTo("Orders");
        assertThat(nav.path("divider").asString("")).isEqualTo("AFTER");
        assertThat(nav.path("roles").isArray() && nav.path("roles").size() == 1
                && "SALES".equals(nav.path("roles").get(0).asString(""))).isTrue();
    }

    @Test
    @DisplayName("@Tab reaches the produced JSON on an entity with no @UI, a zero order included")
    void tabsReachTheProducedJson() {
        requireSupported(Facet.TABS);
        JsonNode tabs = tree(TckCorpus.ORDER).path("uiMetadata").path("tabs");
        assertThat(tabs.isArray() && tabs.size() == 2)
                .withFailMessage(
                        "Order's fields declare two tabs and the type no @UI; the produced "
                                + "uiMetadata.tabs is %s.",
                        tabs)
                .isTrue();
        JsonNode summary = tabs.get(0);
        assertThat(summary.path("name").asString("")).isEqualTo("summary");
        assertThat(summary.path("order").isNumber() && summary.path("order").asInt() == 0)
                .withFailMessage(
                        "Order.reference declares @Tab(order = 0) and the produced tab has order = %s. "
                                + "A missing key means 'not declared', and the default 999 then applies.",
                        summary.path("order"))
                .isTrue();
        assertThat(summary.path("fields").toString()).isEqualTo("[\"reference\",\"totalCents\"]");
        assertThat(tabs.get(1).path("name").asString("")).isEqualTo("notes");
        assertThat(tabs.get(1).path("lazy").asBoolean(false)).isTrue();
    }

    @Test
    @DisplayName("@GraphProperty reaches the produced JSON, AUTO untyped and a renamed property with its field")
    void graphPropertiesReachTheProducedJson() {
        requireSupported(Facet.GRAPH_PROPERTIES);
        JsonNode properties = tree(TckCorpus.PRODUCT).path("graphMetadata").path("properties");
        assertThat(properties.isArray() && properties.size() == 2)
                .withFailMessage(
                        "Product's fields declare two @GraphProperty; the produced "
                                + "graphMetadata.properties is %s. An absent key means the producer "
                                + "does not extract @GraphProperty.",
                        properties)
                .isTrue();
        JsonNode name = properties.get(0);
        assertThat(name.path("name").asString("")).isEqualTo("name");
        assertThat(name.path("indexed").asBoolean(false)).isTrue();
        assertThat(name.has("type"))
                .withFailMessage(
                        "Product.name declares no graphType and the produced property has type %s. "
                                + "AUTO is written as no type at all.",
                        name.path("type"))
                .isFalse();
        JsonNode sku = properties.get(1);
        assertThat(sku.path("name").asString("")).isEqualTo("skuCode");
        assertThat(sku.path("type").asString("")).isEqualTo("STRING");
        assertThat(sku.path("field").asString(""))
                .withFailMessage(
                        "Product.sku is the graph property 'skuCode' and the produced property has "
                                + "field %s.",
                        sku.path("field"))
                .isEqualTo("sku");
    }

    @Test
    @DisplayName("@UIGroup reaches the produced JSON on an entity with no @UI, defaults written out")
    void uiGroupsReachTheProducedJson() {
        requireSupported(Facet.UI_GROUPS);
        JsonNode groups = tree(TckCorpus.CUSTOMER).path("uiMetadata").path("groups");
        assertThat(groups.isArray() && groups.size() == 2)
                .withFailMessage(
                        "Customer's fields declare two groups and the type no @UI; the produced "
                                + "uiMetadata.groups is %s.",
                        groups)
                .isTrue();
        JsonNode identity = groups.get(0);
        assertThat(identity.path("name").asString("")).isEqualTo("identity");
        assertThat(identity.path("fields").toString()).isEqualTo("[\"email\",\"displayName\"]");
        assertThat(identity.path("collapsible").isBoolean() && identity.path("collapsible").asBoolean())
                .withFailMessage(
                        "No 'identity' member declares collapsible and the produced group has "
                                + "collapsible = %s. The annotation default, true, is written out.",
                        identity.path("collapsible"))
                .isTrue();
        assertThat(identity.path("gridSpan").asInt(0)).isEqualTo(UIMetadata.UIGroupMetadata.DEFAULT_GRID_SPAN);
        JsonNode media = groups.get(1);
        assertThat(media.path("name").asString("")).isEqualTo("media");
        assertThat(media.path("collapsible").isBoolean() && !media.path("collapsible").asBoolean())
                .withFailMessage(
                        "Customer.avatar declares @UIGroup(collapsible = false) and the produced group "
                                + "has collapsible = %s.",
                        media.path("collapsible"))
                .isTrue();
    }

    @Test
    @DisplayName("@Blob reaches the produced JSON, on a field without @Field too")
    void blobsReachTheProducedJson() {
        requireSupported(Facet.BLOBS);
        JsonNode fields = tree(TckCorpus.CUSTOMER).path("fields");
        JsonNode avatar = element(fields, "avatar", "The produced Customer carries no field 'avatar'.");
        assertThat(avatar.path("blob").path("contentTypes").toString())
                .withFailMessage(
                        "Customer.avatar declares @Blob(contentTypes = {\"image/png\", \"image/jpeg\"}) "
                                + "and the produced field has blob %s.",
                        avatar.path("blob"))
                .isEqualTo("[\"image/png\",\"image/jpeg\"]");
        JsonNode contract = element(fields, "contract", "The produced Customer carries no field 'contract'.");
        assertThat(contract.path("blob").path("container").asString(""))
                .withFailMessage(
                        "Customer.contract declares @Blob(container = \"contracts\") and no @Field, and "
                                + "the produced field has blob %s.",
                        contract.path("blob"))
                .isEqualTo("contracts");
    }

    /**
     * The element of a JSON array whose {@code name} is {@code name}.
     *
     * @param array the array to search
     * @param name the name to find
     * @param failure the message to fail with when there is none
     * @return the element
     * @throws AssertionError when the array holds no element of that name
     */
    private static JsonNode element(JsonNode array, String name, String failure) {
        for (JsonNode candidate : array) {
            if (name.equals(candidate.path("name").asString(""))) {
                return candidate;
            }
        }
        throw new AssertionError(failure);
    }

    /**
     * Produces the entity's metadata JSON and parses it as a tree.
     *
     * @param entityName one of {@link TckCorpus#entityNames()}
     * @return the producer's JSON for it, parsed
     */
    private JsonNode tree(String entityName) {
        return TckMappers.canonical().readTree(produce(TckCorpus.sourceOf(entityName)));
    }

    /**
     * Produces the entity's metadata JSON and deserializes it.
     *
     * @param entityName one of {@link TckCorpus#entityNames()}
     * @return the producer's JSON for it, deserialized through the canonical mapper posture
     */
    private DomainMetadata read(String entityName) {
        return TckMappers.canonical()
                .readValue(produce(TckCorpus.sourceOf(entityName)), DomainMetadata.class);
    }

    /**
     * Returns the named field, failing the test when it was not produced.
     *
     * @param metadata the entity to look in
     * @param fieldName the field to find
     * @return that field's metadata
     * @throws AssertionError when the producer emitted no such field
     */
    private static FieldMetadata field(DomainMetadata metadata, String fieldName) {
        List<FieldMetadata> fields = metadata.fields() == null ? List.of() : metadata.fields();
        return fields.stream()
                .filter(f -> fieldName.equals(f.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No field '" + fieldName + "' was produced for "
                        + metadata.entityName() + "; got " + fields.stream().map(FieldMetadata::name).toList()));
    }
}
