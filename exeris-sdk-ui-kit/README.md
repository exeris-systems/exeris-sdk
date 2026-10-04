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

| Group | Classes |
|:--|:--|
| Buttons | `exeris-btn` with `-primary`, `-secondary`, `-danger`, `-ghost`, `-sm`, `-lg` |
| Forms | `exeris-input`, `exeris-input-error`, `exeris-select`, `exeris-textarea`, `exeris-checkbox`, `exeris-radio`, `exeris-radio-group`, `exeris-toggle`, `exeris-range`, `exeris-color`, `exeris-file`, `exeris-rating`, `exeris-chips`, `exeris-chip`, `exeris-editor`, `exeris-label`, `exeris-help-text`, `exeris-error-text` |
| Cards | `exeris-card`, `exeris-card-header`, `exeris-card-body`, `exeris-card-footer` |
| Alerts | `exeris-alert` with `-info`, `-success`, `-warning`, `-danger` |
| Badges | `exeris-badge` with `-primary`, `-success`, `-warning`, `-danger`, `-gray` |
| Loading | `exeris-spinner` |
| Data | `exeris-table` |
| Utilities | `exeris-truncate-2`, `exeris-truncate-3`, `exeris-focus-visible`, `exeris-scrollbar-hide`, `exeris-scroll-smooth` |

```html
<button class="exeris-btn exeris-btn-primary">Save</button>
<div class="exeris-alert exeris-alert-warning">Unsaved changes</div>
```

## Theming

Every `exeris-*` utility reads an `--exeris-*` custom property: colours (`primary`, `secondary`,
`success`, `warning`, `danger`, `info`, and the `bg-*`, `text-*` and `border*` surfaces),
`spacing-*`, `radius-*`, `shadow-*` and `transition-*`. Colours are space-separated RGB channels.
The component classes use Tailwind's default palette and do not follow these properties. Override
them on `:root`:

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

## Versioning

This package versions independently of the Exeris SDK's Java artifacts. Its 1.0 freezes names, not
values: renaming or removing an `--exeris-*` property, an `.exeris-*` class or an `exeris-*` utility
is a breaking change, while colours, spacing, radii and shadows may change in a minor. Upgrade
steps are in [MIGRATION.md](https://github.com/exeris-systems/exeris-sdk/blob/main/MIGRATION.md).

## Links

- [Source](https://github.com/exeris-systems/exeris-sdk/tree/main/exeris-sdk-ui-kit)
- [Contributing](https://github.com/exeris-systems/exeris-sdk/blob/main/exeris-sdk-ui-kit/CONTRIBUTING.md)
- [Issues](https://github.com/exeris-systems/exeris-sdk/issues)

## License

Apache-2.0
