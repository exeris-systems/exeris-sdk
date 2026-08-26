package eu.exeris.sdk.tck;

import eu.exeris.sdk.sourcemodel.ast.ActionMetadata;
import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.FieldMetadata;
import eu.exeris.sdk.sourcemodel.ast.RelationshipMetadata;
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
 * <pre>{@code
 * class MyParityTckTest extends AbstractMetadataParityTck {
 *     protected String produce(String src) { return MyProcessor.emitJson(src); }
 *     protected DomainMetadata read(String src) { return new MyReader().read(src); }
 * }
 * }</pre>
 *
 * <p>A facet the producer does not extract yet is declared through {@link #unsupportedFacets()} —
 * reading ahead of a producer manufactures exactly the drift this suite exists to catch, so an
 * unbuilt facet is a skip, not a failure.
 */
public abstract class AbstractMetadataParityTck extends AbstractExerisTck {

    /**
     * @param entitySource Java source text of a single {@code @ExerisDomain} class
     * @return the JSON your build writes for it
     */
    protected abstract String produce(String entitySource);

    /**
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
            for (String field : left.keySet()) {
                FieldMetadata p = left.get(field);
                FieldMetadata r = right.get(field);
                if (r == null) {
                    continue;
                }
                compare(gaps, "fields/" + field + "/type", p.type(), r.type());
                compare(gaps, "fields/" + field + "/required", p.required(), r.required());
                if (bounds) {
                    compare(gaps, "fields/" + field + "/minLength", p.minLength(), r.minLength());
                    compare(gaps, "fields/" + field + "/maxLength", p.maxLength(), r.maxLength());
                    compare(gaps, "fields/" + field + "/min", p.min(), r.min());
                    compare(gaps, "fields/" + field + "/max", p.max(), r.max());
                    compare(gaps, "fields/" + field + "/pattern", p.pattern(), r.pattern());
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
            for (String edge : left.keySet()) {
                RelationshipMetadata p = left.get(edge);
                RelationshipMetadata r = right.get(edge);
                if (r == null) {
                    continue;
                }
                compare(gaps, "relationships/" + edge + "/type", p.type(), r.type());
                compare(gaps, "relationships/" + edge + "/targetEntity", p.targetEntity(), r.targetEntity());
                compare(gaps, "relationships/" + edge + "/mappedBy", p.mappedBy(), r.mappedBy());
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
            for (String action : left.keySet()) {
                ActionMetadata p = left.get(action);
                ActionMetadata r = right.get(action);
                if (r == null) {
                    continue;
                }
                compare(gaps, "actions/" + action + "/httpMethod", p.httpMethod(), r.httpMethod());
            }
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

    private static String edgeKey(RelationshipMetadata edge) {
        return edge.fieldName() != null ? edge.fieldName() : edge.name();
    }

    private static String render(Object value) {
        return value == null ? "<absent>" : "'" + value + "'";
    }
}
