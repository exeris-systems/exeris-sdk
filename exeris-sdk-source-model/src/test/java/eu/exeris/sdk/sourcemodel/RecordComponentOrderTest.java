package eu.exeris.sdk.sourcemodel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Enforces the record-growth stance from {@code MIGRATION-0.x-to-1.0.md} §3:
 * an AST record may gain a <strong>trailing</strong> component, and may not
 * reorder, rename, retype or remove an existing one. (The stance's other half —
 * a record that grows keeps its previous arity as a delegating constructor — is
 * {@link RecordConstructorLedgerTest}'s.)
 *
 * <p><b>Why this exists alongside the japicmp gate.</b> The two are a pair, and
 * neither is sufficient alone. japicmp sees removals, renames and retypes,
 * because each of those changes a record's <em>accessor</em>. It cannot see a
 * same-arity, same-type <em>reorder</em>: every accessor survives untouched, and
 * so does every constructor descriptor, because swapping two {@code String}
 * components leaves {@code (String, String)} exactly where it was. Component
 * order is therefore invisible to the gate and pinned here instead.
 *
 * <p>A reorder across <em>different</em> types does change the canonical
 * constructor's descriptor, which the strict gate reports as
 * {@code CONSTRUCTOR_REMOVED}; the same-type case is invisible to it either way,
 * and it is the one this test exists for.
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

    private static final String SNAPSHOT = "/record-components.txt";

    @Test
    @DisplayName("every AST record's component order extends its recorded prefix")
    void componentOrderGrowsOnlyAtTheTail() throws Exception {
        Map<String, List<String>> recorded = readSnapshot();
        Map<String, List<String>> actual = new LinkedHashMap<>();
        for (Map.Entry<String, Class<?>> e : WireRecords.discover().entrySet()) {
            actual.put(e.getKey(), Arrays.stream(e.getValue().getRecordComponents())
                    .map(java.lang.reflect.RecordComponent::getName)
                    .toList());
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
