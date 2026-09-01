package eu.exeris.sdk.composition;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;

/**
 * The <b>one</b> canonical ADR-024 content-binding implementation — {@code "sha256:" + hex(SHA-256)}
 * over a canonical, sorted serialization of the resolved cap set. Shared by the tooling that emits
 * the binding into {@code cap-manifest.json} and the SKU-boot runtime that recomputes it to assert
 * the deployed composition is the one that was validated (ADR-024 obligation 8b).
 *
 * <h2>Why this is the single definition</h2>
 * <p>Until this module, the algorithm lived twice — once in the tooling's
 * {@code CompositionStamp#computeBinding} and once as a hand-maintained "byte-verbatim port" in the
 * platform composition runtime — pinned only by a shared golden vector. That port had silently
 * dropped the unversioned-provide normalization (it emitted {@code service@null} where the producer
 * emits {@code service@}); the golden fixture used only versioned provides, so the drift went
 * unseen. Collapsing to one implementation here removes that whole class of latent divergence; the
 * golden vector survives as a cross-module conformance pin ({@code CompositionBindingTest}).
 *
 * <h2>Canonical form (load-bearing — must not drift)</h2>
 * <p>Modules are sorted by {@code qualifiedName} ascending (so a reordered-but-intact manifest binds
 * identically). Each module emits {@code qualifiedName + "\n"}, then for every provided service
 * formatted {@code service + "@" + version} — sorted ascending — {@code "  provides " + s + "\n"}
 * (two-space prefix, trailing newline). A {@code null} version normalizes to the empty string, so an
 * unversioned provide is {@code "service@"} — distinct from a literal version {@code "null"} and from
 * {@code "service@1.0"}. The binding hashes the cap set ONLY; the composition version is a separate
 * stamp field matched independently. Any change to sort order, separators, the {@code "  provides "}
 * prefix, the trailing {@code \n}, or the null normalization breaks the hash and false-fails every
 * deploy.
 *
 * @since 0.8.0
 */
public final class CompositionBinding {

    private CompositionBinding() {
    }

    /**
     * Convenience: compute the binding over a manifest's module set. A {@code null} module list
     * (the {@code "modules"} field absent from the JSON — the consumer mapper leaves a reference-type
     * component null, since {@code FAIL_ON_NULL_FOR_PRIMITIVES} guards only primitives) is treated as
     * an empty composition rather than throwing, so a malformed or wrong-schema manifest surfaces as
     * the asserter's descriptive handshake failure, not an opaque {@link NullPointerException} here.
          *
     * @param manifest the manifest to bind
     * @return the canonical binding string for its modules
     */
    public static String compute(CapManifest manifest) {
        List<CapManifest.Module> modules = manifest.modules();
        return compute(modules != null ? modules : List.of());
    }

    /**
     * Compute the content binding for {@code modules}. Robust to the manifest's array order — the
     * canonical form sorts by {@code qualifiedName} regardless. Modules must have a non-null
     * {@code qualifiedName} and non-null provided {@code service}s; an unversioned provide (null
     * version) is legal and normalizes to {@code "service@"}.
          *
     * @param modules the modules to bind, in any order — the binding canonicalizes them
     * @return the canonical binding string
     */
    public static String compute(List<CapManifest.Module> modules) {
        List<CapManifest.Module> sorted = modules.stream()
                .sorted(Comparator.comparing(CapManifest.Module::qualifiedName))
                .toList();
        StringBuilder canonical = new StringBuilder();
        for (CapManifest.Module m : sorted) {
            canonical.append(m.qualifiedName()).append('\n');
            CapManifest.ModuleBody body = m.module();
            if (body != null && body.provides() != null) {
                body.provides().stream()
                        .map(p -> p.service() + '@' + (p.version() == null ? "" : p.version()))
                        .sorted()
                        .forEach(s -> canonical.append("  provides ").append(s).append('\n'));
            }
        }
        return "sha256:" + sha256Hex(canonical.toString());
    }

    private static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e); // never on a conformant JRE
        }
    }
}
