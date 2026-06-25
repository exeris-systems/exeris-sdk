package eu.exeris.sdk.sourcemodel.ast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused builder / factory / convenience-method coverage for the reserved
 * presentation IR records ({@link ViewMetadata}, {@link RegionMetadata},
 * {@link ComponentNodeMetadata}, {@link BindingMetadata}). Wire-format fidelity
 * is covered separately in {@code AstJsonRoundTripTest}.
 */
@DisplayName("Presentation IR records: builder, factories, effective defaults, normalization")
class ViewMetadataTest {

    // ── BindingMetadata ────────────────────────────────────────────────────

    @Test
    @DisplayName("BindingMetadata factories carry the right source")
    void bindingFactories() {
        assertThat(BindingMetadata.none().source()).isEqualTo(BindSource.NONE);
        assertThat(BindingMetadata.entity("Order", "amount").source()).isEqualTo(BindSource.ENTITY);
        assertThat(BindingMetadata.entity("Order", "amount").ref()).isEqualTo("Order");
        assertThat(BindingMetadata.entity("Order", "amount").path()).isEqualTo("amount");
        assertThat(BindingMetadata.projection("OrderSummary", "total").source()).isEqualTo(BindSource.PROJECTION);
        assertThat(BindingMetadata.action("placeOrder").source()).isEqualTo(BindSource.ACTION);
        assertThat(BindingMetadata.action("placeOrder").ref()).isEqualTo("placeOrder");
        assertThat(BindingMetadata.staticBinding().source()).isEqualTo(BindSource.STATIC);
    }

    @Test
    @DisplayName("BindingMetadata effectiveSource() defaults null -> NONE")
    void bindingEffectiveSource() {
        assertThat(new BindingMetadata(null, null, null, null, null).effectiveSource())
                .isEqualTo(BindSource.NONE);
        assertThat(BindingMetadata.entity("Order", "amount").effectiveSource())
                .isEqualTo(BindSource.ENTITY);
    }

    @Test
    @DisplayName("BindingMetadata normalizes blank strings to null")
    void bindingBlankNormalization() {
        BindingMetadata b = new BindingMetadata(BindSource.STATIC, "  ", "", "\t", "  ");
        assertThat(b.ref()).isNull();
        assertThat(b.path()).isNull();
        assertThat(b.expression()).isNull();
        assertThat(b.language()).isNull();
    }

    // ── ComponentNodeMetadata ──────────────────────────────────────────────

    @Test
    @DisplayName("ComponentNodeMetadata.leaf has a binding and no children")
    void componentLeaf() {
        ComponentNodeMetadata leaf = ComponentNodeMetadata.leaf(BlockType.RICH_TEXT, BindingMetadata.staticBinding());
        assertThat(leaf.type()).isEqualTo(BlockType.RICH_TEXT);
        assertThat(leaf.binding().source()).isEqualTo(BindSource.STATIC);
        assertThat(leaf.hasChildren()).isFalse();
        assertThat(leaf.children()).isEmpty();
    }

    @Test
    @DisplayName("ComponentNodeMetadata.container holds children")
    void componentContainer() {
        ComponentNodeMetadata container = ComponentNodeMetadata.container(
                BlockType.GRID,
                List.of(ComponentNodeMetadata.leaf(BlockType.CARD, BindingMetadata.none())));
        assertThat(container.type()).isEqualTo(BlockType.GRID);
        assertThat(container.binding()).isNull();
        assertThat(container.hasChildren()).isTrue();
        assertThat(container.children()).hasSize(1);
    }

    @Test
    @DisplayName("ComponentNodeMetadata effectiveType() defaults null -> CONTAINER")
    void componentEffectiveType() {
        assertThat(new ComponentNodeMetadata(null, null, null, null, null, null).effectiveType())
                .isEqualTo(BlockType.CONTAINER);
        assertThat(ComponentNodeMetadata.leaf(BlockType.HERO, BindingMetadata.none()).effectiveType())
                .isEqualTo(BlockType.HERO);
    }

    @Test
    @DisplayName("ComponentNodeMetadata normalizes blank props/customType and null children")
    void componentNormalization() {
        ComponentNodeMetadata node = new ComponentNodeMetadata(BlockType.CUSTOM, "  ", null, " ", null, null);
        assertThat(node.customType()).isNull();
        assertThat(node.props()).isNull();
        assertThat(node.children()).isNotNull().isEmpty();
        assertThat(node.hasChildren()).isFalse();
        assertThat(node.hasField()).isFalse();
    }

    @Test
    @DisplayName("ComponentNodeMetadata.fieldLeaf carries the @UI-successor render facet")
    void componentFieldFacet() {
        UIMetadata.UIFieldMetadata render = UIMetadata.UIFieldMetadata.simple("price", UIMetadata.ComponentType.NUMBER_INPUT);
        ComponentNodeMetadata node = ComponentNodeMetadata.fieldLeaf(
                BlockType.FORM, BindingMetadata.entity("Product", "price"), render);
        assertThat(node.hasField()).isTrue();
        assertThat(node.field()).isEqualTo(render);
        assertThat(node.field().componentType()).isEqualTo(UIMetadata.ComponentType.NUMBER_INPUT);
        assertThat(node.hasChildren()).isFalse();
    }

    // ── RegionMetadata ─────────────────────────────────────────────────────

    @Test
    @DisplayName("RegionMetadata normalizes blank slot and null components; hasComponents()")
    void regionNormalization() {
        RegionMetadata empty = new RegionMetadata("  ", null);
        assertThat(empty.slot()).isNull();
        assertThat(empty.components()).isNotNull().isEmpty();
        assertThat(empty.hasComponents()).isFalse();

        RegionMetadata filled = new RegionMetadata("main",
                List.of(ComponentNodeMetadata.leaf(BlockType.HERO, BindingMetadata.none())));
        assertThat(filled.slot()).isEqualTo("main");
        assertThat(filled.hasComponents()).isTrue();
    }

    // ── ViewMetadata ───────────────────────────────────────────────

    @Test
    @DisplayName("ViewMetadata.of carries name + kind, no regions")
    void presentationOf() {
        ViewMetadata p = ViewMetadata.of("Landing", ViewKind.PAGE);
        assertThat(p.name()).isEqualTo("Landing");
        assertThat(p.kind()).isEqualTo(ViewKind.PAGE);
        assertThat(p.hasRegions()).isFalse();
        assertThat(p.regions()).isEmpty();
    }

    @Test
    @DisplayName("ViewMetadata builder sets every field")
    void presentationBuilder() {
        ViewMetadata p = ViewMetadata.builder("ProductPage")
                .kind(ViewKind.PAGE)
                .route("/products")
                .title("Products")
                .titleKey("products.title")
                .layout("default-shell")
                .regions(List.of(new RegionMetadata("main",
                        List.of(ComponentNodeMetadata.leaf(BlockType.LIST,
                                BindingMetadata.projection("ProductSummary", "items"))))))
                .build();

        assertThat(p.route()).isEqualTo("/products");
        assertThat(p.title()).isEqualTo("Products");
        assertThat(p.titleKey()).isEqualTo("products.title");
        assertThat(p.layout()).isEqualTo("default-shell");
        assertThat(p.hasRegions()).isTrue();
        assertThat(p.regions()).hasSize(1);
    }

    @Test
    @DisplayName("ViewMetadata effectiveKind() defaults null -> PAGE")
    void presentationEffectiveKind() {
        assertThat(ViewMetadata.builder("X").build().effectiveKind()).isEqualTo(ViewKind.PAGE);
        assertThat(ViewMetadata.of("X", ViewKind.FRAGMENT).effectiveKind()).isEqualTo(ViewKind.FRAGMENT);
    }

    @Test
    @DisplayName("ViewMetadata normalizes blank optional strings and null regions")
    void presentationNormalization() {
        ViewMetadata p = new ViewMetadata("X", null, "  ", "", "\t", " ", null);
        assertThat(p.route()).isNull();
        assertThat(p.title()).isNull();
        assertThat(p.titleKey()).isNull();
        assertThat(p.layout()).isNull();
        assertThat(p.regions()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("ViewMetadata rejects null / blank name")
    void presentationNameRequired() {
        assertThatThrownBy(() -> ViewMetadata.of(null, ViewKind.PAGE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ViewMetadata.of("  ", ViewKind.PAGE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
