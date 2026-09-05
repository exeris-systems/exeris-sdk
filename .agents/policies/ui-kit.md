# Policy: UI Kit Architecture & Invariants

`exeris-sdk-ui-kit` is a standalone npm package providing Tailwind CSS presets, component styling, and theme tokens for generated Exeris frontends and Studio.

## Hard Rules

1. **npm-only (excluded from Maven reactor):**
   `exeris-sdk-ui-kit/` must never be added to `<modules>` in the root `pom.xml`. It builds using npm and Vitest (`npm ci && npm run test:coverage`).
2. **Dual Tailwind v3 and v4 compatibility:**
   The package maintains support for both Tailwind v3 and v4:
   - v3 consumers import `tailwind.preset.js`.
   - v4 consumers import `src/styles/theme.css` (`@theme`).
   - `theme.css` is verified by real Tailwind v4 compilation (`tests/tailwind-v4-compile.test.js`) rather than textual comparison. Values must map through `--exeris-*` CSS variables, never hard-coded color literals.
3. **Dark mode must be declared in both configuration files:**
   - `darkMode: 'class'` in `tailwind.preset.js` (for v3).
   - `@custom-variant dark (&:where(.dark, .dark *));` in `src/styles/theme.css` (for v4).
   Omitting either reverts consumers to `@media (prefers-color-scheme: dark)`. Verified by `tests/dark-mode-signal.test.js`.
4. **Independent release cadence:**
   The UI kit versions independently from the Java modules (e.g. `0.1.x` while Java is at `0.12.x`).
5. **Public surface snapshot:**
   `tests/public-surface.txt` snapshot-gates exported CSS class names and token names. At 1.0:
   - Token names (`--exeris-primary`) and component class vocabulary (`.exeris-input`) are **frozen**.
   - Theme values (colors, radii, shadows) remain **free** to support CMS and consumer customization.
