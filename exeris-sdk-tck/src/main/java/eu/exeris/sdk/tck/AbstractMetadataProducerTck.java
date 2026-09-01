package eu.exeris.sdk.tck;

import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.FieldMetadata;
import eu.exeris.sdk.sourcemodel.mutation.SchemaVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.List;

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
    public AbstractMetadataProducerTck() {
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

    /**
     * @param entityName one of {@link TckCorpus#entityNames()}
     * @return the producer's JSON for it, parsed
     */
    private JsonNode tree(String entityName) {
        return TckMappers.canonical().readTree(produce(TckCorpus.sourceOf(entityName)));
    }

    /**
     * @param entityName one of {@link TckCorpus#entityNames()}
     * @return the producer's JSON for it, deserialized through the canonical mapper posture
     */
    private DomainMetadata read(String entityName) {
        return TckMappers.canonical()
                .readValue(produce(TckCorpus.sourceOf(entityName)), DomainMetadata.class);
    }

    /**
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
