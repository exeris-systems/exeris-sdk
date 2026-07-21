package eu.exeris.sdk.composition;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Makes the documented deserialization contract self-verifying. {@link CapManifest} is annotation-free
 * by design, so two things can only be proven by actually binding producer JSON: that the consumer
 * mapper config in the {@link CapManifest} javadoc is sufficient, and that the record component names
 * match the wire keys the tooling emits (notably {@code "module"} for the body, and a {@code version}
 * omitted for an unversioned provide).
 *
 * <p>The mapper mirrors the AST wire-format reader (a {@code JsonMapper} with
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} and {@code FAIL_ON_NULL_FOR_PRIMITIVES} disabled). jackson-databind
 * is a {@code test}-scope dependency only — it never reaches the published artifact's dependency tree.
 */
class CapManifestDeserializationTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
            .build();

    /** The same versioned golden the {@link CompositionBindingTest} pins, closing the wire → binding loop. */
    private static final String GOLDEN_VERSIONED =
            "sha256:83aae84863de8480b0c1ec943f7d350900a1ff2aab78b4c311684ca2ecc79e96";

    private static final String GOLDEN_UNVERSIONED =
            "sha256:75f3616837c895cafb566e04f14f3349dbc32561256a41e7d6ae5c912061262c";

    /**
     * A realistic emitted manifest: producer-only fields the consumer schema does not model
     * ({@code name}, {@code packageName}, per-module {@code requires}, top-level
     * {@code resolutions}, {@code warnings}) are present and must be ignored, the body is nested
     * under the wire key {@code "module"}, and the module body carries the {@code lifecycleOwner}
     * the producer emits {@code NON_NULL} (Audit has one; Billing, hook-less, omits the field).
     */
    private static final String PRODUCER_JSON = """
            {
              "schemaVersion": 2,
              "stamp": {
                "validated": true,
                "compositionVersion": "0.0.0",
                "contentBinding": "sha256:83aae84863de8480b0c1ec943f7d350900a1ff2aab78b4c311684ca2ecc79e96"
              },
              "modules": [
                {
                  "name": "Audit",
                  "packageName": "com.app",
                  "qualifiedName": "com.app.Audit",
                  "module": {
                    "provides": [ { "service": "com.api.AuditLog", "version": "1.0.0" } ],
                    "requires": [],
                    "lifecycleOwner": "com.app.AuditLifecycle"
                  }
                },
                {
                  "name": "Billing",
                  "packageName": "com.app",
                  "qualifiedName": "com.app.Billing",
                  "module": {
                    "provides": [
                      { "service": "com.api.Invoice", "version": "2.0.0" },
                      { "service": "com.api.PaymentApi", "version": "1.2.0" }
                    ]
                  }
                }
              ],
              "resolutions": [],
              "initOrder": [ "com.app.Audit", "com.app.Billing" ],
              "warnings": []
            }
            """;

    @Test
    void deserializesProducerManifestIgnoringProducerOnlyFields() {
        CapManifest manifest = MAPPER.readValue(PRODUCER_JSON, CapManifest.class);

        assertThat(manifest.schemaVersion()).isEqualTo(2);
        assertThat(manifest.stamp().validated()).isTrue();
        assertThat(manifest.initOrder()).containsExactly("com.app.Audit", "com.app.Billing");
        // The body bound under the wire key "module" — not null, proving the component name matches.
        assertThat(manifest.modules()).hasSize(2);
        assertThat(manifest.modules().getFirst().module()).isNotNull();
        assertThat(manifest.modules().getFirst().module().provides()).hasSize(1);
    }

    @Test
    void bindingFromDeserializedManifestMatchesGolden() {
        // Closes the loop: the binding recomputed from the deserialized wire equals the stamp it carries.
        CapManifest manifest = MAPPER.readValue(PRODUCER_JSON, CapManifest.class);
        assertThat(CompositionBinding.compute(manifest)).isEqualTo(GOLDEN_VERSIONED);
        assertThat(manifest.stamp().contentBinding()).isEqualTo(GOLDEN_VERSIONED);
    }

    @Test
    void lifecycleOwnerBindsFromModuleBodyAndReadsNullWhenAbsent() {
        // 0.9.0 consumer-model growth: the producer has emitted lifecycleOwner (NON_NULL, inside the
        // module body) since the tooling processor's capability extraction; the consumer schema now
        // binds it instead of ignoring it. Absent field ⇒ null ⇒ the cap declares no lifecycle hooks.
        CapManifest manifest = MAPPER.readValue(PRODUCER_JSON, CapManifest.class);

        assertThat(manifest.modules().getFirst().module().lifecycleOwner())
                .isEqualTo("com.app.AuditLifecycle");
        assertThat(manifest.modules().get(1).module().lifecycleOwner()).isNull();
        // Blank normalizes to null in the compact constructor — same identity as absent (no hooks).
        assertThat(new CapManifest.ModuleBody(null, "   ").lifecycleOwner()).isNull();
    }

    @Test
    void omittedVersionDeserializesToNullUnversionedProvide() {
        // The producer's @JsonInclude(NON_NULL) omits a null version entirely; the consumer must read
        // it back as null and the binding must normalize null -> "" (NOT the literal "null").
        String unversionedJson = """
                {
                  "schemaVersion": 2,
                  "stamp": { "validated": true, "compositionVersion": "0.0.0", "contentBinding": "x" },
                  "modules": [
                    { "qualifiedName": "com.app.Solo",
                      "module": { "provides": [ { "service": "com.api.Thing" } ] } }
                  ],
                  "initOrder": [ "com.app.Solo" ]
                }
                """;
        CapManifest manifest = MAPPER.readValue(unversionedJson, CapManifest.class);

        assertThat(manifest.modules().getFirst().module().provides().getFirst().version()).isNull();
        assertThat(CompositionBinding.compute(manifest)).isEqualTo(GOLDEN_UNVERSIONED);
    }

    @Test
    void absentSchemaVersionDeserializesToZeroNotThrow() {
        // FAIL_ON_NULL_FOR_PRIMITIVES=false: a missing primitive reads as 0, to be rejected by the
        // asserter's schema-floor handshake rather than throwing mid-parse.
        String noSchemaVersion = """
                {
                  "stamp": { "validated": true, "compositionVersion": "0.0.0", "contentBinding": "x" },
                  "modules": []
                }
                """;
        CapManifest manifest = MAPPER.readValue(noSchemaVersion, CapManifest.class);
        assertThat(manifest.schemaVersion()).isZero();
    }
}
