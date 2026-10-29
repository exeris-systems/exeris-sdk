package eu.exeris.sdk.sourcemodel.ast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ActionParamMetadata: binding source and query key")
class ActionParamMetadataTest {

    @Test
    void anUndeclaredBindingIsTheBody() {
        ActionParamMetadata p = ActionParamMetadata.required("amount", "BigDecimal");
        assertThat(p.bindingSource()).isNull();
        assertThat(p.queryKey()).isNull();
        assertThat(p.effectiveBindingSource()).isEqualTo(ParamSource.BODY);
        assertThat(p.effectiveQueryKey()).isEqualTo("amount");
    }

    @Test
    void aQueryParameterIsReadUnderItsKeyOrItsName() {
        ActionParamMetadata keyed = ActionParamMetadata.builder("term", "String")
                .bindingSource(ParamSource.QUERY).queryKey("q").build();
        assertThat(keyed.effectiveBindingSource()).isEqualTo(ParamSource.QUERY);
        assertThat(keyed.effectiveQueryKey()).isEqualTo("q");

        ActionParamMetadata unkeyed = ActionParamMetadata.builder("term", "String")
                .bindingSource(ParamSource.QUERY).build();
        assertThat(unkeyed.effectiveQueryKey()).isEqualTo("term");
    }

    @Test
    void aKeyThatRestatesTheNameOrIsBlankIsStoredAsAbsent() {
        // Two producers, one writing the key only when it differs and one writing it always,
        // must build the same record.
        ActionParamMetadata restated = ActionParamMetadata.builder("term", "String")
                .bindingSource(ParamSource.QUERY).queryKey("term").build();
        ActionParamMetadata omitted = ActionParamMetadata.builder("term", "String")
                .bindingSource(ParamSource.QUERY).build();
        assertThat(restated).isEqualTo(omitted);
        assertThat(restated.queryKey()).isNull();
        assertThat(ActionParamMetadata.builder("term", "String").queryKey("  ").build().queryKey()).isNull();
    }

    @Test
    void theElevenComponentConstructorBuildsABodyParameter() {
        ActionParamMetadata old = new ActionParamMetadata("amount", "BigDecimal", "Amount", "desc",
                true, "1", "^[0-9]+$", 1, 9, 0L, 99L);
        ActionParamMetadata built = ActionParamMetadata.builder("amount", "BigDecimal")
                .displayName("Amount").description("desc").required(true).defaultValue("1")
                .pattern("^[0-9]+$").minLength(1).maxLength(9).min(0L).max(99L)
                .build();
        assertThat(old).isEqualTo(built);
        assertThat(old.bindingSource()).isNull();
        assertThat(old.queryKey()).isNull();
    }

    @Test
    void nameAndTypeAreRequired() {
        ActionParamMetadata.Builder nameless = ActionParamMetadata.builder(null, "String");
        assertThatThrownBy(nameless::build)
                .isInstanceOf(NullPointerException.class).hasMessageContaining("name");
        ActionParamMetadata.Builder typeless = ActionParamMetadata.builder("x", null);
        assertThatThrownBy(typeless::build)
                .isInstanceOf(NullPointerException.class).hasMessageContaining("type");
    }
}
