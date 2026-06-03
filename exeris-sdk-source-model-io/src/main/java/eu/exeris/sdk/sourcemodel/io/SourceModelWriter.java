package eu.exeris.sdk.sourcemodel.io;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.util.Optional;

/**
 * Applies field-level edits to Java source <em>idempotently</em>, preserving the
 * user's formatting, comments, and non-Exeris annotations.
 *
 * <p>This is the genuinely hard half of 0.3.0 and the reason {@code -io} is its
 * own module: codegen emits <em>fresh</em> files, whereas an editor/LSP must
 * surgically modify user-authored sources without reformatting them. The
 * preservation is JavaParser's {@code LexicalPreservingPrinter}: only the nodes
 * we touch change; everything else is reproduced byte-for-byte. Every method is
 * idempotent — a no-op edit returns the original source unchanged (and skips
 * the printer entirely, so re-applying a mutation is byte-stable and cheap).
 *
 * <p>0.3.0 scope: field mutations — {@link #addField}, {@link #renameField},
 * {@link #changeFieldType}, {@link #removeField}. These are the application
 * half of what 0.5.0 will model as {@code MutationOp} records (ADR-037
 * pre-emptive ruling: the op records live in {@code source-model}; their
 * application lives here). Action/relationship/UI mutations follow.
 *
 * <p><b>Limitations.</b> Edits act on the field <em>declaration</em> only —
 * references elsewhere (getters, usages) are not updated; that needs symbol
 * solving, which this module deliberately avoids. {@code @ExerisDomain} is
 * matched by simple name. Instances are <b>not thread-safe</b> (single
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
     * type, preserving everything else verbatim. No-op (source returned unchanged)
     * if a field of that name already exists.
     *
     * @throws IllegalArgumentException if the source is not valid Java or has no
     *                                  {@code @ExerisDomain} type
     */
    public String addField(String javaSource, String type, String fieldName) {
        CompilationUnit cu = parseOrThrow(javaSource);
        ClassOrInterfaceDeclaration domain = domainOrThrow(cu);
        if (findVariable(domain, fieldName).isPresent()) {
            return javaSource;
        }
        LexicalPreservingPrinter.setup(cu);
        domain.addField(type, fieldName, Modifier.Keyword.PRIVATE);
        return LexicalPreservingPrinter.print(cu);
    }

    /**
     * Renames field {@code fromName} to {@code toName} on the declaration.
     * No-op if {@code fromName} is absent (already renamed / never present) or
     * {@code toName} already exists (renaming would create a duplicate). The
     * {@code fromName.equals(toName)} case is therefore also a no-op, since
     * {@code toName} is by definition present.
     *
     * @throws IllegalArgumentException if the source is not valid Java or has no
     *                                  {@code @ExerisDomain} type
     */
    public String renameField(String javaSource, String fromName, String toName) {
        CompilationUnit cu = parseOrThrow(javaSource);
        ClassOrInterfaceDeclaration domain = domainOrThrow(cu);
        Optional<VariableDeclarator> target = findVariable(domain, fromName);
        if (target.isEmpty() || findVariable(domain, toName).isPresent()) {
            return javaSource;
        }
        LexicalPreservingPrinter.setup(cu);
        target.get().setName(toName);
        return LexicalPreservingPrinter.print(cu);
    }

    /**
     * Changes the declared type of {@code fieldName} to {@code newType}. No-op if
     * the field is absent or already has that type. The "already that type" check
     * is <em>textual</em> (no symbol solving): a field declared {@code String} is
     * not recognised as equal to {@code java.lang.String}, so passing a
     * fully-qualified name where the source writes it unqualified (or vice versa)
     * will rewrite the declaration rather than no-op.
     *
     * @throws IllegalArgumentException if the source is not valid Java or has no
     *                                  {@code @ExerisDomain} type
     */
    public String changeFieldType(String javaSource, String fieldName, String newType) {
        CompilationUnit cu = parseOrThrow(javaSource);
        ClassOrInterfaceDeclaration domain = domainOrThrow(cu);
        Optional<VariableDeclarator> target = findVariable(domain, fieldName);
        if (target.isEmpty() || target.get().getTypeAsString().equals(newType)) {
            return javaSource;
        }
        LexicalPreservingPrinter.setup(cu);
        target.get().setType(newType);
        return LexicalPreservingPrinter.print(cu);
    }

    /**
     * Removes {@code fieldName}. If it is the sole variable of its declaration the
     * whole {@code FieldDeclaration} (and its annotations/comments) is removed;
     * otherwise just that variable is dropped from a multi-variable declaration.
     * No-op if the field is absent.
     *
     * @throws IllegalArgumentException if the source is not valid Java or has no
     *                                  {@code @ExerisDomain} type
     */
    public String removeField(String javaSource, String fieldName) {
        CompilationUnit cu = parseOrThrow(javaSource);
        ClassOrInterfaceDeclaration domain = domainOrThrow(cu);
        Optional<VariableDeclarator> target = findVariable(domain, fieldName);
        if (target.isEmpty()) {
            return javaSource;
        }
        LexicalPreservingPrinter.setup(cu);
        VariableDeclarator variable = target.get();
        // Invariant: findVariable only returns variables streamed from getFields(),
        // so the enclosing FieldDeclaration is always present.
        FieldDeclaration declaration = variable.findAncestor(FieldDeclaration.class).get();
        if (declaration.getVariables().size() == 1) {
            declaration.remove();
        } else {
            variable.remove();
        }
        return LexicalPreservingPrinter.print(cu);
    }

    private CompilationUnit parseOrThrow(String javaSource) {
        ParseResult<CompilationUnit> result = javaParser.parse(javaSource);
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            throw new IllegalArgumentException("Source is not valid Java: " + result.getProblems());
        }
        return result.getResult().get();
    }

    private ClassOrInterfaceDeclaration domainOrThrow(CompilationUnit cu) {
        return cu.findFirst(ClassOrInterfaceDeclaration.class,
                        c -> c.isAnnotationPresent("ExerisDomain"))
                .orElseThrow(() -> new IllegalArgumentException("No @ExerisDomain type in source"));
    }

    private Optional<VariableDeclarator> findVariable(ClassOrInterfaceDeclaration domain, String name) {
        return domain.getFields().stream()
                .flatMap(field -> field.getVariables().stream())
                .filter(variable -> variable.getNameAsString().equals(name))
                .findFirst();
    }
}
