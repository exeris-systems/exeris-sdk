package eu.exeris.sdk.sourcemodel.io;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

/**
 * Writes edits back into Java source <em>idempotently</em>, preserving the
 * user's formatting, comments, and non-Exeris annotations.
 *
 * <p>This is the genuinely hard half of 0.3.0 and the reason {@code -io} is its
 * own module: codegen emits <em>fresh</em> files, whereas an editor/LSP must
 * surgically modify user-authored sources without reformatting them. The
 * preservation is JavaParser's {@code LexicalPreservingPrinter}: only the nodes
 * we touch change; everything else is reproduced byte-for-byte.
 *
 * <p>0.3.0 spike scope: a single mutation — add a field — demonstrating the
 * preservation + idempotency contract. The full {@code MutationOp} surface
 * (rename, retype, relationship changes) is 0.5.0 (ADR-037 pre-emptive ruling:
 * those op records live in {@code source-model}; their application lives here).
 *
 * <p><b>Limitations.</b> {@code @ExerisDomain} is matched by simple name (no
 * import resolution). Instances are <b>not thread-safe</b> (single
 * {@code JavaParser}); use one per call-site or guard externally. The parser
 * language level is JavaParser's {@code CURRENT} (Java 21 in 3.28.x); Java 26
 * added no new grammar, so entity-shaped sources parse, but revisit when a
 * 26-aware JavaParser ships.
 *
 * @since 0.3.0
 */
public final class SourceModelWriter {

    private final JavaParser javaParser;

    public SourceModelWriter() {
        this.javaParser = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.CURRENT));
    }

    /**
     * Adds a {@code private <type> <fieldName>;} to the first {@code @ExerisDomain}
     * type in {@code javaSource}, preserving everything else verbatim. Idempotent:
     * if a field of that name already exists, the source is returned unchanged.
     *
     * @throws IllegalArgumentException if the source is not valid Java or has no
     *                                  {@code @ExerisDomain} type
     */
    public String addField(String javaSource, String type, String fieldName) {
        ParseResult<CompilationUnit> result = javaParser.parse(javaSource);
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            throw new IllegalArgumentException("Source is not valid Java: " + result.getProblems());
        }
        CompilationUnit cu = result.getResult().get();
        ClassOrInterfaceDeclaration domain = cu.findFirst(ClassOrInterfaceDeclaration.class,
                        c -> c.isAnnotationPresent("ExerisDomain"))
                .orElseThrow(() -> new IllegalArgumentException("No @ExerisDomain type in source"));

        boolean alreadyPresent = domain.getFields().stream()
                .flatMap(field -> field.getVariables().stream())
                .anyMatch(var -> var.getNameAsString().equals(fieldName));
        if (alreadyPresent) {
            // No-op: return the original source untouched. Short-circuiting here
            // avoids the cost of LexicalPreservingPrinter.setup/print on the
            // idempotent path (per-keystroke work for the LSP) and guarantees a
            // byte-identical result.
            return javaSource;
        }

        LexicalPreservingPrinter.setup(cu);
        domain.addField(type, fieldName, Modifier.Keyword.PRIVATE);
        return LexicalPreservingPrinter.print(cu);
    }
}
