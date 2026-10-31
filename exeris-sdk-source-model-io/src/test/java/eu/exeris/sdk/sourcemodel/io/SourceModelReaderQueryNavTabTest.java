package eu.exeris.sdk.sourcemodel.io;

import eu.exeris.sdk.sourcemodel.ast.ActionMetadata;
import eu.exeris.sdk.sourcemodel.ast.ActionParamMetadata;
import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.NavMenuMetadata;
import eu.exeris.sdk.sourcemodel.ast.ParamSource;
import eu.exeris.sdk.sourcemodel.ast.UIMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("reader: @QueryParam, @NavMenu and @Tab")
class SourceModelReaderQueryNavTabTest {

    private final SourceModelReader reader = new SourceModelReader();

    private DomainMetadata read(String source) {
        return reader.read(source).orElseThrow();
    }

    @Nested
    @DisplayName("@QueryParam binds an action parameter from the query string")
    class QueryParams {

        private static final String SEARCH = """
                package x;
                import eu.exeris.sdk.annotation.Action;
                import eu.exeris.sdk.annotation.ActionParam;
                import eu.exeris.sdk.annotation.ExerisDomain;
                import eu.exeris.sdk.annotation.QueryParam;

                @ExerisDomain
                public class Order {
                    @Action(name = "search", label = "Search")
                    public void search(
                            @QueryParam("q") String term,
                            @QueryParam(value = "limit", required = false, defaultValue = "10",
                                        description = "Page size") int limit,
                            @QueryParam("status") String status,
                            @ActionParam(label = "Note", required = false, description = "From the body") String note,
                            @ActionParam(label = "Cursor", description = "Where to resume")
                            @QueryParam(value = "after", required = false, description = "ignored",
                                        defaultValue = "start") String cursor,
                            @ActionParam(label = "Blank", description = "")
                            @QueryParam(value = "", description = "Query description") String blank,
                            String unannotated) {
                    }
                }
                """;

        private ActionParamMetadata param(String name) {
            ActionMetadata search = read(SEARCH).actions().getFirst();
            return search.params().stream()
                    .filter(p -> name.equals(p.name()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no param " + name + " in " + search.params()));
        }

        @Test
        void eitherAnnotationMakesAnActionParameterAndNeitherDoesNot() {
            List<String> names = read(SEARCH).actions().getFirst().params().stream()
                    .map(ActionParamMetadata::name).toList();
            assertThat(names).containsExactly("term", "limit", "status", "note", "cursor", "blank");
        }

        @Test
        void aQueryParamCarriesTheQuerySourceAndAKeyThatDiffersFromTheName() {
            ActionParamMetadata term = param("term");
            assertThat(term.bindingSource()).isEqualTo(ParamSource.QUERY);
            assertThat(term.queryKey()).isEqualTo("q");
            assertThat(term.type()).isEqualTo("java.lang.String");
            assertThat(term.required()).isTrue();
            assertThat(term.displayName()).isNull();
        }

        @Test
        void aKeyEqualToTheNameIsStoredAsAbsent() {
            ActionParamMetadata status = param("status");
            assertThat(status.bindingSource()).isEqualTo(ParamSource.QUERY);
            assertThat(status.queryKey()).isNull();
            assertThat(status.effectiveQueryKey()).isEqualTo("status");
        }

        @Test
        void aQueryParamAloneSuppliesRequiredDescriptionAndDefault() {
            ActionParamMetadata limit = param("limit");
            assertThat(limit.required()).isFalse();
            assertThat(limit.defaultValue()).isEqualTo("10");
            assertThat(limit.description()).isEqualTo("Page size");
            assertThat(limit.type()).isEqualTo("int");
        }

        @Test
        void anActionParamAloneIsABodyParameterAsBefore() {
            ActionParamMetadata note = param("note");
            assertThat(note.bindingSource()).isNull();
            assertThat(note.queryKey()).isNull();
            assertThat(note.effectiveBindingSource()).isEqualTo(ParamSource.BODY);
            assertThat(note.displayName()).isEqualTo("Note");
            assertThat(note.required()).isFalse();
        }

        @Test
        void onBothTheActionParamIsTheNearerDeclarationAndTheQueryParamTheFallback() {
            ActionParamMetadata cursor = param("cursor");
            assertThat(cursor.bindingSource()).isEqualTo(ParamSource.QUERY);
            assertThat(cursor.queryKey()).isEqualTo("after");
            assertThat(cursor.displayName()).isEqualTo("Cursor");
            // declared on @ActionParam: wins
            assertThat(cursor.description()).isEqualTo("Where to resume");
            // not declared on @ActionParam: @QueryParam supplies it
            assertThat(cursor.required()).isFalse();
            assertThat(cursor.defaultValue()).isEqualTo("start");
        }

        @Test
        void aBlankStringCountsAsUndeclared() {
            ActionParamMetadata blank = param("blank");
            assertThat(blank.description()).isEqualTo("Query description");
            assertThat(blank.queryKey()).isNull();
            assertThat(blank.bindingSource()).isEqualTo(ParamSource.QUERY);
        }

        @Test
        void aNonLiteralSingleMemberKeyIsLeftAbsent() {
            String source = """
                    package x;
                    import eu.exeris.sdk.annotation.Action;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.QueryParam;
                    @ExerisDomain
                    public class Order {
                        static final String KEY = "k";
                        @Action(name = "find", label = "Find")
                        public void find(@QueryParam(KEY) String term) {}
                    }
                    """;
            ActionParamMetadata term = read(source).actions().getFirst().params().getFirst();
            assertThat(term.bindingSource()).isEqualTo(ParamSource.QUERY);
            assertThat(term.queryKey()).isNull();
        }

        @Test
        void aQueryParamOutsideAnActionIsNotRead() {
            String source = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.QueryParam;
                    @ExerisDomain
                    public class Order {
                        public void find(@QueryParam("q") String term) {}
                    }
                    """;
            assertThat(read(source).actions()).isEmpty();
        }
    }

    @Nested
    @DisplayName("@NavMenu → DomainMetadata.navMenu")
    class NavMenu {

        @Test
        void everyAttributeIsReadPresentOnly() {
            String source = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.NavMenu;
                    @ExerisDomain
                    @NavMenu(group = "Sales", parent = "Orders", label = "Open orders",
                             icon = "shopping_cart", order = 0, groupOrder = -1, groupIcon = "store",
                             hidden = true, roles = {"SALES", "ADMIN"}, permissions = "orders:read",
                             badge = "NEW", badgeColor = "warn", tooltip = "Awaiting shipment",
                             openInNewTab = true, routePath = "/orders/open", queryParams = "status=OPEN",
                             expanded = true, divider = NavMenu.Divider.AFTER)
                    public class Order {}
                    """;
            NavMenuMetadata expected = NavMenuMetadata.builder()
                    .group("Sales").parent("Orders").label("Open orders").icon("shopping_cart")
                    .order(0).groupOrder(-1).groupIcon("store").hidden(true)
                    .roles(List.of("SALES", "ADMIN")).permissions(List.of("orders:read"))
                    .badge("NEW").badgeColor("warn").tooltip("Awaiting shipment")
                    .openInNewTab(true).routePath("/orders/open").queryParams("status=OPEN")
                    .expanded(true).divider(NavMenuMetadata.Divider.AFTER)
                    .build();
            assertThat(read(source).navMenu()).isEqualTo(expected);
        }

        @Test
        void aMarkerIsAnEntryThatDeclaresNothingFurther() {
            String source = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.NavMenu;
                    @ExerisDomain
                    @NavMenu
                    public class AuditLog {}
                    """;
            NavMenuMetadata entry = read(source).navMenu();
            assertThat(entry).isEqualTo(NavMenuMetadata.declared());
            assertThat(entry.effectiveOrder()).isEqualTo(999);
            assertThat(entry.effectiveBadgeColor()).isEqualTo("accent");
            assertThat(entry.effectiveDivider()).isEqualTo(NavMenuMetadata.Divider.NONE);
        }

        @Test
        void absentWhenNotDeclared() {
            String source = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    @ExerisDomain
                    public class Order {}
                    """;
            assertThat(read(source).navMenu()).isNull();
        }

        @Test
        void anUnknownDividerConstantIsLeftUnset() {
            String source = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.NavMenu;
                    @ExerisDomain
                    @NavMenu(divider = NavMenu.Divider.SOMEWHERE, label = "")
                    public class Order {}
                    """;
            NavMenuMetadata entry = read(source).navMenu();
            assertThat(entry.divider()).isNull();
            assertThat(entry.label()).isNull();
        }
    }

    @Nested
    @DisplayName("@Tab → UIMetadata.tabs")
    class Tabs {

        private static final String EMPLOYEE = """
                package x;
                import eu.exeris.sdk.annotation.ExerisDomain;
                import eu.exeris.sdk.annotation.Field;
                import eu.exeris.sdk.annotation.Relationship;
                import eu.exeris.sdk.annotation.Tab;
                import java.math.BigDecimal;
                import java.util.List;

                @ExerisDomain
                public class Employee {
                    @Field(label = "First Name")
                    @Tab(name = "basic")
                    private String firstName;

                    @Field(label = "Last Name")
                    @Tab(name = "basic", label = "Basic Info", order = 1, lazy = false)
                    private String lastName;

                    @Tab(name = "compensation", label = "Compensation", order = 0, icon = "attach_money",
                         badgeField = "bonusCount", badgeColor = "warn", disabled = true,
                         disabledWhen = "status == 'DRAFT'", visibleWhen = "hasRole('HR')",
                         cssClass = "tab-pay", lazy = true)
                    private BigDecimal salary, bonus;

                    @Tab(name = "basic", label = "Ignored", order = 7, lazy = true)
                    private String nickname;

                    @Relationship(targetEntity = Note.class, displayField = "text")
                    @Tab(name = "notes")
                    private List<Note> notes;

                    @Tab(name = " ")
                    private String unnamed;

                    @Field(label = "Plain")
                    private String plain;
                }
                """;

        @Test
        void oneTabPerNameInFirstDeclaredOrderWithItsMembers() {
            List<UIMetadata.UITabMetadata> tabs = read(EMPLOYEE).uiMetadata().tabs();
            assertThat(tabs).extracting(UIMetadata.UITabMetadata::name)
                    .containsExactly("basic", "compensation", "notes");
            assertThat(tabs.get(0).fields()).containsExactly("firstName", "lastName", "nickname");
            assertThat(tabs.get(1).fields()).containsExactly("salary", "bonus");
            // a relationship field is a member like any other
            assertThat(tabs.get(2).fields()).containsExactly("notes");
        }

        @Test
        void eachAttributeComesFromTheFirstMemberThatDeclaresIt() {
            UIMetadata.UITabMetadata basic = read(EMPLOYEE).uiMetadata().tabs().getFirst();
            // firstName declares nothing; lastName declares the attributes; nickname restates them
            assertThat(basic.label()).isEqualTo("Basic Info");
            assertThat(basic.order()).isEqualTo(1);
            // lastName's explicit false is a declaration, so nickname's true does not replace it
            assertThat(basic.lazy()).isFalse();
            assertThat(basic.icon()).isNull();
            assertThat(basic.effectiveBadgeColor()).isEqualTo("primary");
        }

        @Test
        void everyTabAttributeIsCarried() {
            UIMetadata.UITabMetadata pay = read(EMPLOYEE).uiMetadata().tabs().get(1);
            assertThat(pay).isEqualTo(new UIMetadata.UITabMetadata("compensation", "Compensation", 0,
                    "attach_money", true, "bonusCount", "warn", true, "status == 'DRAFT'",
                    "hasRole('HR')", "tab-pay", List.of("salary", "bonus")));
        }

        @Test
        void tabsWithoutUiYieldUiMetadataAtItsDefaults() {
            UIMetadata ui = read(EMPLOYEE).uiMetadata();
            UIMetadata withoutTabs = UIMetadata.builder().build();
            assertThat(ui.listView()).isEqualTo(withoutTabs.listView());
            assertThat(ui.exportable()).isEqualTo(withoutTabs.exportable());
            assertThat(new UIMetadata(ui.icon(), ui.color(), ui.listView(), ui.detailView(),
                    ui.createForm(), ui.editForm(), ui.searchable(), ui.filterable(), ui.exportable(),
                    ui.bulkActions(), ui.columns(), ui.defaultLayout(), ui.groups(), ui.fieldOverrides()))
                    .isEqualTo(withoutTabs);
        }

        @Test
        void tabsAndUiTogether() {
            String source = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.Tab;
                    import eu.exeris.sdk.annotation.UI;
                    @ExerisDomain
                    @UI(exportable = true)
                    public class Order {
                        @Tab(name = "main") private String ref;
                    }
                    """;
            UIMetadata ui = read(source).uiMetadata();
            assertThat(ui.exportable()).isTrue();
            assertThat(ui.tabs()).containsExactly(UIMetadata.UITabMetadata.of("main", List.of("ref")));
        }

        @Test
        void noTabsLeavesTheUiMetadataAsBefore() {
            String source = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.UI;
                    @ExerisDomain
                    @UI
                    public class Order {
                        private String ref;
                    }
                    """;
            assertThat(read(source).uiMetadata().tabs()).isNull();
        }
    }

    @Test
    @DisplayName("the three are not reported by unmodeledFacets")
    void notReportedAsUnmodeled() {
        String source = """
                package x;
                import eu.exeris.sdk.annotation.Action;
                import eu.exeris.sdk.annotation.ExerisDomain;
                import eu.exeris.sdk.annotation.NavMenu;
                import eu.exeris.sdk.annotation.QueryParam;
                import eu.exeris.sdk.annotation.Tab;
                @ExerisDomain
                @NavMenu(group = "Sales")
                public class Order {
                    @Tab(name = "main") private String ref;
                    @Action(name = "find", label = "Find")
                    public void find(@QueryParam("q") String term) {}
                }
                """;
        assertThat(reader.unmodeledFacets(source)).isEmpty();
    }
}
