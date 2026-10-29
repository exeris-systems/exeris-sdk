---
title: Consume the AST
type: howto
visibility: public
owning-repo: exeris-sdk
status: active
last-verified: 2026-10-29
---

# Consume the AST

**Audience:** you read the `exeris-metadata/<entity>.json` files the SDK's producers write, or you write a producer or reader and want the SDK's conformance kit to judge it.

**Goal:** read AST JSON with a correctly configured Jackson 3 mapper, load the two machine-readable contracts the SDK ships, and bind a reader to the technology compatibility kit.

> **Verified against** `0.13.0-SNAPSHOT` at commit `0753b7d0`, 2026-10-29. Every snippet is quoted or adapted from the cited file and symbol, never from a line range. If a snippet and its source disagree, **the source wins and this guide is the bug**. The tck binding runs in `exeris-sdk-examples`.

## Prerequisites

- `exeris-sdk-source-model` for the records. The record layer is described in [source model](../modules/02-source-model.md).
- Jackson 3 (`tools.jackson.databind`). The mapper posture is specified for Jackson 3.
- For step 4: `exeris-sdk-tck` at test scope. The kit is described in [technology compatibility kit](../modules/08-tck.md).

## Steps

### 1. Configure the mapper

Set `FAIL_ON_NULL_FOR_PRIMITIVES` to `false`. The reason and the full posture are in [section 3 of the 1.0 migration guide](../../MIGRATION-0.x-to-1.0.md) and in the [ast-wire-format policy](https://github.com/exeris-systems/exeris-sdk/blob/main/.agents/policies/ast-wire-format.md).

For production code, build the mapper yourself with that one setting.

Source: `exeris-sdk-source-model/src/test/java/eu/exeris/sdk/sourcemodel/ast/AstJsonRoundTripTest.java`, `mapper` (quoted)

```java
    private final ObjectMapper mapper = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
            .build();
```

In tests you can take the kit's reference mapper instead. The kit is a test-scope dependency, so production code does not use it.

Source: `exeris-sdk-tck/src/main/java/eu/exeris/sdk/tck/TckMappers.java`, `TckMappers.canonical` (quoted)

```java
    public static ObjectMapper canonical() {
        return JsonMapper.builder()
                .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
                .build();
    }
```

### 2. Read an entity

Bind the JSON to `DomainMetadata`. It ignores unknown properties, so the trust fields beside the domain fields do not disturb the read.

Source: `exeris-sdk-examples/src/test/java/eu/exeris/sdk/examples/presentation/PresentationExampleTest.java`, `jsonRoundTrip` (quoted)

```java
        ObjectMapper mapper = TckMappers.canonical();
        DomainMetadata employee = employee();

        String json = mapper.writeValueAsString(employee);

        assertThat(mapper.readValue(json, DomainMetadata.class)).isEqualTo(employee);
```

The two trust fields are read separately into `BaselineTrust` from the same file. See [read and write source](06-read-and-write-source.md).

### 3. Load the shipped contracts

Two JSON documents ship inside the SDK jars. `META-INF/exeris/ast-schema.json` is a JSON Schema of the AST records, inside `exeris-sdk-source-model`. `META-INF/exeris/annotation-catalog.json` describes every annotation with its prose, inside `exeris-sdk-annotations`. The emitters and their guarantees are in [annotation catalog](../modules/04-annotation-catalog.md).

Read them as classpath resources.

Source: `exeris-sdk-source-model/src/test/java/eu/exeris/sdk/sourcemodel/ast/AstSchemaContractTest.java`, `readSchema` (adapted, the missing-resource check removed)

```java
        try (InputStream in = AstSchemaContractTest.class.getResourceAsStream(RESOURCE)) {
            schema = new ObjectMapper().readTree(in);
        }
```

`RESOURCE` is `/META-INF/exeris/ast-schema.json` there. The schema carries `sdkVersion` and `astSchemaVersion`, which are two separate numbers. The second matches `SchemaVersion.CURRENT`.

Source: `exeris-sdk-annotations/src/test/java/eu/exeris/sdk/annotation/AnnotationCatalogContractTest.java`, `readCatalog` (adapted, the resource assertion and the exception wrapper removed)

```java
        try (InputStream in = AnnotationCatalogContractTest.class.getClassLoader()
                .getResourceAsStream(RESOURCE)) {
            return JsonMapper.builder().build().readTree(in);
        }
```

Here `RESOURCE` is `META-INF/exeris/annotation-catalog.json`. The catalog lists each annotation under `annotations`, with its `qualifiedName`, `purpose` and `attributes`.

### 4. Bind the reader kit

Extend `AbstractMetadataReaderTck`, implement `read`, and the kit's cases run against your reader. The binding below is the SDK's own reader.

Source: `exeris-sdk-examples/src/test/java/eu/exeris/sdk/examples/tckbinding/TckBindingExampleTest.java`, `TckBindingExampleTest` (adapted, javadoc and the display-name annotation removed)

```java
class TckBindingExampleTest extends AbstractMetadataReaderTck {

    private final SourceModelReader reader = new SourceModelReader();

    @Override
    protected DomainMetadata read(String entitySource) {
        return reader.read(entitySource).orElseThrow(() -> new AssertionError(
                "no @ExerisDomain type in a TCK corpus source"));
    }

    @Override
    protected Set<Facet> unsupportedFacets() {
        return Set.of();
    }
}
```

`unsupportedFacets` is declared on `AbstractExerisTck`. A facet you list turns the kit's cases for it into skips. List one only when your reader emits nothing for it. `SourceModelReader` reads every facet the kit asks about, so the set here is empty. A skip is not a pass. Count them.

`IDENTITY` and `FIELDS` cannot be declared unsupported. A final case in the kit fails if you try.

For a producer, extend `AbstractMetadataProducerTck`. For both sides at once, extend `AbstractMetadataParityTck`. For your own mapper, extend `AbstractMapperPostureTck`. All four suites are in the [kit page](../modules/08-tck.md).

## Verify

```bash
mvn -pl exeris-sdk-examples -am verify -Dtest=TckBindingExampleTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: `BUILD SUCCESS`. The surefire report for `TckBindingExampleTest` shows 18 tests, 0 skipped.

## Not available today

- **A producer binding example.** The examples module binds a reader only. Producing metadata needs the `exeris-tooling` processor, which this repository does not contain.
- **A mapper-posture binding example.** No example binds `AbstractMapperPostureTck`. It tests the mapper you ship, so the binding is yours to write.
- **Your own corpus.** The kit's corpus is three entities (`Order`, `Customer`, `Product`), and a binder does not supply its own.

## Trade-off

`DomainMetadata` and every other AST record except `EnumMetadata` and its nested `EnumValueMetadata` ignore unknown properties, so a misspelled key in a hand-edited file reads as absent and raises nothing. `ast-schema.json` allows additional properties on every object, so validating against it does not catch the typo either.

## Where this does not apply

- **Jackson 2.** The records and the posture are specified for Jackson 3.
- **The composition manifest.** `cap-manifest.json` has its own records without Jackson annotations. See [composition spec](../modules/05-composition-spec.md).
- **Production classpaths.** The kit is for test scope only.
