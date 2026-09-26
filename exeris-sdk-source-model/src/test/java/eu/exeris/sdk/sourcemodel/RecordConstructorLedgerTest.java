package eu.exeris.sdk.sourcemodel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Enforces the constructor half of the record-growth stance in {@code MIGRATION-0.x-to-1.0.md}
 * §3: a record may gain a trailing component, and <strong>keeps its previous arity as a public
 * constructor</strong> that delegates with {@code null} for what was added. So neither kind of
 * caller breaks — a builder caller never saw the constructor, and a positional caller finds the
 * shape it was compiled against.
 *
 * <p><b>Why the stance changed.</b> Until 0.12.0 growth simply replaced the canonical
 * constructor, and {@code source-model}'s pom told japicmp that {@code CONSTRUCTOR_REMOVED} was
 * binary- and source-compatible. It is neither: removing a public constructor is a
 * {@code NoSuchMethodError} for every class compiled against it (JLS 13.4.12) and a compile
 * error for every positional call. It broke {@code exeris-tooling}'s processor, which has no
 * other way to build {@code SystemFieldsMetadata} with non-canonical names, when that record went
 * from 10 components to 11 (Stellar finding S6). The processor runs on each consumer's processor
 * path linked against whatever SDK Maven resolves there, so under the old rule a newer SDK minor
 * meant a {@code NoSuchMethodError} inside javac.
 *
 * <p><b>What this checks, against {@code record-arities.txt}:</b>
 * <ul>
 *   <li>For every recorded arity {@code N}, a public constructor exists whose parameter types —
 *       erased and generic — are the record's first {@code N} component types. Deleting a
 *       compatibility constructor fails here.</li>
 *   <li>The record's current canonical arity is recorded. Growing a record without appending to
 *       its line fails here, the way {@link RecordComponentOrderTest} forces its own snapshot
 *       forward.</li>
 *   <li>Every public constructor shaped like a component prefix is recorded — it is published
 *       API the moment it ships.</li>
 *   <li>Every public record on the surface has a line, and every line names a record that
 *       exists.</li>
 * </ul>
 *
 * <p><b>Why a test when japicmp exists.</b> japicmp is an opt-in profile ({@code -Psemver},
 * root pom) that needs a resolvable release to compare with, and CI runs it only from the 0.13.0
 * line, when a 0.12.0 baseline is on Central. This needs no baseline, so it runs in every build.
 * Where japicmp runs the two are a pair: the ledger cannot see an arity deleted together with its
 * constructor, and japicmp's strict {@code CONSTRUCTOR_REMOVED} can.
 * {@link RecordComponentOrderTest} stays, because a same-type reorder changes no descriptor and
 * neither of them sees it.
 */
@DisplayName("record-growth stance: every published constructor arity survives")
class RecordConstructorLedgerTest {

    private static final String LEDGER = "/record-arities.txt";

    @Test
    @DisplayName("each recorded arity still has its constructor, and the ledger is current")
    void everyPublishedArityKeepsItsConstructor() throws Exception {
        Map<String, List<Integer>> ledger = readLedger();
        Map<String, Class<?>> records = WireRecords.discover();

        assertThat(records)
                .as("should discover the records; an empty walk would make this test vacuous")
                .hasSizeGreaterThanOrEqualTo(20)
                .containsKeys("eu.exeris.sdk.sourcemodel.ast.DomainMetadata",
                        "eu.exeris.sdk.sourcemodel.ast.SystemFieldsMetadata",
                        "eu.exeris.sdk.sourcemodel.mutation.MutationOp$AddField");
        assertThat(ledger.get("eu.exeris.sdk.sourcemodel.ast.SystemFieldsMetadata"))
                .as("the ledger must carry at least one compatibility arity, or every check below "
                        + "is about canonical constructors a record cannot lack")
                .hasSizeGreaterThan(1);

        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, Class<?>> e : records.entrySet()) {
            String name = e.getKey();
            Class<?> record = e.getValue();
            RecordComponent[] components = record.getRecordComponents();
            List<Integer> arities = ledger.get(name);

            if (arities == null) {
                violations.add(("%s is not in the ledger. A new record's canonical constructor is "
                        + "published API from its first release, so record it: add the line%n    %s=%d")
                        .formatted(name, name, components.length));
                continue;
            }
            if (!arities.contains(components.length)) {
                violations.add(("%s has %d components but the ledger stops at %s. The record grew: "
                        + "append the new arity (%s=%s,%d), and keep a public constructor at every "
                        + "arity already recorded, delegating with null for the new trailing "
                        + "component(s) — MIGRATION-0.x-to-1.0.md §3.")
                        .formatted(name, components.length, arities, name,
                                join(arities), components.length));
            }
            for (int arity : arities) {
                if (arity > components.length) {
                    violations.add(("%s records arity %d but has only %d components. A component "
                            + "was removed, which is a break, or the line is wrong.")
                            .formatted(name, arity, components.length));
                } else if (prefixConstructor(record, components, arity) == null) {
                    violations.add(("%s has no public constructor taking its first %d components "
                            + "(%s). That arity was published; code compiled against it now fails "
                            + "to link. Restore it as a constructor delegating to the canonical one "
                            + "with null for the rest.")
                            .formatted(name, arity, typeNames(components, arity)));
                }
            }
            for (Constructor<?> ctor : record.getConstructors()) {
                int n = ctor.getParameterCount();
                // The canonical arity, when unrecorded, was reported above as growth.
                if (n < components.length && isPrefix(ctor, components, n) && !arities.contains(n)) {
                    violations.add(("%s declares a public constructor of arity %d that the ledger "
                            + "does not record. It is published API once it ships: append it.")
                            .formatted(name, n));
                }
            }
        }
        for (String name : ledger.keySet()) {
            if (!records.containsKey(name)) {
                violations.add(("%s is in the ledger but not on the classpath. Removing or "
                        + "renaming a public record is a break.").formatted(name));
            }
        }

        if (!violations.isEmpty()) {
            fail("Record constructor ledger violated (%d):%n%n%s",
                    violations.size(), String.join("%n%n".formatted(), violations));
        }
    }

    private static Constructor<?> prefixConstructor(Class<?> record, RecordComponent[] components,
                                                    int arity) {
        for (Constructor<?> ctor : record.getConstructors()) {
            if (ctor.getParameterCount() == arity && isPrefix(ctor, components, arity)) {
                return ctor;
            }
        }
        return null;
    }

    /**
     * Erased types decide whether old binaries link; generic types decide whether old sources
     * compile. Both have to match — a {@code List<String>} where {@code List<FieldMetadata>}
     * stood would link and then fail to compile.
     */
    private static boolean isPrefix(Constructor<?> ctor, RecordComponent[] components, int arity) {
        Class<?>[] erased = ctor.getParameterTypes();
        Type[] generic = ctor.getGenericParameterTypes();
        if (erased.length != arity || generic.length != arity) {
            return false;
        }
        for (int i = 0; i < arity; i++) {
            if (!erased[i].equals(components[i].getType())
                    || !generic[i].equals(components[i].getGenericType())) {
                return false;
            }
        }
        return true;
    }

    private static String typeNames(RecordComponent[] components, int arity) {
        return String.join(", ", Arrays.stream(components, 0, arity)
                .map(c -> c.getGenericType().getTypeName().replaceAll("[\\w.]+\\.", ""))
                .toList());
    }

    private static String join(List<Integer> arities) {
        return String.join(",", arities.stream().map(String::valueOf).toList());
    }

    private static Map<String, List<Integer>> readLedger() throws Exception {
        Map<String, List<Integer>> out = new LinkedHashMap<>();
        try (var in = RecordConstructorLedgerTest.class.getResourceAsStream(LEDGER)) {
            if (in == null) {
                fail("Ledger resource missing: %s", LEDGER);
            }
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList()) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int eq = trimmed.indexOf('=');
                if (eq < 0) {
                    fail("Malformed ledger line (expected `Record=4,6,9`): %s", trimmed);
                }
                List<Integer> arities = Arrays.stream(trimmed.substring(eq + 1).split(","))
                        .map(String::strip)
                        .map(Integer::valueOf)
                        .toList();
                if (!arities.equals(List.copyOf(new TreeSet<>(arities)))) {
                    fail("Ledger line is not strictly ascending — arities are appended, never "
                            + "inserted or repeated: %s", trimmed);
                }
                if (out.put(trimmed.substring(0, eq), arities) != null) {
                    fail("Ledger names a record twice: %s", trimmed.substring(0, eq));
                }
            }
        }
        return out;
    }
}
