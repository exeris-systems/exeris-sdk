package eu.exeris.sdk.tck;

import eu.exeris.sdk.sourcemodel.ast.ActionMetadata;
import eu.exeris.sdk.sourcemodel.ast.ActionParamMetadata;
import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.FieldMetadata;
import eu.exeris.sdk.sourcemodel.ast.GraphPropertyMetadata;
import eu.exeris.sdk.sourcemodel.ast.RelationshipMetadata;
import eu.exeris.sdk.sourcemodel.ast.SagaMetadata;
import eu.exeris.sdk.sourcemodel.ast.UIMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ADR-042 parity gate: a producer and a reader of the same source must agree.
 *
 * <p>"The reader reads what the processor writes" has been a discipline in this ecosystem, enforced
 * by whoever remembered it. It is the discipline that keeps three-way conflict detection meaningful
 * — detection compares a reader's view of edited source against a producer's baseline, so any
 * standing disagreement between them reads as user drift that never happened, and a mutation is
 * refused or applied over an edit on that basis.
 *
 * <p>Every parity defect this ecosystem has shipped had the same shape and the same reason for
 * going unnoticed: one side read an attribute under a key the other did not use, and <em>both
 * sides still produced well-formed output</em>. Nothing throws. There is no diagnostic. The only
 * way to see it is to compare the two, which is what this suite does.
 *
 * <p>Bind both halves and it runs:
 *
 * {@snippet lang="java" :
 * class MyParityTckTest extends AbstractMetadataParityTck {
 *     protected String produce(String src) { return MyProcessor.emitJson(src); }
 *     protected DomainMetadata read(String src) { return new MyReader().read(src); }
 * }
 * }
 *
 * <p>A facet the producer does not extract yet is declared through {@link #unsupportedFacets()} —
 * reading ahead of a producer manufactures exactly the drift this suite exists to catch, so an
 * unbuilt facet is a skip, not a failure.
 */
/*
 * S5960 suppressed: in a TCK the assertions are the shipped artifact, not residue. Full rationale
 * on {@code AbstractExerisTck}.
 */
@SuppressWarnings("java:S5960")
public abstract class AbstractMetadataParityTck extends AbstractExerisTck {

    /**
     * For subclasses; this type is extended, never instantiated directly.
     *
     * <p>Declared so the producer/reader parity suite carries a documented constructor rather than an implicit
     * one. It stays {@code public} rather than becoming {@code protected}: the implicit
     * constructor of a public class is public, so narrowing it here would be a binary
     * break on a published artifact — which the semver gate reports as
     * {@code CONSTRUCTOR_LESS_ACCESSIBLE}.
     */
    // java:S5993 asks for protected here, and is right in general — an abstract class's
    // constructor exists only for subclasses. It is wrong for this one: the constructor it
    // replaces was implicit and therefore public, so narrowing it breaks binary
    // compatibility on a published artifact, which japicmp reports as
    // CONSTRUCTOR_LESS_ACCESSIBLE.
    @SuppressWarnings("java:S5993")
    public AbstractMetadataParityTck() {
        // Nothing to initialize; declared so it carries a comment rather than being implicit.
    }

    private static final String FIELDS = "fields/";
    private static final String RELATIONSHIPS = "relationships/";
    private static final String ACTIONS = "actions/";
    private static final String SAGA = "saga";

    /**
     * Produces the build-time JSON for one entity — implement this with your own producer.
     *
     * @param entitySource Java source text of a single {@code @ExerisDomain} class
     * @return the JSON your build writes for it
     */
    protected abstract String produce(String entitySource);

    /**
     * Reads one entity into metadata — implement this with your own reader.
     *
     * @param entitySource the same source text
     * @return the metadata your reader produces for it
     */
    protected abstract DomainMetadata read(String entitySource);

    @Test
    @DisplayName("producer and reader agree on entity identity")
    void identityAgrees() {
        requireSupported(Facet.IDENTITY);
        eachEntity((name, produced, wasRead) -> {
            List<String> gaps = new ArrayList<>();
            compare(gaps, "entityName", produced.entityName(), wasRead.entityName());
            compare(gaps, "packageName", produced.packageName(), wasRead.packageName());
            return gaps;
        });
    }

    @Test
    @DisplayName("producer and reader agree on every field")
    void fieldsAgree() {
        requireSupported(Facet.FIELDS);
        boolean bounds = !unsupportedFacets().contains(Facet.VALIDATION_BOUNDS);
        eachEntity((name, produced, wasRead) -> {
            List<String> gaps = new ArrayList<>();
            Map<String, FieldMetadata> left = byKey(produced.fields(), FieldMetadata::name);
            Map<String, FieldMetadata> right = byKey(wasRead.fields(), FieldMetadata::name);
            compareKeySets(gaps, "fields", left, right);
            for (Map.Entry<String, FieldMetadata> entry : left.entrySet()) {
                FieldMetadata p = entry.getValue();
                FieldMetadata r = right.get(entry.getKey());
                if (r == null) {
                    continue;
                }
                String path = FIELDS + entry.getKey();
                compare(gaps, path + "/type", p.type(), r.type());
                compare(gaps, path + "/required", p.required(), r.required());
                if (bounds) {
                    compare(gaps, path + "/minLength", p.minLength(), r.minLength());
                    compare(gaps, path + "/maxLength", p.maxLength(), r.maxLength());
                    compare(gaps, path + "/min", p.min(), r.min());
                    compare(gaps, path + "/max", p.max(), r.max());
                    compare(gaps, path + "/pattern", p.pattern(), r.pattern());
                }
            }
            return gaps;
        });
    }

    @Test
    @DisplayName("producer and reader agree on every relationship, cardinality included")
    void relationshipsAgree() {
        requireSupported(Facet.RELATIONSHIPS);
        eachEntity((name, produced, wasRead) -> {
            List<String> gaps = new ArrayList<>();
            Map<String, RelationshipMetadata> left = byKey(produced.relationships(), AbstractMetadataParityTck::edgeKey);
            Map<String, RelationshipMetadata> right = byKey(wasRead.relationships(), AbstractMetadataParityTck::edgeKey);
            compareKeySets(gaps, "relationships", left, right);
            for (Map.Entry<String, RelationshipMetadata> entry : left.entrySet()) {
                RelationshipMetadata p = entry.getValue();
                RelationshipMetadata r = right.get(entry.getKey());
                if (r == null) {
                    continue;
                }
                String path = RELATIONSHIPS + entry.getKey();
                compare(gaps, path + "/type", p.type(), r.type());
                compare(gaps, path + "/targetEntity", p.targetEntity(), r.targetEntity());
                compare(gaps, path + "/mappedBy", p.mappedBy(), r.mappedBy());
            }
            return gaps;
        });
    }

    @Test
    @DisplayName("producer and reader agree on every action")
    void actionsAgree() {
        requireSupported(Facet.ACTIONS);
        eachEntity((name, produced, wasRead) -> {
            List<String> gaps = new ArrayList<>();
            Map<String, ActionMetadata> left = byKey(produced.actions(), ActionMetadata::name);
            Map<String, ActionMetadata> right = byKey(wasRead.actions(), ActionMetadata::name);
            compareKeySets(gaps, "actions", left, right);
            for (Map.Entry<String, ActionMetadata> entry : left.entrySet()) {
                ActionMetadata p = entry.getValue();
                ActionMetadata r = right.get(entry.getKey());
                if (r == null) {
                    continue;
                }
                compare(gaps, ACTIONS + entry.getKey() + "/httpMethod", p.httpMethod(), r.httpMethod());
            }
            return gaps;
        });
    }

    /**
     * The return type each action carries. The divergence this exists for is one side carrying
     * the type as written — {@code Receipt}, {@code List<Receipt>} — and the other the
     * fully-qualified name javac reports, or one side writing {@code "void"} where the other
     * writes nothing.
     */
    @Test
    @DisplayName("producer and reader agree on every action's return type")
    void actionResultTypesAgree() {
        requireSupported(Facet.ACTIONS);
        requireSupported(Facet.ACTION_RESULT_TYPE);
        eachEntity((name, produced, wasRead) -> {
            List<String> gaps = new ArrayList<>();
            Map<String, ActionMetadata> right = byKey(wasRead.actions(), ActionMetadata::name);
            for (Map.Entry<String, ActionMetadata> entry : byKey(produced.actions(), ActionMetadata::name).entrySet()) {
                ActionMetadata r = right.get(entry.getKey());
                if (r != null) {
                    compare(gaps, ACTIONS + entry.getKey() + "/resultType",
                            entry.getValue().resultType(), r.resultType());
                }
            }
            return gaps;
        });
    }

    /**
     * The saga's identity is the pair kernel ADR-064 addresses a plan by, so both halves are
     * compared. The version is the half a side can skip without emitting anything malformed: it
     * reports the default {@code 1}, the same source names two different plans, and — as with
     * every divergence this suite exists for — only a comparison of the two sides sees it.
     */
    @Test
    @DisplayName("producer and reader agree on the saga's identity, version included")
    void sagaIdentityAgrees() {
        requireSupported(Facet.SAGA);
        eachEntity((name, produced, wasRead) -> {
            List<String> gaps = new ArrayList<>();
            SagaMetadata p = produced.sagaMetadata();
            SagaMetadata r = wasRead.sagaMetadata();
            if (p == null && r == null) {
                return gaps;
            }
            if (p == null || r == null) {
                gaps.add(SAGA + " — " + (p == null ? "reader emitted one, producer did not"
                        : "producer emitted one, reader did not"));
                return gaps;
            }
            compare(gaps, SAGA + "/name", p.name(), r.name());
            compare(gaps, SAGA + "/version", p.version(), r.version());
            return gaps;
        });
    }

    /**
     * The compensation section, component by component. Two shapes of divergence are what this
     * exists for, and neither produces malformed output: one side drops a declared {@code 0} or
     * {@code false}, which then reads as "not declared"; or one side carries the failure handler
     * as written while the other carries the fully-qualified name.
     */
    @Test
    @DisplayName("producer and reader agree on the saga's compensation section")
    void sagaCompensationAgrees() {
        requireSupported(Facet.SAGA_COMPENSATION);
        eachEntity((name, produced, wasRead) -> {
            List<String> gaps = new ArrayList<>();
            SagaMetadata p = produced.sagaMetadata();
            SagaMetadata r = wasRead.sagaMetadata();
            if (p == null || r == null) {
                // Presence is the SAGA facet's comparison; here there is nothing to compare.
                return gaps;
            }
            compare(gaps, SAGA + "/compensationTimeout", p.compensationTimeout(), r.compensationTimeout());
            compare(gaps, SAGA + "/compensationMaxRetries", p.compensationMaxRetries(), r.compensationMaxRetries());
            compare(gaps, SAGA + "/compensationRetryDelay", p.compensationRetryDelay(), r.compensationRetryDelay());
            compare(gaps, SAGA + "/continueCompensationOnFailure",
                    p.continueCompensationOnFailure(), r.continueCompensationOnFailure());
            compare(gaps, SAGA + "/compensationDlq", p.compensationDlq(), r.compensationDlq());
            compare(gaps, SAGA + "/compensationFailureHandler",
                    p.compensationFailureHandler(), r.compensationFailureHandler());
            compare(gaps, SAGA + "/manualInterventionOnCompensationFailure",
                    p.manualInterventionOnCompensationFailure(), r.manualInterventionOnCompensationFailure());
            return gaps;
        });
    }

    /**
     * Route access and permissions, on the entity and on every action both sides emit. Each
     * element carries only what it declares, so an action that inherits the entity's requirement
     * carries neither component on either side; the nearest-declaration rule is applied by the
     * generator, from these values. The divergences this exists for produce well-formed output: a
     * side that drops {@code PUBLIC} as if ordinal 0 were a default, one that copies the entity's
     * declaration onto an action, or one that loses a permission or reorders the list.
     */
    @Test
    @DisplayName("producer and reader agree on route access and permissions, entity and action")
    void routeAccessAgrees() {
        requireSupported(Facet.ROUTE_ACCESS);
        eachEntity((name, produced, wasRead) -> {
            List<String> gaps = new ArrayList<>();
            compare(gaps, "routeAccess", produced.routeAccess(), wasRead.routeAccess());
            compare(gaps, "permissions", declared(produced.permissions()), declared(wasRead.permissions()));
            Map<String, ActionMetadata> left = byKey(produced.actions(), ActionMetadata::name);
            Map<String, ActionMetadata> right = byKey(wasRead.actions(), ActionMetadata::name);
            for (Map.Entry<String, ActionMetadata> entry : left.entrySet()) {
                ActionMetadata p = entry.getValue();
                ActionMetadata r = right.get(entry.getKey());
                if (r == null) {
                    // Presence is the ACTIONS facet's comparison.
                    continue;
                }
                String path = ACTIONS + entry.getKey();
                compare(gaps, path + "/routeAccess", p.routeAccess(), r.routeAccess());
                compare(gaps, path + "/permissions", declared(p.permissions()), declared(r.permissions()));
            }
            return gaps;
        });
    }

    /**
     * Every action parameter's binding: which parameters exist, where each is read from, under
     * which key, and whether it is required. A side that admits only {@code @ActionParam}
     * parameters, or reads a query parameter as a body one, still emits a well-formed action.
     */
    @Test
    @DisplayName("producer and reader agree on every action parameter's binding")
    void queryParamsAgree() {
        requireSupported(Facet.QUERY_PARAMS);
        eachEntity((name, produced, wasRead) -> {
            List<String> gaps = new ArrayList<>();
            Map<String, ActionMetadata> left = byKey(produced.actions(), ActionMetadata::name);
            Map<String, ActionMetadata> right = byKey(wasRead.actions(), ActionMetadata::name);
            for (Map.Entry<String, ActionMetadata> entry : left.entrySet()) {
                ActionMetadata r = right.get(entry.getKey());
                if (r == null) {
                    // Presence is the ACTIONS facet's comparison.
                    continue;
                }
                String path = ACTIONS + entry.getKey() + "/params";
                Map<String, ActionParamMetadata> pl = byKey(entry.getValue().params(), ActionParamMetadata::name);
                Map<String, ActionParamMetadata> pr = byKey(r.params(), ActionParamMetadata::name);
                compareKeySets(gaps, path, pl, pr);
                for (Map.Entry<String, ActionParamMetadata> param : pl.entrySet()) {
                    ActionParamMetadata p = param.getValue();
                    ActionParamMetadata q = pr.get(param.getKey());
                    if (q == null) {
                        continue;
                    }
                    String at = path + "/" + param.getKey();
                    compare(gaps, at + "/bindingSource", p.effectiveBindingSource(), q.effectiveBindingSource());
                    compare(gaps, at + "/queryKey", p.effectiveQueryKey(), q.effectiveQueryKey());
                    compare(gaps, at + "/required", p.required(), q.required());
                }
            }
            return gaps;
        });
    }

    @Test
    @DisplayName("producer and reader agree on the navigation entry")
    void navMenuAgrees() {
        requireSupported(Facet.NAV_MENU);
        eachEntity((name, produced, wasRead) -> {
            List<String> gaps = new ArrayList<>();
            compare(gaps, "navMenu", produced.navMenu(), wasRead.navMenu());
            return gaps;
        });
    }

    @Test
    @DisplayName("producer and reader agree on every tab")
    void tabsAgree() {
        requireSupported(Facet.TABS);
        eachEntity((name, produced, wasRead) -> {
            List<String> gaps = new ArrayList<>();
            Map<String, UIMetadata.UITabMetadata> left = byKey(tabsOf(produced), UIMetadata.UITabMetadata::name);
            Map<String, UIMetadata.UITabMetadata> right = byKey(tabsOf(wasRead), UIMetadata.UITabMetadata::name);
            compareKeySets(gaps, "tabs", left, right);
            for (Map.Entry<String, UIMetadata.UITabMetadata> entry : left.entrySet()) {
                UIMetadata.UITabMetadata r = right.get(entry.getKey());
                if (r != null) {
                    compare(gaps, "tabs/" + entry.getKey(), entry.getValue(), r);
                }
            }
            if (left.keySet().equals(right.keySet())) {
                compare(gaps, "tabs (order)", List.copyOf(left.keySet()), List.copyOf(right.keySet()));
            }
            return gaps;
        });
    }

    @Test
    @DisplayName("producer and reader agree on every graph property, in order")
    void graphPropertiesAgree() {
        requireSupported(Facet.GRAPH_PROPERTIES);
        eachEntity((name, produced, wasRead) -> {
            List<String> gaps = new ArrayList<>();
            compare(gaps, "graphMetadata/properties", propertiesOf(produced), propertiesOf(wasRead));
            return gaps;
        });
    }

    @Test
    @DisplayName("producer and reader agree on every UI group")
    void uiGroupsAgree() {
        requireSupported(Facet.UI_GROUPS);
        eachEntity((name, produced, wasRead) -> {
            List<String> gaps = new ArrayList<>();
            Map<String, UIMetadata.UIGroupMetadata> left = byKey(groupsOf(produced), UIMetadata.UIGroupMetadata::name);
            Map<String, UIMetadata.UIGroupMetadata> right = byKey(groupsOf(wasRead), UIMetadata.UIGroupMetadata::name);
            compareKeySets(gaps, "groups", left, right);
            for (Map.Entry<String, UIMetadata.UIGroupMetadata> entry : left.entrySet()) {
                UIMetadata.UIGroupMetadata r = right.get(entry.getKey());
                if (r != null) {
                    compare(gaps, "groups/" + entry.getKey(), entry.getValue(), r);
                }
            }
            if (left.keySet().equals(right.keySet())) {
                compare(gaps, "groups (order)", List.copyOf(left.keySet()), List.copyOf(right.keySet()));
            }
            return gaps;
        });
    }

    @Test
    @DisplayName("producer and reader agree on every field's blob")
    void blobsAgree() {
        requireSupported(Facet.BLOBS);
        eachEntity((name, produced, wasRead) -> {
            List<String> gaps = new ArrayList<>();
            Map<String, FieldMetadata> left = byKey(produced.fields(), FieldMetadata::name);
            Map<String, FieldMetadata> right = byKey(wasRead.fields(), FieldMetadata::name);
            for (Map.Entry<String, FieldMetadata> entry : left.entrySet()) {
                FieldMetadata r = right.get(entry.getKey());
                if (r != null) {
                    // Presence is the FIELDS facet's comparison.
                    compare(gaps, FIELDS + entry.getKey() + "/blob", entry.getValue().blob(), r.blob());
                }
            }
            return gaps;
        });
    }

    private static List<GraphPropertyMetadata> propertiesOf(DomainMetadata metadata) {
        return metadata.graphMetadata() == null ? null : metadata.graphMetadata().properties();
    }

    private static List<UIMetadata.UIGroupMetadata> groupsOf(DomainMetadata metadata) {
        return metadata.uiMetadata() == null ? null : metadata.uiMetadata().groups();
    }

    private static List<UIMetadata.UITabMetadata> tabsOf(DomainMetadata metadata) {
        return metadata.uiMetadata() == null ? null : metadata.uiMetadata().tabs();
    }

    /**
     * The saga's trigger, component by component. The divergences are a side that does not read the
     * trigger, one that drops the first enum constant as a default, and one that carries the source
     * as written rather than fully qualified.
     */
    @Test
    @DisplayName("producer and reader agree on the saga's trigger")
    void sagaTriggerAgrees() {
        requireSupported(Facet.SAGA_TRIGGER);
        eachEntity((name, produced, wasRead) -> {
            List<String> gaps = new ArrayList<>();
            SagaMetadata p = produced.sagaMetadata();
            SagaMetadata r = wasRead.sagaMetadata();
            if (p == null || r == null) {
                // Presence is the SAGA facet's comparison; here there is nothing to compare.
                return gaps;
            }
            SagaMetadata.SagaTrigger pt = p.trigger();
            SagaMetadata.SagaTrigger rt = r.trigger();
            if (pt == null || rt == null) {
                compare(gaps, SAGA + "/trigger", pt, rt);
                return gaps;
            }
            compare(gaps, SAGA + "/trigger/type", pt.type(), rt.type());
            compare(gaps, SAGA + "/trigger/source", pt.source(), rt.source());
            compare(gaps, SAGA + "/trigger/topic", pt.topic(), rt.topic());
            compare(gaps, SAGA + "/trigger/cronExpression", pt.cronExpression(), rt.cronExpression());
            return gaps;
        });
    }

    /** A comparison over one entity, returning the paths on which the two sides disagreed. */
    @FunctionalInterface
    private interface EntityComparison {
        List<String> compare(String entityName, DomainMetadata produced, DomainMetadata wasRead);
    }

    /**
     * Runs one comparison across the whole corpus and fails with every disagreement at once.
     *
     * @param comparison the per-entity comparison to run
     */
    private void eachEntity(EntityComparison comparison) {
        List<String> gaps = new ArrayList<>();
        for (String name : TckCorpus.entityNames()) {
            String source = TckCorpus.sourceOf(name);
            DomainMetadata produced = TckMappers.canonical().readValue(produce(source), DomainMetadata.class);
            DomainMetadata wasRead = read(source);
            for (String gap : comparison.compare(name, produced, wasRead)) {
                gaps.add(name + ": " + gap);
            }
        }
        assertThat(gaps)
                .withFailMessage(
                        "Producer and reader disagree on %d path(s):%n  %s%n%nNeither side failed "
                                + "producing this — both emitted well-formed output, which is why a "
                                + "divergence like this survives review. Under ADR-042 the reader "
                                + "reads what the producer writes: every path above would register as "
                                + "user drift during three-way detection and decide a mutation on it.",
                        gaps.size(), String.join("\n  ", gaps))
                .isEmpty();
    }

    /**
     * Records a gap when the producer's value and the reader's value differ.
     *
     * @param path the facet path being compared
     * @param produced the producer's value
     * @param wasRead the reader's value
     */
    private static void compare(List<String> gaps, String path, Object produced, Object wasRead) {
        if (!Objects.equals(produced, wasRead)) {
            gaps.add(path + " — producer: " + render(produced) + ", reader: " + render(wasRead));
        }
    }

    private static void compareKeySets(List<String> gaps, String path,
                                       Map<String, ?> produced, Map<String, ?> wasRead) {
        for (String key : produced.keySet()) {
            if (!wasRead.containsKey(key)) {
                gaps.add(path + " — producer emitted '" + key + "', reader did not");
            }
        }
        for (String key : wasRead.keySet()) {
            if (!produced.containsKey(key)) {
                gaps.add(path + " — reader emitted '" + key + "', producer did not");
            }
        }
    }

    private static <T> Map<String, T> byKey(List<T> values, Function<T, String> key) {
        Map<String, T> indexed = new LinkedHashMap<>();
        if (values != null) {
            for (T value : values) {
                indexed.put(key.apply(value), value);
            }
        }
        return indexed;
    }

    /**
     * A {@code permissions} list as declared: a missing list and an empty one both mean "none
     * declared", so a producer that omits the key and one that writes {@code []} agree.
     */
    private static List<String> declared(List<String> permissions) {
        return permissions == null ? List.of() : permissions;
    }

    private static String edgeKey(RelationshipMetadata edge) {
        return edge.fieldName() != null ? edge.fieldName() : edge.name();
    }

    private static String render(Object value) {
        return value == null ? "<absent>" : "'" + value + "'";
    }
}
