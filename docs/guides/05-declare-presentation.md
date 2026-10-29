---
title: Declare presentation
type: howto
visibility: public
owning-repo: exeris-sdk
status: active
last-verified: 2026-10-29
---

# Declare presentation

**Audience:** you are writing an entity and want to say how its screens are grouped, where it sits in the navigation menu, and how a page composes regions and blocks.

**Goal:** declare entity-level presentation with `@UI`, `@UIGroup`, `@Tab` and `@NavMenu`, declare a page with `@View`, and check what the SDK reads back.

> **Verified against** `0.13.0-SNAPSHOT` at commit `0753b7d0`, 2026-10-29. Every snippet is quoted or adapted from the cited file and symbol, never from a line range. If a snippet and its source disagree, **the source wins and this guide is the bug**. The sources compile and run in `exeris-sdk-examples`, so a removed annotation attribute fails that build first.

## Prerequisites

- `exeris-sdk-annotations` on the compile classpath of the module that holds your entities. The annotations are `@Retention(SOURCE)`, so nothing reaches your runtime. See [annotations](../modules/01-annotations.md).
- To read the declarations back: `exeris-sdk-source-model-io`. See [source model IO](../modules/03-source-model-io.md).
- The tiers of the presentation annotations are in the [stability matrix](../stability-matrix.md). Read them before you rely on one.

## Steps

### 1. Place the entity in the menu and set its views

`@UI` on the entity type turns the standard views on and sets the flags you name. `@NavMenu` places the entity in the navigation menu.

Source: `exeris-sdk-examples/src/main/java/eu/exeris/sdk/examples/presentation/Employee.java`, `Employee` (adapted, imports, javadoc and fields removed)

```java
@ExerisDomain(module = "people", path = "/employees", description = "A person employed by the company")
@UI(filterable = false, exportable = true)
@NavMenu(group = "People", label = "Employees", icon = "badge", order = 10, groupOrder = 20)
public class Employee {
    // ...
}
```

### 2. Group fields into sections and tabs

`@UIGroup` and `@Tab` go on fields. Fields that name the same group or tab are collected under it. A tab takes its attributes from its first member.

Source: `exeris-sdk-examples/src/main/java/eu/exeris/sdk/examples/presentation/Employee.java`, `firstName` (quoted)

```java
    @Field(label = "First name", required = true)
    @UIGroup(name = "identity", label = "Identity", order = 1, icon = "person")
    @Tab(name = "profile", label = "Profile", order = 1)
    private String firstName;
```

A later member names the group again and omits the rest. `lastName` joins `identity` and `profile` this way.

### 3. Declare a page

`@View` marks a class as a page. Each `@Region` member is a slot, and each `@Block` member inside it is a component. `@Bind` ties a block to a data source by name. A blank `@Region` slot is named after the member.

Source: `exeris-sdk-examples/src/main/java/eu/exeris/sdk/examples/presentation/EmployeeDirectory.java`, `EmployeeDirectory` (adapted, imports, javadoc and the header region removed)

```java
@View(name = "EmployeeDirectory", kind = View.Kind.PAGE, route = "/people", title = "Employees")
public final class EmployeeDirectory {

    @Region
    BodyRegion body;

    static final class BodyRegion {

        @Block(type = Block.BlockType.LIST)
        @Bind(source = Bind.Source.ENTITY, ref = "Employee", path = "lastName")
        String employees;

        @Block(type = Block.BlockType.CARD)
        @Bind(source = Bind.Source.ACTION, ref = "invite")
        String inviteCard;
    }
}
```

A page is not an entity and carries no `@ExerisDomain`.

### 4. Read the entity facets back

The reader carries the entity-level facets. A type with `@UI` has every view flag on unless it says otherwise.

Source: `exeris-sdk-examples/src/test/java/eu/exeris/sdk/examples/presentation/PresentationExampleTest.java`, `entityLevelUi` (quoted)

```java
        UIMetadata ui = employee().uiMetadata();

        // A type carrying @UI has its views on unless it says otherwise.
        assertThat(ui.listView()).isTrue();
        assertThat(ui.detailView()).isTrue();
        assertThat(ui.createForm()).isTrue();
        assertThat(ui.editForm()).isTrue();
        assertThat(ui.searchable()).isTrue();
        assertThat(ui.filterable()).isFalse();
        assertThat(ui.exportable()).isTrue();
```

The same test class asserts the group members, the tab order and the menu placement. The `icon`, `color` and `label` attributes of entity-level `@UI` are not read.

### 5. Know that the page is not read back

`reader.read` returns empty for `EmployeeDirectory`, and `DomainMetadata` has no carrier for a view.

Source: `exeris-sdk-examples/src/test/java/eu/exeris/sdk/examples/presentation/PresentationExampleTest.java`, `viewIsNotRead` (quoted)

```java
        String page = source("EmployeeDirectory");

        assertThat(reader.read(page)).isEmpty();
        assertThat(reader.unmodeledFacets(page)).isEmpty();
```

An empty `unmodeledFacets` here does not mean the page is modeled. The guard only arms for a source with an `@ExerisDomain` or `@CapabilityModule` type, and a page has neither. See [source model IO](../modules/03-source-model-io.md).

## Verify

```bash
mvn -pl exeris-sdk-examples -am verify -Dtest=PresentationExampleTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: `BUILD SUCCESS` and seven passing tests in `PresentationExampleTest`.

For the page, the test builds a `ViewMetadata` tree by hand from `RegionMetadata`, `ComponentNodeMetadata` and `BindingMetadata`. It round-trips the tree through JSON and checks the source text for the declarations. That proves the AST records and the source text. It does not prove what the build-time processor emits for the page.

## Not available today

- **Reading `@View`, `@Region`, `@Block` and `@Bind` from source.** The source model reader does not extract them. A tool that edits or inspects pages through `SourceModelReader` sees nothing.
- **Processor output for a page, checked in this repository.** The processor lives in `exeris-tooling`. This repository holds the records and the declarations, not the emitted metadata.
- **Entity-level facets in generated output.** The tier of each facet is in the [stability matrix](../stability-matrix.md). A facet the reader carries and the processor does not extract yet is declared, not enforced.

## Trade-off

The entity side is verified by reading real source, and the page side is verified by writing the expected tree out by hand. The second check can pass while the processor emits something else, because both halves of it are written by the same author.

## Where this does not apply

- **Rendering.** These annotations declare structure. Styling is in [style generated UI](09-style-generated-ui.md), and layout code generation belongs to `exeris-tooling`.
- **Behaviour.** Nothing here declares actions or rules. A page binds to an action by name only.
- **Design-time editing of pages.** The mutation surface does not edit presentation. See [read and write source](06-read-and-write-source.md).
