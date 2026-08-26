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
 * under the wrong key — so a binding that regresses one of them fails on the case rather than on a
 * fixture nobody thought to write.
 *
 * <p>The two entities compile as a <strong>unit</strong>: {@code Customer} declares a relationship
 * to {@code Order}. A binder driving javac must present both sources together.
 *
 * <p>Sources carry a {@code .java.txt} extension inside the jar so they are never mistaken for
 * compilable module content — they are input data, and the annotations they use are
 * {@code @Retention(SOURCE)} markers that would otherwise invite an accidental build path.
 */
public final class TckCorpus {

    /** Simple name of the order entity — fields, validation bounds (including a zero), one action. */
    public static final String ORDER = "Order";

    /** Simple name of the customer entity — validation bounds and a {@code ONE_TO_MANY} edge. */
    public static final String CUSTOMER = "Customer";

    private static final String PACKAGE = "eu.exeris.sdk.tck.corpus";
    private static final List<String> NAMES = List.of(ORDER, CUSTOMER);

    private TckCorpus() {
        // Intentionally empty: static holder — the corpus is classpath data, not state.
    }

    /** @return the simple names of every corpus entity, in a stable order */
    public static List<String> entityNames() {
        return NAMES;
    }

    /** @return the package every corpus entity declares */
    public static String packageName() {
        return PACKAGE;
    }

    /**
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

    /** @return every corpus entity as simple name → source text, in {@link #entityNames()} order */
    public static Map<String, String> all() {
        Map<String, String> sources = new LinkedHashMap<>();
        for (String name : NAMES) {
            sources.put(name, sourceOf(name));
        }
        return sources;
    }
}
