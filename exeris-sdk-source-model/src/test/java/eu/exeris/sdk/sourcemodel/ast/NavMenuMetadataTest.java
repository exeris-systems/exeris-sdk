package eu.exeris.sdk.sourcemodel.ast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("NavMenuMetadata: the @NavMenu carrier")
class NavMenuMetadataTest {

    @Test
    void aDeclaredEntryWithNothingFurtherAppliesTheAnnotationDefaults() {
        NavMenuMetadata entry = NavMenuMetadata.declared();
        assertThat(entry.group()).isNull();
        assertThat(entry.order()).isNull();
        assertThat(entry.roles()).isEmpty();
        assertThat(entry.permissions()).isEmpty();
        assertThat(entry.effectiveOrder()).isEqualTo(NavMenuMetadata.DEFAULT_ORDER).isEqualTo(999);
        assertThat(entry.effectiveGroupOrder()).isEqualTo(999);
        assertThat(entry.effectiveBadgeColor()).isEqualTo("accent");
        assertThat(entry.effectiveDivider()).isEqualTo(NavMenuMetadata.Divider.NONE);
    }

    @Test
    void theBuilderCarriesEveryComponent() {
        NavMenuMetadata entry = NavMenuMetadata.builder()
                .group("Sales").parent("Orders").label("Open").icon("cart")
                .order(0).groupOrder(1).groupIcon("store").hidden(true)
                .roles(List.of("SALES")).permissions(List.of("orders:read"))
                .badge("NEW").badgeColor("warn").tooltip("tip")
                .openInNewTab(true).routePath("/orders/open").queryParams("a=1&b=2")
                .expanded(true).divider(NavMenuMetadata.Divider.BEFORE)
                .build();
        assertThat(entry).isEqualTo(new NavMenuMetadata("Sales", "Orders", "Open", "cart", 0, 1,
                "store", true, List.of("SALES"), List.of("orders:read"), "NEW", "warn", "tip", true,
                "/orders/open", "a=1&b=2", true, NavMenuMetadata.Divider.BEFORE));
        // A declared zero is a position, not "undeclared".
        assertThat(entry.effectiveOrder()).isZero();
        assertThat(entry.effectiveGroupOrder()).isEqualTo(1);
        assertThat(entry.effectiveBadgeColor()).isEqualTo("warn");
        assertThat(entry.effectiveDivider()).isEqualTo(NavMenuMetadata.Divider.BEFORE);
    }

    @Test
    void aBlankStringIsStoredAsAbsent() {
        NavMenuMetadata entry = new NavMenuMetadata(" ", "", "", "", null, null, "", false, null, null,
                "", " ", "", false, "", "", false, null);
        assertThat(entry).isEqualTo(NavMenuMetadata.declared());
    }

    @Test
    void aNullRoleIsRefusedByName() {
        NavMenuMetadata.Builder withNullRole = NavMenuMetadata.builder().roles(java.util.Arrays.asList("A", null));
        assertThatThrownBy(withNullRole::build)
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("roles[1]");
    }
}
