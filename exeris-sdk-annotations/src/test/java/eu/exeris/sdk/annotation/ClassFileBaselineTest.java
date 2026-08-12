package eu.exeris.sdk.annotation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Guards the class-file major the SDK actually emits (ADR-069).
 * <p>
 * The SDK's jars sit on a consumer's <em>compile</em> classpath, so the emitted
 * major is not an implementation detail — it is the thing a consumer trips over:
 * {@code javac} on JDK 25 refuses a major-70 class outright ("class file has
 * wrong version 70.0, should be 69.0"), which would lock out exactly the
 * LTS-only deployments kernel ADR-066 set out to admit.
 * <p>
 * This reads the class-file header rather than asserting on
 * {@code maven.compiler.release}, for the reason the kernel's bytecode scan
 * gives: the stamp is what ships. A property override, a module-level
 * {@code <release>}, or a plugin default that re-raises the target all fail
 * here; a comment claiming compliance does not satisfy it.
 * <p>
 * <b>Raising this ceiling is a decision, not a fix.</b> It follows the kernel's
 * GA baseline — when that moves at a future LTS, this moves with it and
 * ADR-069 is amended. It is not the knob for making a build error go away.
 */
@DisplayName("Class-file baseline: emitted major stays at the kernel's GA LTS")
class ClassFileBaselineTest {

    /** Class-file major for Java 25 — the kernel's GA baseline (ADR-066). */
    private static final int MAX_MAJOR = 69;

    @Test
    @DisplayName("compiled annotations carry class-file major 69 or lower")
    void emittedClassFileMajorIsAtMostBaseline() throws Exception {
        int major = majorVersionOf(ExerisDomain.class);

        assertThat(major)
                .as("emitted class-file major for %s — above %d, a JDK 25 LTS consumer "
                                + "cannot compile against this jar at all (ADR-069)",
                        ExerisDomain.class.getSimpleName(), MAX_MAJOR)
                .isLessThanOrEqualTo(MAX_MAJOR);
    }

    /**
     * Reads bytes 4-7 of the class file: {@code u4 magic}, then
     * {@code u2 minor}, {@code u2 major}.
     */
    private int majorVersionOf(Class<?> type) throws Exception {
        String resource = type.getName().replace('.', '/') + ".class";
        try (InputStream in = type.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                fail("Class file not found on the classpath: %s", resource);
            }
            try (DataInputStream data = new DataInputStream(in)) {
                int magic = data.readInt();
                if (magic != 0xCAFEBABE) {
                    fail("Not a class file (magic=%08X): %s", magic, resource);
                }
                data.readUnsignedShort(); // minor
                return data.readUnsignedShort();
            }
        }
    }
}
