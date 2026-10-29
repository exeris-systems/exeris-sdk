---
title: UI kit
type: module
visibility: public
owning-repo: exeris-sdk
status: active
last-verified: 2026-10-29
---

# UI kit

**Package:** `@exeris/ui-kit` on the public npm registry, on its own version line (the number is in `exeris-sdk-ui-kit/package.json`)
**Dependencies:** peer `tailwindcss >= 4.0.0`. No runtime dependencies. The package is not a Maven module and is excluded from the Java reactor.

`@exeris/ui-kit` supplies the design tokens, the Tailwind CSS v4 theme entry and the component classes that generated Exeris applications render against. It is CSS plus one typed constant, `defaultTheme`, and no JavaScript runtime. It never contains application logic, and it is framework-agnostic.

## Contract

### Entry points

The `exports` map in `package.json` is the public entry list:

| Export | File | Tier |
|---|---|---|
| `.` | `dist/index.js` and `dist/index.d.ts`: `defaultTheme` and `ExerisTheme` | Frozen at the package's 1.0 |
| `./theme` | `src/styles/theme.css`: the Tailwind v4 `@theme` entry and the `dark` variant | Frozen names |
| `./styles` | `src/styles/index.css`: the `.exeris-*` component classes | Frozen names |
| `./preview` | `src/styles/preview.css`: classes no generator emits yet | Outside the freeze |
| `./theme-exeris` | `src/styles/theme-exeris.css`: the opt-in brand theme | Outside the freeze |
| `./styles/*`, `./package.json` | Direct file access | Outside the snapshot |

The consumer imports Tailwind first, then `./theme`, then `./styles`, in the same stylesheet. The component classes use `@apply`, so Tailwind must process them. The README at [exeris-sdk-ui-kit](../../exeris-sdk-ui-kit/README.md) has the full usage and class tables.

### Rules

The invariants are written once, in the [ui-kit policy](../../.agents/policies/ui-kit.md) and [ADR-094](../adr/ADR-094-ui-kit-public-npm-own-version-line-tailwind-v4-only.md). In short:

- **Tailwind v4 only.** `theme.css` is the single source of the utility namespace, and the package ships no Tailwind v3 preset.
- **Dark mode follows a `.dark` class**, not the operating system. A consumer who wants the system signal declares its own `@custom-variant dark` after the import. `tests/dark-mode-signal.test.js` compiles the documented setup with Tailwind v4 to check both.
- **Names freeze, values do not.** `tests/public-surface.txt` lists every frozen name as a `kind:name` line (`class`, `token`, `theme-var`, `theme`), and `tests/public-surface.test.js` compares it with the package. Colors, spacing, radii and shadows are free to change. The `./preview` and `./theme-exeris` entries are outside the snapshot.
- **The TypeScript surface is gated.** `npm run lint`, `npm run docs:check` and `npm run api:check` run in CI against the committed golden `api/ui-kit.api.md`; `npm run api:accept` regenerates it. A removed export, a narrowed peer range or a moved class is also recorded in `api/accepted-api-changes.json`.
- **The version line is the package's own.** Its number is unrelated to the Java line, and the SDK's 1.0.0 freeze does not cover the package. See [section 2b of the 1.0 migration guide](../../MIGRATION-0.x-to-1.0.md).

## Hot path

None. The package has no runtime code on a request path. `defaultTheme` is a constant read at build time by generated Angular code. All styling cost is paid when Tailwind compiles the consumer's stylesheet, and in the browser the cost is ordinary CSS.

## Failure modes

- **Unstyled components.** The consumer imported `./styles` without Tailwind, or listed `index.css` in `angular.json` `styles` on its own. `@apply` is then never processed.
- **Utilities missing.** `./theme` was not imported, or Tailwind below v4 is installed. The peer range is `>=4.0.0`.
- **Dark mode follows the OS or does nothing.** A consumer declared its own `@custom-variant dark`, or never added the `dark` class.
- **Tenant color override ignored.** Tailwind v4 resolves an `@theme` value where it is declared, so a narrower scope must set both the `--exeris-*` property and the matching `--color-exeris-*` property.
- **CI fails.** `public-surface.test.js` on a removed or renamed name, `api:check` on API drift, and the per-file 85 percent coverage gate in `vitest.config.ts`. The last one is today a formality for the single TypeScript file.

## Trade-off

Names freeze and values stay free. A host, a tenant or a CMS can retheme without a release, at the cost that a value change can alter an application's look with no signal from the name snapshot. The drift tests hold the three surfaces consistent with each other, not constant over time.

## Owning ADRs

- ADR-094, public npm distribution, own version line and Tailwind v4 only: [ADR-094](../adr/ADR-094-ui-kit-public-npm-own-version-line-tailwind-v4-only.md).
- ADR-085, which makes this package a gated TypeScript surface: [exeris-docs](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-085-documentation-architecture-and-repo-hygiene-standards.md).

The rules live in [ui-kit policy](../../.agents/policies/ui-kit.md).

## Where this does not apply

The kit is not part of the Maven reactor and is not covered by the SDK's Java semver gate. It does not generate Angular code; the tooling does. It does not target Tailwind v3 or earlier, and it is not a general component library: it provides classes and tokens, not behavior. The opt-in brand theme and the preview classes carry no stability promise.
