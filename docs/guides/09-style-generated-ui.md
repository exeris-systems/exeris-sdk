---
title: Style generated UI
type: howto
visibility: public
owning-repo: exeris-sdk
status: active
last-verified: 2026-10-29
---

# Style generated UI

**Audience:** you build or host a front end that renders Exeris-generated screens and need the design tokens, the Tailwind theme and the component classes they use.

**Goal:** install `@exeris/ui-kit`, import its theme and styles into one stylesheet, and control dark mode and token values.

> **Verified against** the `exeris-sdk-ui-kit` sources at commit `33574011`, 2026-10-29. Every snippet is quoted or adapted from the cited file and symbol, never from a line range. If a snippet and its source disagree, **the source wins and this guide is the bug**. No example module covers the package. Its own test suite compiles the documented setup with Tailwind v4.

## Prerequisites

- Tailwind CSS v4. The package declares it as a peer dependency and ships no preset for earlier majors.
- A build that processes your global stylesheet with Tailwind. In an Angular application that file is `src/styles.css`.
- The contract of the package, its tiers and its failure modes are in [UI kit](../modules/09-ui-kit.md). Frozen names are in the [stability matrix](../stability-matrix.md).

## Steps

### 1. Install the package

The package is published to the public npm registry.

Source: `exeris-sdk-ui-kit/README.md`, `Installation` (quoted)

```bash
npm install @exeris/ui-kit
```

### 2. Import the theme and the styles

Import both entries after Tailwind, in the same stylesheet. The component classes use `@apply`, so Tailwind must process them. Do not list `src/styles/index.css` in `angular.json` on its own.

Source: `exeris-sdk-ui-kit/README.md`, `Usage` (quoted)

```css
@import "tailwindcss";
@import "@exeris/ui-kit/theme";   /* exeris-* utilities and the `dark` variant */
@import "@exeris/ui-kit/styles";  /* the .exeris-* component classes */
```

The entry points come from the `exports` map of the package. Two further entries, `./preview` and `./theme-exeris`, are opt-in and outside the freeze.

Source: `exeris-sdk-ui-kit/package.json`, `exports` (adapted, the root, wildcard and package.json entries removed)

```json
    "./styles": "./src/styles/index.css",
    "./theme": "./src/styles/theme.css",
    "./preview": "./src/styles/preview.css",
    "./theme-exeris": "./src/styles/theme-exeris.css",
```

### 3. Use the classes

Markup uses the `.exeris-*` classes and the `exeris-*` utilities directly.

Source: `exeris-sdk-ui-kit/README.md`, `Component classes` (quoted)

```html
<button class="exeris-btn exeris-btn-primary">Save</button>
<div class="exeris-alert exeris-alert-warning">Unsaved changes</div>
```

### 4. Switch dark mode

Dark mode follows a class. Add `dark` to `<html>` or `<body>`, and the tokens, the component classes and your own `dark:` utilities switch together. To follow the operating system instead, declare the variant after the imports.

Source: `exeris-sdk-ui-kit/README.md`, `Dark mode` (quoted)

```css
@custom-variant dark (@media (prefers-color-scheme: dark));
```

### 5. Override tokens

Colours are space-separated RGB channels in `--exeris-*` custom properties. Override them on `:root`.

Source: `exeris-sdk-ui-kit/README.md`, `Theming` (quoted)

```css
:root {
  --exeris-primary: 220 38 38;
}
```

On a narrower scope, such as a tenant wrapper, set the matching Tailwind colour as well. Tailwind v4 resolves a `@theme` value where it is declared.

Source: `exeris-sdk-ui-kit/README.md`, `Theming` (quoted)

```css
.tenant-acme {
  --exeris-primary: 220 38 38;
  --color-exeris-primary: rgb(var(--exeris-primary));
}
```

## Verify

```bash
cd exeris-sdk-ui-kit && npm ci && npm test
```

Expected: the Vitest run passes, including the dark-mode signal test and the Tailwind v4 compile tests. To check your own application, build it and confirm that a `.exeris-btn` renders with the primary colour and that adding `class="dark"` to `<html>` changes it.

## Not available today

- **A runnable example.** No module in `exeris-sdk-examples` renders these classes.
- **Angular code generation.** The package is framework-agnostic CSS. Emitting components is the job of `exeris-tooling`.
- **Tailwind v3.** The package does not target it and ships no preset.

## Trade-off

Class names are frozen and values are not. A host or tenant can retheme without a release, and a value change can alter an application's look with no signal from the name snapshot. Pin the package version if the look must not move.

## Where this does not apply

- **Behaviour.** The package supplies classes and tokens, not components with logic.
- **The Java reactor.** The package is not a Maven module and is outside the SDK's Java semver gate.
- **The preview and brand entries.** They sit outside the 1.0 freeze.
