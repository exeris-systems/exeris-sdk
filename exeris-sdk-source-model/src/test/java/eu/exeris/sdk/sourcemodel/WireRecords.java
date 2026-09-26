package eu.exeris.sdk.sourcemodel;

import java.io.File;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The public records on the wire-carrying surface, discovered from the classpath — the one walk
 * both record-growth guards share, so {@link RecordComponentOrderTest} and
 * {@link RecordConstructorLedgerTest} cannot disagree about which records the stance covers.
 */
final class WireRecords {

    /**
     * Both wire-carrying packages. The mutation surface is included because its
     * {@code MutationOp} / {@code MutationResult} variants are records under a sealed interface
     * and grow on the same terms — covering only {@code ast} would leave a guard that reads as
     * complete and is not.
     */
    static final List<String> PACKAGES = List.of(
            "eu.exeris.sdk.sourcemodel.ast",
            "eu.exeris.sdk.sourcemodel.mutation");

    private WireRecords() {
    }

    /**
     * Binary class name → class, public records only, sorted by name. Keyed by the full binary
     * name rather than the simple one so a nested variant ({@code MutationOp$AddField}) cannot
     * collide with a top-level type.
     *
     * @return every public record under {@link #PACKAGES}
     * @throws Exception if the classpath cannot be walked or a class cannot be loaded
     */
    static Map<String, Class<?>> discover() throws Exception {
        Map<String, Class<?>> out = new TreeMap<>();
        for (String pkg : PACKAGES) {
            discover(pkg, out);
        }
        return out;
    }

    private static void discover(String pkg, Map<String, Class<?>> out) throws Exception {
        String pkgPath = pkg.replace('.', '/');
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> urls = cl.getResources(pkgPath);
        if (!urls.hasMoreElements()) {
            throw new AssertionError("Package not found on classpath: " + pkg);
        }
        while (urls.hasMoreElements()) {
            URL root = urls.nextElement();
            Path dir = Paths.get(URLDecoder.decode(root.getPath(), StandardCharsets.UTF_8));
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path p : (Iterable<Path>) files::iterator) {
                    if (!Files.isRegularFile(p)) continue;
                    String fname = p.getFileName().toString();
                    if (!fname.endsWith(".class") || fname.equals("package-info.class")) continue;
                    // Nested types are kept: builders are classes and filtered out
                    // below, while sealed-interface variants are records that carry
                    // the wire just as much as a top-level one.
                    String rel = dir.relativize(p).toString().replace(File.separatorChar, '.');
                    String fqn = pkg + "." + rel.substring(0, rel.length() - ".class".length());
                    Class<?> c = Class.forName(fqn, false, cl);
                    if (c.isRecord() && Modifier.isPublic(c.getModifiers())) {
                        out.put(c.getName(), c);
                    }
                }
            }
        }
    }
}
