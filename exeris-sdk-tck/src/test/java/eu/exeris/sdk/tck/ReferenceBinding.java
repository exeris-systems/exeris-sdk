package eu.exeris.sdk.tck;

import eu.exeris.sdk.sourcemodel.ast.ActionMetadata;
import eu.exeris.sdk.sourcemodel.ast.DataScope;
import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.FieldMetadata;
import eu.exeris.sdk.sourcemodel.ast.RelationshipMetadata;
import eu.exeris.sdk.sourcemodel.mutation.BaselineTrust;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * A conforming binding, hand-built.
 *
 * <p>Exists to make the kit's own cases provably non-vacuous: every self-test drives a contract
 * method against this and against a fake broken in one specific way, asserting the first passes and
 * the second fails. Without the pair, a case asserting a negative would stay green whether or not
 * it enforced anything — a non-conforming implementation would pass the suite and the contract
 * would look enforced while nothing enforced it.
 *
 * <p>It is also the readable statement of what the corpus <em>means</em>: the values here are what
 * a correct reader and a correct producer must both arrive at from the shipped sources.
 */
final class ReferenceBinding {

    private ReferenceBinding() {
    }

    static DomainMetadata metadataFor(String entityName) {
        return switch (entityName) {
            case TckCorpus.ORDER -> order();
            case TckCorpus.CUSTOMER -> customer();
            default -> throw new IllegalArgumentException("Not a corpus entity: " + entityName);
        };
    }

    /** @param source a corpus source; the entity is identified by the class it declares */
    static DomainMetadata readFrom(String source) {
        return metadataFor(entityNameOf(source));
    }

    /** @return the JSON a conforming producer writes, trust siblings included */
    static String produceFrom(String source) {
        return json(readFrom(source), true);
    }

    static String entityNameOf(String source) {
        for (String name : TckCorpus.entityNames()) {
            if (source.contains("public class " + name + " ")) {
                return name;
            }
        }
        throw new IllegalArgumentException("No corpus entity declared in the given source");
    }

    /**
     * @param metadata the entity to serialize
     * @param withTrust whether to stamp the two ADR-042 baseline-trust siblings
     * @return the serialized baseline
     */
    static String json(DomainMetadata metadata, boolean withTrust) {
        ObjectNode node = (ObjectNode) TckMappers.canonical().valueToTree(metadata);
        if (withTrust) {
            BaselineTrust trust = BaselineTrust.current("0".repeat(64));
            node.put("schemaVersion", trust.schemaVersion());
            node.put("sourceDigest", trust.sourceDigest());
        }
        return node.toString();
    }

    private static DomainMetadata order() {
        return DomainMetadata.builder(TckCorpus.ORDER, TckCorpus.packageName())
                .module("sales")
                .path("/orders")
                .description("A customer order.")
                .dataScope(DataScope.TENANT)
                .fields(List.of(
                        FieldMetadata.builder("reference", "String")
                                .displayName("Reference")
                                .required(true)
                                .minLength(3)
                                .maxLength(32)
                                .pattern("^ORD-[0-9]+$")
                                .build(),
                        FieldMetadata.builder("totalCents", "Long")
                                .displayName("Total (cents)")
                                .required(true)
                                .min(0L)
                                .max(1_000_000L)
                                .build(),
                        FieldMetadata.builder("note", "String")
                                .displayName("Note")
                                .build()))
                .actions(List.of(ActionMetadata.simple("submit")))
                .build();
    }

    private static DomainMetadata customer() {
        return DomainMetadata.builder(TckCorpus.CUSTOMER, TckCorpus.packageName())
                .module("sales")
                .path("/customers")
                .description("A person or organisation that places orders.")
                .dataScope(DataScope.TENANT)
                .fields(List.of(
                        FieldMetadata.builder("email", "String")
                                .displayName("E-mail")
                                .required(true)
                                .unique(true)
                                .maxLength(254)
                                .build(),
                        FieldMetadata.builder("displayName", "String")
                                .displayName("Display name")
                                .required(true)
                                .minLength(1)
                                .maxLength(120)
                                .build()))
                .relationships(List.of(
                        RelationshipMetadata.oneToMany("orders", TckCorpus.ORDER, "customer")))
                .build();
    }
}
