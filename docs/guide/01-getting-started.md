# 01 — Getting started

← [Guide index](README.md)

## 1. Get the artifacts

There are **no `eu.exeris` artifacts on Maven Central yet.** Central publishing
is wired in the root POM but deliberately not switched on before the kernel
moves — an ecosystem sequencing decision, not an oversight. Releases ship as a
git tag plus a GitHub Release.

So: build from source and consume the local install.

```bash
git clone https://github.com/exeris-systems/exeris-sdk
cd exeris-sdk
mvn -q install
```

Then depend on the annotations:

```xml
<dependency>
    <groupId>eu.exeris</groupId>
    <artifactId>exeris-sdk-annotations</artifactId>
    <version>0.10.0</version>
    <scope>provided</scope>
</dependency>
```

`provided` is the honest scope. The annotations are `@Retention(SOURCE)` — they
are needed to compile and never at runtime, and nothing in this jar should reach
a deployment.

### JDK baseline

`maven.compiler.release=25` across the reactor (ADR-069). These jars sit on your
*compile* classpath, so the baseline follows the kernel's GA line rather than
leading it: a major-70 class file is refused outright by `javac` on JDK 25, which
would lock out LTS-only deployments. You can compile your own code on a newer
JDK; you just cannot ask the SDK to.

## 2. Annotate a class

Note the shape: **every annotation is a sibling of the one it relates to, never
nested inside it.** See [the nested-form trap](README.md#1-the-nested-form-is-a-silent-no-op).

```java
package com.acme.sales;

import eu.exeris.sdk.annotation.*;
import java.math.BigDecimal;
import java.util.UUID;

@ExerisDomain(
    module = "sales",
    aggregate = "Order",
    path = "/orders",
    dataScope = ExerisDomain.DataScope.TENANT,
    softDelete = true,
    audited = true,
    versioned = true
)
public class Order {

    @Field(label = "ID")
    private UUID id;

    @Field(label = "Order Number", required = true, unique = true, indexed = true)
    @Validation(minLength = 3, maxLength = 32)
    private String orderNumber;

    @Field(label = "Customer", required = true)
    @Relationship(targetEntity = Customer.class,
                  relationshipType = Relationship.RelationshipType.MANY_TO_ONE,
                  displayField = "name")
    private UUID customerId;

    @Field(label = "Status", filterable = true, sortable = true)
    private OrderStatus status;

    @Field(label = "Total", readOnly = true,
           computed = true, computedFrom = {"unitPrice", "quantity"})
    private BigDecimal total;

    @Action(description = "Confirm this order", httpMethod = "POST")
    public void confirm(@ActionParam(description = "Confirmation note") String note) {
        // your logic — never generated, never overwritten
    }
}
```

Things deliberately **not** in this example, because they do nothing:

- **No superclass.** There is no `BaseTenantEntity` or any other SDK base type.
  The SDK ships annotations and records; that is the whole artifact.
- **No `@PrimaryKey` / `@TenantId` / `@Version` / `@SoftDelete` markers.** The
  `system` package is extracted by nobody. The columns come from the
  `@ExerisDomain` flags above.
- **No `@UI` on fields.** Field-level `@UI` is not extracted in either form.
- **No `tenantScoped`.** Deprecated for removal in 0.10.0; `dataScope` replaced it.

## 3. Generate

Codegen lives in `exeris-tooling`, as a Maven plugin with goal prefix `exeris`:

```bash
mvn exeris:generate
```

The plugin also exposes `exeris:detach` and `exeris:verify-capabilities`. Its own
documentation in `exeris-tooling` is authoritative for configuration, the
annotation-processor wiring, and which JDK the processor runs under — that is a
tooling concern, and it differs from the SDK's own compile baseline.

## 4. Read what came out

Two stages, and it is worth looking at both the first time.

### The AST hand-off

The processor writes one JSON document per entity:

```
exeris-metadata/Order.json          ← DomainMetadata
exeris-metadata/view_OrderPage.json ← ViewMetadata, if you declared a @View
```

This is the contract between the two halves of the toolchain, and it is the
fastest way to answer "did my annotation survive?". If an attribute you wrote is
not in this file, no generator will ever see it — it is RESERVED, regardless of
what its javadoc says. Diffing this file after a change is the single most useful
debugging habit with this SDK.

The AST types are plain Jackson-serializable records in
`eu.exeris.sdk.sourcemodel.ast`. If you read this JSON yourself, configure your
mapper with `FAIL_ON_NULL_FOR_PRIMITIVES = false` — the AST uses primitive
booleans under `@JsonInclude(NON_DEFAULT)`, so absent fields arrive as `null`.

### The emitted code

From a `@ExerisDomain` class the toolchain emits, per entity:

| Target | What |
|---|---|
| Java (kernel tier) | request handler, service, repository, internal client, application bootstrap |
| Java (conditional) | domain event + event handler, saga driver, graph-sync writer, streaming handlers |
| Java (tests) | handler / service / repository / saga test scaffolding |
| SQL | a Flyway migration for the table, plus the tenant column and RLS policy when tenant-partitioned, and the audit / soft-delete / version columns when those flags are set |
| OpenAPI | the specification document for the emitted REST surface |
| TypeScript | request/response types, enums, query builder |
| Angular | HTTP service, form, list, and any `@View`-declared page, wired into a generated app structure |

**Not emitted:** there is no GraphQL schema or resolver generator anywhere in the
toolchain. `@ExerisDomain(graphqlApi = true)` reaches the AST and is read by
nobody. (The `@Graph*` family is about graph-*database* projection — a different
thing that shares a prefix.)

## 5. What you keep writing by hand

Entity-First means the *mechanical* layer is generated. It does not mean
everything is:

- Method bodies of your `@Action`s.
- Anything the declarative layer cannot express — the `@Derived` / `@Rule`
  annotations exist to declare the mechanical slice, and they are RESERVED, so
  today that boundary sits at "all behaviour is hand-written".
- Field-level presentation detail. `@UI` at field level is not extracted, so
  component choice, ordering and formatting are yours in the generated Angular
  tree.

---

Next: [02 — Modelling an entity](02-modelling-an-entity.md)
