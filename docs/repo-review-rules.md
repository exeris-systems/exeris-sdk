---
title: "Review rules for exeris-sdk"
type: reference
visibility: public
owning-repo: exeris-sdk
status: active
last-verified: 2026-09-25
---

# Review rules for `exeris-sdk`

The `repo-routine` extension of `exeris-systems/.github`'s `docs-guardrails-review.md`, applied
**after** its parts and under its severity tags, output format and verdict schema. It adds checks
and raises severities; it lowers nothing and skips nothing. One review, one verdict, one publisher.

**The criteria are not authored here.** They are owned by the policies under `.agents/policies/`,
which `AGENTS.md` names and `.agents/workflows/sdk-pr-review.md` orders. This file names the
questions a reviewer must reach for and the severity each answer carries. Read the policy a rule
names before applying it: a rule copied here would be a second place to author it, which
`agents-md-schema.md` rule 2 forbids.

## What this repository is answerable for

The most upstream repository in the ecosystem. It ships `@Retention(SOURCE)` annotations and the
canonical AST that `exeris-tooling` and `exeris-platform` compile against, so a contract broken here
breaks someone else's build, and nothing fails in this one. The shared routine's `code` part judges
whether a change is correct. What it cannot know is which of this repository's surfaces other
repositories compile against, and that is what follows.

## Step S — rules of this repository

S1. **Zero runtime coupling** (`zero-runtime-coupling.md`). An annotation that is not
    `@Retention(SOURCE)`, a dependency added to `exeris-sdk-annotations`,
    `exeris-sdk-composition-spec` or `exeris-sdk-composition-lifecycle`, `exeris-sdk-source-model`
    pulling anything beyond `jackson-annotations`, JavaParser outside `exeris-sdk-source-model-io`,
    or an import of downstream code → `[HARD BLOCK]`.

S2. **The AST is a wire format** (`ast-wire-format.md`). An AST type declared as a class rather than
    a record → `[HARD BLOCK]`: Jackson 3 omits its fields without an error. A record component
    added, changed or removed with no `AstJsonRoundTripTest` case, or a change to the mutation
    surface with no `MutationWireFormatTest` case → `[CONTRACT]`. A change to what the AST carries
    whose *Cross-repo impact* names neither `exeris-tooling` nor `exeris-platform` → `[CROSS-REPO]`.

S3. **`@Field` and `@Validation` keep their scopes** (`field-validation-scoping.md`). A constraint
    attribute on `@Field`, a lifecycle flag on `@Validation`, or a second validation carrier in the
    AST → `[HARD BLOCK]`. The scoping rationale changed in one of the two `package-info.java` files
    the policy names and not in the other → `[CONTRACT]`.

S4. **Public is declared, never inferred** (`route-access.md`). Empty `roles` or `permissions` read
    as public access, or an `UNSPECIFIED` constant added to either `RouteAccess` type →
    `[HARD BLOCK]`. Each opens a protected endpoint in every application built on the change.

S5. **A published element leaves through the deprecation pipeline**
    (`stability-and-deprecation.md`). This raises the shared routine's rule 27. A public annotation,
    attribute, AST record or public method removed or renamed without
    `@Deprecated(forRemoval = true)`, a `{@link}` replacement in its `@deprecated` text, and a
    `MIGRATION.md` section → `[HARD BLOCK]`, not `[CONTRACT]`, however small the diff. Other
    repositories compile against this surface, so a break with no pipeline lands in their build with
    no warning in this one.

S6. **The class-file baseline is the kernel's** (`jdk-baseline.md`). `maven.compiler.release` other
    than 25 anywhere in the reactor → `[HARD BLOCK]`. A plugin or module override that can raise the
    emitted class-file major without `ClassFileBaselineTest` seeing it → `[CONTRACT]`.

S7. **A contract regression has a test that catches it.** This specialises the shared routine's rule
    30 for the layers `exeris-sdk-verification` owns. A new or renamed `@interface` that
    `AnnotationContractTest` does not reach, or an exception carved out of the JaCoCo gate on
    `exeris-sdk-source-model` or the Vitest gate on `exeris-sdk-ui-kit` → `[CONTRACT]`.

## Where this does not apply, and what it costs

Not to correctness, scope, or the pull request's body and commits. The shared `code` and `pr` parts
judge those, and `sdk-pr-review.md`'s steps for them are not restated here: a rule in two places
drifts in one of them. Not to `ui-kit.md` or `javadoc-and-contract-emitters.md`: doc comments are
the shared `code-docs` part's and the Javadoc and TSDoc gates', and whether either policy earns a
rule of its own here is a change of its own.

The cost is that S1 to S7 are prose a reviewer applies, not a program. This repository hands the
review no `repo-checks` output, so the tests these rules name are evidence only through the build
checks on the pull request, which the reviewer cannot read. A rule the reviewer could only check by
running a test is reported as unchecked, not as passing.
