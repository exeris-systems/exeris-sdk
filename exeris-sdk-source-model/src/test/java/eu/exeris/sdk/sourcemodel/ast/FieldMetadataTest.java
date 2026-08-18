package eu.exeris.sdk.sourcemodel.ast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FieldMetadata: factories, type checks, builder")
class FieldMetadataTest {

    @Test
    void compactConstructorRejectsNullName() {
        assertThatThrownBy(() -> new FieldMetadata(null, "String", null, null, null,
                false, false, false, false, false, false, false, false, false, null,
                null, null, null, null, null, null, null, null, false, null, true, true, null, null, null, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("name");
    }

    @Test
    void compactConstructorRejectsNullType() {
        assertThatThrownBy(() -> new FieldMetadata("amount", null, null, null, null,
                false, false, false, false, false, false, false, false, false, null,
                null, null, null, null, null, null, null, null, false, null, true, true, null, null, null, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("type");
    }

    @Test
    void compactConstructorDefaultsNullComputedFromToEmptyList() {
        FieldMetadata f = new FieldMetadata("a", "String", null, null, null,
                false, false, false, false, false, false, false, false, false, null,
                null, null, null, null, null, null, null, null, false, null, true, true, null, null, null, null);
        assertThat(f.computedFrom()).isNotNull().isEmpty();
    }

    @Test
    void simpleFactoryEnablesSearchableSortableFilterable() {
        FieldMetadata f = FieldMetadata.simple("orderNumber", "String");
        assertThat(f.name()).isEqualTo("orderNumber");
        assertThat(f.type()).isEqualTo("String");
        assertThat(f.searchable()).isTrue();
        assertThat(f.sortable()).isTrue();
        assertThat(f.filterable()).isTrue();
        assertThat(f.required()).isFalse();
    }

    @Test
    void requiredFactoryAlsoMarksRequired() {
        FieldMetadata f = FieldMetadata.required("orderNumber", "String");
        assertThat(f.required()).isTrue();
        assertThat(f.searchable()).isTrue();
    }

    @Test
    void hasValidationTrueWhenAnyConstraintSet() {
        assertThat(FieldMetadata.simple("a", "String").hasValidation()).isFalse();
        assertThat(FieldMetadata.builder("a", "String").minLength(1).build().hasValidation()).isTrue();
        assertThat(FieldMetadata.builder("a", "String").maxLength(10).build().hasValidation()).isTrue();
        assertThat(FieldMetadata.builder("a", "Long").min(1L).build().hasValidation()).isTrue();
        assertThat(FieldMetadata.builder("a", "Long").max(99L).build().hasValidation()).isTrue();
        assertThat(FieldMetadata.builder("a", "String").pattern("\\d+").build().hasValidation()).isTrue();
    }

    @Test
    void builderPreservesZeroValuedBounds() {
        // 0.9.0: zero is a meaningful bound (per-component NON_NULL on the
        // wire); the builder and accessors must hand back 0, not null.
        FieldMetadata f = FieldMetadata.builder("amount", "Long")
                .min(0L).max(0L).minLength(0).maxLength(0)
                .build();
        assertThat(f.min()).isEqualTo(0L);
        assertThat(f.max()).isEqualTo(0L);
        assertThat(f.minLength()).isEqualTo(0);
        assertThat(f.maxLength()).isEqualTo(0);
    }

    @Test
    void hasValidationTrueWhenOnlyZeroMinSet() {
        // A zero bound is a real constraint (e.g. min = 0 non-negativity), not
        // an unset one — hasValidation() is a null-check, not a truthiness check.
        assertThat(FieldMetadata.builder("a", "Long").min(0L).build().hasValidation()).isTrue();
    }

    @Test
    void effectiveColumnNameUsesExplicitWhenSet() {
        FieldMetadata f = FieldMetadata.builder("orderNumber", "String").columnName("order_no").build();
        assertThat(f.effectiveColumnName()).isEqualTo("order_no");
    }

    @Test
    void effectiveColumnNameSnakeCasesFieldNameWhenBlank() {
        assertThat(FieldMetadata.simple("orderLineTotal", "Long").effectiveColumnName()).isEqualTo("order_line_total");
        // Builder default for columnName is null → also falls through to snake_case.
        assertThat(FieldMetadata.builder("orderId", "Long").columnName("").build().effectiveColumnName())
                .isEqualTo("order_id");
    }

    @Test
    void effectiveDisplayNameUsesExplicitOrFallsBack() {
        assertThat(FieldMetadata.builder("amount", "Long").displayName("Order Amount").build().effectiveDisplayName())
                .isEqualTo("Order Amount");
        assertThat(FieldMetadata.builder("amount", "Long").displayName("  ").build().effectiveDisplayName())
                .isEqualTo("amount");
        assertThat(FieldMetadata.simple("amount", "Long").effectiveDisplayName()).isEqualTo("amount");
    }

    @Test
    void isEnumTrueOnlyWhenEnumTypeIsNonBlank() {
        assertThat(FieldMetadata.simple("a", "String").isEnum()).isFalse();
        assertThat(FieldMetadata.builder("a", "String").enumType("").build().isEnum()).isFalse();
        assertThat(FieldMetadata.builder("a", "String").enumType("OrderStatus").build().isEnum()).isTrue();
    }

    @Test
    void typeCategorisationCoversEveryBranch() {
        assertThat(FieldMetadata.simple("a", "String").isString()).isTrue();
        assertThat(FieldMetadata.simple("a", "java.lang.String").isString()).isTrue();
        assertThat(FieldMetadata.simple("a", "Long").isString()).isFalse();

        assertThat(FieldMetadata.simple("a", "Integer").isInteger()).isTrue();
        assertThat(FieldMetadata.simple("a", "Long").isInteger()).isTrue();
        assertThat(FieldMetadata.simple("a", "int").isInteger()).isTrue();
        assertThat(FieldMetadata.simple("a", "long").isInteger()).isTrue();
        assertThat(FieldMetadata.simple("a", "String").isInteger()).isFalse();

        assertThat(FieldMetadata.simple("a", "BigDecimal").isDecimal()).isTrue();
        assertThat(FieldMetadata.simple("a", "Double").isDecimal()).isTrue();
        assertThat(FieldMetadata.simple("a", "Float").isDecimal()).isTrue();
        assertThat(FieldMetadata.simple("a", "String").isDecimal()).isFalse();

        assertThat(FieldMetadata.simple("a", "Integer").isNumeric()).isTrue();
        assertThat(FieldMetadata.simple("a", "BigDecimal").isNumeric()).isTrue();
        assertThat(FieldMetadata.simple("a", "String").isNumeric()).isFalse();

        assertThat(FieldMetadata.simple("a", "Boolean").isBoolean()).isTrue();
        assertThat(FieldMetadata.simple("a", "boolean").isBoolean()).isTrue();
        assertThat(FieldMetadata.simple("a", "Long").isBoolean()).isFalse();

        assertThat(FieldMetadata.simple("a", "LocalDate").isTemporal()).isTrue();
        assertThat(FieldMetadata.simple("a", "LocalDateTime").isTemporal()).isTrue();
        assertThat(FieldMetadata.simple("a", "Instant").isTemporal()).isTrue();
        assertThat(FieldMetadata.simple("a", "OffsetDateTime").isTemporal()).isTrue();
        assertThat(FieldMetadata.simple("a", "String").isTemporal()).isFalse();
    }

    @Test
    void builderPropagatesEverySetter() {
        FieldMetadata f = FieldMetadata.builder("notes", "String")
                .columnName("notes_col").displayName("Notes").description("Free-form notes")
                .required(true).unique(true).indexed(true)
                .searchable(true).sortable(true).filterable(true)
                .audited(true).readOnly(true).hidden(true)
                .defaultValue("''").minLength(0).maxLength(2000)
                .min(1L).max(99L).pattern(".*").format("text").dataType("currency").enumType("")
                .computed(true).computedFrom(List.of("a", "b"))
                .inCreate(false).inUpdate(false)
                .build();

        assertThat(f.columnName()).isEqualTo("notes_col");
        assertThat(f.displayName()).isEqualTo("Notes");
        assertThat(f.description()).isEqualTo("Free-form notes");
        assertThat(f.required()).isTrue();
        assertThat(f.unique()).isTrue();
        assertThat(f.indexed()).isTrue();
        assertThat(f.audited()).isTrue();
        assertThat(f.readOnly()).isTrue();
        assertThat(f.hidden()).isTrue();
        assertThat(f.defaultValue()).isEqualTo("''");
        assertThat(f.minLength()).isEqualTo(0);
        assertThat(f.maxLength()).isEqualTo(2000);
        assertThat(f.min()).isEqualTo(1L);
        assertThat(f.max()).isEqualTo(99L);
        assertThat(f.pattern()).isEqualTo(".*");
        assertThat(f.format()).isEqualTo("text");
        assertThat(f.dataType()).isEqualTo("currency");
        assertThat(f.computed()).isTrue();
        assertThat(f.computedFrom()).containsExactly("a", "b");
        assertThat(f.inCreate()).isFalse();
        assertThat(f.inUpdate()).isFalse();
    }

    @Test
    void builderComputedFromNullCoercedToEmpty() {
        FieldMetadata f = FieldMetadata.builder("a", "String").computedFrom(null).build();
        assertThat(f.computedFrom()).isEmpty();
    }

    @Test
    void builderNormalizesBlankDataTypeToNull() {
        // @Field.dataType defaults to "": blank must become null so it is dropped
        // by @JsonInclude(NON_DEFAULT) instead of serializing as "dataType":"".
        assertThat(FieldMetadata.builder("a", "String").dataType("").build().dataType()).isNull();
        assertThat(FieldMetadata.builder("a", "String").dataType("  ").build().dataType()).isNull();
        assertThat(FieldMetadata.builder("a", "String").dataType("currency").build().dataType())
                .isEqualTo("currency");
    }

    @Test
    void builderCarriesI18nMessageKeys() {
        FieldMetadata f = FieldMetadata.builder("orderNumber", "String")
                .displayName("Order Number").displayNameKey("order.field.orderNumber.label")
                .description("The order's number").descriptionKey("order.field.orderNumber.help")
                .build();
        assertThat(f.displayNameKey()).isEqualTo("order.field.orderNumber.label");
        assertThat(f.descriptionKey()).isEqualTo("order.field.orderNumber.help");
    }

    @Test
    void builderNormalizesBlankI18nKeysToNull() {
        // @Field.labelKey/descriptionKey default to "": blank must become null so
        // they are dropped by @JsonInclude(NON_DEFAULT) rather than serialized as "".
        assertThat(FieldMetadata.builder("a", "String").displayNameKey("").build().displayNameKey()).isNull();
        assertThat(FieldMetadata.builder("a", "String").displayNameKey("  ").build().displayNameKey()).isNull();
        assertThat(FieldMetadata.builder("a", "String").displayNameKey(null).build().displayNameKey()).isNull();
        assertThat(FieldMetadata.builder("a", "String").descriptionKey("").build().descriptionKey()).isNull();
        assertThat(FieldMetadata.builder("a", "String").descriptionKey(null).build().descriptionKey()).isNull();
    }

    @Test
    void blobFacetIsAbsentUnlessDeclared() {
        assertThat(FieldMetadata.simple("total", "BigDecimal").hasBlob()).isFalse();
        assertThat(FieldMetadata.simple("total", "BigDecimal").blob()).isNull();

        FieldMetadata f = FieldMetadata.builder("statement", "BlobRef")
                .blob(BlobMetadata.ofContentTypes(java.util.List.of("application/pdf")))
                .build();
        assertThat(f.hasBlob()).isTrue();
        assertThat(f.blob().contentTypes()).containsExactly("application/pdf");
    }
}
