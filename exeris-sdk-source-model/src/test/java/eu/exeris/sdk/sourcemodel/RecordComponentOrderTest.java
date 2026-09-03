package eu.exeris.sdk.sourcemodel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Enforces the record-growth stance from {@code MIGRATION-0.x-to-1.0.md} §3:
 * an AST record may gain a <strong>trailing</strong> component, and may not
 * reorder, rename, retype or remove an existing one.
 *
 * <p><b>Why this exists alongside the japicmp gate.</b> The two are a pair, and
 * neither is sufficient alone. japicmp sees removals, renames and retypes,
 * because each of those changes a record's <em>accessor</em>. It cannot see a
 * same-arity, same-type <em>reorder</em>: every accessor survives untouched and
 * the only trace is constructor parameter order — which the root pom's
 * {@code CONSTRUCTOR_REMOVED} override deliberately stops reading, since that
 * same signal is what a legitimate trailing append produces. Component order is
 * therefore invisible to the gate and pinned here instead.
 *
 * <p>A reorder is the quiet failure mode worth spending a test on. Jackson binds
 * records by name, so the wire survives and {@code AstJsonRoundTripTest} stays
 * green; positional callers recompile without complaint and silently swap two
 * values. Nothing else in the build would notice.
 *
 * <p><b>The snapshot is meant to be edited — deliberately.</b> When a record
 * legitimately grows, append the new component to its line in
 * {@code record-components.txt} in the same commit. The test fails on any other
 * shape of change, including a record it has never seen, so a new AST type
 * cannot enter the contract without its order being recorded.
 */
@DisplayName("record-growth stance: AST record components grow at the tail only")
class RecordComponentOrderTest {

    /**
     * Both wire-carrying packages. The mutation surface is included because its
     * {@code MutationOp} / {@code MutationResult} variants are records under a
     * sealed interface and grow on the same terms — covering only {@code ast}
     * would leave a guard that reads as complete and is not.
     */
    private static final List<String> PACKAGES = List.of(
            "eu.exeris.sdk.sourcemodel.ast",
            "eu.exeris.sdk.sourcemodel.mutation");

    private static final String SNAPSHOT = "/record-components.txt";

    @Test
    @DisplayName("every AST record's component order extends its recorded prefix")
    void componentOrderGrowsOnlyAtTheTail() throws Exception {
        Map<String, List<String>> recorded = readSnapshot();
        Map<String, List<String>> actual = new LinkedHashMap<>();
        for (String pkg : PACKAGES) {
            actual.putAll(discoverRecordComponents(pkg));
        }

        assertThat(actual)
                .as("should discover the records; an empty walk would make this test vacuous")
                .hasSizeGreaterThanOrEqualTo(20)
                .containsKeys("eu.exeris.sdk.sourcemodel.ast.DomainMetadata",
                        "eu.exeris.sdk.sourcemodel.ast.FieldMetadata",
                        "eu.exeris.sdk.sourcemodel.mutation.MutationOp$AddField");

        List<String> violations = new ArrayList<>();

        for (Map.Entry<String, List<String>> e : new TreeMap<>(actual).entrySet()) {
            String record = e.getKey();
            List<String> now = e.getValue();
            List<String> before = recorded.get(record);

            if (before == null) {
                violations.add(("%s is not in the snapshot. A new AST record joins the frozen "
                        + "contract, so record its component order: add the line%n    %s=%s")
                        .formatted(record, record, String.join(",", now)));
                continue;
            }
            if (now.size() < before.size() || !now.subList(0, before.size()).equals(before)) {
                violations.add(("%s changed shape below the tail.%n  recorded: %s%n  now:      %s%n"
                        + "  Growth is legal only by appending. A reorder, rename, retype or "
                        + "removal of an existing component is a break — see "
                        + "MIGRATION-0.x-to-1.0.md §3.")
                        .formatted(record, before, now));
            } else if (now.size() > before.size()) {
                // Appending is legal, and this is not a complaint about the record. It is a
                // complaint about the snapshot: the prefix comparison above accepts a tail the
                // file never recorded, so the file silently records less than it claims to.
                // Both DomainMetadata and ActionMetadata had drifted exactly this way — the
                // 0.12.0 routeAccess component grew past a snapshot that still ended at
                // dataScope, with the class javadoc above telling authors to append and nothing
                // making them. A snapshot that goes stale unnoticed is a gate that is green
                // about a shape it is not holding.
                violations.add(("%s grew legally but the snapshot was not updated. Append the new "
                        + "component(s) to its line in record-components.txt:%n    %s=%s")
                        .formatted(record, record, String.join(",", now)));
            }
        }

        for (String record : new TreeMap<>(recorded).keySet()) {
            if (!actual.containsKey(record)) {
                violations.add(("%s is in the snapshot but no longer on the classpath. Removing a "
                        + "public AST record is a break; if it was renamed, that is a break too.")
                        .formatted(record));
            }
        }

        if (!violations.isEmpty()) {
            fail("Record-growth stance violated (%d):%n%n%s",
                    violations.size(), String.join("%n%n".formatted(), violations));
        }
    }

    /**
     * Binary class name → component names in declaration order, public records
     * only. Keyed by the full name rather than the simple one so a nested
     * variant ({@code MutationOp$AddField}) cannot collide with a top-level type.
     */
    private Map<String, List<String>> discoverRecordComponents(String pkg) throws Exception {
        String pkgPath = pkg.replace('.', '/');
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        var urls = cl.getResources(pkgPath);
        if (!urls.hasMoreElements()) {
            fail("Package not found on classpath: %s", pkg);
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
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
                    if (c.isRecord() && java.lang.reflect.Modifier.isPublic(c.getModifiers())) {
                        out.put(c.getName(),
                                Arrays.stream(c.getRecordComponents())
                                        .map(java.lang.reflect.RecordComponent::getName)
                                        .toList());
                    }
                }
            }
        }
        return out;
    }

    private Map<String, List<String>> readSnapshot() throws Exception {
        Map<String, List<String>> out = new LinkedHashMap<>();
        try (var in = RecordComponentOrderTest.class.getResourceAsStream(SNAPSHOT)) {
            if (in == null) {
                fail("Snapshot resource missing: %s", SNAPSHOT);
            }
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList()) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int eq = trimmed.indexOf('=');
                if (eq < 0) {
                    fail("Malformed snapshot line (expected `Record=comp1,comp2`): %s", trimmed);
                }
                out.put(trimmed.substring(0, eq),
                        List.of(trimmed.substring(eq + 1).split(",")));
            }
        }
        return out;
    }
}
