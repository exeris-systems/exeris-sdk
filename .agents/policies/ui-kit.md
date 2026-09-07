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
6. **TSDoc + API golden on the TypeScript surface:**
   ADR-085 §F.21a–c names this package one of three gated TypeScript surfaces in the ecosystem; the
   rules are [`tsdoc-conventions.md`](https://github.com/exeris-systems/exeris-docs/blob/main/standards/tsdoc-conventions.md).
   - Every exported symbol carries a **release tag** (`@public` / `@beta` / `@alpha` / `@internal`) and
     a doc comment — including nested property signatures, which typedoc checks.
   - `@author` and `@version` are banned; `@since` is `major.minor` (`0.1`, never `0.1.0`).
   - Markdown, not Javadoc markup: no `<p>`, no `{@code}`, no `{type}` in a tag.
   - **`api/ui-kit.api.md` is the committed golden**, the TypeScript analogue of japicmp. CI fails on
     drift; accept a change with `npm run api:accept` and commit it in the same PR. A `-` line
     removes a name, which is a **major** for this package (rule 4, rule 5).
   - The three checks are `npm run lint` (shared ESLint fragment from `exeris-systems/.github`),
     `npm run docs:check` (typedoc) and `npm run api:check` (api-extractor), wired in CI as the
     `tsdoc` job of `.github/workflows/guardrails.yml`.
   - The lint needs the bundle as a **real directory** at `exeris-sdk-ui-kit/.guardrails` — a symlink
     defeats Node's plugin resolution. See the package README's Contributing section.
