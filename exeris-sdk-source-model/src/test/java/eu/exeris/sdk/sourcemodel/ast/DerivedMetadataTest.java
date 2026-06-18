package eu.exeris.sdk.sourcemodel.ast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DerivedMetadata")
class DerivedMetadataTest {

    @Test
    void ofExpressionHasDefaults() {
        DerivedMetadata d = DerivedMetadata.of("unitPrice * quantity");
        assertThat(d.expression()).isEqualTo("unitPrice * quantity");
        assertThat(d.language()).isNull();
        assertThat(d.effectiveLanguage()).isEqualTo("spel");
        assertThat(d.dependsOn()).isEmpty();
        assertThat(d.hasDependencies()).isFalse();
    }

    @Test
    void ofExpressionWithDependencies() {
        DerivedMetadata d = DerivedMetadata.of("customer.tier", List.of("customer.tier"));
        assertThat(d.dependsOn()).containsExactly("customer.tier");
        assertThat(d.hasDependencies()).isTrue();
    }

    @Test
    void compactConstructorNormalizes() {
        // explicit language kept
        assertThat(new DerivedMetadata("x", "jexl", null).effectiveLanguage()).isEqualTo("jexl");
        // blank language → null → default applies
        DerivedMetadata blank = new DerivedMetadata("x", "   ", null);
        assertThat(blank.language()).isNull();
        assertThat(blank.effectiveLanguage()).isEqualTo("spel");
        // null dependsOn → empty list
        assertThat(new DerivedMetadata("x", null, null).dependsOn()).isEmpty();
    }

    @Test
    void expressionIsRequiredNonBlank() {
        assertThatThrownBy(() -> new DerivedMetadata(null, "spel", List.of()))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("expression");
        assertThatThrownBy(() -> new DerivedMetadata("   ", "spel", List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("expression");
    }

    @Test
    void dependsOnCopiedDefensively() {
        List<String> src = new ArrayList<>();
        src.add("a");
        DerivedMetadata d = new DerivedMetadata("x", null, src);
        src.add("b");
        assertThat(d.dependsOn()).containsExactly("a");
    }
}
