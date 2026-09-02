# @exeris-systems/ui-kit

> Exeris UI Kit - Base styles and design tokens for generated components

## Features

- 🎨 **CSS Design Tokens** - Centralized theme via CSS Custom Properties
- 🌙 **Dark Mode Support** - Automatic dark theme via `.dark` class
- 🧩 **Tailwind Preset** - Drop-in preset for Tailwind CSS projects
- 📦 **Zero Runtime** - Pure CSS, no JavaScript runtime

## Versioning

This package versions **independently of the Exeris SDK's Java artifacts**. The SDK's 1.0.0
freeze does not cover it, and its version number is its own.

Its 1.0 will freeze **names, not values**: every `--exeris-*` custom property, every `.exeris-*`
class, and every Tailwind key that produces a utility are the contract — they appear in generated
components and in your own markup, so renaming one is a breaking change and happens at a major.
The values behind them (colours, spacing, radii, shadows) are the theming surface you are meant
to override, and they may change in a minor.

## Installation

```bash
npm install @exeris-systems/ui-kit
```

> **Note:** this package is published to **GitHub Packages**, which requires authentication even for reads. Add `@exeris-systems:registry=https://npm.pkg.github.com` to your `.npmrc` and authenticate with a token that has `read:packages`. This requirement is lifted once the package moves to the public npm registry.

## Usage

### With Tailwind CSS

The `exeris-*` design-token namespace (`bg-exeris-primary`, `font-exeris`,
`p-exeris-md`, `rounded-exeris-md`, `shadow-exeris-lg`, `animate-exeris-spin`, …)
ships two ways — pick the one matching your Tailwind major.

**Tailwind v3** — JS preset in `tailwind.config.js`:

```javascript
import exerisPreset from '@exeris-systems/ui-kit/tailwind.preset.js';

export default {
  presets: [exerisPreset],
  content: ['./src/**/*.{html,ts}'],
  // ... your config
};
```

**Tailwind v4** — CSS-first (v4 removed JS presets, so the preset above does not
apply). Import both of this package's entries next to Tailwind:

```css
@import "tailwindcss";
@import "@exeris-systems/ui-kit/theme";   /* token namespace + the `dark` variant */
@import "@exeris-systems/ui-kit/styles";  /* the .exeris-* component classes */
```

Both are required on v4 — see [Import Base Styles](#import-base-styles) for what
each one carries and what a build missing `…/theme` loses.

Both entries declare the same tokens and both resolve them through the same
`--exeris-*` custom properties, so a utility follows a runtime override on either
major. Two tests keep it that way: one compares the two declarations, the other
compiles `theme.css` with a real Tailwind v4 and checks the utilities it produces.

**Scoped overrides on v4 need one extra line.** Re-pointing a token on `:root`
works as you would expect. Re-pointing it on a *container* — a `.dark` wrapper, a
tenant scope — also needs the colour it maps to, because v4 resolves a `@theme`
value where it is declared:

```css
.tenant-acme {
  --exeris-primary: 220 38 38;
  --color-exeris-primary: rgb(var(--exeris-primary)); /* v4 only; v3 needs no repeat */
}
```

The kit already does this for its own `.dark`, so dark mode needs nothing from you.

### Import Base Styles

In your main CSS file. **On v3 this one import is the whole story; on v4 it is half of it** —
copy the block for your major rather than the first one you see:

```css
/* Tailwind v3 — the preset in tailwind.config.js carries the token namespace */
@import '@exeris-systems/ui-kit/styles';
```

```css
/* Tailwind v4 — both entries, every time */
@import "tailwindcss";
@import "@exeris-systems/ui-kit/theme";
@import "@exeris-systems/ui-kit/styles";
```

`styles` works on both majors. The file is written against v3 — it opens with the `@tailwind`
directives, which are no-ops under v4 — and every `.exeris-*` component class it declares is
emitted by a v4 build too, checked by compiling it with each major on every CI run.

> **Why v4 needs `…/theme` as well**, even if you never write a `bg-exeris-*` utility: only
> `theme.css` carries the v4 `@theme` mapping (v4 has no JS preset to read it from) **and** the
> `dark` variant definition. Drop it and the component classes silently fall back to the
> operating system's dark setting while your `.dark` toggle moves the tokens — see
> [Dark Mode](#dark-mode). The `--exeris-*` design tokens come along either way: both files
> declare them, with identical values, which is harmless and guarded by a test.

Or in Angular's `angular.json`:

```json
{
  "styles": [
    "node_modules/@exeris-systems/ui-kit/src/styles/index.css",
    "src/styles.css"
  ]
}
```

## CSS Classes

### Buttons

```html
<button class="exeris-btn exeris-btn-primary">Primary</button>
<button class="exeris-btn exeris-btn-secondary">Secondary</button>
<button class="exeris-btn exeris-btn-danger">Danger</button>
<button class="exeris-btn exeris-btn-ghost">Ghost</button>
```

### Form Inputs

```html
<input class="exeris-input" type="text" />
<input class="exeris-input exeris-input-error" type="text" />
<label class="exeris-label">Label</label>
<p class="exeris-error-text">Error message</p>
<p class="exeris-help-text">Help text</p>
```

### Cards

```html
<div class="exeris-card">
  <div class="exeris-card-header">Header</div>
  <div class="exeris-card-body">Content</div>
  <div class="exeris-card-footer">Footer</div>
</div>
```

### Badges

```html
<span class="exeris-badge exeris-badge-primary">Primary</span>
<span class="exeris-badge exeris-badge-success">Success</span>
<span class="exeris-badge exeris-badge-warning">Warning</span>
<span class="exeris-badge exeris-badge-danger">Danger</span>
```

### Alerts

```html
<div class="exeris-alert exeris-alert-info">Info message</div>
<div class="exeris-alert exeris-alert-success">Success message</div>
<div class="exeris-alert exeris-alert-warning">Warning message</div>
<div class="exeris-alert exeris-alert-danger">Error message</div>
```

## Design Tokens (CSS Variables)

```css
:root {
  /* Colors */
  --exeris-primary: 79 70 229;
  --exeris-secondary: 100 116 139;
  --exeris-success: 34 197 94;
  --exeris-warning: 245 158 11;
  --exeris-danger: 239 68 68;
  --exeris-info: 59 130 246;

  /* Spacing */
  --exeris-spacing-xs: 0.25rem;
  --exeris-spacing-sm: 0.5rem;
  --exeris-spacing-md: 1rem;
  --exeris-spacing-lg: 1.5rem;
  --exeris-spacing-xl: 2rem;

  /* Border Radius */
  --exeris-radius-sm: 0.25rem;
  --exeris-radius-md: 0.375rem;
  --exeris-radius-lg: 0.5rem;
  --exeris-radius-xl: 0.75rem;

  /* Transitions */
  --exeris-transition-fast: 150ms;
  --exeris-transition-normal: 200ms;
  --exeris-transition-slow: 300ms;
}
```

## Dark Mode

Apply the `.dark` class to your `<html>` or `<body>` element:

```html
<html class="dark">
  <!-- Dark theme active -->
</html>
```

That class is the only signal, and it drives both halves of the package: the design tokens
(`--exeris-*`, and every `bg-exeris-*` / `p-exeris-*` utility that reads them) and the component
classes' own dark styling. Toggling it re-themes everything at once — no OS coordination needed,
and no flash of light chrome on a dark-themed page.

It applies to `dark:` utilities *you* write too, not just the ones this package ships: with the
preset (v3) or the theme entry (v4) in your build, `dark:bg-exeris-primary` in your own markup
follows the same class.

**If you would rather follow the OS**, override it in your own config — a consumer's setting wins
over the preset's:

```javascript
export default {
  presets: [exerisPreset],
  darkMode: 'media',      // or ['selector', '[data-theme="dark"]'] for your own attribute
};
```

<details>
<summary>Why this is stated so explicitly</summary>

Until recently the two halves answered to *different* signals. The tokens followed `.dark`, while
the component classes' `dark:` variants fell back to Tailwind's default,
`@media (prefers-color-scheme: dark)`. An app that toggled the class got dark tokens and light
component chrome unless the operating system happened to agree — dark text on a light input.

It is now one switch: `darkMode: 'class'` in the v3 preset, and
`@custom-variant dark (&:where(.dark, .dark *))` in `theme.css` for v4, which has no JS preset to
read the former from. Both are asserted against real compiler output on every CI run
(`tests/dark-mode-signal.test.js`), including that your override above still works.

**On v4 this needs the `…/theme` import.** A v4 build that pulls in `…/styles` alone keeps the
media query for the component classes, because the `@custom-variant` line lives in the theme
entry. It is not in `index.css` because that file is also v3's entry, and v3 does not consume
`@custom-variant` — it copies the at-rule straight into every v3 consumer's compiled output.
</details>

## License

Apache-2.0

