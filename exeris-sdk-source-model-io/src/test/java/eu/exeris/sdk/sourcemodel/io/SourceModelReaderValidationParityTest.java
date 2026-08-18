package eu.exeris.sdk.sourcemodel.io;

import eu.exeris.sdk.annotation.Validation;
import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.FieldMetadata;
import org.assertj.core.api.AbstractListAssert;
import org.assertj.core.api.ObjectAssert;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * Reader ↔ processor parity over the {@code @Validation} → {@link FieldMetadata}
 * surface (ADR-042). The processor's {@code extractFieldMetadata} extracts five
 * attributes present-only — {@code min}/{@code max}/{@code minLength}/
 * {@code maxLength}/{@code pattern} — and the reader must carry the same values,
 * or every reattach over a field declaring one of them reports spurious drift.
 * {@code minLength}/{@code maxLength} were silently dropped on read until 0.9.0;
 * the budgetHQ corpus (14 {@code maxLength} occurrences, 5 patterns) pins the
 * fix to real values below.
 */
@DisplayName("reader↔processor @Validation parity: bounds land on FieldMetadata")
class SourceModelReaderValidationParityTest {

    private static final Path CORPUS_DIR = Path.of("src", "test", "resources", "corpus", "budgethq");

    private final SourceModelReader reader = new SourceModelReader();

    private DomainMetadata read(String fileName) {
        try {
            return reader.read(Files.readString(CORPUS_DIR.resolve(fileName))).orElseThrow();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Fields carrying any of the five extracted validation bounds, in source order. */
    private static List<FieldMetadata> validated(DomainMetadata domain) {
        return domain.fields().stream().filter(FieldMetadata::hasValidation).toList();
    }

    /**
     * (name, minLength, maxLength, min, max, pattern) tuples of the validated
     * fields — {@code containsExactly} over these asserts both the exact declared
     * values and that no other field gained a bound it never declared.
     */
    private static AbstractListAssert<?, List<? extends Tuple>, Tuple, ObjectAssert<Tuple>> assertValidated(DomainMetadata domain) {
        return assertThat(validated(domain))
                .extracting(FieldMetadata::name, FieldMetadata::minLength, FieldMetadata::maxLength,
                        FieldMetadata::min, FieldMetadata::max, FieldMetadata::pattern);
    }

    // --- corpus-grounded value assertions (every declaring field, exact values) ---

    @Test
    void bankAccountCarriesMaxLengthsAndCurrencyPattern() {
        assertValidated(read("BankAccount.java")).containsExactly(
                tuple("providerAccountId", null, 256, null, null, null),
                tuple("name", null, 256, null, null, null),
                tuple("currency", null, null, null, null, "[A-Z]{3}"),
                tuple("type", null, 64, null, null, null));
    }

    @Test
    void bankConnectionCarriesProviderAndStatusMaxLengths() {
        assertValidated(read("BankConnection.java")).containsExactly(
                tuple("provider", null, 64, null, null, null),
                tuple("status", null, 32, null, null, null));
    }

    @Test
    void bankTransactionCarriesMaxLengthsAndCurrencyPattern() {
        assertValidated(read("BankTransaction.java")).containsExactly(
                tuple("providerTxId", null, 256, null, null, null),
                tuple("currency", null, null, null, null, "[A-Z]{3}"),
                tuple("description", null, 512, null, null, null),
                tuple("categoryHint", null, 128, null, null, null));
    }

    @Test
    void investmentCarriesMaxLengthsAndCurrencyPattern() {
        assertValidated(read("Investment.java")).containsExactly(
                tuple("name", null, 256, null, null, null),
                tuple("ticker", null, 16, null, null, null),
                tuple("assetClass", null, 64, null, null, null),
                tuple("currency", null, null, null, null, "[A-Z]{3}"));
    }

    @Test
    void userCarriesMaxLengthsEmailRegexAndCurrencyPattern() {
        // email co-declares pattern + maxLength on ONE @Validation; the regex is
        // asserted in its unescaped form — StringLiteralExpr.asString() unescapes
        // "\\s" to "\s", matching the javac-side value the processor extracts
        assertValidated(read("User.java")).containsExactly(
                tuple("email", null, 320, null, null, "^[^@\\s]+@[^@\\s]+$"),
                tuple("displayName", null, 128, null, null, null),
                tuple("locale", null, 35, null, null, null),
                tuple("defaultCurrency", null, null, null, null, "[A-Z]{3}"));
    }

    @Test
    void corpusValidationInventoryIsFourteenMaxLengthsAndFivePatterns() {
        // pins the corpus-wide multiset so a corpus member added/edited with new
        // validation bounds must also join the per-entity assertions above —
        // and so a silently shrunken corpus cannot pass the suite vacuously
        List<FieldMetadata> all;
        try (Stream<Path> files = Files.list(CORPUS_DIR)) {
            all = files.filter(p -> p.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .map(p -> {
                        try {
                            return Files.readString(p);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .flatMap(source -> reader.read(source).stream())
                    .flatMap(domain -> domain.fields().stream())
                    .filter(FieldMetadata::hasValidation)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        assertThat(all.stream().map(FieldMetadata::maxLength).filter(Objects::nonNull))
                .containsExactlyInAnyOrder(16, 32, 35, 64, 64, 64, 128, 128, 256, 256, 256, 256, 320, 512);
        assertThat(all.stream().map(FieldMetadata::pattern).filter(Objects::nonNull))
                .containsExactlyInAnyOrder("[A-Z]{3}", "[A-Z]{3}", "[A-Z]{3}", "[A-Z]{3}", "^[^@\\s]+@[^@\\s]+$");
        // the corpus declares none of these three — their present-paths are pinned
        // by readsAllFiveBoundsFromOneAnnotation below, not by the corpus
        assertThat(all).allSatisfy(field -> {
            assertThat(field.minLength()).isNull();
            assertThat(field.min()).isNull();
            assertThat(field.max()).isNull();
        });
    }

    // --- present-paths the corpus cannot exercise (no minLength/min/max in corpus) ---

    @Test
    void readsAllFiveBoundsFromOneAnnotation() {
        // min is deliberately non-zero: FieldMetadata is @JsonInclude(NON_DEFAULT)
        // and Jackson 3 drops boxed Long(0) on serialization, so 0 is not a
        // wire-safe min until the Field/Validation overlap fix
        String src = """
                package x;
                import eu.exeris.sdk.annotation.ExerisDomain;
                import eu.exeris.sdk.annotation.Field;
                import eu.exeris.sdk.annotation.Validation;
                @ExerisDomain
                public class T {
                    @Field(label = "Code")
                    @Validation(min = 1, max = 9999, minLength = 2, maxLength = 40, pattern = "[a-z]+")
                    private String code;

                    @Field(label = "Free")
                    @Validation(maxLength = 10)
                    private String free;
                }
                """;
        DomainMetadata domain = reader.read(src).orElseThrow();
        assertThat(domain.findField("code")).get().satisfies(field -> {
            assertThat(field.min()).isEqualTo(1L);
            assertThat(field.max()).isEqualTo(9999L);
            assertThat(field.minLength()).isEqualTo(2);
            assertThat(field.maxLength()).isEqualTo(40);
            assertThat(field.pattern()).isEqualTo("[a-z]+");
        });
        // present-only: a lone maxLength must not flood in the annotation defaults
        // (minLength 0 / min Long.MIN_VALUE / max Long.MAX_VALUE) — absent stays null
        assertThat(domain.findField("free")).get().satisfies(field -> {
            assertThat(field.maxLength()).isEqualTo(10);
            assertThat(field.minLength()).isNull();
            assertThat(field.min()).isNull();
            assertThat(field.max()).isNull();
            assertThat(field.pattern()).isNull();
        });
    }

    // --- declared-extraction-set guard over @Validation's attribute surface ---

    /**
     * The five attributes {@code applyValidation} maps onto {@link FieldMetadata},
     * in lock-step with the processor's extraction.
     */
    private static final Set<String> APPLIED =
            Set.of("min", "max", "minLength", "maxLength", "pattern");

    /**
     * Deliberately unextracted until a generator consumes them — mirrors the
     * processor, which reads none of these into {@link FieldMetadata} either.
     * Moving an entry out of this set means teaching BOTH the processor and
     * {@link SourceModelReader} in the same release: parity first (ADR-042).
     */
    private static final Set<String> DOCUMENTED_UNEXTRACTED = Set.of(
            "decimalMin", "decimalMax",
            "email", "url", "uuid",
            "positive", "positiveOrZero", "negative", "negativeOrZero",
            "past", "pastOrPresent", "future", "futureOrPresent",
            "customValidator", "message", "groups", "asyncValidator");

    @Test
    void everyValidationAttributeIsAppliedOrDocumentedUnextracted() {
        // AnnotationContractTest-style guard: the NEXT @Validation attribute added
        // without an extraction decision fails here instead of shipping silently
        // unread (how the minLength/maxLength gap survived until 0.9.0).
        // Deprecated attributes (required, validateOn) ride the deprecation
        // pipeline, not the extraction contract, so they are excluded.
        Set<String> declared = Arrays.stream(Validation.class.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .filter(method -> !method.isAnnotationPresent(Deprecated.class))
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertThat(APPLIED).doesNotContainAnyElementsOf(DOCUMENTED_UNEXTRACTED);
        // set equality both ways: a new attribute must be classified, and a stale
        // entry (removed/renamed attribute) must be pruned
        assertThat(declared).isEqualTo(
                Stream.concat(APPLIED.stream(), DOCUMENTED_UNEXTRACTED.stream())
                        .collect(Collectors.toSet()));
    }
}
