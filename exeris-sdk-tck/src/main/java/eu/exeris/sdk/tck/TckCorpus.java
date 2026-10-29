package eu.exeris.sdk.tck;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The entity sources every binding is measured against, shipped inside the kit jar.
 *
 * <p>Shipped rather than left to the binder because a binder-supplied corpus measures the binder's
 * imagination. Each entity here carries a case with a defect behind it — a zero-valued bound that a
 * class-level {@code NON_DEFAULT} drops, a relationship whose cardinality attribute has been read
 * under the wrong key, a saga version that one side reads and the other skips, a declared
 * {@code false} that a boxed component drops, a return type carried as written rather than fully
 * qualified, a {@code PUBLIC} route access that ordinal 0 invites a side to drop — so a binding
 * that regresses one of them fails on the case rather than on a fixture nobody thought to write.
 *
 * <p>The entities compile as a <strong>unit</strong>: {@code Customer} declares a relationship
 * to {@code Order}. A binder driving javac must present the sources together.
 *
 * <p>Because a producer binding <em>compiles</em> these, they may use no attribute that is
 * deprecated for removal — a fixture declaring its tier through {@code @ExerisDomain.tenantScoped}
 * would stop compiling at 1.0.0, which is the release this kit exists to guard.
 *
 * <p>Sources carry a {@code .java.txt} extension inside the jar so they are never mistaken for
 * compilable module content — they are input data, and the annotations they use are
 * {@code @Retention(SOURCE)} markers that would otherwise invite an accidental build path.
 */
public final class TckCorpus {

    /**
     * Simple name of the order entity — fields, validation bounds (including a zero), five actions
     * returning nothing, an entity, a {@code record}, a {@code List} and a primitive, and
     * a {@code @Saga} declaring a non-default {@code version} and a compensation section whose
     * values include a {@code 0}, two {@code false}s and a member-type failure handler. Its route
     * access is {@code AUTHENTICATED} with two permissions, over an action that inherits it, one
     * that declares permissions of its own and one that is {@code PUBLIC}.
     */
    public static final String ORDER = "Order";

    /**
     * Simple name of the customer entity — validation bounds, a {@code ONE_TO_MANY} edge, and
     * entity-level permissions without a {@code @RouteAccess}.
     */
    public static final String CUSTOMER = "Customer";

    /**
     * Simple name of the product entity — a {@code PUBLIC} entity with an action that declares
     * {@code AUTHENTICATED} and a permission of its own.
     *
     * @since 0.13
     */
    public static final String PRODUCT = "Product";

    private static final String PACKAGE = "eu.exeris.sdk.tck.corpus";
    private static final List<String> NAMES = List.of(ORDER, CUSTOMER, PRODUCT);

    private TckCorpus() {
        // Intentionally empty: static holder — the corpus is classpath data, not state.
    }

    /**
     * The corpus entities, named.
     *
     * @return the simple names of every corpus entity, in a stable order
     */
    public static List<String> entityNames() {
        return NAMES;
    }

    /**
     * The package the corpus is written in.
     *
     * @return the package every corpus entity declares
     */
    public static String packageName() {
        return PACKAGE;
    }

    /**
     * One entity's source, by name.
     *
     * @param simpleName one of {@link #entityNames()}
     * @return that entity's Java source text
     * @throws IllegalArgumentException if the name is not a corpus entity
     */
    public static String sourceOf(String simpleName) {
        if (!NAMES.contains(simpleName)) {
            throw new IllegalArgumentException(
                    "Not a corpus entity: " + simpleName + " (have " + NAMES + ")");
        }
        String resource = "/eu/exeris/sdk/tck/corpus/" + simpleName + ".java.txt";
        try (InputStream in = TckCorpus.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Corpus resource missing from the kit jar: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read corpus resource " + resource, e);
        }
    }

    /**
     * The return type of each of {@code Order}'s actions, in the form javac reports it.
     *
     * @return action name → {@code ActionMetadata.resultType}, in declaration order
     */
    static Map<String, String> orderResultTypes() {
        Map<String, String> types = new LinkedHashMap<>();
        types.put("submit", "void");
        types.put("customer", PACKAGE + "." + CUSTOMER);
        types.put("receipt", PACKAGE + ".Order.Receipt");
        types.put("receipts", "java.util.List<" + PACKAGE + ".Order.Receipt>");
        types.put("lineCount", "int");
        types.put("approve", "void");
        types.put("track", "void");
        return types;
    }

    /**
     * The whole corpus, ready to feed to an implementation under test.
     *
     * @return every corpus entity as simple name → source text, in {@link #entityNames()} order
     */
    public static Map<String, String> all() {
        Map<String, String> sources = new LinkedHashMap<>();
        for (String name : NAMES) {
            sources.put(name, sourceOf(name));
        }
        return sources;
    }
}
