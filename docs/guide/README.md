# Exeris SDK — user guide

This is the guide for people who have to *use* the SDK: what to annotate, what
comes out the other end, and — the part no javadoc used to answer — which of the
938 declared attributes actually do anything.

| | |
|---|---|
| [01 — Getting started](01-getting-started.md) | Resolve the artifacts, annotate a class, run codegen, read the output. |
| [02 — Modelling an entity](02-modelling-an-entity.md) | `@ExerisDomain`, `@Field`, `@Validation`, `@Relationship`, `@Action`. |
| [03 — Behaviour and presentation](03-behaviour-and-presentation.md) | Events, sagas, `@View`, `@UI`, capabilities, and the reserved layers. |
| [04 — Status matrix](04-status-matrix.md) | Every annotation, every extracted attribute, with the evidence. |

Guide version: SDK **0.10.0**. Evidence traced against `exeris-tooling` at
`9c72a8f`. When the two disagree, `exeris-tooling` is right and this guide is
stale — please open an issue.

## The one thing to understand first

Exeris annotations are **descriptive metadata, not behaviour**. Every annotation
in this SDK is `@Retention(SOURCE)`: it does not survive into the class file, it
is not on your runtime classpath, and the SDK has no dependencies at all.
Annotating a class changes what the *build-time toolchain emits*. It changes
nothing about how your program runs.

That has a consequence which shapes this entire guide. An annotation has an
effect only if two independent hops carry it:

```
your annotated .java
     │
     │  hop 1 — extraction
     │  exeris-tooling / ExerisDomainProcessor    (build-time, javac)
     │  or eu.exeris.sdk.sourcemodel.io / SourceModelReader  (JavaParser, for Studio/LSP)
     ▼
exeris-metadata/<Entity>.json          ← the AST (eu.exeris.sdk.sourcemodel.ast)
     │
     │  hop 2 — consumption
     │  exeris-tooling / codegen-java + codegen-ts
     ▼
emitted Java · SQL · OpenAPI · TypeScript/Angular
```

An attribute can be declared and not extracted. It can be extracted and read by
no generator. Both look identical from where you sit — the code compiles, the
build is green, and nothing appears. So every annotation and attribute in this
guide carries an explicit status.

## Status vocabulary

Four words, used consistently here, in the package javadoc, and in the AST
package.

| Status | Means | What happens if you write it |
|---|---|---|
| **LIVE** | Extracted into the AST **and** read by a generator that emits something. | Emitted output changes. |
| **PARTIAL** | Reaches the AST, but no generator reads it — or only one of the two readers handles it. | Nothing is emitted. The value is on the wire, so a future generator can pick it up without a source change. |
| **RESERVED** | Declared here, extracted by nobody. Does not reach the AST at all. | Nothing. It compiles, and that is all it does. |
| **DEPRECATED** | `@Deprecated(forRemoval = true)`, with a named replacement and a named removal release. | Works during the window, with a build warning. See [`MIGRATION.md`](../../MIGRATION.md). |

The distinction that matters most is **LIVE vs PARTIAL**. Reaching the AST is not
the same as producing output, and it is the easier of the two to verify — which
is why earlier audits of this surface (including the 0.9.0 deprecation sweep)
did not catch the difference.

## The honest headline

The annotation surface is deliberately designed ahead of the generators. That is
a defensible strategy — it lets the model settle before the emitters chase it —
but it means the surface is mostly aspirational today:

| | Count |
|---|---|
| Declared attributes (surface snapshot) | **938** |
| Annotation types referenced by the processor at all | **21** of 49 |
| Attributes actually extracted into the AST | **98** |
| Of those, read by a generator that emits something | **79** |

The [status matrix](04-status-matrix.md) is where those numbers are derived, per
annotation; these are the same figures, not a rounded summary of them.

**You cannot infer from an attribute's existence that it does anything.** That is
not a bug report; it is how to read this SDK. The [status
matrix](04-status-matrix.md) tells you which is which, per attribute, with the
`exeris-tooling` site that proves it.

## Two traps worth knowing before you write a line

### 1. The nested form is a silent no-op

Several annotations declare a member typed as another annotation —
`@Field(validation = @Validation(...))`, `@Field(ui = @UI(...))`,
`@ExerisDomain(ui = @UI(...))`. These members exist and they compile. **No reader
looks at them.** Both producers find related annotations by walking the element's
*directly present* annotations, so only the sibling form is seen.

```java
// NO-OP — compiles clean, reaches no AST, produces nothing
@Field(label = "Order Number", validation = @Validation(minLength = 3))
private String orderNumber;

// READ — the constraint reaches FieldMetadata
@Field(label = "Order Number")
@Validation(minLength = 3)
private String orderNumber;
```

Always write siblings.

### 2. System columns come from flags, not from the system markers

The entire `eu.exeris.sdk.annotation.system` package — `@PrimaryKey`,
`@TenantId`, `@Version`, `@SoftDelete`, the `@Audit*` family — and the
`security` package (`@Encrypted`, `@RowLevelSecurity`) are extracted by nobody.
Marking a field `@TenantId` creates no column, no filter and no policy.

What creates them is the entity-level flag set on `@ExerisDomain`:
`dataScope`, `softDelete`, `audited`, `versioned`. See
[02 — Modelling an entity](02-modelling-an-entity.md#system-columns).

## Stability

`0.x` is unstable: any 0.x release may break the surface, and downstream
consumers pin exact versions. `1.0.0` freezes it — additive minors and bug-fix
patches only. Practically, that makes the LIVE/PARTIAL/RESERVED split a
*versioning* question too: RESERVED surface frozen at 1.0.0 is surface the
project has to carry until 2.0 whether or not a generator ever arrives.

## Where else to look

- [`MIGRATION.md`](../../MIGRATION.md) — every deprecation, replacement, removal release.
- [`ROADMAP.md`](../../ROADMAP.md) — what each release added; what 1.0.0 freezes.
- [`docs/adr/`](../adr) — the decisions behind the surface.
- `exeris-tooling` — the processor and the generators. **Authoritative** for what
  is emitted and how to run it; this guide only reports what it found there.
