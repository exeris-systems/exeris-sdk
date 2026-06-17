package eu.exeris.sdk.sourcemodel.ast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ActionMetadata + ActionParamMetadata")
class ActionMetadataTest {

    @Test
    void compactConstructorRejectsNullName() {
        assertThatThrownBy(() -> new ActionMetadata(null, null, null, "POST", null,
                false, false, false, false, List.of(), List.of(), List.of(), null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("name");
    }

    @Test
    void compactConstructorDefaultsNullHttpMethodToPost() {
        ActionMetadata a = new ActionMetadata("approve", null, null, null, null,
                false, false, false, false, null, null, null, null);
        assertThat(a.httpMethod()).isEqualTo("POST");
        assertThat(a.params()).isEmpty();
        assertThat(a.permissions()).isEmpty();
        assertThat(a.producesEvents()).isEmpty();
    }

    @Test
    void methodNameCarriesAndFallsBackToActionName() {
        // explicit method name kept distinct from the action identity
        ActionMetadata renamed = ActionMetadata.builder("commandFormation")
                .methodName("setFormation").build();
        assertThat(renamed.methodName()).isEqualTo("setFormation");
        assertThat(renamed.effectiveMethodName()).isEqualTo("setFormation");

        // absent / blank method name → effectiveMethodName falls back to the action name
        assertThat(ActionMetadata.simple("approve").methodName()).isNull();
        assertThat(ActionMetadata.simple("approve").effectiveMethodName()).isEqualTo("approve");
        assertThat(ActionMetadata.builder("approve").methodName("  ").build().effectiveMethodName())
                .isEqualTo("approve");
    }

    @Test
    void simpleFactoryHasSafeDefaults() {
        ActionMetadata a = ActionMetadata.simple("approve");
        assertThat(a.name()).isEqualTo("approve");
        assertThat(a.httpMethod()).isEqualTo("POST");
        assertThat(a.async()).isFalse();
        assertThat(a.params()).isEmpty();
    }

    @Test
    void predicatesReflectListContent() {
        ActionMetadata empty = ActionMetadata.simple("approve");
        assertThat(empty.hasParams()).isFalse();
        assertThat(empty.hasPermissions()).isFalse();
        assertThat(empty.hasProducedEvents()).isFalse();

        ActionMetadata full = ActionMetadata.builder("reject")
                .addParam(ActionParamMetadata.required("reason", "String"))
                .permissions(List.of("ROLE_MGR"))
                .producesEvents(List.of("OrderRejected"))
                .build();
        assertThat(full.hasParams()).isTrue();
        assertThat(full.hasPermissions()).isTrue();
        assertThat(full.hasProducedEvents()).isTrue();
    }

    @Test
    void effectiveDisplayNameFallsBackToName() {
        assertThat(ActionMetadata.builder("approve").displayName("Approve Order").build().effectiveDisplayName())
                .isEqualTo("Approve Order");
        assertThat(ActionMetadata.simple("approve").effectiveDisplayName()).isEqualTo("approve");
        assertThat(ActionMetadata.builder("approve").displayName("   ").build().effectiveDisplayName())
                .isEqualTo("approve");
    }

    @Test
    void builderPropagatesEverySetter() {
        ActionParamMetadata p1 = ActionParamMetadata.required("reason", "String");
        ActionParamMetadata p2 = ActionParamMetadata.optional("notify", "Boolean", "true");
        ActionMetadata a = ActionMetadata.builder("reject")
                .displayName("Reject Order").description("Reject the pending order")
                .httpMethod("DELETE").resultType("void")
                .async(true).idempotent(true).dangerous(true).requiresConfirmation(true)
                .params(List.of(p1))
                .addParam(p2)
                .permissions(List.of("ROLE_MGR", "ROLE_ADMIN"))
                .producesEvents(List.of("OrderRejected"))
                .build();

        assertThat(a.displayName()).isEqualTo("Reject Order");
        assertThat(a.description()).isEqualTo("Reject the pending order");
        assertThat(a.httpMethod()).isEqualTo("DELETE");
        assertThat(a.resultType()).isEqualTo("void");
        assertThat(a.async()).isTrue();
        assertThat(a.idempotent()).isTrue();
        assertThat(a.dangerous()).isTrue();
        assertThat(a.requiresConfirmation()).isTrue();
        assertThat(a.params()).containsExactly(p1, p2);
        assertThat(a.permissions()).containsExactly("ROLE_MGR", "ROLE_ADMIN");
        assertThat(a.producesEvents()).containsExactly("OrderRejected");
    }

    // ----- ActionParamMetadata -----

    @Test
    void paramRequiredFactorySetsRequired() {
        ActionParamMetadata p = ActionParamMetadata.required("reason", "String");
        assertThat(p.name()).isEqualTo("reason");
        assertThat(p.type()).isEqualTo("String");
        assertThat(p.required()).isTrue();
        assertThat(p.defaultValue()).isNull();
    }

    @Test
    void paramOptionalFactoryUsesDefaultValue() {
        ActionParamMetadata p = ActionParamMetadata.optional("notify", "Boolean", "true");
        assertThat(p.required()).isFalse();
        assertThat(p.defaultValue()).isEqualTo("true");
    }

    @Test
    void paramRejectsNullNameOrType() {
        assertThatThrownBy(() -> new ActionParamMetadata(null, "String", null, null, false, null, null, null, null, null, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("name");
        assertThatThrownBy(() -> new ActionParamMetadata("a", null, null, null, false, null, null, null, null, null, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("type");
    }

    @Test
    void paramHasValidationTrueForAnyConstraint() {
        assertThat(ActionParamMetadata.required("a", "String").hasValidation()).isFalse();
        assertThat(ActionParamMetadata.builder("a", "String").pattern("\\d+").build().hasValidation()).isTrue();
        assertThat(ActionParamMetadata.builder("a", "String").minLength(1).build().hasValidation()).isTrue();
        assertThat(ActionParamMetadata.builder("a", "String").maxLength(10).build().hasValidation()).isTrue();
        assertThat(ActionParamMetadata.builder("a", "Long").min(1L).build().hasValidation()).isTrue();
        assertThat(ActionParamMetadata.builder("a", "Long").max(99L).build().hasValidation()).isTrue();
    }

    @Test
    void paramEffectiveDisplayNameFallsBackToName() {
        assertThat(ActionParamMetadata.builder("reason", "String").displayName("Reason").build().effectiveDisplayName())
                .isEqualTo("Reason");
        assertThat(ActionParamMetadata.builder("reason", "String").displayName("  ").build().effectiveDisplayName())
                .isEqualTo("reason");
        assertThat(ActionParamMetadata.required("reason", "String").effectiveDisplayName()).isEqualTo("reason");
    }

    @Test
    void paramBuilderPropagatesEverySetter() {
        ActionParamMetadata p = ActionParamMetadata.builder("reason", "String")
                .displayName("Reason").description("Why rejected").required(true)
                .defaultValue("n/a").pattern(".+").minLength(1).maxLength(255)
                .min(0L).max(1L)
                .build();
        assertThat(p.displayName()).isEqualTo("Reason");
        assertThat(p.description()).isEqualTo("Why rejected");
        assertThat(p.required()).isTrue();
        assertThat(p.defaultValue()).isEqualTo("n/a");
        assertThat(p.pattern()).isEqualTo(".+");
        assertThat(p.minLength()).isEqualTo(1);
        assertThat(p.maxLength()).isEqualTo(255);
        assertThat(p.min()).isEqualTo(0L);
        assertThat(p.max()).isEqualTo(1L);
    }
}
