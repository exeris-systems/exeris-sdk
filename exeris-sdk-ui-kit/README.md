# @exeris/ui-kit

> Exeris UI Kit - Base styles and design tokens for generated components

## Features

- 🎨 **CSS Design Tokens** - Centralized theme via CSS Custom Properties
- 🌙 **Dark Mode Support** - Automatic dark theme via `.dark` class
- 🧩 **Tailwind v4 theme** - `@theme` entry that generates the `exeris-*` utilities
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
npm install @exeris/ui-kit
```

The package is on the public npm registry, so it needs no `.npmrc` entry and no token. It was
published to GitHub Packages as `@exeris-systems/ui-kit` before; to move, change the dependency name
and drop the `@exeris-systems:registry` line from `.npmrc`.

## Usage

The package targets **Tailwind CSS v4**. Import Tailwind and both of this package's entries in
your global stylesheet — in an Angular application, `src/styles.css`:

```css
@import "tailwindcss";
@import "@exeris/ui-kit/theme";   /* token namespace + the `dark` variant */
@import "@exeris/ui-kit/styles";  /* the .exeris-* component classes */
```

Both entries are required, every time:

- **`…/theme`** declares the `exeris-*` design-token namespace through `@theme`, so Tailwind
  generates `bg-exeris-primary`, `font-exeris`, `p-exeris-md`, `rounded-exeris-md`,
  `shadow-exeris-lg`, `animate-exeris-spin`, …, and it defines the `dark` variant. Without it the
  component classes fall back to the operating system's dark setting while your `.dark` toggle
  moves the tokens — see [Dark Mode](#dark-mode).
- **`…/styles`** declares the `.exeris-*` component classes. It uses `@apply` and `dark:`
  variants, so Tailwind has to process it in the same stylesheet as `@import "tailwindcss"`. Do
  not list `node_modules/@exeris/ui-kit/src/styles/index.css` on its own in `angular.json`'s
  `styles` array: that file is then compiled without Tailwind and fails.

The `--exeris-*` design tokens come with either entry; both declare them with identical values,
which is harmless and guarded by a test.

Every `exeris` utility resolves through an `--exeris-*` custom property, so it follows a runtime
override. One test compiles `theme.css` with a real Tailwind v4 and checks that each utility
still reaches its property.

**Scoped overrides need one extra line.** Re-pointing a token on `:root` works as you would
expect. Re-pointing it on a *container* — a `.dark` wrapper, a tenant scope — also needs the
colour it maps to, because Tailwind v4 resolves a `@theme` value where it is declared:

```css
.tenant-acme {
  --exeris-primary: 220 38 38;
  --color-exeris-primary: rgb(var(--exeris-primary));
}
```

The kit already does this for its own `.dark`, so dark mode needs nothing from you.

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

It applies to `dark:` utilities *you* write too, not just the ones this package ships:
`dark:bg-exeris-primary` in your own markup follows the same class. The theme entry declares it
with `@custom-variant dark (&:where(.dark, .dark *))`, and `tests/dark-mode-signal.test.js`
asserts the compiled output against it.

**If you would rather follow the OS**, declare your own variant after the theme import:

```css
@import "tailwindcss";
@import "@exeris/ui-kit/theme";
@import "@exeris/ui-kit/styles";
@custom-variant dark (@media (prefers-color-scheme: dark));
```

## License

Apache-2.0


## Contributing

Three checks guard this package's published surface, and CI runs all three
(`tsdoc-gate` in `.github/workflows/guardrails.yml`):

```bash
npm run lint         # TSDoc rules — the shared exeris-systems/.github fragment
npm run build        # tsc, which the next two read
npm run docs:check   # typedoc: every export and every token documented
npm run api:check    # api-extractor: api/ui-kit.api.md matches the built .d.ts
npm test             # the name-snapshot and drift suites
```

`npm run lint` loads its rules from [`exeris-systems/.github`](https://github.com/exeris-systems/.github),
which has to sit at `./.guardrails` — the path the gate checks it out to. Once, from this
directory:

```bash
git clone --depth 1 https://github.com/exeris-systems/.github .guardrails
```

If you already have that repository cloned, a worktree of your clone at the same path works
too, and `git pull` then keeps both current.

It has to be a real directory, **not a symlink** to a checkout elsewhere. Node resolves the
config's realpath before it looks for `node_modules`, so through a symlink it searches beside
the bundle instead of beside this package and never finds `eslint-plugin-jsdoc`. `.guardrails`
is git-ignored.

When a change to `src/index.ts` alters the surface, accept it deliberately and commit the
result — `npm run api:accept` rewrites `api/ui-kit.api.md`. A `-` line in that diff removes
a name, which is a **major** for this package (see [Versioning](#versioning)).
