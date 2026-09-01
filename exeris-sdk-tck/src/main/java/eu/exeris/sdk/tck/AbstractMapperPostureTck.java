package eu.exeris.sdk.tck;

import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract for anyone deserializing SDK-emitted JSON with their own mapper.
 *
 * <p>The cheapest suite in the kit and the one most likely to catch something. It takes no Jackson
 * type in its binding API on purpose: the subject <em>is</em> the consumer's mapper configuration,
 * so handing them a working one would answer the question by assuming it.
 *
 * <pre>{@code
 * class MyMapperTckTest extends AbstractMapperPostureTck {
 *     private final ObjectMapper mapper = myProductionMapper();
 *     protected <T> T readValue(String json, Class<T> type) {
 *         return mapper.readValue(json, type);
 *     }
 * }
 * }</pre>
 *
 * <p>Bind the mapper the application actually ships, not one built for the test. A mapper
 * configured correctly beside a production one that is not proves nothing about the read path that
 * will run.
 *
 * <p><strong>One case, on purpose.</strong> Three others were written for this suite and removed
 * after being measured against a deliberately misconfigured mapper: an unknown property cannot
 * break a consumer, because the AST records carry {@code @JsonIgnoreProperties(ignoreUnknown =
 * true)} themselves; a zero-valued bound cannot be lost on write, because a per-component
 * {@code @JsonInclude(NON_NULL)} beats a mapper-wide default inclusion; and a polymorphic
 * {@code MutationOp} resolves its subtype on a stock mapper, because {@code @JsonTypeInfo} drives
 * it. Each is a real guarantee — and each is the <em>record's</em> to keep, not the consumer's, so
 * a case asserting it would pass for every binding ever written. That reads as coverage and is
 * not. What remains is the one obligation that genuinely sits on the consumer's side.
 */
/*
 * S5960 suppressed: in a TCK the assertions are the shipped artifact, not residue. Full rationale
 * on {@code AbstractExerisTck}.
 */
@SuppressWarnings("java:S5960")
public abstract class AbstractMapperPostureTck extends AbstractExerisTck {

    /**
     * For subclasses; this type is extended, never instantiated directly.
     *
     * <p>Declared so the mapper-posture suite carries a documented constructor rather than an implicit
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
    public AbstractMapperPostureTck() {
        // Nothing to initialize; declared so it carries a comment rather than being implicit.
    }

    /**
     * Deserializes with the mapper under test.
     *
     * @param json the document to read
     * @param type the target type
     * @param <T> the target type
     * @return the deserialized value
     */
    protected abstract <T> T readValue(String json, Class<T> type);

    @Test
    @DisplayName("an explicit null on a primitive is tolerated rather than fatal")
    void explicitNullOnAPrimitiveIsTolerated() {
        String json = """
                {"entityName":"Order","packageName":"eu.exeris.sdk.tck.corpus","restApi":null}
                """;
        DomainMetadata metadata;
        try {
            metadata = readValue(json, DomainMetadata.class);
        } catch (RuntimeException e) {
            throw new AssertionError(
                    "Reading a DomainMetadata with an explicit null on a primitive boolean threw: " + e
                            + "\n\nJackson 3 defaults FAIL_ON_NULL_FOR_PRIMITIVES to true (Jackson 2 "
                            + "defaulted it to false), and the AST is primitive-boolean heavy. A "
                            + "baseline is a file you did not necessarily write: a third-party "
                            + "producer, a hand edit, or a re-serialization under ALWAYS inclusion "
                            + "all put explicit nulls on the wire, and this mapper cannot read any of "
                            + "them. Set FAIL_ON_NULL_FOR_PRIMITIVES = false.", e);
        }
        assertThat(metadata.entityName()).isEqualTo("Order");
        assertThat(metadata.restApi())
                .withFailMessage("A null-valued primitive must read as the type default.")
                .isFalse();
    }
}
