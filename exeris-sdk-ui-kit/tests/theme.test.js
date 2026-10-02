import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { THEME, THEME_UTILITIES, block, declarations } from './support/tailwind.js';

/**
 * The Tailwind v4 `@theme` entry (`src/styles/theme.css`) must stay in sync with
 * the raw `--exeris-*` tokens in `index.css`: every `exeris` utility reads a token
 * the host can override, so a `@theme` variable pointing at a token nothing
 * declares renders as an unset value. This is the drift guard. It also verifies
 * the `package.json` exports, so the documented imports resolve.
 *
 * Scope note: this file guards the *shape* of theme.css — which entries exist,
 * what they point at, and that its token block mirrors `index.css` exactly. What
 * those entries compile to is tested in `tests/tailwind-v4-compile.test.js`,
 * which runs a real v4 compiler over this file.
 */
const root = dirname(dirname(fileURLToPath(import.meta.url)));
const themeCss = readFileSync(join(root, 'src/styles/theme.css'), 'utf8');
const indexCss = readFileSync(join(root, 'src/styles/index.css'), 'utf8');
const pkg = JSON.parse(readFileSync(join(root, 'package.json'), 'utf8'));
const rootTokens = declarations(block(indexCss, ':root'));

function cssVar(css, name) {
  const m = css.match(new RegExp(`${name}:\\s*([^;]+);`));
  return m ? m[1].trim() : undefined;
}

describe('Tailwind v4 @theme entry', () => {
  it('maps every exeris colour to its runtime channels', () => {
    const colours = [...THEME.keys()].filter((name) => name.startsWith('--color-exeris-'));
    expect(colours.length, 'the @theme colour set looks too short to be real').toBeGreaterThan(5);
    for (const name of colours) {
      const key = name.replace('--color-exeris-', '');
      // `rgb(var(…))` and not a literal: v4 has no `<alpha-value>`, and reading
      // the channels is what lets `/50` color-mix the live value.
      expect(THEME.get(name), `${name} must read the runtime channels`).toBe(`rgb(var(--exeris-${key}))`);
    }
  });

  it('points every indirect entry at a token index.css declares', () => {
    for (const { themeVar, runtimeVar } of THEME_UTILITIES.filter((e) => e.runtimeVar)) {
      expect(rootTokens.has(runtimeVar), `${themeVar} reads ${runtimeVar}, which index.css :root does not declare`)
        .toBe(true);
    }
  });

  it('defines the custom keyframes its animations reference', () => {
    expect(themeCss).toContain('@keyframes fadeIn');
    expect(themeCss).toContain('@keyframes slideUp');
  });

  it('uses Inter as the primary exeris font', () => {
    expect(cssVar(themeCss, '--font-exeris')).toContain('Inter');
  });
});

/**
 * The `theme.css` file replicates the `--exeris-*` declarations from `index.css`.
 * This duplication is only safe while the two remain identical, so this guard
 * ensures they stay in sync.
 */
describe('theme.css token blocks mirror index.css', () => {
  for (const selector of [':root', '.dark']) {
    it(`${selector} declares the same tokens with the same values`, () => {
      const fromIndex = declarations(block(indexCss, selector));
      const fromTheme = declarations(block(themeCss, selector));

      // theme.css additionally recomputes the @theme colours in .dark; those are
      // the v4 mapping, not tokens, and index.css has no business carrying them.
      const tokens = new Map([...fromTheme].filter(([name]) => name.startsWith('--exeris-')));

      expect([...tokens.keys()].sort(), `${selector} token set drifted from index.css`)
        .toEqual([...fromIndex.keys()].sort());
      for (const [name, value] of fromIndex) {
        expect(tokens.get(name), `${selector} ${name} differs from index.css`).toBe(value);
      }
    });
  }
});

describe('package exports', () => {
  it('exposes the theme and the component layer so the documented imports resolve', () => {
    expect(pkg.exports['./theme'], 'consumers @import "@exeris/ui-kit/theme"')
      .toBe('./src/styles/theme.css');
    expect(pkg.exports['./styles'], 'consumers @import "@exeris/ui-kit/styles"')
      .toBe('./src/styles/index.css');
  });

  it('ships no Tailwind v3 preset', () => {
    expect(pkg.exports['./tailwind.preset.js'], 'only the v4 @theme entry is exported; the package has no Tailwind preset')
      .toBeUndefined();
    expect(pkg.files).not.toContain('tailwind.preset.js');
  });
});
