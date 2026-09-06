package eu.exeris.sdk.sourcemodel.io;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
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
 * {@link #changeFieldType}, {@link #removeField} — relationship mutations —
 * {@link #addRelationship}, {@link #removeRelationship} — and action mutations —
 * {@link #addAction}, {@link #removeAction}. These are the
 * application half of what 0.5.0 will model as {@code MutationOp} records
 * (ADR-037 pre-emptive ruling: the op records live in {@code source-model};
 * their application lives here). UI mutations follow.
 *
 * <p><b>Limitations.</b> Removal mutations are not byte-exact inverses of their
 * additions: {@code LexicalPreservingPrinter} leaves an indentation-only line
 * where the removed member stood (characterized by
 * {@code BudgetHqCorpusRoundTripTest} — content and AST equality hold, byte
 * equality does not). Consumers diffing for conflict detection must compare
 * ASTs, not text. Edits act on the field <em>declaration</em> only —
 * references elsewhere (getters, usages) are not updated; that needs symbol
 * solving, which this module deliberately avoids. {@code @ExerisDomain} is
 * matched by simple name. Instances are <b>not thread-safe</b> (single
 * {@code JavaParser}); use one per call-site or guard externally. The parser
 * language level is JavaParser's {@code CURRENT} (Java 21 in 3.28.x); neither the
 * JDK 25 LTS baseline (ADR-069) nor the JDK 26 the reactor also builds against
 * added grammar an entity-shaped source uses, so those sources parse — revisit
 * when JavaParser ships a level matching the baseline.
 *
 * @since 0.3
 */
public final class SourceModelWriter {

    private final JavaParser javaParser;

    /**
     * Creates a writer with the default JavaParser configuration.
     */
    public SourceModelWriter() {
        this.javaParser = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.CURRENT));
    }

    /**
     * Adds a {@code private <type> <fieldName>;} to the first {@code @ExerisDomain}
     * type, preserving everything else verbatim. No-op (source returned unchanged)
     * if a field of that name already exists.
     *
     * @param javaSource the entity's Java source text
     * @param type the new field's Java type
     * @param fieldName the new field's name
     * @return the source with the field added
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
     * @param javaSource the entity's Java source text
     * @param fromName the field's current name
     * @param toName the name to give it
     * @return the source with the field renamed
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
     * @param javaSource the entity's Java source text
     * @param fieldName the field to retype
     * @param newType the type to give it
     * @return the source with the field's type changed
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
     * @param javaSource the entity's Java source text
     * @param fieldName the field to remove
     * @return the source with the field removed
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
        removeVariable(target.get());
        return LexicalPreservingPrinter.print(cu);
    }

    /**
     * Adds a {@code @Relationship}-annotated field
     * {@code @Relationship(relationshipType = RelationshipType.<relationshipType>)
     * private <targetType> <fieldName>;} to the first {@code @ExerisDomain} type.
     * No-op if a field of that name already exists.
     *
     * <p>{@code relationshipType} is the {@code RelationshipType} constant name
     * (e.g. {@code "ONE_TO_MANY"}); it is written verbatim into the annotation,
     * not validated against the enum.
     *
     * @param javaSource the entity's Java source text
     * @param fieldName the field holding the association
     * @param targetType the entity on the other end
     * @param relationshipType the association's cardinality
     * @return the source with the relationship added
     * @throws IllegalArgumentException if the source is not valid Java, has no
     *                                  {@code @ExerisDomain} type, or
     *                                  {@code relationshipType} produces a
     *                                  malformed annotation
     */
    public String addRelationship(String javaSource, String fieldName, String targetType, String relationshipType) {
        CompilationUnit cu = parseOrThrow(javaSource);
        ClassOrInterfaceDeclaration domain = domainOrThrow(cu);
        if (findVariable(domain, fieldName).isPresent()) {
            return javaSource;
        }
        // Parse via the instance parser (not StaticJavaParser) to keep the
        // configured language level and the class's single-parser threading
        // contract; validate before mutating so a bad type throws cleanly.
        ParseResult<AnnotationExpr> annotation = javaParser.parseAnnotation(
                "@Relationship(relationshipType = RelationshipType." + relationshipType + ")");
        if (!annotation.isSuccessful() || annotation.getResult().isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid relationshipType '" + relationshipType + "': " + annotation.getProblems());
        }
        LexicalPreservingPrinter.setup(cu);
        domain.addField(targetType, fieldName, Modifier.Keyword.PRIVATE)
                .addAnnotation(annotation.getResult().get());
        return LexicalPreservingPrinter.print(cu);
    }

    /**
     * Removes the field {@code fieldName} only if it carries {@code @Relationship}
     * (so it won't silently delete a plain field that happens to share the name).
     * No-op if the field is absent or is not a relationship. Removal semantics
     * match {@link #removeField} (whole declaration vs. single variable).
     *
     * @param javaSource the entity's Java source text
     * @param fieldName the field holding the association
     * @return the source with the relationship removed
     * @throws IllegalArgumentException if the source is not valid Java or has no
     *                                  {@code @ExerisDomain} type
     */
    public String removeRelationship(String javaSource, String fieldName) {
        CompilationUnit cu = parseOrThrow(javaSource);
        ClassOrInterfaceDeclaration domain = domainOrThrow(cu);
        Optional<VariableDeclarator> target = findVariable(domain, fieldName);
        if (target.isEmpty()) {
            return javaSource;
        }
        // Invariant: a variable from getFields() always has an enclosing FieldDeclaration.
        FieldDeclaration declaration = target.get().findAncestor(FieldDeclaration.class).get();
        if (declaration.getAnnotationByName("Relationship").isEmpty()) {
            return javaSource; // present, but not a relationship -> no-op
        }
        LexicalPreservingPrinter.setup(cu);
        removeVariable(target.get());
        return LexicalPreservingPrinter.print(cu);
    }

    /**
     * Drops a variable's whole {@code FieldDeclaration} (with its annotations and
     * comments) when it is the sole variable, otherwise just that variable from a
     * multi-variable declaration. Caller must have run {@code LexicalPreservingPrinter.setup}.
     */
    private void removeVariable(VariableDeclarator variable) {
        // Invariant: callers pass variables streamed from getFields(), so the
        // enclosing FieldDeclaration is always present.
        FieldDeclaration declaration = variable.findAncestor(FieldDeclaration.class).get();
        if (declaration.getVariables().size() == 1) {
            declaration.remove();
        } else {
            variable.remove();
        }
    }

    /**
     * Adds a minimal {@code @Action(name = "<actionName>", path = "/<actionName>")
     * public void <actionName>() {}} method to the first {@code @ExerisDomain}
     * type. No-op if an action of that name already exists (matched by effective
     * action name — {@code @Action.name} or, absent, the method name).
     *
     * @param javaSource the entity's Java source text
     * @param actionName the action's name
     * @return the source with the action added
     * @throws IllegalArgumentException if the source is not valid Java, has no
     *                                  {@code @ExerisDomain} type, or
     *                                  {@code actionName} produces a malformed
     *                                  annotation
     */
    public String addAction(String javaSource, String actionName) {
        CompilationUnit cu = parseOrThrow(javaSource);
        ClassOrInterfaceDeclaration domain = domainOrThrow(cu);
        if (findAction(domain, actionName).isPresent()) {
            return javaSource;
        }
        ParseResult<AnnotationExpr> annotation = javaParser.parseAnnotation(
                "@Action(name = \"" + actionName + "\", path = \"/" + actionName + "\")");
        if (!annotation.isSuccessful() || annotation.getResult().isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid actionName '" + actionName + "': " + annotation.getProblems());
        }
        LexicalPreservingPrinter.setup(cu);
        domain.addMethod(actionName, Modifier.Keyword.PUBLIC)
                .addAnnotation(annotation.getResult().get());
        return LexicalPreservingPrinter.print(cu);
    }

    /**
     * Removes the {@code @Action} method whose effective action name
     * ({@code @Action.name}, or the method name when absent) equals
     * {@code actionName}. Methods without {@code @Action} are never touched.
     * No-op if no such action exists.
     *
     * @param javaSource the entity's Java source text
     * @param actionName the action to remove
     * @return the source with the action removed
     * @throws IllegalArgumentException if the source is not valid Java or has no
     *                                  {@code @ExerisDomain} type
     */
    public String removeAction(String javaSource, String actionName) {
        CompilationUnit cu = parseOrThrow(javaSource);
        ClassOrInterfaceDeclaration domain = domainOrThrow(cu);
        Optional<MethodDeclaration> target = findAction(domain, actionName);
        if (target.isEmpty()) {
            return javaSource;
        }
        LexicalPreservingPrinter.setup(cu);
        target.get().remove();
        return LexicalPreservingPrinter.print(cu);
    }

    private Optional<MethodDeclaration> findAction(ClassOrInterfaceDeclaration domain, String actionName) {
        return domain.getMethods().stream()
                .filter(method -> method.getAnnotationByName("Action").isPresent())
                .filter(method -> effectiveActionName(method).equals(actionName))
                .findFirst();
    }

    /** {@code @Action.name} when present and non-blank, else the method name (matches the reader). */
    private String effectiveActionName(MethodDeclaration method) {
        return method.getAnnotationByName("Action")
                .filter(AnnotationExpr::isNormalAnnotationExpr)
                .map(AnnotationExpr::asNormalAnnotationExpr)
                .flatMap(ann -> ann.getPairs().stream()
                        .filter(pair -> pair.getNameAsString().equals("name"))
                        .map(pair -> pair.getValue())
                        .filter(Expression::isStringLiteralExpr)
                        .map(value -> value.asStringLiteralExpr().asString())
                        .findFirst())
                .filter(name -> !name.isBlank())
                .orElse(method.getNameAsString());
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
