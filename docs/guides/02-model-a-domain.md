---
title: Model a domain
type: tutorial
visibility: public
owning-repo: exeris-sdk
status: active
last-verified: 2026-10-29
---

# Model a domain

**Audience:** you are writing your first Exeris entity and want to see each annotation do something you can check.

> **Verified against** `0.13.0-SNAPSHOT` at commit `33574011`, 2026-10-29, on `main`. Every snippet is quoted or minimally adapted from the cited class, by path and symbol. If a snippet and its source disagree, **the source wins and this guide is the defect**.

## Prerequisites

- [01 Platform and dependencies](01-platform-and-dependencies.md) done: JDK 25, and the annotations and `exeris-sdk-source-model-io` on your classpath.
- A clone of this repository, because the example and its test live in `exeris-sdk-examples`.
- The `invoicing` example, which you read and build here: `Customer`, `Invoice` and `InvoiceLine` under `exeris-sdk-examples/src/main/java/eu/exeris/sdk/examples/invoicing/`.

## What you are building

You are writing three plain Java classes that describe a billing domain. The annotations are `@Retention(SOURCE)`, so they leave no trace in the class files. What you check at the end is the metadata that `SourceModelReader` reads out of the source text. The contract of that metadata is in [Source model](../modules/02-source-model.md).

## Steps

### 1. Declare the entity and its system fields

`@ExerisDomain` marks a class as an entity and sets its module, its path and its data scope. System fields are named by marker annotations, not by convention: the reader records which field is the key, the tenant, the version and the creation time.

Source: `exeris-sdk-examples/src/main/java/eu/exeris/sdk/examples/invoicing/Customer.java`, the `Customer` class (adapted — imports, Javadoc and the `email` field removed).

```java
@ExerisDomain(
        module = "invoicing",
        path = "/customers",
        description = "A party that is billed",
        dataScope = ExerisDomain.DataScope.TENANT)
public class Customer {

    @PrimaryKey
    private UUID id;

    @TenantId
    private UUID tenantId;

    @Version
    private long version;

    @AuditCreatedAt
    private Instant createdAt;

    @Field(label = "Name", required = true, searchable = true)
    @Validation(minLength = 2, maxLength = 120)
    private String name;

    @Field(label = "Tax id", inUpdate = false)
    @Validation(pattern = "[A-Z0-9]{8,14}")
    private String taxId;
}
```

`@PrimaryKey` is PARTIAL: the reader carries the marker and the build-time processor does not read it yet. The status words on this page (LIVE, PARTIAL, RESERVED) say how far the toolchain consumes an annotation. They are not the 1.0 freeze tier, and the [stability matrix](../stability-matrix.md) lists both side by side.

### 2. Split `@Field` from `@Validation`

`@Field` says how an attribute behaves in the entity lifecycle: `required`, `inCreate`, `inUpdate`, `unique`, `readOnly`. `@Validation` is where a constraint is declared: `min`, `max`, `minLength`, `maxLength`, `pattern`. Write both as siblings on the field, as above. The reader consults `@Validation` only on a field that also carries `@Field`.

The scoping rule has one home, [field and validation scoping](../../.agents/policies/field-validation-scoping.md). Two consequences show up in the metadata:

- `name` is `required` and accepts create and update, with length bounds 2 to 120.
- `taxId` is declared as accepted on create and not on update. That sentence describes the declaration only. `inUpdate` is PARTIAL, so no generator filters the update payload by it yet.

### 3. Relate entities

A `@Relationship` names the target entity and the cardinality. The one-to-many side names the field on the other side in `mappedBy`. The reader takes the target from the declared type of the field, unwrapping a collection, and not from `targetEntity`.

Source: `exeris-sdk-examples/src/main/java/eu/exeris/sdk/examples/invoicing/Invoice.java`, the `customer` and `lines` fields (quoted).

```java
@Relationship(
        targetEntity = Customer.class,
        displayField = "name",
        relationshipType = Relationship.RelationshipType.MANY_TO_ONE)
private Customer customer;

@Relationship(
        targetEntity = InvoiceLine.class,
        displayField = "description",
        relationshipType = Relationship.RelationshipType.ONE_TO_MANY,
        mappedBy = "invoice",
        cascadeDelete = true)
private List<InvoiceLine> lines;
```

`InvoiceLine` carries the inverse `MANY_TO_ONE` back to `Invoice`. The reader preserves declaration order, so `customer` precedes `lines` in the result.

### 4. Add an action and a query parameter

An action is a method marked `@Action`. `@ActionParam` carries `required` and `defaultValue` for a parameter. `@QueryParam` binds a parameter to the query string under a key.

Source: `exeris-sdk-examples/src/main/java/eu/exeris/sdk/examples/invoicing/Invoice.java`, the `issue` and `viewShared` methods (adapted — Javadoc removed).

```java
@Action(name = "issue", label = "Issue", description = "Issue a draft invoice")
public void issue(
        @ActionParam(label = "Due in days", required = false, defaultValue = "30") int dueInDays) {
}

@Action(name = "viewShared", label = "View shared invoice", httpMethod = "GET")
@RouteAccess(RouteAccess.Level.PUBLIC)
public Invoice viewShared(
        @QueryParam(value = "token", description = "Payment link token") String shareToken) {
    return this;
}
```

The query key here is `token` while the parameter is `shareToken`. The metadata stores a key only when it differs from the parameter name, so the example uses two names to make the key visible.

`@QueryParam` is PARTIAL: the reader carries it and a generator does not use it yet.

### 5. Mark one route public

`@RouteAccess(PUBLIC)` on `viewShared` declares that the route needs no authenticated caller, because the token in the link is the credential. `issue` leaves `routeAccess` undeclared, so the reader returns `null` for it.

`@RouteAccess` is RESERVED, and its 1.0 tier is frozen as declared. The SDK carries the declaration into the metadata. A route policy is emitted from it only by a tooling release that reads it. The rule for declaring access is in [route access](../../.agents/policies/route-access.md).

### 6. Read the model back

Reading is how you prove the declarations say what you meant. `SourceModelReader.read` takes source text and returns the first `@ExerisDomain` type as `DomainMetadata`, or an empty `Optional`.

Source: `exeris-sdk-examples/src/test/java/eu/exeris/sdk/examples/invoicing/InvoicingExampleTest.java`, the `reader` field, the `read` helper and the `lifecycleAndValidation` test (adapted — only the `Customer.name` assertions kept, and the `SOURCES` path constant and the private `field` helper that looks a field up by name are not shown).

```java
private final SourceModelReader reader = new SourceModelReader();

private DomainMetadata read(String entity) throws IOException {
    return reader.read(Files.readString(SOURCES.resolve(entity + ".java"))).orElseThrow();
}

DomainMetadata customer = read("Customer");

FieldMetadata name = field(customer, "name");
assertThat(name.required()).isTrue();
assertThat(name.inCreate()).isTrue();
assertThat(name.inUpdate()).isTrue();
assertThat(name.minLength()).isEqualTo(2);
assertThat(name.maxLength()).isEqualTo(120);
```

The same test class asserts the relationships, the actions and the public route, and checks that each `DomainMetadata` survives a JSON round trip through the canonical mapper. Run it from the repository root:

```bash
mvn -pl exeris-sdk-examples -am verify
```

Expect `BUILD SUCCESS`. Change `minLength = 2` to another value in `Customer.java` and the test fails, which is the check this tutorial rests on.

## Not available today

The reader does not carry every attribute the annotations accept. These are the ones to know before you rely on the metadata:

- **`@Relationship.displayField` and `cascadeDelete`** are written in the example and not read. `displayField` reads back as its default, `name`, for every relationship, including `Invoice.lines`, which declares `description`.
- **`@Field(validation = ...)`** yields `null` bounds. The sibling `@Validation` form is the only one that reaches the metadata.
- **`@Validation` flag constraints** such as e-mail or URL checks are extracted by nobody, so the example does not use them.
- **`@ActionParam.validation`** is not read.
- **`@Relationship.required`** is not mapped. The relationship stays optional.
- **`@Action.confirm`** does not set `requiresConfirmation` in the metadata.

Whether each of these survives to 1.0 is in [the stability matrix](../stability-matrix.md) and [MIGRATION](../../MIGRATION-0.x-to-1.0.md).

## Where this does not apply

- **Enforcement.** The metadata records what you declared. Whether a platform validates, hides or rejects a field is the job of the processor and generators, and several attributes above are PARTIAL on that side.
- **Constant-valued attributes.** The reader matches annotations by simple name and reads literals, so an attribute set from a constant can read differently here than in the processor. The [source model IO page](../modules/03-source-model-io.md) owns that rule.
- **Other behavior.** Events, sagas, graph and presentation are in guides [03](03-declare-behavior.md), [04](04-model-a-graph.md) and [05](05-declare-presentation.md).
- **Trade-off you accept.** Declaring intent in source is cheap and reviewable, and a declared attribute that nothing consumes yet looks identical to one that does.
