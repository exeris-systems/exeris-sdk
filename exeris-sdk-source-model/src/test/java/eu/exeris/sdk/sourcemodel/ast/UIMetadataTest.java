package eu.exeris.sdk.sourcemodel.ast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UIMetadata + nested config types")
class UIMetadataTest {

    @Test
    void defaultsFactoryProducesEnterpriseDefaults() {
        UIMetadata ui = UIMetadata.defaults();
        assertThat(ui.listView()).isTrue();
        assertThat(ui.detailView()).isTrue();
        assertThat(ui.createForm()).isTrue();
        assertThat(ui.editForm()).isTrue();
        assertThat(ui.searchable()).isTrue();
        assertThat(ui.filterable()).isTrue();
        assertThat(ui.exportable()).isFalse();
        assertThat(ui.bulkActions()).isFalse();
        assertThat(ui.columns()).isEqualTo(12);
        assertThat(ui.defaultLayout()).isEqualTo("grid");
        assertThat(ui.groups()).isEmpty();
        assertThat(ui.fieldOverrides()).isEmpty();
    }

    @Test
    void builderPropagatesEverySetter() {
        UIMetadata.UIGroupMetadata g = UIMetadata.UIGroupMetadata.simple("main", "Main", List.of("name"));
        UIMetadata.UIFieldMetadata f = UIMetadata.UIFieldMetadata.simple("name", UIMetadata.ComponentType.TEXT_INPUT);
        UIMetadata ui = UIMetadata.builder()
                .icon("file").color("#000")
                .listView(false).detailView(false).createForm(false).editForm(false)
                .searchable(false).filterable(false).exportable(true).bulkActions(true)
                .columns(6).defaultLayout("list")
                .groups(List.of(g))
                .fieldOverrides(List.of(f))
                .build();
        assertThat(ui.icon()).isEqualTo("file");
        assertThat(ui.color()).isEqualTo("#000");
        assertThat(ui.listView()).isFalse();
        assertThat(ui.exportable()).isTrue();
        assertThat(ui.bulkActions()).isTrue();
        assertThat(ui.columns()).isEqualTo(6);
        assertThat(ui.defaultLayout()).isEqualTo("list");
        assertThat(ui.groups()).hasSize(1).first().isEqualTo(g);
        assertThat(ui.fieldOverrides()).hasSize(1).first().isEqualTo(f);
    }

    @Test
    void uiGroupSimpleFactorySetsDefaults() {
        UIMetadata.UIGroupMetadata g = UIMetadata.UIGroupMetadata.simple("billing", "Billing", List.of("name", "amount"));
        assertThat(g.name()).isEqualTo("billing");
        assertThat(g.label()).isEqualTo("Billing");
        assertThat(g.order()).isEqualTo(1);
        assertThat(g.gridSpan()).isEqualTo(12);
        assertThat(g.collapsible()).isFalse();
        assertThat(g.collapsed()).isFalse();
        assertThat(g.fields()).containsExactly("name", "amount");
    }

    @Test
    void uiFieldSimpleAndFullWidthFactories() {
        UIMetadata.UIFieldMetadata simple = UIMetadata.UIFieldMetadata.simple("name", UIMetadata.ComponentType.TEXT_INPUT);
        assertThat(simple.gridSpan()).isEqualTo(6);
        assertThat(simple.componentType()).isEqualTo(UIMetadata.ComponentType.TEXT_INPUT);
        assertThat(simple.displayInList()).isTrue();
        assertThat(simple.displayInDetail()).isTrue();
        assertThat(simple.editableInForm()).isTrue();

        UIMetadata.UIFieldMetadata full = UIMetadata.UIFieldMetadata.fullWidth("notes", UIMetadata.ComponentType.TEXT_AREA);
        assertThat(full.gridSpan()).isEqualTo(12);
        assertThat(full.componentType()).isEqualTo(UIMetadata.ComponentType.TEXT_AREA);
    }

    @Test
    void componentTypeEnumIsComplete() {
        // Touch values() so the synthetic enum methods get coverage too.
        // Adding a ComponentType is an additive change; the assertion below is
        // intentionally lower-bound to avoid coupling the test to enum size.
        assertThat(UIMetadata.ComponentType.values().length).isGreaterThanOrEqualTo(20);
        assertThat(UIMetadata.ComponentType.values()).contains(
                UIMetadata.ComponentType.AUTO,
                UIMetadata.ComponentType.TEXT_INPUT,
                UIMetadata.ComponentType.SELECT,
                UIMetadata.ComponentType.AUTOCOMPLETE,
                UIMetadata.ComponentType.HIDDEN);
        assertThat(UIMetadata.ComponentType.valueOf("AUTOCOMPLETE"))
                .isEqualTo(UIMetadata.ComponentType.AUTOCOMPLETE);
    }

    @Test
    void autocompleteForEntityFactory() {
        UIMetadata.AutocompleteConfig a = UIMetadata.AutocompleteConfig.forEntity("Customer", "name");
        assertThat(a.targetEntity()).isEqualTo("Customer");
        assertThat(a.displayField()).isEqualTo("name");
        assertThat(a.valueField()).isEqualTo("id");
        assertThat(a.minChars()).isEqualTo(2);
        assertThat(a.maxResults()).isEqualTo(20);
        assertThat(a.allowCreate()).isFalse();
        assertThat(a.searchEndpoint()).isNull();
        assertThat(a.createAction()).isNull();
    }

    @Test
    void selectConfigFromEnumAndFromEndpoint() {
        UIMetadata.SelectConfig fromEnum = UIMetadata.SelectConfig.fromEnum("OrderStatus");
        assertThat(fromEnum.optionsSource()).isEqualTo("enum:OrderStatus");
        assertThat(fromEnum.multiple()).isFalse();
        assertThat(fromEnum.clearable()).isTrue();
        assertThat(fromEnum.searchable()).isFalse();
        assertThat(fromEnum.staticOptions()).isNull();

        UIMetadata.SelectConfig fromEndpoint = UIMetadata.SelectConfig.fromEndpoint("/api/options");
        assertThat(fromEndpoint.optionsSource()).isEqualTo("api");
        assertThat(fromEndpoint.optionsEndpoint()).isEqualTo("/api/options");
        assertThat(fromEndpoint.searchable()).isTrue();
    }

    @Test
    void selectOptionSimpleFactory() {
        UIMetadata.SelectOption o = UIMetadata.SelectOption.simple("ACTIVE", "Active");
        assertThat(o.value()).isEqualTo("ACTIVE");
        assertThat(o.label()).isEqualTo("Active");
        assertThat(o.icon()).isNull();
        assertThat(o.group()).isNull();
        assertThat(o.disabled()).isFalse();
    }
}
