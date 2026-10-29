---
title: Read and write source
type: howto
visibility: public
owning-repo: exeris-sdk
status: active
last-verified: 2026-10-29
---

# Read and write source

**Audience:** you are building an editor, a language server or a codegen step that reads `@ExerisDomain` source, edits it, and must not overwrite what a user changed by hand.

**Goal:** read an entity into metadata, edit its source text, and apply a mutation only when the source still matches the baseline the last code generation recorded.

> **Verified against** `0.13.0-SNAPSHOT` at commit `33574011`, 2026-10-29. Every snippet is quoted or adapted from the cited file and symbol, never from a line range. If a snippet and its source disagree, **the source wins and this guide is the bug**. The sources compile and run in `exeris-sdk-examples`.

## Prerequisites

- `exeris-sdk-source-model-io` and `exeris-sdk-source-model` on your classpath. The contracts of the reader, writer, detector and applier are in [source model IO](../modules/03-source-model-io.md). This page does not restate them.
- The baseline vocabulary (`MutationOp`, `MutationResult`, `SchemaVersion`, `SourceDigest`) is in [source model](../modules/02-source-model.md).
- A Jackson 3 mapper configured as described in [consume the AST](07-consume-the-ast.md).

## Steps

### 1. Read an entity

`SourceModelReader.read` takes source text and returns the first `@ExerisDomain` type as `DomainMetadata`, or an empty `Optional`. Nothing touches the file system, so you pass the text in yourself.

Source: `exeris-sdk-examples/src/test/java/eu/exeris/sdk/examples/studioedit/StudioEditExampleTest.java`, `baselineOf` (quoted)

```java
    private String baselineOf(String source) {
        DomainMetadata metadata = reader.read(source).orElseThrow();
        ObjectNode baseline = (ObjectNode) mapper.valueToTree(metadata);
        baseline.put("sourceDigest", SourceDigest.of(source));
        baseline.put("schemaVersion", SchemaVersion.CURRENT);
        return baseline.toString();
    }
```

This method also builds the baseline. The baseline is the reader output serialised with the canonical mapper, plus the two trust fields `sourceDigest` and `schemaVersion`. In a real pipeline code generation writes those fields into `exeris-metadata/<entity>.json`, and `BaselineTrust` is the record that reads them back.

### 2. Check what the reader drops

Before you round-trip a source, call `unmodeledFacets`. A non-empty result means a baseline from the processor may hold something the reader dropped.

An empty result is not proof that every annotation is modeled. The `Order` example declares `@SagaTransition`, `@Rule` and `@Derived`, none of which the reader reads, and `unmodeledFacets` still returns empty.

Source: `exeris-sdk-examples/src/test/java/eu/exeris/sdk/examples/ordering/OrderingExampleTest.java`, `reservedSurfacesAreNotRead` (adapted, the assertions on transitions, rules and the derived field removed)

```java
        // The guard names only the annotations it knows it drops; these three are absent
        // from the read model without being reported.
        assertThat(reader.unmodeledFacets(source())).isEmpty();
```

### 3. Edit the source text

`SourceModelWriter` edits declarations and keeps the formatting of untouched nodes. A repeated edit returns the input unchanged.

Source: `exeris-sdk-examples/src/test/java/eu/exeris/sdk/examples/studioedit/StudioEditExampleTest.java`, `writerEditsSource` (adapted, assertions removed)

```java
        String withNote = writer.addField(source, "String", "note");
        String renamed = writer.renameField(withNote, "note", "memo");
```

The writer does not update references such as getters or call sites.

### 4. Apply an edit against a baseline

`SourceModelMutationApplier.apply` takes the operation, the baseline JSON and the current source. It runs the conflict detector first and writes only on `Success`.

Source: `exeris-sdk-examples/src/test/java/eu/exeris/sdk/examples/studioedit/StudioEditExampleTest.java`, `appliesOverMatchingBaseline` (adapted, the final assertion on the re-read field names removed)

```java
        String source = invoiceSource();

        ApplyResult result = applier.apply(renameNumberToReference(), baselineOf(source), source);

        assertThat(result.outcome()).isInstanceOf(MutationResult.Success.class);
        assertThat(result.applied()).isTrue();
```

`renameNumberToReference()` builds `new MutationOp.RenameField(MutationPath.field("Invoice", "number").toString(), "reference")` in the same test class.

### 5. Handle a conflict

When the user edited the same field after the baseline was recorded, the verdict is `Conflict` and the returned source is the input, untouched.

Source: `exeris-sdk-examples/src/test/java/eu/exeris/sdk/examples/studioedit/StudioEditExampleTest.java`, `conflictOnDriftedSource` (quoted)

```java
        String baseline = baselineOf(invoiceSource());
        // The user renamed the field by hand after the baseline was recorded.
        String drifted = writer.renameField(invoiceSource(), "number", "code");
        MutationOp op = renameNumberToReference();

        ApplyResult result = applier.apply(op, baseline, drifted);

        assertThat(result.outcome()).isInstanceOf(MutationResult.Conflict.class);
        assertThat(result.applied()).isFalse();
        assertThat(result.source()).isEqualTo(drifted);
```

The SDK reports the conflict. Your host decides whether to merge, prompt or discard.

### 6. Handle a baseline you cannot trust

A null baseline is `NoBaseline` with cause `MISSING_BASELINE`. A baseline written under another schema version is `NoBaseline` with cause `SCHEMA_VERSION_SKEW`. In both cases the source is untouched.

Source: `exeris-sdk-examples/src/test/java/eu/exeris/sdk/examples/studioedit/StudioEditExampleTest.java`, `noBaselineWhenMissing` (adapted, the first line removed)

```java
        ApplyResult result = applier.apply(renameNumberToReference(), null, source);

        assertThat(result.outcome()).isInstanceOf(MutationResult.NoBaseline.class);
        assertThat(((MutationResult.NoBaseline) result.outcome()).cause())
                .isEqualTo(MutationResult.NoBaselineCause.MISSING_BASELINE);
        assertThat(result.source()).isEqualTo(source);
```

A skewed baseline is cured by running code generation once after the upgrade. The rule is in [section 3 of the 1.0 migration guide](../../MIGRATION-0.x-to-1.0.md).

`MutationResult` has a fourth variant, `ValidationError`, and `NoBaseline` has a third cause, `STALE_DIGEST`. This example triggers neither. Handle all of them. Their conditions are in [source model IO](../modules/03-source-model-io.md).

## Verify

```bash
mvn -pl exeris-sdk-examples -am verify -Dtest=StudioEditExampleTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: `BUILD SUCCESS` and five passing tests in `StudioEditExampleTest`.

## Not available today

- **Batch application.** The applier takes one operation against one source.
- **Detector-only use in the example.** The example reaches `SourceModelConflictDetector` through the applier. To get a verdict without writing, call its `detect` overloads yourself.
- **The `STALE_DIGEST` path.** The four-argument `apply` with a concurrency token is not exercised by the example.
- **Facets the reader does not read.** `@Channel`, `@Schedule`, `@SagaTransition`, `@Rule` and `@Derived` are not in the read model.

## Trade-off

The applier refuses on drift and never merges. A user who touched the field loses nothing, and your tool carries the cost of asking what to do next. A silent merge would hide a decision that belongs to the person who wrote the source.

## Where this does not apply

- **Symbol-aware refactoring.** The writer matches by simple name and edits declarations only.
- **Build-time extraction.** The `exeris-tooling` processor owns that path and uses the records only.
- **Concurrent callers.** The threading rules of the reader, writer and applier are in [source model IO](../modules/03-source-model-io.md).
