package eu.exeris.sdk.sourcemodel.io;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
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
 * and extracts its entity name ({@code @ExerisDomain(name=...)}, falling back to
 * the class name), package, and fields. Of the {@code @Field} attributes only
 * {@code required} is read; all others are left at the {@link FieldMetadata}
 * factory defaults (e.g. {@code searchable}/{@code sortable}/{@code filterable}
 * default to {@code true} — see {@link FieldMetadata#simple}). Actions,
 * relationships, UI, etc. are not yet read; those follow in 0.3.0. The shape it
 * produces is the same canonical {@code DomainMetadata} the processor emits.
 *
 * <p><b>Limitations.</b> Annotation matching is by <em>simple name</em>
 * ({@code ExerisDomain}, {@code Field}) without import resolution — activating
 * JavaParser symbol-solving would pull heavy optional deps and is out of scope.
 * Instances are <b>not thread-safe</b> (they hold a single {@code JavaParser});
 * use one per call-site or guard externally.
 *
 * @since 0.3.0
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

        String entityName = exerisDomainName(type).orElse(type.getNameAsString());
        return DomainMetadata.builder(entityName, packageName)
                .fields(fields)
                .build();
    }

    /**
     * The {@code name} attribute of {@code @ExerisDomain(name = "...")} when
     * present and non-blank — this is the canonical entity name the processor
     * uses, which may differ from the Java class name. Empty otherwise (caller
     * falls back to the class name).
     */
    private Optional<String> exerisDomainName(ClassOrInterfaceDeclaration type) {
        return type.getAnnotationByName("ExerisDomain")
                .filter(AnnotationExpr::isNormalAnnotationExpr)
                .map(AnnotationExpr::asNormalAnnotationExpr)
                .flatMap(ann -> ann.getPairs().stream()
                        .filter(pair -> pair.getNameAsString().equals("name"))
                        .map(MemberValuePair::getValue)
                        .filter(Expression::isStringLiteralExpr)
                        .map(value -> value.asStringLiteralExpr().asString())
                        .findFirst())
                .filter(name -> !name.isBlank());
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
