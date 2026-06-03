package eu.exeris.sdk.sourcemodel.io;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.FieldMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads Java source into a {@link DomainMetadata} without invoking {@code javac}
 * — the in-editor counterpart to the build-time annotation processor.
 *
 * <p>0.3.0 spike scope: locates the first {@code @ExerisDomain}-annotated type
 * and extracts its name, package, and fields (with {@code @Field(required=...)}
 * honoured). It deliberately does not yet read actions, relationships, UI, etc.;
 * those follow in 0.3.0. The shape it produces is the same canonical
 * {@code DomainMetadata} the processor emits, so downstream tooling is agnostic
 * to which side produced it.
 */
public final class SourceModelReader {

    private final JavaParser javaParser;

    public SourceModelReader() {
        this.javaParser = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.CURRENT));
    }

    /**
     * Parses {@code javaSource} and returns the domain model for its first
     * {@code @ExerisDomain} type, or {@link Optional#empty()} if the source
     * contains no such type.
     *
     * @throws IllegalArgumentException if the source is not valid Java
     */
    public Optional<DomainMetadata> read(String javaSource) {
        ParseResult<CompilationUnit> result = javaParser.parse(javaSource);
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            throw new IllegalArgumentException("Source is not valid Java: " + result.getProblems());
        }
        CompilationUnit cu = result.getResult().get();
        return cu.findFirst(ClassOrInterfaceDeclaration.class,
                        type -> type.isAnnotationPresent("ExerisDomain"))
                .map(type -> toDomain(cu, type));
    }

    private DomainMetadata toDomain(CompilationUnit cu, ClassOrInterfaceDeclaration type) {
        String packageName = cu.getPackageDeclaration()
                .map(pkg -> pkg.getNameAsString())
                .orElse("");

        List<FieldMetadata> fields = new ArrayList<>();
        for (FieldDeclaration field : type.getFields()) {
            boolean required = isRequired(field);
            for (VariableDeclarator var : field.getVariables()) {
                String name = var.getNameAsString();
                String fieldType = var.getTypeAsString();
                fields.add(required
                        ? FieldMetadata.required(name, fieldType)
                        : FieldMetadata.simple(name, fieldType));
            }
        }

        return DomainMetadata.builder(type.getNameAsString(), packageName)
                .fields(fields)
                .build();
    }

    /**
     * {@code @Field}-shape ownership of {@code required} (canonical per the
     * annotations package-info): true only when {@code @Field(required = true)}
     * is present. A bare {@code @Field} marker or no annotation means not-required.
     */
    private boolean isRequired(FieldDeclaration field) {
        Optional<AnnotationExpr> ann = field.getAnnotationByName("Field");
        if (ann.isEmpty() || !ann.get().isNormalAnnotationExpr()) {
            return false;
        }
        NormalAnnotationExpr normal = ann.get().asNormalAnnotationExpr();
        return normal.getPairs().stream()
                .anyMatch(pair -> pair.getNameAsString().equals("required")
                        && pair.getValue().toString().equals("true"));
    }
}
