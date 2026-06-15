package eu.exeris.sdk.sourcemodel.mutation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Content hash of a domain source file, the baseline's {@code sourceDigest}
 * (ADR-042, obligation 5). Codegen stamps {@code of(source)} into the baseline
 * JSON; later it doubles as the optimistic-concurrency token (obligation 7) a
 * mutation batch is checked against at apply time (slice 4).
 *
 * <p>Lives in {@code source-model} (pure, JavaParser-free) so both sides compute
 * it <strong>identically</strong>: the build-time codegen that emits it and the
 * {@code -io} layer that recomputes it must agree byte-for-byte, so the
 * algorithm is one shared contract, not two implementations.
 *
 * <h2>Normalization</h2>
 * <p>The digest is taken over a normalized form so that edits the AST ignores do
 * not spuriously invalidate the baseline (the ADR floor — line-ending and
 * trailing-whitespace neutral):
 * <ul>
 *   <li>line endings unified to {@code \n} (CRLF / CR → LF);</li>
 *   <li>trailing whitespace stripped from every line;</li>
 *   <li>trailing blank lines / whitespace at end-of-file stripped.</li>
 * </ul>
 * It is deliberately <em>textual</em>, not a parse-and-reprint canonicalization:
 * keeping it in {@code source-model} means it must not depend on JavaParser. A
 * structural (AST-equal) digest is a possible future refinement, but a textual
 * floor is enough for a concurrency token and avoids the heavy dependency.
 *
 * @since 0.5.0
 */
public final class SourceDigest {

    private SourceDigest() {
    }

    /** The hex SHA-256 of the {@linkplain #normalize normalized} source. */
    public static String of(String source) {
        byte[] hash = sha256(normalize(source).getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }

    /** Line-ending- and trailing-whitespace-neutral form (see class javadoc). */
    static String normalize(String source) {
        String unified = source.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = unified.split("\n", -1);
        StringBuilder sb = new StringBuilder(unified.length());
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(lines[i].stripTrailing());
        }
        return sb.toString().stripTrailing();
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JLS-referenced platform spec; absence is fatal.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
