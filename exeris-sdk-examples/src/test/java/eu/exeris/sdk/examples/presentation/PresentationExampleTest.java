/*
 * Copyright (C) 2026 Exeris Systems.
 * SPDX-License-Identifier: Apache-2.0
 */
package eu.exeris.sdk.examples.presentation;

import eu.exeris.sdk.sourcemodel.ast.BindSource;
import eu.exeris.sdk.sourcemodel.ast.BindingMetadata;
import eu.exeris.sdk.sourcemodel.ast.BlockType;
import eu.exeris.sdk.sourcemodel.ast.ComponentNodeMetadata;
import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.NavMenuMetadata;
import eu.exeris.sdk.sourcemodel.ast.RegionMetadata;
import eu.exeris.sdk.sourcemodel.ast.UIMetadata;
import eu.exeris.sdk.sourcemodel.ast.ViewKind;
import eu.exeris.sdk.sourcemodel.ast.ViewMetadata;
import eu.exeris.sdk.sourcemodel.io.SourceModelReader;
import eu.exeris.sdk.tck.TckMappers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reads the presentation example sources exactly as the documentation quotes them. The entity
 * facets ({@code @UI}, {@code @UIGroup}, {@code @Tab}, {@code @NavMenu}) are asserted as read. The
 * source model reader does not read {@code @View}, so for the page the tests assert that the read
 * is empty and that the declared structure, written as the AST the build-time processor produces,
 * is a valid {@code ViewMetadata}.
 */
@DisplayName("examples: presentation")
class PresentationExampleTest {

    private static final Path SOURCES = Path.of("src/main/java/eu/exeris/sdk/examples/presentation");

    private final SourceModelReader reader = new SourceModelReader();

    private String source(String type) throws IOException {
        return Files.readString(SOURCES.resolve(type + ".java"));
    }

    private DomainMetadata employee() throws IOException {
        return reader.read(source("Employee")).orElseThrow();
    }

    @Test
    @DisplayName("@UI turns the views on by default and sets the flags it declares")
    void entityLevelUi() throws IOException {
        UIMetadata ui = employee().uiMetadata();

        // A type carrying @UI has its views on unless it says otherwise.
        assertThat(ui.listView()).isTrue();
        assertThat(ui.detailView()).isTrue();
        assertThat(ui.createForm()).isTrue();
        assertThat(ui.editForm()).isTrue();
        assertThat(ui.searchable()).isTrue();
        assertThat(ui.filterable()).isFalse();
        assertThat(ui.exportable()).isTrue();
    }

    @Test
    @DisplayName("@UIGroup collects member fields into sections")
    void groups() throws IOException {
        List<UIMetadata.UIGroupMetadata> groups = employee().uiMetadata().groups();

        assertThat(groups).extracting(UIMetadata.UIGroupMetadata::name)
                .containsExactly("identity", "contact", "employment");

        UIMetadata.UIGroupMetadata identity = groups.getFirst();
        assertThat(identity.label()).isEqualTo("Identity");
        assertThat(identity.order()).isEqualTo(1);
        assertThat(identity.icon()).isEqualTo("person");
        assertThat(identity.fields()).containsExactly("firstName", "lastName");

        UIMetadata.UIGroupMetadata contact = groups.get(1);
        assertThat(contact.collapsed()).isTrue();
        assertThat(contact.fields()).containsExactly("email");

        assertThat(groups.get(2).fields()).containsExactly("hiredOn", "position");
    }

    @Test
    @DisplayName("@Tab collects member fields into tabs; attributes come from the first member")
    void tabs() throws IOException {
        List<UIMetadata.UITabMetadata> tabs = employee().uiMetadata().tabs();

        assertThat(tabs).extracting(UIMetadata.UITabMetadata::name).containsExactly("profile", "employment");

        UIMetadata.UITabMetadata profile = tabs.getFirst();
        assertThat(profile.label()).isEqualTo("Profile");
        assertThat(profile.effectiveOrder()).isEqualTo(1);
        assertThat(profile.lazy()).isFalse();
        assertThat(profile.fields()).containsExactly("firstName", "lastName", "email");

        UIMetadata.UITabMetadata employment = tabs.get(1);
        assertThat(employment.effectiveOrder()).isEqualTo(2);
        assertThat(employment.lazy()).isTrue();
        assertThat(employment.effectiveBadgeColor()).isEqualTo("primary");
        assertThat(employment.fields()).containsExactly("hiredOn", "position");
    }

    @Test
    @DisplayName("@NavMenu places the entity in the navigation menu")
    void navMenu() throws IOException {
        NavMenuMetadata nav = employee().navMenu();

        assertThat(nav.group()).isEqualTo("People");
        assertThat(nav.label()).isEqualTo("Employees");
        assertThat(nav.icon()).isEqualTo("badge");
        assertThat(nav.effectiveOrder()).isEqualTo(10);
        assertThat(nav.effectiveGroupOrder()).isEqualTo(20);
        assertThat(nav.hidden()).isFalse();
        assertThat(nav.effectiveDivider()).isEqualTo(NavMenuMetadata.Divider.NONE);
    }

    @Test
    @DisplayName("@View is not read: the page is not an entity")
    void viewIsNotRead() throws IOException {
        String page = source("EmployeeDirectory");

        assertThat(reader.read(page)).isEmpty();
        assertThat(reader.unmodeledFacets(page)).isEmpty();
    }

    @Test
    @DisplayName("the declared page structure is a ViewMetadata of two regions and three blocks")
    void viewStructure() throws IOException {
        ViewMetadata view = ViewMetadata.builder("EmployeeDirectory")
                .kind(ViewKind.PAGE)
                .route("/people")
                .title("Employees")
                .regions(List.of(
                        new RegionMetadata("header", List.of(
                                ComponentNodeMetadata.leaf(BlockType.HERO, BindingMetadata.staticBinding()))),
                        new RegionMetadata("body", List.of(
                                ComponentNodeMetadata.leaf(BlockType.LIST,
                                        BindingMetadata.entity("Employee", "lastName")),
                                ComponentNodeMetadata.leaf(BlockType.CARD, BindingMetadata.action("invite"))))))
                .build();

        assertThat(view.effectiveKind()).isEqualTo(ViewKind.PAGE);
        assertThat(view.regions()).extracting(RegionMetadata::slot).containsExactly("header", "body");
        RegionMetadata body = view.regions().get(1);
        assertThat(body.components()).extracting(ComponentNodeMetadata::effectiveType)
                .containsExactly(BlockType.LIST, BlockType.CARD);
        assertThat(body.components().getFirst().binding().effectiveSource()).isEqualTo(BindSource.ENTITY);
        assertThat(body.components().get(1).binding().ref()).isEqualTo("invite");

        // The structure above is the one the source declares.
        String page = source("EmployeeDirectory");
        assertThat(page).contains(
                "@View(name = \"EmployeeDirectory\", kind = View.Kind.PAGE, route = \"/people\", title = \"Employees\")",
                "@Region(slot = \"header\")",
                "Block.BlockType.HERO", "Block.BlockType.LIST", "Block.BlockType.CARD");

        ObjectMapper mapper = TckMappers.canonical();
        assertThat(mapper.readValue(mapper.writeValueAsString(view), ViewMetadata.class)).isEqualTo(view);
    }

    @Test
    @DisplayName("the entity metadata survives a JSON round trip")
    void jsonRoundTrip() throws IOException {
        ObjectMapper mapper = TckMappers.canonical();
        DomainMetadata employee = employee();

        String json = mapper.writeValueAsString(employee);

        assertThat(mapper.readValue(json, DomainMetadata.class)).isEqualTo(employee);
    }
}
