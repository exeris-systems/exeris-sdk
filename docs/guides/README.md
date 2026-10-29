---
title: Developer guides
type: reference
visibility: public
owning-repo: exeris-sdk
status: active
last-verified: 2026-10-29
---

# Developer guides

**Audience:** you write annotated Java entities against `exeris-sdk` and want task-oriented paths through it, in the order you need things.

These guides are not the normative documents. [`docs/modules/`](../modules/README.md) holds the contract of each module, [`docs/stability-matrix.md`](../stability-matrix.md) holds the tier of each surface, and [`docs/adr/`](../adr) holds the decisions. A guide that touches one of those links to it instead of restating it, so a change has one place to land.

## Pick your path

| Guide | Read it when |
|---|---|
| [01 Platform and dependencies](01-platform-and-dependencies.md) | You need the JDK baseline, the Maven coordinates and the npm package. Start here whichever other guide you want. |
| [02 Model a domain](02-model-a-domain.md) | You are writing your first entity: fields, validation, relationships, actions, and reading the result back. |
| [03 Declare behavior](03-declare-behavior.md) | You are adding domain events and a saga to an entity, and need to know which of them the reader carries. |
| [04 Model a graph](04-model-a-graph.md) | You are projecting an entity into a graph store with `@Graph`, `@GraphProperty` and `@GraphEdge`. |
| [05 Declare presentation](05-declare-presentation.md) | You are declaring list, detail and form presentation with `@UI`, `@UIGroup`, `@Tab` and `@NavMenu`, and a page with `@View`. |
| [06 Read and write source](06-read-and-write-source.md) | You are building an editor, language server or codegen step that reads and edits `@ExerisDomain` source through the mutation writer and applier. |
| [07 Consume the AST](07-consume-the-ast.md) | You read or produce the AST wire format and want to prove a reader against the SDK's test kit. |
| [08 Capabilities and composition](08-capabilities-and-composition.md) | You want a worked example of a capability module, its lifecycle hooks, the `cap-manifest.json` stamp and the boot conductor. |
| [09 Style generated UI](09-style-generated-ui.md) | You consume `@exeris/ui-kit` from a Tailwind v4 project. |
| [Examples index](../examples/README.md) | You want the runnable sources the guides quote, listed by scenario. |

## What these guides promise

Every guide carries a verification header that names the commit it was checked against. Every snippet taken from the repository carries a citation line with the source file, the symbol, and whether the snippet was quoted verbatim or adapted. Commands you run are not citations.

If a snippet and its source disagree, the source wins and the guide is the defect. Report it or fix it. Do not code against the guide.

Where a guide touches something the SDK does not do yet, it says so in a "Not available today" section. Nothing here describes a target state.

Most snippets come from the [`exeris-sdk-examples`](../../exeris-sdk-examples) module. It compiles against the annotations and its tests read the same files through `SourceModelReader`, so a renamed annotation attribute fails that build before it fails a page.

## Maintaining these guides

**Citations are a path plus a symbol, never a line number.** A line range rots on every unrelated edit to the same file. A path and a symbol survive until the symbol is renamed, and then the check below fails. Keep the `(quoted)` or `(adapted)` marker accurate: it tells the next reader whether a difference from the source is drift or deliberate trimming.

**Prefer sources with teeth.** When one behavior can be shown from an example, a test body or Javadoc, quote them in that order. An API change stops an example compiling, so someone has to touch it. Javadoc is the weakest source, because nothing breaks when it goes stale.

**Run the citation check after a refactor that moves or renames files.** From the repository root:

```bash
tools/docs-citations/check.sh
```

The script reads the `Source:` lines of the guides and examples pages. Its header states the exact checks. It does not verify that a snippet still says what the surrounding prose claims, and that one step needs a human.

**Bump `last-verified` only after checking the code**, as the [documentation style guide](https://github.com/exeris-systems/exeris-docs/blob/main/standards/docs-style-guide.md) says, and move the commit in the verification header at the same time.

**Keep the snippet count low.** Each snippet is a maintenance liability. The test for a new one is whether deleting it and linking to the source would lose the reader.

## Where these guides stop

- [`docs/modules/`](../modules/README.md): the contract, failure modes and owning ADRs of each module.
- [`docs/stability-matrix.md`](../stability-matrix.md): how far you can lean on a given surface.
- [`docs/architecture.md`](../architecture.md): how the modules fit together.
- [`docs/glossary.md`](../glossary.md): the vocabulary.
- [`MIGRATION-0.x-to-1.0.md`](../../MIGRATION-0.x-to-1.0.md): what is removed at 1.0.0.
- [Kernel guides](https://github.com/exeris-systems/exeris-kernel/blob/main/docs/guides/README.md): running an application on the kernel, which this repository does not cover.
