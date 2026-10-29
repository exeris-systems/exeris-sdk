package eu.exeris.sdk.brief;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The reading side of the brief contract as a consumer would build it: YAML in, validated against
 * the emitted {@code brief-schema.json} with a draft 2020-12 validator, bound to {@link Brief}
 * with unknown properties failing.
 */
final class BriefDocuments {

    static final String SCHEMA_RESOURCE = "/META-INF/exeris/brief-schema.json";
    static final String CATALOG_RESOURCE = "/META-INF/exeris/annotation-catalog.json";

    static final ObjectMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();
    static final ObjectMapper YAML = YAMLMapper.builder().build();

    private static JsonNode schemaDocument;
    private static Schema schema;

    private BriefDocuments() {
    }

    static synchronized JsonNode schemaDocument() {
        if (schemaDocument == null) {
            schemaDocument = readResource(SCHEMA_RESOURCE,
                    "It is emitted by BriefSchemaProcessor on this module's annotation processor path; "
                            + "an absent file means the processor did not run.");
        }
        return schemaDocument;
    }

    static synchronized Schema schema() {
        if (schema == null) {
            schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                    .getSchema(schemaDocument());
        }
        return schema;
    }

    static JsonNode catalog() {
        return readResource(CATALOG_RESOURCE,
                "It ships in the exeris-sdk-annotations jar, a test dependency of this module.");
    }

    private static JsonNode readResource(String resource, String hint) {
        try (InputStream in = BriefDocuments.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new AssertionError(resource + " is missing. " + hint);
            }
            return JSON.readTree(in);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /** A fixture under {@code briefs/}, parsed; {@code null} when the file holds no document. */
    static JsonNode fixture(String name) {
        try (InputStream in = BriefDocuments.class.getResourceAsStream("/briefs/" + name)) {
            if (in == null) {
                throw new AssertionError("no fixture briefs/" + name);
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonNode tree = YAML.readTree(text);
            return tree == null || tree.isMissingNode() || tree.isNull() ? null : tree;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /** Schema errors for a document; an absent document is never valid. */
    static List<String> schemaErrors(JsonNode document) {
        if (document == null) {
            return List.of("no document");
        }
        return schema().validate(document).stream().map(Object::toString).toList();
    }

    /** Binds strictly; {@code null} when the document is absent or does not bind. */
    static Brief bind(JsonNode document) {
        if (document == null) {
            return null;
        }
        try {
            return JSON.treeToValue(document, Brief.class);
        } catch (RuntimeException _) {
            return null;
        }
    }

    static ObjectNode copy(JsonNode node) {
        return (ObjectNode) node.deepCopy();
    }

    /** The concern-hint names the schema admits. */
    static Set<String> schemaVocabulary() {
        Set<String> names = new TreeSet<>();
        schemaDocument().get("$defs").get("ConcernHint").get("enum").forEach(n -> names.add(n.asString()));
        return names;
    }
}
