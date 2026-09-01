import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import preset from '../tailwind.preset.js';

/**
 * The Tailwind v4 `@theme` entry (`src/styles/theme.css`, B1) must stay in sync
 * with the v3 `tailwind.preset.js` and the raw `--exeris-*` tokens in
 * `index.css`: a v3 consumer (preset) and a v4 consumer (this @theme) must get
 * the same `exeris-*` utility namespace, or generated components render
 * differently depending on the host's Tailwind major. This is the drift guard.
 * Also pins the B2 `package.json` exports so the documented imports resolve.
 *
 * Scope note: this file guards the *shape* of theme.css — which entries exist,
 * and that its token block still mirrors `index.css`. What those entries compile
 * to is `tests/tailwind-v4-compile.test.js`, which runs a real v4 over this file.
 * The split matters: an earlier version of this test asserted that each v4
 * colour literal equalled the light-theme channels in `index.css`, which read
 * like parity but was the bug — a literal cannot follow `.dark`.
 */
const root = dirname(dirname(fileURLToPath(import.meta.url)));
const themeCss = readFileSync(join(root, 'src/styles/theme.css'), 'utf8');
const indexCss = readFileSync(join(root, 'src/styles/index.css'), 'utf8');
const pkg = JSON.parse(readFileSync(join(root, 'package.json'), 'utf8'));

function cssVar(css, name) {
  const m = css.match(new RegExp(`${name}:\\s*([^;]+);`));
  return m ? m[1].trim() : undefined;
}

/** The body of the first `<opener> {` … `}` block, comments and all. */
function block(css, opener) {
  const start = css.indexOf(`${opener} {`);
  if (start < 0) throw new Error(`no '${opener}' block found`);
  let depth = 0;
  for (let i = css.indexOf('{', start); i < css.length; i += 1) {
    if (css[i] === '{') depth += 1;
    else if (css[i] === '}') {
      depth -= 1;
      if (depth === 0) return css.slice(css.indexOf('{', start) + 1, i);
    }
  }
  throw new Error(`unterminated '${opener}' block`);
}

/** `--name: value` pairs in a block, comments stripped. */
function declarations(body) {
  const found = new Map();
  for (const [, name, value] of body.replace(/\/\*[\s\S]*?\*\//g, '').matchAll(/(--[\w-]+):\s*([^;]+);/g)) {
    found.set(name, value.trim().replace(/\s+/g, ' '));
  }
  return found;
}

const themeBlock = declarations(block(themeCss, '@theme'));

describe('Tailwind v4 @theme entry (B1)', () => {
  it('declares exactly the preset exeris color set, mapped to the runtime channels', () => {
    const presetColors = Object.keys(preset.theme.extend.colors.exeris).sort();
    const themeColors = [...themeBlock.keys()]
      .filter((name) => name.startsWith('--color-exeris-'))
      .map((name) => name.replace('--color-exeris-', ''))
      .sort();
    expect(themeColors, 'v4 @theme must declare the same exeris colors as the v3 preset').toEqual(presetColors);

    for (const key of presetColors) {
      // `rgb(var(…))` and not a literal: v4 has no `<alpha-value>`, and reading
      // the channels is what lets `/50` color-mix the live value.
      expect(themeBlock.get(`--color-exeris-${key}`), `--color-exeris-${key} must read the runtime channels`)
        .toBe(`rgb(var(--exeris-${key}))`);
    }
  });

  it('declares every preset spacing / radius / shadow / duration / animation token', () => {
    for (const key of Object.keys(preset.theme.extend.spacing)) {
      expect(themeCss, `missing --spacing-${key}`).toContain(`--spacing-${key}:`);
    }
    for (const key of Object.keys(preset.theme.extend.borderRadius)) {
      expect(themeCss, `missing --radius-${key}`).toContain(`--radius-${key}:`);
    }
    for (const key of Object.keys(preset.theme.extend.boxShadow)) {
      expect(themeCss, `missing --shadow-${key}`).toContain(`--shadow-${key}:`);
    }
    for (const key of Object.keys(preset.theme.extend.transitionDuration)) {
      expect(themeCss, `missing --transition-duration-${key}`).toContain(`--transition-duration-${key}:`);
    }
    for (const key of Object.keys(preset.theme.extend.animation)) {
      expect(themeCss, `missing --animate-${key}`).toContain(`--animate-${key}:`);
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
 * theme.css repeats the `--exeris-*` declarations because a v4 build cannot
 * consume `index.css` (it opens with `@tailwind` directives and its component
 * layer uses `@apply exeris-btn`). The copy is only safe while it is identical,
 * so this is the guard that makes the duplication acceptable.
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

describe('package exports (B2)', () => {
  it('exposes the v4 theme and the v3 preset so the documented imports resolve', () => {
    expect(pkg.exports['./theme'], 'v4 consumers @import "@exeris-systems/ui-kit/theme"')
      .toBe('./src/styles/theme.css');
    expect(pkg.exports['./tailwind.preset.js'], 'README imports @exeris-systems/ui-kit/tailwind.preset.js')
      .toBe('./tailwind.preset.js');
  });
});
