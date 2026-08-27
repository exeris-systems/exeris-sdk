package eu.exeris.sdk.annotation;

import java.io.File;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.fail;

/**
 * One definition of "every annotation this module declares", shared by the tests that
 * hold the surface to a contract.
 *
 * <p>It exists as a helper rather than as a method on one of them because two tests now
 * ask the same question for different reasons — the retention/target invariants, and
 * whether the generated catalog covers the surface — and two walks would eventually
 * answer it differently. The catalog test in particular is only a real check while this
 * walk is derived independently of the catalog: it reads <em>compiled classes</em>, where
 * the catalog is built from <em>sources</em> by an annotation processor, so a bug in
 * either shows up as disagreement rather than as two copies of the same mistake.
 *
 * <p><strong>Nested types are included.</strong> Roughly a quarter of this SDK's
 * declaration sites are nested inside another annotation, and a walk that skipped them
 * would hold three quarters of the surface to the contract while reporting success.
 */
final class DeclaredAnnotations {

    private DeclaredAnnotations() {
    }

    /**
     * Discovers every annotation type ({@link Class#isAnnotation()}) under the given
     * package <em>and all of its sub-packages</em>, by enumerating every classpath root
     * that contains it and walking each root recursively.
     *
     * <p>Multi-root enumeration matters under Maven: the test classloader exposes both
     * {@code target/classes} (where the annotations live) and {@code target/test-classes}
     * (where the tests live) as separate locations for the same package, and only the
     * first would be visible to {@code getResource} alone. Recursion means a new
     * sub-package needs no test edit — it is picked up and held to the same contract
     * automatically.
     */
    @SuppressWarnings("unchecked")
    static List<Class<? extends Annotation>> under(String pkg) throws Exception {
        String pkgPath = pkg.replace('.', '/');
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        var urls = cl.getResources(pkgPath);
        if (!urls.hasMoreElements()) {
            fail("Package not found on classpath: %s", pkg);
        }
        List<Class<? extends Annotation>> out = new ArrayList<>();
        while (urls.hasMoreElements()) {
            URL root = urls.nextElement();
            Path dir = Paths.get(URLDecoder.decode(root.getPath(), StandardCharsets.UTF_8));
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path p : (Iterable<Path>) files::iterator) {
                    if (!Files.isRegularFile(p)) continue;
                    String fname = p.getFileName().toString();
                    if (!fname.endsWith(".class") || fname.equals("package-info.class")) continue;
                    // Rebuild the binary name from the path relative to the package root,
                    // so sub-packages (system/, security/, …) resolve — and so does a
                    // nested type, whose file already carries the '$' its binary name needs.
                    String rel = dir.relativize(p).toString()
                            .replace(File.separatorChar, '.');
                    String binaryName = pkg + "." + rel.substring(0, rel.length() - ".class".length());
                    Class<?> c = Class.forName(binaryName, false, cl);
                    if (c.isAnnotation()) {
                        out.add((Class<? extends Annotation>) c);
                    }
                }
            }
        }
        return out;
    }
}
