---
title: "ADR-094: @exeris/ui-kit is a public npm package on its own version line, and supports Tailwind CSS v4 only"
type: adr
visibility: public
owning-repo: exeris-sdk
status: active
last-verified: 2026-10-04
slug: adr/ADR-094
---

# ADR-094: @exeris/ui-kit is a public npm package on its own version line, and supports Tailwind CSS v4 only

| Attribute       | Value                                                                                          |
|:----------------|:-----------------------------------------------------------------------------------------------|
| **Status**      | **ACCEPTED**                                                                                   |
| **Deciders**    | Arkadiusz Przychocki                                                                           |
| **Date**        | 2026-10-01                                                                                     |
| **Scope**       | `exeris-sdk` (`exeris-sdk-ui-kit`, the npm package)                                            |
| **Owning Repo** | `exeris-sdk`                                                                                   |
| **Driven By**   | The `exeris-tooling` 0.9.0 release, which publishes `@exeris/codegen-ts` to npm and emits the UI kit into every generated application |
| **Relates To**  | [ADR-085](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-085-documentation-architecture-and-repo-hygiene-standards.md) §F.21 — the package is a gated TypeScript surface with an API golden |

> The number is held by the [`exeris-docs/adr-index.md`](https://github.com/exeris-systems/exeris-docs/blob/main/adr-index.md) row.

## Context and Problem Statement

The UI kit is the design-token and component-class package a generated Angular application renders
against. `@exeris/codegen-ts` writes it into the generated application's `package.json`, and the
generated markup uses its token utilities (`bg-exeris-primary`, `p-exeris-md`, …). It shipped as
`@exeris-systems/ui-kit` on GitHub Packages, which requires a token with `read:packages` even to
install a public package. `exeris-tooling` 0.9.0 publishes `@exeris/codegen-ts` to the public npm
registry, and a public package cannot emit a dependency its consumers cannot install without
credentials.

Two properties of the package were decided in practice and recorded only in `MIGRATION.md` and
`ROADMAP.md`. Its version line is independent of the SDK's Java modules
(`MIGRATION-0.x-to-1.0.md` §2b): it was at `0.1.0` against a `0.12.0` Java line, as
`@exeris/codegen-ts` runs at its own number against `exeris-tooling`. And its own 1.0 freezes names
— every `--exeris-*` custom property, every `.exeris-*` class, every Tailwind key that produces a
utility — and not the values behind them, which are the theming surface.

The package also carried two sources for one utility namespace: a Tailwind v3 JS preset
(`tailwind.preset.js`, exported as `./tailwind.preset.js`) and a Tailwind v4 `@theme` entry
(`theme.css`). Generated applications are on Tailwind v4, where a JS preset is inert, and the
emitted `tailwind.config.js` that referenced the preset was never loaded by a v4 build. Keeping
both meant every token change was written twice and every test compared the two. Removing the
preset after the package's 1.0 would be a major.

The question this ADR answers: where is the UI kit published, how is it versioned, and which
Tailwind major does it support?

## 🏁 The Decision

**`@exeris/ui-kit` is published to the public npm registry on a version line of its own, and from
0.2.0 it supports Tailwind CSS v4 only.**

**Concrete obligations:**

1. **Public registry, one name.** The package is `@exeris/ui-kit` on registry.npmjs.org, ~~published~~
   staged with npm provenance from a `ui-kit-v<version>` tag by `.github/workflows/publish-ui-kit.yml`
   *(amended 2026-10-02: CI stages the version and a maintainer approves it with 2FA — see
   `## Amendments`)*. It installs with no `.npmrc` entry and no token. The GitHub Packages copy is
   not updated.
2. **Own version line.** The package's version is independent of the SDK's Java modules and moves
   by its own semver: a removal from the exported surface is a minor while the package is `0.x`
   and a major after its 1.0. The SDK's 1.0.0 freeze does not cover it.
3. **Names freeze at its 1.0, values do not.** `tests/public-surface.txt` pins every `--exeris-*`
   custom property, every ~~`.exeris-*` class~~ `@exeris/ui-kit/styles` class *(amended
   2026-10-04: the classes no generator emits are in `@exeris/ui-kit/preview`, outside the freeze
   — see `## Amendments`)*, every `@theme` variable (`theme-var:`) and every `defaultTheme` path;
   removing or renaming one is a breaking change for the package. Colour,
   spacing, radius and shadow values stay free.
4. **Tailwind v4 only.** The package ships no Tailwind v3 preset. Its peer dependency is
   `tailwindcss >= 4`. The `@theme` block of `src/styles/theme.css` is the single source of truth
   for the `exeris` utility namespace, and the compile, drift, dark-mode and public-surface tests
   derive from it.
5. **Accepted breaks are recorded.** A removal from the package's exported surface — an `exports`
   entry, a peer range narrowed, a TypeScript symbol — is listed in
   `exeris-sdk-ui-kit/api/accepted-api-changes.json` (`signature`, `since`, `adr`,
   `justification`) in the PR that makes it, beside the API golden `api/ui-kit.api.md`, and has a
   `MIGRATION.md` section.

## Consequences

### ✅ Positive Outcomes

- **[+] `@exeris/codegen-ts` can be public.** A generated application installs its UI dependency
  from the same registry as everything else, with no credentials.
- **[+] One source per token.** A token is declared once in `theme.css` (and its runtime property in
  `index.css`); the tests check consistency instead of comparing two declarations of the same thing.
- **[+] The package's contract is written down.** Its distribution, its version line and the scope
  of its 1.0 are decisions with a record, not conventions inferred from MIGRATION entries.

### ⚠️ Trade-offs

- **[-] Tailwind v3 consumers lose support.** A host on v3 stays on `@exeris-systems/ui-kit` 0.1.0 or
  moves to v4. No such consumer is known; generated applications are on v4.
- **[-] Two version numbers to read.** The SDK and the UI kit carry different numbers, and a reader
  has to know which line a change belongs to. The CHANGELOG and MIGRATION entries name the package.
- **[-] A new publishing credential.** Publication needs the `@exeris` npm organisation and an
  `NPM_TOKEN` repository secret, which did not exist before. *(amended 2026-10-02: the token is
  stage-only, and every version needs a maintainer's 2FA approval to go live — see `## Amendments`)*

### 📋 What is NOT in scope

- What generated applications import and emit — `exeris-tooling`'s generator decides that, including
  dropping its `tailwind.config.js` and its GitHub Packages `.npmrc`.
- Whether the `.exeris-*` component classes stay in the 1.0 contract, which no generator emits today;
  the ROADMAP section "`@exeris/ui-kit` — the road to its own 1.0" carries that item.

### 🚫 Non-Goals

- **Versioning the kit with the SDK.** Binding a CSS package's cadence to a compilation contract's
  would hold back the theming surface, and a CSS break and a Java break fail consumers differently.
- **Framework bindings.** The kit stays framework-agnostic: CSS, tokens and a TypeScript constant.
  Angular-specific concerns belong to the generator.

### ⚠️ Risks and Assumptions

- **Assumes:** generated applications and the hosts that consume the kit run Tailwind v4.
- **Reversed by:** a consumer that must stay on Tailwind v3 and cannot pin 0.1.0 — then a v3 entry
  returns as an additive minor, not a revert of the version line.
- **Risk:** a publish without the token or the organisation fails the workflow with a message and
  publishes nothing; the maintainer who pushes the tag notices first.

## Cross-references

- `MIGRATION-0.x-to-1.0.md` §2b — the package's independent version line and its own 1.0.
- `MIGRATION.md` §0.11.x → 0.12.x — the upgrade step: the new name, the registry, the v3 removal.
- `ROADMAP.md`, "`@exeris/ui-kit` — the road to its own 1.0" — the open items before that 1.0.
- `.agents/policies/ui-kit.md` — the package's invariants, enforced at review.
- `exeris-sdk-ui-kit/api/accepted-api-changes.json` — the breaks this ADR accepts.

## Engineering Protocol

1. `tests/public-surface.test.js` fails on a removed or renamed name unless the snapshot is
   re-baselined in the same change.
2. `tests/theme.test.js` asserts the package exports no `./tailwind.preset.js` and ships no preset.
3. `api/ui-kit.api.md` (api-extractor, `tsdoc-gate`) fails on a TypeScript surface change that is not
   accepted in the same PR.
4. `publish-ui-kit.yml` runs the package's gates before ~~`npm publish`~~ `npm stage publish` and
   refuses to ~~publish~~ stage without `NPM_TOKEN` *(amended 2026-10-02 — see `## Amendments`)*.

## Amendments

- **2026-10-04 — the freeze covers the classes a generator uses; the rest are preview.**
  Obligation 3 pinned every `.exeris-*` class, but a generated application used none of them, so
  1.0 would have frozen 47 names that nothing had exercised. `exeris-tooling` 0.9.0 (its P20) emits
  22 of them. Those stay in `@exeris/ui-kit/styles` and in `tests/public-surface.txt`. The other 25
  move to a new `@exeris/ui-kit/preview` entry, `src/styles/preview.css`, absent from the snapshot:
  a preview class may change or go in a minor, and moves into `styles` additively when a generator
  uses it. `tests/preview-entry.test.js` holds the split: no class in both entries, no preview class
  in the snapshot, and the textarea's repeated field base equal to the input's. Shipped in 0.3.0,
  with `exeris-sdk-ui-kit/api/accepted-api-changes.json` recording the move. Rejected: freezing all
  47 at 1.0 (names nobody used), and deleting the 25 (the controls exist in `ComponentType` and
  will be emitted by 1.x field widgets).

- **2026-10-02 — publication is staged and approved with 2FA.** Obligation 1, the credential
  trade-off and protocol step 4 named a direct `npm publish` with a token. npm restricts granular
  tokens that bypass 2FA and removes their direct publish in January 2027, so a CI token that
  publishes on its own is a credential with an end date. `publish-ui-kit.yml` runs `npm stage
  publish` with a stage-only `NPM_TOKEN`; the version goes live when a maintainer approves it with
  2FA (`npm stage approve <stage-id>`, or the package's Staged Packages tab on npmjs.com). The
  registry, the name, the provenance statement, the gates and the tag trigger are unchanged.
  Rejected: a token with 2FA bypass, which stops publishing in January 2027. Trusted publishing
  (OIDC) is compatible with this flow and is the next step once the package exists on the registry;
  it removes the `NPM_TOKEN` secret, not the approval.
