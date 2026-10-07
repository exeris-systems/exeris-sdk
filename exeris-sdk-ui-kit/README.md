# @exeris/ui-kit

Design tokens, a Tailwind CSS v4 theme and component classes for Exeris-generated applications.
Pure CSS, plus a typed `defaultTheme` constant; no JavaScript runtime.

## Installation

```bash
npm install @exeris/ui-kit
```

Requires Tailwind CSS v4 (`tailwindcss >= 4`).

## Usage

In your global stylesheet (in an Angular application, `src/styles.css`):

```css
@import "tailwindcss";
@import "@exeris/ui-kit/theme";   /* exeris-* utilities and the `dark` variant */
@import "@exeris/ui-kit/styles";  /* the .exeris-* component classes */
```

Import both entries, after Tailwind and in the same stylesheet. The component classes use
`@apply`, so Tailwind must process them: do not add `src/styles/index.css` to `angular.json`'s
`styles` array on its own.

The theme generates utilities such as `bg-exeris-primary`, `text-exeris-danger`, `p-exeris-md`,
`rounded-exeris-md`, `shadow-exeris-lg`, `font-exeris` and `animate-exeris-spin`.

## Component classes

`@exeris/ui-kit/styles` — the classes generated applications use, frozen at the package's 1.0:

| Group | Classes |
|:--|:--|
| Buttons | `exeris-btn` with `-primary`, `-secondary`, `-danger`, `-ghost`, `-sm` |
| Forms | `exeris-input`, `exeris-input-error`, `exeris-select`, `exeris-checkbox`, `exeris-label`, `exeris-help-text`, `exeris-error-text` |
| Cards | `exeris-card`, `exeris-card-header`, `exeris-card-body` |
| Alerts | `exeris-alert` with `-warning`, `-danger` |
| Badges | `exeris-badge` with `-success` |
| Data | `exeris-table` |

`@exeris/ui-kit/preview` — classes no generator emits yet. They may change in a minor and move to
`styles` when a generator uses them. Add `@import "@exeris/ui-kit/preview";` after the styles
import to use them:

| Group | Classes |
|:--|:--|
| Buttons | `exeris-btn-lg` |
| Forms | `exeris-textarea`, `exeris-radio`, `exeris-radio-group`, `exeris-toggle`, `exeris-range`, `exeris-color`, `exeris-file`, `exeris-rating`, `exeris-chips`, `exeris-chip`, `exeris-editor` |
| Cards | `exeris-card-footer` |
| Alerts | `exeris-alert-info`, `exeris-alert-success` |
| Badges | `exeris-badge-primary`, `exeris-badge-warning`, `exeris-badge-danger`, `exeris-badge-gray` |
| Loading | `exeris-spinner` |
| Utilities | `exeris-truncate-2`, `exeris-truncate-3`, `exeris-focus-visible`, `exeris-scrollbar-hide`, `exeris-scroll-smooth` |

```html
<button class="exeris-btn exeris-btn-primary">Save</button>
<div class="exeris-alert exeris-alert-warning">Unsaved changes</div>
```

## Theming

The `exeris-*` utilities and the component classes read `--exeris-*` custom properties: colours
(`primary`, `secondary`, `success`, `warning`, `danger`, `info`, and the `bg-*`, `text-*` and
`border*` surfaces), `spacing-*`, `radius-*`, `shadow-*` and `transition-*`. Colours are
space-separated RGB channels. Alerts, badges, chips, the file button, the danger button and error
text use Tailwind's palette. Override the properties on `:root`:

```css
:root {
  --exeris-primary: 220 38 38;
}
```

To override a colour on a narrower scope (a tenant wrapper, for example), set the matching Tailwind
colour as well, because Tailwind v4 resolves a `@theme` value where it is declared:

```css
.tenant-acme {
  --exeris-primary: 220 38 38;
  --color-exeris-primary: rgb(var(--exeris-primary));
}
```

## Dark mode

Add the `dark` class to `<html>` or `<body>`. It switches the tokens, the component classes and
your own `dark:` utilities together. To follow the operating system instead, declare the variant
after the imports:

```css
@custom-variant dark (@media (prefers-color-scheme: dark));
```

## Exeris brand theme

`@exeris/ui-kit/theme-exeris` is an opt-in brand theme following the Exeris brand kit (v6): IBM Plex
Sans and Mono, square corners, flat blue-black surfaces, a Flow Blue and Flow Cyan accent, mono
uppercase buttons and labels. Import it last, then put
`data-theme="exeris"` on `<html>` or on any container:

```css
@import "tailwindcss";
@import "@exeris/ui-kit/theme";
@import "@exeris/ui-kit/styles";
@import "@exeris/ui-kit/preview";       /* optional */
@import "@exeris/ui-kit/theme-exeris";
```

```html
<html data-theme="exeris">
```

Without the attribute nothing changes: the default theme stays as described above. Inside it, every
component class and `exeris-*` utility renders in the brand with no change to the markup. The theme
re-points the `--exeris-*` properties and squares Tailwind's `rounded-*` scale within the scope.

- **Dark only.** The brand has one colour scheme, and `.dark` inside the scope changes nothing. Add
  `class="dark"` next to the attribute if your own `dark:` utilities should apply.
- **Fonts are yours to load.** The theme names IBM Plex and never fetches it. Self-host it, or link
  it from your page. Without it the text falls back to the system sans-serif and monospace faces.
  The base text size is left alone. The brand sets body copy at 13px; set that on your own
  root if you want it.
- **Brand tokens.** The scope declares the palette as `--ex-*` properties. The brand kit's five
  colours are declared verbatim; every other step is derived from them in oklch. The `--exeris-*`
  colours the theme sets are the sRGB values of these, because the kit's colours are RGB channels.

| Token | Value | Role |
|:--|:--|:--|
| `--ex-bg` | `#0A0F1A` | Background |
| `--ex-fg` | `#F8FAFC` | Surface: text on dark (`--exeris-text-primary`) |
| `--ex-flow-blue` | `#2563EB` | Flow Blue: the primary action, solid (`--exeris-primary`) |
| `--ex-flow-cyan` | `#06B6D4` | Flow Cyan: links, focus, info, eyebrow (`--exeris-border-focus`, `--exeris-info`) |
| `--ex-flow` | `#2563EB` → `#06B6D4` | The flow gradient, for the flow element only |
| `--ex-evidence` | `#F59E0B` | Evidence Orange: verified results only |
| `--ex-bg-1`, `--ex-surface` … `--ex-surface-4` | oklch, hue 265 | Surface steps above the background |
| `--ex-fg-2` … `--ex-fg-5` | oklch, hue 265 | Text steps, from secondary text to line numbers |
| `--ex-line`, `--ex-line-2`, `--ex-line-strong` | oklch, hue 265 | Rules and borders |
| `--ex-flow-blue-hover`, `--ex-flow-blue-text` | oklch, hue 263 | The primary hover fill, and Flow Blue lifted for small text |
| `--ex-ok`, `--ex-warn`, `--ex-err` | oklch | Functional status (`--exeris-success`, `-warning`, `-danger`) |
| `--ex-font`, `--ex-mono` | IBM Plex Sans, IBM Plex Mono | Type |

- **The accent is solid.** Buttons, links and focus use Flow Blue or Flow Cyan as flat colours. The
  `--ex-flow` gradient is for the flow element alone, one hero or mark element per view, and no class
  in the theme paints it: put it on that element yourself (`background: var(--ex-flow)`).
- **Evidence Orange is for verified results.** It marks a measured figure or a verdict and is never a
  UI accent: only `exeris-tag-evidence` and `exeris-kpi-value-evidence` paint it, no `--exeris-*`
  token carries it, and the warning colour is a yellow at a distinct hue. Use the evidence modifiers
  only on a value that has been measured and checked.

The scope also provides brand primitives the component layer lacks:

| Primitive | Classes |
|:--|:--|
| Eyebrow | `exeris-eyebrow` |
| Tag | `exeris-tag` with `-flow`, `-cyan`, `-evidence`, `-ok`, `-warn`, `-err`, `-muted` |
| LED status dot | `exeris-led` with `-ok`, `-flow`, `-warn`, `-err`, `-live`. `-flow` and `-live` pulse, and stay steady under `prefers-reduced-motion` |
| KPI | `exeris-kpi`, `exeris-kpi-label`, `exeris-kpi-value` with `-flow`, `-cyan`, `-evidence`, `exeris-kpi-sub` |
| Code block | `exeris-code`, token spans `exeris-code-kw`, `-an`, `-ty`, `-str`, `-num`, `-cm`, `-fn`, `-pn`, line numbers `exeris-code-ln` |
| Definition row | `exeris-def` (a `<div>` holding one `<dt>` and `<dd>`, inside a `<dl>`) |
| Section head | `exeris-section-head` (an `<h2>` and a `<p>`, optionally an eyebrow) |
| Dashed link | `exeris-link` |

```html
<span class="exeris-tag exeris-tag-flow"><span class="exeris-led exeris-led-flow"></span>flow</span>
```

Like `@exeris/ui-kit/preview`, this entry sits outside the 1.0 freeze. Its path, the attribute, the
`--ex-*` names and the primitives may change in a minor.

## Versioning

This package versions independently of the Exeris SDK's Java artifacts. Its 1.0 freezes names, not
values: renaming or removing an `--exeris-*` property, a class in `@exeris/ui-kit/styles` or an
`exeris-*` utility is a breaking change, while colours, spacing, radii and shadows may change in a
minor. The `@exeris/ui-kit/preview` classes are outside that freeze. Upgrade
steps are in [MIGRATION.md](https://github.com/exeris-systems/exeris-sdk/blob/main/MIGRATION.md).

## Links

- [Source](https://github.com/exeris-systems/exeris-sdk/tree/main/exeris-sdk-ui-kit)
- [Contributing](CONTRIBUTING.md)
- [Issues](https://github.com/exeris-systems/exeris-sdk/issues)

## License

Apache-2.0
