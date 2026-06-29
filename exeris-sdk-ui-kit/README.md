# @exeris-systems/ui-kit

> Exeris UI Kit - Base styles and design tokens for generated components

## Features

- 🎨 **CSS Design Tokens** - Centralized theme via CSS Custom Properties
- 🌙 **Dark Mode Support** - Automatic dark theme via `.dark` class
- 🧩 **Tailwind Preset** - Drop-in preset for Tailwind CSS projects
- 📦 **Zero Runtime** - Pure CSS, no JavaScript runtime

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
apply). Import the `@theme` entry next to Tailwind:

```css
@import "tailwindcss";
@import "@exeris-systems/ui-kit/theme";
```

Both entries declare the same tokens, so generated components render identically
on either major; a parity test keeps them in sync.

### Import Base Styles

In your main CSS file:

```css
@import '@exeris-systems/ui-kit/styles';
```

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

## License

Apache-2.0

