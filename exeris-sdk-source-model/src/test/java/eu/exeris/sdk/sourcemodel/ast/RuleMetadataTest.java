package eu.exeris.sdk.sourcemodel.ast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RuleMetadata")
class RuleMetadataTest {

    @Test
    void ofNameExpressionHasDefaults() {
        RuleMetadata r = RuleMetadata.of("hasLineItems", "lineItems.size() > 0");
        assertThat(r.name()).isEqualTo("hasLineItems");
        assertThat(r.expression()).isEqualTo("lineItems.size() > 0");
        assertThat(r.message()).isNull();
        assertThat(r.severity()).isNull();
        assertThat(r.language()).isNull();
        assertThat(r.effectiveSeverity()).isEqualTo("ERROR");
        assertThat(r.effectiveLanguage()).isEqualTo("spel");
        assertThat(r.hasMessage()).isFalse();
    }

    @Test
    void fullRuleKeepsDeclaredValues() {
        RuleMetadata r = new RuleMetadata("discountNeedsApproval",
                "discount <= 0 or approvedBy != null", "order.rule.discount", "WARN", "jexl");
        assertThat(r.message()).isEqualTo("order.rule.discount");
        assertThat(r.hasMessage()).isTrue();
        assertThat(r.effectiveSeverity()).isEqualTo("WARN");
        assertThat(r.effectiveLanguage()).isEqualTo("jexl");
    }

    @Test
    void compactConstructorNormalizesBlanksToNull() {
        RuleMetadata r = new RuleMetadata("r", "expr", "   ", "   ", "   ");
        assertThat(r.message()).isNull();
        assertThat(r.severity()).isNull();
        assertThat(r.language()).isNull();
        assertThat(r.effectiveSeverity()).isEqualTo("ERROR");
        assertThat(r.effectiveLanguage()).isEqualTo("spel");
        assertThat(r.hasMessage()).isFalse();
    }

    @Test
    void nameAndExpressionAreRequiredNonBlank() {
        assertThatThrownBy(() -> new RuleMetadata(null, "expr", null, null, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("name");
        assertThatThrownBy(() -> new RuleMetadata("   ", "expr", null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("name");
        assertThatThrownBy(() -> new RuleMetadata("r", null, null, null, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("expression");
        assertThatThrownBy(() -> new RuleMetadata("r", "  ", null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("expression");
    }
}
